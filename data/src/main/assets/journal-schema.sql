-- Experimental standalone journal schema. Not installed by the app or a Room migration.
-- Caller must execute the entire schema in one transaction on a compatible fresh database.
CREATE TABLE journal_events (
    sequence INTEGER PRIMARY KEY AUTOINCREMENT,
    event_id TEXT NOT NULL UNIQUE CHECK(typeof(event_id) = 'text' AND instr(event_id, char(0)) = 0) CHECK(length(trim(event_id, char(9) || char(10) || char(11) || char(12) || char(13) || char(28) || char(29) || char(30) || char(31) || char(32) || char(160) || char(5760) || char(8192) || char(8193) || char(8194) || char(8195) || char(8196) || char(8197) || char(8198) || char(8199) || char(8200) || char(8201) || char(8202) || char(8232) || char(8233) || char(8239) || char(8287) || char(12288))) > 0),
    project_id TEXT NOT NULL CHECK(typeof(project_id) = 'text' AND instr(project_id, char(0)) = 0) CHECK(length(trim(project_id, char(9) || char(10) || char(11) || char(12) || char(13) || char(28) || char(29) || char(30) || char(31) || char(32) || char(160) || char(5760) || char(8192) || char(8193) || char(8194) || char(8195) || char(8196) || char(8197) || char(8198) || char(8199) || char(8200) || char(8201) || char(8202) || char(8232) || char(8233) || char(8239) || char(8287) || char(12288))) > 0),
    record_id TEXT NOT NULL CHECK(typeof(record_id) = 'text' AND instr(record_id, char(0)) = 0) CHECK(length(trim(record_id, char(9) || char(10) || char(11) || char(12) || char(13) || char(28) || char(29) || char(30) || char(31) || char(32) || char(160) || char(5760) || char(8192) || char(8193) || char(8194) || char(8195) || char(8196) || char(8197) || char(8198) || char(8199) || char(8200) || char(8201) || char(8202) || char(8232) || char(8233) || char(8239) || char(8287) || char(12288))) > 0),
    timestamp_millis INTEGER NOT NULL CHECK(typeof(timestamp_millis) = 'integer'),
    action TEXT NOT NULL CHECK(typeof(action) = 'text') CHECK(action IN ('created', 'amended', 'voided')),
    previous_event_id TEXT CHECK(previous_event_id IS NULL OR typeof(previous_event_id) = 'text'),
    record_kind TEXT CHECK(record_kind IS NULL OR (typeof(record_kind) = 'text' AND instr(record_kind, char(0)) = 0)),
    snapshot TEXT CHECK(snapshot IS NULL OR typeof(snapshot) = 'text'),
    reason TEXT CHECK(reason IS NULL OR (typeof(reason) = 'text' AND instr(reason, char(0)) = 0)),
    CHECK (
        (action = 'created' AND previous_event_id IS NULL AND record_kind IS NOT NULL AND length(trim(record_kind, char(9) || char(10) || char(11) || char(12) || char(13) || char(28) || char(29) || char(30) || char(31) || char(32) || char(160) || char(5760) || char(8192) || char(8193) || char(8194) || char(8195) || char(8196) || char(8197) || char(8198) || char(8199) || char(8200) || char(8201) || char(8202) || char(8232) || char(8233) || char(8239) || char(8287) || char(12288))) > 0 AND snapshot IS NOT NULL AND reason IS NULL)
        OR (action = 'amended' AND previous_event_id IS NOT NULL AND record_kind IS NULL AND snapshot IS NOT NULL AND reason IS NOT NULL AND length(trim(reason, char(9) || char(10) || char(11) || char(12) || char(13) || char(28) || char(29) || char(30) || char(31) || char(32) || char(160) || char(5760) || char(8192) || char(8193) || char(8194) || char(8195) || char(8196) || char(8197) || char(8198) || char(8199) || char(8200) || char(8201) || char(8202) || char(8232) || char(8233) || char(8239) || char(8287) || char(12288))) > 0)
        OR (action = 'voided' AND previous_event_id IS NOT NULL AND record_kind IS NULL AND snapshot IS NULL AND reason IS NOT NULL AND length(trim(reason, char(9) || char(10) || char(11) || char(12) || char(13) || char(28) || char(29) || char(30) || char(31) || char(32) || char(160) || char(5760) || char(8192) || char(8193) || char(8194) || char(8195) || char(8196) || char(8197) || char(8198) || char(8199) || char(8200) || char(8201) || char(8202) || char(8232) || char(8233) || char(8239) || char(8287) || char(12288))) > 0)
    )
);
CREATE INDEX index_journal_events_record ON journal_events(project_id, record_id, sequence);

-- SQLite's REPLACE may delete a conflicting row without invoking delete triggers unless
-- recursive_triggers is enabled. Reject the conflicting INSERT before conflict handling.
CREATE TRIGGER journal_reject_existing_event BEFORE INSERT ON journal_events
WHEN EXISTS (SELECT 1 FROM journal_events WHERE event_id = NEW.event_id)
BEGIN SELECT RAISE(ABORT, 'journal duplicate event'); END;

-- New sequence numbers are assigned by SQLite, not by a caller. NEW.sequence is -1 for
-- an unspecified rowid in a BEFORE INSERT trigger. The AFTER trigger rejects explicit -1.
CREATE TRIGGER journal_reject_assigned_sequence BEFORE INSERT ON journal_events
WHEN NEW.sequence != -1
BEGIN SELECT RAISE(ABORT, 'journal sequence is automatic'); END;
CREATE TRIGGER journal_reject_nonpositive_sequence AFTER INSERT ON journal_events
WHEN NEW.sequence <= 0
BEGIN SELECT RAISE(ABORT, 'journal sequence must be positive'); END;

CREATE TRIGGER journal_reject_duplicate_record BEFORE INSERT ON journal_events
WHEN NEW.action = 'created' AND EXISTS (
    SELECT 1 FROM journal_events WHERE project_id = NEW.project_id AND record_id = NEW.record_id
)
BEGIN SELECT RAISE(ABORT, 'journal record already exists'); END;

-- A correction must target the currently effective head; a voided record is final.
CREATE TRIGGER journal_reject_stale_change BEFORE INSERT ON journal_events
WHEN NEW.action IN ('amended', 'voided') AND NOT EXISTS (
    SELECT 1 FROM journal_events AS latest
    WHERE latest.project_id = NEW.project_id AND latest.record_id = NEW.record_id
      AND latest.sequence = (SELECT MAX(sequence) FROM journal_events
                             WHERE project_id = NEW.project_id AND record_id = NEW.record_id)
      AND latest.event_id = NEW.previous_event_id AND latest.action != 'voided'
)
BEGIN SELECT RAISE(ABORT, 'journal stale or missing record'); END;

CREATE TRIGGER journal_reject_update BEFORE UPDATE ON journal_events
BEGIN SELECT RAISE(ABORT, 'journal updates forbidden'); END;
CREATE TRIGGER journal_reject_delete BEFORE DELETE ON journal_events
BEGIN SELECT RAISE(ABORT, 'journal deletes forbidden'); END;
