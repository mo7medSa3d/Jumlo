#!/usr/bin/env python3
"""Verify catalogue icon coverage, licensed provenance, size and distinct silhouettes."""
from pathlib import Path
import hashlib
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
registry = (root / 'app/src/main/java/com/jumlo/app/data/jobs/CapabilityRegistry.kt').read_text()
ids = re.findall(r'UtilityTool\("([^\"]+)"', registry)
android = '{http://schemas.android.com/apk/res/android}'
missing = [id for id in ids if not (root / f'app/src/main/res/drawable/ic_tool_{id}.xml').exists()]
assert not missing, f'Missing tool vectors: {missing}'
paths = {}
for id in ids:
    icon = root / f'app/src/main/res/drawable/ic_tool_{id}.xml'
    vector = ET.parse(icon).getroot()
    assert vector.attrib[android+'width'] == '24dp', id
    assert vector.attrib[android+'height'] == '24dp', id
    geometry = '|'.join(p.attrib[android+'pathData'] for p in vector.iter('path'))
    assert geometry, f'Empty vector: {id}'
    digest = hashlib.sha256(geometry.encode()).hexdigest()
    assert digest not in paths, f'{id} shares geometry with {paths.get(digest)}'
    paths[digest] = id
mapping = (root / 'app/src/main/java/com/jumlo/app/ui/components/ToolIcons.kt').read_text()
for id in ids:
    assert f'"{id}" to R.drawable.ic_tool_{id}' in mapping, f'Missing icon mapping: {id}'
assert (root / 'app/src/main/assets/licenses/material-symbols-Apache-2.0.txt').exists()
print(f'PASS: {len(ids)} unique 24dp tool vectors, full mapping and bundled Apache license.')
