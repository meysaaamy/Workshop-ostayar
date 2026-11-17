#!/usr/bin/env python3
"""Static compatibility checks, NOT generated Room validation or Android execution."""
import re
import sqlite3
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
entities=(ROOT/'data/src/main/kotlin/com/workshop/ledger/data/legacy/LegacyEntities.kt').read_text()
class WiringTest(unittest.TestCase):
    def test_1_to_2_sql_matches_apk_extraction(self):
        source=(ROOT/'data/src/main/kotlin/com/workshop/ledger/data/migration/LegacyRoomMigration1To2.kt').read_text()
        actual=re.findall(r'^\s*"(CREATE .*?)"[,]?$',source,re.M)
        expected=[s.strip().rstrip(';') for s in (ROOT/'docs/legacy-room-migration-1-2.sql').read_text().splitlines() if s.startswith('CREATE ')]
        self.assertEqual(actual,expected)
        self.assertEqual((ROOT/'data/src/androidTest/assets/legacy-v2-schema.sql').read_bytes(),(ROOT/'docs/legacy-room-migration-1-2.sql').read_bytes())
    def test_entity_metadata_matches_simulated_sqlite_v3(self):
        db=sqlite3.connect(':memory:')
        self.addCleanup(db.close)
        db.executescript((ROOT/'docs/legacy-room-migration-1-2.sql').read_text())
        source=(ROOT/'data/src/main/kotlin/com/workshop/ledger/data/migration/LegacyRoomMigration2To3.kt').read_text()
        dates=re.findall(r'DateColumn\("([^"]+)", "[^"]+", "([^"]+)"\)',source)
        amounts=re.findall(r'MoneyColumn\("([^"]+)", "[^"]+", "([^"]+)"\)',source)
        for table,col in dates: db.execute(f"ALTER TABLE `{table}` ADD COLUMN `{col}` TEXT NOT NULL DEFAULT ''")
        for table,col in amounts: db.execute(f'ALTER TABLE `{table}` ADD COLUMN `{col}` INTEGER NOT NULL DEFAULT 0')
        db.execute("ALTER TABLE users ADD COLUMN migration_workFactorDecimal TEXT NOT NULL DEFAULT ''")
        pattern=r'@Entity\(tableName = "(\w+)", primaryKeys = \[(.*?)\](.*?)\)\s*data class \w+\((.*?)\n\)'
        matches=list(re.finditer(pattern,entities,re.S))
        tables={r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        self.assertEqual({m.group(1) for m in matches},tables)
        for m in matches:
            table,keys,indices,body=m.groups()
            expected_cols={row[1]:(row[2],bool(row[3]),row[4]) for row in db.execute(f'PRAGMA table_info(`{table}`)')}
            actual_cols={}
            for name,default,kt,nullable in re.findall(r'@ColumnInfo\(name = "(\w+)"(?:, defaultValue = "([^"]+)")?\) val `\w+`: (String|Long|Double)(\?)?',body):
                actual_cols[name]=({'String':'TEXT','Long':'INTEGER','Double':'REAL'}[kt],not bool(nullable),default or None)
            self.assertEqual(actual_cols,expected_cols,table)
            actual_keys=re.findall(r'"(\w+)"',keys)
            expected_keys=[r[1] for r in sorted(db.execute(f'PRAGMA table_info(`{table}`)'),key=lambda r:r[5]) if r[5]]
            self.assertEqual(actual_keys,expected_keys,table)
            found={name:re.findall(r'"(\w+)"',cols) for cols,name in re.findall(r'Index\(value = \[(.*?)\], name = "(\w+)"\)',indices)}
            expected={r[1]:[col[2] for col in db.execute(f'PRAGMA index_info(`{r[1]}`)')] for r in db.execute(f'PRAGMA index_list(`{table}`)') if r[3]!='pk'}
            self.assertEqual(found,expected,table)
    def test_application_wiring_and_no_destructive_fallback(self):
        db=(ROOT/'data/src/main/kotlin/com/workshop/ledger/data/legacy/WorkshopDatabase.kt').read_text()
        app=(ROOT/'app/src/main/java/com/workshop/ledger/StayarApplication.kt').read_text()
        self.assertIn('"workshop-ledger.db"',db)
        self.assertIn('.addMigrations(LegacyRoomMigration1To2, LegacyRoomMigration2To3)',db)
        self.assertNotIn('fallbackToDestructiveMigration',db)
        self.assertIn('by lazy',app)
        self.assertIn('WorkshopDatabase.create(this)',app)
        self.assertIn('android:name=".StayarApplication"',(ROOT/'app/src/main/AndroidManifest.xml').read_text())
if __name__=='__main__': unittest.main()
