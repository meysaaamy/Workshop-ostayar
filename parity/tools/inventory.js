#!/usr/bin/env node
// Inventory named function declarations and named function-valued variables in inline scripts.
const fs = require('node:fs');
const cp = require('node:child_process');
const path = require('node:path');
const acorn = require('acorn');
const root = path.resolve(__dirname, '../..');
const html = cp.execFileSync('unzip',['-p',path.join(root,'Stayar-v1_16.apk'),'assets/index.html'],{encoding:'utf8',maxBuffer:4e6});
const rows=[];
for (const match of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script\s*>/gi)) {
  if (/\bsrc\s*=/.test(match[1]) || !match[2].trim()) continue;
  const lineOffset = html.slice(0, match.index + match[0].indexOf(match[2])).split('\n').length - 1;
  const ast=acorn.parse(match[2],{ecmaVersion:'latest',locations:true,sourceType:'script'});
  function visit(node, scope) {
    if (!node || typeof node !== 'object') return;
    let next = scope;
    if (node.type === 'FunctionDeclaration' && node.id) {
      rows.push({name:node.id.name,scope,line:lineOffset+node.loc.start.line});
      next = scope + '/' + node.id.name;
    } else if (node.type === 'VariableDeclarator' && node.id.type === 'Identifier' && node.init && ['ArrowFunctionExpression','FunctionExpression'].includes(node.init.type)) {
      rows.push({name:node.id.name,scope,line:lineOffset+node.loc.start.line});
      next = scope + '/' + node.id.name;
    } else if (['FunctionExpression','ArrowFunctionExpression'].includes(node.type)) next = scope + '/(anonymous)';
    for (const [key,value] of Object.entries(node)) {
      if (key === 'loc' || key === 'start' || key === 'end') continue;
      if (Array.isArray(value)) value.forEach(v=>visit(v,next));
      else if (value && typeof value === 'object') visit(value,next);
    }
  }
  visit(ast,'script');
}
const selected = new Set(['attFraction','allocate','parseMoney','receiptShare','wageRateFor','compute','toGregorian']);
let out='# فهرست اولیهٔ توابع نام‌دار JavaScript\n\nخودکار از اسکریپت‌های درون `assets/index.html` استخراج شده است؛ شمارهٔ خط مربوط به همان فایل APK است. این فهرست اعلان تابع و متغیر دارای تابع را، از جمله توابع محلی، ثبت می‌کند؛ متدهای شیء، انتساب‌ها و callbackهای بی‌نام الزاماً پوشش ندارند. این ماتریس همارزی کامل نیست و هیچ معادل Kotlin هنوز پیاده‌سازی نشده است.\n\n| تابع | محدوده | خط | معادل جدید و آزمون | وضعیت |\n|---|---|---:|---|---|\n';
for(const r of rows) out+=`| ${r.name} | ${r.scope} | ${r.line} | ${selected.has(r.name) ? 'نمونهٔ golden قدیمی؛ Kotlin ندارد' : 'مشخص نشده'} | بررسی‌نشده |\n`;
fs.writeFileSync(path.join(root,'docs/legacy-function-inventory.md'),out);
console.log(`Inventoried ${rows.length} named declarations (${rows.filter(r=>r.scope==='script').length} top-level).`);
