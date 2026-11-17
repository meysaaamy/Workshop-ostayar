#!/usr/bin/env node
// Test legacy web control flow with a fake storage callback; this is not a Room or SAF integration test.
const assert=require('node:assert/strict'), vm=require('node:vm');
const {src}=require('./legacy-engine');
const start=src.indexOf('function saveRecoverySnapshot(reason) {'),end=src.indexOf('const tsText = ',start);
assert.ok(start>=0&&end>start,'missing legacy backup functions');
class FakeDate extends Date { static now(){ return FakeDate.time; } }
FakeDate.time=1_000_000;
const env={recovery:{json:'old'},snaps:[],intervalMin:15,last:0};
const ctx=vm.createContext({Date:FakeDate,ENV:env,S:{revision:1},saveBlocked:false,saveOk:false,toasts:[],
 saveEnv:()=>ctx.saveOk,toast:(...args)=>ctx.toasts.push(args)});
vm.runInContext(src.slice(start,end)+'\nglobalThis.api={ensureSafetyCopy,autoBackupTick};',ctx,{timeout:2000});
const old=env.recovery;
assert.equal(ctx.api.ensureSafetyCopy('destructive action'),false);
assert.strictEqual(env.recovery,old,'failed storage must leave the prior recovery in memory');
assert.equal(ctx.toasts.length,1);
ctx.saveOk=true;
assert.equal(ctx.api.ensureSafetyCopy('destructive action'),true);
assert.equal(env.recovery.reason,'destructive action');
assert.equal(JSON.parse(env.recovery.json).revision,1);
ctx.api.autoBackupTick(false);assert.equal(env.snaps.length,1);
ctx.api.autoBackupTick(false);assert.equal(env.snaps.length,1,'interval must prevent another snapshot');
FakeDate.time+=15*60000;ctx.api.autoBackupTick(false);assert.equal(env.snaps.length,1,'unchanged state must not create another snapshot');
for(let revision=2;revision<=5;revision++){ctx.S.revision=revision;ctx.api.autoBackupTick(true);}
assert.equal(env.snaps.length,3);
assert.deepEqual(Array.from(env.snaps,x=>JSON.parse(x.json).revision),[3,4,5]);
// Execute replaceWholeState itself with mocked validation/storage/UI dependencies.
const a=src.indexOf('function replaceWholeState(ns, reason) {'),b=src.indexOf('async function exportFullDatabaseJson()',a);
assert.ok(a>=0&&b>a,'missing replaceWholeState');
const previous={version:'old'},replacement={version:'new'};
const sandbox={S:previous,clientValidate:()=>'',nativeDryRun:()=>'',reportImportErrors:()=>{},
 window:{saveFlush:()=>true},safetyOk:false,ensureSafetyCopy:()=>sandbox.safetyOk,
 persistOk:true,persistCalls:0,persistNow:()=>{sandbox.persistCalls++;return sandbox.persistOk;},
 toast:()=>{},applyTheme:()=>{},closeAllModals:()=>{},render:()=>{},dirty:true,saveBlocked:false,loadError:''};
const ctx2=vm.createContext(sandbox);
vm.runInContext(src.slice(a,b)+'\nglobalThis.replace=replaceWholeState;',ctx2,{timeout:2000});
assert.equal(ctx2.replace(replacement,'test'),false);assert.strictEqual(ctx2.S,previous);assert.equal(ctx2.persistCalls,0);
ctx2.safetyOk=true;ctx2.persistOk=false;
assert.equal(ctx2.replace(replacement,'test'),false);assert.strictEqual(ctx2.S,previous);assert.equal(ctx2.persistCalls,1);
ctx2.persistOk=true;
assert.equal(ctx2.replace(replacement,'test'),true);assert.strictEqual(ctx2.S,replacement);assert.equal(ctx2.persistCalls,2);
console.log('Verified mocked whole-state replacement stops if safety copy fails and restores old memory state if persistence fails.');
console.log('Verified legacy web recovery-failure handling and in-memory snapshot interval/dedup/retention with mocked storage.');
