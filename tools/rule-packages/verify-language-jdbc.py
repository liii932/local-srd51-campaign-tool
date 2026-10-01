#!/usr/bin/env python3
"""Replay approved SQL and verify FI-07 on a new, memory-only local MySQL container.

The default is read-only manifest validation. --execute-disposable requires prior
authorization for the container, schema/accounts and test writes. It deliberately
has no existing-container, JDBC URL, database, password or volume arguments.
An operator reviews the captured live schema/ACL evidence before allowing the IT.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import signal
import socket
import subprocess
import tempfile
import threading
import time
from datetime import datetime, timedelta, timezone
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
DOCKER = ["/usr/bin/docker", "--host", "unix:///var/run/docker.sock"]
MAVEN = "/usr/bin/mvn"
SOURCE = "dnd_tool_rules"
RUNTIME = "dnd_tool_se"
LABEL = "com.dndtool.language-jdbc-acceptance"
SOURCE_TABLES = sorted(("rule_schema_meta", "rule_release", "rule_language", "rule_tool",
                       "rule_package_installation", "rule_package_installation_partition",
                       "rule_installation_control"))
RUNTIME_TABLES = sorted(("runtime_run_identity", "runtime_rule_snapshot", "runtime_rule_language", "runtime_rule_tool"))
ACCOUNTS = {
    "root": "localhost",
    "dnd_tool_se_migration_replay": "localhost",
    "dnd_tool_rules_migrator": "127.0.0.1",
    "dnd_tool_rules_installer": "127.0.0.1",
    "dnd_tool_rules_app": "127.0.0.1",
    "dnd_tool_se_app": "127.0.0.1",
    "dnd_tool_se_validation_ro": "127.0.0.1",
}


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def payload_digest(raw):
    sql = raw.decode("utf-8").replace("\r\n", "\n").replace("\r", "\n")
    begin, end = "-- CHECKSUM-SCOPE-BEGIN", "-- CHECKSUM-SCOPE-END"
    require(sql.count(begin) == sql.count(end) == 1, "Invalid checksum markers")
    start, stop = sql.index(begin) + len(begin), sql.index(end)
    require(stop > start + 1 and sql[start] == sql[stop - 1] == "\n", "Invalid checksum scope")
    return sha(sql[start + 1:stop - 1].encode("utf-8"))


def manifests():
    java = (ROOT / "src/main/java/com/dndtool/persistence/SchemaMigrations.java").read_text("utf-8")
    block = re.search(r"APPROVED_MIGRATIONS = List[.]of\((.*?)\);", java, re.S)
    require(block is not None, "Runtime migration manifest not recognized")
    pattern = r"new Definition\((V[0-9]{3})_VERSION, \1_SCRIPT_NAME, \1_APPROVED_SHA256\)"
    prefixes = re.findall(pattern, block[1])
    require(re.sub(pattern, "", block[1]).replace(",", "").strip() == "", "Unexpected manifest entry")
    result = []
    for number, prefix in enumerate(prefixes, 1):
        require(prefix == f"V{number:03d}", "Unordered runtime migration manifest")
        require(re.search(rf"{prefix}_VERSION = {number};", java), "Version constant mismatch")
        name = re.search(rf'{prefix}_SCRIPT_NAME = "([A-Za-z0-9_.-]+[.]sql)";', java)
        digest = re.search(rf'{prefix}_APPROVED_SHA256 =\s*"([0-9a-f]{{64}})";', java)
        require(name and digest and name[1].startswith(prefix + "__"), "Invalid migration constant")
        result.append((RUNTIME, number, name[1], digest[1], ROOT / "src/main/resources/db/migration" / name[1]))
    require(len(result) == 20, "This acceptance profile requires review when the runtime chain changes")
    java = (ROOT / "src/main/java/com/dndtool/persistence/RuleSchemaMigrations.java").read_text("utf-8")
    block = re.search(r"APPROVED = List[.]of\((.*?)\);", java, re.S)
    require(block is not None, "Rules manifest not recognized")
    pattern = r'new Expectation\(\s*SCHEMA_ROLE, ([0-9]+), "([a-zA-Z0-9_.-]+[.]sql)",\s*"([0-9a-f]{64})"\)'
    rules = re.findall(pattern, block[1])
    require(re.sub(pattern, "", block[1]).replace(",", "").strip() == "", "Unexpected rules manifest entry")
    require(len(rules) == 2, "This acceptance profile requires review when the RULES chain changes")
    for expected, (version, name, digest) in enumerate(rules, 1):
        require(int(version) == expected and name.startswith(f"V{expected:03d}__"), "Unordered rules manifest")
        result.append((SOURCE, expected, name, digest, ROOT / "database/rules/migration" / name))
    checked = []
    for schema, version, name, approved, path in result:
        raw = path.read_bytes()
        require(payload_digest(raw) == approved, "Approved digest mismatch: " + name)
        checked.append((schema, version, name, approved, path, raw))
    return checked


def report_assertions(report, expected_tests=None):
    node = ET.parse(report).getroot()
    require(int(node.get("tests", "0")) > 0, "No executed tests: " + report.name)
    if expected_tests is not None:
        require(int(node.get("tests")) == expected_tests, "Unexpected test count: " + report.name)
    for key in ("failures", "errors", "skipped"):
        require(int(node.get(key, "0")) == 0, "Unsuccessful acceptance: " + report.name)


def validate_data_mounts(value, mount_text):
    # Docker's --tmpfs is recorded in HostConfig.Tmpfs, unlike --mount type=tmpfs.
    mounts = value["Mounts"]
    require(not value["HostConfig"].get("Binds") and all(
        mount["Type"] == "tmpfs" and mount["Destination"] == "/var/lib/mysql" for mount in mounts),
        "Persistent/unexpected mount detected")
    require(set(value["HostConfig"].get("Tmpfs") or {}) == {"/var/lib/mysql"}, "Unexpected tmpfs configuration")
    data_mounts = [row.split() for row in mount_text.splitlines() if len(row.split()) >= 4 and row.split()[1] == "/var/lib/mysql"]
    require(len(data_mounts) == 1 and data_mounts[0][2] == "tmpfs", "Live MySQL data directory is not tmpfs")
    require({"rw", "nosuid", "nodev"}.issubset(set(data_mounts[0][3].split(","))), "Unexpected data mount options")
    return data_mounts[0]


def sql_tokens(sql):
    # MySQL may insert spaces between charset introducers and quoted literals.
    # Preserve every literal and operator; ignore only whitespace and comments.
    tokens = re.findall(r"'(?:''|\\.|[^'\\])*'|`(?:``|[^`])*`|--[^\r\n]*|/\*.*?\*/|[A-Za-z_][A-Za-z_0-9]*|[0-9]+|<>|>=|<=|!=|[^\s]", sql, re.S)
    return [token for token in tokens if not token.startswith(("--", "/*"))]


def show_trigger_body(output, name):
    # MySQL 8.0 SHOW CREATE has seven columns. Split from the ends so literal
    # tabs/newlines in SQL Original Statement stay intact. I_S.ACTION_STATEMENT
    # is a UTF-8 projection that omits charset introducers, not the original SQL.
    first = output.split("\t", 2)
    require(len(first) == 3 and first[0] == name, "Unexpected SHOW CREATE TRIGGER header")
    fields = first[2].rsplit("\t", 4)
    require(len(fields) == 5, "Unexpected SHOW CREATE TRIGGER columns")
    body = re.search(r"\bFOR EACH ROW\s+(.*)\Z", fields[0], re.S)
    require(body is not None, "Missing original trigger body")
    return body[1]


def validate_accounts(output):
    rows = [tuple(row.split("\t")) for row in output.splitlines()]
    expected = {(user, host, "N") for user, host in ACCOUNTS.items()}
    expected.update((user, "localhost", "Y") for user in ("mysql.infoschema", "mysql.session", "mysql.sys"))
    require(len(rows) == len(expected) and set(rows) == expected,
            "Unexpected MySQL accounts or host patterns; installation prohibited")


def stop_process_group(process):
    # start_new_session=True makes this leader's PID the owned process-group ID.
    # The leader may already be gone while Surefire still belongs to that group.
    try:
        try:
            os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            pass
    finally:
        try:
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        finally:
            process.wait(timeout=10)


class Acceptance:
    def __init__(self, migrations):
        self.migrations = migrations
        self.passwords = {name: secrets.token_hex(24) for name in ACCOUNTS}
        self.container = None
        self.token = secrets.token_hex(12)
        self.name = "dnd-language-jdbc-" + self.token
        self.create_attempted = False
        self.raw_hashes = {path: sha(raw) for _, _, _, _, path, raw in migrations}
        # /tmp may be cleared when WSL stops; retain review evidence on the Linux disk.
        self.directory = Path(tempfile.mkdtemp(prefix="dnd-language-jdbc-", dir="/var/tmp"))
        for name in ("audit", "evidence", "state"):
            (self.directory / name).mkdir(mode=0o700)
        self.port = None
        self.server_uuid = None

    def redact(self, text):
        for secret in self.passwords.values():
            text = text.replace(secret, "[redacted]")
        return text

    def run(self, command, *, data=None, file=None, env=None, check=True):
        result = subprocess.run(command, input=data, stdin=file, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, env=env, cwd=ROOT, timeout=180)
        if check and result.returncode:
            raise RuntimeError(self.redact(result.stderr.decode("utf-8", "replace")) or
                               "Command failed without diagnostics")
        return result

    def docker(self, *arguments, **kwargs):
        return self.run(DOCKER + list(arguments), **kwargs)

    def inspect(self):
        value = json.loads(self.docker("inspect", self.container).stdout)[0]
        require(value["Id"] == self.container and value["Config"]["Labels"].get(LABEL) == self.token,
                "Container ownership mismatch; refusing further actions")
        return value

    def mysql(self, sql=None, *, user="root", schema=None, path=None, approved=None):
        require(self.container is not None, "No owned container")
        env = dict(os.environ, MYSQL_PWD=self.passwords[user])
        command = DOCKER + ["exec", "--interactive", "--env", "MYSQL_PWD", self.container,
                            "mysql", "--no-defaults", "--batch", "--raw", "--skip-column-names",
                            "--default-character-set=utf8mb4", "--binary-mode", "--user=" + user]
        if ACCOUNTS[user] == "127.0.0.1":
            command += ["--protocol=TCP", "--host=127.0.0.1", "--port=" + str(self.port)]
        else:
            command += ["--protocol=SOCKET"]
        if schema:
            command += ["--database=" + schema]
        if path:
            # Check and replay the same original bytes, with no second read or SQL rewrite.
            raw = path.read_bytes()
            if approved is not None:
                require(payload_digest(raw) == approved and sha(raw) == self.raw_hashes[path],
                        "Approved migration changed before execution: " + path.name)
            return self.run(command, data=raw, env=env).stdout.decode("utf-8")
        return self.run(command, data=sql.encode("utf-8"), env=env).stdout.decode("utf-8")

    def save(self, relative, value):
        path = self.directory / relative
        raw = value.encode("utf-8")
        with path.open("xb") as output:
            os.chmod(path, 0o600)
            output.write(raw)
        return sha(raw)

    def maven(self, classes, *, jdbc=False):
        command = [MAVEN, "--batch-mode", "-Dstyle.color=never", "-Dtest=" + ",".join(classes)]
        env = dict(os.environ)
        if jdbc:
            command.append("-Ddnd.language.jdbc.acceptance=true")
            env["DND_LANGUAGE_ACCEPTANCE_DIRECTORY"] = str(self.directory)
            for suffix, user in (("INSTALLER", "dnd_tool_rules_installer"), ("SOURCE", "dnd_tool_rules_app"),
                                 ("RUNTIME", "dnd_tool_se_app"), ("VERIFIER", "dnd_tool_se_validation_ro")):
                env["DND_LANGUAGE_" + suffix + "_PASSWORD"] = self.passwords[user]
        command.append("test")
        started = time.time_ns()
        lines = []
        process = subprocess.Popen(command, cwd=ROOT, env=env, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace",
                                   start_new_session=True)
        def expire():
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        timer = threading.Timer(600, expire)
        timer.daemon = True
        timer.start()
        try:
            for line in process.stdout:
                line = self.redact(line)
                print(line, end="", flush=True)
                lines.append(line)
            code = process.wait()
        finally:
            timer.cancel()
            try:
                stop_process_group(process)
            finally:
                process.stdout.close()
        self.save("audit/" + ("jdbc" if jdbc else "guard") + "-maven.txt", "".join(lines))
        require(code == 0, "Maven acceptance failed")
        for name in classes:
            matches = list((ROOT / "target/surefire-reports").glob("TEST-*." + name + ".xml"))
            require(len(matches) == 1 and matches[0].stat().st_mtime_ns >= started,
                    "Missing fresh test report: " + name)
            report_assertions(matches[0], 1 if jdbc else None)
            self.save("audit/" + matches[0].name, matches[0].read_text("utf-8"))

    def start(self):
        image = json.loads(self.docker("image", "inspect", "mysql:8.0").stdout)[0]
        require(image["Os"] == "linux" and set(image["Config"].get("Volumes") or {}) == {"/var/lib/mysql"},
                "Unexpected image volumes or OS; review the image before execution")
        self.image = image["Id"]
        with socket.socket() as reservation:
            reservation.bind(("127.0.0.1", 0))
            self.port = reservation.getsockname()[1]
        require(1024 <= self.port <= 65535 and self.port not in (3306, 33060), "Unsafe generated port")
        env = dict(os.environ, MYSQL_ROOT_PASSWORD=self.passwords["root"])
        self.create_attempted = True
        self.container = self.docker(
            "create", "--pull=never", "--name", self.name,
            "--label", LABEL + "=" + self.token, "--network=host",
            "--tmpfs", "/var/lib/mysql:rw,nosuid,nodev,size=2g", "--env", "MYSQL_ROOT_PASSWORD",
            "--env", "MYSQL_ROOT_HOST=localhost",
            self.image, "--bind-address=127.0.0.1", "--port=" + str(self.port), "--mysqlx=OFF",
            "--skip-name-resolve", "--skip-log-bin", "--default-time-zone=+00:00",
            "--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_bin", env=env
        ).stdout.decode().strip()
        require(re.fullmatch("[0-9a-f]{64}", self.container), "Invalid created container ID")
        value = self.inspect()
        require(value["Image"] == self.image and value["HostConfig"]["NetworkMode"] == "host"
                and not value["HostConfig"].get("Binds"), "Unexpected container configuration")
        self.docker("start", self.container)
        for _ in range(120):
            value = self.inspect()
            require(value["State"]["Running"], "Disposable MySQL exited during startup")
            try:
                ready = self.mysql("SELECT @@port, @@skip_networking, @@server_uuid, @@version, @@GLOBAL.partial_revokes;").strip().split("\t")
                if ready[0:2] == [str(self.port), "0"]:
                    self.server_uuid = ready[2]
                    require(ready[3].startswith("8.0.") and ready[4] == "0", "Unsupported MySQL or grant mode")
                    self.version = ready[3]
                    break
            except RuntimeError:
                pass
            time.sleep(1)
        require(self.server_uuid is not None, "Disposable MySQL readiness timeout")
        value = self.inspect()
        kernel_mount = validate_data_mounts(value, self.docker("exec", self.container, "/bin/cat", "/proc/mounts").stdout.decode())
        require(self.mysql("SELECT @@datadir;").strip() == "/var/lib/mysql/", "Unexpected MySQL data directory")
        self.save("audit/container.json", json.dumps({"id": self.container, "image": self.image,
                  "label": self.token, "mounts": value["Mounts"], "tmpfs": value["HostConfig"]["Tmpfs"],
                  "kernel_data_mount": kernel_mount, "port": self.port,
                  "server_uuid": self.server_uuid, "mysql_version": self.version}, indent=2) + "\n")
        print("Owned memory-only MySQL ready: " + self.version, flush=True)

    def migrate(self):
        self.mysql("CREATE DATABASE dnd_tool_se CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n"
                   "CREATE DATABASE dnd_tool_rules CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;")
        for name, host in ACCOUNTS.items():
            if name != "root":
                self.mysql(f"CREATE USER '{name}'@'{host}' IDENTIFIED BY '{self.passwords[name]}';")
        self.mysql("GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES, TRIGGER, "
                   "CREATE TEMPORARY TABLES ON `dnd\\_tool\\_se`.* TO 'dnd_tool_se_migration_replay'@'localhost';")
        self.mysql(path=ROOT / "database/grants/rule-source-migrator.sql")
        for schema, _, name, approved, path, _ in self.migrations:
            user = "dnd_tool_rules_migrator" if schema == SOURCE else "dnd_tool_se_migration_replay"
            self.mysql(user=user, schema=schema, path=path, approved=approved)
            print("Replayed " + schema + "/" + name, flush=True)
        for name in ("rule-source-installer.sql", "rule-source-app.sql", "runtime-language-snapshot.sql", "runtime-tool-snapshot.sql"):
            self.mysql(path=ROOT / "database/grants" / name)
        self.mysql("GRANT SELECT ON `dnd\\_tool\\_se`.* TO 'dnd_tool_se_validation_ro'@'127.0.0.1';")

    def audit(self):
        captures = {}
        for schema, table, user in ((RUNTIME, "schema_meta", "dnd_tool_se_validation_ro"),
                                    (SOURCE, "rule_schema_meta", "dnd_tool_rules_app")):
            actual = self.mysql("SELECT schema_version, CONVERT(script_name USING ascii), "
                                f"CONVERT(script_sha256 USING ascii) FROM {table} ORDER BY schema_version;",
                                user=user, schema=schema)
            expected = "".join(f"{v}\t{n}\t{d}\n" for s, v, n, d, _, _ in self.migrations if s == schema)
            require(actual == expected, "Live ledger differs from approved manifest: " + schema)
            captures[schema + "-ledger"] = actual
        for schema, tables in ((SOURCE, SOURCE_TABLES), (RUNTIME, RUNTIME_TABLES)):
            auditor = "dnd_tool_rules_migrator" if schema == SOURCE else "dnd_tool_se_migration_replay"
            if schema == SOURCE:
                actual = self.mysql(f"SELECT table_name FROM information_schema.tables WHERE table_schema='{schema}' ORDER BY table_name;", user=auditor)
                require(actual.splitlines() == tables, "Unexpected source objects")
            for table in tables:
                captures[schema + "." + table] = self.mysql(f"SHOW CREATE TABLE {schema}.{table};", user=auditor)
            for kind, query in {
                "columns": "SELECT table_name, column_name, ordinal_position, column_type, is_nullable, column_default, extra, collation_name FROM information_schema.columns",
                "indexes": "SELECT table_name, index_name, non_unique, seq_in_index, column_name FROM information_schema.statistics",
                "constraints": "SELECT * FROM information_schema.table_constraints",
                "checks": "SELECT * FROM information_schema.check_constraints",
                "foreign-keys": "SELECT * FROM information_schema.referential_constraints",
            }.items():
                column = "constraint_schema" if kind in ("checks", "foreign-keys") else "table_schema"
                captures[schema + "-" + kind] = self.mysql(query + f" WHERE {column}='{schema}';", user=auditor)
            captures[schema + "-triggers"] = self.mysql(
                f"SELECT trigger_name, event_manipulation, event_object_table, action_timing, definer, "
                f"HEX(action_statement), sql_mode, character_set_client, collation_connection "
                f"FROM information_schema.triggers WHERE trigger_schema='{schema}' ORDER BY trigger_name;", user=auditor)
            require(self.mysql(f"SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema='{schema}';").strip() == "0", "Unexpected routines")
            require(self.mysql(f"SELECT COUNT(*) FROM information_schema.events WHERE event_schema='{schema}';").strip() == "0", "Unexpected events")
            require(self.mysql(f"SELECT COUNT(*) FROM information_schema.views WHERE table_schema='{schema}';").strip() == "0", "Unexpected views")
        self.save("audit/live-definitions.json", json.dumps(captures, ensure_ascii=False, indent=2) + "\n")
        # Compare all source and runtime snapshot trigger bodies to the immutable migration SQL.
        for schema, definer in ((SOURCE, "dnd_tool_rules_migrator@127.0.0.1"),
                                (RUNTIME, "dnd_tool_se_migration_replay@localhost")):
            original = "\n".join(raw.decode("utf-8") for side, version, _, _, _, raw in self.migrations
                                 if side == schema and (side == SOURCE or version >= 19))
            expected = {name: (event, table, timing, body) for name, timing, event, table, body in re.findall(
                r"CREATE TRIGGER (\w+) (BEFORE|AFTER) (INSERT|UPDATE|DELETE) ON (\w+) FOR EACH ROW\s+(.*?)\$\$", original, re.S)}
            actual = {}
            for row in captures[schema + "-triggers"].splitlines():
                name, event, table, timing, owner, body, *_ = row.split("\t")
                if schema == SOURCE or table in RUNTIME_TABLES:
                    require(owner == definer, "Trigger definer mismatch: " + name)
                    auditor = "dnd_tool_rules_migrator" if schema == SOURCE else "dnd_tool_se_migration_replay"
                    original_statement = self.mysql(f"SHOW CREATE TRIGGER {schema}.{name};", user=auditor)
                    captures[schema + ".trigger." + name] = original_statement
                    actual[name] = (event, table, timing, show_trigger_body(original_statement, name))
            require(actual.keys() == expected.keys(), "Unexpected trigger set")
            for name, definition in expected.items():
                if actual[name][:3] != definition[:3] or sql_tokens(actual[name][3]) != sql_tokens(definition[3]):
                    mismatch = json.dumps(dict(name=name, expected=definition, actual=actual[name]), indent=2)
                    self.save("audit/trigger-mismatch.json", mismatch + "\n")
                    print(mismatch, flush=True)  # Public DDL only; no account credentials.
                    raise RuntimeError("Trigger definition mismatch: " + name)
        captures["incoming-foreign-keys"] = self.mysql(
            "SELECT * FROM information_schema.key_column_usage WHERE referenced_table_schema IN ('dnd_tool_rules','dnd_tool_se') ORDER BY table_schema, table_name, ordinal_position;")
        for name, host in ACCOUNTS.items():
            if name != "root":
                captures["grants-" + name] = self.mysql(f"SHOW GRANTS FOR '{name}'@'{host}';")
        captures["roles"] = self.mysql("SELECT * FROM mysql.role_edges; SELECT * FROM mysql.default_roles;")
        require(captures["roles"] == "", "Unexpected inherited roles")
        captures["accounts"] = self.mysql("SELECT user,host,account_locked FROM mysql.user ORDER BY user,host;")
        validate_accounts(captures["accounts"])
        captures["connections"] = self.mysql("SELECT user,host,db,command FROM information_schema.processlist ORDER BY user,host;")
        captures["server"] = self.mysql("SELECT @@server_uuid, @@version, @@sql_mode, @@global.partial_revokes, @@port, @@bind_address, @@skip_networking, @@log_bin;")
        captures["fresh-history"] = self.mysql(
            "SELECT control_id,protocol_version,metadata_row_count,row_version FROM rule_installation_control;"
            "SELECT (SELECT COUNT(*) FROM rule_release),(SELECT COUNT(*) FROM rule_language),(SELECT COUNT(*) FROM rule_tool),"
            "(SELECT COUNT(*) FROM rule_package_installation),(SELECT COUNT(*) FROM rule_package_installation_partition);", user="dnd_tool_rules_app", schema=SOURCE)
        require(captures["fresh-history"] == "1\t1\t1\t0\n0\t0\t0\t0\t0\n", "Source is not freshly migrated")
        require(self.mysql("SELECT (SELECT COUNT(*) FROM runtime_run_identity),(SELECT COUNT(*) FROM runtime_rule_snapshot),"
                           "(SELECT COUNT(*) FROM runtime_rule_language),(SELECT COUNT(*) FROM runtime_rule_tool);", user="dnd_tool_se_validation_ro", schema=RUNTIME) == "0\t0\t0\t0\n", "Runtime not empty")
        captures["runtime-counts"] = self.mysql("SELECT table_name, table_type, engine FROM information_schema.tables WHERE table_schema='dnd_tool_se' ORDER BY table_name;", user="dnd_tool_se_validation_ro")
        self.legacy_sql = "SELECT module_key,release_version,canonical_format_version,hash_algorithm,content_sha256,release_status FROM module_release ORDER BY module_key,release_version;"
        captures["legacy-releases"] = self.mysql(self.legacy_sql, user="dnd_tool_se_validation_ro", schema=RUNTIME)
        self.legacy = captures["legacy-releases"]
        captures["original-file-sha256"] = "".join(f"{schema}/{name}\t{sha(raw)}\n" for schema, _, name, _, _, raw in self.migrations)
        self.schema_hash = self.save("audit/live-schema.json", json.dumps(captures, ensure_ascii=False, indent=2) + "\n")
        self.grants_hash = sha(("\n".join(sorted(captures["grants-dnd_tool_rules_installer"].splitlines())) + "\n").encode())
        print("Live audit captured outside repository: " + str(self.directory), flush=True)
        print("Review live-schema.json against the original SQL and grants: full columns/types/nullability, CHECK enforcement, indexes/FKs, triggers/definers, incoming FKs, accounts/roles, empty history and exclusive access.", flush=True)
        print("After independent operator review, enter accept-schema; any other input aborts and destroys the instance:", flush=True)
        require(input().strip() == "accept-schema", "Schema/privilege audit not accepted")

    def evidence(self):
        reports = {
            "schema": f"Independent operator reviewed audit/live-schema.json SHA-256 {self.schema_hash}.\n"
                      "Approved raw migrations, complete definitions/constraints/definers and effective grants reviewed before installation.\n",
            "history": f"New disposable container {self.container}; image {self.image}; server UUID {self.server_uuid}.\n"
                       "Data directory is fresh tmpfs, no restore or previous executor. Approved raw chains replayed once.\n"
                       "Initial control (1,1,1,0), no roots/languages/installations/partitions; history lower bound 0.\n",
            "isolation": f"Owned container label {LABEL}={self.token}; only loopback port {self.port}; no persistent volumes.\n"
                         "Dedicated random credentials, no inherited roles. Migration/admin sessions ended before installer.\n"
                         "One ordered acceptance executor uses one owner-only state directory for the entire ticket history.\n"
                         "Evidence covers this disposable run only, not restore history or power-loss durability.\n",
        }
        hashes = {key + "_audit_sha256": self.save("evidence/" + key + "-audit.txt", text)
                  for key, text in reports.items()}
        config = dict(evidence_version=1, target="language-jdbc-" + self.token, lineage="fresh-" + self.token,
                      jdbc_url=f"jdbc:mysql://127.0.0.1:{self.port}/dnd_tool_rules", user="dnd_tool_rules_installer",
                      server_uuid=self.server_uuid, current_user="dnd_tool_rules_installer@127.0.0.1",
                      grants_sha256=self.grants_hash, expires_at=(datetime.now(timezone.utc) + timedelta(hours=1)).isoformat().replace("+00:00", "Z"),
                      permission="INSTALL", isolated_operation="none", history_minimum_version=0,
                      state_directory=str(self.directory / "state"), **hashes)
        self.save("evidence/maintenance.json", json.dumps(config, indent=2) + "\n")
        self.save("acceptance.json", json.dumps(dict(acceptance_version=1, port=self.port, server_uuid=self.server_uuid)) + "\n")

    def close(self):
        if self.create_attempted and not self.container:
            matches = self.docker("ps", "--all", "--no-trunc", "--quiet", "--filter",
                                  "label=" + LABEL + "=" + self.token, "--filter", "name=^/" + self.name + "$").stdout.decode().splitlines()
            require(len(matches) <= 1, "Ambiguous owned container; cleanup requires review")
            if matches:
                self.container = matches[0]
        if self.container:
            self.inspect()  # Never remove a container by a reused name or a foreign label.
            self.docker("rm", "--force", self.container)
            require(not self.docker("ps", "--all", "--quiet", "--filter", "label=" + LABEL + "=" + self.token).stdout.strip(),
                    "Disposable container remains")
            print("Destroyed owned MySQL container; no persistent volume was created.", flush=True)
            self.save("audit/cleanup.txt", "Owned container destroyed; verified no matching container remains.\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--execute-disposable", action="store_true", help="execute authorized disposable migrations and JDBC acceptance")
    args = parser.parse_args()
    migrations = manifests()
    print("Approved payloads verified: runtime V001-V020 and RULES V001-V002. SQL files remain unchanged.", flush=True)
    if not args.execute_disposable:
        return
    require(os.name == "posix" and Path("/var/run/docker.sock").is_socket(), "Requires native Linux/WSL and the local Docker socket")
    os.umask(0o077)
    signal.signal(signal.SIGTERM, lambda *_: (_ for _ in ()).throw(KeyboardInterrupt()))
    acceptance = Acceptance(migrations)
    try:
        acceptance.maven(["MySqlIntegrationTestSupportTest", "LanguageJdbcAcceptanceTest"])
        acceptance.start()
        acceptance.migrate()
        acceptance.audit()
        acceptance.evidence()
        acceptance.maven(["LanguagePartitionJdbcIT"], jdbc=True)
        require(acceptance.mysql(acceptance.legacy_sql, user="dnd_tool_se_validation_ro", schema=RUNTIME) == acceptance.legacy,
                "Legacy release metadata changed during language acceptance")
        print("FI-07 real JDBC acceptance passed with no skipped tests.", flush=True)
    finally:
        try:
            acceptance.close()
        finally:
            print("External audit directory: " + str(acceptance.directory), flush=True)


if __name__ == "__main__":
    main()
