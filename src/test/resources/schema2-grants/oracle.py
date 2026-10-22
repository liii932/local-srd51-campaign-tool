#!/usr/bin/env python3
"""Reproduce the finite grant vectors using only Python's standard library.

The handwritten author JSON is the input. No expected file, Java code or Java output
is used to calculate an expected value. Default mode checks every fixed byte;
--write explicitly replaces this fixture directory's generated files.
"""

import argparse
from copy import deepcopy
import hashlib
from io import BytesIO
import json
from pathlib import Path
import zipfile


ROOT = Path(__file__).resolve().parent
MODULES = ("grants_base", "grants_extension", "grants_compatibility")
DOMAIN = b"local-dnd/schema2-production/1/"


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True,
                      separators=(",", ":")).encode("utf-8")


def digest(tag, value):
    return hashlib.sha256(DOMAIN + tag.encode("ascii") + b"\0" + canonical(value)).hexdigest()


def identity_key(identity):
    if identity["kind"] == "rule":
        return ("rule", identity["module_key"], identity["rule_type"], identity["rule_key"])
    return ("entry", *identity_key(identity["rule"])[1:], identity["collection_key"],
            identity["module_key"], identity["entry_key"])


def change_key(change):
    return (*identity_key(change["target"]), change.get("field", "enabled"))


def raw_zip(files, manifest):
    output = BytesIO()
    paths = ["author-package.json"] + [row["path"] for row in manifest["files"]]
    with zipfile.ZipFile(output, "w") as archive:
        contents = [("NOTICE", b"Finite schema-2 proficiency grants fixture. No qualification.\n")]
        contents += [(path, files[path]) for path in reversed(paths)]
        for path, content in contents:
            entry = zipfile.ZipInfo(path, (2001, 2, 3, 4, 5, 6))
            entry.create_system = 3
            entry.external_attr = 0o100644 << 16
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry, content)
    return output.getvalue()


def build():
    generated, members, rules, entries, chains = {}, [], {}, {}, {}
    hashes = {"raw": {}, "logic": {}}
    for load_index, module in enumerate(MODULES):
        folder = ROOT / module
        header_bytes = (folder / "author-package.json").read_bytes()
        header = json.loads(header_bytes)
        files = {"author-package.json": header_bytes}
        rows = {kind: [] for kind in ("definitions", "entries", "changes")}
        for declaration in header["files"]:
            path = declaration["path"]
            files[path] = (folder / path).read_bytes()
            rows[declaration["kind"]] += [(row, path, index)
                for index, row in enumerate(json.loads(files[path]))]
        assert all(content.endswith(b"\n") for content in files.values())
        transport = raw_zip(files, header)
        raw_sha = hashlib.sha256(transport).hexdigest()
        generated[module + ".zip.hex"] = transport.hex().encode("ascii") + b"\n"

        logic = {key: deepcopy(header[key]) for key in ("author_schema_version", "module_key",
            "release_version", "role", "dependencies", "compatible_combinations", "load_order_constraints")}
        logic["dependencies"].sort(key=lambda row: row["module_key"])
        for dependency in logic["dependencies"]:
            dependency["supported_versions"].sort()
        logic["compatible_combinations"].sort(key=canonical)
        logic["load_order_constraints"].sort(key=lambda row: (row["before"], row["after"]))
        for kind in rows:
            order = change_key if kind == "changes" else lambda row: identity_key(row["identity"])
            logic[kind] = sorted((row for row, _, _ in rows[kind]), key=order)
        logical_sha = digest("module-logic", logic)
        generated["expected/" + module + "-logic.json"] = canonical(logic)
        hashes["raw"][module], hashes["logic"][module] = raw_sha, logical_sha
        members.append(dict(module_key=module, release_version=header["release_version"],
            role=header["role"], raw_zip_sha256=raw_sha, logical_sha256=logical_sha))

        def append_step(target, field, state, path, pointer, op, origin):
            key = (*identity_key(target), field)
            chain = chains.setdefault(key, dict(target=target, field=field, steps=[]))
            steps = chain["steps"]
            before = deepcopy(steps[-1]["after"]) if steps else {"state": "uninitialized"}
            steps.append(dict(op=op, before=before, after=deepcopy(state), load_index=load_index,
                provider=dict(module_key=module, release_version=header["release_version"]),
                author_location=dict(raw_zip_sha256=raw_sha, path=path, pointer=pointer), origin=origin))

        previous_objects = rules.keys() | entries.keys()
        for kind, destination in (("definitions", rules), ("entries", entries)):
            for author_row, path, index in rows[kind]:
                row = deepcopy(author_row)
                target = row["identity"]
                fields = {key: {"state": "present", "value": value}
                    for key, value in row.items() if key != "identity"}
                fields.setdefault("author_note", {"state": "absent"})
                fields["enabled"] = {"state": "present", "value": True}
                for field, state in fields.items():
                    origin = "default_enabled" if field == "enabled" else (
                        "omitted_optional" if state["state"] == "absent" else "declared")
                    pointer = f"/{index}" + (f"/{field}" if origin == "declared" else "")
                    append_step(target, field, state, path, pointer, "initial", origin)
                row["enabled"] = True
                destination[identity_key(target)] = row
        for change, path, index in rows["changes"]:
            target, op = change["target"], change["op"]
            assert identity_key(target) in previous_objects, (
                f"{module}/{path}/{index}: changes require a preceding package declaration")
            field = change.get("field", "enabled")
            state = {"state": "absent"} if op == "clear_field" else {
                "state": "present", "value": change["value"]}
            append_step(target, field, state, path, f"/{index}", op, "explicit")
            row = (rules if target["kind"] == "rule" else entries)[identity_key(target)]
            if op == "clear_field":
                del row[field]
            else:
                row[field] = deepcopy(change["value"])

    configuration = dict(author_schema_version=2, canonical_profile="schema2-production-json-1",
        composition_algorithm="schema2-production-projection-1", members=members)
    final_rules = dict(rules=[rules[key] for key in sorted(rules)],
        entries=[entries[key] for key in sorted(entries)])
    hashes["config"] = digest("composition-config", configuration)
    hashes["rules"] = digest("final-rules", final_rules)
    full_chain = dict(composition_sha256=hashes["config"], chains=[chains[key] for key in sorted(chains)])
    hashes["chain"] = digest("full-chain", full_chain)
    assert (len(rules), len(entries), len(chains), sum(len(c["steps"]) for c in chains.values())) == (15, 18, 135, 152)
    for name, value in (("configuration", configuration), ("final-rules", final_rules),
                        ("chains", full_chain), ("digests", hashes)):
        generated["expected/" + name + ".json"] = canonical(value)
    return generated


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="replace generated grant fixtures")
    options = parser.parse_args()
    generated = build()
    mismatches = []
    for name, expected in generated.items():
        path = ROOT / name
        if options.write:
            path.write_bytes(expected)
        elif not path.exists() or path.read_bytes() != expected:
            mismatches.append(name)
    if mismatches:
        raise SystemExit("Grant oracle byte mismatch: " + ", ".join(mismatches))
    print(f"Grant oracle {'wrote' if options.write else 'verified'} {len(generated)} fixed artifacts.")


if __name__ == "__main__":
    main()
