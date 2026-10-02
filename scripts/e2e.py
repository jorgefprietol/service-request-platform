"""Verify the real HTTP/PostgreSQL stack, races, restart recovery and readiness.

Uses only Python's standard library. Writes no credentials to evidence.
Run against the isolated development or CI Compose project.
"""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.request import Request, urlopen
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


def call(path, method="GET", data=None, role="requester", headers=None):
    request_headers = {"Content-Type": "application/json"}
    if role:
        request_headers["Authorization"] = "Bearer " + env[role.upper() + "_TOKEN"]
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
    check("list is principal scoped", all(r["request"]["owner"] == "requester" for r in call("/api/requests")[1]))
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
    compose("restart", "api")
    check("application recovers after restart", ready())
    check("request and audit survive application restart", call(path)[1]["request"]["version"] == 5 and len(call(path + "/history")[1]) == 6)
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
    report = {"result": "passed", "checks": len(checks), "scenarios": checks}
    (ROOT / "artifacts").mkdir(exist_ok=True)
    (ROOT / "artifacts" / "e2e.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Verified {len(checks)} HTTP/PostgreSQL scenarios.")


if __name__ == "__main__":
    main()
