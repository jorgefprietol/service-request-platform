"""Require the image SBOM to identify every packaged runtime Java library."""
from pathlib import Path
from urllib.parse import unquote
from zipfile import ZipFile
import json

root = Path(__file__).resolve().parents[1]
with ZipFile(root / "artifacts/application.jar") as jar:
    packaged = {Path(name).name for name in jar.namelist()
                if name.startswith("BOOT-INF/lib/") and name.endswith(".jar")}
sbom = json.loads((root / "artifacts/security/sbom.cdx.json").read_text(encoding="utf-8"))
identified = set()
for component in sbom.get("components", []):
    purl = component.get("purl", "")
    if purl.startswith("pkg:maven/") and "@" in purl:
        coordinate, version = purl.split("?", 1)[0].rsplit("@", 1)
        name = unquote(coordinate.rsplit("/", 1)[1])
        identified.add(f"{name}-{unquote(version)}.jar")
missing = sorted(packaged - identified)
if not packaged or missing:
    raise SystemExit("Incomplete runtime SBOM; unidentified libraries: " + str(missing))
report = {"packagedJavaLibraries": len(packaged), "identifiedJavaLibraries": len(packaged),
          "result": "passed"}
(root / "artifacts/security/sbom-coverage.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(f"SBOM identifies all {len(packaged)} packaged runtime Java libraries.")
