"""Approximate generated Room entity DDL for host-side trigger tests; not Room itself."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = '''CREATE TABLE journal_events (
sequence INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
event_id TEXT NOT NULL, project_id TEXT NOT NULL, record_id TEXT NOT NULL,
timestamp_millis INTEGER NOT NULL, action TEXT NOT NULL, previous_event_id TEXT,
record_kind TEXT, snapshot TEXT, reason TEXT);
CREATE UNIQUE INDEX index_journal_events_event_id ON journal_events(event_id);
CREATE INDEX index_journal_events_project_id_record_id_sequence ON journal_events(project_id,record_id,sequence);
'''

def create(db):
    db.executescript(BASE)
    triggers=(ROOT / 'data/src/main/assets/journal-room-triggers.sql').read_text()
    statements=[s.strip() for s in triggers.split('-- stayar-statement') if s.strip()]
    assert len(statements)==8
    for statement in statements: db.execute(statement)
