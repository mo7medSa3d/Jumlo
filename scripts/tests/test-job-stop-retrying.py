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
            conn.execute('INSERT INTO jobs VALUES (?,?,?,?,?,?,?)',('job','{}','{}',stage,owner,0,0))
            self.assertEqual(0,conn.execute(q('cancelIfOpen'),{'id':'job','snapshot':'cancel'}).rowcount)
            self.assertEqual(expected,conn.execute(q('stopRetryingIfUnleased'),{'id':'job','snapshot':'stopped'}).rowcount)
            expectedStage='CANCELLED' if expected else stage
            self.assertEqual(expectedStage,conn.execute('SELECT stage FROM jobs').fetchone()[0])
        conn.close()
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
