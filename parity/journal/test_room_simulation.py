#!/usr/bin/env python3
"""Host SQLite behavior for a Room-like synthetic table, not a Room instrumentation test."""
import sqlite3
import tempfile
import unittest
from pathlib import Path
from test_journal_sql import JournalSqlTest, INSERT, create, amend
import room_simulation

class RoomSimulationTest(JournalSqlTest):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / 'journal.db'
        self.db = sqlite3.connect(self.path)
        room_simulation.create(self.db)
    def test_ignore_can_silently_skip_constraint_failure(self):
        self.db.execute(INSERT, create())
        # Room-like trigger actively aborts invalid fields, including with OR IGNORE.
        self.fail(INSERT.replace('INSERT INTO','INSERT OR IGNORE INTO'), amend(reason=''))

if __name__ == '__main__':
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(RoomSimulationTest)
    result=unittest.TextTestRunner(verbosity=1).run(suite)
    raise SystemExit(0 if result.wasSuccessful() else 1)
