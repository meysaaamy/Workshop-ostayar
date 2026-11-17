#!/usr/bin/env python3
"""SQLite integration tests for the standalone journal prototype (not Room)."""
import sqlite3
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCHEMA = (ROOT / 'data/src/main/assets/journal-schema.sql').read_text()
INSERT = '''INSERT INTO journal_events
(event_id, project_id, record_id, timestamp_millis, action, previous_event_id, record_kind, snapshot, reason)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)'''


def create(event='e1', project='p', record='r', snapshot='{"amount":1}'):
    return (event, project, record, 1, 'created', None, 'expense', snapshot, None)


def amend(event='e2', previous='e1', project='p', record='r', snapshot='{"amount":2}', reason='اصلاح'):
    return (event, project, record, 2, 'amended', previous, None, snapshot, reason)


def void(event='e3', previous='e2', project='p', record='r', reason='ابطال'):
    return (event, project, record, 3, 'voided', previous, None, None, reason)


class JournalSqlTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / 'journal.db'
        self.db = sqlite3.connect(self.path)
        self.db.executescript(SCHEMA)
    def tearDown(self):
        self.db.close()
        self.temp.cleanup()
    def rows(self):
        return self.db.execute('SELECT sequence,event_id,action,previous_event_id,snapshot FROM journal_events ORDER BY sequence').fetchall()
    def fail(self, sql, args=()):
        before = self.rows()
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute(sql, args)
        self.assertEqual(self.rows(), before)
    def test_history_and_immutable(self):
        self.db.execute(INSERT, create()); self.db.execute(INSERT, amend()); self.db.execute(INSERT, void())
        self.assertEqual(self.rows(), [(1,'e1','created',None,'{"amount":1}'),(2,'e2','amended','e1','{"amount":2}'),(3,'e3','voided','e2',None)])
        self.fail("UPDATE journal_events SET snapshot='x' WHERE event_id='e1'")
        self.fail("DELETE FROM journal_events WHERE event_id='e1'")
        self.fail(INSERT, amend('e4','e3'))
        self.fail(INSERT, create('e4'))
    def test_replace_and_manual_sequence_rejected(self):
        self.db.execute('PRAGMA recursive_triggers=OFF')
        self.db.execute(INSERT, create())
        # REPLACE must not bypass the delete trigger when recursive_triggers is OFF.
        self.fail(INSERT.replace('INSERT INTO','INSERT OR REPLACE INTO'), create('e1','other','other'))
        columns = '(sequence,event_id,project_id,record_id,timestamp_millis,action,record_kind,snapshot)'
        for seq in (-1, 0, 1, 99):
            self.fail(f'INSERT OR REPLACE INTO journal_events {columns} VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
                      (seq, f'new{seq}', 'other', f'r{seq}', 1, 'created', 'expense', '{}'))
    def test_stale_duplicate_and_constraints(self):
        self.db.execute(INSERT, create())
        self.fail(INSERT, amend(previous='bad'))
        self.fail(INSERT, create('binary', 'p', 'new', sqlite3.Binary(b'payload')))
        self.fail(INSERT, ('binary-id', 'p', sqlite3.Binary(b'r'), 1, 'created', None, 'expense', '{}', None))
        self.fail(INSERT, create('a\u0000b'))
        for reason in [' ', '\t', '\n', '\u00a0', '\u2003', 'a\u0000b']:
            self.fail(INSERT, amend(reason=reason))
        self.fail(INSERT, void(previous='e1', reason=' '))
        self.fail(INSERT, ('e2','p','missing',2,'amended','e1',None,'{}','fix'))
        self.fail(INSERT, ('e2','p','r',2,'created','e1','expense','{}',None))
        self.db.execute(INSERT, create('e2','another-project'))
        self.db.execute(INSERT, amend('e3','e1'))
        self.fail(INSERT, amend('e4','e1'))
        self.assertEqual(len(self.rows()), 3)
    def test_ignore_can_silently_skip_constraint_failure(self):
        self.db.execute(INSERT, create())
        cursor = self.db.execute(INSERT.replace('INSERT INTO','INSERT OR IGNORE INTO'), amend(reason=''))
        self.assertEqual(cursor.rowcount, 0)
        self.assertEqual(len(self.rows()), 1)
        # Callers must use ordinary INSERT and must not interpret a skipped row as success.

    def test_rollback_and_reopen(self):
        self.db.execute(INSERT, create()); self.db.commit()
        with self.assertRaises(sqlite3.IntegrityError):
            with self.db:
                self.db.execute(INSERT, amend())
                self.db.execute(INSERT, amend('e3','e1'))
        self.assertEqual(len(self.rows()), 1)
        self.db.close(); self.db=sqlite3.connect(self.path)
        self.assertEqual(self.rows(), [(1,'e1','created',None,'{"amount":1}')])
        self.db.execute(INSERT, amend()); self.db.commit()
        self.fail(INSERT, amend('e3','e1'))
    def test_independent_connections_detect_stale_head(self):
        self.db.execute(INSERT, create()); self.db.commit()
        other = sqlite3.connect(self.path)
        try:
            other.execute(INSERT, amend()); other.commit()
            self.fail(INSERT, amend('e3','e1'))
            self.db.execute(INSERT, amend('e3','e2')); self.db.commit()
            self.assertEqual(len(self.rows()), 3)
        finally:
            other.close()

if __name__ == '__main__':
    unittest.main()
