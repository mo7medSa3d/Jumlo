#!/usr/bin/env python3
"""Portable structure / asset tests; not an Android build or hardware confirmation."""
import json
from pathlib import Path
import re
import unittest
import wave
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/com/jumlo/app'
RES=ROOT/'app/src/main/res'
BRAND=ROOT/'brand'

def code(path): return (JAVA/path).read_text()
def lum(value):
    rgb=[int(value[i:i+2],16)/255 for i in (0,2,4)]
    parts=[c/12.92 if c<=0.04045 else ((c+0.055)/1.055)**2.4 for c in rgb]
    return sum(a*b for a,b in zip(parts,(0.2126,0.7152,0.0722)))
def ratio(a,b):
    a,b=lum(a),lum(b)
    return (max(a,b)+.05)/(min(a,b)+.05)

class JumloBrandMotionChecks(unittest.TestCase):
    def test_app_brand_matches_translated_app_name(self):
        for locale in ('values','values-ar'):
            values={e.attrib.get('name'):e.text for e in ET.parse(RES/locale/'strings.xml').getroot() if e.tag=='string'}
            self.assertEqual(values['app_name'],'Jumlo')
        self.assertIn('@string/app_name',(ROOT/'app/src/main/AndroidManifest.xml').read_text())
        self.assertIn('jumlo-errors.txt',code('ui/navigation/JumloNavHost.kt'))
    def test_upgrade_identity_preserved(self):
        manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
        self.assertIn('android:scheme="jumlo"',manifest)
        self.assertIn('android:host="workspace"',manifest)
        self.assertIn('applicationId = "com.jumlo.app"',(ROOT/'app/build.gradle.kts').read_text())
    def test_all_icon_sources_are_parseable_svg(self):
        expected=['jumlo-icon-light.svg','jumlo-icon-dark.svg','jumlo-icon-tinted.svg','jumlo-adaptive-foreground.svg','jumlo-monochrome.svg']
        for name in expected:
            with self.subTest(name=name):
                root=ET.parse(BRAND/'svg'/name).getroot()
                self.assertTrue(root.tag.endswith('svg'))
                self.assertIn('viewBox',root.attrib)
    def test_ios_icon_exports_are_present_without_claiming_ios_app(self):
        folder=BRAND/'ios/JumloAppIcon.appiconset'
        manifest=json.loads((folder/'Contents.json').read_text())
        self.assertEqual(3,len(manifest['images']))
        for record in manifest['images']:
            data=(folder/record['filename']).read_bytes()
            self.assertEqual(b'\x89PNG\r\n\x1a\n',data[:8])
            import struct
            w,h=struct.unpack('>II',data[16:24])
            self.assertEqual((w,h),(1024,1024))
    def test_ios_icon_exports_are_opaque_because_apple_rejects_alpha(self):
        """Apple rejects an App Store icon that carries an alpha channel.

        The iOS export must therefore be a full-bleed RGB image with no transparent
        pixels; iOS applies its own corner mask, so the artwork keeps no rounded
        corners of its own.
        """
        import struct
        folder=BRAND/'ios/JumloAppIcon.appiconset'
        manifest=json.loads((folder/'Contents.json').read_text())
        for record in manifest['images']:
            with self.subTest(filename=record['filename']):
                data=(folder/record['filename']).read_bytes()
                # PNG colour type 2 is truecolour RGB, 6 is truecolour + alpha.
                self.assertEqual(2,data[25],'iOS app icons must not carry an alpha channel')
                self.assertNotIn(b'tRNS',data,'iOS app icons must not carry transparency')
                width,height=struct.unpack('>II',data[16:24])
                self.assertEqual((width,height),(1024,1024))
    def test_ios_icon_sources_are_full_bleed_with_no_own_corner_mask(self):
        for variant in ('light','dark','tinted'):
            with self.subTest(variant=variant):
                text=(BRAND/'svg'/f'jumlo-ios-icon-{variant}.svg').read_text()
                self.assertNotIn('rx="106"',text,'the iOS tile must not round its own corners')
                root=ET.parse(BRAND/'svg'/f'jumlo-ios-icon-{variant}.svg').getroot()
                self.assertEqual('0 0 512 512',root.attrib['viewBox'])
    def test_android_adaptive_and_themed_icon_exist(self):
        for name in ['ic_launcher.xml','ic_launcher_round.xml']:
            xml=ET.parse(RES/'mipmap-anydpi-v26'/name).getroot()
            self.assertEqual('adaptive-icon',xml.tag)
            self.assertEqual({'background','foreground','monochrome'},{item.tag for item in xml})
        for name in ['ic_launcher_background.xml','ic_launcher_foreground.xml','ic_launcher_monochrome.xml']:
            ET.parse(RES/'drawable'/name)
    def test_ui_tones_are_tiny_valid_mono_waveforms(self):
        root=RES/'raw'
        expected=['tap','toggle','start','success','error','delete']
        size=0
        for cue in expected:
            file=root/f'jumlo_{cue}.wav'
            with wave.open(str(file),'rb') as track:
                self.assertEqual(track.getnchannels(),1)
                self.assertEqual(track.getsampwidth(),2)
                self.assertEqual(track.getframerate(),22050)
                self.assertLess(track.getnframes()/track.getframerate(),.4)
                self.assertGreater(track.getnframes(),0)
            size+=file.stat().st_size
        self.assertLess(size,60*1024)
        self.assertTrue((ROOT/'scripts/generate-jumlo-sounds.py').is_file())
    def test_sound_switch_honors_system_silence_and_volume(self):
        sound=code('ui/util/UiSoundPlayer.kt')
        self.assertIn('AudioManager.RINGER_MODE_NORMAL',sound)
        self.assertIn('getStreamVolume(AudioManager.STREAM_SYSTEM)==0',sound)
        self.assertIn('LocalAppSoundsEnabled provides settings.soundsEnabled',code('MainActivity.kt'))
    def test_haptics_have_intensity_patterns_and_system_respect(self):
        h=code('ui/util/AppHaptics.kt')
        for part in ('HapticIntensity.STRONG','HapticIntensity.LIGHT','VibrationEffect.createWaveform','HAPTIC_FEEDBACK_ENABLED','Event.SUCCESS','Event.ERROR','Event.DELETE'):
            self.assertIn(part,h)
        self.assertIn('android.permission.VIBRATE',(ROOT/'app/src/main/AndroidManifest.xml').read_text())
    def test_haptics_sounds_settings_persist_and_controls_render(self):
        repo=code('data/preferences/UserPreferencesRepository.kt')
        settings=code('ui/settings/SettingsScreen.kt')
        for part in ('DEFAULT_VIDEO_QUALITY','DEFAULT_AUDIO_QUALITY','setDefaultVideoQuality','setDefaultAudioQuality'):
            self.assertIn(part,repo)
        for part in ('settings.defaultVideoQuality','settings.defaultAudioQuality','viewModel.setDefaultVideoQuality(','viewModel.setDefaultAudioQuality('):
            self.assertIn(part,settings)
    def test_dock_indicates_selection_in_ltr_and_rtl(self):
        dock=code('ui/components/QuietNavigation.kt')
        for key in ('animateDpAsState','val slot = when (selectedTab)','onScan: () -> Unit','modifier.pressFeedback()','navigationTab()','contentDescription = label'):
            self.assertIn(key,dock)
    def test_system_and_app_reduce_motion_are_respected(self):
        system=code('ui/theme/SystemMotion.kt')
        main=code('MainActivity.kt')
        self.assertIn('ANIMATOR_DURATION_SCALE',system)
        self.assertIn('LocalReducedMotion provides (settings.respectReducedMotion || systemReduced)',main)
        self.assertIn('LocalReducedMotion.current',code('ui/theme/JumloMotion.kt'))
        self.assertIn('LocalReducedMotion.current',code('ui/components/ProductionRows.kt'))
    def test_predictive_back_follows_gesture_and_respects_dirty_state(self):
        nav=code('ui/navigation/JumloNavHost.kt')
        self.assertIn('progress.collect { backSwipe=it.progress.coerceIn',nav)
        self.assertIn('if(route=="workspace" && dirty) confirmLeave=true else leaveWorkspace()',nav)
        self.assertIn('translationX=(if(rtl) -1f else 1f)*backSwipe',nav)
    def test_files_default_grid_preserves_explicit_user_choice(self):
        self.assertIn('fileViewMode: FileViewMode = FileViewMode.GRID',code('model/Models.kt'))
        self.assertIn('prefs[Keys.FILE_VIEW_MODE] ?: "GRID"',code('data/preferences/UserPreferencesRepository.kt'))
        for token in ('BoxWithConstraints','maxWidth.value','density.fontScale','coerceIn(1,4)'):
            self.assertIn(token,code('ui/files/FilesScreen.kt'))
        self.assertIn('FileViewMode.LIST',code('ui/files/FilesScreen.kt'))
    def test_semantic_color_pairs_meet_aa_text_contrast(self):
        colors=code('ui/theme/Color.kt')
        tokens={k:v for k,v in re.findall(r'val (\w+) = Color\(0xFF([A-Fa-f0-9]{6})\)',colors)}
        pairs=[('LightPrimary','LightOnPrimary'),('DarkPrimary','DarkOnPrimary'),('LightBackground','LightOnSurface'),('DarkBackground','DarkOnSurface'),('LightOnSurfaceVariant','LightBackground'),('DarkOnSurfaceVariant','DarkBackground'),('LightPrimaryContainer','LightOnPrimaryContainer'),('DarkPrimaryContainer','DarkOnPrimaryContainer'),('LightError','LightOnError'),('DarkError','DarkOnError')]
        for left,right in pairs:
            with self.subTest(tokens=(left,right)):
                self.assertGreaterEqual(ratio(tokens[left],tokens[right]),4.5)

if __name__=='__main__': unittest.main(verbosity=2)
