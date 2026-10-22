#!/usr/bin/env python3
"""Generate original rounded vector tool glyphs and coordinated Jumlo mark assets.

All paths are project-local 24px line icons with no external font or network needed.
"""
from pathlib import Path
from xml.sax.saxutils import escape
from io import BytesIO
import cairosvg
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DRAWABLE = ROOT / 'app/src/main/res/drawable'
BRAND = ROOT / 'brand/svg'
TOOL_SVG = BRAND / 'tools'
PREVIEWS = ROOT / 'brand/previews'
IOS = ROOT / 'brand/ios/JumloAppIcon.appiconset'
for directory in (DRAWABLE, BRAND, TOOL_SVG, PREVIEWS, IOS): directory.mkdir(parents=True, exist_ok=True)

# Paired paths deliberately share one consistent 1.8dp, round-cap outline language.
ICONS = {
    'document': ['M7 2.5H14L19 7.5V20a1.5 1.5 0 0 1 -1.5 1.5H7A2 2 0 0 1 5 19.5v-15A2 2 0 0 1 7 2.5Z', 'M14 2.5V7.5H19', 'M8 12H16', 'M8 16H15'],
    'image': ['M5 4H19a2 2 0 0 1 2 2V18a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V6a2 2 0 0 1 2 -2Z', 'M8.3 9.2h.1','M4 17l5-5 4.5 4 3.1-3 4.1 4'],
    'files': ['M8 4H18a2 2 0 0 1 2 2V19a2 2 0 0 1 -2 2H8a2 2 0 0 1 -2 -2V6a2 2 0 0 1 2 -2Z', 'M4 16H3a1 1 0 0 1 -1 -1V5a3 3 0 0 1 3 -3H15', 'M10 10H16', 'M10 14H16'],
    'scan': ['M3 9V6a3 3 0 0 1 3-3h3', 'M15 3h3a3 3 0 0 1 3 3v3', 'M21 15v3a3 3 0 0 1 -3 3h-3', 'M9 21H6a3 3 0 0 1 -3-3v-3', 'M7 12h10', 'M9 9h6', 'M9 15h6'],
    'download': ['M12 3v12', 'M7.5 10.5L12 15l4.5-4.5', 'M4 17v2.5A1.5 1.5 0 0 0 5.5 21h13a1.5 1.5 0 0 0 1.5-1.5V17'],
    'cloud': ['M7 18H5.8a4.1 4.1 0 0 1 -1-8 7.1 7.1 0 0 1 13.3-1.9A4.8 4.8 0 0 1 18 18h-1', 'M12 12v8', 'M9 17l3 3 3-3'],
    'folder': ['M4.5 6H10l2 2h7.5a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-15a2 2 0 0 1 -2 -2V8a2 2 0 0 1 2 -2Z', 'M3 11h18'],
    'archive': ['M4 5a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v3H4V5Z','M5 8h14v11a2 2 0 0 1 -2 2H7a2 2 0 0 1 -2 -2V8Z','M10 12h4','M12 9v6'],
    'unarchive': ['M4 5a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v3H4V5Z','M5 8v11a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2V8','M12 18V11','M9 14l3-3 3 3'],
    'lock': ['M6 10H18a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2H6a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2Z','M8 10V7a4 4 0 0 1 8 0v3','M12 14v3'],
    'shield': ['M12 2l8 3v6c0 5.3-3.1 8.7-8 11-4.9-2.3-8-5.7-8-11V5l8-3Z','M8.5 12l2.5 2.5 4.5-5'],
    'edit': ['M5 19l4.5-1 10-10a2.1 2.1 0 0 0 -3-3l-10 10L5 19Z','M14.7 6.3l3 3','M4 21h16'],
    'merge': ['M7 3H5a2 2 0 0 0 -2 2v5a2 2 0 0 0 2 2h6', 'M17 3h2a2 2 0 0 1 2 2v5a2 2 0 0 1 -2 2h-6', 'M12 5v14','M8 15l4 4 4-4','M8 22h8'],
    'split': ['M5 4h14a2 2 0 0 1 2 2v4', 'M5 4a2 2 0 0 0 -2 2v4','M3 14v4a2 2 0 0 0 2 2h5','M21 14v4a2 2 0 0 1 -2 2h-5','M12 2v20','M9 11l3-3 3 3','M9 15l3 3 3-3'],
    'compress': ['M3 3l7 7','M6 10h4V6','M21 3l-7 7','M18 10h-4V6','M3 21l7-7','M6 14h4v4','M21 21l-7-7','M18 14h-4v4'],
    'crop': ['M6 3v13a2 2 0 0 0 2 2h13','M3 6h13a2 2 0 0 1 2 2v13','M6 9h9v9'],
    'eye': ['M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6-10-6-10-6Z','M12 9a3 3 0 1 0 0 6 3 3 0 1 0 0-6Z'],
    'grid': ['M4.5 3h5A1.5 1.5 0 0 1 11 4.5v5A1.5 1.5 0 0 1 9.5 11h-5A1.5 1.5 0 0 1 3 9.5v-5A1.5 1.5 0 0 1 4.5 3Z','M14.5 3h5A1.5 1.5 0 0 1 21 4.5v5a1.5 1.5 0 0 1 -1.5 1.5h-5A1.5 1.5 0 0 1 13 9.5v-5A1.5 1.5 0 0 1 14.5 3Z','M4.5 13h5A1.5 1.5 0 0 1 11 14.5v5A1.5 1.5 0 0 1 9.5 21h-5A1.5 1.5 0 0 1 3 19.5v-5A1.5 1.5 0 0 1 4.5 13Z','M14.5 13h5A1.5 1.5 0 0 1 21 14.5v5a1.5 1.5 0 0 1 -1.5 1.5h-5A1.5 1.5 0 0 1 13 19.5v-5A1.5 1.5 0 0 1 14.5 13Z'],
    'settings': ['M4 6h16','M4 12h16','M4 18h16','M9 4v4','M15 10v4','M8 16v4'],
    'compare': ['M3 4h18v16H3Z','M12 4v16','M6 8h3','M15 16h3','M15 10h3'],
    'layers': ['M12 3l9 5-9 5-9-5 9-5Z','M3 12l9 5 9-5','M3 16l9 5 9-5'],
    'search': ['M10.5 4a6.5 6.5 0 1 0 0 13 6.5 6.5 0 1 0 0-13Z','M16 16l5 5'],
    'checklist': ['M7 3H17a2 2 0 0 1 2 2v15a2 2 0 0 1 -2 2H7a2 2 0 0 1 -2 -2V5a2 2 0 0 1 2 -2Z','M9 9l1.3 1.3L13 7.5','M9 16l1.3 1.3L13 14.5','M15 9h2','M15 16h2'],
    'ocr': ['M4 9V6a2 2 0 0 1 2 -2h3','M15 4h3a2 2 0 0 1 2 2v3','M4 15v3a2 2 0 0 0 2 2h3','M15 20h3a2 2 0 0 0 2 -2v-3','M7 9h10','M12 9v8','M9 17h6'],
    'watermark': ['M5 3h11l4 4v12a2 2 0 0 1 -2 2H6a2 2 0 0 1 -2 -2V5a2 2 0 0 1 1 -2Z','M16 3v4h4','M7 15c2-4 3 4 5 0s3 4 5 0','M8 18h9'],
    'globe': ['M12 2.5a9.5 9.5 0 1 0 0 19 9.5 9.5 0 1 0 0-19Z','M2.8 12h18.4','M12 2.7c-4 5-4 13.5 0 18.6','M12 2.7c4 5 4 13.5 0 18.6'],
    'info': ['M12 2.5a9.5 9.5 0 1 0 0 19 9.5 9.5 0 1 0 0-19Z','M12 11v6','M12 7h.05'],
    'link': ['M10 13a5 5 0 0 0 7.2 0l2-2a5 5 0 0 0 -7-7l-2 2','M14 11a5 5 0 0 0 -7.2 0l-2 2a5 5 0 0 0 7 7l2-2'],
    'trash': ['M4 7h16','M9 7V4h6v3','M6.5 7l1 14h9l1-14','M10 11v6','M14 11v6'],
    'copy': ['M8 8h11a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2H8a2 2 0 0 1 -2 -2V10a2 2 0 0 1 2 -2Z','M4 16H3a1 1 0 0 1 -1 -1V5a3 3 0 0 1 3 -3h10a1 1 0 0 1 1 1v1'],
    'sparkles': ['M12 2l2.2 7.8L22 12l-7.8 2.2L12 22l-2.2-7.8L2 12l7.8-2.2L12 2Z','M19 2v4','M17 4h4'],
    'home': ['M3 11.5L12 4l9 7.5','M5.5 10.5V20h13v-9.5','M10 20v-6h4v6'],
    'printer': ['M6 9V3h12v6','M6 18H4a2 2 0 0 1 -2 -2v-5a2 2 0 0 1 2 -2h16a2 2 0 0 1 2 2v5a2 2 0 0 1 -2 2h-2','M6 15h12v7H6v-7Z','M18 12h.1'],
    'user': ['M12 4a4 4 0 1 0 0 8 4 4 0 1 0 0-8Z','M4 21v-2a8 8 0 0 1 16 0v2'],
    'star': ['M12 2.5l3 6.2 6.8 1-4.9 4.8 1.2 6.8L12 18l-6.1 3.3 1.2-6.8-4.9-4.8 6.8-1L12 2.5Z'],
    'rotate': ['M20 8V3l-3 3a9 9 0 1 0 3.5 11','M20 3h-5'],
    'audio': ['M5 10v4','M9 6v12','M13 3v18','M17 8v8','M21 11v2'],
    'video': ['M5 5h10a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V7a2 2 0 0 1 2 -2Z','M17 10l5-3v10l-5-3'],
}

