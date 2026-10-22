#!/usr/bin/env python3
"""Apply borderless tools, modern original vector iconography and launch polish."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/com/jumlo/app'
RES=ROOT/'app/src/main/res'

def update(path, old, new, count=1):
    p=Path(path)
    s=p.read_text()
    if s.count(old)!=count: raise RuntimeError(f'Expected {count} matches for {p}: {old[:95]!r}, got {s.count(old)}')
    p.write_text(s.replace(old,new))

TOOLS=JAVA/'ui/tools/ToolsScreen.kt'
update(TOOLS,'import androidx.compose.foundation.BorderStroke\n','')
update(TOOLS,'import androidx.compose.foundation.shape.CircleShape\n','')
update(TOOLS,'            maxWidth >= 900.dp -> if (largeText) 4 else 6\n            maxWidth >= 600.dp -> if (largeText) 3 else 4\n            else -> if (largeText) 2 else 3',
      '            maxWidth >= 900.dp -> if (largeText) 3 else 5\n            maxWidth >= 600.dp -> if (largeText) 2 else 3\n            else -> 2')
# Remove chips' hard stroke and restore proper 44dp interaction size.
update(TOOLS,'''FilterChip(category == "all", { category = "all" },
                                label = { Text(stringResource(R.string.ui_all)) })''',
'''FilterChip(category == "all", { category = "all" },
                                modifier = Modifier.heightIn(min = 44.dp),
                                shape = RoundedCornerShape(16.dp), border = null,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                                label = { Text(stringResource(R.string.ui_all)) })''')
update(TOOLS,'''FilterChip(category == pillar.name, { category = pillar.name },
                                label = { Text(if (settings.language.isRtl) pillar.labelAr else pillar.labelEn) })''',
'''FilterChip(category == pillar.name, { category = pillar.name },
                                modifier = Modifier.heightIn(min = 44.dp),
                                shape = RoundedCornerShape(16.dp), border = null,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                                label = { Text(if (settings.language.isRtl) pillar.labelAr else pillar.labelEn) })''')
update(TOOLS,'''        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.85f))''',
'''        color = colors.surfaceContainerLow,
        shadowElevation = 2.dp''')
update(TOOLS,'''            Box(Modifier.size(54.dp).background(iconSurface, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center) {
                Icon(resolveStitchIcon(tool.iconName), contentDescription = null,
                    modifier = Modifier.size(26.dp), tint = tint)
            }''',
'''            Box(Modifier.size(56.dp).background(iconSurface, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center) {
                ModernToolIcon(tool.iconName, tint, Modifier.size(28.dp))
            }''')
update(TOOLS,'modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 13.dp)',
      'modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 16.dp)')
update(TOOLS, 'verticalArrangement = Arrangement.spacedBy(9.dp)\n        ) {\n            Box(Modifier.size(56.dp)',
      'verticalArrangement = Arrangement.spacedBy(12.dp)\n        ) {\n            Box(Modifier.size(56.dp)')
# Text sizing is readable on a 2-column phone grid.
update(TOOLS,'style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)',
      'style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)')

NEON=JAVA/'ui/components/NeonUi.kt'
update(NEON,'''        border = BorderStroke(1.dp, accent?.copy(alpha = 0.35f)
            ?: colors.outlineVariant.copy(alpha = 0.82f)),
        shadowElevation = 1.dp''',
'''        shadowElevation = 2.dp''')
update(NEON,'''        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.85f))''',
'''        color = colors.surfaceContainerLow,
        shadowElevation = 2.dp''')
update(NEON,'''                Icon(resolveStitchIcon(tool.iconName), contentDescription = null,
                    tint = tint, modifier = Modifier.size(if (compact) 25.dp else 28.dp))''',
'''                ModernToolIcon(tool.iconName, tint,
                    Modifier.size(if (compact) 27.dp else 29.dp))''')
# Hero scans are real onClick, just the icon changes; no fake feature added.
update(NEON,'Icon(Icons.Rounded.DocumentScanner, contentDescription = null,\n                        tint = colors.onPrimary, modifier = Modifier.size(28.dp))',
      'ModernToolIcon("document_scanner", colors.onPrimary, Modifier.size(28.dp))')
update(NEON,'Icon(Icons.Rounded.DocumentScanner, null, Modifier.size(20.dp))',
      'ModernToolIcon("document_scanner", colors.primary, Modifier.size(20.dp))')

HOME=JAVA/'ui/home/HomeScreen.kt'
update(HOME,'''                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)''',
'''                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    shadowElevation = 2.dp''')
update(HOME,'import androidx.compose.foundation.BorderStroke\n','')

TOP=JAVA/'ui/components/QuietTopBars.kt'
update(TOP,'import androidx.compose.foundation.background\n','import androidx.compose.foundation.background\nimport androidx.compose.foundation.Image\n')
update(TOP,'import androidx.compose.ui.graphics.Color\n','import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.Brush\n')
update(TOP,'import androidx.compose.ui.res.stringResource\n','import androidx.compose.ui.res.stringResource\nimport androidx.compose.ui.res.painterResource\n')
update(TOP,'import androidx.compose.ui.unit.dp\n','import androidx.compose.ui.unit.dp\nimport androidx.compose.ui.unit.sp\n')
update(TOP,'''                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(15.dp))
                        .background(colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null,
                        tint = colors.onPrimary, modifier = Modifier.size(24.dp))
                }''',
'''                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(15.dp))
                        .background(Brush.linearGradient(listOf(colors.primary, colors.tertiary))),
                    contentAlignment = Alignment.Center
                ) {
                    Image(painterResource(R.drawable.ic_prism_brand_mark),
                        contentDescription = null, modifier = Modifier.size(37.dp))
                }''')
update(TOP,'style = QuietTheme.typography.screenTitle,\n                    color = colors.onSurface',
      'style = QuietTheme.typography.screenTitle.copy(letterSpacing = (-0.8).sp),\n                    color = colors.onSurface')

DOCK=JAVA/'ui/components/QuietNavigation.kt'
update(DOCK,'import androidx.compose.ui.graphics.vector.ImageVector\n','import androidx.compose.ui.res.painterResource\n')
update(DOCK,'''            border = BorderStroke(1.dp, palette.outlineVariant),
            tonalElevation = 0.dp,''',
'''            tonalElevation = 0.dp,''')
update(DOCK,'''                            border = BorderStroke(3.dp, palette.surfaceContainerLow),
                            shadowElevation = QuietTheme.elevation.floating,''',
'''                            shadowElevation = QuietTheme.elevation.floating,''')
update(DOCK,'Icon(if(onPrint!=null) Icons.Rounded.Print else Icons.Rounded.DocumentScanner, null, Modifier.size(28.dp))',
      'Icon(painterResource(if(onPrint!=null) R.drawable.ic_prism_printer else R.drawable.ic_prism_scan), null, Modifier.size(29.dp))')
update(DOCK,'''private fun icon(tab: DockTab): ImageVector = when (tab) {
    DockTab.HOME -> Icons.Rounded.Home
    DockTab.FILES -> Icons.Rounded.Folder
    DockTab.TOOLS -> Icons.Rounded.GridView
    DockTab.SETTINGS -> Icons.Rounded.Person
    DockTab.DOWNLOADS -> Icons.Rounded.Download
    DockTab.PRINT -> Icons.Rounded.Print
}''',
'''private fun icon(tab: DockTab): Int = when (tab) {
    DockTab.HOME -> R.drawable.ic_prism_home
    DockTab.FILES -> R.drawable.ic_prism_folder
    DockTab.TOOLS -> R.drawable.ic_prism_grid
    DockTab.SETTINGS -> R.drawable.ic_prism_user
    DockTab.DOWNLOADS -> R.drawable.ic_prism_download
    DockTab.PRINT -> R.drawable.ic_prism_printer
}''')
update(DOCK,'Icon(icon(tab), null, Modifier.size(23.dp).graphicsLayer {',
      'Icon(painterResource(icon(tab)), null, Modifier.size(24.dp).graphicsLayer {')

SPLASH_XML=RES/'values-v31/jumlo_splash.xml'
update(SPLASH_XML,'#2522A7','#0C1231',2)

# Improve the bilingual brand sentence without renaming the existing app id.
update(RES/'values/jumlo.xml','Make files flow.','Create. Scan. Make it flow.')
update(RES/'values-ar/jumlo.xml',"<string name=\"brand_splash_tagline\">\u0645\u0644\u0641\u0627\u062a\u0643 \u0628\u0627\u0646\u0633\u064a\u0627\u0628\u064a\u0629.</string>",
      '<string name="brand_splash_tagline">\u0623\u0646\u0634\u0626. \u0627\u0645\u0633\u062d. \u0648\u0627\u0646\u062c\u0632 \u0628\u0633\u0647\u0648\u0644\u0629.</string>')

print('Updated tools, home, dock, top bar, splash background and translated taglines.')
