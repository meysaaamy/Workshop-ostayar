#!/usr/bin/env python3
"""Compare a bounded selection of Kotlin replay and actual SQLite trigger decisions."""
import json
import os
import random
import sqlite3
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCHEMA = (ROOT / 'data/src/main/assets/journal-schema.sql').read_text()
INSERT = '''INSERT INTO journal_events
(event_id,project_id,record_id,timestamp_millis,action,previous_event_id,record_kind,snapshot,reason)
VALUES (?,?,?,?,?,?,?,?,?)'''


def event(action, event_id, project='p', record='r', previous='e0', kind='expense', snapshot='{}', reason='اصلاح'):
    return {'action': action, 'event': event_id, 'project': project, 'record': record,
            'previous': previous if action != 'created' else None,
            'kind': kind if action == 'created' else None,
            'snapshot': snapshot if action != 'voided' else None,
            'reason': reason if action != 'created' else None, 'time': 42}


def scenarios():
    rng = random.Random(0x57A7A2)
    blankish = ['', ' ', '\t', '\n', '\u00a0', '\u2003', '\u3000', '\u200b', '\u0085', '\u0000', 'a\u0000b', 'a', 'علت']
    result = [[], [event('created', 'e0')], [event('created','e0'), event('amended','e1')],
              [event('created','e0'), event('voided','e1'), event('amended','e2','p','r','e1')]]
    for _ in range(2500):
        seq = []
        for n in range(rng.randrange(1, 6)):
            action = rng.choice(['created', 'amended', 'voided'])
            seq.append(event(action, rng.choice(['e0', 'e1', 'e2', *blankish]),
                             rng.choice(['p', 'q', *blankish]), rng.choice(['r', 's', *blankish]),
                             rng.choice(['e0', 'e1', 'e2', *blankish]),
                             rng.choice(['expense', *blankish]), rng.choice(['{}', '', '{"x":1}', 'سند']),
                             rng.choice(blankish)))
        result.append(seq)
    # More valid histories than blind random generation, with targeted changes at the end.
    for n in range(400):
        seq=[event('created','e0'),event('amended','e1'),event('amended','e2','p','r','e1')]
        mode=n % 7
        if mode==0: seq.append(event('voided','e3','p','r','e2'))
        if mode==1: seq.append(event('amended','e3','p','r','e0'))
        if mode==2: seq.append(event('created','e4'))
        if mode==3: seq.append(event('created','e4','other','r'))
        if mode==4: seq.append(event('amended','e1','p','r','e2'))
        if mode==5: seq.append(event('amended','e3','p','r','e2',reason=rng.choice(blankish)))
        if mode==6: seq.extend([event('voided','e3','p','r','e2'),event('amended','e4','p','r','e3')])
        result.append(seq)
    # Compare every non-surrogate BMP code point as a reason, including NUL and all spaces.
    for codepoint in range(0x10000):
        if 0xD800 <= codepoint <= 0xDFFF:
            continue
        result.append([event('created','e0'),event('amended','e1',reason=chr(codepoint))])
    return result


def sqlite_accepts(db, seq):
    db.execute('SAVEPOINT scenario')
    try:
        for e in seq:
            db.execute(INSERT, (e['event'],e['project'],e['record'],e['time'],e['action'],
                                e['previous'],e['kind'],e['snapshot'],e['reason']))
        return 'A'
    except sqlite3.IntegrityError:
        return 'R'
    finally:
        db.execute('ROLLBACK TO scenario')
        db.execute('RELEASE scenario')


def main():
    if len(sys.argv) not in (2,3): raise SystemExit('Usage: check_journal_parity.py compiled-kotlin-jar [room-simulation]')
    cases = scenarios()
    encoded = ''.join(json.dumps(x,ensure_ascii=True,separators=(',',':'))+'\n' for x in cases)
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java')
    proc = subprocess.run([java,'-cp',sys.argv[1],'stayar.journalparity.JournalReplayOracleKt'],
                          input=encoded, text=True, capture_output=True, check=True)
    actual = proc.stdout.splitlines()
    if len(actual) != len(cases): raise AssertionError(f'Kotlin returned {len(actual)} lines for {len(cases)} scenarios')
    with sqlite3.connect(':memory:') as db:
        if len(sys.argv) == 3:
            if sys.argv[2] != 'room-simulation': raise ValueError('unknown schema')
            import room_simulation
            room_simulation.create(db)
        else:
            db.executescript(SCHEMA)
        for i, seq in enumerate(cases):
            expected = sqlite_accepts(db, seq)
            if actual[i] != expected:
                raise AssertionError(f'Parity failure in generated scenario {i}: SQLite={expected} Kotlin={actual[i]} case={seq!r}')
    print(f'Compared Kotlin replay and {"Room-like SQLite simulation" if len(sys.argv)==3 else "standalone SQLite"} acceptance for {len(cases)} generated event sequences; no actual Room/device test.')

if __name__ == '__main__': main()
