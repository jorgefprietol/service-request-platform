"""Verify the real HTTP/PostgreSQL stack, races, restart recovery and readiness.

Uses only Python's standard library. Writes no credentials to evidence.
Run against the isolated development or CI Compose project.
"""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.parse import urlencode
from urllib.error import HTTPError
import json
import os
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
env = {}
for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
    if "=" in line and not line.startswith("#"):
        key, value = line.split("=", 1)
        env[key] = value
env.update({k: os.environ[k] for k in env if k in os.environ})
BASE = "http://127.0.0.1:" + env["APP_PORT"]
checks = []
tokens = {}


def oidc_token(username, password_key, client="service-request-verification"):
    url = "http://127.0.0.1:" + env["IDENTITY_PORT"] + "/realms/service-requests/protocol/openid-connect/token"
    body = urlencode({"grant_type":"password", "client_id":client, "username":username, "password":env[password_key]}).encode()
    with urlopen(Request(url,data=body,headers={"Content-Type":"application/x-www-form-urlencoded"}),timeout=20) as response:
        return json.load(response)["access_token"]


def call(path, method="GET", data=None, role="requester", headers=None):
    request_headers = {"Content-Type": "application/json"}
    if role:
        request_headers["Authorization"] = "Bearer " + tokens[role]
    request_headers.update(headers or {})
    request = Request(BASE + path, data=json.dumps(data).encode() if data is not None else None,
                      method=method, headers=request_headers)
    try:
        response = urlopen(request, timeout=15)
    except HTTPError as error:
        response = error
    with response:
        content = response.read().decode()
        try:
            parsed = json.loads(content)
        except json.JSONDecodeError:
            parsed = content
        return response.status, parsed, response.headers


def check(name, condition):
    if not condition:
        raise AssertionError(name)
    checks.append(name)
    print("PASS", name, flush=True)


def compose(*arguments):
    subprocess.run(["docker", "compose", *arguments], cwd=ROOT, check=True,
                   stdout=subprocess.DEVNULL)


def ready(expected=200, deadline=90):
    until = time.monotonic() + deadline
    while time.monotonic() < until:
        try:
            if call("/actuator/health/readiness", role=None)[0] == expected:
                return True
        except (OSError, TimeoutError):
            pass
        time.sleep(1)
    return False


