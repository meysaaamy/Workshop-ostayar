#!/usr/bin/env node
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),cp=require('node:child_process'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..'),file=path.join(root,'parity/golden/reports-sample.json'),bytes=fs.readFileSync(file),g=JSON.parse(bytes);
const sha=x=>crypto.createHash('sha256').update(x).digest('hex');
assert.equal(g.source.apkSha256,sha(fs.readFileSync(path.join(root,'Stayar-v1_16.apk'))));
assert.equal(g.cases.length,2);
const read=c=>{const b=Buffer.from(c.result.contentBase64,'base64');assert.deepEqual([...b.subarray(0,3)],[239,187,191]);return b.subarray(3).toString('utf8');};
const f=read(g.cases[0]),a=read(g.cases[1]);
assert.equal(g.cases[0].fn,'exportFinancialReportCSV');assert.equal(g.cases[1].fn,'exportDetailedAuditCSV');
assert.ok(f.includes('گزارش جامع مالی — استایار\r\n'));
assert.ok(f.includes('پرداختی‌های نقدی,20\r\n'));
assert.ok(a.includes("'=هزینه شخصی"));assert.ok(a.includes('پرداختی‌ها,,,,,30'));
assert.ok(a.includes('واریزی,1403/01/01 تا 1403/01/01,واریزی,,,99,,غیرفعال,'));
cp.execFileSync(process.execPath,[path.join(__dirname,'generate-reports.js')]);
assert.equal(sha(fs.readFileSync(file)),sha(bytes),'report golden not reproducible');
console.log('Checked two full original CSV byte fixtures, BOM, injection guard, inactive row and legacy payout-total discrepancy.');
