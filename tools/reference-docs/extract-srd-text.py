#!/usr/bin/env python3
"""Extract the bundled SRD 5.1 PDF in coordinate-based reading order.

Requires pdfminer.six. No OCR, network access, rule rewriting or PDF changes.
Run with --check to reproduce the extraction in memory and verify saved output.
"""

from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import dataclass
import json
import os
from pathlib import Path
import re
import sys

try:
    from pdfminer.converter import PDFPageAggregator
    from pdfminer.layout import LTChar, LTRect
    from pdfminer.pdfinterp import PDFPageInterpreter, PDFResourceManager
    from pdfminer.pdfpage import PDFPage
except ImportError:
    sys.exit("pdfminer.six is required; run this script with a Python environment containing it.")


ROOT = Path(__file__).resolve().parents[2]
EXPECTED_PAGES = 403
GUTTER_LEFT, GUTTER_RIGHT = 294, 322
MIDPOINT = (GUTTER_LEFT + GUTTER_RIGHT) / 2


def normalize(text: str) -> str:
    # These are the PDF's actual one-glyph ToUnicode mappings, not prose edits.
    if text in ("-\xad‐‑", "-\xad‐"):
        return "-"
    if text == "\u0336":
        return "—"  # The printed empty-table-cell dash.
    if text.isspace():
        return " "
    if any(ord(c) < 32 or 127 <= ord(c) <= 159 for c in text):
        raise ValueError(f"Unexpected control character in PDF glyph: {text!r}")
    return text


@dataclass(frozen=True)
class Glyph:
    id: int
    text: str
    x0: float
    y0: float
    x1: float
    y1: float
    size: float
    font: str
    baseline: float


def read_pages(path: Path, selected: set[int] | None):
    manager = PDFResourceManager()
    # Automatic layout groups table cells into columns and is intentionally off.
    device = PDFPageAggregator(manager, laparams=None)
    interpreter = PDFPageInterpreter(manager, device)
    with path.open("rb") as stream:
        for number, page in enumerate(PDFPage.get_pages(stream, caching=True), 1):
            if selected is not None and number not in selected:
                continue
            interpreter.process_page(page)
            glyphs, rects = [], []

            def visit(item):
                if isinstance(item, LTChar):
                    glyphs.append(Glyph(len(glyphs), normalize(item.get_text()),
                                        *item.bbox, item.size, item.fontname, item.matrix[5]))
                elif isinstance(item, LTRect):
                    rects.append(item.bbox)
                elif hasattr(item, "__iter__"):
                    for child in item:
                        visit(child)

            visit(device.get_result())
            yield number, glyphs, rects


def rows(glyphs: list[Glyph]) -> list[list[Glyph]]:
    """Keep aligned text/dashes together without pdfminer word/box heuristics."""
    result: list[list[Glyph]] = []
    for glyph in sorted(glyphs, key=lambda g: (-g.baseline, g.x0, g.id)):
        if not result or abs(glyph.baseline - result[-1][0].baseline) > 1.3:
            result.append([glyph])
        else:
            result[-1].append(glyph)
    return [sorted(row, key=lambda g: (g.x0, g.id)) for row in result]


def small_table_row(row: list[Glyph]) -> bool:
    ink = [g for g in row if g.text.strip()]
    return bool(ink) and all(g.size < 9.3 and "Calibri" in g.font for g in ink)


def merge_intervals(intervals, distance=25):
    result = []
    for low, high in sorted(intervals):
        if result and low - result[-1][1] <= distance:
            result[-1] = (result[-1][0], max(high, result[-1][1]))
        else:
            result.append((low, high))
    return result


