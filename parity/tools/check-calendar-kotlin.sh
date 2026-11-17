#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17+ installation}"
: "${KOTLINC:=kotlinc}"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
"$KOTLINC" "$root/parity/kotlin/GoldenReader.kt" "$root/parity/kotlin/CompareGolden.kt" "$root/parity/kotlin/CompareCalendar.kt" -include-runtime -d "$tmp/comparator.jar"
golden="$root/parity/golden/calendar-1200-1700.jsonl"
apk="$root/Stayar-v1_16.apk"
# Only self-test the comparator; outputs are copied from golden and are not a Kotlin calendar implementation.
node - "$golden" "$tmp" <<'JS'
const fs=require('fs'),crypto=require('crypto'),[file,dir]=process.argv.slice(2);
const bytes=fs.readFileSync(file),sha=crypto.createHash('sha256').update(bytes).digest('hex');
const lines=bytes.toString('utf8').trimEnd().split('\n');lines.shift();
const out=[JSON.stringify({goldenSha256:sha})];
for(const line of lines) out.push(JSON.stringify(JSON.parse(line).slice(3)));
fs.writeFileSync(dir+'/same.jsonl',out.join('\n')+'\n');
const index=lines.findIndex(l=>l.startsWith('[1403,12,30,'));if(index<0) throw Error('missing leap boundary');
const bad=out.slice();const row=JSON.parse(bad[index+1]);row[2]++;bad[index+1]=JSON.stringify(row);
fs.writeFileSync(dir+'/different.jsonl',bad.join('\n')+'\n');
fs.writeFileSync(dir+'/short.jsonl',out.slice(0,-1).join('\n')+'\n');
JS
java="$JAVA_HOME/bin/java"
"$java" -cp "$tmp/comparator.jar" stayar.parity.CompareCalendarKt "$golden" "$tmp/same.jsonl" "$apk"
for name in different short; do
 if "$java" -cp "$tmp/comparator.jar" stayar.parity.CompareCalendarKt "$golden" "$tmp/$name.jsonl" "$apk" >"$tmp/$name.log" 2>&1; then
  echo "Comparator unexpectedly accepted $name" >&2; exit 1
 fi
done
grep -q '1403, 12, 30' "$tmp/different.log"
grep -q 'Missing actual row' "$tmp/short.log"
echo 'Calendar comparator self-test passed: a changed leap-day result and a missing row were rejected. No new calendar engine was tested.'
