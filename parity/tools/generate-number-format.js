#!/usr/bin/env node
// JS Number formatting oracle, independent of the Kotlin implementation.
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'../..');let seed=0x65a721;
const rand=()=>seed=(Math.imul(seed,1664525)+1013904223)>>>0;
const nums=[0,-0,1,-1,1.005,1.015,2.675,-2.675,1e-7,1e-6,1e20,1e21,Number.MIN_VALUE,Number.MAX_VALUE,9007199254740991];
for(let i=0;i<3000;i++){
 const sign=(rand()&1)?1:-1, magnitude=(rand()%10000000)/1000;
 const pow=(rand()%31)-15; nums.push(sign*magnitude*Math.pow(10,pow));
}
const lines=nums.map(x=>JSON.stringify({input:String(x),string:String(x),fixed2:String(+x.toFixed(2)),percent:String(+(x*100).toFixed(2))}));
const data=lines.join('\n')+'\n',out=path.join(root,'parity/golden/number-format.jsonl');
if(process.argv.includes('--check')){if(fs.readFileSync(out,'utf8')!==data)throw Error('number format fixture mismatch');}
else fs.writeFileSync(out,data);
console.log(`Generated or checked ${nums.length} JS number formatting fixtures.`);
