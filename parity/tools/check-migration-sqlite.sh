#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17+ installation}"
: "${KOTLINC:=kotlinc}"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
"$KOTLINC" "$root/domain/src/main/kotlin/com/workshop/ledger/domain/JalaliCalendar.kt" \
  "$root/data/src/main/kotlin/com/workshop/ledger/data/migration/LegacyRoomMigration2To3.kt" \
  "$root/parity/migration/kotlin-stubs/androidx/room/migration/Migration.kt" \
  "$root/parity/migration/kotlin-stubs/androidx/sqlite/db/SupportSQLiteDatabase.kt" \
  "$root/parity/migration/LegacyMigrationSqliteTest.kt" "$root/parity/kotlin/GoldenReader.kt" \
  -include-runtime -d "$tmp/test.jar"
"$JAVA_HOME/bin/java" -cp "$tmp/test.jar" stayar.migrationtest.LegacyMigrationSqliteTestKt "$root/parity/migration/sqlite_worker.py"
