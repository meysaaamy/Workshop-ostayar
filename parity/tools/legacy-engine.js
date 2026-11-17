// Load legacy JavaScript directly from the checked-in APK; use only trusted APKs.
const fs = require('node:fs');
const cp = require('node:child_process');
const vm = require('node:vm');
const path = require('node:path');
const root = path.resolve(__dirname, '../..');
const apk = path.join(root, 'Stayar-v1_16.apk');
const src = cp.execFileSync('unzip', ['-p', apk, 'assets/index.html'], {encoding:'utf8', maxBuffer: 4e6});
function between(start, end) {
  const a = src.indexOf(start), b = src.indexOf(end, a + start.length);
  if (a < 0 || b < 0) throw Error(`Source markers missing: ${start} / ${end}`);
  return src.slice(a, b);
}
const code = [
  between('window.jalaali = (function () {', 'function defaultSettings() {'),
  between('function defaultSettings() {', 'function newProject('),
  'const LS = {get: () => null, set: () => true};',
  between('const ONBOARDING_STATUSES = ', '/* ---------- input hardening:'),
  between('const SAN = ', 'function loadState() {'),
  between('const liveUsers = ', '/* ---- attendance value'),
  between('function otFactor(p) {', 'function attAbsTip('),
  between('function buildWageIndex(p) {', '/* ==========================================================================' + '\n   Money to Persian words'),
  between('const MAX_MONEY = ', 'const moneyVal = '),
  between('const CV_ID = ', '/* Ask the native StateValidator'),
  between('function inactiveAttCount(prefix) {', 'function renderMatrix() {'),
  between('function sanityCheck() {', 'function cleanOrphans() {'),
  between('function csvCell(v) {', 'function downloadCSV('),
  'globalThis.engine = {attFraction, attFr, wageRateFor, allocate, receiptShare, compute, parseMoney, cvDate, cvAmount, cvAttValue, clientValidate, normalizeState, csvCell, health: p => { S = {projects: [p], activeProjectId: p.id}; return sanityCheck(); }, jalaali: window.jalaali};'
].join('\n');
const context = vm.createContext({window:{}});
vm.runInContext(code, context, {timeout: 2000, filename:'extracted-legacy-engine.js'});
const e = context.engine;
module.exports = {e, apk, src, root, context};
