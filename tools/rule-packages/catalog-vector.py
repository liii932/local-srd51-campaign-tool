#!/usr/bin/env python3
"""Check DRAFT author fields, or regenerate the separate application test vector."""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import re
import struct
import unicodedata

ROOT = Path(__file__).resolve().parents[2]
EXPECTED_ROWS = ROOT / 'rule-packages/srd51-complete-expected.tsv'
EXPECTED_CANONICAL_SHA256 = '2cda7fb75e4222f220bc93dc3b13b5c196ad2fa0a9e33a4b43000e348ae28245'
PARTITIONS = (
    ('language', 'character/languages.json', 18),
    ('tool', 'character/tools.json', 37),
)
ROW_FIELDS = ('key', 'display_name', 'description', 'category', 'source_page', 'sort_order')

def u32(value):
    return struct.pack('>I', value)

def lp(value):
    data = str(value).encode('utf-8')
    return u32(len(data)) + data

def row(fields):
    return u32(len(fields)) + b''.join(lp(name) + bytes([tag]) + lp(value)
                                     for name, tag, value in fields)

def partition(name, rows):
    return lp(name) + u32(len(rows)) + b''.join(rows)

def matrix(path, prefix, page):
    source = (ROOT / path).read_text(encoding='utf-8')
    values = re.search(r'(?:MATRIX|BASELINE)\s*=\s*"""(.*?)"""', source, re.S)
    if values is None:
        raise ValueError('Missing independent matrix')
    result = []
    for index, line in enumerate(values.group(1).strip().splitlines(), 1):
        key, name, category = line.strip().split('|')
        result.append((prefix, prefix.split('.')[1]+'.'+key, name,
                       name+' is an SRD 5.1 '+prefix.split('.')[1]+' catalog entry.', category, page, index))
    return result

def canonical(rows):
    definitions, attributes = [], []
    for kind, key, name, description, category, page, order in sorted(rows):
        definitions.append(row([('definition_type', 2, kind), ('definition_key', 2, key),
                                ('display_name', 1, name), ('description', 1, description), ('sort_order', 3, order)]))
        for attribute, value_type, tag, value in [('catalog.category', 'IDENTIFIER', 2, category),
                                                   ('source.page', 'INTEGER', 3, page)]:
            attributes.append(row([('definition_type', 2, kind), ('definition_key', 2, key),
                                   ('attribute_key', 2, attribute), ('attribute_order', 3, 1),
                                   ('value_type', 2, value_type), ('value', tag, value)]))
    release = row([('module_key', 2, 'dnd5e2014_srd51_se'), ('release_version', 2, '1'),
                   ('canonical_format_version', 3, 2), ('hash_algorithm', 2, 'SHA-256')])
    return (b'DND_TOOL_SE_MODULE_CANONICAL' + u32(2) + u32(4)
            + partition('release', [release]) + partition('catalog_definition', definitions)
            + partition('catalog_attribute', attributes) + partition('catalog_relation', []))


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f'duplicate field {key}')
        result[key] = value
    return result


def reject_number(token):
    raise ValueError(f'expected an integer token, got {token}')


def positive_integer(token):
    if token[0] in '-0' or len(token) > 10 or int(token) > 2147483647:
        raise ValueError('expected a positive 32-bit integer token')
    return int(token)


def read_json(path, maximum):
    try:
        with path.open('rb') as stream:
            payload = stream.read(maximum + 1)
        if len(payload) > maximum:
            raise ValueError(f'exceeds {maximum} bytes')
        return json.loads(payload.decode('utf-8'), object_pairs_hook=unique_object,
                          parse_int=positive_integer, parse_float=reject_number, parse_constant=reject_number)
    except (OSError, ValueError, RecursionError) as failure:
        raise ValueError(f'{path.name}: {failure}') from failure


def author_text(value, maximum):
    if (not isinstance(value, str) or not value.strip()
            or any(unicodedata.category(char) in {'Cc', 'Cs'} for char in value)):
        raise ValueError('invalid author text')
    normalized = unicodedata.normalize('NFC', value)
    if len(normalized) > maximum:
        raise ValueError(f'author text exceeds {maximum} code points')
    return normalized


