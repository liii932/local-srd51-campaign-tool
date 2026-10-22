"""Offline regression checks for the external acceptance boundary; run this file directly."""
import importlib.util
import json
from pathlib import Path
from subprocess import CompletedProcess, TimeoutExpired
import tempfile
import unittest
from unittest.mock import Mock, call, patch


spec = importlib.util.spec_from_file_location("language_jdbc", Path(__file__).resolve().parents[1] / "verify-language-jdbc.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


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

    def test_approved_manifest_is_read_only_and_complete(self):
        migrations = module.manifests()
        self.assertEqual(28, len(migrations))
        self.assertEqual(list(range(1, 25)), [item[1] for item in migrations if item[0] == module.RUNTIME])
        self.assertEqual([1, 2, 3, 4], [item[1] for item in migrations if item[0] == module.SOURCE])
        for _, _, _, digest, path, raw in migrations:
            self.assertEqual(raw, path.read_bytes())
            self.assertEqual(digest, module.payload_digest(raw))

    def test_default_entry_validates_current_manifests_without_external_execution(self):
        with patch("sys.argv", ["verify-language-jdbc.py"]), \
                patch.object(module, "Acceptance", side_effect=AssertionError("No disposable executor")), \
                patch.object(module.subprocess, "run", side_effect=AssertionError("No subprocess")), \
                patch.object(module.subprocess, "Popen", side_effect=AssertionError("No subprocess")), \
                patch.object(module.tempfile, "mkdtemp", side_effect=AssertionError("No audit directory")), \
                patch.object(module.socket, "socket", side_effect=AssertionError("No network")), \
                patch("builtins.print") as output:
            module.main()
        self.assertEqual("Approved payloads verified: runtime V001-V024 and RULES V001-V004. SQL files remain unchanged.",
                         output.call_args.args[0])

    def test_disposable_replay_covers_current_chains_and_retains_narrow_language_tool_grants(self):
        acceptance = object.__new__(module.Acceptance)
        acceptance.migrations = module.manifests()
        acceptance.passwords = {name: "unused-fixture" for name in module.ACCOUNTS}
        acceptance.mysql = Mock()
        with patch("builtins.print"):
            acceptance.migrate()
        replays = [call.kwargs for call in acceptance.mysql.call_args_list if "approved" in call.kwargs]
        self.assertEqual(28, len(replays))
        self.assertEqual({"V021__schema2-runtime-snapshot.sql", "V003__schema2-source-ledger.sql"},
                         {call["path"].name for call in replays if call["path"].name.startswith(("V021__", "V003__schema2"))})
        grants = [call.kwargs["path"].name for call in acceptance.mysql.call_args_list
                  if "path" in call.kwargs and "approved" not in call.kwargs]
        self.assertEqual(["rule-source-migrator.sql", "rule-source-installer.sql", "rule-source-app.sql",
                          "runtime-language-snapshot.sql", "runtime-tool-snapshot.sql"], grants)
        self.assertNotIn("dnd_tool_rules_accepter", module.ACCOUNTS)
        self.assertEqual({"rule_schema2_artifact", "rule_schema2_acceptance", "rule_schema2_control", "rule_schema2_character_source", "rule_schema2_proficiency_grant", "rule_schema2_character_change"},
                         {table for table in module.SOURCE_TABLES if table.startswith("rule_schema2_")})
        expected_runtime = {"s2_runtime_identity", "s2_runtime_snapshot", "s2_runtime_member", "s2_runtime_language",
                            "s2_runtime_language_set", "s2_runtime_language_entry", "s2_runtime_field_chain",
                            "s2_runtime_chain_step", "s2_runtime_operation", "s2_runtime_control", "s2_runtime_tool", "s2_runtime_tool_set", "s2_runtime_tool_entry", "s2_runtime_character_source", "s2_runtime_proficiency_grant"}
        self.assertEqual(expected_runtime, set(module.SCHEMA2_RUNTIME_TABLES))
        self.assertTrue(expected_runtime.issubset(module.RUNTIME_TABLES))

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
