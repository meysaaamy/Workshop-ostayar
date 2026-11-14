#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17+ installation}"
: "${KOTLINC:=kotlinc}"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
"$KOTLINC" "$root/domain/src/main/kotlin/com/workshop/ledger/domain/AppendOnlyJournal.kt" \
  "$root/parity/journal/JournalReplayOracle.kt" "$root/parity/kotlin/GoldenReader.kt" \
  -include-runtime -d "$tmp/journal.jar"
python3 "$root/parity/journal/check_journal_parity.py" "$tmp/journal.jar"
python3 "$root/parity/journal/check_journal_parity.py" "$tmp/journal.jar" room-simulation
