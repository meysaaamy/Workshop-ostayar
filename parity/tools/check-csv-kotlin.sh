#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17+ installation}"
: "${KOTLINC:=kotlinc}"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
"$KOTLINC" "$root/domain/src/main/kotlin/com/workshop/ledger/domain/"*.kt \
  "$root/data/src/main/kotlin/com/workshop/ledger/data/export/"*.kt \
  "$root/data/src/test/kotlin/com/workshop/ledger/data/export/"*.kt "$root/parity/kotlin/GoldenReader.kt" \
  -include-runtime -d "$tmp/test.jar"
"$JAVA_HOME/bin/java" -cp "$tmp/test.jar" com.workshop.ledger.data.export.LegacyCsvTestKt \
  "$root/parity/golden/legacy-sample.json" "$root/parity/golden/reports-sample.json"
