#!/usr/bin/env python3
import hashlib
import json
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from preflight import inspect, classify_hours, ROOT


class PreflightTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / 'legacy.db'
        with sqlite3.connect(self.path) as db:
            db.executescript((ROOT / 'docs/legacy-room-schema.sql').read_text())
            db.execute('PRAGMA user_version=2')
            db.execute("INSERT INTO projects VALUES ('p','test',0)")
            db.execute("INSERT INTO users VALUES ('p','u','name','role','master',1,1,1,0,NULL)")
            db.execute("INSERT INTO wages VALUES ('p','w','u',101,'1403/01/01')")
            db.execute("INSERT INTO attendance VALUES ('p','1403/01/01','u',?)", ('{"t":"h","h":1.2500}',))
    def tearDown(self):
        self.temp.cleanup()
    def test_valid_read_only(self):
        before = hashlib.sha256(self.path.read_bytes()).digest()
        report = inspect(self.path)
        self.assertFalse(report['hasIssues'], report)
        self.assertEqual(report['attendanceValues'], {'legacy_non_half_hour': 1})
        self.assertEqual(report['rowCounts']['users'], 1)
        self.assertEqual(before, hashlib.sha256(self.path.read_bytes()).digest())
    def test_find_failures_without_dropping_rows(self):
        with sqlite3.connect(self.path) as db:
            db.execute("INSERT INTO wages VALUES ('p','bad','u',100.25,'1402/12/30')")
            db.execute("INSERT INTO attendance VALUES ('p','1403/01/02','u',?)", ('{"t":"h","h":24.5}',))
            db.execute("INSERT INTO receipts VALUES ('p','r','1403/01/05','1403/01/01',-1,'',1,0,0)")
        report = inspect(self.path)
        self.assertEqual(report['rowCounts']['wages'], 2)
        for key in ('wages.rate.fractional', 'wages.effectiveDate.invalid_or_unsupported',
                    'attendance.invalid_value', 'receipts.inverted_interval', 'receipts.amount.out_of_range'):
            self.assertEqual(report['issues'].get(key), 1, key)
        proc = subprocess.run([sys.executable, str(ROOT / 'parity/migration/preflight.py'), str(self.path)], capture_output=True, text=True)
        self.assertEqual(proc.returncode, 1, proc.stderr)
        self.assertTrue(json.loads(proc.stdout)['hasIssues'])
    def test_wrong_schema_and_version(self):
        with sqlite3.connect(self.path) as db:
            db.execute('DROP TABLE wages')
            db.execute('PRAGMA user_version=1')
        r = inspect(self.path)
        self.assertEqual(r['issues']['wages.missing_table'], 1)
        self.assertEqual(r['issues']['unexpected_database_version'], 1)
    def test_reads_committed_wal(self):
        with sqlite3.connect(self.path) as writer:
            self.assertEqual(writer.execute('PRAGMA journal_mode=WAL').fetchone()[0], 'wal')
            writer.execute("INSERT INTO attendance VALUES ('p','1403/01/02','u',?)", ('{"t":"h","h":1.25}',))
            writer.commit()
            self.assertTrue(Path(str(self.path) + '-wal').exists())
            self.assertEqual(inspect(self.path)['rowCounts']['attendance'], 2)
    def test_attendance_classification(self):
        for raw in ['0', '0.0', '0.50', '1', '1.0', '"0"', '"0.5"', '"1"']:
            self.assertEqual(classify_hours(raw), 'mark')
        for raw in ['{"t":"h","h":0}', '{"t":"h","h":24}', '{"t":"h","h":1.5}']:
            self.assertEqual(classify_hours(raw), 'half_hour')
        for raw in ['{"t":"h","h":1.25}', '{"t":"h","h":"1.2500"}']:
            self.assertEqual(classify_hours(raw), 'legacy_non_half_hour')
        for raw in ['true', 'null', '"2"', '"1.0"', '{', '{"t":"h","h":25}', '{"t":"h","h":NaN}', '{"t":"h","h":true}']:
            self.assertEqual(classify_hours(raw), 'invalid')

if __name__ == '__main__':
    unittest.main()
