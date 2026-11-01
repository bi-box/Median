'use strict';

// Host-side operation/time comparison. This measures the collector, not Android rendering.
const fs = require('node:fs');
const vm = require('node:vm');
const { performance } = require('node:perf_hooks');
const [beforePath, afterPath] = process.argv.slice(2);
if (!afterPath) throw new Error('expected before and after generated install scripts');

const ordinary = Array.from({ length: 20000 }, (_, i) => ({
  name: 'https://static.example/assets/chunk-' + i + '.js', initiatorType: 'script'
}));
const xhrRuns = 1000;

function fixture(script) {
  const counts = { urls: 0, headers: 0, xhrListeners: 0 };
  let callback;
  class MeasuredURL extends URL { constructor(...args) { super(...args); counts.urls++; } }
  class XHR {
    constructor() { this.listeners = []; this.responseURL = ''; }
    open(method, url) { this.responseURL = url; }
    send() {}
    addEventListener(type, listener) { if (type === 'load') { this.listeners.push(listener); counts.xhrListeners++; } }
    getResponseHeader() { counts.headers++; return 'video/mp4'; }
    finish() { for (const listener of this.listeners) listener(); }
  }
  function Observer(fn) { callback = fn; }
  Observer.prototype.observe = function() {};
  const context = {
    document: { addEventListener() {} }, location: { href: 'https://page.example/watch' },
    performance: { getEntriesByType() { return []; } }, PerformanceObserver: Observer,
    URL: MeasuredURL, XMLHttpRequest: XHR
  };
  context.window = context;
  vm.createContext(context);
  vm.runInContext(script, context);
  return { context, counts, resource(entries) { callback({ getEntries() { return entries; } }); } };
}

function measure(script, kind) {
  const probe = fixture(script);
  const start = performance.now();
  if (kind === 'ordinaryResources') probe.resource(ordinary);
  else {
    const request = new probe.context.XMLHttpRequest();
    for (let i = 0; i < xhrRuns; i++) {
      request.open('GET', 'https://cdn.example/opaque-' + i);
      request.send(); request.finish();
    }
  }
  return { ms: performance.now() - start, ...probe.counts, mediaEntries: probe.context.__medianMediaLog.length };
}

function summarize(script, kind) {
  measure(script, kind); // Warm up; report the median of five fresh documents.
  const runs = Array.from({ length: 5 }, () => measure(script, kind));
  runs.sort((a, b) => a.ms - b.ms);
  const middle = runs[2];
  return { ...middle, ms: Number(middle.ms.toFixed(3)), minMs: Number(runs[0].ms.toFixed(3)), maxMs: Number(runs[4].ms.toFixed(3)) };
}

const before = fs.readFileSync(beforePath, 'utf8');
const after = fs.readFileSync(afterPath, 'utf8');
const result = { runtime: process.version, ordinaryResources: ordinary.length, reusedXHRRequests: xhrRuns, rounds: 5, before: {}, after: {} };
for (const kind of ['ordinaryResources', 'reusedXHR']) {
  result.before[kind] = summarize(before, kind);
  result.after[kind] = summarize(after, kind);
}
console.log(JSON.stringify(result, null, 2));