def wide_bands(glyphs: list[Glyph], rects) -> list[tuple[float, float]]:
    seeds = [(g.y0, g.y1) for g in glyphs if g.text.strip()
             and g.x0 < GUTTER_RIGHT and g.x1 > GUTTER_LEFT]
    seeds += [(y0, y1) for x0, y0, x1, y1 in rects
              if x0 < MIDPOINT < x1 and y0 > 60]
    bands = merge_intervals(seeds)
    if not bands:
        return []
    physical_rows = rows(glyphs)
    expanded = []
    for low, high in bands:
        # Include table headers/rows that do not themselves enter the gutter.
        changed = True
        while changed:
            changed = False
            for row in physical_rows:
                row_low, row_high = min(g.y0 for g in row), max(g.y1 for g in row)
                if small_table_row(row) and row_low <= high + 13 and row_high >= low - 13:
                    updated = min(low, row_low), max(high, row_high)
                    if updated != (low, high):
                        low, high = updated
                        changed = True
        # The source's full-width table title is often left aligned above it.
        above = [row for row in physical_rows if min(g.y0 for g in row) > high
                 and min(g.y0 for g in row) - high < 18]
        if above:
            nearest = min(above, key=lambda row: min(g.y0 for g in row))
            ink = [g for g in nearest if g.text.strip()]
            if ink and all(g.x1 < GUTTER_LEFT and g.size >= 11.5 for g in ink):
                high = max(g.y1 for g in nearest)
        expanded.append((low - 0.1, high + 0.1))
    return sorted(merge_intervals(expanded, distance=0), reverse=True)


def fragments(row: list[Glyph]):
    """Split only at physical gaps between cells; retain word spaces verbatim."""
    result, current = [], []
    previous = None
    for glyph in row:
        if previous is not None and glyph.x0 - previous.x1 > 4.5:
            if current:
                result.append(current)
            current = []
        current.append(glyph)
        previous = glyph
    if current:
        result.append(current)
    return result


def row_text(row: list[Glyph], origin: float) -> str:
    output = ""
    for fragment in fragments(row):
        text = re.sub(r" +", " ", "".join(g.text for g in fragment)).strip()
        if not text:
            continue
        # Three source points per monospace position leaves room for cell text.
        position = max(0, round((fragment[0].x0 - origin) / 3))
        if output:
            output += " " * max(2, position - len(output))
        else:
            output = " " * position
        output += text
    return output.rstrip()


