#!/usr/bin/env python3
"""Audit a SQLite application WAR against its corresponding source checkout."""

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
from xml.etree import ElementTree
from zipfile import BadZipFile, ZipFile


ROOT = Path(__file__).resolve().parents[2]
CLASSES = "WEB-INF/classes/"
LIBS = "WEB-INF/lib/"
RULES = CLASSES + "storage/rules/"
MANIFEST = RULES + "bundled-members.tsv"
MAVEN_METADATA = "META-INF/maven/com.dndtool/dnd-tool-se/"
GENERATOR = "com/dndtool/service/storage/BundledRuleManifestGenerator"
RULE_FILES = {
    "dm-runtime-check-variant.zip", "dm-runtime-house-rules.zip",
    "srd51-characters.zip", "srd51-magic.zip", "srd51-runtime.zip",
}
REQUIRED_WEB = {
    "WEB-INF/web.xml", "WEB-INF/storage.html", "WEB-INF/player.html",
    "host/assets/host-storage.js", "host/assets/host-storage.css",
    "host/assets/host-player-participation.js", "host/assets/host-player-participation.css",
    "host/assets/local-invite-qr.js", "player/assets/player.js", "player/assets/player.css",
}
REQUIRED_CLASSES = {
    "persistence/storage/CampaignDomainRepository", "persistence/storage/LocalRuleLibraryLedger",
    "service/CampaignDomainEngine", "service/Schema2CharacterMaterial",
    "service/Schema2CharacterRequest", "service/Schema2CombatRequest",
    "service/Schema2CharacterRuntime", "service/Schema2CombatHistory",
    "service/Schema2PreparationIdentity", "service/Schema2FeatureSupport",
    "service/Schema2InitialCharacterPlanService$InitialPlan", "service/Schema2InitialCharacterPlanService$Plan",
    "service/storage/BundledCampaignRules", "service/storage/CampaignCodec",
    "service/storage/CampaignDomainCodec", "service/storage/CampaignRuleContext",
    "service/storage/CampaignRuleSupport", "service/storage/CampaignStorageService",
    "service/storage/LocalRuleLibrary", "service/storage/PlayerGatewayService",
    "service/storage/PlayerCampaignProjection", "service/storage/PlayerCandidateFactory",
}
SERVLET_ROUTES = {
    "HealthServlet": {"/health"},
    "HostHomeServlet": {"", "/host", "/host/"},
    "HostStorageServlet": {"/host/storage", "/api/host/storage/*"},
    "HostPlayerServlet": {"/api/host/storage/players/*"},
    "PlayerServlet": {"/player", "/player/", "/api/player/*"},
}
FILTERS = ["PlayerBoundaryFilter", "HostBoundaryFilter", "HostRequestSecurityFilter"]
FILTER_ROUTES = [
    {"/*"},
    {"/", "/host/*", "/api/host/*"},
    {"/host", "/host/", "/host/storage", "/api/host/storage/*"},
]
FILTER_DISPATCHERS = [{"REQUEST", "FORWARD", "INCLUDE", "ERROR"}, {"REQUEST"}, {"REQUEST"}]
REMOVED_JDBC = frozenset("""
JdbcCampaignArchiveV2CharacterStateImportRepository JdbcCampaignArchiveV2CharacterStateRepository
JdbcCampaignModuleBindingRepository JdbcCharacterAggregateMutationRepository JdbcCharacterCardRepository
JdbcCharacterModuleBindingRepository JdbcCharacterProficiencyGrantRepository JdbcLevelAdvancementRepository
JdbcLevelOneCharacterCreationRepository JdbcModuleCatalogRepository JdbcRuntimeCharacterCatalogRepository
JdbcRuntimeLanguageSnapshotRepository JdbcSchema2CharacterRuntimeRepository JdbcSchema2CharacterSourceRepository
JdbcSchema2CombatRuntimeRepository JdbcSchema2CombatSnapshotRows JdbcSchema2CombatSourceRows
JdbcSchema2EquipmentSnapshotRows JdbcSchema2ExecutionSourceRows JdbcSchema2GrowthSnapshotRows
JdbcSchema2InitialCharacterPlanner JdbcSchema2InitialPlanRepository JdbcSchema2MonsterAtoms
JdbcSchema2MonsterBreathRows JdbcSchema2MonsterSnapshotRows JdbcSchema2MonsterSourceRows
JdbcSchema2RuntimeSnapshotRepository JdbcSchema2SnapshotControlRepository JdbcSchema2SpellSnapshotRows
JdbcSchema2SpellSourceRows JdbcSchema2WizardSnapshotRows JdbcSchema2WizardSourceRows
JdbcSourceLanguageRepository JdbcSourceSchema2Repository
""".split())
FORBIDDEN_LIBRARY = re.compile(
    r"(?:^|/)(?:com/mysql|org/mariadb|org/apache/tomcat/dbcp|org/flywaydb|org/junit|org/apache/catalina)(?:/|$)",
    re.IGNORECASE,
)
FORBIDDEN_LEGACY = re.compile(
    r"(?:^|/)(?:db/migration|database|offline)(?:/|$)"
    r"|(?:^|/)(?:SchemaMigrations|RuleSchemaMigrations|RuleDatabaseSchemaVerifier"
    r"|DatabaseSchemaVerifier|DatabaseDiagnostics|BusinessWriteTransactions|CampaignArchiveV2[A-Za-z0-9]*)[.$/]"
    r"|(?:^|/)(?:mysql|mariadb|tomcat-dbcp|tomcat-embed|jakarta.servlet-api|junit|flyway)[-.:]",
    re.IGNORECASE,
)
SENSITIVE_PATH = re.compile(
    r"(^|/)(?:\.git|\.env(?:\.[^/]+)?|\.agents|\.codex|test-classes|fixtures|logs?|backups?|saves?|evidence"
    r"|server\.xml|context\.xml|root\.xml|setenv\.[^/]+|credentials?\.[^/]+|secrets?\.[^/]+)(/|$)"
    r"|\.(?:sql|p12|pfx|jks|keystore|key|pem|cer|crt|db(?:-journal|-wal|-shm)?|sqlite3?|log|bak|etl|dmp)$"
    r"|(?-i:Test|IT|TestProcess|LoopLauncher)(?:\$[^/]*)?\.class$",
    re.IGNORECASE,
)
SENSITIVE_CONTENT = re.compile(
    rb"jdbc:(?:mysql|mariadb):|jdbc/DndToolSE|com[./]mysql[./]cj|org[./]mariadb[./]jdbc"
    rb"|-----BEGIN [A-Z ]*(?:PRIVATE KEY|CERTIFICATE)-----",
    re.IGNORECASE,
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def forbidden_entry(name: str) -> bool:
    removed_repository = (name.endswith(".class")
                          and Path(name).stem.split("$", 1)[0] in REMOVED_JDBC)
    return removed_repository or bool(FORBIDDEN_LEGACY.search(name) or FORBIDDEN_LIBRARY.search(name)
                                      or SENSITIVE_PATH.search(name))


def members(archive: ZipFile) -> set[str]:
    names = set()
    for entry in archive.infolist():
        name = entry.filename
        parts = name.rstrip("/").split("/")
        require(
            name not in names and "\\" not in name and ":" not in name
            and all(part not in {"", ".", ".."} for part in parts)
            and not stat.S_ISLNK(entry.external_attr >> 16),
            f"duplicate or unsafe ZIP entry {name!r}",
        )
        names.add(name)
    return names


def descriptor(archive: ZipFile) -> None:
    root = ElementTree.fromstring(archive.read("WEB-INF/web.xml"))
    namespace = {"w": "https://jakarta.ee/xml/ns/jakartaee"}
    find = lambda parent, tag: parent.findall("w:" + tag, namespace)
    value = lambda parent, tag: parent.findtext("w:" + tag, default="", namespaces=namespace)
    require(root.get("metadata-complete") == "true", "production descriptor must register the Web surface")
    require(not any(find(root, kind) for kind in ("resource-ref", "resource-env-ref", "env-entry")),
            "unexpected JNDI resource in production descriptor")
    listener = "com.dndtool.web.StorageApplicationListener"
    require([node.text for node in root.findall("w:listener/w:listener-class", namespace)] == [listener],
            "unexpected application listener")
    expected_servlets = {name: f"com.dndtool.web.{name}" for name in SERVLET_ROUTES}
    servlets = [(value(node, "servlet-name"), value(node, "servlet-class")) for node in find(root, "servlet")]
    require(len(servlets) == len(expected_servlets) and dict(servlets) == expected_servlets,
            "unexpected servlet registration")
    mappings = [(value(node, "servlet-name"), {url.text or "" for url in find(node, "url-pattern")})
                for node in find(root, "servlet-mapping")]
    require(len(mappings) == len(SERVLET_ROUTES) and dict(mappings) == SERVLET_ROUTES,
            "unexpected servlet routes")
    for element in ("filter", "filter-mapping"):
        require([value(node, "filter-name") for node in find(root, element)] == FILTERS,
                f"unexpected {element} names or order")
    filter_classes = [f"com.dndtool.security.{name}" for name in FILTERS]
    require([value(node, "filter-class") for node in find(root, "filter")] == filter_classes, "unexpected filter classes")
    mapped_filters = find(root, "filter-mapping")
    require([{entry.text for entry in find(node, "url-pattern")} for node in mapped_filters] == FILTER_ROUTES,
            "unexpected filter routes")
    require([{entry.text for entry in find(node, "dispatcher")} for node in mapped_filters] == FILTER_DISPATCHERS,
            "unexpected filter dispatchers")
    for name in [listener, *expected_servlets.values(), *filter_classes]:
        archive.getinfo(CLASSES + name.replace(".", "/") + ".class")


def source_inputs(archive: ZipFile, files: set[str], jars: set[str]) -> set[str]:
    build_resources = ROOT / "src/main/resources"
    web_root = ROOT / "src/main/webapp"
    source_resources = {CLASSES + p.relative_to(build_resources).as_posix(): p
                        for p in build_resources.rglob("*") if p.is_file()}
    web_resources = {p.relative_to(web_root).as_posix(): p for p in web_root.rglob("*") if p.is_file()}
    require(REQUIRED_WEB <= files, f"missing Web entries {sorted(REQUIRED_WEB - files)}")
    required_rules = {RULES + name for name in RULE_FILES}
    require({name for name in files if name.startswith(RULES)} == required_rules | {MANIFEST},
            "unexpected rule artifacts")
    require(CLASSES + "storage/campaign-schema.json" in files, "missing SQLite structure declaration")
    for name, path in sorted((source_resources | web_resources).items()):
        require(name in files and archive.read(name) == path.read_bytes(), f"source bytes differ for {name}")
    source_root = ROOT / "src/main/java"
    available_sources = {
        path.relative_to(source_root).as_posix().removesuffix(".java")
        for path in source_root.rglob("*.java")
    }
    expected = {name for name in available_sources if name != GENERATOR and not name.startswith("com/dndtool/offline/")}
    compiled = {name for name in files if name.startswith(CLASSES) and name.endswith(".class")}
    owners = {name[len(CLASSES):-len(".class")].split("$", 1)[0] for name in compiled}
    require(owners == expected,
            f"application classes missing {sorted(expected - owners)} or without packaged source {sorted(owners - expected)}")
    mandatory = {CLASSES + "com/dndtool/" + name + ".class" for name in REQUIRED_CLASSES}
    require(mandatory <= compiled, f"missing runtime classes {sorted(mandatory - compiled)}")
    metadata = {"META-INF/MANIFEST.MF", MAVEN_METADATA + "pom.xml", MAVEN_METADATA + "pom.properties"}
    unexpected = files - (source_resources.keys() | web_resources.keys() | compiled | jars | metadata | {MANIFEST})
    require(not unexpected, f"unexpected WAR entries {sorted(unexpected)}")
    return compiled


def libraries(archive: ZipFile, names: set[str]) -> None:
    shipped_classes = set()
    driver_names = []
    for name in sorted(names):
        with ZipFile(io.BytesIO(archive.read(name))) as jar:
            jar_names = members(jar)
            forbidden = sorted(entry for entry in jar_names if forbidden_entry(entry))
            require(not forbidden, f"forbidden library bytecode in {name}: {forbidden[:5]}")
            shipped_classes.update(entry for entry in jar_names if entry.endswith(".class"))
            service = "META-INF/services/java.sql.Driver"
            if service in jar_names:
                driver_names.extend(line.partition("#")[0].strip() for line in jar.read(service).decode().splitlines())
    require({"org/sqlite/JDBC.class", "com/google/gson/Gson.class"} <= shipped_classes,
            "missing SQLite JDBC or Gson classes")
    require([name for name in driver_names if name] == ["org.sqlite.JDBC"],
            "unexpected JDBC driver service")


def checked_jdk(command: list[str]) -> str:
    run = subprocess.run(command, text=True, capture_output=True, errors="replace", check=False)
    require(run.returncode == 0, f"{command[0]} exited {run.returncode}\n{run.stdout}{run.stderr}")
    return run.stdout


def java_checks(archive: ZipFile, names: set[str], compiled: set[str], jars: set[str]) -> None:
    with tempfile.TemporaryDirectory(prefix="dnd-war-audit-") as temp:
        work = Path(temp)
        for name in sorted(compiled | jars | {entry for entry in names if entry.startswith(RULES)}):
            archive.extract(name, work)
        class_dir = work / CLASSES
        lib_path = os.pathsep.join(str(work / entry) for entry in sorted(jars))
        missing = checked_jdk(["jdeps", "--multi-release", "21", "--class-path", lib_path,
                               "--missing-deps", str(class_dir)])
        unresolved = [line.strip() for line in missing.splitlines()
                      if line.startswith(" ") and "not found" in line and " -> jakarta.servlet." not in line]
        require(not unresolved, f"unresolved application class references {unresolved}")
        generator = work / "generator"
        generator.mkdir()
        runtime = os.pathsep.join([str(class_dir), lib_path])
        checked_jdk(["javac", "--release", "21", "-encoding", "UTF-8", "-cp", runtime,
                     "-d", str(generator), str(ROOT / "src/main/java" / (GENERATOR + ".java"))])
        manifest = work / "bundled-members.tsv"
        checked_jdk(["java", "-cp", os.pathsep.join([str(generator), runtime]), GENERATOR.replace("/", "."),
                     str(work / RULES), str(manifest)])
        require(manifest.read_bytes() == archive.read(MANIFEST), "bundled rule manifest differs")


def audit(war: Path) -> dict:
    with war.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    with ZipFile(war) as archive:
        names = members(archive)
        files = {name for name in names if not name.endswith("/")}
        forbidden = sorted(name for name in files if forbidden_entry(name))
        require(not forbidden, f"forbidden package entries {forbidden}")
        jars = {name for name in files if name.startswith(LIBS) and name.endswith(".jar")
                and "/" not in name[len(LIBS):]}
        require({name for name in files if name.endswith(".jar")} == jars, "unexpected JAR location")
        for name in sorted(files):
            if Path(name).suffix in {".class", ".html", ".js", ".css", ".json", ".xml", ".properties", ".tsv"}:
                require(not SENSITIVE_CONTENT.search(archive.read(name)), f"forbidden content in {name}")
        compiled = source_inputs(archive, files, jars)
        descriptor(archive)
        libraries(archive, jars)
        java_checks(archive, files, compiled, jars)
        return {"bytes": war.stat().st_size, "sha256": digest, "zipEntries": len(names),
                "applicationClasses": len(compiled), "libraries": len(jars), "bundledRules": len(RULE_FILES),
                "forbiddenMatches": 0, "result": "passed"}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("war", type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(audit(args.war), sort_keys=True))
    except (BadZipFile, ElementTree.ParseError, KeyError, OSError, ValueError) as error:
        print(f"WAR audit failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
