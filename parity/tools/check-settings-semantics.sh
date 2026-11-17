#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17+ installation}"
: "${KOTLINC:=kotlinc}"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
"$KOTLINC" "$root/data/src/main/kotlin/com/workshop/ledger/data/settings/LegacySettingsSemantics.kt" \
 "$root/data/src/test/kotlin/com/workshop/ledger/data/settings/LegacySettingsSemanticsTest.kt" -include-runtime -d "$tmp/test.jar"
"$JAVA_HOME/bin/java" -cp "$tmp/test.jar" com.workshop.ledger.data.settings.LegacySettingsSemanticsTestKt
