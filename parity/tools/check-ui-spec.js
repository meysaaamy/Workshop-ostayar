#!/usr/bin/env node
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),cp=require('node:child_process'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..'), file=path.join(root,'docs/legacy-ui-literals.json'), bytes=fs.readFileSync(file);
const spec=JSON.parse(bytes), sha=b=>crypto.createHash('sha256').update(b).digest('hex');
assert.equal(spec.source.apkSha256,sha(fs.readFileSync(path.join(root,'Stayar-v1_16.apk'))));
cp.execFileSync(process.execPath,[path.join(__dirname,'extract-ui-spec.js')]);
assert.equal(sha(bytes),sha(fs.readFileSync(file)));
const v=spec.variables;
assert.equal(v.NAV.value.length,14); assert.equal(v.OB_STEPS.value.length,12);
assert.deepEqual(v.OB_STEPS.value.slice(-2).map(x=>x.t),['گزارش‌ها و سنجش سلامت','تنظیمات و امنیت']);
assert.equal(Object.keys(v.CRUD.pages).length,7);
assert.deepEqual(v.CRUD.pages.users.fields.map(x=>x.label),['نام','نوع','عنوان / نقش','ضریب کارکرد اوستا','فعال']);
assert.ok(v.BASIS_WARN.value.cash.includes('مانده قابل تقسیم'));
const chapters=['TUTOR_STEPS','TUTOR_STEPS_CH2','TUTOR_STEPS_CH3'];
assert.deepEqual(chapters.map(k=>v[k].value.length),[15,7,6]);
for(const chapter of chapters) {
 const steps=v[chapter].value;
 assert.equal(new Set(steps.map(s=>s.id)).size,steps.length);
 for(const step of steps){assert.ok(step.title&&step.say&&step.aim?.dynamicSource&&step.done?.dynamicSource&&step.result?.dynamicSource);}
}
assert.deepEqual(v.TUTOR_CHAPTERS.value.map(c=>c.steps.dynamicSource),chapters);

console.log('Checked legacy UI literal source hash, reproducibility, navigation, walkthrough, 28 interactive steps and selected CRUD labels.');
