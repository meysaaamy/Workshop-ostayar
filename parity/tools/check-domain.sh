#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17+ installation}"
: "${KOTLINC:=kotlinc}"
# Simple architecture guard; a proper Android lint rule is still required in the full project.
if grep -REn '\b(Float|Double|toDouble|toFloat)\b' "$root/domain/src/main/kotlin"; then
  echo 'Forbidden floating-point primitive found in domain sources' >&2; exit 1
fi
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
"$KOTLINC" "$root/domain/src/main/kotlin/com/workshop/ledger/domain/"*.kt \
  "$root/domain/src/test/kotlin/com/workshop/ledger/domain/"*.kt \
  "$root/parity/kotlin/GoldenReader.kt" "$root/parity/kotlin/CompareGolden.kt" \
  -include-runtime -d "$tmp/domain-test.jar"
"$JAVA_HOME/bin/java" -cp "$tmp/domain-test.jar" com.workshop.ledger.domain.DomainParityKt \
  "$root/parity/golden/legacy-sample.json" "$root/parity/golden/calendar-1200-1700.jsonl" "$root/Stayar-v1_16.apk"

"$JAVA_HOME/bin/java" -cp "$tmp/domain-test.jar" com.workshop.ledger.domain.FinancialParityKt "$root/parity/golden/legacy-sample.json"
"$JAVA_HOME/bin/java" -cp "$tmp/domain-test.jar" com.workshop.ledger.domain.InputConstraintsTestKt
"$JAVA_HOME/bin/java" -cp "$tmp/domain-test.jar" com.workshop.ledger.domain.AppendOnlyJournalTestKt
"$JAVA_HOME/bin/java" -cp "$tmp/domain-test.jar" com.workshop.ledger.domain.LegacyReportNumberTestKt "$root/parity/golden/number-format.jsonl"
