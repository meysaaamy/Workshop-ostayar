#!/usr/bin/env python3
"""Passphrase-encrypted offline SQLite/DataStore snapshot; host tool, not Android restore."""
import argparse
import getpass
import os
import shutil
import tarfile
import tempfile
from pathlib import Path
from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives.kdf.scrypt import Scrypt
import offline_backup as plain

# Version 1: fixed scrypt parameters, AES-256-GCM; header authenticated as AAD.
MAGIC = b'STAYAR-ENCRYPTED-1\x00'
SALT_SIZE, NONCE_SIZE, TAG_SIZE = 16, 12, 16
HEADER_SIZE = len(MAGIC) + SALT_SIZE + NONCE_SIZE
MAX_ARCHIVE = 4 * 1024 * 1024 * 1024
CHUNK = 1024 * 1024


def key_for(password, salt):
    if not isinstance(password, str) or len(password) < 12:
        raise ValueError('Passphrase must contain at least 12 characters')
    return Scrypt(salt=salt, length=32, n=2**15, r=8, p=1).derive(password.encode('utf-8'))


class EncryptWriter:
    def __init__(self, out, encryptor): self.out, self.encryptor = out, encryptor
    def write(self, value):
        self.out.write(self.encryptor.update(value))
        return len(value)
    def flush(self): self.out.flush()


