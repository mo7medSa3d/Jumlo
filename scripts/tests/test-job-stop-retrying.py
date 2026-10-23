from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
class StopRetryingContracts(unittest.TestCase):
    def test_sql_atomically_cancels_failed_retryable_jobs(self):
        source=(ROOT/'app/src/main/java/com/jumlo/app/data/local/ProcessingEntities.kt').read_text()
        statement=source.split('suspend fun stopRetryingIfUnleased')[0].split('@Query(')[-1]
        for status in ('FAILED','PARTIAL','WAITING_FOR_INPUT'):
            self.assertIn("'"+status+"'",statement)
        self.assertIn('leaseOwner IS NULL',statement)
        self.assertNotIn("'COMPLETED'",statement)
    def test_actual_sql_preserves_legacy_cancel_and_requires_unleased_retry_state(self):
        import sqlite3,re
        dao=(ROOT/'app/src/main/java/com/jumlo/app/data/local/ProcessingEntities.kt').read_text()
        migration=(ROOT/'app/src/main/java/com/jumlo/app/data/local/DatabaseMigrations.kt').read_text()
        q=lambda method: re.search(r'@Query\("([^"\n]+)"\)\s*suspend\s+fun\s+'+method+r'\(',dao).group(1)
        conn=sqlite3.connect(':memory:')
        for statement in re.findall(r'db\.execSQL\("([^"\n]+)"\)',migration):conn.execute(statement)
        for stage,owner,expected in [('FAILED',None,1),('PARTIAL',None,1),('WAITING_FOR_INPUT',None,1),
                                      ('FAILED','worker',0),('COMPLETED',None,0),('CANCELLED',None,0)]:
            conn.execute('DELETE FROM jobs')
            conn.execute('INSERT INTO jobs VALUES (?,?,?,?,?,?,?)',('job','{}','{}',stage,owner,3000 if owner else 0,0))
            self.assertEqual(0,conn.execute(q('cancelIfOpen'),{'id':'job','snapshot':'cancel'}).rowcount)
            self.assertEqual(expected,conn.execute(q('stopRetryingIfUnleased'),{'id':'job','snapshot':'stopped','now':1000}).rowcount)
            expectedStage='CANCELLED' if expected else stage
            self.assertEqual(expectedStage,conn.execute('SELECT stage FROM jobs').fetchone()[0])
        conn.close()
    def lease_database(self):
        import sqlite3,re
        dao=(ROOT/'app/src/main/java/com/jumlo/app/data/local/ProcessingEntities.kt').read_text()
        migration=(ROOT/'app/src/main/java/com/jumlo/app/data/local/DatabaseMigrations.kt').read_text()
        query=lambda method: re.search(r'@Query\("([^"\n]+)"\)\s*suspend\s+fun\s+'+method+r'\(',dao).group(1)
        conn=sqlite3.connect(':memory:')
        self.addCleanup(conn.close)
        for statement in re.findall(r'db\.execSQL\("([^"\n]+)"\)',migration):conn.execute(statement)
        return conn,query
    def test_expired_retryable_lease_is_reclaimed_without_losing_snapshot_and_old_owner_is_fenced(self):
        conn,q=self.lease_database()
        for stage in ('FAILED','PARTIAL','INTERRUPTED','PAUSED','WAITING_FOR_INPUT'):
            with self.subTest(stage=stage):
                conn.execute('DELETE FROM jobs')
                conn.execute('INSERT INTO jobs VALUES (?,?,?,?,?,?,?)',('job','request','prior',stage,'dead-worker',999,7))
                snapshot='{"completedUnits":8,"outputs":["retained"],"downloadRestartGeneration":4}'
                self.assertEqual(1,conn.execute(q('retry'),{'id':'job','snapshot':snapshot,'now':1000}).rowcount)
                self.assertEqual(('QUEUED',None,0,snapshot,7),conn.execute('SELECT stage,leaseOwner,leaseUntil,snapshotJson,generation FROM jobs').fetchone())
                self.assertEqual(0,conn.execute(q('updateOwned'),{'id':'job','owner':'dead-worker','snapshot':'stale','stage':'RUNNING'}).rowcount)
                self.assertEqual(1,conn.execute(q('claim'),{'id':'job','owner':'new-worker','now':1000,'until':60000}).rowcount)
                conn.execute(q('release'),{'id':'job','owner':'dead-worker'})
                self.assertEqual(('new-worker',snapshot,8),conn.execute('SELECT leaseOwner,snapshotJson,generation FROM jobs').fetchone())
    def test_active_lease_and_nonretryable_stages_cannot_be_reclaimed(self):
        conn,q=self.lease_database()
        for stage,until in [('PAUSED',2000),('WAITING_FOR_INPUT',2000),('FAILED',2000),('PARTIAL',2000),
                            ('RUNNING',999),('COMPLETED',999),('CANCELLED',999)]:
            with self.subTest(stage=stage,until=until):
                conn.execute('DELETE FROM jobs')
                conn.execute('INSERT INTO jobs VALUES (?,?,?,?,?,?,?)',('job','request','prior',stage,'worker',until,7))
                self.assertEqual(0,conn.execute(q('retry'),{'id':'job','snapshot':'retry','now':1000}).rowcount)
                self.assertEqual(0,conn.execute(q('stopRetryingIfUnleased'),{'id':'job','snapshot':'stop','now':1000}).rowcount)
                self.assertEqual((stage,'worker',until,'prior',7),conn.execute('SELECT stage,leaseOwner,leaseUntil,snapshotJson,generation FROM jobs').fetchone())
    def test_expired_terminal_lease_can_be_abandoned_without_leaving_a_stale_owner(self):
        conn,q=self.lease_database()
        for stage in ('FAILED','PARTIAL','WAITING_FOR_INPUT'):
            with self.subTest(stage=stage):
                conn.execute('DELETE FROM jobs')
                conn.execute('INSERT INTO jobs VALUES (?,?,?,?,?,?,?)',('job','request','prior',stage,'dead-worker',999,7))
                self.assertEqual(1,conn.execute(q('stopRetryingIfUnleased'),{'id':'job','snapshot':'cancelled','now':1000}).rowcount)
                self.assertEqual(('CANCELLED',None,0,'cancelled'),conn.execute('SELECT stage,leaseOwner,leaseUntil,snapshotJson FROM jobs').fetchone())
    def test_failure_rows_offer_stop_and_recover_with_accessible_layout(self):
        source=(ROOT/'app/src/main/java/com/jumlo/app/ui/components/ProductionRows.kt').read_text()
        self.assertIn('job_stop_retrying',source)
        self.assertIn('FlowRow(',source)
        self.assertIn('onCancel()',source)
    def test_localizations(self):
        import xml.etree.ElementTree as ET
        for sub in ('values','values-ar'):
            names={x.get('name') for x in ET.parse(ROOT/'app/src/main/res'/sub/'reconstruction.xml').getroot()}
            self.assertIn('job_stop_retrying', names)
if __name__=='__main__':unittest.main()