def author_rows(directory):
    header = read_json(directory / 'author-package.json', 8192)
    expected = dict(author_schema_version=1, module_key='dnd5e2014_srd51_se', release_version='1',
                    canonical_format_version=2, archive_format_version=2, hash_algorithm='SHA-256',
                    partitions=[dict(partition_key=f'character.{kind}', path=path) for kind, path, _ in PARTITIONS])
    if not isinstance(header, dict):
        raise ValueError('author-package.json: expected an object')
    author_text(header.pop('package_display_name', None), 120)
    if header != expected or any(type(header[field]) is not int for field in expected if type(expected[field]) is int):
        raise ValueError('author-package.json: unexpected identity, format, or partitions')

    for kind, path, count in PARTITIONS:
        items = read_json(directory / path, 262144)
        if not isinstance(items, list) or len(items) != count:
            raise ValueError(f'{path}: expected {count} rows')
        key_field = f'{kind}_key'
        fields = {key_field, *ROW_FIELDS[1:]}
        for index, item in enumerate(items, 1):
            if (not isinstance(item, dict) or set(item) != fields
                    or any(not isinstance(item[field], str) for field in (key_field, 'category'))
                    or any(type(item[field]) is not int for field in ('source_page', 'sort_order'))):
                raise ValueError(f'{path}: invalid fields or types at row {index}')
            yield (f'character.{kind}', item[key_field], author_text(item['display_name'], 120),
                   author_text(item['description'], 1000), item['category'], item['source_page'], item['sort_order'])


def check(directory):
    expected = []
    with EXPECTED_ROWS.open(encoding='utf-8', newline='') as stream:
        matrix_rows = csv.reader(stream, delimiter='\t', quoting=csv.QUOTE_NONE)
        if tuple(next(matrix_rows, ())) != ROW_FIELDS:
            raise ValueError(f'{EXPECTED_ROWS.name}: invalid header')
        for line, item in enumerate(matrix_rows, 2):
            if len(item) != len(ROW_FIELDS):
                raise ValueError(f'{EXPECTED_ROWS.name}: invalid fields at line {line}')
            key, name, description, category, page, order = item
            partition_key = f'character.{key.split(".", 1)[0]}'
            expected.append((partition_key, key, name, description, category, int(page), int(order)))
    actual = sorted(author_rows(directory))
    if len(actual) != len(expected):
        raise ValueError('unexpected number of author catalog rows')
    fields = ('partition', *ROW_FIELDS)
    for candidate, approved in zip(actual, sorted(expected)):
        for field, value, expected_value in zip(fields, candidate, approved):
            if value != expected_value:
                raise ValueError(f'{approved[1]}: {field} differs from expected {expected_value!r}')
    data = canonical(actual)
    if hashlib.sha256(data).hexdigest() != EXPECTED_CANONICAL_SHA256:
        raise ValueError('DRAFT projection differs from the independent canonical byte expectation')
    print(f'DRAFT catalog verified: {len(actual)} rows, {len(data)} canonical bytes')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', type=Path, metavar='AUTHOR_DIR',
                        help='read-only comparison against the independent DRAFT author expectations')
    args = parser.parse_args()
    if args.check is not None:
        try:
            check(args.check)
        except (OSError, ValueError, csv.Error) as failure:
            parser.exit(1, f'{failure}\n')
        return

    rows = matrix('src/test/java/com/dndtool/module/CharacterCatalogAuthorPackageReaderTest.java', 'character.language', 59)
    rows += matrix('src/test/java/com/dndtool/module/ToolCatalogOracle.java', 'character.tool', 70)
    assert len(rows) == 55
    data = canonical(rows)
    (ROOT / 'src/test/resources/module-canonical-v2-language-tools.hex').write_text(data.hex()+'\n', encoding='ascii')
    print(f'{len(data)} bytes; sha256={hashlib.sha256(data).hexdigest()}')


if __name__ == '__main__':
    main()
