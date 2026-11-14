#!/usr/bin/env python3
"""Exercise APK-extracted 1→2 SQL on synthetic SQLite fixtures, not Android Room."""
import hashlib
import sqlite3
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
APK=ROOT/'Stayar-v1_16.apk'
SQL=ROOT/'docs/legacy-room-migration-1-2.sql'

def statements(path):
    return [line.strip().removesuffix(';') for line in path.read_text(encoding='utf8').splitlines()
            if line.strip() and not line.lstrip().startswith('--')]

class MigrationTest(unittest.TestCase):
    def setUp(self):
        self.db=sqlite3.connect(':memory:',isolation_level=None)
        self.migration=statements(SQL)
    def tearDown(self): self.db.close()
    def run_migration(self):
        # Simulate transactional execution and version update; the extracted migrate() does
        # not issue this PRAGMA itself. Real Room validation and version handling are separate.
        self.db.execute('BEGIN')
        try:
            for statement in self.migration: self.db.execute(statement)
            self.db.execute('PRAGMA user_version=2')
            self.db.execute('COMMIT')
        except BaseException:
            self.db.execute('ROLLBACK')
            raise
    def test_provenance_and_reference_schema(self):
        self.assertIn(hashlib.sha256(APK.read_bytes()).hexdigest(),SQL.read_text())
        self.assertEqual(len(self.migration),20)
        reference=statements(ROOT/'docs/legacy-room-schema.sql')
        without_room=[s for s in reference if 'room_master_table' not in s]
        self.assertEqual(set(self.migration),set(without_room))
        self.assertTrue(all('IF NOT EXISTS' in s for s in self.migration))
    def test_preexisting_compatible_rows_and_unknown_table_are_preserved(self):
        self.db.execute(self.migration[0])
        self.db.execute("INSERT INTO projects VALUES ('p','کارگاه',123)")
        self.db.execute('CREATE TABLE unknown_old_table (payload TEXT)')
        self.db.execute("INSERT INTO unknown_old_table VALUES ('legacy')")
        self.db.execute('PRAGMA user_version=1')
        self.run_migration()
        self.assertEqual(self.db.execute('SELECT * FROM projects').fetchall(),[('p','کارگاه',123)])
        self.assertEqual(self.db.execute('SELECT * FROM unknown_old_table').fetchall(),[('legacy',)])
        self.assertEqual(self.db.execute('PRAGMA user_version').fetchone()[0],2)
        self.run_migration()  # only validates SQL IF NOT EXISTS idempotence, not Room behavior
        self.assertEqual(self.db.execute('SELECT COUNT(*) FROM projects').fetchone()[0],1)
    def test_conflicting_schema_rolls_back_synthetic_transaction(self):
        self.db.execute('CREATE TABLE wages (projectId TEXT NOT NULL,id TEXT NOT NULL,PRIMARY KEY(projectId,id))')
        self.db.execute("INSERT INTO wages VALUES ('p','w')")
        self.db.execute('PRAGMA user_version=1')
        with self.assertRaises(sqlite3.OperationalError): self.run_migration()
        self.assertEqual(self.db.execute('PRAGMA user_version').fetchone()[0],1)
        self.assertEqual(self.db.execute('SELECT * FROM wages').fetchall(),[('p','w')])
        self.assertIsNone(self.db.execute("SELECT name FROM sqlite_master WHERE name='projects'").fetchone())
    def test_if_not_exists_does_not_repair_a_wrong_existing_index(self):
        for s in self.migration: self.db.execute(s)
        self.db.execute('DROP INDEX index_wages_projectId_userId')
        self.db.execute('CREATE INDEX index_wages_projectId_userId ON wages(projectId)')
        self.run_migration()
        cols=[row[2] for row in self.db.execute('PRAGMA index_info(index_wages_projectId_userId)')]
        self.assertEqual(cols,['projectId'])
        # A real Room schema validation is necessary to detect the incompatible index.

if __name__=='__main__': unittest.main()
