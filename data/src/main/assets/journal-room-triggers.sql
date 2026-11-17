CREATE TRIGGER journal_validate_fields BEFORE INSERT ON journal_events
WHEN NOT COALESCE((
    (typeof(NEW.event_id) = 'text' AND instr(NEW.event_id, char(0)) = 0 AND length(trim(NEW.event_id, char(9) || char(10) || char(11) || char(12) || char(13) || char(28) || char(29) || char(30) || char(31) || char(32) || char(160) || char(5760) || char(8192) || char(8193) || char(8194) || char(8195) || char(8196) || char(8197) || char(8198) || char(8199) || char(8200) || char(8201) || char(8202) || char(8232) || char(8233) || char(8239) || char(8287) || char(12288))) > 0)
    AND (typeof(NEW.project_id) = 'text' AND instr(NEW.project_id, char(0)) = 0 AND length(trim(NEW.project_id, char(9) || char(10) || char(11) || char(12) || char(13) || char(28) || char(29) || char(30) || char(31) || char(32) || char(160) || char(5760) || char(8192) || char(8193) || char(8194) || char(8195) || char(8196) || char(8197) || char(8198) || char(8199) || char(8200) || char(8201) || char(8202) || char(8232) || char(8233) || char(8239) || char(8287) || char(12288))) > 0)
    AND (typeof(NEW.record_id) = 'text' AND instr(NEW.record_id, char(0)) = 0 AND length(trim(NEW.record_id, char(9) || char(10) || char(11) || char(12) || char(13) || char(28) || char(29) || char(30) || char(31) || char(32) || char(160) || char(5760) || char(8192) || char(8193) || char(8194) || char(8195) || char(8196) || char(8197) || char(8198) || char(8199) || char(8200) || char(8201) || char(8202) || char(8232) || char(8233) || char(8239) || char(8287) || char(12288))) > 0)
    AND (typeof(NEW.timestamp_millis) = 'integer')
    AND (typeof(NEW.action) = 'text' AND NEW.action IN ('created','amended','voided'))
    AND ((NEW.previous_event_id IS NULL OR (typeof(NEW.previous_event_id) = 'text')))
    AND ((NEW.record_kind IS NULL OR (typeof(NEW.record_kind) = 'text' AND instr(NEW.record_kind, char(0)) = 0)))
    AND ((NEW.snapshot IS NULL OR (typeof(NEW.snapshot) = 'text')))
    AND ((NEW.reason IS NULL OR (typeof(NEW.reason) = 'text' AND instr(NEW.reason, char(0)) = 0)))
    AND (((NEW.action='created' AND NEW.previous_event_id IS NULL AND NEW.record_kind IS NOT NULL AND length(trim(NEW.record_kind, char(9) || char(10) || char(11) || char(12) || char(13) || char(28) || char(29) || char(30) || char(31) || char(32) || char(160) || char(5760) || char(8192) || char(8193) || char(8194) || char(8195) || char(8196) || char(8197) || char(8198) || char(8199) || char(8200) || char(8201) || char(8202) || char(8232) || char(8233) || char(8239) || char(8287) || char(12288))) > 0 AND NEW.snapshot IS NOT NULL AND NEW.reason IS NULL) OR (NEW.action='amended' AND NEW.previous_event_id IS NOT NULL AND NEW.record_kind IS NULL AND NEW.snapshot IS NOT NULL AND NEW.reason IS NOT NULL AND length(trim(NEW.reason, char(9) || char(10) || char(11) || char(12) || char(13) || char(28) || char(29) || char(30) || char(31) || char(32) || char(160) || char(5760) || char(8192) || char(8193) || char(8194) || char(8195) || char(8196) || char(8197) || char(8198) || char(8199) || char(8200) || char(8201) || char(8202) || char(8232) || char(8233) || char(8239) || char(8287) || char(12288))) > 0) OR (NEW.action='voided' AND NEW.previous_event_id IS NOT NULL AND NEW.record_kind IS NULL AND NEW.snapshot IS NULL AND NEW.reason IS NOT NULL AND length(trim(NEW.reason, char(9) || char(10) || char(11) || char(12) || char(13) || char(28) || char(29) || char(30) || char(31) || char(32) || char(160) || char(5760) || char(8192) || char(8193) || char(8194) || char(8195) || char(8196) || char(8197) || char(8198) || char(8199) || char(8200) || char(8201) || char(8202) || char(8232) || char(8233) || char(8239) || char(8287) || char(12288))) > 0)))
), 0)
BEGIN SELECT RAISE(ABORT, 'journal invalid fields'); END;
-- stayar-statement
CREATE TRIGGER journal_reject_existing_event BEFORE INSERT ON journal_events
WHEN EXISTS (SELECT 1 FROM journal_events WHERE event_id = NEW.event_id)
BEGIN SELECT RAISE(ABORT, 'journal duplicate event'); END;
-- stayar-statement
CREATE TRIGGER journal_reject_assigned_sequence BEFORE INSERT ON journal_events
WHEN NEW.sequence != -1
BEGIN SELECT RAISE(ABORT, 'journal sequence is automatic'); END;
-- stayar-statement
CREATE TRIGGER journal_reject_nonpositive_sequence AFTER INSERT ON journal_events
WHEN NEW.sequence <= 0
BEGIN SELECT RAISE(ABORT, 'journal sequence must be positive'); END;
-- stayar-statement
CREATE TRIGGER journal_reject_duplicate_record BEFORE INSERT ON journal_events
WHEN NEW.action = 'created' AND EXISTS (
    SELECT 1 FROM journal_events WHERE project_id = NEW.project_id AND record_id = NEW.record_id
)
BEGIN SELECT RAISE(ABORT, 'journal record already exists'); END;
-- stayar-statement
CREATE TRIGGER journal_reject_stale_change BEFORE INSERT ON journal_events
WHEN NEW.action IN ('amended', 'voided') AND NOT EXISTS (
    SELECT 1 FROM journal_events AS latest
    WHERE latest.project_id = NEW.project_id AND latest.record_id = NEW.record_id
      AND latest.sequence = (SELECT MAX(sequence) FROM journal_events
                             WHERE project_id = NEW.project_id AND record_id = NEW.record_id)
      AND latest.event_id = NEW.previous_event_id AND latest.action != 'voided'
)
BEGIN SELECT RAISE(ABORT, 'journal stale or missing record'); END;
-- stayar-statement
CREATE TRIGGER journal_reject_update BEFORE UPDATE ON journal_events
BEGIN SELECT RAISE(ABORT, 'journal updates forbidden'); END;
-- stayar-statement
CREATE TRIGGER journal_reject_delete BEFORE DELETE ON journal_events
BEGIN SELECT RAISE(ABORT, 'journal deletes forbidden'); END;
