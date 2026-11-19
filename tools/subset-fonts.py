#!/usr/bin/env python3
"""Rebuild the bundled Arabic/Latin subset from the original fonts (fonttools required).

Usage: python3 tools/subset-fonts.py ORIGINAL_FONT_DIRECTORY
The checked-in subset is ready to build; this tool is not a build prerequisite.
"""
from pathlib import Path
import json
import os
import sys
import tempfile
from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / 'app/src/main/res/font'
RANGES = [(0x20,0x24F),(0x300,0x36F),(0x600,0x6FF),(0x750,0x77F),
          (0x8A0,0x8FF),(0x2000,0x206F),(0x20A0,0x20CF),(0x2100,0x22FF),
          (0xFB50,0xFDFF),(0xFE70,0xFEFF)]
CHARACTERS = {n for first,last in RANGES for n in range(first,last+1)}


def main(argv: list[str] | None = None) -> int:
    args = sys.argv[1:] if argv is None else argv
    if len(args) != 1 or args[0] in ('-h', '--help'):
        print('Usage: python3 tools/subset-fonts.py ORIGINAL_FONT_DIRECTORY', file=sys.stderr)
        return 0 if args and args[0] in ('-h', '--help') else 2
    source = Path(args[0]).resolve()
    if not source.is_dir():
        print(f'subset-fonts: source directory not found: {source}', file=sys.stderr)
        return 2
    results = []
    for filename,family in [('ibm_plex_sans_variable.ttf','Jumlo Sans'),
                            ('ibm_plex_sans_arabic_regular.ttf','Jumlo Sans Arabic')]:
        original = source / filename
        if not original.is_file():
            print(f'subset-fonts: missing font file: {original}', file=sys.stderr)
            return 2
        font = TTFont(original)
        try:
            needed = set(font.getBestCmap()) & CHARACTERS
            options = subset.Options()
            options.name_IDs=['*']
            options.name_legacy=True
            options.name_languages=['*']
            options.layout_features=['*']
            options.notdef_glyph=True
            options.notdef_outline=True
            cutter = subset.Subsetter(options=options)
            cutter.populate(unicodes=needed)
            cutter.subset(font)
            if 'fvar' in font:
                instantiated = instantiateVariableFont(font,{'wdth':100,'wght':(400,400,700)},inplace=False)
                font.close()
                font = instantiated
            # Modified fonts use new family/PostScript names; preserve original copyright/license records.
            for record in font['name'].names:
                if record.nameID in (1,3,4,6,16,25):
                    name = family.replace(' ','') if record.nameID in (6,25) else family
                    record.string = name.encode(record.getEncoding(),errors='replace')
            target = DESTINATION / filename
            fd, tmp_name = tempfile.mkstemp(prefix='.subset-', suffix='.ttf', dir=DESTINATION)
            os.close(fd)
            tmp_path = Path(tmp_name)
            try:
                font.save(tmp_path)
                result = TTFont(tmp_path)
                try:
                    if not (needed <= set(result.getBestCmap())):
                        raise RuntimeError('Required glyphs lost')
                finally:
                    result.close()
                os.chmod(tmp_path, 0o644)
                os.replace(tmp_path, target)
            except BaseException:
                tmp_path.unlink(missing_ok=True)
                raise
            results.append({'file':filename,'original_bytes':original.stat().st_size,
                            'subset_bytes':target.stat().st_size,'unicode_codepoints':len(needed)})
        finally:
            font.close()
    print(json.dumps(results,indent=2))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
