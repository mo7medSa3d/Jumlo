#!/usr/bin/env python3
"""Tests the actual packaged Python network policy with no internet access."""
import importlib.util
from pathlib import Path
import unittest
path=Path(__file__).resolve().parents[1]/'app/src/media/assets/media/network_guard.py'
spec=importlib.util.spec_from_file_location('jumlo_network_guard',path)
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class GuardTest(unittest.TestCase):
    def test_public_https(self):
        self.assertEqual(module.validate_url('https://media.example.org/file'), 'https://media.example.org/file')
    def test_http_requires_explicit_consent(self):
        with self.assertRaisesRegex(ValueError,'MEDIA_HTTP_CONSENT_REQUIRED'):
            module.validate_url('http://media.example.org/file')
        self.assertEqual(module.validate_url('http://media.example.org/file',True),'http://media.example.org/file')
    def test_private_network_literals_and_hostnames(self):
        for url in ('https://localhost/a','https://localhost./a','https://x.local/a',
                    'http://127.0.0.1/a','http://127.1/a','http://[::1]/a',
                    'http://192.168.1.1/a','http://169.254.169.254/a','http://10.0.0.1/a'):
            with self.subTest(url=url), self.assertRaisesRegex(ValueError,'MEDIA_PRIVATE_NETWORK_URL'):
                module.validate_url(url,True)
    def test_credentials_illegal_schemes_and_invalid_ports(self):
        for url in ('file:///tmp/a','https://user:pw@example.org/a','ftp://example.org/a',
                    'https://example.org:99999/a','https://example.org:0/a',
                    'https://example.org:\\x00/a'):
            with self.subTest(url=url), self.assertRaises(ValueError):
                module.validate_url(url,True)

if __name__=='__main__': unittest.main(verbosity=2)
