# Phase 0 — offline OCR model selection for Arabic and English

Date: 2026-10-21
Scope: which open model reads Arabic and English, offline, on an Android phone,
inside the Jumlo app.

## The benchmark

`tests/ocr_benchmark/` holds 30 pages with the ground truth next to them:

| category | pages | share | what it is |
|---|---:|---:|---|
| `ar_printed` | 12 | 40 % | Arabic printed pages: invoices, tables, forms, reports, a menu, a hotel card |
| `en_printed` | 6 | 20 % | English printed pages: invoice, report, notice, form, table, shipping note |
| `mixed` | 6 | 20 % | Arabic and English on the same page, including bilingual tables |
| `photo` | 6 | 20 % | the same pages as phone photos: perspective, shadow, low light, blur, noise, JPEG artefacts |

The ground truth is exact by construction: the pages are rendered from a known
text (`tests/ocr_benchmark/tools/make_benchmark.py`), so the file on the page and
the transcription are the same string. Nothing is guessed. The pages were
checked by eye before they were used.

**One thing the benchmark has to be honest about.** In an Arabic paragraph a
number with a separator is laid out with its parts in the opposite order, because
the separator is a neutral that takes the paragraph direction: `٢٠٢٤-٠٨٧` is
*written* in that order and *rendered* as `٠٨٧-٢٠٢٤`. An engine cannot read back
an order the image does not carry, so the generator puts such numbers in a
left-to-right run, the way a careful typesetter does. The same applies to a
Latin name inside an Arabic line: the renderer places the runs by hand, in the
written order, because the bidi algorithm of the text engine would otherwise
swap them.

## The candidates

| candidate | model | licence | size on disk |
|---|---|---|---|
| **PaddleOCR** | `PP-OCRv5_mobile_det` + `arabic_PP-OCRv5_mobile_rec`, both ONNX | Apache-2.0 | 12.2 MB (det 4.6 MB + rec 7.6 MB) |
| **Tesseract 5 (best)** | `ara` + `eng`, `tessdata_best` | Apache-2.0 | 27.0 MB (ara 12.0 MB + eng 14.7 MB) |
| **Tesseract 5 (fast)** | `ara` + `eng`, `tessdata_fast` | Apache-2.0 | 5.3 MB (ara 1.4 MB + eng 3.9 MB) |

A third, permissively licensed model was looked for and not found: the Arabic
ONNX recognisers that exist on the model hub are community uploads without a
licence, so under the rule *only Apache-2.0, MIT or BSD may ship*, they do not
qualify. The two candidates above are the whole field.

## The measurements

Host: Linux x86_64, four threads, Tesseract 5.5.3 and ONNX Runtime 1.29.0.
Character error rate and word error rate are computed on normalised text
(`tests/ocr_benchmark/tools/score_ocr.py`): whitespace is collapsed, the Unicode
form is normalised, and the bidi marks an engine adds are removed. Whitespace is
not counted as an error.

| candidate | overall CER | overall WER | Arabic CER | English CER | Mixed CER | Photo CER | s/page | peak RSS |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| **PaddleOCR** | **25.4 %** | 53.1 % | **22.6 %** | 5.5 % | 35.2 % | 41.1 % | 0.54 | 383 MB |
| Tesseract 5 (best) | 30.9 % | 48.5 % | 40.9 % | **0.5 %** | **29.9 %** | 42.2 % | 0.95 | 90 MB |
| Tesseract 5 (fast) | 29.2 % | 44.2 % | 35.4 % | **0.0 %** | 36.8 % | **38.4 %** | 0.39 | 63 MB |

Per category, the median and the worst page:

| candidate | Arabic median / worst | English median / worst | Mixed median / worst | Photo median / worst |
|---|---|---|---|---|
| PaddleOCR | 20.4 % / 62.4 % | 0.0 % / 17.4 % | 44.3 % / 60.5 % | 40.7 % / 65.9 % |
| Tesseract 5 (best) | 49.3 % / 67.3 % | 0.0 % / 2.9 % | 28.6 % / 53.8 % | 41.4 % / 80.0 % |
| Tesseract 5 (fast) | 33.8 % / 64.2 % | 0.0 % / 0.0 % | 38.1 % / 59.6 % | 34.4 % / 79.4 % |

