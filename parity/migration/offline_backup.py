#!/usr/bin/env python3
"""Offline, unencrypted SQLite + opaque DataStore snapshot; restores to a new directory only.

This is a host-side recovery aid, NOT an Android backup or an app restore implementation.
The caller must stop the old application and all database/DataStore writers throughout.
"""
import argparse
from contextlib import closing
import hashlib
import json
import os
import shutil
import sqlite3
import stat
import sys
from pathlib import Path

MAX_SETTINGS = 128 * 1024 * 1024
MAX_MANIFEST = 16 * 1024
DB = 'workshop-ledger.db'
SETTINGS = 'ledger_settings.preferences_pb'
MANIFEST = 'manifest.json'
FORMAT = 'stayar-offline-snapshot-1'


def regular(path):
    path = Path(path)
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode):
        raise ValueError(f'Not a regular file: {path.name}')
    return path


def metadata(path):
    h = hashlib.sha256()
    size = 0
    with regular(path).open('rb') as f:
        while chunk := f.read(1024 * 1024):
            h.update(chunk)
            size += len(chunk)
    return {'bytes': size, 'sha256': h.hexdigest()}


def check_sqlite(path):
    with closing(sqlite3.connect(regular(path).resolve().as_uri() + '?mode=ro', uri=True)) as conn:
        conn.execute('PRAGMA query_only=ON')
        result = conn.execute('PRAGMA quick_check').fetchall()
        if result != [('ok',)]:
            raise ValueError('SQLite quick_check failed')
        return conn.execute('PRAGMA user_version').fetchone()[0]


def require_stopped(stopped):
    if not stopped:
        raise ValueError('The app and all writers must be stopped; explicit confirmation required')


def new_directory(path):
    path = Path(path)
    # mkdir is exclusive: never overwrite any existing path, including an empty directory.
    path.mkdir(mode=0o700)
    return path


def write_file(path, content):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'wb') as out:
        out.write(content)
        out.flush()
        os.fsync(out.fileno())


def sync_file(path):
    with regular(path).open('rb') as f:
        os.fsync(f.fileno())


def backup(database, settings, destination, *, stopped=False):
    require_stopped(stopped)
    source = regular(database).resolve()
    settings = None if settings is None else regular(settings).resolve()
    if settings and settings == source:
        raise ValueError('Database and settings paths must differ')
    dest = new_directory(destination)
    try:
        # SQLite's backup API incorporates committed WAL data; do not copy the main DB alone.
        with closing(sqlite3.connect(source.as_uri() + '?mode=ro', uri=True)) as src:
            src.execute('PRAGMA query_only=ON')
            with closing(sqlite3.connect(str(dest / DB))) as dst:
                os.chmod(dest / DB, 0o600)
                src.backup(dst)
                dst.execute("PRAGMA journal_mode=DELETE")
        sync_file(dest / DB)
        version = check_sqlite(dest / DB)
        files = {DB: metadata(dest / DB)}
        if settings is not None:
            before = metadata(settings)
            if before['bytes'] > MAX_SETTINGS:
                raise ValueError('DataStore exceeds the size limit')
            with settings.open('rb') as f:
                content = f.read(MAX_SETTINGS + 1)
            after = metadata(settings)
            if before != after or len(content) != before['bytes'] or hashlib.sha256(content).hexdigest() != before['sha256']:
                raise ValueError('DataStore changed during backup')
            write_file(dest / SETTINGS, content)
            files[SETTINGS] = metadata(dest / SETTINGS)
        manifest = {'format': FORMAT, 'sqlite_user_version': version,
                    'settings_present': settings is not None, 'files': files}
        write_file(dest / MANIFEST, (json.dumps(manifest, sort_keys=True, indent=2) + '\n').encode())
        verify(dest)
        return manifest
    except BaseException:
        # Only remove the directory created exclusively by this call, never a pre-existing one.
        shutil.rmtree(dest)
        raise


def no_duplicate_pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate manifest key')
        result[key] = value
    return result


