"""Generate local credentials once; never print or overwrite secrets."""
from pathlib import Path
import os
import secrets

root = Path(__file__).resolve().parents[1]
path = root / ".env"
content = "APP_PORT=18110\n" + "".join(
    f"{key}={secrets.token_hex(32)}\n"
    for key in ("DATABASE_PASSWORD", "REQUESTER_TOKEN", "OPERATOR_TOKEN")
)
try:
    descriptor = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
except FileExistsError:
    print("Existing .env preserved.")
else:
    with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as output:
        output.write(content)
    print("Local .env generated. Credentials were not printed.")
