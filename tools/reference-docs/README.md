# Reference PDF text extraction

`extract-srd-text.py` converts the bundled 403-page SRD 5.1 PDF into the
[page-based reading copy](../../docs/references/srd-5.1/README.md). It preserves
the English source text, page boundaries, printed line wraps and page footers.
It does not summarize, translate, OCR, correct rules or change the PDF.

Use Python 3.10 or newer with `pdfminer.six` installed. The extraction was
developed with `pdfminer.six` 20260107; use that version when reproducing the
checked-in output. Invoke the script from a Python environment containing the
dependency; no dependency installation or network access occurs in the script.

From the repository root:

```sh
python tools/reference-docs/extract-srd-text.py
python tools/reference-docs/extract-srd-text.py --check
```

The default input and output are resolved relative to the script's repository,
so the current working directory does not affect them. `--input` and `--output`
can select other paths for the same source/layout. `--check` reproduces all
pages in memory and compares page content and the JSON index without writing.
For a review sample, `--pages 3,11,56,63 --output /tmp/srd-review` produces only
the selected pages and an index marked `complete: false`.

The extractor reads raw glyph and rectangle coordinates with automatic PDF
layout grouping disabled. Cross-gutter glyphs and table-cell rectangles define
full-width regions; between these regions the complete left column precedes
the complete right column. Source lines and table cells stay together in
monospaced text blocks. Large-font source lines populate the index's `headings`
array; this is a discovery aid rather than an inferred hierarchy or complete
subject index.

Only the source's known encoding artifacts are normalized: whitespace glyphs
containing tab, carriage return, nonbreaking space or line separator become
ordinary spaces; the multi-codepoint glyph mappings `-\u00ad‐‑` and `-\u00ad‐`
become one hyphen; the combining-stroke glyph used for an empty table value
becomes an em dash. Other punctuation and source wording are retained.

Every extraction checks the expected page set, assigns each nonspace source
glyph to exactly one output region, compares normalized source/output
nonspace character counts, and rejects remaining control characters or known
encoding artifacts. These checks detect missing or repeated extracted glyphs;
they do not establish that the source itself is correct or replace inspection
of reading order. Review representative layouts against the original PDF:

- Page 3: finish the left-column racial-trait introduction before `Subraces`.
- Page 11: upper columns, full-width Bard table, then lower `Spellcasting` columns.
- Page 56: left-column advancement rows 1–15, right-column rows 16–20, then multiclassing.
- Pages 63–64: armor prose, full-width table start, and the continuation page.
- A spell page, monster stat block and magic-item table: keep labels and their
  values on the same source line.

The output retains physical line and page breaks, including words or sentences
that continue in another column or on the next page. Consult the source PDF
when exact typography or an ambiguous layout matters. License and attribution
follow [the reference directory](../../docs/references/README.md).