def verify(snapshot):
    snapshot = Path(snapshot)
    if not stat.S_ISDIR(snapshot.lstat().st_mode):
        raise ValueError('Snapshot must be a directory, not a symlink')
    manifest_file = regular(snapshot / MANIFEST)
    if manifest_file.stat().st_size > MAX_MANIFEST:
        raise ValueError('Manifest exceeds size limit')
    manifest = json.loads(manifest_file.read_text(encoding='utf-8'), object_pairs_hook=no_duplicate_pairs)
    if not isinstance(manifest, dict) or set(manifest) != {'format','sqlite_user_version','settings_present','files'}:
        raise ValueError('Malformed manifest')
    if manifest['format'] != FORMAT or type(manifest['sqlite_user_version']) is not int or type(manifest['settings_present']) is not bool:
        raise ValueError('Unsupported manifest')
    expected = {DB, SETTINGS} if manifest['settings_present'] else {DB}
    if not isinstance(manifest['files'],dict) or set(manifest['files']) != expected:
        raise ValueError('Unexpected snapshot file list')
    if {x.name for x in snapshot.iterdir()} != expected | {MANIFEST}:
        raise ValueError('Unexpected extra or missing snapshot files')
    for name in sorted(expected):
        info = manifest['files'][name]
        if not isinstance(info,dict) or set(info) != {'bytes','sha256'} or type(info['bytes']) is not int or info['bytes'] < 0:
            raise ValueError('Malformed file metadata')
        if name == SETTINGS and info['bytes'] > MAX_SETTINGS:
            raise ValueError('DataStore exceeds size limit')
        if metadata(snapshot / name) != info:
            raise ValueError(f'Checksum mismatch: {name}')
    if check_sqlite(snapshot / DB) != manifest['sqlite_user_version']:
        raise ValueError('SQLite version mismatch')
    return manifest


def restore(snapshot, destination, *, stopped=False):
    require_stopped(stopped)
    # Always restore into a new directory, never replace live app data or an existing backup.
    snapshot = Path(snapshot)
    manifest = verify(snapshot)
    dest = new_directory(destination)
    try:
        files = [DB] + ([SETTINGS] if manifest['settings_present'] else [])
        for name in files:
            with regular(snapshot / name).open('rb') as src:
                fd = os.open(dest / name, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
                with os.fdopen(fd, 'wb') as out:
                    shutil.copyfileobj(src, out, length=1024 * 1024)
                    out.flush()
                    os.fsync(out.fileno())
            if metadata(dest / name) != manifest['files'][name]:
                raise ValueError(f'Restored file checksum mismatch: {name}')
        if check_sqlite(dest / DB) != manifest['sqlite_user_version']:
            raise ValueError('Restored SQLite version mismatch')
        return manifest
    except BaseException:
        shutil.rmtree(dest)
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    b = sub.add_parser('backup'); b.add_argument('--database', required=True); group=b.add_mutually_exclusive_group(required=True); group.add_argument('--settings'); group.add_argument('--settings-absent', action='store_true'); b.add_argument('--destination', required=True)
    b.add_argument('--app-stopped', action='store_true', help='confirm all app/database/DataStore writers are stopped')
    r = sub.add_parser('restore'); r.add_argument('--snapshot', required=True); r.add_argument('--destination', required=True)
    r.add_argument('--app-stopped', action='store_true', help='confirm all app/database/DataStore writers are stopped')
    v = sub.add_parser('verify'); v.add_argument('--snapshot', required=True)
    args = parser.parse_args()
    try:
        if args.command == 'backup': result = backup(args.database,args.settings,args.destination,stopped=args.app_stopped)
        elif args.command == 'restore': result = restore(args.snapshot,args.destination,stopped=args.app_stopped)
        else: result = verify(args.snapshot)
    except (OSError, sqlite3.Error, ValueError, UnicodeError) as e:
        print(f'Failed: {e}', file=sys.stderr)
        return 2
    print(json.dumps(result,ensure_ascii=False,indent=2))
    return 0

if __name__ == '__main__':
    sys.exit(main())