def encrypt_snapshot(snapshot, destination, password):
    plain.verify(snapshot)
    salt, nonce = os.urandom(SALT_SIZE), os.urandom(NONCE_SIZE)
    header = MAGIC + salt + nonce
    encryptor = Cipher(algorithms.AES(key_for(password, salt)), modes.GCM(nonce)).encryptor()
    encryptor.authenticate_additional_data(header)
    dest = Path(destination)
    fd = os.open(dest, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(fd, 'wb') as out:
            out.write(header)
            with tarfile.open(fileobj=EncryptWriter(out, encryptor), mode='w|', format=tarfile.USTAR_FORMAT) as archive:
                for name in (plain.MANIFEST, plain.DB, plain.SETTINGS):
                    file = Path(snapshot) / name
                    if file.exists():
                        plain.regular(file)
                        if file.stat().st_size > (MAX_ARCHIVE if name == plain.DB else plain.MAX_SETTINGS if name == plain.SETTINGS else plain.MAX_MANIFEST):
                            raise ValueError('Snapshot component exceeds size limit')
                        # Set metadata explicitly; no source paths or file ownership in archive.
                        info = tarfile.TarInfo(name); info.size = file.stat().st_size; info.mode = 0o600; info.mtime = 0
                        with file.open('rb') as src: archive.addfile(info, src)
            out.write(encryptor.finalize()); out.write(encryptor.tag)
            out.flush(); os.fsync(out.fileno())
    except BaseException:
        dest.unlink(missing_ok=True)
        raise


def unpack_verified(encrypted, password, workdir):
    encrypted = plain.regular(encrypted)
    size = encrypted.stat().st_size
    if size < HEADER_SIZE + TAG_SIZE or size > MAX_ARCHIVE + 2 * plain.MAX_SETTINGS:
        raise ValueError('Encrypted snapshot size invalid')
    archive_file = Path(workdir) / 'authenticated.tar'
    with encrypted.open('rb') as source:
        header = source.read(HEADER_SIZE)
        if len(header) != HEADER_SIZE or not header.startswith(MAGIC): raise ValueError('Unsupported encrypted snapshot')
        salt = header[len(MAGIC):len(MAGIC)+SALT_SIZE]; nonce = header[-NONCE_SIZE:]
        source.seek(-TAG_SIZE, os.SEEK_END); tag = source.read(TAG_SIZE); source.seek(HEADER_SIZE)
        decryptor = Cipher(algorithms.AES(key_for(password, salt)), modes.GCM(nonce,tag)).decryptor()
        decryptor.authenticate_additional_data(header)
        remaining = size - HEADER_SIZE - TAG_SIZE
        fd = os.open(archive_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'wb') as out:
            while remaining:
                chunk = source.read(min(CHUNK,remaining))
                if not chunk: raise ValueError('Truncated encrypted snapshot')
                out.write(decryptor.update(chunk)); remaining -= len(chunk)
            try: out.write(decryptor.finalize())
            except InvalidTag as exc: raise ValueError('Wrong passphrase or corrupted snapshot') from exc
    snapshot = Path(workdir) / 'snapshot'; snapshot.mkdir(mode=0o700)
    seen = set()
    with tarfile.open(archive_file, mode='r:') as archive:
        for item in archive:
            if not item.isfile() or item.name not in (plain.MANIFEST, plain.DB, plain.SETTINGS) or item.name in seen:
                raise ValueError('Unexpected archive entry')
            seen.add(item.name)
            limit = MAX_ARCHIVE if item.name == plain.DB else plain.MAX_SETTINGS if item.name == plain.SETTINGS else plain.MAX_MANIFEST
            if item.size < 0 or item.size > limit: raise ValueError('Archive entry exceeds size limit')
            inp = archive.extractfile(item)
            if inp is None: raise ValueError('Missing archive content')
            fd = os.open(snapshot / item.name, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with inp, os.fdopen(fd, 'wb') as out: shutil.copyfileobj(inp,out,CHUNK)
    plain.verify(snapshot)
    return snapshot


def backup(database, settings, destination, password, *, stopped=False):
    plain.require_stopped(stopped)
    # Both staging and decryption use private temporary files, not encryption-at-rest.
    with tempfile.TemporaryDirectory(prefix='stayar-snapshot-') as temp:
        snapshot = Path(temp) / 'snapshot'
        plain.backup(database,settings,snapshot,stopped=True)
        encrypt_snapshot(snapshot,destination,password)


def verify(encrypted, password):
    with tempfile.TemporaryDirectory(prefix='stayar-verify-') as temp:
        unpack_verified(encrypted,password,temp)


def restore(encrypted, destination, password, *, stopped=False):
    plain.require_stopped(stopped)
    with tempfile.TemporaryDirectory(prefix='stayar-restore-') as temp:
        snapshot=unpack_verified(encrypted,password,temp)
        plain.restore(snapshot,destination,stopped=True)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    sub=parser.add_subparsers(dest='command',required=True)
    b=sub.add_parser('backup');b.add_argument('--database',required=True)
    group=b.add_mutually_exclusive_group(required=True);group.add_argument('--settings');group.add_argument('--settings-absent',action='store_true')
    b.add_argument('--destination',required=True);b.add_argument('--app-stopped',action='store_true')
    e=sub.add_parser('encrypt-legacy');e.add_argument('--snapshot',required=True);e.add_argument('--destination',required=True)
    r=sub.add_parser('restore');r.add_argument('--snapshot',required=True);r.add_argument('--destination',required=True);r.add_argument('--app-stopped',action='store_true')
    v=sub.add_parser('verify');v.add_argument('--snapshot',required=True)
    args=parser.parse_args()
    try:
        if args.command in ('backup','restore'): plain.require_stopped(args.app_stopped)
        password=getpass.getpass('Passphrase: ')
        if args.command in ('backup','encrypt-legacy'):
            if password != getpass.getpass('Confirm passphrase: '): raise ValueError('Passphrases differ')
            if args.command=='backup': backup(args.database,args.settings,args.destination,password,stopped=True)
            else: encrypt_snapshot(args.snapshot,args.destination,password)
        elif args.command=='restore': restore(args.snapshot,args.destination,password,stopped=True)
        else: verify(args.snapshot,password)
    except (ValueError,OSError,tarfile.TarError,EOFError) as e:
        print(f'Failed: {e}',file=__import__('sys').stderr); return 2
    print('Done. This host tool does not install or restore app data on Android.')
    return 0

if __name__=='__main__': raise SystemExit(main())
