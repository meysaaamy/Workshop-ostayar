#!/usr/bin/env node
// Execute old CSV report builders and capture their actual file bytes via a fake saveBlob.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),vm=require('node:vm');
const {context,apk,src,root}=require('./legacy-engine');
function between(a,b){const start=src.indexOf(a),end=src.indexOf(b,start+a.length);if(start<0||end<0)throw Error('source markers missing');return src.slice(start,end);}
process.env.TZ='UTC';
class FixedDate extends Date {constructor(...args){super(...(args.length?args:['2024-04-03T12:34:00.000Z']));} static now(){return Date.parse('2024-04-03T12:34:00.000Z');}}
const saved=[];
context.Date=FixedDate;context.Blob=Blob;context.CustomEvent=class {constructor(type,details){this.type=type;this.detail=details.detail;}};
context.window.dispatchEvent=()=>{};
context.saveBlob=(blob,filename,mime)=>{saved.push({blob,filename,mime});return true;};
const extra=[
 between('function attLabel(v) {','/* ---- wage rate'),
 between('const KIND_FA = ', 'const CRUD = '),
 between('const rangeText = ','function shareItems('),
 between('const fileStamp = ','function blobToBase64('),
 between('function downloadCSV(filename, rows) {','/* ---------- statements ---------- */'),
 'globalThis.runReports = async p => { S = {projects:[p],activeProjectId:p.id}; await exportFinancialReportCSV(); exportDetailedAuditCSV(); };'
].join('\n');
vm.runInContext(extra,context,{timeout:2000,filename:'extracted-report-functions.js'});
const capturedRows=[]; const originalDownload=context.downloadCSV;
context.downloadCSV=(filename,rows)=>{capturedRows.push(rows);return originalDownload(filename,rows);};
const base=JSON.parse(fs.readFileSync(path.join(root,'parity/golden/legacy-sample.json'))).cases.find(c=>c.fn==='compute'&&c.args[1].users?.some(u=>u.id==='m2'));
if(!base)throw Error('missing rich fixture');
const project=JSON.parse(JSON.stringify(base.args[1]));
project.name='کارگاه نمونه'; project.settings.rangeFrom='1403/01/01';project.settings.rangeTo='1403/01/04';
project.categories=[{id:'c',name:'مصالح'}];
project.expenses[0].description='=هزینه شخصی';project.expenses[0].categoryId='c';
project.expenses[1].description='هزینه مشترک، ابزار';project.expenses[1].categoryId='c';
for(const r of project.receipts)r.description='واریزی';
for(const r of project.payouts)r.description='پرداخت';
for(const r of project.transfers)r.description='انتقال';
for(const r of project.adjustments)r.description='تعدیل';
(async()=>{
 await context.runReports(project);if(saved.length!==2)throw Error(`Expected two saves, got ${saved.length}`);
 const cases=[];
 for(let i=0;i<saved.length;i++){
  const x=saved[i],bytes=Buffer.from(await x.blob.arrayBuffer());
  cases.push({fn:i===0?'exportFinancialReportCSV':'exportDetailedAuditCSV',args:[project],result:{filename:x.filename,mime:x.mime,rows:capturedRows[i],contentBase64:bytes.toString('base64')}});
 }
 // Additional comprehensive reports from the same old export, varying meaningful project state.
 const variants=[];
 for(let i=0;i<14;i++){
  const q=JSON.parse(JSON.stringify(project));
  q.settings.profitBasis=i%2?'commitment':'cash';
  q.settings.overtimeFactor=[1,1.333,1.5,2][i%4];
  q.settings.absentPenalty=[0,0.5,1.25][i%3];
  if(i===1)q.receipts=[];
  if(i===2)q.attendance={};
  if(i===3){q.settings.rangeFrom='';q.settings.rangeTo='';}
  if(i===4)q.name='=نام، خطر';
  if(i===5)q.users[0].active=false;
  if(i===6)q.attendance['1403/01/04'].s={t:'h',h:1.25};
  if(i===7)q.users[2].workFactor=0.1;
  if(i===8){q.attendance['1403/01/01'].missing=1;q.payouts.push({date:'1403/01/04',amount:3,userId:'missing'});q.expenses[0].categoryId='missing';}
  if(i===9){q.attendance['1403/01/04'].s={t:'h',h:25};q.attendance['1403/01/04'].m={t:'h',h:-1};}
  if(i===10){q.expenses[0].active=false;q.payouts[0].active=false;q.transfers[0].active=false;}
  if(i===11){q.receipts[0].dateFrom='1403/01/05';q.receipts[0].dateTo='1403/01/01';}
  if(i===12){q.attendance['1403/01/01'].m='0';q.attendance['1403/01/01'].s='0.5';q.attendance['1403/01/01'].m2='1';}
  if(i===13){q.transfers[0].kind='other';q.adjustments[0].kind='unknown';q.users[1].type='custom';}
  const before=saved.length;await context.runReports(q);
  const output=Buffer.from(await saved[before].blob.arrayBuffer());
  const auditOutput=Buffer.from(await saved[before+1].blob.arrayBuffer());
  variants.push({args:[q],result:{contentBase64:output.toString('base64')},auditResult:{contentBase64:auditOutput.toString('base64')}});
 }
 const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
 const data={source:{apkSha256:sha(fs.readFileSync(apk)),htmlSha256:sha(src)},clock:'2024-04-03T12:34:00.000Z',cases,financialVariants:variants};
 const out=path.join(root,'parity/golden/reports-sample.json');fs.writeFileSync(out,JSON.stringify(data,null,2)+'\n');
 console.log(`Wrote ${cases.length} original CSV report fixtures with a fixed clock.`);
})().catch(e=>{console.error(e);process.exitCode=1;});