# A single painter-based icon language provides consistent stroke weight and bounds.
MAPPING = {
    'document':['picture_as_pdf','pdf','description','assignment'],
    'image':['photo_library','image','collections','photo','add_photo_alternate'],
    'download':['download','file_download','cloud_download'],
    'link':['add_link','link','url'],
    'folder':['folder','folder_open','folder_shared'],
    'archive':['folder_zip','archive','zip'],
    'unarchive':['unarchive','extract'],
    'merge':['merge','call_merge','layers_merge','file_copy'],
    'layers':['layers','view_agenda'],
    'copy':['copy_all'],
    'crop':['crop','crop_rotate','aspect_ratio'],
    'compress':['compress','press','speed'],
    'eye':['visibility','file_open','preview','visibility_off'],
    'lock':['lock','enhanced_encryption','password'],
    'shield':['security','shield_lock','verified_user','shield','verified','approval'],
    'settings':['settings','tune','tune_rounded'],
    'search':['search'],
    'home':['home'],
    'grid':['grid_view','apps','dashboard'],
    'scan':['document_scanner'],
    'split':['content_cut','cut','scissors'],
    'sparkles':['bolt','auto_fix','flash_on','magic','build_circle'],
    'globe':['language','translate','webpage'],
    'info':['info'],
    'edit':['edit','rename','draw','drive_file_rename_outline','text_fields'],
    'checklist':['checklist'],
    'ocr':['ocr','text_scan'],
    'watermark':['watermark'],
    'trash':['delete','trash'],
    'cloud':['cloud','cloud_sync','sync'],
    'printer':['print'],
    'user':['person','account'],
    'star':['star','favorite'],
    'rotate':['rotate','rotate_right','history','schedule_history'],
    'compare':['swap','compare'],
    'files':['file_open2','storage','hard_drive'],
    'audio':['audio','audio_file'],
    'video':['video','movie'],
}

