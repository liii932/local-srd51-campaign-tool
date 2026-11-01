#!/usr/bin/env python3
"""Deterministically pack the finite production author sources; never install rules."""
import argparse
import io
import json
from pathlib import Path
import zipfile


PACKAGES = ("srd51-runtime", "srd51-characters", "srd51-magic", "dm-runtime-house-rules")


def archive(directory: Path, notice: bytes, guide: bytes) -> bytes:
    manifest = json.loads((directory / "author-package.json").read_text(encoding="utf-8"))
    members = {"author-package.json": (directory / "author-package.json").read_bytes(),
               "NOTICE": notice, "README.md": guide}
    for declaration in manifest["files"]:
        path = declaration["path"]
        if Path(path).name != path or path in members:
            raise ValueError("Invalid or duplicate bundled member: " + path)
        members[path] = (directory / path).read_bytes()
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_STORED, allowZip64=False) as zipped:
        for name, body in sorted(members.items()):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            info.compress_type = zipfile.ZIP_STORED
            zipped.writestr(info, body)
    return output.getvalue()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    source = Path(__file__).resolve().parent
    output = source.parent.parent / "src/main/resources/storage/rules"
    notice = (source / "notice.md").read_bytes()
    guide = (source / "package-guide.md").read_bytes()
    if not args.check:
        output.mkdir(parents=True, exist_ok=True)
    for name in PACKAGES:
        expected = archive(source / name, notice, guide)
        target = output / (name + ".zip")
        if args.check:
            if not target.is_file() or target.read_bytes() != expected:
                raise SystemExit("Bundled resource differs from author source: " + str(target))
        else:
            target.write_bytes(expected)
    print("Verified four deterministic rule bundles" if args.check else "Built four finite rule bundles")


if __name__ == "__main__":
    main()
