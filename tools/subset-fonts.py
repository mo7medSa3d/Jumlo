#!/usr/bin/env python3
"""Rebuild the bundled Arabic/Latin subset from the original fonts (fonttools required).

Usage: python3 tools/subset-fonts.py ORIGINAL_FONT_DIRECTORY
The checked-in subset is ready to build; this tool is not a build prerequisite.
"""
from pathlib import Path
import json
import sys
from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parents[1]
source = Path(sys.argv[1]).resolve()
destination = ROOT / 'app/src/main/res/font'
ranges = [(0x20,0x24F),(0x300,0x36F),(0x600,0x6FF),(0x750,0x77F),
          (0x8A0,0x8FF),(0x2000,0x206F),(0x20A0,0x20CF),(0x2100,0x22FF),
          (0xFB50,0xFDFF),(0xFE70,0xFEFF)]
characters = {n for first,last in ranges for n in range(first,last+1)}
results = []
for filename,family in [('ibm_plex_sans_variable.ttf','Jumlo Sans'),
                        ('ibm_plex_sans_arabic_regular.ttf','Jumlo Sans Arabic')]:
    original=source/filename
    font=TTFont(original)
    needed=set(font.getBestCmap()) & characters
    options=subset.Options()
    options.name_IDs=['*']
    options.name_legacy=True
    options.name_languages=['*']
    options.layout_features=['*']
    options.notdef_glyph=True
    options.notdef_outline=True
    cutter=subset.Subsetter(options=options)
    cutter.populate(unicodes=needed)
    cutter.subset(font)
    if 'fvar' in font:
        font=instantiateVariableFont(font,{'wdth':100,'wght':(400,400,700)},inplace=False)
    # Modified fonts use new family/PostScript names; preserve original copyright/license records.
    for record in font['name'].names:
        if record.nameID in (1,3,4,6,16,25):
            name=family.replace(' ','') if record.nameID in (6,25) else family
            record.string=name.encode(record.getEncoding(),errors='replace')
    target=destination/filename
    font.save(target)
    result=TTFont(target)
    assert needed <= set(result.getBestCmap()), 'Required glyphs lost'
    results.append({'file':filename,'original_bytes':original.stat().st_size,
                    'subset_bytes':target.stat().st_size,'unicode_codepoints':len(needed)})
print(json.dumps(results,indent=2))
