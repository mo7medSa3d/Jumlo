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
    def test_all_rounded_vector_icons_are_valid(self):
        vectors=list((RES/'drawable').glob('ic_prism_*.xml'))
        self.assertGreaterEqual(len(vectors),39)
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
                        self.assertEqual(path.get(ns+'strokeLineCap'),'round')
                        self.assertTrue(path.get(ns+'pathData'))
    def test_every_catalogue_icon_maps_to_a_real_vector(self):
        mappings=read('ui/components/ModernToolIcons.kt')
        resources=set(re.findall(r'R.drawable.(ic_prism_\w+)',mappings))
        for icon in resources: self.assertTrue((RES/'drawable'/f'{icon}.xml').is_file(),icon)
        catalogue=read('data/jobs/CapabilityRegistry.kt')
        names=set()
        for line in catalogue.splitlines():
            if 'UtilityTool(' not in line: continue
            values=re.findall(r'"([^"]+)"',line)
            if len(values)>=9:
                names.add(values[8])
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
    def test_brand_is_coherent_on_launcher_topbar_and_intro(self):
        for name in ['ic_launcher_foreground.xml','ic_launcher_monochrome.xml',
                     'ic_launcher_background.xml','ic_prism_brand_mark.xml']:
            ET.parse(RES/'drawable'/name)
        self.assertIn('R.drawable.ic_prism_brand_mark',read('ui/components/QuietTopBars.kt'))
        self.assertIn('R.drawable.ic_prism_brand_mark',read('ui/components/JumloSplash.kt'))
        for folder in ('values','values-ar'):
            values={tag.attrib['name']:tag.text for tag in ET.parse(RES/folder/'strings.xml').getroot() if tag.tag=='string'}
            self.assertEqual(values.get('app_name'),'Jumlo')
            self.assertTrue({tag.attrib['name'] for tag in ET.parse(RES/folder/'jumlo.xml').getroot()}.issuperset({'brand_splash_tagline'}))
        self.assertIn('#0C1231',(RES/'values-v31/jumlo_splash.xml').read_text())
    def test_startup_motion_is_tied_to_actual_readiness(self):
        main=read('MainActivity.kt')
        splash=read('ui/components/JumloSplash.kt')
        self.assertIn('settings.isLoaded && workspaceReady',main)
        self.assertIn('coldLauncher && !reduceMotion',main)
        self.assertIn('SystemClock.elapsedRealtime()',main)
        self.assertIn('delay(remaining)',main)
        self.assertIn('AnimatedVisibility(',main)
        self.assertIn('if (reduceMotion) ExitTransition.None',main)
        self.assertIn('LocalReducedMotion.current',splash)
        self.assertIn('if (reduced) 0f else',splash)
        self.assertIn('rememberInfiniteTransition',splash)
        self.assertIn('stringResource(R.string.brand_splash_tagline)',splash)
    def test_live_workspace_bindings_are_not_replaced_with_mock_actions(self):
        tools=read('ui/tools/ToolsScreen.kt')
        home=read('ui/home/HomeScreen.kt')
        self.assertIn('onClick = { haptics.actionClick(); onClick() }',tools)
        self.assertIn('onClick = { haptics.actionClick(); onClick() }',read('ui/components/NeonUi.kt'))
        self.assertIn('CapabilityRegistry.canProcess(it.id)',home)
        self.assertIn('NeonToolTile(',home)
        self.assertIn('JumloNavHost(appViewModel)',read('MainActivity.kt'))

if __name__=='__main__': unittest.main(verbosity=2)
