#!/usr/bin/env python3
"""Test-only SQLite process. Its line protocol is private to the migration test harness."""
import json
import hashlib
import struct
import random
import stat
import math
import sqlite3
import sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
conn=sqlite3.connect(':memory:',isolation_level=None)
source_columns={}
inject_late_failure=False

def initialize(kind):
    global conn, source_columns, inject_late_failure
    inject_late_failure=(kind=='late_failure')
    conn.close();conn=sqlite3.connect(':memory:',isolation_level=None)
    conn.executescript((ROOT/'docs/legacy-room-schema.sql').read_text(encoding='utf8'))
    conn.execute('PRAGMA user_version=2')
    conn.execute("INSERT INTO projects VALUES ('p','پروژه',123)")
    conn.execute("INSERT INTO users VALUES ('p','m','اوستا','master','master',0.1,1,1,1,NULL)")
    conn.execute("INSERT INTO users VALUES ('p','s','شاگرد','assistant','apprentice',1.0221679580375851e235,0,0,1,17)")
    conn.execute("INSERT INTO wages VALUES ('p','w','s',101,'1403/01/01')")
    conn.execute("INSERT INTO attendance VALUES ('p','1403/01/02','s',?)", ('{"t":"h","h":1.2500}',))
    conn.execute("INSERT INTO expenses VALUES ('p','e','1403/01/01','description',NULL,'m',31,1,1,2)")
    conn.execute("INSERT INTO receipts VALUES ('p','r','1399/12/30','1403/01/03',999999999999,'receipt',1,1,2)")
    conn.execute("INSERT INTO payouts VALUES ('p','b','1403/01/02','s',2,'pay',0,1,2)")
    conn.execute("INSERT INTO transfers VALUES ('p','t','1403/01/01','m',NULL,4,'transfer','advance',1,1,2)")
    conn.execute("INSERT INTO adjustments VALUES ('p','a','1403/01/02','s','bonus',5,'adj',1,1,2)")
    conn.execute("INSERT INTO categories VALUES ('p','c','مصالح')")
    conn.execute('CREATE TABLE extra_old_table (payload BLOB)')
    conn.execute('INSERT INTO extra_old_table VALUES (?)',(sqlite3.Binary(b'\x00\xff'),))
    if kind=='fraction': conn.execute('UPDATE wages SET rate=101.25')
    if kind=='invalid_date': conn.execute("UPDATE wages SET effectiveDate='1402/12/30'")
    if kind=='invalid_factor': conn.execute('UPDATE users SET workFactor=? WHERE id=?',(math.inf,'s'))
    if kind=='blob_amount': conn.execute('UPDATE expenses SET amount=?',(sqlite3.Binary(b'x'),))
    if kind=='target_exists': conn.execute("ALTER TABLE users ADD COLUMN migration_workFactorDecimal TEXT")
    if kind=='many_factors':
        rng=random.Random(0x517a)
        for i in range(4000):
            value=struct.unpack('>d',rng.randbytes(8))[0]
            if math.isfinite(value):
                conn.execute("INSERT INTO users VALUES (?,?,?,?,?,?,?,?,?,?)",('p',f'f{i}','n','r','master',value,0,0,1,None))
    if kind=='unexpected_trigger': conn.execute("CREATE TRIGGER unexpected BEFORE UPDATE ON receipts BEGIN SELECT 1; END")
    capture_columns()

def capture_columns():
    global source_columns
    source_columns={}
    for (table,) in conn.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name"):
        quoted=table.replace('\"','\"\"')
        source_columns[table]=[row[1] for row in conn.execute(f'PRAGMA table_info(\"{quoted}\")')]

def digest_legacy_rows():
    def encode(v):
        if isinstance(v,bytes): return ['blob',v.hex()]
        if isinstance(v,float): return ['real',struct.pack('>d',v).hex()]
        if isinstance(v,int): return ['integer',str(v)]
        if v is None: return ['null']
        return ['text',v]
    snapshot=[]
    for table,columns in source_columns.items():
        names=','.join('"'+s.replace('"','""')+'"' for s in columns)
        quoted=table.replace('\"','\"\"')
        rows=conn.execute(f'SELECT {names} FROM \"{quoted}\"').fetchall()
        rows=sorted(rows,key=lambda row: json.dumps([encode(v) for v in row],ensure_ascii=True,separators=(',',':')))
        snapshot.append([table,columns,[[encode(v) for v in row] for row in rows]])
    return hashlib.sha256(json.dumps(snapshot,ensure_ascii=True,separators=(',',':')).encode()).hexdigest()


def main(req):
    if req['op']=='init': initialize(req['kind']);return None
    if req['op']=='init_external':
        global conn, inject_late_failure
        path=Path(req['path'])
        if not stat.S_ISREG(path.lstat().st_mode): raise ValueError('database must be a regular non-symlink')
        source=sqlite3.connect(path.resolve().as_uri()+'?mode=ro',uri=True)
        try:
            conn.close();conn=sqlite3.connect(':memory:',isolation_level=None)
            source.backup(conn)
        finally: source.close()
        inject_late_failure=False
        capture_columns()
        return None
    if req['op']=='exec':
        if inject_late_failure and req['sql'].startswith('UPDATE `receipts` SET `migration_'): raise sqlite3.OperationalError('injected late failure')
        conn.execute(req['sql'],req.get('args',[]));return None
    if req['op']=='query': return conn.execute(req['sql']).fetchall()
    if req['op']=='legacy_digest': return digest_legacy_rows()
    if req['op']=='factor_roundtrip':
        rows=conn.execute('SELECT workFactor,migration_workFactorDecimal FROM users').fetchall()
        return all(float(value)==float(text) for value,text in rows)
    raise ValueError('unknown request')

for line in sys.stdin:
    try:
        response={'ok':True,'result':main(json.loads(line))}
    except Exception as exc:
        response={'ok':False,'error':str(exc)}
    print(json.dumps(response,ensure_ascii=True,separators=(',',':')),flush=True)
