#!/usr/bin/env node
// Extract source literals from legacy UI configuration without executing UI code.
const fs=require('node:fs'), cp=require('node:child_process'), path=require('node:path'), crypto=require('node:crypto'), acorn=require('acorn');
const root=path.resolve(__dirname,'../..'), apk=path.join(root,'Stayar-v1_16.apk');
const html=cp.execFileSync('unzip',['-p',apk,'assets/index.html'],{encoding:'utf8',maxBuffer:4e6});
const needed=new Set(['NAV','BOTTOM','OB_STEPS','BASIS_INFO','BASIS_WARN','CRUD','TUTOR_STEPS','TUTOR_STEPS_CH2','TUTOR_STEPS_CH3','TUTOR_CHAPTERS']);
const output={source:{apkSha256:crypto.createHash('sha256').update(fs.readFileSync(apk)).digest('hex'),htmlSha256:crypto.createHash('sha256').update(html).digest('hex')},note:'Static source extraction only. Dynamic expressions are recorded as JavaScript source, not evaluated.',variables:{}};
for(const match of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script\s*>/gi)) {
 if(/\bsrc\s*=/.test(match[1]) || !match[2].trim()) continue;
 const text=match[2], offset=html.slice(0,match.index+match[0].indexOf(text)).split('\n').length-1;
 const ast=acorn.parse(text,{ecmaVersion:'latest',locations:true,sourceType:'script'});
 function fieldKey(prop) {return prop.key.type==='Identifier'?prop.key.name:prop.key.value;}
 function value(n) {
  if(!n) return null;
  if(n.type==='Literal' && !n.regex) return n.value;
  if(n.type==='ArrayExpression') return n.elements.map(value);
  if(n.type==='ObjectExpression' && n.properties.every(p=>p.type==='Property' && !p.computed && p.kind==='init')) return Object.fromEntries(n.properties.map(p=>[fieldKey(p),value(p.value)]));
  return {dynamicSource:text.slice(n.start,n.end)};
 }
 for(const statement of ast.body) if(statement.type==='VariableDeclaration') for(const decl of statement.declarations) {
  const name=decl.id.name;if(!needed.has(name)) continue;
  if(output.variables[name]) throw Error(`Duplicate ${name}`);
  if(name==='CRUD') {
   const pages={};
   for(const page of decl.init.properties) {
    const name=fieldKey(page), props=Object.fromEntries(page.value.properties.map(p=>[fieldKey(p),p.value]));
    const fields=props.fields;
    if(!fields || fields.type!=='ArrowFunctionExpression'||fields.body.type!=='ArrayExpression') throw Error(`Unsupported fields for ${name}`);
    pages[name]={line:offset+page.loc.start.line,title:value(props.title),single:value(props.single),intro:value(props.intro),fields:value(fields.body),validate:value(props.validate),build:value(props.build)};
   }
   output.variables.CRUD={line:offset+decl.loc.start.line,pages};
  } else output.variables[name]={line:offset+decl.loc.start.line,value:value(decl.init)};
 }
}
for(const key of needed) if(!output.variables[key]) throw Error(`Missing ${key}`);
const out=path.join(root,'docs/legacy-ui-literals.json');
fs.writeFileSync(out,JSON.stringify(output,null,2)+'\n');
console.log(`Extracted ${output.variables.NAV.value.length} navigation entries, ${output.variables.OB_STEPS.value.length} guide steps and ${Object.keys(output.variables.CRUD.pages).length} CRUD configs; ${['TUTOR_STEPS','TUTOR_STEPS_CH2','TUTOR_STEPS_CH3'].map(k=>output.variables[k].value.length).join('/')} interactive steps.`);
