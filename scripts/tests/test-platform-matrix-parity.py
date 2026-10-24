#!/usr/bin/env python3
"""Cross-check the live-qualification matrix against the real routing policy.

This is not a network test and not a claim that any platform extracts. It proves
the two sources of truth agree: every platform the app routes
(``DownloadLinkPolicy``) has a qualification row, and every row's badge and
representative hosts match the classifier. A route added to the app without a
matrix row, or a matrix row for a route the app never dispatches, fails here.
"""
import importlib.util
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ROUTING = ROOT / 'downloader-core/src/main/kotlin/com/jumlo/app/core/DownloadLinkPolicy.kt'
RUNNER = ROOT / 'scripts/qualify-live-media.py'


def _load_runner():
    spec = importlib.util.spec_from_file_location('live_media_qualification', RUNNER)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _extractor_domains(source):
    block = re.search(r'extractorDomains\s*=\s*setOf\((.*?)\)\s*\n', source, re.S)
    assert block, 'extractorDomains set not found'
    return set(re.findall(r'"([^"]+)"', block.group(1)))


def _badge_map(source):
    # matches("a", "b") -> "Badge"
    return {
        host.lower(): badge
        for hosts, badge in re.findall(r'matches\(([^)]*)\)\s*->\s*"([^"]+)"', source)
        for host in re.findall(r'"([^"]+)"', hosts)
    }


class PlatformMatrixParityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.runner = _load_runner()
        cls.source = ROUTING.read_text(encoding='utf-8')
        cls.domains = _extractor_domains(cls.source)
        cls.badges = _badge_map(cls.source)

    def test_matrix_is_superset_of_routed_platforms(self):
        covered = set()
        for _, hosts in self.runner.PLATFORM_META.values():
            covered.update(hosts)
        for domain in self.domains:
            # A base domain or any subdomain of a declared representative host.
            if not any(domain == host or domain.endswith('.' + host) for host in covered):
                self.fail(f'route {domain!r} has no qualification row in PLATFORM_META')

    def test_no_matrix_row_points_at_an_unrouted_host(self):
        for key, (_, hosts) in self.runner.PLATFORM_META.items():
            if key == 'direct':
                self.assertEqual(hosts, ())
                continue
            for host in hosts:
                self.assertIn(host, self.domains, f'{key} declares unrouted host {host!r}')

    def test_every_route_resolves_to_its_declared_badge(self):
        for key, (badge, hosts) in self.runner.PLATFORM_META.items():
            if key == 'direct':
                continue
            for host in hosts:
                self.assertIn(host.lower(), self.badges, f'{host} has no badge mapping')
                self.assertEqual(badge, self.badges[host.lower()],
                                 f'{key} badge {badge!r} != routing badge for {host}')

    def test_direct_fallback_row_is_present(self):
        self.assertIn('direct', self.runner.PLATFORM_META)
        self.assertIn('direct', self.runner.PLATFORMS)


if __name__ == '__main__':
    unittest.main(verbosity=2)
