#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17+ installation}"
: "${KOTLINC:=kotlinc}"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
"$KOTLINC" "$root/parity/kotlin/GoldenReader.kt" "$root/parity/kotlin/CompareGolden.kt" -include-runtime -d "$tmp/reader.jar"
java="$JAVA_HOME/bin/java"
golden="$root/parity/golden/legacy-sample.json"
apk="$root/Stayar-v1_16.apk"
"$java" -cp "$tmp/reader.jar" stayar.parity.GoldenReaderKt "$golden" "$apk"
# Harness self-test only: outputs are copied from golden, not computed by a new engine.
node - "$golden" "$tmp" <<'JS'
const fs=require('fs'),crypto=require('crypto'),[file,dir]=process.argv.slice(2);
const bytes=fs.readFileSync(file), g=JSON.parse(bytes), sha=crypto.createHash('sha256').update(bytes).digest('hex');
const results=g.cases.map(c=>c.result);
fs.writeFileSync(dir+'/same.json',JSON.stringify({goldenSha256:sha,results}));
const index=g.cases.findIndex(c=>c.fn==='compute'&&c.result.acc?.s?.baseWage===5999);
if(index<0)throw Error('missing floating boundary case');
results[index].acc.s.baseWage=6000;
fs.writeFileSync(dir+'/different.json',JSON.stringify({goldenSha256:sha,results}));
fs.writeFileSync(dir+'/stale.json',JSON.stringify({goldenSha256:'stale',results}));
const original=g.cases.map(c=>c.result);
const fractionIndex=g.cases.findIndex(c=>c.fn==='attFraction'&&c.args[1]===1.333&&c.args[0]?.h===8.5);
if(fractionIndex<0)throw Error('missing fractional case');
const delta=JSON.parse(JSON.stringify(original)); delta[fractionIndex]+=0.0000001;
fs.writeFileSync(dir+'/delta.json',JSON.stringify({goldenSha256:sha,results:delta}));
const missing=JSON.parse(JSON.stringify(original)); delete missing[index].acc.s.baseWage;
fs.writeFileSync(dir+'/missing.json',JSON.stringify({goldenSha256:sha,results:missing}));
fs.writeFileSync(dir+'/short.json',JSON.stringify({goldenSha256:sha,results:original.slice(1)}));
JS
"$java" -cp "$tmp/reader.jar" stayar.parity.CompareGoldenKt "$golden" "$tmp/same.json" "$apk"
if "$java" -cp "$tmp/reader.jar" stayar.parity.CompareGoldenKt "$golden" "$tmp/different.json" "$apk" >"$tmp/diff.log" 2>&1; then
  echo 'Comparator unexpectedly accepted different results' >&2; exit 1
fi
grep -q 'baseWage.*5999.*6000' "$tmp/diff.log"
if "$java" -cp "$tmp/reader.jar" stayar.parity.CompareGoldenKt "$golden" "$tmp/stale.json" "$apk" >"$tmp/stale.log" 2>&1; then
  echo 'Comparator unexpectedly accepted a stale golden checksum' >&2; exit 1
fi
grep -q 'different golden file' "$tmp/stale.log"
echo 'Comparator self-test passed: identical data accepted, altered value and stale hash rejected. No new engine was tested.'

for mismatch in delta missing short; do
  if "$java" -cp "$tmp/reader.jar" stayar.parity.CompareGoldenKt "$golden" "$tmp/$mismatch.json" "$apk" >"$tmp/$mismatch.log" 2>&1; then
    echo "Comparator unexpectedly accepted $mismatch" >&2; exit 1
  fi
done
grep -q 'attFraction' "$tmp/delta.log"
grep -q 'baseWage' "$tmp/missing.log"
grep -q 'Expected .* results' "$tmp/short.log"
echo 'Additional self-tests passed: fractional difference, missing key, and missing case rejected.'
# Exercise the same comparator on full CSV byte fixtures (self-test only).
reports="$root/parity/golden/reports-sample.json"
node - "$reports" "$tmp" <<'JS'
const fs=require('fs'),crypto=require('crypto'),[file,dir]=process.argv.slice(2);
const bytes=fs.readFileSync(file),g=JSON.parse(bytes),hash=crypto.createHash('sha256').update(bytes).digest('hex');
const results=g.cases.map(c=>c.result);
fs.writeFileSync(dir+'/reports-same.json',JSON.stringify({goldenSha256:hash,results}));
results[1].contentBase64='wrong';
fs.writeFileSync(dir+'/reports-bad.json',JSON.stringify({goldenSha256:hash,results}));
JS
"$java" -cp "$tmp/reader.jar" stayar.parity.CompareGoldenKt "$reports" "$tmp/reports-same.json" "$apk"
if "$java" -cp "$tmp/reader.jar" stayar.parity.CompareGoldenKt "$reports" "$tmp/reports-bad.json" "$apk" >"$tmp/reports-bad.log" 2>&1; then
  echo 'Comparator unexpectedly accepted altered report bytes' >&2; exit 1
fi
grep -q 'contentBase64' "$tmp/reports-bad.log"
echo 'Report comparator self-test passed: altered bytes rejected; no new exporter was tested.'
