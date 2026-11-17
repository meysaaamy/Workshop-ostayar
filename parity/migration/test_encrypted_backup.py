#!/usr/bin/env python3
import os
import io
import tarfile
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
import sqlite3
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import encrypted_backup as enc
import offline_backup as plain

ROOT=Path(__file__).resolve().parents[2]
PASSWORD='محرمانه-strong-passphrase'
class EncryptionTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
        self.db=self.root/'db';self.settings=self.root/'settings';self.encrypted=self.root/'snapshot.enc'
        self.conn=sqlite3.connect(self.db);self.addCleanup(self.conn.close)
        self.conn.execute('PRAGMA journal_mode=WAL')
        self.conn.executescript((ROOT/'docs/legacy-room-migration-1-2.sql').read_text())
        self.conn.execute('PRAGMA user_version=2')
        self.conn.execute("INSERT INTO projects VALUES ('p','secret-workshop-name',1)")
        self.conn.execute("INSERT INTO attendance VALUES ('p','1403/01/01','u','{\"h\":1.2500}')")
        self.conn.commit();self.settings.write_bytes(b'\x00secret-settings\xff')
    def save(self): enc.backup(self.db,self.settings,self.encrypted,PASSWORD,stopped=True)
    def test_roundtrip_committed_wal_and_opaque_settings(self):
        self.save(); contents=self.encrypted.read_bytes()
        self.assertNotIn(b'secret-workshop-name',contents);self.assertNotIn(b'secret-settings',contents)
        enc.verify(self.encrypted,PASSWORD)
        dest=self.root/'restored';enc.restore(self.encrypted,dest,PASSWORD,stopped=True)
        self.assertEqual((dest/plain.SETTINGS).read_bytes(),self.settings.read_bytes())
        with sqlite3.connect(dest/plain.DB) as conn:
            self.assertEqual(conn.execute('SELECT name FROM projects').fetchone()[0],'secret-workshop-name')
            self.assertEqual(conn.execute('SELECT valueJson FROM attendance').fetchone()[0],'{"h":1.2500}')
    def test_wrong_passphrase_and_tamper_never_restore(self):
        self.save();raw=self.encrypted.read_bytes()
        for pos in (len(enc.MAGIC),enc.HEADER_SIZE+10,len(raw)-1):
            mutated=bytearray(raw);mutated[pos]^=1; bad=self.root/f'tamper-{pos}';bad.write_bytes(mutated)
            with self.assertRaises(ValueError): enc.restore(bad,self.root/f'out-{pos}',PASSWORD,stopped=True)
            self.assertFalse((self.root/f'out-{pos}').exists())
        with self.assertRaises(ValueError): enc.restore(self.encrypted,self.root/'wrong','wrong-password-123',stopped=True)
        self.assertFalse((self.root/'wrong').exists())
    def test_nonce_and_salt_unique(self):
        self.save();other=self.root/'other';enc.backup(self.db,self.settings,other,PASSWORD,stopped=True)
        self.assertNotEqual(self.encrypted.read_bytes()[:enc.HEADER_SIZE],other.read_bytes()[:enc.HEADER_SIZE])
    def test_no_overwrite_and_confirmation(self):
        with self.assertRaises(ValueError): enc.backup(self.db,self.settings,self.encrypted,PASSWORD)
        self.save();before=self.encrypted.read_bytes()
        with self.assertRaises(FileExistsError): self.save()
        self.assertEqual(before,self.encrypted.read_bytes())
        with self.assertRaises(ValueError): enc.restore(self.encrypted,self.root/'out',PASSWORD)
        target=self.root/'existing';target.mkdir();(target/'keep').write_text('keep')
        with self.assertRaises(FileExistsError): enc.restore(self.encrypted,target,PASSWORD,stopped=True)
        self.assertEqual((target/'keep').read_text(),'keep')
    def test_encrypt_preexisting_plain_snapshot(self):
        old=self.root/"old-snapshot";plain.backup(self.db,self.settings,old,stopped=True)
        enc.encrypt_snapshot(old,self.encrypted,PASSWORD);enc.verify(self.encrypted,PASSWORD)
        self.assertTrue((old/plain.DB).exists())  # never silently delete the old backup
    def test_empty_settings_and_short_password(self):
        with self.assertRaises(ValueError): enc.backup(self.db,None,self.encrypted,'short',stopped=True)
        self.assertFalse(self.encrypted.exists())
        enc.backup(self.db,None,self.encrypted,PASSWORD,stopped=True)
        out=self.root/'out';enc.restore(self.encrypted,out,PASSWORD,stopped=True)
        self.assertFalse((out/plain.SETTINGS).exists())
    def test_partial_encryption_failure_removed(self):
        with patch.object(enc.tarfile,'open',side_effect=OSError('injected')):
            with self.assertRaises(OSError): self.save()
        self.assertFalse(self.encrypted.exists())
    def test_authenticated_archive_path_traversal_rejected(self):
        salt=os.urandom(enc.SALT_SIZE);nonce=os.urandom(enc.NONCE_SIZE)
        header=enc.MAGIC+salt+nonce; buf=io.BytesIO()
        with tarfile.open(fileobj=buf,mode='w') as archive:
            info=tarfile.TarInfo('../escape');info.size=4;archive.addfile(info,io.BytesIO(b'evil'))
        cipher=Cipher(algorithms.AES(enc.key_for(PASSWORD,salt)),modes.GCM(nonce)).encryptor()
        cipher.authenticate_additional_data(header)
        self.encrypted.write_bytes(header+cipher.update(buf.getvalue())+cipher.finalize()+cipher.tag)
        with self.assertRaises(ValueError): enc.restore(self.encrypted,self.root/'out',PASSWORD,stopped=True)
        self.assertFalse((self.root/'out').exists());self.assertFalse((self.root/'escape').exists())
    def test_truncated_rejected(self):
        self.save();raw=self.encrypted.read_bytes();self.encrypted.write_bytes(raw[:-32])
        with self.assertRaises(ValueError): enc.verify(self.encrypted,PASSWORD)
if __name__=='__main__':unittest.main()