def extract_page(number: int, glyphs: list[Glyph], rects, source_path="../../srd-5.1.pdf"):
    body = [g for g in glyphs if g.y0 > 60]
    footer = [g for g in glyphs if g.y0 <= 60]
    bands = wide_bands(body, rects)
    regions: list[tuple[str, list[Glyph]]] = []
    remaining = body

    def columns(items):
        for label, predicate in (("Left column", lambda g: g.x0 < MIDPOINT),
                                 ("Right column", lambda g: g.x0 >= MIDPOINT)):
            group = [g for g in items if predicate(g)]
            if any(g.text.strip() for g in group):
                regions.append((label, group))

    for low, high in bands:
        columns([g for g in remaining if g.y0 > high])
        wide = [g for g in remaining if low <= g.y0 <= high]
        if wide:
            regions.append(("Full-width region", wide))
        remaining = [g for g in remaining if g.y0 < low]
    columns(remaining)
    if any(g.text.strip() for g in footer):
        regions.append(("Original page footer", footer))

    source_url = f"{source_path}#page={number}"
    output = [f"# SRD 5.1 — PDF page {number}", "",
              f"Source: [original PDF, page {number}]({source_url}).", ""]
    nav = []
    if number > 1:
        nav.append(f"[Previous page](page-{number - 1:03}.md)")
    if number < EXPECTED_PAGES:
        nav.append(f"[Next page](page-{number + 1:03}.md)")
    output += [" · ".join(nav), "",
               "Page boundaries are preserved; text and tables may continue on adjacent pages.", ""]
    seen, emitted_text, headings = [], [], []
    for label, group in regions:
        source_rows = rows(group)
        ink = [g for g in group if g.text.strip()]
        if not ink:
            continue
        origin = min(g.x0 for g in ink)
        output += [f"## {label}", "", "```text"]
        previous_bottom = None
        for row in source_rows:
            line = row_text(row, origin)
            if not line.strip():
                continue
            top, bottom = max(g.y1 for g in row), min(g.y0 for g in row)
            if previous_bottom is not None and previous_bottom - top > 4:
                output.append("")
            output.append(line)
            previous_bottom = bottom
            nonempty = [g for g in row if g.text.strip()]
            seen.extend(g.id for g in nonempty)
            emitted_text.append(line)
            if label != "Original page footer" and nonempty and all(g.size >= 11.5 for g in nonempty):
                headings.append(line.strip())
        output += ["```", ""]
    if not regions:
        output += ["This PDF page is blank; no source text was present.", ""]

    expected = [g.id for g in glyphs if g.text.strip()]
    if Counter(seen) != Counter(expected):
        raise ValueError(f"Page {number}: missing or repeated source glyphs")
    source_characters = Counter(c for g in glyphs for c in g.text if not c.isspace())
    rendered_characters = Counter(c for line in emitted_text for c in line if not c.isspace())
    if source_characters != rendered_characters:
        raise ValueError(f"Page {number}: normalized source/output characters differ")
    content = "\n".join(output)
    if re.search(r"[\x00-\x09\x0b-\x1f\x7f-\x9f\u00a0\u00ad\u0336]", content):
        raise ValueError(f"Page {number}: control or PDF encoding artifact remains")
    entry = {"pdf_page": number, "path": f"pages/page-{number:03}.md",
             "headings": headings, "blank": not bool(expected),
             "source_glyphs": len(glyphs), "nonspace_glyphs": len(expected),
             "normalized_nonspace_characters": sum(source_characters.values()),
             "regions": [label for label, group in regions if any(g.text.strip() for g in group)]}
    return content, entry


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=ROOT / "docs/references/srd-5.1.pdf")
    parser.add_argument("--output", type=Path, default=ROOT / "docs/references/srd-5.1")
    parser.add_argument("--pages", help="Comma-separated PDF page numbers for a review sample")
    parser.add_argument("--check", action="store_true", help="Compare saved output without writing")
    args = parser.parse_args()
    selected = {int(p) for p in args.pages.split(",")} if args.pages else None
    if selected is not None and (not selected or min(selected) < 1 or max(selected) > EXPECTED_PAGES):
        parser.error(f"Page numbers must be within 1..{EXPECTED_PAGES}")
    if selected is not None and not args.check and args.output.resolve() == (ROOT / "docs/references/srd-5.1").resolve():
        parser.error("Sample generation requires a different --output directory to protect the complete index")
    page_source = os.path.relpath(args.input.resolve(), (args.output / "pages").resolve()).replace(os.sep, "/")
    index_source = os.path.relpath(args.input.resolve(), args.output.resolve()).replace(os.sep, "/")
    entries = []
    for number, glyphs, rects in read_pages(args.input, selected):
        content, entry = extract_page(number, glyphs, rects, page_source)
        destination = args.output / entry["path"]
        if args.check:
            if destination.read_text(encoding="utf-8") != content:
                sys.exit(f"Saved extraction differs: {destination}")
        else:
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_text(content, encoding="utf-8", newline="\n")
        entries.append(entry)
        if number % 50 == 0:
            print(f"Checked {number} PDF pages", flush=True)
    wanted = sorted(selected) if selected is not None else list(range(1, EXPECTED_PAGES + 1))
    if [entry["pdf_page"] for entry in entries] != wanted:
        sys.exit(f"Expected PDF pages {wanted[0]}..{wanted[-1]}; source page count differs")
    index = {"format_version": 1, "source": index_source, "source_page_count": EXPECTED_PAGES,
             "complete": selected is None, "pages": entries}
    index_text = json.dumps(index, ensure_ascii=False, indent=2) + "\n"
    if args.check:
        saved = json.loads((args.output / "index.json").read_text(encoding="utf-8"))
        compared = saved if selected is None else {**saved, "complete": False,
            "pages": [entry for entry in saved["pages"] if entry["pdf_page"] in selected]}
        if compared != index:
            sys.exit("Saved index differs from the source extraction")
        if selected is None and len(list((args.output / "pages").glob("page-*.md"))) != EXPECTED_PAGES:
            sys.exit("Saved page-file count differs")
    else:
        (args.output / "index.json").write_text(index_text, encoding="utf-8", newline="\n")
    print(json.dumps({"pages": len(entries), "source_glyphs": sum(e["source_glyphs"] for e in entries),
                      "nonspace_glyphs_emitted_once": sum(e["nonspace_glyphs"] for e in entries),
                      "normalized_nonspace_characters": sum(e["normalized_nonspace_characters"] for e in entries),
                      "blank_pages": [e["pdf_page"] for e in entries if e["blank"]],
                      "mode": "checked" if args.check else "written"}, ensure_ascii=False))


if __name__ == "__main__":
    main()
