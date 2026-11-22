"""Local regression tests for the DRAFT check and application fingerprint vector."""
import hashlib
from io import BytesIO
import json
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[3]


def length_prefixed(stream):
    length, = struct.unpack('>I', stream.read(4))
    return stream.read(length)


def run_script(script, *arguments):
    command = [sys.executable, '-B', str(script), *map(str, arguments)]
    return subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, encoding='utf-8')


class RuleVectorsTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)

    def copy(self, relative):
        destination = self.root / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        if (ROOT / relative).is_dir():
            shutil.copytree(ROOT / relative, destination)
        else:
            shutil.copyfile(ROOT / relative, destination)
        return destination

    def test_draft_check_accepts_reordering_and_rejects_invalid_header_or_expectations(self):
        script = self.copy('tools/rule-packages/catalog-vector.py')
        author = self.copy('rule-packages/srd51-complete')
        expected = self.copy('rule-packages/srd51-complete-expected.tsv')

        languages = author / 'character/languages.json'
        rows = json.loads(languages.read_bytes())
        languages.write_text(json.dumps(rows[::-1]), encoding='utf-8')
        result = run_script(script, '--check', author)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse((self.root / 'src').exists(), 'Author check must not create application test vectors')

        header_file = author / 'author-package.json'
        original = header_file.read_bytes()
        header = json.loads(original)
        header['author_schema_version'] = True
        for payload, diagnostic in (
                (json.dumps(header).encode('utf-8'), 'unexpected identity, format, or partitions'),
                (br'{"\u0061uthor_schema_version": 1,' + original.lstrip()[1:], 'duplicate field author_schema_version')):
            with self.subTest(diagnostic=diagnostic):
                header_file.write_bytes(payload)
                result = run_script(script, '--check', author)
                self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
                self.assertIn(diagnostic, result.stderr)

        header_file.write_bytes(original)
        heading, first_row, remaining = expected.read_text(encoding='utf-8').split('\n', 2)
        expected.write_text(f'{heading}\n{first_row}\textra\n{remaining}', encoding='utf-8')
        result = run_script(script, '--check', author)
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn('invalid fields at line 2', result.stderr)

    def test_fingerprint_preserves_raw_crlf_and_encodes_the_nfc_package_name(self):
        script = self.copy('tools/rule-packages/fingerprint-vector.py')
        fixture = self.copy('src/test/resources/character-catalog-author')
        header = fixture / 'author-package.json'
        declaration = json.loads(header.read_bytes())
        declaration['package_display_name'] = 'Cafe\u0301'
        raw_header = (json.dumps(declaration, indent=2) + '\n').replace('\n', '\r\n').encode('utf-8')
        header.write_bytes(raw_header)
        result = run_script(script)
        self.assertEqual(result.returncode, 0, result.stderr)

        vector = fixture.parent / 'source-installation-fingerprint-character-catalog-v1.hex'
        stream = BytesIO(bytes.fromhex(vector.read_text(encoding='ascii')))
        length_prefixed(stream)  # Domain marker
        _, count = struct.unpack('>IH', stream.read(6))
        fields = {}
        for _ in range(count):
            index, _, length = struct.unpack('>HBI', stream.read(7))
            fields[index] = stream.read(length)
        self.assertEqual(fields[10], 'Café'.encode('utf-8'))

        files = BytesIO(fields[17])
        files.read(4)  # Inventory count
        self.assertEqual(length_prefixed(files), b'author-package.json')
        self.assertEqual(length_prefixed(files), b'author-header')
        self.assertEqual(struct.unpack('>Q', files.read(8))[0], len(raw_header))
        self.assertEqual(length_prefixed(files), hashlib.sha256(raw_header).hexdigest().encode('ascii'))


if __name__ == '__main__':
    unittest.main()
