#!/usr/bin/env python3
"""Read-only diagnostic for a copy of a legacy Room v2 database; NOT a migration."""
import argparse
import json
import math
import sqlite3
import sys
from collections import Counter
from decimal import Decimal, InvalidOperation
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CALENDAR = ROOT / 'parity/golden/calendar-1200-1700.jsonl'
MAX_AMOUNT = 999_999_999_999
# Only fields relevant to these checks. Room identity, constraints, indices and foreign keys
# are deliberately not certified here. Never treat this as a schema migration validator.
FIELDS = {
    'projects': ('id',), 'users': ('projectId', 'id'),
    'wages': ('projectId', 'id', 'rate', 'effectiveDate'),
    'attendance': ('projectId', 'date', 'userId', 'valueJson'),
    'expenses': ('projectId', 'id', 'date', 'amount'),
    'receipts': ('projectId', 'id', 'dateFrom', 'dateTo', 'amount'),
    'payouts': ('projectId', 'id', 'date', 'amount'),
    'transfers': ('projectId', 'id', 'date', 'amount'),
    'adjustments': ('projectId', 'id', 'date', 'amount'),
    'categories': ('projectId', 'id'),
}
AMOUNTS = ('wages', 'expenses', 'receipts', 'payouts', 'transfers', 'adjustments')
DATES = {'wages': ('effectiveDate',), 'attendance': ('date',), 'expenses': ('date',),
         'receipts': ('dateFrom', 'dateTo'), 'payouts': ('date',),
         'transfers': ('date',), 'adjustments': ('date',)}


def valid_dates():
    with CALENDAR.open(encoding='utf-8') as source:
        header = json.loads(next(source))
        if header.get('format') != 'stayar-calendar-v1' or header.get('yearFrom') != 1200 or header.get('yearTo') != 1700:
            raise ValueError('unexpected calendar reference')
        return {f'{row[0]:04d}/{row[1]:02d}/{row[2]:02d}' for row in map(json.loads, source)}


def classify_hours(raw):
    try:
        obj = json.loads(raw, parse_float=Decimal, parse_int=Decimal,
                         parse_constant=lambda _: (_ for _ in ()).throw(ValueError('nonfinite')))
        if isinstance(obj, bool):
            return 'invalid'
        if isinstance(obj, Decimal) and obj in (Decimal('0'), Decimal('0.5'), Decimal('1')):
            return 'mark'
        if isinstance(obj, str) and obj in ('0', '0.5', '1'):
            return 'mark'
        if not isinstance(obj, dict) or obj.get('t') != 'h' or isinstance(obj.get('h'), bool):
            return 'invalid'
        h = Decimal(str(obj.get('h')))
        if not h.is_finite() or h < 0 or h > 24:
            return 'invalid'
        return 'half_hour' if h * 2 == (h * 2).to_integral_value() else 'legacy_non_half_hour'
    except (ValueError, TypeError, InvalidOperation, json.JSONDecodeError):
        return 'invalid'


def inspect(path):
    path = Path(path).resolve(strict=True)
    if not path.is_file():
        raise ValueError('database is not a regular file')
    dates = valid_dates()
    # mode=ro reads a live WAL if present; it is not a consistent backup across DB and DataStore.
    conn = sqlite3.connect(path.as_uri() + '?mode=ro', uri=True)
    conn.execute('PRAGMA query_only=ON')
    result = {'databaseVersion': None, 'rowCounts': {}, 'issues': {}, 'attendanceValues': {},
              'scope': 'diagnostic only; not Room validation or migration'}
    problems, attendance = Counter(), Counter()
    try:
        conn.execute('BEGIN')
        result['databaseVersion'] = conn.execute('PRAGMA user_version').fetchone()[0]
        if result['databaseVersion'] != 2:
            problems['unexpected_database_version'] += 1
        present = {r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        usable = set()
        for table, fields in FIELDS.items():
            if table not in present:
                problems[f'{table}.missing_table'] += 1
                continue
            # Table names are fixed constants, not user input.
            actual = {row[1] for row in conn.execute(f'PRAGMA table_info("{table}")')}
            missing = set(fields) - actual
            for field in sorted(missing):
                problems[f'{table}.missing_column.{field}'] += 1
            if missing:
                continue
            usable.add(table)
            result['rowCounts'][table] = conn.execute(f'SELECT COUNT(*) FROM "{table}"').fetchone()[0]
        for table in AMOUNTS:
            if table not in usable:
                continue
            field = 'rate' if table == 'wages' else 'amount'
            for (value,) in conn.execute(f'SELECT "{field}" FROM "{table}"'):
                # Integers in the allowed range are exactly representable by binary64.
                if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
                    problems[f'{table}.{field}.not_finite_number'] += 1
                elif value != int(value):
                    problems[f'{table}.{field}.fractional'] += 1
                elif not 0 <= value <= MAX_AMOUNT:
                    problems[f'{table}.{field}.out_of_range'] += 1
        for table, fields in DATES.items():
            if table not in usable:
                continue
            for field in fields:
                for (value,) in conn.execute(f'SELECT "{field}" FROM "{table}"'):
                    if not isinstance(value, str) or value not in dates:
                        problems[f'{table}.{field}.invalid_or_unsupported'] += 1
        if 'receipts' in usable:
            for start, end in conn.execute('SELECT dateFrom,dateTo FROM receipts'):
                if start in dates and end in dates and start > end:
                    problems['receipts.inverted_interval'] += 1
        if 'attendance' in usable:
            for (raw,) in conn.execute('SELECT valueJson FROM attendance'):
                kind = classify_hours(raw)
                attendance[kind] += 1
                if kind == 'invalid':
                    problems['attendance.invalid_value'] += 1
        conn.rollback()
    finally:
        conn.close()
    result['issues'] = dict(sorted(problems.items()))
    result['attendanceValues'] = dict(sorted(attendance.items()))
    result['hasIssues'] = bool(problems)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('database', help='path to an offline copy of workshop-ledger.db')
    args = parser.parse_args()
    try:
        report = inspect(args.database)
    except (OSError, sqlite3.Error, ValueError) as exc:
        print(f'Cannot inspect database: {exc}', file=sys.stderr)
        return 2
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 1 if report['hasIssues'] else 0


if __name__ == '__main__':
    sys.exit(main())
