# On-Device Arabic OCR Model (`ara.traineddata`)

Place the official Tesseract `tessdata_fast` Arabic model file (`ara.traineddata`, ~1.4 MB) in this directory before building a release APK in Android Studio:

- Official download: https://github.com/tesseract-ocr/tessdata_fast/raw/main/ara.traineddata
- Target path: `app/src/main/assets/tessdata/ara.traineddata`

`ArabicTesseractOcrEngine` automatically extracts this file on first run into private app storage for 100% offline Arabic OCR with zero server cost.
