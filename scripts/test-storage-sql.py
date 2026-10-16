#!/usr/bin/env python3
"""Execute the actual migration/DAO SQL with SQLite. This is NOT Room/device qualification."""
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = ROOT / 'app/src/main/java/com/jumlo/app/data/local'
DAO = (PACKAGE / 'ProcessingEntities.kt').read_text()
MIGRATIONS = re.findall(r'db\.execSQL\("([^"\n]+)"\)', (PACKAGE / 'DatabaseMigrations.kt').read_text())

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
        self.assertEqual(0,self.run_query('retry',id='job',snapshot='queued'))
        self.run_query('release',id='job',owner='old')
        self.assertEqual(1,self.run_query('retry',id='job',snapshot='queued'))
    def test_output_logical_identity_is_unique(self):
        self.db.execute('INSERT INTO outputs VALUES (?, ?, ?, ?)',('one','job','item','a'))
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute('INSERT INTO outputs VALUES (?, ?, ?, ?)',('two','job','item','b'))
    def test_source_uri_can_have_distinct_immutable_snapshot_ids(self):
        for key in ('old','new'):
            self.db.execute('INSERT INTO sources VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',(key,'content://same','input',None,None,1,0,0,None,key))
        self.assertEqual(2,self.db.execute('SELECT COUNT(*) FROM sources').fetchone()[0])
    def test_worker_start_failure_never_overwrites_cancelled_state(self):
        self.job(stage='CANCELLED')
        self.assertEqual(0,self.run_query('failBeforeClaim',id='job',snapshot='failed'))
    def test_migration_sql_retains_preexisting_table_rows(self):
        self.db.execute('CREATE TABLE managed_files(id TEXT PRIMARY KEY,name TEXT)')
        self.db.execute("INSERT INTO managed_files VALUES ('legacy','original')")
        for statement in MIGRATIONS:
            self.db.execute(statement)
        self.assertEqual([('legacy','original')],self.db.execute('SELECT * FROM managed_files').fetchall())

if __name__ == '__main__':
    unittest.main(verbosity=2)
