#!/usr/bin/env python3
import sqlite3
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import offline_backup as backup

ROOT=Path(__file__).resolve().parents[2]
class OfflineBackupTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name); self.db=self.root/'original.db'; self.settings=self.root/'settings.pb'
        self.conn=sqlite3.connect(self.db); self.addCleanup(self.conn.close)
        self.conn.execute('PRAGMA journal_mode=WAL')
        self.conn.executescript((ROOT/'docs/legacy-room-migration-1-2.sql').read_text())
        self.conn.execute('PRAGMA user_version=2')
        self.conn.execute("INSERT INTO projects VALUES ('p','کارگاه',17)")
        self.conn.execute("INSERT INTO users VALUES ('p','u','نام','role','master',1.25,1,1,1,NULL)")
        self.conn.execute("INSERT INTO wages VALUES ('p','w','u',101,'1403/01/01')")
        self.conn.execute("INSERT INTO attendance VALUES ('p','1403/01/02','u',?)",('{"t":"h","h":1.2500}',))
        self.conn.commit()
        # Opaque bytes intentionally contain NUL and unknown fields: no protobuf interpretation.
        self.settings.write_bytes(b'\x0a\x06active\x00\xff\x00unknown\x10\x01')
    def test_wal_and_datastore_roundtrip(self):
        self.assertTrue(Path(str(self.db)+'-wal').exists())
        snap=self.root/'snapshot'; manifest=backup.backup(self.db,self.settings,snap,stopped=True)
        self.assertEqual(manifest['sqlite_user_version'],2); self.assertTrue(manifest['settings_present'])
        result=self.root/'restored';backup.restore(snap,result,stopped=True)
        self.assertEqual((result/backup.SETTINGS).read_bytes(),self.settings.read_bytes())
        with sqlite3.connect(result/backup.DB) as db:
            self.assertEqual(db.execute('PRAGMA user_version').fetchone()[0],2)
            self.assertEqual(db.execute('SELECT name FROM projects').fetchone()[0],'کارگاه')
            self.assertEqual(db.execute('SELECT valueJson FROM attendance').fetchone()[0],'{"t":"h","h":1.2500}')
        self.assertEqual(backup.verify(snap),manifest)
    def test_explicit_confirmation_and_no_overwrite(self):
        dest=self.root/'dest'; dest.mkdir(); (dest/'keep').write_text('safe')
        with self.assertRaises(ValueError): backup.backup(self.db,self.settings,self.root/'no-confirm')
        with self.assertRaises(FileExistsError): backup.backup(self.db,self.settings,dest,stopped=True)
        snap=self.root/'snapshot';backup.backup(self.db,self.settings,snap,stopped=True)
        with self.assertRaises(ValueError): backup.restore(snap,self.root/'no-restore-confirm')
        with self.assertRaises(FileExistsError): backup.restore(snap,dest,stopped=True)
        self.assertEqual((dest/'keep').read_text(),'safe')
    def test_settings_absent_is_explicit(self):
        snap=self.root/'snapshot';m=backup.backup(self.db,None,snap,stopped=True)
        self.assertFalse(m['settings_present']);backup.restore(snap,self.root/'out',stopped=True)
        self.assertFalse((self.root/'out'/backup.SETTINGS).exists())
    def test_corruption_and_manifest_tampering_rejected_without_output(self):
        snap=self.root/'snapshot';backup.backup(self.db,self.settings,snap,stopped=True)
        (snap/backup.SETTINGS).write_bytes(b'tampered')
        with self.assertRaises(ValueError): backup.restore(snap,self.root/'out',stopped=True)
        self.assertFalse((self.root/'out').exists())
    def test_duplicate_manifest_keys_and_extra_files_rejected(self):
        snap=self.root/'snapshot';backup.backup(self.db,self.settings,snap,stopped=True)
        original=(snap/backup.MANIFEST).read_text()
        (snap/backup.MANIFEST).write_text(original[:-2]+',"format":"other"}')
        with self.assertRaises(ValueError): backup.verify(snap)
        (snap/backup.MANIFEST).write_text(original)
        (snap/'unexpected').write_bytes(b'x')
        with self.assertRaises(ValueError): backup.verify(snap)
    def test_failure_removes_partial_and_never_changes_source(self):
        before=self.settings.read_bytes();snap=self.root/'snapshot'
        with patch.object(backup,'write_file',side_effect=OSError('injected error')):
            with self.assertRaises(OSError): backup.backup(self.db,self.settings,snap,stopped=True)
        self.assertFalse(snap.exists());self.assertEqual(self.settings.read_bytes(),before)
        self.assertEqual(self.conn.execute('SELECT COUNT(*) FROM projects').fetchone()[0],1)
        backup.backup(self.db,self.settings,snap,stopped=True)
        with patch.object(backup,'check_sqlite',side_effect=OSError('injected failure')):
            with self.assertRaises(OSError): backup.restore(snap,self.root/'failed',stopped=True)
        self.assertFalse((self.root/'failed').exists()); self.assertTrue(snap.exists())
    def test_oversized_settings_and_symlink_rejected(self):
        with patch.object(backup,'MAX_SETTINGS',4):
            with self.assertRaises(ValueError): backup.backup(self.db,self.settings,self.root/'too-big',stopped=True)
        self.assertFalse((self.root/'too-big').exists())
        symlink=self.root/'symlink';symlink.symlink_to(self.settings)
        with self.assertRaises(ValueError): backup.backup(self.db,symlink,self.root/'symlink-out',stopped=True)
    def test_partial_restore_cleanup_preserves_snapshot(self):
        snap=self.root/'snapshot';backup.backup(self.db,self.settings,snap,stopped=True)
        with patch.object(backup.shutil,'copyfileobj',side_effect=OSError('injected copy failure')):
            with self.assertRaises(OSError): backup.restore(snap,self.root/'partial',stopped=True)
        self.assertFalse((self.root/'partial').exists())
        backup.verify(snap)
    def test_changed_settings_fails_closed(self):
        real=backup.metadata; calls=0
        def unstable(path):
            nonlocal calls
            if Path(path)==self.settings:
                calls+=1
                if calls==2: self.settings.write_bytes(b'changed')
            return real(path)
        with patch.object(backup,'metadata',side_effect=unstable):
            with self.assertRaises(ValueError): backup.backup(self.db,self.settings,self.root/'snapshot',stopped=True)
        self.assertFalse((self.root/'snapshot').exists())
    def test_valid_sqlite_with_altered_db_detected(self):
        snap=self.root/'snapshot';backup.backup(self.db,self.settings,snap,stopped=True)
        with sqlite3.connect(snap/backup.DB) as db: db.execute("UPDATE projects SET name='other'")
        with self.assertRaises(ValueError): backup.verify(snap)
if __name__=='__main__': unittest.main()