## The choice

**PaddleOCR (PP-OCRv5 mobile detection + `arabic_PP-OCRv5_mobile_rec`, Apache-2.0).**

The rule is accuracy first, then speed, then size, and Arabic is the priority
language. PaddleOCR has the best overall error rate (25.4 % against 30.9 % and
29.2 %) and clearly the best Arabic (22.6 % against 40.9 % and 35.4 %). The
Arabic gap is the whole story: Tesseract's `ara` model misreads the
Arabic-Indic digits that fill every price, date and phone number in an Arabic
document, while the PaddleOCR Arabic model has them in its character set.

What Tesseract is better at is English (0.0-0.5 % against 5.5 %) and the word
error rate (44-48 % against 53 %), and it is lighter: 5.3 MB of models against
12.2 MB, and a third of the memory.

## The two things the measurements do not say

- **No device was available.** The speed and the memory above are host numbers
  on x86_64. A Snapdragon 7-series or Dimensity 700-series phone is slower and
  has a different memory profile, and ONNX Runtime on an ARM core is a
  different code path from ONNX Runtime on x86. The device numbers are **not
  measured**.
- **The PaddleOCR word error rate is not comparable to Tesseract's**, because
  the Arabic recogniser has no space in its character set: it returns the words
  of a line one after another, and the word boundaries have to be recovered from
  the model's own alignment. The character error rate, which ignores
  whitespace, is the comparable number.

## The properties of the PaddleOCR Arabic recogniser that the app has to handle

These are not bugs; they are how the model behaves, and the app has to undo them.

1. It reads an Arabic word in the order the glyphs appear in the image, so an
   Arabic word comes out backwards and has to be turned around.
2. The words of a line come out in the order they appear in the image, which for
   an Arabic line is the opposite of the order they are read in. The order is
   restored with the second step of the Unicode bidirectional algorithm: the
   line has a direction, and the words written against it are turned around as a
   group, so a Latin name inside an Arabic line keeps its own order.
3. The character set is 747 characters plus a space, and the class numbers run
   from one, with the blank at zero. A model file of 749 classes therefore means
   the space is the last class and the list in the model's own configuration file
   is one short of it.
4. The model is a **word** model, not a line model: the space class is what
   separates the words, and a line crop is read as a run of words.

## The cost on the device

| item | cost |
|---|---|
| ONNX Runtime for Android | the runtime, not the model: it is a library in the APK. The base APK is 10.6 MB today and the 60 MB policy leaves room, but the number has to be measured on the built artifact. |
| the models | 12.2 MB, downloaded on demand, never in the base APK |
| peak memory | 383 MB on the host over 30 pages; the per-page figure and the device figure are not measured |

## The files

| path | what it is |
|---|---|
| `tests/ocr_benchmark/tools/make_benchmark.py` | builds the pages and the ground truth |
| `tests/ocr_benchmark/tools/run_paddleocr.py` | the PaddleOCR candidate |
| `tests/ocr_benchmark/tools/run_tesseract.py` | the Tesseract candidate |
| `tests/ocr_benchmark/tools/rtl.py` | the reading order, shared by both candidates |
| `tests/ocr_benchmark/tools/score_ocr.py` | the error rates |
| `tests/ocr_benchmark/tools/bench.py` | the comparison table |
| `tests/ocr_benchmark/results/summary.json` | the numbers in this file |

Run it with:

```bash
python3 tests/ocr_benchmark/tools/make_benchmark.py
python3 tests/ocr_benchmark/tools/run_paddleocr.py
python3 tests/ocr_benchmark/tools/run_tesseract.py --tessdata <dir>
python3 tests/ocr_benchmark/tools/bench.py paddleocr tesseract_best tesseract_fast
```
