#!/usr/bin/env node
// Phase-zero mapping draft: sections are inferred from source location, not a parity claim.
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'../..');
const inventory=fs.readFileSync(path.join(root,'docs/legacy-function-inventory.md'),'utf8');
const rows=inventory.split('\n').filter(x=>x.startsWith('| ')&&!x.startsWith('| تابع')).map(l=>l.split('|').slice(1,-1).map(x=>x.trim())).filter(x=>x.length===5 && x[1]==='script' && /^\d+$/.test(x[2]));
if(rows.length!==283) throw Error(`Expected 283 top-level declarations, found ${rows.length}; review source changes first`);
const direct={attFraction:'attFraction',wageRateFor:'wageRateFor',allocate:'allocate',receiptShare:'receiptShare',compute:'compute',parseMoney:'parseMoney',cvDate:'cvDate',cvAttValue:'cvAttValue',clientValidate:'clientValidate',normalizeState:'normalizeState',sanityCheck:'health',csvCell:'csvCell'};
const sample=JSON.parse(fs.readFileSync(path.join(root,'parity/golden/legacy-sample.json')));
const counts=sample.cases.reduce((m,c)=>(m[c.fn]=(m[c.fn]||0)+1,m),{});
const implemented = {
 parseMoney:'domain/MoneyParser.parse', wageRateFor:'domain/WageRates.forDate',
 allocate:'domain/LargestRemainder.allocate', receiptShare:'domain/ReceiptShares.share (تاریخ معتبر)',
 attFraction:'domain/AttendanceMath.fraction (انواع پشتیبانی‌شده)', compute:'domain/FinancialEngine.compute (تاریخ معتبر)',
 csvCell:'data/LegacyCsv.cell (انواع پشتیبانی‌شده)', downloadCSV:'data/LegacyCsv.encode (صرفاً تولید بایت؛ بدون ذخیره)',
 exportFinancialReportCSV:'data/FinancialCsvReport.rows (محدود؛ بدون ذخیرهٔ فایل)',
 exportDetailedAuditCSV:'data/AuditCsvReport.rows (محدود؛ بدون ذخیرهٔ فایل)' 
};
const financial=new Set(['otFactor','attFraction','absPenalty','isStaffUser','attFr','buildWageIndex','wageRateFor','allocate','receiptShare','compute']);
const storage=new Set(['sanId','sanRef','sanDay','sanNum','sanText','sanKind','sanOpt','sanFix','sanitizeProject','normalizeState','loadState','NATIVE_STRIP','nativeJson','persistNow','saveState']);
const all=[];
function plan(name,line){
 if(financial.has(name))return 'domain: مدل و محاسبات (فاز ۱)';
 if(storage.has(name))return 'data: سازگاری و مخزن (فاز ۲)';
 if(name==='parseMoney'||name==='fa2en')return 'domain: ورودی و تبدیل عدد (فاز ۱)';
 if(name==='defaultSettings'||name==='newProject'||name==='liveUsers'||name==='userName')return 'domain/data: مدل و پروژه (فازهای ۱–۲)';
 if(name==='P'||name==='uid'||name==='num')return 'domain/data: زیرساخت مدل (فازهای ۱–۲)';
 if(name==='sanityCheck'||name==='cleanOrphans')return 'domain/app: سلامت و پاک‌سازی (فازهای ۱ و ۵)';
 if(line<686)return 'domain/app: تقویم یا نمایش تاریخ (فازهای ۱ و ۳)';
 if(line<718)return 'domain/app: مدل یا قالب‌بندی (فازهای ۱ و ۳)';
 if(line<978)return 'domain/app: مبلغ و قالب‌بندی (فازهای ۱ و ۳)';
 if(line<1050)return 'app/data: آموزش یا ذخیرهٔ وضعیت (فازهای ۲ و ۵)';
 if(line<1155)return 'data: ذخیره و سازگاری (فاز ۲)';
 if(line<1256)return 'app: صدا، لرزش یا دسترس‌پذیری (فاز ۶)';
 if(line<1470)return 'app: پوسته، فرم یا دیالوگ Compose (فاز ۳)';
 if(line<1585)return 'app: انتخاب تاریخ جلالی (فاز ۳)';
 if(line<1904)return 'app: حضور و غیاب (فاز ۳)';
 if(line<2059)return 'app: ناوبری و پوسته (فاز ۳)';
 if(line<2319)return 'app/data: فرم‌ها و عملیات رکورد (فازهای ۲–۳)';
 if(line<2412)return 'app: نمودار برداری (فاز ۳)';
 if(line<2521)return 'app: داشبورد و تسویه (فازهای ۳ و ۵)';
 if(line<2833)return 'app: گزارش، CSV و PDF (فاز ۴)';
 if(line<3023)return 'data/app: بکاپ، ورود یا قفل (فازهای ۲–۴)';
 if(line<3213)return 'data/app: پروژه و نمونه (فاز ۵)';
 if(line<4367)return 'app/data: آموزش تعاملی و sandbox (فاز ۵)';
 return 'app/domain: سلامت، آموزش یا تنظیمات (فاز ۵)';
}
let out='# ماتریس پیشنهادی ۲۸۳ تابع سطح اسکریپت\n\nاین نگاشت از محل بخش در سورس تولید شده و **طرح اولیه نیازمند بازبینی** است، نه ادعای همارزی یا نام کلاس نهایی. ده تابع دارای پیاده‌سازی جزئی و آزمون مستقیم Kotlin هستند؛ وضعیت بقیه نیازمند بازبینی است و برچسب انتظار به معنی بررسی کامل نیست. ستون آزمون شمار نمونه‌های مرجع قدیمی را نشان می‌دهد و همارزی همهٔ حالت‌ها را ثابت نمی‌کند. توابع محلی، متدهای شیء، انتساب‌ها و callbackها در فهرست جداگانه باقی‌اند.\n\n| تابع قدیمی | خط | مقصد پیشنهادی | آزمون مرجع فعلی | وضعیت |\n|---|---:|---|---|---|\n';
for(const r of rows){const [name,,line]=r, test=direct[name], n=test?counts[test]||0:0; const intended=implemented[name]||plan(name,+line), status=implemented[name]?'جزئی؛ آزمون مرجع Kotlin':'در انتظار پیاده‌سازی';all.push({name,line:+line,intended,test:test||null,count:n});out+=`| ${name} | ${line} | ${intended} | ${n?`${test}: ${n} نمونهٔ قدیمی`:'هنوز مشخص نشده'} | ${status} |\n`;}
fs.writeFileSync(path.join(root,'docs/legacy-function-matrix-draft.md'),out);
console.log(`Mapped ${all.length} declarations to tentative destinations; ${all.filter(x=>x.count).length} have directly named legacy cases.`);
