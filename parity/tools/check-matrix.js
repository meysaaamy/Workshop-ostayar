#!/usr/bin/env node
const fs=require('node:fs'),crypto=require('node:crypto'),path=require('node:path'),cp=require('node:child_process'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..'),file=path.join(root,'docs/legacy-function-matrix-draft.md');
const sha=x=>crypto.createHash('sha256').update(x).digest('hex');
const before=fs.readFileSync(file);cp.execFileSync(process.execPath,[path.join(__dirname,'inventory.js')]);cp.execFileSync(process.execPath,[path.join(__dirname,'build-matrix.js')]);
assert.equal(sha(before),sha(fs.readFileSync(file)));
const lines=before.toString('utf8').split('\n').filter(x=>x.startsWith('| ')&&!x.startsWith('| تابع'));
assert.equal(lines.length,283);
for(const name of ['attFraction','compute','parseMoney','sanityCheck','csvCell','normalizeState']) {
 const line=lines.find(l=>l.startsWith(`| ${name} |`));assert.ok(line?.includes('نمونهٔ قدیمی'),name);
}
assert.equal(lines.filter(x=>x.includes('جزئی؛ آزمون مرجع Kotlin')).length,14);
assert.ok(lines.filter(x=>!x.includes('جزئی؛ آزمون مرجع Kotlin')).every(x=>x.includes('در انتظار پیاده‌سازی')));
console.log('Checked reproducibility and 283 tentative mappings; fourteen have partial implementations and direct Kotlin tests.');
