#!/usr/bin/env python3
"""Deterministically pack the finite production author sources; never install rules."""
import argparse
import io
import json
from pathlib import Path
import zipfile


PACKAGES = ("srd51-runtime", "srd51-characters", "srd51-magic", "dm-runtime-house-rules",
            "dm-runtime-check-variant")
AUTHOR_CANDIDATES = ("dm-runtime-monster-variant", "dm-runtime-wolf-variant")
EXISTING_GUIDE_PACKAGES = ("srd51-runtime", "srd51-characters", "dm-runtime-house-rules")


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
    parser.add_argument("--output-dir", type=Path, help="write candidate bundles to a separate directory")
    parser.add_argument("packages", nargs="*", metavar="PACKAGE", help="package directory names (default: all)")
    args = parser.parse_args()
    selected = args.packages or PACKAGES
    for name in selected:
        if name not in PACKAGES + AUTHOR_CANDIDATES:
            parser.error(f"Unknown package: {name}")
        if name in AUTHOR_CANDIDATES and args.output_dir is None:
            parser.error(f"{name} is an author candidate. Supply --output-dir.")
    source = Path(__file__).resolve().parent
    output = args.output_dir or source.parent.parent / "src/main/resources/storage/rules"
    notice = (source / "notice.md").read_bytes()
    if not args.check:
        output.mkdir(parents=True, exist_ok=True)
    for name in selected:
        directory = source / name
        guide = directory / "package-guide.md"
        if not guide.is_file():
            # Keep unchanged releases reproducible with the guide they originally shipped.
            guide = source / ("base-package-guide.md" if name in EXISTING_GUIDE_PACKAGES else "package-guide.md")
        expected = archive(directory, notice, guide.read_bytes())
        target = output / (name + ".zip")
        if target.is_file() and target.read_bytes() == expected:
            continue
        if args.check:
            raise SystemExit("Bundled resource differs from author source: " + str(target))
        target.write_bytes(expected)
    action = "Verified" if args.check else "Built"
    print(f"{action} {len(selected)} deterministic rule bundles")


if __name__ == "__main__":
    main()
