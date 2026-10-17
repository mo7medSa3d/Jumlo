"""Execute the production transport guard against actual stdlib URL/redirect objects."""
import sys
sys.dont_write_bytecode = True
import importlib.util
import pathlib
import unittest
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('guard', ROOT / 'app/src/main/assets/media/network_guard.py')
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)

class GuardTests(unittest.TestCase):
    def test_https_is_allowed(self):
        self.assertEqual(guard.validate_url('https://example.org/a?q=2'), 'https://example.org/a?q=2')
    def test_plain_http_requires_consent(self):
        with self.assertRaises(ValueError): guard.validate_url('http://example.org/a')
        self.assertEqual(guard.validate_url('http://example.org/a', True), 'http://example.org/a')
    def test_non_network_schemes_are_rejected(self):
        for url in ('file:///etc/passwd', 'data:text/plain,abc', 'ftp://example.org/a', 'javascript:alert(1)'):
            with self.subTest(url=url), self.assertRaises(ValueError): guard.validate_url(url, True)
    def test_credentials_and_controls_are_rejected(self):
        for url in ('https://a:b@example.org/a', 'https://example.org/\n', 'https://example.org:bad/a'):
            with self.subTest(url=url), self.assertRaises(ValueError): guard.validate_url(url)
    def test_downgrade_redirect_is_rejected_even_with_consent(self):
        req = urllib.request.Request('https://example.org/a')
        with self.assertRaises(ValueError): guard.redirect(req, 'http://example.org/a', True)
    def test_redirect_strips_cross_origin_credentials(self):
        req = urllib.request.Request('https://a.example/a', headers={'Authorization':'secret', 'Cookie':'secret', 'Referer':'https://a.example/private?token=x', 'X-Api-Key':'secret', 'Accept':'*/*'})
        redirected = guard.redirect(req, 'https://b.example/b', False)
        self.assertEqual(dict(redirected.header_items()), {'Accept':'*/*'})
    def test_same_origin_preserves_authorization_but_not_cookie(self):
        req = urllib.request.Request('https://a.example/a', headers={'Authorization':'secret', 'Cookie':'secret'})
        redirected = guard.redirect(req, 'https://a.example/b', False)
        self.assertEqual(dict(redirected.header_items()), {'Authorization':'secret'})
    def test_redirect_port_counts_as_origin(self):
        req = urllib.request.Request('https://a.example/a', headers={'Authorization':'secret'})
        self.assertNotIn('Authorization', dict(guard.redirect(req, 'https://a.example:8443/b', False).header_items()))

if __name__ == '__main__': unittest.main(verbosity=2)
