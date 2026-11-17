#!/usr/bin/env node
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),cp=require('node:child_process'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..'),file=path.join(root,'parity/golden/calendar-1200-1700.jsonl');
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const bytes=fs.readFileSync(file), lines=bytes.toString('utf8').trimEnd().split('\n'), header=JSON.parse(lines.shift());
assert.equal(header.format,'stayar-calendar-v1');
assert.equal(header.apkSha256,sha(fs.readFileSync(path.join(root,'Stayar-v1_16.apk'))));
assert.equal(header.yearFrom,1200);assert.equal(header.yearTo,1700);assert.equal(lines.length,182986);
let previous='', has1399=false,has1403=false,has1402=false;
for(const line of lines) {
 const r=JSON.parse(line); assert.equal(r.length,9);assert.ok(r.every(Number.isInteger));
 assert.deepEqual(r.slice(0,3),r.slice(6,9));
 const key=r.slice(0,3).map((x,i)=>String(x).padStart(i?2:4,'0')).join('/');
 assert.ok(key>previous);previous=key;
 if(key==='1399/12/30') has1399=true;if(key==='1403/12/30') has1403=true;if(key==='1402/12/30') has1402=true;
}
assert.ok(has1399&&has1403&&!has1402);
cp.execFileSync(process.execPath,[path.join(__dirname,'generate-calendar.js')]);
assert.equal(sha(fs.readFileSync(file)),sha(bytes),'calendar golden not reproducible');
console.log(`Checked ${lines.length} calendar reference rows, date ordering, leap boundaries and source reproducibility.`);
