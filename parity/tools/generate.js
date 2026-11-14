#!/usr/bin/env node
// Execute selected legacy functions from the checked-in APK, not a reimplementation.
const fs = require('node:fs');
const cp = require('node:child_process');
const crypto = require('node:crypto');
const path = require('node:path');
const { e, apk, src, root } = require('./legacy-engine');
let seed = 0x51a7a2;
function rand(n) { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed % n; }
const cases = [];
function add(fn, args, result) { cases.push({fn, args, result}); }
for (const ot of [1,1.25,1.5,2,1.333]) for (const v of [1,0.5,0,{t:'h',h:0.5},{t:'h',h:8},{t:'h',h:8.5},{t:'h',h:24},{t:'h',h:24.5},{t:'h',h:-1}]) add('attFraction',[v,ot],e.attFraction(v,ot));
for (const raw of ['', '۱۲۳٬۴۵۶٫۷۸', '١٢٣،٤٥٦', '0000', '-23', '9999999999999', '.50', ' 1,234 ']) add('parseMoney',[raw],e.parseMoney(raw));
for (let i=0;i<300;i++) { const total=rand(200001)-100000, weights=Array.from({length:1+rand(5)},()=>rand(101)); add('allocate',[total,weights],e.allocate(total,weights)); }
for (const [total,weights] of [[-2.5,[1,1,1]],[2.5,[1,1,1]],[-5,[1,1]],[5,[0,0]],[5,[]],[999999999999,[1,1,1]],[0,[1,2]]]) add('allocate',[total,weights],e.allocate(total,weights));
for (let i=0;i<500;i++) {
 const v={t:'h',h:(rand(2600)-50)/100};
 const overtimeFactor=[1,1.25,1.5,2,1.333,1.001][rand(6)];
 add('attFraction',[v,overtimeFactor],e.attFraction(v,overtimeFactor));
}
// Fractional weight allocation at realistic and near-ceiling magnitudes.
for (let i=0;i<800;i++) {
 const total=(rand(2)?-1:1)*(i%4===0?999999999999-rand(10000):rand(1000000));
 const weights=Array.from({length:1+rand(6)},()=>rand(10000)/1000);
 add('allocate',[total,weights],e.allocate(total,weights));
}
for (const year of [1398,1399,1400,1402,1403,1404]) for (const [month,day] of [[1,1],[6,31],[12,29],[12,30]]) if (e.jalaali.isValidJalaaliDate(year,month,day)) add('toGregorian',[year,month,day],e.jalaali.toGregorian(year,month,day));
for (const rg of [{from:'',to:''},{from:'1403/01/01',to:'1403/01/02'},{from:'1403/01/03',to:'1403/01/05'},{from:'1403/01/06',to:''}]) { const r={dateFrom:'1403/01/01',dateTo:'1403/01/05',amount:101}; add('receiptShare',[r,rg],e.receiptShare(r,rg)); }
for (const [r,rg] of [
 [{dateFrom:'bad',dateTo:'bad',amount:101},{from:'',to:''}],
 [{dateFrom:'1403/01/05',dateTo:'1403/01/01',amount:101},{from:'',to:''}],
 [{dateFrom:'1403/01/01',dateTo:'1403/01/05',amount:101},{from:'bad',to:''}],
 [{dateFrom:'1403/01/01',dateTo:'1403/01/05',amount:0},{from:'',to:''}]
]) add('receiptShare',[r,rg],e.receiptShare(r,rg));
// Date range partitioning with amounts near the cap, both signs and open bounds.
for (let i=0;i<500;i++) {
 const day=n=>'1403/01/'+String(n).padStart(2,'0');
 const start=1+rand(20), end=start+rand(32-start);
 const lo=1+rand(31), hi=lo+rand(32-lo);
 const r={dateFrom:day(start),dateTo:day(end),amount:(rand(2)?-1:1)*(i%3===0?999999999999-rand(1000):rand(1000000))};
 const range={from:i%4===0?'':day(lo),to:i%5===0?'':day(hi)};
 add('receiptShare',[r,range],e.receiptShare(r,range));
}
const p = {id:'p',settings:{overtimeFactor:1.25,absentPenalty:0.5,profitBasis:'cash'},users:[{id:'m',name:'اوستا',type:'master',active:true,workFactor:1},{id:'s',name:'شاگرد',type:'apprentice',active:true,workFactor:1}],wages:[{userId:'s',rate:101,effectiveDate:'1403/01/02'}],attendance:{'1403/01/01':{m:1,s:0.5}},receipts:[{dateFrom:'1403/01/01',dateTo:'1403/01/05',amount:501}],expenses:[],payouts:[],transfers:[],adjustments:[]};
for (const basis of ['cash','commitment']) { const project=JSON.parse(JSON.stringify(p)); project.settings.profitBasis=basis; add('compute',[null,project],e.compute(null,project)); }
for (const date of ['1403/01/01','1403/01/02','1403/01/03']) add('wageRateFor',['s',date,null,p],e.wageRateFor('s',date,null,p));
// Wage selection: unsorted rows, repeated effective dates, missing users and dates before/after rates.
for (let i=0;i<300;i++) {
 const wages=[]; const count=rand(7);
 for(let j=0;j<count;j++) wages.push({id:'w'+j,userId:rand(3)===0?'other':'s',rate:rand(1000000),effectiveDate:'1403/01/'+String(1+rand(15)).padStart(2,'0')});
 const date='1403/01/'+String(1+rand(20)).padStart(2,'0'), project={wages};
 add('wageRateFor',['s',date,null,project],e.wageRateFor('s',date,null,project));
}
// Scenario coverage is intentionally explicit so every fixture is reviewable.
const rich = JSON.parse(JSON.stringify(p));
rich.users.push({id:'m2',name:'اوستای دوم',type:'master',active:true,workFactor:1.5}, {id:'inactive',name:'اوستای غیرفعال',type:'master',active:false,workFactor:1});
rich.wages.push({userId:'s',rate:205,effectiveDate:'1403/01/04'});
rich.attendance = {'1403/01/01':{m:0,m2:1,s:1,inactive:1},'1403/01/04':{m:1,m2:{t:'h',h:8.5},s:{t:'h',h:24}},'1403/01/05':{m:0.5,s:0}};
rich.receipts = [{dateFrom:'1403/01/01',dateTo:'1403/01/05',amount:999999999999},{dateFrom:'1403/01/01',dateTo:'1403/01/01',amount:99,active:false}];
rich.expenses = [{date:'1403/01/01',amount:31,payerId:'m'},{date:'1403/01/04',amount:21,payerId:'shared'}, {date:'1403/01/04',amount:100,active:false}];
rich.payouts = [{date:'1403/01/04',amount:20,userId:'s'}, {date:'1403/01/04',amount:10,userId:'m'}];
rich.transfers = [{date:'1403/01/04',amount:100,kind:'peer',fromId:'m',toId:'m2'},{date:'1403/01/04',amount:40,kind:'advance',toId:'m'}];
rich.adjustments = ['bonus','penalty','assistance','settle'].flatMap((kind,i)=>[{date:'1403/01/04',amount:3+i,kind,userId:'m'},{date:'1403/01/04',amount:4+i,kind,userId:'s'}]);
for (const basis of ['cash','commitment']) for (const penalty of [0,0.25,0.5,1,3]) for (const overtimeFactor of [1,1.25,1.5,2,1.333]) {
 const project=JSON.parse(JSON.stringify(rich)); Object.assign(project.settings,{profitBasis:basis,absentPenalty:penalty,overtimeFactor});
 for(const range of [null,{from:'1403/01/04',to:'1403/01/04'}]) add('compute',[range,project],e.compute(range,project));
}
for (let i=0;i<100;i++) {
 const project=JSON.parse(JSON.stringify(rich)); project.settings.profitBasis=i%2?'cash':'commitment';
 project.attendance['1403/01/04'].s={t:'h',h:(1+rand(48))/2};
 project.receipts[0].amount=rand(1000000); project.expenses[0].amount=rand(10000);
 add('compute',[null,project],e.compute(null,project));
}
const noWork=JSON.parse(JSON.stringify(rich)); noWork.attendance={}; noWork.receipts[0].amount=10; add('compute',[null,noWork],e.compute(null,noWork));
const negative=JSON.parse(JSON.stringify(p)); negative.receipts=[]; negative.attendance={'1403/01/01':{m:1}}; negative.transfers=[{date:'1403/01/01',kind:'advance',toId:'m',amount:100}]; add('compute',[null,negative],e.compute(null,negative));
for (const v of [{t:'h',h:24.5},{t:'h',h:-1},{t:'h',h:0.5}]) {
 const project=JSON.parse(JSON.stringify(p)); project.attendance={'1403/01/01':{m:1,s:v}};
 add('compute',[null,project],e.compute(null,project));
}
const bounded=JSON.parse(JSON.stringify(rich));
add('compute',[{from:'1403/01/01',to:'1403/01/05'},bounded],e.compute({from:'1403/01/01',to:'1403/01/05'},bounded));
// Raw legacy string attendance is accepted by the engine, but counted as hourly before normalization.
for (const raw of ['1','0.5','0']) {
 const project=JSON.parse(JSON.stringify(p)); project.attendance={'1403/01/01':{m:raw,s:raw}};
 add('compute',[null,project],e.compute(null,project));
}
// Vary independent record sets, active/deleted users, work factors and dates.
for (let i=0;i<160;i++) {
 const project=JSON.parse(JSON.stringify(rich));
 project.users.push({id:'o',name:'کارگر',type:'worker',active:i%4!==0,workFactor:1});
 project.users[0].workFactor=[0,0.1,0.75,1,1.333][i%5];
 project.users[2].workFactor=[0.5,1,1.25,2][i%4];
 if (i%7===0) project.users[2].deletedAt=123;
 if (i%3===0) project.users[0].active=false;
 project.settings.profitBasis=i%2?'cash':'commitment';
 project.settings.overtimeFactor=[1,1.333,1.5][i%3];
 project.settings.absentPenalty=[0,0.5,1.25][i%3];
 project.attendance['1403/01/02']={m:{t:'h',h:(i%48)/2},m2:i%2?0:0.5,s:1,o:{t:'h',h:1.25}};
 project.wages.push({userId:'o',rate:50+i,effectiveDate:'1403/01/01'});
 project.receipts[0].amount=(i*18971)%1000000;
 project.payouts.push({date:'1403/01/02',amount:i*2,userId:'o'});
 project.expenses.push({date:'1403/01/02',amount:i,payerId:i%2?'o':'m2'});
 project.transfers.push({date:'1403/01/02',amount:i,kind:'peer',fromId:'m2',toId:'m'});
 const range=i%3===0?{from:'1403/01/02',to:'1403/01/04'}:null;
 add('compute',[range,project],e.compute(range,project));
}
// Legacy browser-side validation behavior, before normalization or native dry-run.
for (const date of ['1199/12/29','1200/01/01','1399/12/30','1402/12/30','1403/12/30','1700/12/29','1701/01/01','1403/1/01']) add('cvDate',[date],e.cvDate(date));
for (const value of [0,0.5,1,{t:'h',h:0},{t:'h',h:0.25},{t:'h',h:1.25},{t:'h',h:24},{t:'h',h:24.5},{t:'h',h:-1},'1']) add('cvAttValue',[value],e.cvAttValue(value));
const validState={projects:[{id:'p',name:'پروژه',settings:{},users:[],wages:[],expenses:[],receipts:[],payouts:[],transfers:[],adjustments:[],categories:[],attendance:{}}]};
for(const entry of [null,{id:'e',date:'1403/01/01',amount:-1},{id:'e',date:'1403/01/01',amount:999999999999},{id:'e',date:'1403/01/01',amount:1000000000000},{id:'e',date:'1402/12/30',amount:1}]) {
 const state=JSON.parse(JSON.stringify(validState)); if(entry) state.projects[0].expenses.push(entry);
 add('clientValidate',[state],e.clientValidate(state));
}
for (const value of [{t:'h',h:1.25},{t:'h',h:0},{t:'h',h:24.5}]) {
 const state=JSON.parse(JSON.stringify(validState)); state.projects[0].attendance={'1403/01/01':{'u':value}};
 add('clientValidate',[state],e.clientValidate(state));
}
for (const [collection,record] of [
 ['users',{id:'u',name:'الف',type:'invalid',workFactor:1}],
 ['users',{id:'bad id',name:'الف',type:'master',workFactor:1}],
 ['users',{id:'u',name:'الف',type:'master',workFactor:-1}],
 ['transfers',{id:'t',date:'1403/01/01',amount:1,kind:'bad'}],
 ['adjustments',{id:'a',date:'1403/01/01',amount:1,kind:'bad'}],
 ['receipts',{id:'r',dateFrom:'1403/01/02',dateTo:'1403/01/01',amount:1}],
 ['wages',{id:'w',userId:'u',effectiveDate:'1403/01/01',rate:1000000000000}]
]) {
 const state=JSON.parse(JSON.stringify(validState)); state.projects[0][collection].push(record);
 add('clientValidate',[state],e.clientValidate(state));
}
// Normalization inputs intentionally contain stable IDs and timestamps; missing IDs use time/random in the old app.
for (const [version,native,overtime] of [[2,false,1.4],[2,true,1.4],[3,false,1.4],[null,false,1.4],[2,false,1.5]]) {
 const state={projects:[{id:'p',name:'پروژه',createdAt:100,settings:{overtimeFactor:overtime}}],activeProjectId:'missing'};
 if(version!==null) state.version=version;
 const options=native?{native:true}:{};
 const input=JSON.parse(JSON.stringify(state));
 add('normalizeState',[input,options],e.normalizeState(state,options));
}
const sandbox={version:3,activeProjectId:'demo_quest_1',projects:[{id:'demo_quest_1',name:'تمرین',createdAt:100,settings:{},attendance:{'1403/1/1':{u:'0.5'}}}],onboarding:{status:'in_quest',questStep:2,sandboxProjectId:'demo_quest_1',chapter:2}};
add('normalizeState',[JSON.parse(JSON.stringify(sandbox)),{}],e.normalizeState(sandbox,{}));
const quarterHour=JSON.parse(JSON.stringify(p));
quarterHour.attendance={'1403/01/01':{m:1,s:{t:'h',h:1.25}}};
add('compute',[null,quarterHour],e.compute(null,quarterHour));
// CSV injection guard, quoting and distinction between negative numbers and strings.
for (const value of [null,'',0,-10,'-10','=SUM(A1:A2)','+cmd','@x','\talert','\rvalue','نام، نام','a,b','a"b','x\ny','=1,2','سلام']) add('csvCell',[value],e.csvCell(value));
// Each health scenario changes one aspect of the same complete project; other rules may also report.
const healthProject=JSON.parse(JSON.stringify(p)); healthProject.categories=[];
const mutations = [
 x=>{},
 x=>{x.users[0].active=false;},
 x=>{x.wages=[];},
 x=>{x.attendance['1403/01/01'].s={t:'h',h:24.5};},
 x=>{x.users[1].active=false;},
 x=>{x.attendance['1403/01/01'].ghost=1;},
 x=>{x.expenses.push({date:'1403/01/01',amount:1,description:'بدون دسته'});},
 x=>{x.expenses.push({date:'bad',amount:0,description:'اشتباه'});},
 x=>{x.payouts.push({date:'1403/01/01',amount:1000,userId:'s'});},
 x=>{delete x.attendance['1403/01/01'].m;}
];
for (const mutate of mutations) { const project=JSON.parse(JSON.stringify(healthProject)); mutate(project); add('health',[project],e.health(project)); }
// Seeded money text fuzz cases, including Persian/Arabic digits, punctuation and JS whitespace.
const moneyChars=Array.from('0123456789۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩٫٬،., -+abc\u00a0\u2007\u202f\ufeff');
for(let i=0;i<1200;i++) {
 let raw=''; const count=rand(60); for(let j=0;j<count;j++) raw+=moneyChars[rand(moneyChars.length)];
 add('parseMoney',[raw],e.parseMoney(raw));
}
add('parseMoney',[null],e.parseMoney(null));
// Legacy IEEE-754 rounds this daily wage down despite the exact decimal being 5999.5.
const floatingBoundary=JSON.parse(JSON.stringify(p));
floatingBoundary.settings.overtimeFactor=1.333;
floatingBoundary.wages=[{userId:'s',rate:4000,effectiveDate:'1403/01/01'}];
floatingBoundary.attendance={'1403/01/01':{m:1,s:{t:'h',h:11}}};
add('compute',[null,floatingBoundary],e.compute(null,floatingBoundary));
// More field-validator cases from the actual old web functions.
for (const v of [null,true,false,'1','۱۲۳',0,0.1,0.5,1,999999999999,999999999999.5,1000000000000,-1]) add('cvAmount',[v],e.cvAmount(v));
for (let i=0;i<500;i++) {
 const v=(rand(300001)-100000)/100;
 add('cvAmount',[v],e.cvAmount(v));
 const h=(rand(30001)-3000)/1000;
 const a=i%3===0?h:i%3===1?{t:'h',h}:{t:'x',h};
 add('cvAttValue',[a],e.cvAttValue(a));
}
for (let i=0;i<300;i++) {
 const y=1198+rand(505),m=rand(15),d=rand(34);
 const v=`${String(y).padStart(4,'0')}/${String(m).padStart(2,'0')}/${String(d).padStart(2,'0')}`;
 add('cvDate',[v],e.cvDate(v));
}
// Client validator first-error order on realistic JSON project shapes.
const validatorBase={projects:[{id:'p',name:'پروژه',settings:{},users:[{id:'u',name:'علی',type:'apprentice',workFactor:1}],categories:[{id:'c',name:'مصالح'}],wages:[{id:'w',rate:101,effectiveDate:'1403/01/01'}],expenses:[{id:'e',date:'1403/01/01',amount:2}],receipts:[{id:'r',dateFrom:'1403/01/01',dateTo:'1403/01/02',amount:4}],payouts:[{id:'b',date:'1403/01/01',amount:2}],transfers:[{id:'t',date:'1403/01/01',amount:2,kind:'peer'}],adjustments:[{id:'a',date:'1403/01/01',amount:2,kind:'bonus'}],attendance:{'1403/01/01':{u:{t:'h',h:1.25}}}}]};
const validatorMutations=[
 s=>s.projects=[], s=>s.projects.push(JSON.parse(JSON.stringify(s.projects[0]))),s=>s.projects[0].id='?',s=>s.projects[0].name='  ',
 s=>s.projects[0].settings.rangeFrom='1402/12/30',s=>s.projects[0].settings.overtimeFactor=0,s=>s.projects[0].settings.absentPenalty=3.1,
 s=>s.projects[0].settings.profitBasis='other',s=>s.projects[0].settings.theme='blue',s=>s.projects[0].settings.locked='true',
 s=>s.projects[0].users[0].id='!',s=>s.projects[0].users.push(JSON.parse(JSON.stringify(s.projects[0].users[0]))),
 s=>s.projects[0].users[0].name='\uFEFF',s=>s.projects[0].users[0].role='x'.repeat(201),s=>s.projects[0].users[0].type='unknown',
 s=>delete s.projects[0].users[0].type,s=>s.projects[0].users[0].workFactor=-1,s=>s.projects[0].users[0].active='false',
 s=>s.projects[0].categories[0].name='',s=>s.projects[0].wages[0].rate=-1,s=>s.projects[0].wages[0].effectiveDate='wrong',
 s=>s.projects[0].expenses[0].date='wrong',s=>s.projects[0].expenses[0].amount=-1,s=>s.projects[0].expenses[0].description='x'.repeat(2001),
 s=>s.projects[0].receipts[0].dateTo='wrong',s=>s.projects[0].receipts[0].dateFrom='1403/01/03',s=>s.projects[0].receipts[0].amount=-1,
 s=>s.projects[0].payouts[0].amount=-1,s=>s.projects[0].transfers[0].kind='other',s=>s.projects[0].adjustments[0].kind='other',
 s=>s.projects[0].attendance={'1402/12/30':{}},s=>s.projects[0].attendance={'1403/01/01':[]},s=>s.projects[0].attendance={'1403/01/01':{u:'1'}},
 s=>s.projects[0].users=[null],s=>s.projects[0].users=[[]],s=>s.projects[0].expenses[0].description={a:1},
 s=>s.projects[0].settings.overtimeFactor=1.0000000000000001,s=>s.projects[0].settings.absentPenalty=-0.5,
 s=>s.projects[0].users[0].workFactor=0.1,s=>s.projects[0].payouts[0].amount=0.25,
];
for(const mutate of [()=>{},...validatorMutations]) {
 const state=JSON.parse(JSON.stringify(validatorBase));mutate(state);add('clientValidate',[state],e.clientValidate(state));
}
// Combine validator mutations to check ordering and diverse supported JSON scalar fields.
const fieldValues=[null,true,false,'','0','  ','valid','\uFEFF',0,0.5,1,-1,3,5,1000000000000,'x'.repeat(201),['a','b'],{k:'v'}];
for(let i=0;i<400;i++) {
 const state=JSON.parse(JSON.stringify(validatorBase));const p=state.projects[0];
 const v=()=>fieldValues[rand(fieldValues.length)];
 switch(i%12) {
  case 0:p.id=v();p.name=v();break;
  case 1:p.settings.overtimeFactor=v();p.settings.absentPenalty=v();break;
  case 2:p.settings.profitBasis=v();p.settings.theme=v();p.settings.locked=v();break;
  case 3:p.users[0].name=v();p.users[0].type=v();break;
  case 4:p.users[0].role=v();p.users[0].workFactor=v();break;
  case 5:p.users[0].active=v();p.users[0].main=v();break;
  case 6:p.expenses[0].description=v();p.expenses[0].amount=v();break;
  case 7:p.receipts[0].amount=v();p.receipts[0].description=v();break;
  case 8:p.transfers[0].kind=v();p.adjustments[0].kind=v();break;
  case 9:p.wages[0].rate=v();p.categories[0].name=v();break;
  case 10:p.attendance['1403/01/01'].u=v();break;
  case 11:p.settings.rangeFrom=v();p.settings.rangeTo=v();break;
 }
 add('clientValidate',[state],e.clientValidate(state));
}
const output = {source:{apkSha256:crypto.createHash('sha256').update(fs.readFileSync(apk)).digest('hex'),htmlSha256:crypto.createHash('sha256').update(src).digest('hex')}, seed:'0x51a7a2',cases};
const out = path.join(root,'parity/golden/legacy-sample.json');
fs.writeFileSync(out,JSON.stringify(output,null,2)+'\n');
console.log(`Wrote ${cases.length} legacy cases to ${path.relative(root,out)}`);
