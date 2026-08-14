'use strict';
const fs = require('fs');
const vm = require('vm');
const payload = fs.readFileSync(process.argv[2], 'utf8');
const bridgeToken = '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef';
const calls = [];
const appended = [];
const storage = new Map();
const root = { appendChild(node) { appended.push(node); return node; } };
const document = {
  readyState: 'complete', body: {}, head: root, documentElement: root,
  createElement(name) {
    return { tagName: String(name).toUpperCase(), style: {}, setAttribute() {}, addEventListener() {},
      remove() {}, select() {}, textContent: '', value: '' };
  },
  addEventListener() {}, removeEventListener() {}, execCommand() { return true; }
};
const localStorage = {
  get length() { return storage.size; },
  getItem(key) { return storage.has(key) ? storage.get(key) : null; },
  setItem(key, value) { storage.set(key, String(value)); },
  removeItem(key) { storage.delete(key); },
  key(index) { return Array.from(storage.keys())[index] || null; }
};
const context = {
  document,
  localStorage,
  location: { protocol: 'https:', hostname: 'example.com', href: 'https://example.com/page' },
  prompt(message) {
    if (!message.startsWith('__MEDIAN_BRIDGE__')) return '';
    const request = JSON.parse(message.slice('__MEDIAN_BRIDGE__'.length));
    calls.push(request);
    if (request.a === 'getValue') return JSON.stringify({ ok: true, exists: false, v: 'null' });
    if (request.a === 'listValues') return JSON.stringify({ ok: true, v: [] });
    return JSON.stringify({ ok: true });
  },
  setTimeout(callback) { callback(); return 1; }, clearTimeout() {},
  addEventListener() {}, removeEventListener() {}, open() { return null; },
  URL, Promise, JSON, Object, Array, RegExp, String, Number, Date, Math,
  Uint8Array, ArrayBuffer, Blob, TextDecoder,
  atob(value) { return Buffer.from(value, 'base64').toString('binary'); },
  btoa(value) { return Buffer.from(value, 'binary').toString('base64'); },
  console
};
context.window = context;
context.self = context;
context.top = context;
vm.createContext(context);

function assert(value, message) { if (!value) throw new Error(message); }
(async () => {
  vm.runInContext(payload, context);
  assert(context.__medianRunCount === 1, 'document-start script did not execute once');
  assert(context.__medianTestGM && context.__medianTestGM.info.version === '2.3.0', 'modern GM runtime missing');
  assert(await context.__medianTestGM.getValue('missing', 42) === 42, 'missing-value default was not preserved');
  assert(await context.__medianTestGM.getResourceText('sample') === 'hello', 'resource text decoding failed');
  await context.__medianTestGM.setValue('x', 1);
  const dispatchers = Object.getOwnPropertyNames(context)
    .filter(name => name.startsWith('__medianDispatch_')).map(name => context[name]);
  const menuOwner = dispatchers.find(dispatcher => dispatcher.menus(bridgeToken).length === 1);
  assert(menuOwner, 'registered script command was not exposed');
  const menu = menuOwner.menus(bridgeToken)[0];
  assert(menuOwner.runMenu(bridgeToken, menu.id), 'script command did not run');
  assert(calls.some(call => call.a === 'xhr') && calls.some(call => call.a === 'setValue'), 'native bridge actions were not issued');
  const beforeCalls = calls.length;
  const beforeNodes = appended.length;
  vm.runInContext(payload, context);
  assert(context.__medianRunCount === 1 && calls.length === beforeCalls && appended.length === beforeNodes,
    'OEM fallback marker allowed duplicate execution');
  console.log('Userscript runtime behavior passed');
})().catch(error => { console.error(error); process.exitCode = 1; });
