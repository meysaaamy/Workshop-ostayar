#!/usr/bin/env node
// Characterization of the legacy engine only. Not proof of Kotlin parity.
const assert = require('node:assert/strict');
const {e} = require('./legacy-engine');
let state=0x17beef;
function random() { state=(Math.imul(state,1664525)+1013904223)>>>0; return state / 0x100000000; }
function int(n) { return Math.floor(random()*n); }
let allocations=0, partitions=0;
for (let i=0;i<10000;i++) {
  const total=(int(2) ? -1 : 1)*Math.floor(random()*999999999999);
  const weights=Array.from({length:1+int(10)},()=>int(1000));
  const actual=e.allocate(total,weights);
  assert.equal(actual.length,weights.length);
  assert.equal(actual.reduce((a,b)=>a+b,0),weights.some(Boolean) ? total : 0);
  assert.deepEqual(Array.from(actual),Array.from(e.allocate(total,weights)));
  allocations++;
  const amount=Math.floor(random()*999999999999);
  const cutoff=1+int(30);
  const r={dateFrom:'1403/01/01',dateTo:'1403/01/31',amount};
  const day=n=>'1403/01/'+String(n).padStart(2,'0');
  const first=e.receiptShare(r,{from:day(1),to:day(cutoff)});
  const second=e.receiptShare(r,{from:day(cutoff+1),to:day(31)});
  assert.equal(first+second,amount);
  partitions++;
}
console.log(`${allocations} legacy allocations and ${partitions} receipt partitions satisfy selected properties (fixed seed).`);
