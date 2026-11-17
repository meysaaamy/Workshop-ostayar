#!/usr/bin/env node
// Preserve numeric JSON text before JSON.parse rounds it into the original JS engine.
const fs=require('node:fs'),path=require('node:path'),{e,root}=require('./legacy-engine');
const raw=['0','-0','1','0.5','0.50000000000000001','1.00000000000000001','1.25',
 '24','24.0000000000000001','24.00000000000001','-0.00000000000000000001',
 '999999999999','999999999999.000001','999999999999.1','1000000000000',
 '1e-325','-1e-325','1e-400','-1e-400','1e-100000','1e309','1e100000','1.7976931348623157e308'];
const values=raw.map(s=>{let v=JSON.parse(s);return {raw:s,amount:e.cvAmount(v),attendance:e.cvAttValue(v),hours:e.cvAttValue({t:'h',h:v})};});
const output=JSON.stringify({cases:values},null,2)+'\n',target=path.join(root,'parity/golden/legacy-field-raw.json');
if(process.argv.includes('--check')) {if(fs.readFileSync(target,'utf8')!==output)throw Error('validator fixture differs');}
else fs.writeFileSync(target,output);
console.log(`Checked ${raw.length} raw legacy numeric validator cases.`);