androidns = 'http://schemas.android.com/apk/res/android'
for key, paths in ICONS.items():
    elements=''.join(f'<path android:fillColor="@android:color/transparent" android:strokeColor="#FFFFFFFF" android:strokeWidth="1.8" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{escape(path)}" />' for path in paths)
    xml=f'''<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="{androidns}" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n{elements}\n</vector>\n'''
    (DRAWABLE/f'ic_prism_{key}.xml').write_text(xml)
    svg=f'<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#4743D5" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' + ''.join(f'<path d="{escape(path)}"/>' for path in paths) + '</svg>'
    (TOOL_SVG/f'{key}.svg').write_text(svg)

# Foreground brand silhouette. Fits the adaptive-icon 66x66 safe area.
page='M43 24H63L77 38V72C77 77 73 81 68 81H42C36 81 33 77 33 71V34C33 28 36 24 43 24Z'
fold='M63 24V34C63 37 65 39 69 39H77L63 24Z'
jsweep='M59 46V60C59 68 55 72 48 72C43 72 39 69 38 66'
jbar='M49 46H69'
mark_svg=f'''<path d="{page}" fill="#fff"/><path d="{fold}" fill="#83F5D2"/><path d="{jsweep}" stroke="#3734B6" stroke-width="6.5" fill="none" stroke-linecap="round" stroke-linejoin="round"/><path d="{jbar}" stroke="#3734B6" stroke-width="6" fill="none" stroke-linecap="round"/>'''
mono_svg=f'''<path d="{page}" fill="none" stroke="#fff" stroke-width="4" stroke-linejoin="round"/><path d="M63 24V39H77" fill="none" stroke="#fff" stroke-width="3" stroke-linejoin="round"/><path d="{jsweep}" fill="none" stroke="#fff" stroke-width="6" stroke-linecap="round"/><path d="{jbar}" fill="none" stroke="#fff" stroke-width="6" stroke-linecap="round"/>'''

