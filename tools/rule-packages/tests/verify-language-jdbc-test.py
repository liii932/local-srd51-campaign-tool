"""Offline regression checks for the external acceptance boundary; run this file directly."""
from contextlib import contextmanager
import importlib.util
import json
from pathlib import Path
import re
from subprocess import CompletedProcess, TimeoutExpired
import tempfile
import unittest
from unittest.mock import Mock, call, patch


spec = importlib.util.spec_from_file_location("language_jdbc", Path(__file__).resolve().parents[1] / "verify-language-jdbc.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def representative_migrations():
    """Independent approved inputs for orchestration tests, not a runnable migration chain."""
    fixtures = (
        ("dnd_tool_se", 19, "src/main/resources/db/migration/V019__runtime-language-snapshot.sql",
         "6df15d978dc79d0298c7cf4fc470f75633b58199795b4a3fae4f1a1bf44f3173"),
        ("dnd_tool_rules", 2, "database/rules/migration/V002__tool-catalog.sql",
         "2a5a8a67ebef60754ff9ca572d651878558ccc56ed2bfd311ce811ef42082c40"),
    )
    return [(schema, version, Path(relative).name, digest, module.ROOT / relative,
             (module.ROOT / relative).read_bytes()) for schema, version, relative, digest in fixtures]


@contextmanager
def isolated_cli(arguments):
    with patch("sys.argv", ["verify-language-jdbc.py", *arguments]), \
            patch.object(module, "Acceptance", side_effect=AssertionError("No disposable executor")), \
            patch.object(module.subprocess, "run", side_effect=AssertionError("No subprocess")), \
            patch.object(module.subprocess, "Popen", side_effect=AssertionError("No subprocess")), \
            patch.object(module.tempfile, "mkdtemp", side_effect=AssertionError("No audit directory")), \
            patch.object(module.socket, "socket", side_effect=AssertionError("No network")), \
            patch.object(module.Path, "is_socket", side_effect=AssertionError("No Docker socket check")), \
            patch("builtins.print") as output:
        yield output


def reviewed_access_grants():
    """Represent SHOW GRANTS for the real narrow grant files and disposable runtime reader."""
    grants = {}
    for role, files in (
            ("dnd_tool_rules_app", ("rule-source-app.sql",)),
            ("dnd_tool_se_app", ("runtime-language-snapshot.sql", "runtime-tool-snapshot.sql", "business-write-guard.sql")),
    ):
        sql = "\n".join((module.ROOT / "database/grants" / name).read_text("utf-8") for name in files)
        statements = [re.sub(r"'([^']+)'@'([^']+)'", r"`\1`@`\2`", line.rstrip(";"))
                      for line in sql.splitlines() if line.startswith("GRANT ")]
        grants["grants-" + role] = "\n".join(statements) + "\n"
    grants["grants-dnd_tool_se_validation_ro"] = (
        "GRANT USAGE ON *.* TO `dnd_tool_se_validation_ro`@`127.0.0.1`\n"
        "GRANT SELECT ON `dnd\\_tool\\_se`.* TO `dnd_tool_se_validation_ro`@`127.0.0.1`\n")
    return grants


class AcceptanceBoundaryTest(unittest.TestCase):
    def test_default_wildcard_root_and_unexpected_accounts_are_rejected(self):
        rows = [user + "\t" + host + "\tN" for user, host in module.ACCOUNTS.items()]
        rows.extend(user + "\tlocalhost\tY" for user in ("mysql.infoschema", "mysql.session", "mysql.sys"))
        valid = "\n".join(rows) + "\n"
        module.validate_accounts(valid)
        for invalid in (valid + "root\t%\tN\n", valid.replace("mysql.sys\tlocalhost\tY", "mysql.sys\tlocalhost\tN"), valid + rows[0] + "\n"):
            with self.assertRaisesRegex(RuntimeError, "Unexpected MySQL accounts"):
                module.validate_accounts(invalid)

    def test_show_create_preserves_charset_introducers_and_literal_tabs(self):
        body = "BEGIN IF NEW.status <> _binary'DRAFT' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='a\tb'; END IF; END"
        result = "guard\tSTRICT_TRANS_TABLES\tCREATE DEFINER=`owner`@`localhost` TRIGGER `guard` BEFORE INSERT ON `item` FOR EACH ROW " + body + "\tutf8mb4\tutf8mb4_0900_ai_ci\tutf8mb4_0900_bin\t2026-01-01 00:00:00.00\n"
        self.assertEqual(body, module.show_trigger_body(result, "guard"))
        with self.assertRaises(RuntimeError):
            module.show_trigger_body(result, "other")

    def test_exited_maven_leader_still_terminates_and_kills_its_surefire_group(self):
        process = Mock(pid=41017)
        process.wait.return_value = 0
        process.poll.side_effect = AssertionError("Leader status does not prove group termination")
        with patch.object(module.os, "killpg") as kill:
            module.stop_process_group(process)
        self.assertEqual([call(41017, module.signal.SIGTERM), call(41017, module.signal.SIGKILL)], kill.call_args_list)
        self.assertEqual([call(timeout=10), call(timeout=10)], process.wait.call_args_list)

    def test_unresponsive_leader_is_killed_and_reaped_after_term_timeout(self):
        process = Mock(pid=41018)
        process.wait.side_effect = [TimeoutExpired(["mvn"], 10), -9]
        with patch.object(module.os, "killpg") as kill:
            module.stop_process_group(process)
        self.assertEqual([call(41018, module.signal.SIGTERM), call(41018, module.signal.SIGKILL)], kill.call_args_list)
        self.assertEqual([call(timeout=10), call(timeout=10)], process.wait.call_args_list)

    def test_disappeared_process_group_is_safe_and_leader_is_still_reaped(self):
        process = Mock(pid=41019)
        process.wait.return_value = 0
        with patch.object(module.os, "killpg", side_effect=ProcessLookupError) as kill:
            module.stop_process_group(process)
        self.assertEqual([call(41019, module.signal.SIGTERM), call(41019, module.signal.SIGKILL)], kill.call_args_list)
        self.assertEqual([call(timeout=10), call(timeout=10)], process.wait.call_args_list)

    def test_trigger_comparison_ignores_token_spacing_but_preserves_literals(self):
        first = "BEGIN IF NEW.status <> _binary'DRAFT' THEN -- comment\n SIGNAL SQLSTATE '45000'; END IF; END"
        formatted = "BEGIN IF NEW.status<>_binary 'DRAFT' THEN SIGNAL SQLSTATE '45000'; END IF; END"
        self.assertEqual(module.sql_tokens(first), module.sql_tokens(formatted))
        self.assertNotEqual(module.sql_tokens(first), module.sql_tokens(formatted.replace("DRAFT", "DRA FT")))
        self.assertNotEqual(module.sql_tokens(first), module.sql_tokens(formatted.replace("<>", "=")))

    def test_tmpfs_requires_both_safe_docker_config_and_actual_kernel_mount(self):
        value = {"Mounts": [], "HostConfig": {"Tmpfs": {"/var/lib/mysql": "rw,nosuid,nodev,size=2g"}}}
        live = "tmpfs /var/lib/mysql tmpfs rw,nosuid,nodev,size=2097152k 0 0\n"
        self.assertEqual("tmpfs", module.validate_data_mounts(value, live)[2])
        for bad in (live.replace(" tmpfs rw", " ext4 rw"), "", live.replace(",nodev", "")):
            with self.assertRaises(RuntimeError):
                module.validate_data_mounts(value, bad)
        value["Mounts"] = [{"Type": "volume", "Destination": "/var/lib/mysql"}]
        with self.assertRaises(RuntimeError):
            module.validate_data_mounts(value, live)

    def test_representative_payloads_match_independent_approved_digests(self):
        for _, _, _, digest, path, raw in representative_migrations():
            self.assertEqual(digest, module.payload_digest(raw), path.name)

    def test_default_entry_accepts_both_reviewed_chains_without_external_execution(self):
        with isolated_cli([]) as output:
            module.main()
        output.assert_called_once_with(
            "Approved payloads verified: runtime V001-V042 and RULES V001-V012. SQL files remain unchanged.",
            flush=True)

    def test_unknown_file_in_either_schema_fails_both_cli_modes_before_external_execution(self):
        real_glob = Path.glob
        for directory, name, schema in (
                ("src/main/resources/db/migration", "V043__unreviewed.sql", "dnd_tool_se"),
                ("database/rules/migration", "V013__unreviewed.sql", "dnd_tool_rules"),
        ):
            def with_extra(path, pattern):
                yield from real_glob(path, pattern)
                if path == module.ROOT / directory and pattern == "V*.sql":
                    yield path / name

            for arguments in ([], ["--execute-disposable"]):
                with self.subTest(schema=schema, arguments=arguments), isolated_cli(arguments) as output, \
                        patch.object(Path, "glob", with_extra):
                    with self.assertRaisesRegex(RuntimeError, "Unexpected migration files: " + schema):
                        module.main()
                    output.assert_not_called()

    def test_future_runtime_manifest_entry_requires_a_new_review(self):
        real_read = Path.read_text

        def with_extra(path, *arguments, **keywords):
            source = real_read(path, *arguments, **keywords)
            if path.name == "SchemaMigrations.java":
                last = "new Definition(V042_VERSION, V042_SCRIPT_NAME, V042_APPROVED_SHA256)"
                self.assertIn(last, source)
                return source.replace(last, last + ", " + last.replace("V042", "V043"))
            return source

        with isolated_cli(["--execute-disposable"]) as output, patch.object(Path, "read_text", with_extra):
            with self.assertRaisesRegex(RuntimeError, "requires review when the runtime chain changes"):
                module.main()
            output.assert_not_called()

    def test_representative_replay_binds_roles_schemas_bytes_and_narrow_grants(self):
        acceptance = object.__new__(module.Acceptance)
        acceptance.migrations = representative_migrations()
        acceptance.container = "owned-fixture"
        acceptance.port = 43307
        acceptance.passwords = {name: "fixture-" + name for name in module.ACCOUNTS}
        acceptance.raw_hashes = {path: module.sha(raw) for _, _, _, _, path, raw in acceptance.migrations}
        acceptance.run = Mock(return_value=CompletedProcess([], 0, b"", b""))
        acceptance.mysql = Mock(wraps=acceptance.mysql)
        with patch("builtins.print"):
            acceptance.migrate()
        replays = [call.kwargs for call in acceptance.mysql.call_args_list if "approved" in call.kwargs]
        self.assertEqual([
            dict(user="dnd_tool_se_migration_replay", schema="dnd_tool_se", path=acceptance.migrations[0][4],
                 approved="6df15d978dc79d0298c7cf4fc470f75633b58199795b4a3fae4f1a1bf44f3173"),
            dict(user="dnd_tool_rules_migrator", schema="dnd_tool_rules", path=acceptance.migrations[1][4],
                 approved="2a5a8a67ebef60754ff9ca572d651878558ccc56ed2bfd311ce811ef42082c40"),
        ], replays)
        payloads = [entry[5] for entry in acceptance.migrations]
        executions = [entry for entry in acceptance.run.call_args_list if entry.kwargs["data"] in payloads]
        self.assertEqual(payloads, [entry.kwargs["data"] for entry in executions])
        for execution, user, schema, protocol in zip(executions,
                ("dnd_tool_se_migration_replay", "dnd_tool_rules_migrator"),
                ("dnd_tool_se", "dnd_tool_rules"), ("SOCKET", "TCP"), strict=True):
            command = execution.args[0]
            self.assertIn("--user=" + user, command)
            self.assertIn("--database=" + schema, command)
            self.assertIn("--protocol=" + protocol, command)
            self.assertIn("--binary-mode", command)
            self.assertEqual("fixture-" + user, execution.kwargs["env"]["MYSQL_PWD"])
        self.assertIn("--host=127.0.0.1", executions[1].args[0])
        self.assertIn("--port=43307", executions[1].args[0])
        grants = [call.kwargs["path"].name for call in acceptance.mysql.call_args_list
                  if "path" in call.kwargs and "approved" not in call.kwargs]
        self.assertEqual(["rule-source-migrator.sql", "rule-source-installer.sql", "rule-source-app.sql",
                          "runtime-language-snapshot.sql", "runtime-tool-snapshot.sql", "business-write-guard.sql"], grants)
        self.assertNotIn("dnd_tool_rules_accepter", module.ACCOUNTS)

    def test_current_replay_uses_both_complete_chains_and_keeps_narrow_runtime_grants(self):
        acceptance = object.__new__(module.Acceptance)
        acceptance.migrations = module.manifests()
        acceptance.passwords = {name: "fixture-" + name for name in module.ACCOUNTS}
        acceptance.mysql = Mock()
        with patch("builtins.print"):
            acceptance.migrate()
        approved_replays = [entry.kwargs for entry in acceptance.mysql.call_args_list if "approved" in entry.kwargs]
        self.assertEqual(54, len(approved_replays))
        self.assertEqual([("dnd_tool_se", "dnd_tool_se_migration_replay")] * 42
                         + [("dnd_tool_rules", "dnd_tool_rules_migrator")] * 12,
                         [(entry["schema"], entry["user"]) for entry in approved_replays])
        self.assertEqual([f"V{number:03d}" for number in (*range(1, 43), *range(1, 13))],
                         [entry["path"].name.split("__")[0] for entry in approved_replays])
        self.assertEqual("V042__schema2-spell-save-damage.sql", approved_replays[41]["path"].name)
        self.assertEqual("V012__schema2-spell-save-damage-source.sql", approved_replays[-1]["path"].name)
        grants = [entry.kwargs["path"].name for entry in acceptance.mysql.call_args_list
                  if "path" in entry.kwargs and "approved" not in entry.kwargs]
        self.assertEqual(["rule-source-migrator.sql", "rule-source-installer.sql", "rule-source-app.sql",
                          "runtime-language-snapshot.sql", "runtime-tool-snapshot.sql", "business-write-guard.sql"], grants)

    def test_audit_profile_includes_current_chain_and_final_trigger_definitions(self):
        tables, triggers = module.profile_objects(module.manifests())
        self.assertTrue({"rule_schema_meta", "rule_release", "rule_language", "rule_tool",
                         "rule_package_installation", "rule_package_installation_partition",
                         "rule_installation_control", "rule_schema2_control", "rule_schema2_wizard_change",
                         "rule_schema2_monster_action_value"}.issubset(tables[module.SOURCE]))
        runtime = module.runtime_audit_tables(tables[module.RUNTIME])
        self.assertTrue({"runtime_run_identity", "runtime_rule_snapshot", "runtime_rule_language",
                         "runtime_rule_tool", "business_write_guard", "s2_runtime_snapshot", "s2_runtime_control",
                         "s2_character_arcane_recovery_choice", "s2_combat_spell_damage_source"}.issubset(runtime))
        self.assertEqual(159, len(triggers[module.SOURCE]))
        self.assertEqual(305, len(triggers[module.RUNTIME]))
        event, table, timing, body = triggers[module.RUNTIME]["s2_character_combat_input_insert"]
        self.assertEqual(("INSERT", "s2_character_combat_input", "BEFORE"), (event, table, timing))
        self.assertIn("BETWEEN 1 AND 28800", body)
        self.assertEqual("character_proficiency_grant_v2", triggers[module.RUNTIME]["character_grant_insert"][1])
        event, table, timing, body = triggers[module.RUNTIME]["character_grant_insert"]
        show_create = ("character_grant_insert\tSTRICT_TRANS_TABLES\t"
                       f"CREATE DEFINER=`migrator`@`localhost` TRIGGER character_grant_insert {timing} {event} ON {table}\n"
                       f"FOR EACH ROW\n{body.strip()}\tutf8mb4\tutf8mb4_0900_ai_ci\tutf8mb4_0900_bin\t2026-01-01 00:00:00.00\n")
        self.assertEqual(module.sql_tokens(body), module.sql_tokens(module.show_trigger_body(show_create, "character_grant_insert")))

    def test_disposable_audit_rejects_broadened_or_missing_direct_privileges(self):
        tables, _ = module.profile_objects(module.manifests())
        grants = reviewed_access_grants()
        module.validate_access_grants(grants, tables[module.SOURCE])
        for role, replacement in (
                ("dnd_tool_rules_app", grants["grants-dnd_tool_rules_app"].replace("GRANT SELECT", "GRANT SELECT, INSERT", 1)),
                ("dnd_tool_se_validation_ro", grants["grants-dnd_tool_se_validation_ro"].replace("127.0.0.1", "%")),
                ("dnd_tool_se_validation_ro", grants["grants-dnd_tool_se_validation_ro"].replace(r"\_", "_")),
                ("dnd_tool_se_app", grants["grants-dnd_tool_se_app"] +
                 "GRANT INSERT ON `dnd_tool_se`.`s2_combat_event` TO `dnd_tool_se_app`@`127.0.0.1`\n"),
        ):
            with self.subTest(role=role), self.assertRaisesRegex(RuntimeError, "Unexpected.*grant"):
                module.validate_access_grants({**grants, "grants-" + role: replacement}, tables[module.SOURCE])

    def test_live_ledger_is_checked_as_each_reader_and_rejects_a_missing_tail(self):
        acceptance = object.__new__(module.Acceptance)
        acceptance.migrations = module.manifests()
        acceptance.tables, acceptance.triggers = module.profile_objects(acceptance.migrations)
        rows = {schema: [f"{version}\t{name}\t{digest}\n"
                         for side, version, name, digest, _, _ in acceptance.migrations if side == schema]
                for schema in (module.RUNTIME, module.SOURCE)}
        acceptance.mysql = Mock(side_effect=["".join(rows[module.RUNTIME]), "".join(rows[module.SOURCE][:-1])])
        acceptance.save = Mock()
        with self.assertRaisesRegex(RuntimeError, "Live ledger differs from approved manifest: dnd_tool_rules"):
            acceptance.audit()
        ledger_reads = acceptance.mysql.call_args_list
        self.assertEqual([{"user": "dnd_tool_se_validation_ro", "schema": "dnd_tool_se"},
                          {"user": "dnd_tool_rules_app", "schema": "dnd_tool_rules"}],
                         [read.kwargs for read in ledger_reads])
        self.assertIn("FROM schema_meta", ledger_reads[0].args[0])
        self.assertIn("FROM rule_schema_meta", ledger_reads[1].args[0])
        acceptance.save.assert_not_called()

    def test_connected_server_with_unsupported_grant_mode_is_never_ready(self):
        for version, partial_revokes in (("8.0.41", "1"), ("8.4.1", "0")):
            acceptance = object.__new__(module.Acceptance)
            acceptance.name = "owned"
            acceptance.token = "fixture"
            acceptance.passwords = {"root": "mock-root"}
            acceptance.server_uuid = None
            image = {"Os": "linux", "Id": "mysql-image", "Config": {"Volumes": {"/var/lib/mysql": {}}}}
            acceptance.docker = Mock(side_effect=[
                CompletedProcess([], 0, json.dumps([image]).encode(), b""),
                CompletedProcess([], 0, ("a" * 64).encode(), b""),
                CompletedProcess([], 0, b"", b""),
            ])
            acceptance.inspect = Mock(return_value={
                "Image": image["Id"], "HostConfig": {"NetworkMode": "host"}, "State": {"Running": True},
            })
            acceptance.mysql = Mock(side_effect=[
                RuntimeError("transient startup"),
                "43307\t0\tfixture-uuid\t" + version + "\t" + partial_revokes + "\n",
            ])
            with self.subTest(version=version, partial_revokes=partial_revokes), \
                    patch.object(module.socket, "socket") as socket, patch.object(module.time, "sleep") as sleep:
                socket.return_value.__enter__.return_value.getsockname.return_value = ("127.0.0.1", 43307)
                with self.assertRaisesRegex(RuntimeError, "Unsupported MySQL or grant mode"):
                    acceptance.start()
            self.assertIsNone(acceptance.server_uuid)
            self.assertEqual(3, acceptance.docker.call_count)
            sleep.assert_called_once_with(1)

    def test_checksum_normalizes_only_for_comparison_and_rejects_bad_scopes(self):
        raw = b"-- CHECKSUM-SCOPE-BEGIN\nSELECT 1;\n-- CHECKSUM-SCOPE-END\n"
        self.assertEqual(module.payload_digest(raw), module.payload_digest(raw.replace(b"\n", b"\r\n")))
        for bad in (raw + raw, raw.replace(b"\nSELECT", b"SELECT"), b"\xff" + raw):
            with self.assertRaises((RuntimeError, UnicodeError)):
                module.payload_digest(bad)

    def test_replays_exact_validated_raw_bytes_without_second_execution_read(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "migration.sql"
            raw = b"USE dnd_tool_se;\r\n-- CHECKSUM-SCOPE-BEGIN\r\nSELECT 1;\r\n-- CHECKSUM-SCOPE-END\r\n"
            path.write_bytes(raw)
            acceptance = object.__new__(module.Acceptance)
            acceptance.container = "owned"
            acceptance.passwords = {"root": "unused"}
            acceptance.raw_hashes = {path: module.sha(raw)}
            def execute(command, **arguments):
                path.write_bytes(b"changed after validation")
                self.assertEqual(raw, arguments["data"])
                return CompletedProcess(command, 0, b"", b"")
            acceptance.run = execute
            acceptance.mysql(path=path, approved=module.payload_digest(raw))

    def test_out_of_scope_file_change_is_rejected_before_mysql(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "migration.sql"
            raw = b"-- CHECKSUM-SCOPE-BEGIN\nSELECT 1;\n-- CHECKSUM-SCOPE-END\n"
            path.write_bytes(b"USE unintended;\n" + raw)
            acceptance = object.__new__(module.Acceptance)
            acceptance.container = "owned"
            acceptance.passwords = {"root": "unused"}
            acceptance.raw_hashes = {path: module.sha(raw)}
            acceptance.run = Mock()
            with self.assertRaisesRegex(RuntimeError, "changed before execution"):
                acceptance.mysql(path=path, approved=module.payload_digest(raw))
            acceptance.run.assert_not_called()

    def test_foreign_label_never_allows_removal(self):
        acceptance = object.__new__(module.Acceptance)
        acceptance.container = "a" * 64
        acceptance.token = "owned"
        acceptance.create_attempted = True
        acceptance.docker = Mock(return_value=CompletedProcess([], 0, json.dumps([
            {"Id": acceptance.container, "Config": {"Labels": {module.LABEL: "foreign"}}}
        ]).encode(), b""))
        with self.assertRaisesRegex(RuntimeError, "ownership mismatch"):
            acceptance.close()
        self.assertEqual([("inspect", acceptance.container)], [call.args for call in acceptance.docker.call_args_list])

    def test_lost_create_response_recovers_only_exact_name_and_label(self):
        acceptance = object.__new__(module.Acceptance)
        acceptance.container = None
        acceptance.token = "owned"
        acceptance.name = "dnd-language-jdbc-owned"
        acceptance.create_attempted = True
        identity = "a" * 64
        acceptance.docker = Mock(side_effect=[
            CompletedProcess([], 0, (identity + "\n").encode(), b""),
            CompletedProcess([], 0, json.dumps([{"Id": identity, "Config": {"Labels": {module.LABEL: "owned"}}}]).encode(), b""),
            CompletedProcess([], 0, b"", b""), CompletedProcess([], 0, b"", b"")])
        acceptance.save = Mock()
        acceptance.close()
        query = acceptance.docker.call_args_list[0].args
        self.assertIn("name=^/dnd-language-jdbc-owned$", query)
        self.assertIn("label=" + module.LABEL + "=owned", query)
        self.assertEqual(("rm", "--force", identity), acceptance.docker.call_args_list[2].args)

    def test_skipped_or_empty_acceptance_is_not_success(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "report.xml"
            for attributes in ('tests="0"', 'tests="1" skipped="1"', 'tests="1" errors="1"', 'tests="1" failures="1"'):
                report.write_text("<testsuite " + attributes + "/>")
                with self.assertRaises(RuntimeError):
                    module.report_assertions(report, 1)


if __name__ == "__main__":
    unittest.main()
