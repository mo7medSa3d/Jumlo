#!/usr/bin/env python3
"""Deterministic real PNG inputs for importing/processing through Jumlo's public UI.

This creates input files only. It never seeds the app database or invents published outputs.
"""
import argparse
import binascii
import json
from pathlib import Path
import struct
import zlib


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", binascii.crc32(kind + data) & 0xFFFFFFFF)


def png(index, width=1280, height=960):
    compressor = zlib.compressobj(level=6)
    templates = []
    for parity in (0, 1):
        row = bytearray(1 + width * 3)  # index 0 = 0 (PNG scanline filter: None)
        for x in range(width):
            offset = 1 + x * 3
            row[offset] = (x // 5 + index * 17) % 256
            row[offset + 2] = 48 + (((x // 80) + parity + index) % 2) * 144
        templates.append(row)
    last_g = [-1, -1]
    blocks = []
    for y in range(height):
        parity = (y // 80) % 2
        row = templates[parity]
        g = (y // 4 + index * 29) % 256
        if g != last_g[parity]:
            row[2::3] = bytes([g]) * width
            last_g[parity] = g
        blocks.append(compressor.compress(row))
    blocks.append(compressor.flush())
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", b"".join(blocks)) + chunk(b"IEND", b""))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    records = []
    for index in range(1, 25):
        path = args.output / f"jumlo-benchmark-{index:03}.png"
        data = png(index)
        path.write_bytes(data)
        records.append({"file": path.name, "width": 1280, "height": 960, "bytes": len(data)})
    (args.output / "manifest.json").write_text(json.dumps(records, indent=2) + "\n", encoding="utf-8")
    print(f"Created {len(records)} PNG inputs in {args.output}; import and process them in Jumlo before running Files benchmarks.")


if __name__ == "__main__":
    main()
