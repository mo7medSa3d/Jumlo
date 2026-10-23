#!/usr/bin/env python3
"""Source-contract tests only: validate brand, modern vectors and startup behavior."""
from pathlib import Path
from xml.etree import ElementTree as ET
import re
import unittest

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'app/src/main/java/com/jumlo/app'
RES=ROOT/'app/src/main/res'

def read(rel): return (SOURCE/rel).read_text()

class Prism2026LaunchIconChecks(unittest.TestCase):
    def test_used_icon_family_is_filled_vectors_without_the_extended_library(self):
        vectors=list((RES/'drawable').glob('ic_prism_*.xml'))
        self.assertTrue(vectors)
        self.assertNotIn('material-icons-extended',(ROOT/'app/build.gradle.kts').read_text())
        ns='{http://schemas.android.com/apk/res/android}'
        for file in vectors:
            with self.subTest(icon=file.name):
                root=ET.parse(file).getroot()
                self.assertEqual(root.tag,'vector')
                self.assertTrue(root.findall('.//path'))
                self.assertTrue(root.get(ns+'viewportWidth'))
                if file.name != 'ic_prism_brand_mark.xml':
                    self.assertEqual(root.get(ns+'viewportWidth'),'24')
                    for path in root.findall('.//path'):
                        self.assertTrue(path.get(ns+'pathData'))
                        # Filled/duotone paths replaced the old stroke-only icon family.
                        self.assertNotIn(path.get(ns+'fillColor'),(None,'#00000000','@android:color/transparent'))
                        self.assertGreater(float(path.get(ns+'fillAlpha','1')),0)
                        self.assertEqual(float(path.get(ns+'strokeWidth','0')),0)
    def test_every_catalogue_icon_maps_to_a_real_vector(self):
        mappings=read('ui/components/ModernToolIcons.kt')
        resources=set(re.findall(r'R.drawable.(ic_prism_\w+)',mappings))
        self.assertTrue(resources)
        for icon in resources: self.assertTrue((RES/'drawable'/f'{icon}.xml').is_file(),icon)
        catalogue=read('data/jobs/CapabilityRegistry.kt')
        names=set()
        for line in catalogue.splitlines():
            if 'UtilityTool(' not in line: continue
            values=re.findall(r'"([^"]+)"',line)
            if len(values)>=7:
                names.add(values[6])
        self.assertTrue(names,'Catalogue parsing must not pass vacuously')
        # Model data and icon route names are never required to use a generic fallback.
        mapped=set(re.findall(r'"([a-z][a-z0-9_]*)"',mappings))
        self.assertFalse(names-mapped, f'Catalogue icons missing a modern vector: {names-mapped}')
        self.assertIn('ModernToolIcon(tool.iconName',read('ui/components/NeonUi.kt'))
        self.assertIn('ModernToolIcon(tool.iconName',read('ui/tools/ToolsScreen.kt'))
    def test_tools_use_borderless_and_readable_grid(self):
        tools=read('ui/tools/ToolsScreen.kt')
        tiles=read('ui/components/NeonUi.kt')
        self.assertNotIn('BorderStroke(', tools)
        segment=tiles.split('fun NeonToolTile(',1)[1].split('fun NeonOutputPreview(',1)[0]
        self.assertNotIn('BorderStroke(',segment)
        self.assertIn('shadowElevation = 2.dp',tools)
        self.assertIn('shadowElevation = 2.dp',segment)
        self.assertIn('else -> 2',tools)
        self.assertIn('heightIn(min = 122.dp)',tools)
        self.assertIn('CapabilityRegistry.enabled(it.id)',tools)
    def test_brand_uses_the_active_launcher_theme_and_simple_topbar(self):
        for name in ['ic_launcher_foreground.xml','ic_launcher_monochrome.xml',
                     'ic_launcher_background.xml','ic_prism_brand_mark.xml']:
            ET.parse(RES/'drawable'/name)
        self.assertIn('Text(title, style = QuietTheme.typography.screenTitle',read('ui/components/QuietTopBars.kt'))
        self.assertIn('title=if(shellRoute=="home") stringResource(R.string.app_name)',read('ui/navigation/JumloNavHost.kt'))
        self.assertNotIn('QuietTonalIconButton(Icons.Rounded.Search',read('ui/components/QuietTopBars.kt'))
        for folder in ('values','values-ar'):
            values={tag.attrib['name']:tag.text for tag in ET.parse(RES/folder/'strings.xml').getroot() if tag.tag=='string'}
            self.assertEqual(values.get('app_name'),'Jumlo')
            self.assertTrue({tag.attrib['name'] for tag in ET.parse(RES/folder/'jumlo.xml').getroot()}.issuperset({'brand_splash_tagline'}))
        android='{http://schemas.android.com/apk/res/android}'
        activity=next(node for node in ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot().findall('application/activity')
                      if node.get(android+'name')=='.MainActivity')
        self.assertEqual(activity.get(android+'theme'),'@style/Theme.Jumlo.Starting')
        themes=ET.parse(RES/'values/themes.xml').getroot()
        starting=next(node for node in themes.findall('style') if node.get('name')=='Theme.Jumlo.Starting')
        self.assertEqual(starting.get('parent'),'Theme.SplashScreen')
        items={node.get('name'):node.text for node in starting.findall('item')}
        self.assertEqual(items['windowSplashScreenAnimatedIcon'],'@drawable/ic_launcher_foreground')
        self.assertEqual(items['postSplashScreenTheme'],'@style/Theme.Jumlo')
        # Resolve qualified resources instead of pinning the old blue API31 override.
        night_colors={node.get('name'):node.text for path in (RES/'values-night').glob('*.xml')
                      for node in ET.parse(path).getroot() if node.tag=='color'}
        self.assertIn(night_colors.get('jumlo_launch_background'),('#000000','#FF000000'))
        backgrounds={'android:windowBackground','windowSplashScreenBackground',
                     'android:windowSplashScreenBackground','android:windowSplashScreenIconBackgroundColor'}
        for folder in RES.glob('values*'):
            for path in folder.glob('*.xml'):
                for style in ET.parse(path).getroot().findall('style'):
                    if style.get('name') not in {'Theme.Jumlo','Theme.Jumlo.Starting'}: continue
                    for item in style.findall('item'):
                        if item.get('name') in backgrounds:
                            self.assertEqual(item.text,'@color/jumlo_launch_background',str(path))
    def test_startup_uses_bounded_platform_splash_and_exit_motion(self):
        main=read('MainActivity.kt')
        self.assertIn('installSplashScreen()',main)
        self.assertIn('splash.setKeepOnScreenCondition',main)
        self.assertIn('!appViewModel.settings.value.isLoaded || !appViewModel.workspaceReady.value',main)
        limit=re.search(r'SystemClock.elapsedRealtime\(\)\s*-\s*startedAt\s*<\s*(\d+)L',main)
        self.assertIsNotNone(limit,'A readiness-only condition can hold the splash indefinitely')
        exit_duration=re.search(r'provider.view.animate\(\).alpha\(0f\).setDuration\((\d+)L\)',main)
        self.assertIsNotNone(exit_duration)
        self.assertLessEqual(int(limit.group(1))+int(exit_duration.group(1)),1500)
        self.assertIn('.withEndAction { provider.remove() }.start()',main)
        self.assertNotIn('delay(',main)
        self.assertLess(main.index('installSplashScreen()'),main.index('super.onCreate(savedInstanceState)'))
    def test_live_workspace_bindings_are_not_replaced_with_mock_actions(self):
        tools=read('ui/tools/ToolsScreen.kt')
        home=read('ui/home/HomeScreen.kt')
        self.assertIn('onClick = { haptics.actionClick(); onClick() }',tools)
        self.assertIn('onClick = { haptics.actionClick(); onClick() }',read('ui/components/NeonUi.kt'))
        self.assertIn('CapabilityRegistry.enabled(id)',home)
        self.assertIn('CapabilityRegistry.tools.firstOrNull { it.id == id }?.let(onTool)',home)
        self.assertIn('HomeQuickAction(stringResource(label), icon, action,',home)
        self.assertIn('open("pdf_compress")',home)
        self.assertIn('open("dl_media")',home)
        self.assertIn('JumloNavHost(appViewModel)',read('MainActivity.kt'))

if __name__=='__main__': unittest.main(verbosity=2)
