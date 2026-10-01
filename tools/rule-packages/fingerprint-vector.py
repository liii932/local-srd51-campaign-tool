#!/usr/bin/env python3
"""Independently regenerate the fixed CD-09 test vector (no Java reader/encoder calls)."""
import hashlib
import json
from pathlib import Path
import struct

root = Path(__file__).resolve().parents[2]
source = root / 'rule-packages/srd51-complete'
files = {name: (source / name).read_text(encoding='utf-8').replace('\r\n', '\n').encode()
         for name in ['author-package.json', 'character/languages.json', 'character/tools.json']}
files.update({'notice.md': b'License\n', 'package-guide.md': b'Documentation\n'})
roles = {'author-package.json': 'author-header', 'character/languages.json': 'author-partition',
         'character/tools.json': 'author-partition', 'notice.md': 'license', 'package-guide.md': 'documentation'}
inventory = [{'path': name, 'role': roles[name], 'byte_length': len(data),
              'raw_sha256': hashlib.sha256(data).hexdigest()} for name, data in sorted(files.items())]
manifest = dict(installation_manifest_version=1, author_schema_version=1, module_key='dnd5e2014_srd51_se',
                release_version='1', canonical_format_version=2, archive_format_version=2,
                hash_algorithm='SHA-256', verification_scope='PARTITION',
                partition_keys=['character.language', 'character.tool'], files=inventory)
manifest_bytes = (json.dumps(manifest, separators=(',', ':')) + '\n').encode()
head = json.loads(files['author-package.json'])
lp = lambda data: struct.pack('>I', len(data)) + data
text = lambda value: value.encode('utf-8')
integer = lambda value: struct.pack('>Q', value)
mapping = struct.pack('>I', 2) + lp(b'character.language') + lp(b'character/languages.json') + lp(b'character.tool') + lp(b'character/tools.json')
listed = struct.pack('>I', 5)
for entry in inventory:
    listed += lp(text(entry['path'])) + lp(text(entry['role'])) + integer(entry['byte_length']) + lp(text(entry['raw_sha256']))
fields = [(1,b'INSTALL'), (1,b'dnd5e2014_srd51_se'), (1,b'1'), (3,integer(0)), (3,integer(1)),
          (3,integer(1)), (3,integer(2)), (3,integer(2)), (1,b'SHA-256'),
          (2,text(head['package_display_name'])), (1,b'PARTITION'), (0,b''),
          (1,text(hashlib.sha256(manifest_bytes).hexdigest())), (1,b'srd51-character-catalog'),
          (3,integer(1)), (4,mapping), (5,listed)]
encoded = lp(b'DND_TOOL_SE_SOURCE_INSTALLATION_REQUEST_V1') + struct.pack('>IH', 1, 17)
for index, (tag, payload) in enumerate(fields, 1):
    encoded += struct.pack('>HBI', index, tag, len(payload)) + payload
(root / 'src/test/resources/source-installation-fingerprint-character-catalog-v1.hex').write_text(encoded.hex() + '\n', encoding='ascii')
print(f'{len(encoded)} bytes; sha256={hashlib.sha256(encoded).hexdigest()}')