def main():
    check("readiness includes database", ready())
    tokens["requester"] = oidc_token("requester.one","IDENTITY_REQUESTER_PASSWORD")
    tokens["second-requester"] = oidc_token("requester.two","IDENTITY_SECOND_REQUESTER_PASSWORD")
    tokens["operator"] = oidc_token("operations.primary","IDENTITY_OPERATOR_PASSWORD")
    requester_identity = call("/api/me")[1]
    operator_identity = call("/api/me",role="operator")[1]
    check("OIDC preserves distinct individual subjects", requester_identity["subject"] != operator_identity["subject"] and operator_identity["displayName"] == "operations.primary" and "OPERATOR" in operator_identity["roles"])
    wrong_audience = oidc_token("requester.one","IDENTITY_REQUESTER_PASSWORD","unrelated-verification")
    check("JWT for another audience rejected", call("/api/requests",headers={"Authorization":"Bearer " + wrong_audience})[0] == 401)
    jwt_parts = tokens["requester"].split(".")
    import base64
    payload = json.loads(base64.urlsafe_b64decode(jwt_parts[1] + "=" * (-len(jwt_parts[1]) % 4)))
    payload["realm_access"]["roles"] = ["operator"]
    jwt_parts[1] = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip("=")
    check("tampered role claims rejected by signature", call("/api/requests",headers={"Authorization":"Bearer " + ".".join(jwt_parts)})[0] == 401)
    check("anonymous request rejected", call("/api/requests", role=None)[0] == 401)
    check("invalid credential rejected", call("/api/requests", headers={"Authorization": "Bearer invalid"})[0] == 401)
    check("operator metrics protected", call("/actuator/metrics")[0] == 403)
    check("operator metrics available", call("/actuator/metrics", role="operator")[0] == 200)
    code, page, headers = call("/", role=None)
    check("console and security headers", code == 200 and "Content-Security-Policy" in headers and "Service Request Platform" in page)
    check("OpenAPI contract available", call("/openapi.yaml", role=None)[0] == 200)
    draft = {"title": "Investigate checkout latency", "description": "Trace service degradation", "priority": "HIGH"}
    key = str(uuid.uuid4())
    code, data, headers = call("/api/requests", "POST", draft, headers={"Idempotency-Key": key})
    check("created with location and etag", code == 201 and headers["ETag"] == '"0"' and "Location" in headers)
    request = data["request"]
    path = "/api/requests/" + request["id"]
    check("request owner is authenticated OIDC subject", request["owner"] == requester_identity["subject"])
    check("second requester cannot access first requester data", call(path,role="second-requester")[0] == 404 and call(path + "/history",role="second-requester")[0] == 404)
    from datetime import datetime
    hours = (datetime.fromisoformat(request["slaDueAt"].replace("Z", "+00:00")) - datetime.fromisoformat(request["createdAt"].replace("Z", "+00:00"))).total_seconds() / 3600
    check("high priority has eight hour SLA", hours == 8)
    code, replay, _ = call("/api/requests", "POST", draft, headers={"Idempotency-Key": key})
    check("replay preserves request identity", code == 200 and replay["request"]["id"] == request["id"])
    check("key reuse with changed content conflicts", call("/api/requests", "POST", {**draft, "priority": "LOW"}, headers={"Idempotency-Key": key})[0] == 409)
    check("requester cannot transition", call(path + "/transitions", "POST", {"status": "IN_PROGRESS"}, headers={"If-Match": '"0"'})[0] == 403)
    check("invalid lifecycle transition rejected", call(path + "/transitions", "POST", {"status": "CLOSED"}, role="operator", headers={"If-Match": '"0"'})[0] == 400)
    check("missing concurrency header rejected", call(path + "/transitions", "POST", {"status": "IN_PROGRESS"}, role="operator")[0] == 400)
    race_key = str(uuid.uuid4())
    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(lambda _: call("/api/requests", "POST", draft, headers={"Idempotency-Key": race_key}), range(8)))
    check("postgres concurrent creation has one winner", sorted(r[0] for r in results) == [200] * 7 + [201])
    ids = {r[1]["request"]["id"] for r in results}
    check("concurrent retries produce one identity", len(ids) == 1)
    check("concurrent creation produces one audit", len(call("/api/requests/" + ids.pop() + "/history")[1]) == 1)
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda _: call(path + "/transitions", "POST", {"status": "IN_PROGRESS"}, role="operator", headers={"If-Match": '"0"'}), range(2)))
    check("postgres concurrent edits have one winner", sorted(r[0] for r in results) == [200, 409])
    check("transition and audit commit together", len(call(path + "/history")[1]) == 2)
    for version, status in [(1, "RESOLVED"), (2, "IN_PROGRESS"), (3, "RESOLVED"), (4, "CLOSED")]:
        check("workflow " + str(version) + " to " + status, call(path + "/transitions", "POST", {"status": status}, role="operator", headers={"If-Match": f'"{version}"'})[0] == 200)
    check("closed requests are terminal", call(path + "/transitions", "POST", {"status": "IN_PROGRESS"}, role="operator", headers={"If-Match": '"5"'})[0] == 400)
    operator_code, operator_request, _ = call("/api/requests", "POST", draft, role="operator", headers={"Idempotency-Key": key})
    check("keys are principal scoped", operator_code == 201 and operator_request["request"]["id"] != request["id"])
    hidden = "/api/requests/" + operator_request["request"]["id"]
    check("requester cannot see operator request", call(hidden)[0] == 404 and call(hidden + "/history")[0] == 404)
    check("list is principal scoped", all(r["request"]["owner"] == requester_identity["subject"] for r in call("/api/requests")[1]))
    check("invalid pagination rejected", call("/api/requests?limit=101")[0] == 400)
    check("invalid fields rejected", call("/api/requests", "POST", {**draft, "title": " "}, headers={"Idempotency-Key": str(uuid.uuid4())})[0] == 400)
    code, template_request, _ = call("/api/templates/incident/requests", "POST", {"title": "Investigate inventory interruption"}, headers={"Idempotency-Key": str(uuid.uuid4())})
    check("template instantiates its SLA and defaults", code == 201 and template_request["request"]["priority"] == "HIGH")
    # Exercise a genuine PostgreSQL constraint failure AFTER the status update.
    # A duplicate audit revision forces the complete transition transaction to roll back.
    rollback_request = template_request["request"]
    sql = "INSERT INTO request_audit(request_id,version,action,actor,occurred_at) VALUES ('" + rollback_request["id"] + "',1,'FAULT_TEST','test',CURRENT_TIMESTAMP)"
    compose("exec", "-T", "database", "psql", "-v", "ON_ERROR_STOP=1", "-U", "requests", "-d", "requests", "-c", sql)
    rollback_path = "/api/requests/" + rollback_request["id"]
    check("audit write failure rejects transaction", call(rollback_path + "/transitions", "POST", {"status": "IN_PROGRESS"}, role="operator", headers={"If-Match": '"0"'})[0] == 500)
    check("audit failure rolls back request update", call(rollback_path)[1]["request"]["version"] == 0)
    compose("exec", "-T", "database", "psql", "-v", "ON_ERROR_STOP=1", "-U", "requests", "-d", "requests", "-c", "DELETE FROM request_audit WHERE request_id = '" + rollback_request["id"] + "' AND version = 1")
    assignment = {"operatorSubject":operator_identity["subject"]}
    check("requester cannot assign", call(rollback_path + "/assignment","POST",assignment,headers={"If-Match":'"0"'})[0] == 403)
    check("unregistered assignment target rejected", call(rollback_path + "/assignment","POST",{"operatorSubject":"untrusted-subject"},role="operator",headers={"If-Match":'"0"'})[0] == 400)
    code, assigned, _ = call(rollback_path + "/assignment","POST",assignment,role="operator",headers={"If-Match":'"0"'})
    check("request assigned to registered operator subject", code == 200 and assigned["request"]["assignedTo"] == operator_identity["subject"])
    check("stale assignment rejected", call(rollback_path + "/assignment","POST",assignment,role="operator",headers={"If-Match":'"0"'})[0] == 409)
    history = call(rollback_path + "/history")[1]
    check("assignment audit records authenticated actor and target", history[-1]["actor"] == operator_identity["subject"] and history[-1]["detail"] == operator_identity["subject"])
    check("requester cannot read operational alerts", call("/api/alerts")[0] == 403)
    compose("exec","-T","database","psql","-v","ON_ERROR_STOP=1","-U","requests","-d","requests","-c","UPDATE service_request SET created_at = CURRENT_TIMESTAMP - INTERVAL '10 hours', sla_due_at = CURRENT_TIMESTAMP - INTERVAL '2 hours' WHERE id = '" + rollback_request["id"] + "'")
    until = time.monotonic() + 35
    detected = []
    while time.monotonic() < until:
        detected = [a for a in call("/api/alerts",role="operator")[1] if a["requestId"] == rollback_request["id"]]
        if detected: break
        time.sleep(1)
    check("SLA monitor persists one active overdue alert", len(detected) == 1 and detected[0]["assignedTo"] == operator_identity["subject"])
    compose("restart", "api")
    check("application recovers after restart", ready())
    check("request and audit survive application restart", call(path)[1]["request"]["version"] == 5 and len(call(path + "/history")[1]) == 6)
    check("assignment and overdue alert survive restart", call(rollback_path)[1]["request"]["assignedTo"] == operator_identity["subject"] and len([a for a in call("/api/alerts",role="operator")[1] if a["requestId"] == rollback_request["id"]]) == 1)
    compose("stop", "database")
    try:
        check("readiness fails when database is unavailable", ready(expected=503, deadline=40))
        check("liveness remains up during database outage", call("/actuator/health/liveness", role=None)[0] == 200)
    finally:
        compose("start", "database")
    check("readiness recovers with database", ready())
    check("requests survive database restart", call(path)[1]["request"]["status"] == "CLOSED")
    code, replay, _ = call("/api/requests", "POST", draft, headers={"Idempotency-Key": key})
    check("idempotency survives restart", code == 200 and replay["request"]["id"] == request["id"])
    check("terminal request suppresses active SLA alert", call(rollback_path + "/transitions","POST",{"status":"CANCELLED"},role="operator",headers={"If-Match":'"1"'})[0] == 200 and not any(a["requestId"] == rollback_request["id"] for a in call("/api/alerts",role="operator")[1]))
    report = {"result": "passed", "checks": len(checks), "scenarios": checks}
    (ROOT / "artifacts").mkdir(exist_ok=True)
    (ROOT / "artifacts" / "e2e.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Verified {len(checks)} HTTP/PostgreSQL scenarios.")


if __name__ == "__main__":
    main()
