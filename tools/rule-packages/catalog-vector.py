#!/usr/bin/env python3
"""Encode the reviewed language/tool test matrices without Java or author/store projections."""
from pathlib import Path
import re
import struct
import hashlib

ROOT = Path(__file__).resolve().parents[2]

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

rows = matrix('src/test/java/com/dndtool/module/CharacterCatalogAuthorPackageReaderTest.java', 'character.language', 59)
rows += matrix('src/test/java/com/dndtool/module/ToolCatalogOracle.java', 'character.tool', 70)
assert len(rows) == 55
definitions, attributes = [], []
for kind, key, name, description, category, page, order in sorted(rows):
    definitions.append(row([('definition_type',2,kind), ('definition_key',2,key),
                            ('display_name',1,name), ('description',1,description), ('sort_order',3,order)]))
    for attribute, value_type, tag, value in [('catalog.category','IDENTIFIER',2,category), ('source.page','INTEGER',3,page)]:
        attributes.append(row([('definition_type',2,kind), ('definition_key',2,key),
                               ('attribute_key',2,attribute), ('attribute_order',3,1),
                               ('value_type',2,value_type), ('value',tag,value)]))
release = row([('module_key',2,'dnd5e2014_srd51_se'), ('release_version',2,'1'),
               ('canonical_format_version',3,2), ('hash_algorithm',2,'SHA-256')])
data = b'DND_TOOL_SE_MODULE_CANONICAL' + u32(2) + u32(4)
data += partition('release',[release]) + partition('catalog_definition',definitions)
data += partition('catalog_attribute',attributes) + partition('catalog_relation',[])
(ROOT / 'src/test/resources/module-canonical-v2-language-tools.hex').write_text(data.hex()+'\n', encoding='ascii')
print(f'{len(data)} bytes; sha256={hashlib.sha256(data).hexdigest()}')
