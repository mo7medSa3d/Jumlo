#!/usr/bin/env python3
"""Verify every processing tool in the registry reaches a real handler.

Reads three sources and cross-checks them, without a device or Gradle:
  * CapabilityRegistry (allTools + sha256) — every tool id
  * CapabilityPolicy — the processing set (canProcess) vs navigation set
  * OperationDispatcher.dispatch — the when-branches that route to handlers

A tool is covered only if it is in the processing set AND appears in a
dispatcher branch. Navigation tools (scan_doc, file_02, file_trash,
dl_settings) must NOT appear in a dispatcher branch — they are routed by
the nav host, and a dispatcher branch for them would be dead code.

Exit code 0 only when: every processing id is dispatched, every
dispatched id is a known processing id, and no navigation id is dispatched.
This is a static wiring gate, not a runtime pick->result test.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


def main():
    registry = read("app/src/main/java/com/jumlo/app/data/jobs/CapabilityRegistry.kt")
    policy = read("core-common/src/main/kotlin/com/jumlo/app/core/CapabilityPolicy.kt")
    dispatcher = read("app/src/main/java/com/jumlo/app/data/jobs/OperationDispatcher.kt")

    registry_ids = set(re.findall(r'UtilityTool\("([a-z0-9_]+)"', registry))
    assert registry_ids, "no tool ids found in registry"

    # CapabilityPolicy declares navigation explicitly; everything else in the
    # registry is a processing tool (canProcess = enabled + PROCESSING kind).
    nav_match = re.search(r'private val navigation = setOf\(([^)]*)\)', policy)
    assert nav_match, "navigation set not found in CapabilityPolicy"
    navigation = set(re.findall(r'"([a-z0-9_]+)"', nav_match.group(1)))
    processing = registry_ids - navigation
    assert processing, "no processing tools found"

    dispatch_body = dispatcher.split("private suspend fun dispatch", 1)[1]
    dispatched = set()
    for branch in re.findall(r'"([a-z0-9_]+)"', dispatch_body):
        dispatched.add(branch)

    errors = []
    missing = sorted(processing - dispatched)
    if missing:
        errors.append(f"processing tools with NO dispatcher branch: {missing}")
    unknown = sorted(dispatched - processing)
    if unknown:
        errors.append(f"dispatcher branches for non-processing ids: {unknown}")
    dead_nav = sorted(navigation & dispatched)
    if dead_nav:
        errors.append(f"navigation tools wrongly dispatched: {dead_nav}")
    nav_undispatched = sorted(navigation - dispatched)
    if not nav_undispatched:
        errors.append("expected at least one navigation-only tool")

    print(f"registry tools: {len(registry_ids)}")
    print(f"processing tools: {len(processing)}")
    print(f"navigation tools: {len(navigation)}")
    print(f"dispatched ids: {len(dispatched)}")
    if errors:
        for error in errors:
            print(f"FAIL: {error}")
        return 1
    print(f"PASS: all {len(processing)} processing tools dispatch to a handler; "
          f"{len(navigation)} navigation tools stay nav-only")
    return 0


if __name__ == "__main__":
    sys.exit(main())