def svg_icon(mode='light', rounded=True):
    if mode=='dark': a,b='#0B1535','#5351E8'
    elif mode=='tinted': a,b='#282B63','#676FE5'
    else: a,b='#3837BC','#8658F4'
    rect = '<rect width="512" height="512" rx="108" fill="url(#bg)"/>' if rounded else '<rect width="512" height="512" fill="url(#bg)"/>'
    graphic = mono_svg if mode=='tinted' else mark_svg
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 512 512"><defs><linearGradient id="bg" x1="0" y1="1" x2="1" y2="0"><stop stop-color="{a}"/><stop offset="1" stop-color="{b}"/></linearGradient><radialGradient id="glow"><stop stop-color="#B9B2FF" stop-opacity=".42"/><stop offset="1" stop-color="#B9B2FF" stop-opacity="0"/></radialGradient></defs>{rect}<circle cx="405" cy="90" r="255" fill="url(#glow)"/><g transform="scale(4.74074074)">{graphic}</g></svg>'''

(DRAWABLE/'ic_launcher_foreground.xml').write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="{androidns}" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
 <path android:fillColor="#FFFFFFFF" android:pathData="{page}"/>
 <path android:fillColor="#FF83F5D2" android:pathData="{fold}"/>
 <path android:fillColor="@android:color/transparent" android:strokeColor="#FF3734B6" android:strokeWidth="6.5" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{jsweep}"/>
 <path android:fillColor="@android:color/transparent" android:strokeColor="#FF3734B6" android:strokeWidth="6" android:strokeLineCap="round" android:pathData="{jbar}"/>
