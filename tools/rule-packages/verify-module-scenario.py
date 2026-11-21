#!/usr/bin/env python3
"""Run selected rule-package cases against the mechanisms in a supplied application WAR."""

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
from tempfile import TemporaryDirectory
from time import perf_counter
from zipfile import BadZipFile, ZipFile


CLASSES = "WEB-INF/classes/"
LIBRARIES = "WEB-INF/lib/"
BUNDLE_NAME = re.compile(r"[a-z0-9][a-z0-9-]*")
DRIVER_NAME = re.compile(r"[A-Za-z][A-Za-z0-9_]*")


def run(war: Path, scenario: Path, modules: list[str], cases: list[str]) -> int:
    with scenario.open(encoding="utf-8") as source:
        document = json.load(source)
    if not isinstance(document, dict):
        raise ValueError("The scenario must be a JSON object")
    if not modules:
        modules = document.get("bundles")
        if not isinstance(modules, list) or not modules or any(
            not isinstance(name, str) or not BUNDLE_NAME.fullmatch(name) for name in modules
        ):
            raise ValueError("The scenario requires an ordered list of bundled package names")
        modules = ["@" + name for name in modules]

    tools = Path(__file__).resolve().parent
    sources = [tools / "ModuleScenario.java"]
    main_class = document.get("driver", "ModuleScenario")
    if not isinstance(main_class, str) or not DRIVER_NAME.fullmatch(main_class):
        raise ValueError("driver must name the Java class beside the scenario JSON")
    if "driver" in document:
        driver = scenario.with_name(f"{main_class}.java").resolve()
        if not driver.is_file():
            raise ValueError(f"Scenario driver does not exist: {driver}")
        sources.append(driver)
    with TemporaryDirectory(prefix="dnd-module-") as directory:
        work = Path(directory)
        start = perf_counter()
        with ZipFile(war) as archive:
            class_files = [item for item in archive.infolist()
                           if item.filename.startswith(CLASSES) and not item.is_dir()]
            jars = [item for item in archive.infolist()
                    if item.filename.startswith(LIBRARIES) and item.filename.endswith(".jar")
                    and "/" not in item.filename[len(LIBRARIES):]]
            if not class_files or not jars:
                raise ValueError("Expected a WAR containing the application classes and runtime libraries")
            selected = class_files + jars
            names = [item.filename for item in selected]
            if len(names) != len(set(names)) or any(
                "\\" in name or ":" in name or any(part in ("", ".", "..") for part in name.split("/"))
                for name in names
            ):
                raise ValueError("WAR contains a duplicate or unsafe classpath entry")
            archive.extractall(work, selected)
        extraction_ms = (perf_counter() - start) * 1000

        packages = []
        for module in modules:
            if module.startswith("@"):
                name = module[1:]
                if not BUNDLE_NAME.fullmatch(name):
                    raise ValueError(f"Invalid bundled package name: {name!r}")
                packages.append(Path(CLASSES) / "storage" / "rules" / f"{name}.zip")
            else:
                packages.append(Path(module).resolve())
        for package in packages:
            if not (work / package).is_file():
                raise ValueError(f"Rule-package ZIP does not exist: {package}")

        classpath = os.pathsep.join(str(path) for path in [work / CLASSES, *(work / jar.filename for jar in jars)])
        compiled = work / "scenarios"
        compiled.mkdir()
        start = perf_counter()
        subprocess.run(["javac", "--release", "21", "-encoding", "UTF-8", "-cp", classpath, "-d", compiled, *sources],
                       cwd=work, timeout=60, check=True)
        compilation_ms = (perf_counter() - start) * 1000
        print(f"WAR ready ({extraction_ms:.0f} ms extract, {compilation_ms:.0f} ms compile)", flush=True)
        return subprocess.run(
            ["java", "-cp", os.pathsep.join([classpath, str(compiled)]),
             main_class, scenario.resolve(), str(len(packages)), *packages, *cases],
            cwd=work, timeout=60, check=False,
        ).returncode


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--war", required=True, type=Path, help="existing application WAR")
    parser.add_argument("--module", action="append", default=[], metavar="ZIP_OR_@BUNDLE",
                        help="replace scenario bundles with an explicit ordered list; repeat per member")
    parser.add_argument("--case", action="append", default=[], metavar="ID",
                        help="run a named case; repeat for a batch, or omit to run the entire scenario file")
    parser.add_argument("scenario", type=Path, help="JSON file with a composition, case IDs and hand-written expectations")
    args = parser.parse_args()
    try:
        return run(args.war, args.scenario, args.module, args.case)
    except subprocess.CalledProcessError:
        parser.exit(1, "Module scenario could not compile against the selected WAR\n")
    except (BadZipFile, OSError, ValueError, subprocess.TimeoutExpired) as failure:
        parser.exit(1, f"Module scenario could not run: {failure}\n")


if __name__ == "__main__":
    raise SystemExit(main())
