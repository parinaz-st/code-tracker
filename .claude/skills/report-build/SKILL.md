---
name: report-build
description: Build the LaTeX-style project reports (HTML source to PDF with Edge headless, and to DOCX with Word) including SVG figures. Use when writing or updating docs/evaluation/report or docs/comment-transition/report, or when a report PDF/DOCX must be regenerated.
---

# Building the reports

There are two reports. Each has an HTML source styled like a LaTeX article, SVG figures, and a `build.ps1`:

| Report | Source | Output |
|---|---|---|
| Evaluation guide for students | `docs/evaluation/report/report.html` | `Evaluating-CodeTracker.pdf` |
| Comment-transition report | `docs/comment-transition/report/report.html` | `Comment-Aware-Change-History-CodeTracker.pdf` and `.docx` |

No TeX, Python or pandoc is installed on this machine. Edge renders the PDF and Word renders the DOCX.

## Build

```powershell
powershell -ExecutionPolicy Bypass -File docs/evaluation/report/build.ps1
powershell -ExecutionPolicy Bypass -File docs/comment-transition/report/build.ps1
```

The script renders every `figures/*.svg` to a 2× PNG, which the Markdown and Word versions use; the PDF uses the SVGs directly. It then prints the HTML to PDF with `msedge --headless=new --print-to-pdf`.

## Writing content

- **Style:** reuse the CSS block of the existing `report.html`: `booktabs` tables, `alg` tables for algorithms, `figure` divs with `caption`. Number figures and tables, and cite as `[n]`.
- **Figures:** hand-written SVG with an explicit `width`, `height` and `viewBox`, the Cambria/Times font, black lines and grey fills. After the build, **look at the PNG** (Read tool) to catch overlapping labels and stray arrowheads; text that overflows its box is the most common problem.
- **Numbers:** take them from the oracle README, the phase docs, `final.csv` or the papers, never from memory. To read the papers, Word can convert a small PDF to text (`Documents.Open`, `SaveAs2` with format 2). For large PDFs, a stream-inflating Java extractor worked better, because Word blocks on a hidden dialog.
- **Copyright:** summarise the papers in your own words. Quote at most one short sentence.

## Traps

- **A PDF that is open in a viewer or in the browser pane can't be overwritten.** The evaluation build then writes `<name>-updated.pdf`. Close the viewer (or the pane tab) and rebuild, then delete the `-updated` copy.
- **Word automation:** in Windows PowerShell 5.1, `SaveAs2` needs `[ref]` plain-string arguments, or it hangs. Always call `$word.Quit()` in a `finally`.
- **`.tex` copies:** a `report.tex` (if present) is maintained separately. Editing the HTML does not update it, and editing the `.tex` does not update the HTML/PDF/DOCX. Ask which one is the main copy.
