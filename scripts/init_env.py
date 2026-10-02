"""Generate local credentials once; never print or overwrite secrets."""
from pathlib import Path
import os
import secrets

root = Path(__file__).resolve().parents[1]
path = root / ".env"
defaults = {"APP_PORT": "18110", "IDENTITY_PORT": "18112"}
for key in ("DATABASE_PASSWORD", "REQUESTER_TOKEN", "OPERATOR_TOKEN", "IDENTITY_ADMIN_PASSWORD",
            "IDENTITY_REQUESTER_PASSWORD", "IDENTITY_SECOND_REQUESTER_PASSWORD", "IDENTITY_OPERATOR_PASSWORD"):
    defaults[key] = secrets.token_hex(32)
content = "".join(f"{key}={value}\n" for key, value in defaults.items())
try:
    descriptor = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
except FileExistsError:
    existing = path.read_text(encoding="utf-8")
    present = {line.split("=",1)[0] for line in existing.splitlines() if "=" in line}
    additions = "".join(f"{key}={value}\n" for key,value in defaults.items() if key not in present)
    if additions:
        with path.open("a",encoding="utf-8",newline="\n") as output:
            if existing and not existing.endswith("\n"): output.write("\n")
            output.write(additions)
    print("Existing credentials preserved; missing settings added without printing secrets.")
else:
    with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as output:
        output.write(content)
    print("Local .env generated. Credentials were not printed.")