</vector>
''')
(DRAWABLE/'ic_launcher_monochrome.xml').write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="{androidns}" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
 <path android:fillColor="@android:color/transparent" android:strokeColor="#FFFFFFFF" android:strokeWidth="3.5" android:strokeLineJoin="round" android:pathData="{page}"/>
 <path android:fillColor="@android:color/transparent" android:strokeColor="#FFFFFFFF" android:strokeWidth="2.7" android:pathData="M63,24V39H77"/>
 <path android:fillColor="@android:color/transparent" android:strokeColor="#FFFFFFFF" android:strokeWidth="6" android:strokeLineCap="round" android:pathData="{jsweep}"/>
 <path android:fillColor="@android:color/transparent" android:strokeColor="#FFFFFFFF" android:strokeWidth="6" android:strokeLineCap="round" android:pathData="{jbar}"/>
</vector>
''')
(DRAWABLE/'ic_launcher_background.xml').write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="{androidns}" android:shape="rectangle"><gradient android:angle="45" android:startColor="#3837BC" android:endColor="#8658F4"/></shape>
''')
# Use a crop to remove launcher safe-zone whitespace in Compose's standalone brand tile.
(DRAWABLE/'ic_prism_brand_mark.xml').write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="{androidns}" android:width="64dp" android:height="64dp" android:viewportWidth="64" android:viewportHeight="64">
 <group android:translateX="-22" android:translateY="-22">
 <path android:fillColor="#FFFFFFFF" android:pathData="{page}"/>
 <path android:fillColor="#FF83F5D2" android:pathData="{fold}"/>
 <path android:fillColor="@android:color/transparent" android:strokeColor="#FF3734B6" android:strokeWidth="6.5" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{jsweep}"/>
 <path android:fillColor="@android:color/transparent" android:strokeColor="#FF3734B6" android:strokeWidth="6" android:strokeLineCap="round" android:pathData="{jbar}"/>
 </group>
</vector>
''')

for mode in ('light','dark','tinted'):
    (BRAND/f'jumlo-icon-{mode}.svg').write_text(svg_icon(mode,True))
    (BRAND/f'jumlo-ios-icon-{mode}.svg').write_text(svg_icon(mode,False))
    raw=cairosvg.svg2png(bytestring=svg_icon(mode,False).encode(),output_width=1024,output_height=1024)
    Image.open(BytesIO(raw)).convert('RGB').save(IOS/f'Jumlo-{mode}.png',optimize=True)
(BRAND/'jumlo-adaptive-foreground.svg').write_text('<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">'+mark_svg+'</svg>')
(BRAND/'jumlo-symbol.svg').write_text('<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="22 22 64 64">'+mark_svg+'</svg>')
(BRAND/'jumlo-monochrome.svg').write_text('<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">'+mono_svg+'</svg>')
for size in (32,64,128,192,256,512):
    raw=cairosvg.svg2png(bytestring=svg_icon('light',True).encode(),output_width=size,output_height=size)
    (PREVIEWS/f'jumlo-{size}.png').write_bytes(raw)

# Generate the mapping only once: Kotlin references generated vector IDs with static R.
entries=[]
for icon, names in MAPPING.items():
    keys=', '.join('"'+name+'"' for name in names)
    entries.append(f'    {keys} -> R.drawable.ic_prism_{icon}')
mapping_code='''package com.jumlo.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.jumlo.app.R

/** Original 2026 rounded outline set, consistent stroke and optically centered at 24dp. */
@DrawableRes
internal fun modernToolIconRes(name: String): Int = when (name) {
'''+ '\n'.join(entries)+'''
    else -> R.drawable.ic_prism_sparkles
}

@Composable
fun ModernToolIcon(iconName: String, tint: Color, modifier: Modifier = Modifier) {
    Icon(painter = painterResource(modernToolIconRes(iconName)),
        contentDescription = null, modifier = modifier, tint = tint)
}
'''
(ROOT/'app/src/main/java/com/jumlo/app/ui/components/ModernToolIcons.kt').write_text(mapping_code)
print(f'Generated {len(ICONS)} original vector glyphs, updated Android + iOS brand assets.')
