#!/usr/bin/env node
// Characterize the old calendar, not a test of a new Kotlin implementation.
const assert = require('node:assert/strict');
const {e} = require('./legacy-engine');
const j=e.jalaali;
let count=0, exceptions=0;
for(let y=0;y<=3177;y++) for(let m=1;m<=12;m++) for(let d=1;d<=j.jalaaliMonthLength(y,m);d++) {
  count++;
  const g=j.toGregorian(y,m,d);
  try {
    const back=j.toJalaali(g.gy,g.gm,g.gd);
    assert.equal(`${back.jy}/${back.jm}/${back.jd}`,`${y}/${m}/${d}`);
  } catch(error) {
    if (error.message!=='Invalid Jalaali year 3178' || y!==3177 || !(m>10 || (m===10 && d>=12))) throw error;
    exceptions++;
  }
}
assert.equal(count,1160740); assert.equal(exceptions,78);
console.log(`${count} valid four-digit Jalali dates checked against legacy round-trip; ${exceptions} legacy boundary exceptions documented. No Kotlin comparison.`);
