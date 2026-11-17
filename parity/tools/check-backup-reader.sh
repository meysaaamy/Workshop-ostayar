#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17+ installation}"
: "${KOTLINC:=kotlinc}"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
"$KOTLINC" "$root/domain/src/main/kotlin/com/workshop/ledger/domain/"*.kt \
  "$root/data/src/main/kotlin/com/workshop/ledger/data/backup/"*.kt \
  "$root/data/src/test/kotlin/com/workshop/ledger/data/backup/LegacyBackupReaderTest.kt" \
  -include-runtime -d "$tmp/test.jar"
"$JAVA_HOME/bin/java" -cp "$tmp/test.jar" com.workshop.ledger.data.backup.LegacyBackupReaderTestKt "$root/parity/golden/backup-full.json" "$root/parity/golden/backup-project.json"
