#!/usr/bin/env python3
"""Execute the actual migration/DAO SQL with SQLite. This is NOT Room/device qualification."""
from pathlib import Path
import json
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = ROOT / 'app/src/main/java/com/jumlo/app/data/local'
DAO = (PACKAGE / 'ProcessingEntities.kt').read_text(encoding="utf-8")
MIGRATIONS = re.findall(r'db\.execSQL\("([^"\n]+)"\)', (PACKAGE / 'DatabaseMigrations.kt').read_text(encoding="utf-8"))
SCHEMA_DIR = ROOT / 'app/schemas/com.jumlo.app.data.local.AppDatabase'

def query(name: str) -> str:
    found = re.search(r'@Query\("([^"\n]+)"\)\s*suspend\s+fun\s+' + re.escape(name) + r'\(', DAO)
    if found is None:
        raise AssertionError(f'Production query missing: {name}')
    return found.group(1)

class ActualSqlTests(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        for statement in MIGRATIONS:
            self.db.execute(statement)
    def tearDown(self):
        self.db.close()
    def job(self, stage='QUEUED', owner=None, until=0):
        self.db.execute('INSERT INTO jobs VALUES (?, ?, ?, ?, ?, ?, ?)', ('job','request','snapshot',stage,owner,until,0))
    def run_query(self, name, **args):
        return self.db.execute(query(name),args).rowcount
    def test_claim_does_not_steal_a_live_lease(self):
        self.job()
        self.assertEqual(1,self.run_query('claim',id='job',owner='one',now=10,until=100))
        self.assertEqual(0,self.run_query('claim',id='job',owner='two',now=20,until=110))
    def test_old_owner_cannot_write_after_lease_takeover(self):
        self.job(owner='old',until=9)
        self.assertEqual(1,self.run_query('claim',id='job',owner='new',now=10,until=100))
        self.assertEqual(0,self.run_query('updateOwned',id='job',owner='old',snapshot='wrong',stage='COMPLETED'))
    def test_cancelled_job_cannot_claim_or_be_marked_complete(self):
        self.job(stage='CANCELLED',owner='old',until=9)
        self.assertEqual(0,self.run_query('claim',id='job',owner='new',now=10,until=100))
        self.assertEqual(0,self.run_query('updateOwned',id='job',owner='old',snapshot='wrong',stage='COMPLETED'))
    def test_retry_waits_for_prior_worker_cleanup(self):
        self.job(stage='INTERRUPTED',owner='old',until=100)
        self.assertEqual(0,self.run_query('retry',id='job',snapshot='queued',now=10))
        self.run_query('release',id='job',owner='old')
        self.assertEqual(1,self.run_query('retry',id='job',snapshot='queued',now=11))
    def test_retry_reclaims_expired_lease_only_in_retryable_stages(self):
        for stage,expected in [('INTERRUPTED',1),('PAUSED',1),('WAITING_FOR_INPUT',1),('FAILED',1),('PARTIAL',1),
                               ('RUNNING',0),('FINALIZING',0),('COMPLETED',0),('CANCELLED',0)]:
            with self.subTest(stage=stage):
                self.job(stage=stage,owner='old',until=100)
                self.assertEqual(0,self.run_query('retry',id='job',snapshot='too_early',now=100))
                self.assertEqual(expected,self.run_query('retry',id='job',snapshot='queued',now=101))
                row=self.db.execute('SELECT stage,leaseOwner,leaseUntil,snapshotJson FROM jobs WHERE id=?',('job',)).fetchone()
                self.assertEqual(('QUEUED',None,0,'queued') if expected else (stage,'old',100,'snapshot'),row)
                self.db.execute('DELETE FROM jobs')
    def test_pause_is_atomic_and_cannot_overwrite_finalizing_or_terminal_results(self):
        for stage, expected in [('QUEUED',1),('VALIDATING',1),('RUNNING',1),('FINALIZING',0),('COMPLETED',0),('CANCELLED',0)]:
            self.job(stage=stage)
            self.assertEqual(expected,self.run_query('pauseIfActive',id='job',snapshot='paused'),stage)
            row=self.db.execute('SELECT stage FROM jobs WHERE id=?',('job',)).fetchone()
            self.assertEqual('PAUSED' if expected else stage,row[0])
            self.db.execute('DELETE FROM jobs')
    def test_stale_cancel_cannot_overwrite_completed_or_failed_results(self):
        for stage, expected in [('QUEUED',1),('RUNNING',1),('FINALIZING',1),('PAUSED',1),('FAILED',0),('PARTIAL',0),('COMPLETED',0),('CANCELLED',0)]:
            self.job(stage=stage)
            self.assertEqual(expected,self.run_query('cancelIfOpen',id='job',snapshot='cancelled'),stage)
            row=self.db.execute('SELECT stage FROM jobs WHERE id=?',('job',)).fetchone()
            self.assertEqual('CANCELLED' if expected else stage,row[0])
            self.db.execute('DELETE FROM jobs')
    def test_output_logical_identity_is_unique(self):
        self.db.execute('INSERT INTO outputs VALUES (?, ?, ?, ?)',('one','job','item','a'))
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute('INSERT INTO outputs VALUES (?, ?, ?, ?)',('two','job','item','b'))
    def test_output_lookup_and_trash_delete_queries(self):
        self.db.execute('INSERT INTO outputs VALUES (?, ?, ?, ?)',('out1','job','item','{"id":"out1"}'))
        row = self.db.execute(query('outputById'), {'id': 'out1'}).fetchone()
        self.assertIsNotNone(row)
        self.assertEqual('out1', row[0])
        self.assertEqual(1, self.run_query('deleteOutput', id='out1'))
        self.assertIsNone(self.db.execute(query('outputById'), {'id': 'out1'}).fetchone())
    def test_source_uri_can_have_distinct_immutable_snapshot_ids(self):
        for key in ('old','new'):
            self.db.execute('INSERT INTO sources VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',(key,'content://same','input',None,None,1,0,0,None,key))
        self.assertEqual(2,self.db.execute('SELECT COUNT(*) FROM sources').fetchone()[0])
    def test_worker_start_failure_never_overwrites_cancelled_state(self):
        self.job(stage='CANCELLED')
        self.assertEqual(0,self.run_query('failBeforeClaim',id='job',snapshot='failed'))
    def test_legacy_download_url_scrub_removes_signed_queries_without_losing_progress(self):
        self.db.execute("CREATE TABLE download_tasks (id TEXT PRIMARY KEY, fullUrl TEXT, downloadedBytes INTEGER)")
        self.db.execute("INSERT INTO download_tasks VALUES ('signed', 'https://file.test?a=signed_secret', 42)")
        callback = (ROOT / 'app/src/main/java/com/jumlo/app/data/local/LegacyDownloadPrivacyCallback.kt').read_text(encoding="utf-8")
        match = re.search(r'REDACT_LEGACY_URLS\s*=\s*"([^"]+)"', callback)
        self.assertIsNotNone(match)
        sql = match.group(1)
        self.db.execute(sql)
        self.assertEqual([('signed','',42)], self.db.execute('SELECT id, fullUrl, downloadedBytes FROM download_tasks').fetchall())

    def test_migration_sql_creates_tables_matching_current_schema(self):
        s2 = json.loads((SCHEMA_DIR / '2.json').read_text(encoding="utf-8"))
        # The v1 -> v2 migration adds these tables; the two legacy tables are from v1.
        migrated = {'sources', 'jobs', 'outputs', 'drafts'}
        tables = {row[0] for row in self.db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        self.assertEqual(migrated, tables)
        for entity in s2['database']['entities']:
            if entity['tableName'] not in migrated:
                continue
            cols = [r[1] for r in self.db.execute(f"PRAGMA table_info(`{entity['tableName']}`)").fetchall()]
            expected_cols = [f['columnName'] for f in entity['fields']]
            self.assertEqual(expected_cols, cols, entity['tableName'])

if __name__ == '__main__':
    unittest.main(verbosity=2)
