#!/usr/bin/env node
// Exhaustive calendar reference for dates accepted by the legacy web validator.
// Each row: [jy,jm,jd,gy,gm,gd,roundtripJy,roundtripJm,roundtripJd].
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {e,apk,src,root}=require('./legacy-engine');
const out=path.join(root,'parity/golden/calendar-1200-1700.jsonl');
const hash=x=>crypto.createHash('sha256').update(x).digest('hex');
const header={format:'stayar-calendar-v1',apkSha256:hash(fs.readFileSync(apk)),htmlSha256:hash(src),yearFrom:1200,yearTo:1700,columns:['jy','jm','jd','gy','gm','gd','roundtripJy','roundtripJm','roundtripJd']};
const fd=fs.openSync(out,'w');let count=0;
try {
 fs.writeSync(fd,JSON.stringify(header)+'\n');
 let batch='';
 for(let y=1200;y<=1700;y++) for(let m=1;m<=12;m++) for(let d=1;d<=e.jalaali.jalaaliMonthLength(y,m);d++) {
  const date=String(y).padStart(4,'0')+'/'+String(m).padStart(2,'0')+'/'+String(d).padStart(2,'0');
  if(!e.cvDate(date)) throw Error(`Valid calendar date rejected by web validator: ${date}`);
  const g=e.jalaali.toGregorian(y,m,d), j=e.jalaali.toJalaali(g.gy,g.gm,g.gd);
  batch+=JSON.stringify([y,m,d,g.gy,g.gm,g.gd,j.jy,j.jm,j.jd])+'\n';
  count++;
  if(batch.length>1048576) { fs.writeSync(fd,batch);batch=''; }
 }
 fs.writeSync(fd,batch);
} finally {fs.closeSync(fd);}
console.log(`Wrote ${count} web-valid calendar dates to ${path.relative(root,out)}.`);
