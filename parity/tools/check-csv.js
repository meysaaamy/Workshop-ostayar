#!/usr/bin/env node
// Execute legacy csvCell and downloadCSV, intercepting the produced Blob without filesystem/UI access.
const assert=require('node:assert/strict'),vm=require('node:vm');
const {src}=require('./legacy-engine');
const a=src.indexOf('function csvCell(v) {'),b=src.indexOf('async function exportFinancialReportCSV()',a);
assert.ok(a>=0&&b>a,'missing legacy CSV functions');
let saved;
const context=vm.createContext({Blob,saveBlob:(blob,name,mime)=>{saved={blob,name,mime};return true;}});
vm.runInContext(src.slice(a,b)+'\nglobalThis.testDownload=downloadCSV;',context,{timeout:2000});
(async()=>{
 const result=context.testDownload('test.csv',[['نام','مبلغ','متن'],['=cmd',-10,'a,b'],['-10',0,'a"b']]);
 assert.equal(result,true);assert.equal(saved.name,'test.csv');assert.equal(saved.mime,'text/csv');
 const bytes=Buffer.from(await saved.blob.arrayBuffer());
 assert.deepEqual([...bytes.subarray(0,3)],[0xEF,0xBB,0xBF]);
 assert.equal(bytes.subarray(3).toString('utf8'),'نام,مبلغ,متن\r\n\'=cmd,-10,"a,b"\r\n\'-10,0,"a""b"');
 console.log('Verified original CSV includes UTF-8 BOM, CRLF, quoting and formula-injection guard.');
})().catch(e=>{console.error(e);process.exitCode=1;});
