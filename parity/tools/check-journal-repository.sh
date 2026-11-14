#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17+ installation}"
: "${KOTLINC:=kotlinc}"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
"$KOTLINC" "$root/domain/src/main/kotlin/com/workshop/ledger/domain/AppendOnlyJournal.kt" \
  "$root/data/src/main/kotlin/com/workshop/ledger/data/journal/JournalRepository.kt" \
  "$root/data/src/test/kotlin/com/workshop/ledger/data/journal/JournalRepositoryTest.kt" \
  -include-runtime -d "$tmp/repository.jar"
"$JAVA_HOME/bin/java" -cp "$tmp/repository.jar" com.workshop.ledger.data.journal.JournalRepositoryTestKt
