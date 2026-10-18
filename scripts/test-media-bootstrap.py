#!/usr/bin/env python3
"""Offline static integration checks for the current native media transport.

Legacy network_guard.py and embedded Python runtime were intentionally removed
from the production APK. Actual URL policy and transfer logic are exercised by
the Kotlin host tests in test-core.sh, which verify redirects, ranges, ETags,
checksum behavior, HTTPS and error handling.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main/java/com/jumlo/app'
BASE = ROOT / 'app/src/main/java/com/jumlo/app'

class CurrentNativeTransportContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.runtime=(BASE/'data/media/NativeDirectMediaRuntime.kt').read_text()
        cls.view_model=(MAIN/'ui/downloads/MediaViewModel.kt').read_text()
        cls.handler=(MAIN/'data/media/MediaOperationHandler.kt').read_text()
        cls.html=(MAIN/'core/media/Html5MediaDiscovery.kt').read_text()

    def test_removed_python_runtime_is_not_a_startup_dependency(self):
        self.assertFalse((ROOT/'app/src/main/assets/media/network_guard.py').exists())
        self.assertFalse((ROOT/'app/src/main/assets/media/bootstrap.py').exists())
        self.assertIn('NativeDirectMediaRuntime',self.runtime)
        base_factory=(ROOT/'app/src/base/java/com/jumlo/app/data/media/MediaRuntimeFactory.kt').read_text()
        self.assertIn('NativeDirectMediaRuntime',base_factory)
        self.assertIn('StreamTransfer(',self.runtime)

    def test_runtime_checks_every_redirect_and_prevents_https_downgrade(self):
        self.assertIn('HttpPolicy.url(current.resolve(location).toString(), allowHttp)',self.runtime)
        self.assertIn('current.scheme == "https" && next.scheme != "https"',self.runtime)
        self.assertIn('followRedirects(false)',self.runtime)
        self.assertIn('followSslRedirects(false)',self.runtime)

    def test_auth_headers_are_derived_from_each_destination_origin(self):
        self.assertIn('headers(current.toString(), profileIndex)',self.runtime)
        self.assertIn('sessions.cookieHeaderFor(url)',self.runtime)
        self.assertIn('PlatformHeaderPolicy.headersFor(',self.runtime)

    def test_static_html_discovery_is_bounded_and_passive(self):
        self.assertIn('Html5MediaDiscovery.discover',self.runtime)
        self.assertIn('html.length <= 262144',self.html)
        self.assertIn('take(256)',self.html)
        self.assertIn('uri.scheme != "https"',self.html)
        self.assertNotIn('Runtime.getRuntime()',self.runtime)

    def test_browser_profiles_are_applied_automatically_at_runtime(self):
        self.assertIn('BrowserProfilePolicy.tryNext(', self.runtime)
        self.assertIn('compatibilityAttempt = profileIndex', self.runtime)
        self.assertIn('Html5MediaDiscovery.thumbnail', self.runtime)

    def test_public_html_without_verified_media_can_select_alternate_profile(self):
        self.assertIn('BrowserProfilePolicy.tryAlternatePage(', self.runtime)
        self.assertIn('preferredProfileIndex', self.runtime)
        self.assertIn('currentPage.profileIndex', self.runtime)

    def test_media_errors_no_longer_direct_users_to_a_removed_login_screen(self):
        import xml.etree.ElementTree as ET
        for language in ('values', 'values-ar'):
            path=ROOT/'app/src/main/res'/language/'media_strings.xml'
            values={element.attrib['name']:element.text or '' for element in ET.parse(path).getroot()}
            for code in ('media_error_private', 'media_error_session', 'media_error_rate'):
                msg=values[code]
                self.assertNotIn('Platform Login', msg)
                self.assertNotIn('تسجيل دخول المنصات', msg)
            for obsolete in ('media_session_btn','media_tab_webview_login','media_cookies_save_btn'):
                self.assertNotIn(obsolete, values)

    def test_download_workspace_does_not_expose_cookie_browser_or_header_editor(self):
        ui=(MAIN/'ui/downloads/MediaWorkspace.kt').read_text()
        self.assertNotIn('PlatformSessionSheet(',ui)
        self.assertNotIn('showSessionSheet',ui)
        self.assertNotIn('media_session_btn',ui)
        self.assertFalse((MAIN/'ui/downloads/PlatformSessionSheet.kt').exists())
        self.assertIn('media_restricted_hint',ui)

    def test_actual_transfer_uses_compatible_profile_and_stops_on_denial(self):
        downloader=(MAIN/'data/processing/DownloadOperationHandler.kt').read_text()
        self.assertIn('BrowserProfilePolicy.tryNext(',downloader)
        self.assertIn('response.close()',downloader)
        self.assertIn('compatibilityAttempt = profileIndex',downloader)
        self.assertIn('BrowserProfilePolicy.tryNext(',self.view_model)

    def test_transfer_never_publishes_partial_downloads(self):
        self.assertIn('native-media.part',self.runtime)
        self.assertIn('StandardCopyOption.ATOMIC_MOVE',self.runtime)
        self.assertIn('MediaOutputPolicy.inspect(target)',self.runtime)
        self.assertIn('target.delete()',self.runtime)
        self.assertIn('if (completed) directory.deleteRecursively()',self.handler)

    def test_media_choice_must_match_real_reanalysis(self):
        self.assertIn('MediaCatalog.fingerprint(choice)',self.view_model)
        self.assertIn('MEDIA_REANALYSIS_REQUIRED',self.runtime)
        self.assertIn('source.extension != choice.formats.single().extension',self.runtime)

if __name__ == '__main__': unittest.main(verbosity=2)
