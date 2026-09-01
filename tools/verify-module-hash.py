#!/usr/bin/env python3
"""Independently compute the reviewed built-in module's canonical v1 SHA-256.

This reference implementation deliberately does not import or invoke the Java
encoder. It asks the configured MySQL client to execute hard-coded SELECTs with
the repository's read-only Agent account, validates the reviewed V003 row
counts, accepts only the reviewed pre-publication or published metadata state,
constructs the canonical bytes from the normative projection table,
and prints only identity, byte length, row counts, and the final digest.

The external option file is passed directly to mysql as its first client
option. This script verifies only that the path names a file; it never opens,
reads, copies, hashes, or prints the option-file contents.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import struct
import subprocess
import sys
import unicodedata
from dataclasses import dataclass
from decimal import Decimal
from pathlib import Path
from typing import Callable, Iterable, Sequence


MAGIC = b"DND_TOOL_SE_MODULE_CANONICAL"
FORMAT_VERSION = 1
MODULE_KEY = "dnd5e2014_srd51_se_v1"
RELEASE_VERSION = "1"
APPROVED_SHA256 = "8c58297049084b808fcf27b888efb7b9345989cafef137a1200f092853c3731e"

NULL = b"\x00"
TEXT_TAG = 0x01
IDENTIFIER_TAG = 0x02
INTEGER_TAG = 0x03
DECIMAL_TAG = 0x04
BOOLEAN_TAG = 0x05

RELEASE_ID_SQL = (
    "(SELECT id FROM module_release "
    f"WHERE module_key = '{MODULE_KEY}' AND release_version = '{RELEASE_VERSION}')"
)


class ReferenceHashError(Exception):
    """Carries no database values or client defaults into user-facing output."""


@dataclass(frozen=True)
class Partition:
    name: str
    rows: tuple[tuple[tuple[str, bytes], ...], ...]


def u32(value: int) -> bytes:
    if value < 0 or value > 2_147_483_647:
        raise ReferenceHashError()
    return struct.pack(">I", value)


def ascii_bytes(value: str, *, allow_empty: bool = False) -> bytes:
    if not isinstance(value, str) or (not value and not allow_empty):
        raise ReferenceHashError()
    try:
        return value.encode("ascii", "strict")
    except UnicodeEncodeError as exception:
        raise ReferenceHashError() from exception


def normalized_text(value: str) -> str:
    if not isinstance(value, str):
        raise ReferenceHashError()
    lines = value.replace("\r\n", "\n").replace("\r", "\n")
    normalized = unicodedata.normalize("NFC", lines)
    try:
        normalized.encode("utf-8", "strict")
    except UnicodeEncodeError as exception:
        raise ReferenceHashError() from exception
    return normalized


def name(value: str) -> bytes:
    payload = ascii_bytes(value)
    return u32(len(payload)) + payload


def scalar(tag: int, payload: bytes) -> bytes:
    return bytes((tag,)) + u32(len(payload)) + payload


def text_value(value: str) -> bytes:
    return scalar(TEXT_TAG, normalized_text(value).encode("utf-8", "strict"))


def identifier_value(value: str) -> bytes:
    return scalar(IDENTIFIER_TAG, ascii_bytes(value))


def integer_value(value: int) -> bytes:
    if isinstance(value, bool) or not isinstance(value, int):
        raise ReferenceHashError()
    return scalar(INTEGER_TAG, str(value).encode("ascii"))


def decimal_value(value: str) -> bytes:
    if not isinstance(value, str):
        raise ReferenceHashError()
    decimal = Decimal(value)
    if not decimal.is_finite():
        raise ReferenceHashError()
    # format(..., "f") preserves every stored digit without applying Decimal's
    # ambient precision context; only insignificant fractional zeroes are removed.
    canonical = format(decimal, "f")
    if "." in canonical:
        canonical = canonical.rstrip("0").rstrip(".")
    if decimal.is_zero():
        canonical = "0"
    return scalar(DECIMAL_TAG, canonical.encode("ascii"))


def boolean_value(value: int | bool) -> bytes:
    if value not in (0, 1, False, True):
        raise ReferenceHashError()
    return scalar(BOOLEAN_TAG, b"\x01" if bool(value) else b"\x00")


def typed_value(
    value_type: str,
    text: object,
    identifier: object,
    integer: object,
    decimal: object,
    boolean: object,
) -> bytes:
    if value_type == "TEXT" and isinstance(text, str):
        return text_value(text)
    if value_type == "IDENTIFIER" and isinstance(identifier, str):
        return identifier_value(identifier)
    if value_type == "INTEGER" and isinstance(integer, int):
        return integer_value(integer)
    if value_type == "DECIMAL" and isinstance(decimal, str):
        return decimal_value(decimal)
    if value_type == "BOOLEAN" and boolean in (0, 1, False, True):
        return boolean_value(boolean)
    raise ReferenceHashError()


def nullable_identifier(value: object) -> bytes:
    return NULL if value is None else identifier_value(require_str(value))


def nullable_text(value: object) -> bytes:
    return NULL if value is None else text_value(require_str(value))


def nullable_integer(value: object) -> bytes:
    return NULL if value is None else integer_value(require_int(value))


def nullable_decimal(value: object) -> bytes:
    return NULL if value is None else decimal_value(require_str(value))


def nullable_boolean(value: object) -> bytes:
    return NULL if value is None else boolean_value(value)


def require_str(value: object) -> str:
    if not isinstance(value, str):
        raise ReferenceHashError()
    return value


def require_int(value: object) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise ReferenceHashError()
    return value


def sort_bytes(value: object) -> bytes:
    return normalized_text(require_str(value)).encode("utf-8", "strict")


def row(*fields: tuple[str, bytes]) -> tuple[tuple[str, bytes], ...]:
    return tuple(fields)


def encode_record(fields: Sequence[tuple[str, bytes]]) -> bytes:
    output = bytearray(u32(len(fields)))
    for field_name, encoded_value in fields:
        output.extend(name(field_name))
        output.extend(encoded_value)
    return bytes(output)


def encode_partition(partition: Partition) -> bytes:
    output = bytearray(name(partition.name))
    output.extend(u32(len(partition.rows)))
    for fields in partition.rows:
        output.extend(encode_record(fields))
    return bytes(output)


class ReadOnlyMysql:
    def __init__(self, client: Path, option_file: Path, database: str) -> None:
        if not client.is_file() or not option_file.is_file():
            raise ReferenceHashError()
        self._client = client
        self._option_file = option_file
        self._database = database

    def rows(self, sql: str) -> list[list[object]]:
        normalized = sql.strip().upper()
        if not normalized.startswith("SELECT ") or ";" in sql:
            raise ReferenceHashError()
        command = [
            str(self._client),
            f"--defaults-extra-file={self._option_file}",
            f"--database={self._database}",
            "--host=127.0.0.1",
            "--port=3306",
            "--protocol=TCP",
            "--connect-timeout=5",
            "--default-character-set=utf8mb4",
            "--batch",
            "--raw",
            "--skip-column-names",
            f"--execute={sql}",
        ]
        try:
            completed = subprocess.run(
                command,
                check=False,
                capture_output=True,
                timeout=15,
            )
        except (OSError, subprocess.TimeoutExpired) as exception:
            raise ReferenceHashError() from exception
        if completed.returncode != 0:
            raise ReferenceHashError()
        try:
            lines = completed.stdout.decode("utf-8", "strict").splitlines()
            return [json.loads(line) for line in lines if line]
        except (UnicodeDecodeError, json.JSONDecodeError) as exception:
            raise ReferenceHashError() from exception


def json_query(columns: str, table: str) -> str:
    return (
        f"SELECT JSON_ARRAY({columns}) FROM {table} "
        f"WHERE module_release_id = {RELEASE_ID_SQL}"
    )


def sorted_rows(
    raw_rows: Iterable[list[object]],
    key: Callable[[list[object]], object],
    mapper: Callable[[list[object]], tuple[tuple[str, bytes], ...]],
) -> tuple[tuple[tuple[str, bytes], ...], ...]:
    source = list(raw_rows)
    source.sort(key=key)
    keys = [key(item) for item in source]
    if len(keys) != len(set(keys)):
        raise ReferenceHashError()
    return tuple(mapper(item) for item in source)


def field_bound(data_type: str, integer: object, decimal: object) -> bytes:
    if data_type == "INTEGER":
        return nullable_integer(integer)
    if data_type == "DECIMAL":
        return nullable_decimal(decimal)
    if data_type in ("TEXT", "BOOLEAN") and integer is None and decimal is None:
        return NULL
    raise ReferenceHashError()


def parameter_bound(data_type: str, integer: object, decimal: object) -> bytes:
    if data_type in ("INTEGER", "TEXT"):
        return nullable_integer(integer)
    if data_type == "DECIMAL":
        return nullable_decimal(decimal)
    if data_type in ("REFERENCE", "BOOLEAN") and integer is None and decimal is None:
        return NULL
    raise ReferenceHashError()


def load_partitions(mysql: ReadOnlyMysql) -> list[Partition]:
    release_rows = mysql.rows(
        "SELECT JSON_ARRAY(module_key, release_version, canonical_format_version, "
        "hash_algorithm, content_sha256, release_status, released_at IS NULL) "
        "FROM module_release WHERE module_key = 'dnd5e2014_srd51_se_v1' "
        "AND release_version = '1'"
    )
    draft_state = [MODULE_KEY, RELEASE_VERSION, 1, "SHA-256", None, "DRAFT", 1]
    released_state = [
        MODULE_KEY,
        RELEASE_VERSION,
        1,
        "SHA-256",
        APPROVED_SHA256,
        "RELEASED",
        0,
    ]
    if release_rows not in ([draft_state], [released_state]):
        raise ReferenceHashError()

    partitions: list[Partition] = [Partition("release", (row(
        ("module_key", identifier_value(MODULE_KEY)),
        ("release_version", identifier_value(RELEASE_VERSION)),
        ("canonical_format_version", integer_value(FORMAT_VERSION)),
        ("hash_algorithm", identifier_value("SHA-256")),
    ),))]

    raw = mysql.rows(json_query(
        "constant_key, value_type, text_value, identifier_value, integer_value, "
        "CAST(decimal_value AS CHAR), boolean_value",
        "module_rule_constant"))
    partitions.append(Partition("rule_constant", sorted_rows(raw,
        lambda r: sort_bytes(r[0]),
        lambda r: row(
            ("constant_key", identifier_value(require_str(r[0]))),
            ("value_type", identifier_value(require_str(r[1]))),
            ("value", typed_value(require_str(r[1]), *r[2:7])),
        ))))

    raw = mysql.rows(json_query(
        "field_key, display_name, data_type, default_text, default_integer, "
        "CAST(default_decimal AS CHAR), default_boolean, minimum_integer, "
        "maximum_integer, CAST(minimum_decimal AS CHAR), "
        "CAST(maximum_decimal AS CHAR), dependent_max_field_key, unit, description",
        "module_field_definition"))
    partitions.append(Partition("field_definition", sorted_rows(raw,
        lambda r: sort_bytes(r[0]),
        lambda r: row(
            ("field_key", identifier_value(require_str(r[0]))),
            ("display_name", text_value(require_str(r[1]))),
            ("data_type", identifier_value(require_str(r[2]))),
            ("default_value", typed_value(
                require_str(r[2]), r[3], None, r[4], r[5], r[6])),
            ("minimum_value", field_bound(require_str(r[2]), r[7], r[9])),
            ("maximum_value", field_bound(require_str(r[2]), r[8], r[10])),
            ("dependent_max_field_key", nullable_identifier(r[11])),
            ("unit", nullable_text(r[12])),
            ("description", text_value(require_str(r[13]))),
        ))))

    def simple_partition(
        partition_name: str,
        table: str,
        columns: str,
        key: Callable[[list[object]], object],
        mapper: Callable[[list[object]], tuple[tuple[str, bytes], ...]],
    ) -> None:
        partitions.append(Partition(
            partition_name,
            sorted_rows(mysql.rows(json_query(columns, table)), key, mapper)))

    simple_partition("class_definition", "module_class_definition",
        "class_key, display_name", lambda r: sort_bytes(r[0]), lambda r: row(
            ("class_key", identifier_value(require_str(r[0]))),
            ("display_name", text_value(require_str(r[1])))))
    simple_partition("proficiency_tier", "module_proficiency_tier",
        "proficiency_key, enum_code, numerator, denominator, rounding_algorithm",
        lambda r: sort_bytes(r[0]), lambda r: row(
            ("proficiency_key", identifier_value(require_str(r[0]))),
            ("enum_code", identifier_value(require_str(r[1]))),
            ("numerator", integer_value(require_int(r[2]))),
            ("denominator", integer_value(require_int(r[3]))),
            ("rounding_algorithm", identifier_value(require_str(r[4])))))
    simple_partition("proficiency_bonus_band", "module_proficiency_bonus_band",
        "minimum_total_level, maximum_total_level, bonus",
        lambda r: (require_int(r[0]), require_int(r[1]), require_int(r[2])),
        lambda r: row(
            ("minimum_total_level", integer_value(require_int(r[0]))),
            ("maximum_total_level", integer_value(require_int(r[1]))),
            ("bonus", integer_value(require_int(r[2])))))
    simple_partition("skill_definition", "module_skill_definition",
        "skill_key, display_name, ability_field_key", lambda r: sort_bytes(r[0]),
        lambda r: row(
            ("skill_key", identifier_value(require_str(r[0]))),
            ("display_name", text_value(require_str(r[1]))),
            ("ability_field_key", identifier_value(require_str(r[2])))))
    simple_partition("save_definition", "module_save_definition",
        "save_key, ability_field_key", lambda r: sort_bytes(r[0]), lambda r: row(
            ("save_key", identifier_value(require_str(r[0]))),
            ("ability_field_key", identifier_value(require_str(r[1])))))
    simple_partition("item_template", "module_item_template",
        "item_key, display_name, description", lambda r: sort_bytes(r[0]), lambda r: row(
            ("item_key", identifier_value(require_str(r[0]))),
            ("display_name", text_value(require_str(r[1]))),
            ("description", text_value(require_str(r[2])))))
    simple_partition("npc_template", "module_entity_template",
        "template_key, display_name", lambda r: sort_bytes(r[0]), lambda r: row(
            ("template_key", identifier_value(require_str(r[0]))),
            ("display_name", text_value(require_str(r[1])))))

    raw = mysql.rows(json_query(
        "template_key, field_key, value_type, text_value, integer_value, "
        "CAST(decimal_value AS CHAR), boolean_value",
        "module_entity_template_value"))
    partitions.append(Partition("npc_template_field_value", sorted_rows(raw,
        lambda r: (sort_bytes(r[0]), sort_bytes(r[1])), lambda r: row(
            ("template_key", identifier_value(require_str(r[0]))),
            ("field_key", identifier_value(require_str(r[1]))),
            ("value", typed_value(
                require_str(r[2]), r[3], None, r[4], r[5], r[6])),
        ))))
    simple_partition("npc_template_class_level", "module_entity_template_class_level",
        "template_key, class_key, level",
        lambda r: (sort_bytes(r[0]), sort_bytes(r[1])), lambda r: row(
            ("template_key", identifier_value(require_str(r[0]))),
            ("class_key", identifier_value(require_str(r[1]))),
            ("level", integer_value(require_int(r[2])))))
    simple_partition("npc_template_proficiency", "module_entity_template_proficiency",
        "template_key, target_kind, target_key, proficiency_key",
        lambda r: (sort_bytes(r[0]), sort_bytes(r[1]), sort_bytes(r[2])), lambda r: row(
            ("template_key", identifier_value(require_str(r[0]))),
            ("target_kind", identifier_value(require_str(r[1]))),
            ("target_key", identifier_value(require_str(r[2]))),
            ("proficiency_key", identifier_value(require_str(r[3])))))
    simple_partition("check_definition", "module_check_definition",
        "check_key, enum_code, modifier_algorithm", lambda r: sort_bytes(r[0]), lambda r: row(
            ("check_key", identifier_value(require_str(r[0]))),
            ("enum_code", identifier_value(require_str(r[1]))),
            ("modifier_algorithm", identifier_value(require_str(r[2])))))
    simple_partition("roll_mode", "module_roll_mode",
        "roll_mode_key, enum_code, candidate_count, selection_algorithm",
        lambda r: sort_bytes(r[0]), lambda r: row(
            ("roll_mode_key", identifier_value(require_str(r[0]))),
            ("enum_code", identifier_value(require_str(r[1]))),
            ("candidate_count", integer_value(require_int(r[2]))),
            ("selection_algorithm", identifier_value(require_str(r[3])))))
    simple_partition("event_template", "module_event_template",
        "event_key, display_name", lambda r: sort_bytes(r[0]), lambda r: row(
            ("event_key", identifier_value(require_str(r[0]))),
            ("display_name", text_value(require_str(r[1])))))
    simple_partition("event_allowed_check", "module_event_check",
        "event_key, check_key", lambda r: (sort_bytes(r[0]), sort_bytes(r[1])), lambda r: row(
            ("event_key", identifier_value(require_str(r[0]))),
            ("check_key", identifier_value(require_str(r[1])))))
    simple_partition("event_allowed_effect", "module_event_effect",
        "event_key, effect_key", lambda r: (sort_bytes(r[0]), sort_bytes(r[1])), lambda r: row(
            ("event_key", identifier_value(require_str(r[0]))),
            ("effect_key", identifier_value(require_str(r[1])))))
    simple_partition("effect_definition", "module_effect_definition",
        "effect_key, execution_algorithm", lambda r: sort_bytes(r[0]), lambda r: row(
            ("effect_key", identifier_value(require_str(r[0]))),
            ("execution_algorithm", identifier_value(require_str(r[1])))))

    raw = mysql.rows(json_query(
        "effect_key, parameter_key, data_type, reference_kind, minimum_integer, "
        "maximum_integer, CAST(minimum_decimal AS CHAR), "
        "CAST(maximum_decimal AS CHAR), text_normalization, "
        "reject_control_characters, parameter_order",
        "module_effect_parameter"))
    partitions.append(Partition("effect_parameter", sorted_rows(raw,
        lambda r: (sort_bytes(r[0]), require_int(r[10]), sort_bytes(r[1])),
        lambda r: row(
            ("effect_key", identifier_value(require_str(r[0]))),
            ("parameter_key", identifier_value(require_str(r[1]))),
            ("data_type", identifier_value(require_str(r[2]))),
            ("reference_kind", nullable_identifier(r[3])),
            ("minimum_value", parameter_bound(require_str(r[2]), r[4], r[6])),
            ("maximum_value", parameter_bound(require_str(r[2]), r[5], r[7])),
            ("text_normalization", nullable_identifier(r[8])),
            ("reject_control_characters", nullable_boolean(r[9])),
            ("parameter_order", integer_value(require_int(r[10]))),
        ))))
    simple_partition("map_definition", "module_map_definition",
        "map_key, map_type", lambda r: sort_bytes(r[0]), lambda r: row(
            ("map_key", identifier_value(require_str(r[0]))),
            ("map_type", identifier_value(require_str(r[1])))))
    simple_partition("map_node", "module_map_node",
        "map_key, node_key, display_name",
        lambda r: (sort_bytes(r[0]), sort_bytes(r[1])), lambda r: row(
            ("map_key", identifier_value(require_str(r[0]))),
            ("node_key", identifier_value(require_str(r[1]))),
            ("display_name", text_value(require_str(r[2])))))
    simple_partition("map_connection", "module_map_connection",
        "map_key, endpoint_low_key, endpoint_high_key",
        lambda r: (sort_bytes(r[0]), sort_bytes(r[1]), sort_bytes(r[2])), lambda r: row(
            ("map_key", identifier_value(require_str(r[0]))),
            ("endpoint_low_key", identifier_value(require_str(r[1]))),
            ("endpoint_high_key", identifier_value(require_str(r[2])))))
    return partitions


EXPECTED_COUNTS = (
    1, 25, 10, 12, 4, 5, 18, 6, 3, 3, 30, 0, 72, 4, 3, 4, 3, 16, 5, 13, 1, 4, 3,
)


def canonical_bytes(partitions: Sequence[Partition]) -> bytes:
    if len(partitions) != 23:
        raise ReferenceHashError()
    counts = tuple(len(partition.rows) for partition in partitions)
    if counts != EXPECTED_COUNTS:
        raise ReferenceHashError()
    output = bytearray(MAGIC)
    output.extend(u32(FORMAT_VERSION))
    output.extend(u32(len(partitions)))
    for partition in partitions:
        output.extend(encode_partition(partition))
    return bytes(output)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Compute the independent canonical v1 built-in module digest")
    parser.add_argument("--mysql-client", required=True, type=Path)
    parser.add_argument("--defaults-extra-file", required=True, type=Path)
    parser.add_argument("--database", default="dnd_tool_se")
    return parser.parse_args()


def main() -> int:
    try:
        args = parse_args()
        mysql = ReadOnlyMysql(args.mysql_client, args.defaults_extra_file, args.database)
        partitions = load_partitions(mysql)
        payload = canonical_bytes(partitions)
        digest = hashlib.sha256(payload).hexdigest()
        print(f"module={MODULE_KEY}@{RELEASE_VERSION}")
        print(f"canonical_format_version={FORMAT_VERSION}")
        print(f"canonical_bytes={len(payload)}")
        print("partition_counts=" + ",".join(
            f"{partition.name}:{len(partition.rows)}" for partition in partitions))
        print(f"sha256={digest}")
        return 0
    except ReferenceHashError:
        print("reference_hash_status=FAILED", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
