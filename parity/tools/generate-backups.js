#!/usr/bin/env node
// Run actual legacy export functions from a trusted APK; dependencies are controlled fakes.
const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const {src,root}=require('./legacy-engine');
function between(a,b) {const s=src.indexOf(a),e=src.indexOf(b,s+a.length);if(s<0||e<0)throw Error('Missing export source markers');return src.slice(s,e);}
const p={id:'p',name:'کارگاه نمونه',settings:{},users:[{id:'u',name:'استاد',type:'master'}],wages:[],attendance:{'1403/01/01':{u:{t:'h',h:1.25}}},expenses:[],receipts:[],payouts:[],transfers:[],adjustments:[],categories:[]};
const saved=[];
const context=vm.createContext({Blob,S:{version:3,activeProjectId:'p',projects:[p]},P:()=>p,ENV:{},Date,
 jNowStr:()=> '1403/01/01 12:00',safeName:(name,ext)=>name+'.'+ext,saveBlob:(blob)=>{saved.push(blob);return true;},saveEnv:()=>{}});
const code=[between('async function exportFullDatabaseJson() {','function backupNag('),between('function exportProjectJson() {','function importBackupText(')].join('\n');
vm.runInContext(code,context,{timeout:2000});
(async()=>{
 await context.exportFullDatabaseJson(); context.exportProjectJson();
 if(saved.length!==2)throw Error('Expected two backup exports');
 const targets=['backup-full.json','backup-project.json'];
 for(let i=0;i<2;i++) {
  const target=path.join(root,'parity/golden',targets[i]),bytes=Buffer.from(await saved[i].arrayBuffer());
  if(process.argv.includes('--check')) {if(!fs.readFileSync(target).equals(bytes))throw Error(`Backup fixture mismatch: ${targets[i]}`);}
  else fs.writeFileSync(target,bytes);
 }
 console.log('Checked two legacy backup exports with fixed, mocked dependencies.');
})().catch(e=>{console.error(e);process.exitCode=1;});
