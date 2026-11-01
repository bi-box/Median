'use strict';
const assert = require('node:assert/strict');
const vm = require('node:vm');
const { spawnSync } = require('node:child_process');

const [java, classes] = process.argv.slice(2);
if (!java || !classes) throw new Error('expected Java executable and compiled test classes directory');
const exported = spawnSync(java, ['-cp', classes, 'com.xinyv.median.ConnectionHintsScriptDump'], { encoding: 'utf8' });
if (exported.error || exported.status !== 0) throw exported.error || new Error(exported.stderr);
const script = exported.stdout;
new vm.Script(script);
let checks = 0;
const forbidden = () => { throw new Error('collector must not request, observe, schedule, or query layout'); };
const timing = (name, initiatorType = 'img', values = {}) => ({
  name, initiatorType, responseEnd: 900, duration: 100, responseStatus: 200, ...values
});
const image = (currentSrc, values = {}) => ({
  currentSrc, complete: true, naturalWidth: 320, naturalHeight: 180, loading: '',
  getAttribute() { return null; }, getBoundingClientRect: forbidden, ...values
});
function collect(resources, images = [], nav = { loadEventStart: 1000, responseStatus: 200 }, readyState = 'complete', pendingOnly = false) {
  let resourceReads = 0, imageReads = 0;
  const state = {
    URL, location: new URL('https://page.example/private?session=secret'),
    document: { readyState, get images() { imageReads++; if (pendingOnly) forbidden(); return images; }, querySelectorAll: forbidden, createElement: forbidden, addEventListener: forbidden },
    performance: { getEntriesByType(type) { if (type === 'navigation') return nav ? [nav] : []; resourceReads++; if (pendingOnly) forbidden(); return resources; } },
    fetch: forbidden, XMLHttpRequest: forbidden, PerformanceObserver: forbidden,
    MutationObserver: forbidden, setTimeout: forbidden, setInterval: forbidden,
    requestAnimationFrame: forbidden, getComputedStyle: forbidden
  };
  state.window = state;
  const raw = vm.runInNewContext(script, state, { timeout: 1000 });
  if (pendingOnly) { assert.equal(resourceReads, 0); assert.equal(imageReads, 0); }
  return raw === null ? null : JSON.parse(raw);
}
function expectImage(values = {}, entryValues = {}, expected = true, nav) {
  const url = 'https://images.example/opaque?signature=private&size=320';
  const result = collect([timing(url, 'img', entryValues)], [image(url, values)], nav);
  assert.equal(result.r.length, expected ? 1 : 0);
  if (expected) assert.deepEqual(result.r[0], { u: url, t: 'image', e: 900, d: 100 });
  checks++;
}

expectImage();
expectImage({ naturalWidth: 16, naturalHeight: 16 });
for (const values of [
  { complete: false }, { naturalWidth: 0 }, { naturalHeight: 0 },
  { naturalWidth: 1, naturalHeight: 1 }, { naturalWidth: 2 }, { naturalHeight: 2 },
  { naturalWidth: 15 }, { naturalHeight: 15 }, { naturalWidth: NaN }, { naturalHeight: Infinity },
  { loading: 'lazy' }, { loading: 'LAZY' }, { getAttribute() { return 'lazy'; } },
  { currentSrc: '' }, { currentSrc: undefined },
  { currentSrc: 'https://images.example/opaque?signature=other&size=320' },
  { currentSrc: 'https://images.example/other?signature=private&size=320' },
  { currentSrc: 'https://images.example:8443/opaque?signature=private&size=320' }
]) expectImage(values, {}, false);
for (const values of [
  { responseStatus: 404 }, { responseStatus: 500 }, { responseEnd: 0 },
  { responseEnd: -1 }, { responseEnd: NaN }, { responseEnd: Infinity },
  { responseEnd: 1000.001 }, { responseEnd: '900' },
  { duration: -1 }, { duration: NaN }, { duration: Infinity }, { duration: '100' }
]) expectImage({}, values, false);
expectImage({}, { responseStatus: 0 }); // Cross-origin Timing-Allow-Origin may hide status.
assert.equal(collect([], [], null, 'complete', true), null);
checks++;
for (const nav of [{}, { loadEventStart: 0 }, { loadEventStart: -1 },
  { loadEventStart: NaN }, { loadEventStart: Infinity }, { loadEventStart: '1000' }]) {
  assert.deepEqual(collect([], [], nav, 'complete', true), { o: 'https://page.example', p: true });
  checks++;
}
for (const readyState of ['loading', 'interactive', undefined, 'COMPLETE']) {
  // Pass explicit missing state through a wrapper value because default arguments supply complete.
  assert.deepEqual(collect([], [], { loadEventStart: 1000 }, readyState === undefined ? null : readyState, true),
    { o: 'https://page.example', p: true });
  checks++;
}
assert.equal(collect([], [], { loadEventStart: 0, responseStatus: 503 }, 'loading', true), null);
checks++;
const boundaryUrl = 'https://images.example/load-boundary';
assert.equal(collect([timing(boundaryUrl, 'img', { responseEnd: 1000 })], [image(boundaryUrl)]).r[0].e, 1000);
checks++;

for (const url of [
  'https://images.example/no-extension?token=private',
  'https://images.example/a.js?actually=image',
  'https://images.example/a%2Fb?encoded=%2F',
  'https://images.example:8443/opaque'
]) {
  assert.equal(collect([timing(url)], [image(url)]).r[0].u, url);
  checks++;
}
for (const url of [
  'data:image/png,bytes', 'blob:https://page.example/id', 'file:///private/image',
  'https://user:password@images.example/image', 'https://page.example/image',
  '//images.example/image'
]) {
  assert.equal(collect([timing(url)], [image(url)]).r.length, 0);
  checks++;
}
const selected = 'https://images.example/selected';
assert.equal(collect([timing(selected)], [{ src: selected, complete: true, naturalWidth: 320, naturalHeight: 180 }]).r.length, 0);
assert.equal(collect([timing(selected, 'fetch')], [image(selected)]).r.length, 0);
assert.equal(collect([timing(selected, 'link')], [image(selected)]).r[0].t, 'image');
assert.equal(collect([timing(selected, 'link')], []).r.length, 0);
checks += 4;

const mixed = [
  timing('https://scripts.example/extensionless', 'script', { duration: 40, responseEnd: 400 }),
  timing('https://styles.example/app.css?version=1', 'link', { duration: 30, responseEnd: 700 }),
  timing('https://fonts.example/web.woff2?version=1', 'css', { duration: 20, responseEnd: 950 }),
  timing('https://mime-styles.example/opaque', 'link', { duration: 10, contentType: 'Text/CSS; charset=UTF-8' }),
  timing(selected, 'img', { duration: 50, responseEnd: 300 }),
  timing('https://unused.example/preload', 'link', { duration: 10000 }),
  timing('https://fetch.example/data', 'fetch', { duration: 10000 }),
  timing('https://raw-images.example/pixel.png', 'img', { duration: 10000 })
];
const originalOrder = mixed.map(x => x.name);
const result = collect(mixed, [image(selected)]);
assert.equal(result.o, 'https://page.example');
assert.deepEqual(result.r.map(x => x.t), ['image', 'script', 'stylesheet', 'font', 'stylesheet']);
assert.deepEqual(result.r.map(x => x.d), [50, 40, 30, 20, 10]);
assert.deepEqual(mixed.map(x => x.name), originalOrder, 'sorting must not mutate the browser timing array');
assert.deepEqual(Object.keys(result).sort(), ['o', 'r']);
for (const value of result.r) assert.deepEqual(Object.keys(value).sort(), ['d', 'e', 't', 'u']);
assert.equal(collect(mixed, [], { responseStatus: 500, loadEventStart: 1000 }), null);
assert.equal(collect(mixed.filter(x => x.initiatorType !== 'img'), [], null), null,
  'missing navigation timing must not publish an authoritative empty observation');
checks += 8;

const many = Array.from({ length: 400 }, (_, i) => timing('https://cdn' + i + '.example/app', 'script', { duration: i }));
Object.defineProperty(many, 400, { get: forbidden });
const capped = collect(many).r;
assert.equal(capped.length, 16);
assert.deepEqual(capped.map(x => x.d), Array.from({ length: 16 }, (_, i) => 399 - i));
const imgs = Array.from({ length: 256 }, (_, i) => image('https://images.example/image-' + i));
Object.defineProperty(imgs, 256, { get: forbidden });
assert.equal(collect([timing('https://images.example/image-255')], imgs).r[0].t, 'image');
assert.equal(collect([timing('https://images.example/image-256')], imgs).r.length, 0);
const long = 'https://images.example/' + 'a'.repeat(2048);
assert.equal(collect([timing(long)], [image(long)]).r.length, 0);
checks += 5;

const slowPictures = Array.from({ length: 20 }, (_, i) => 'https://images.example/picture-' + i);
const slowImageEntries = slowPictures.map((url, i) => timing(url, 'img', { duration: 500 + i }));
const slowImageDom = slowPictures.map(url => image(url));
const imageDominated = collect([...slowImageEntries, timing('https://scripts.example/app.js', 'script', { duration: 1 })], slowImageDom).r;
assert.equal(imageDominated.length, 16);
assert.equal(imageDominated.filter(x => x.t === 'script').length, 1, 'slow images cannot remove the script class');
assert.equal(imageDominated.filter(x => x.t === 'image').length, 15, 'unused critical slots return to image class');
const scriptDominated = collect([...Array.from({ length: 20 }, (_, i) => timing('https://scripts.example/chunk-' + i, 'script', { duration: 500 + i })), timing(slowPictures[0], 'img', { duration: 1 })], slowImageDom).r;
assert.equal(scriptDominated.length, 16);
assert.equal(scriptDominated.filter(x => x.t === 'image').length, 1, 'slow scripts cannot remove the image class');
assert.equal(scriptDominated.filter(x => x.t === 'script').length, 15, 'unused image slots return to critical class');
checks += 6;

const balanced = collect([...slowImageEntries, ...Array.from({ length: 20 }, (_, i) => timing('https://scripts.example/chunk-' + i, 'script', { duration: 100 + i }))], slowImageDom).r;
assert.equal(balanced.length, 16);
assert.equal(balanced.filter(x => x.t === 'image').length, 8, 'image class retains half the full candidate budget');
assert.equal(balanced.filter(x => x.t === 'script').length, 8, 'critical class retains half the full candidate budget');
assert.deepEqual(balanced.map(x => x.d), [...balanced.map(x => x.d)].sort((a, b) => b - a), 'final payload remains sorted by duration');
checks += 4;

const diverse = collect([
  ...Array.from({ length: 20 }, (_, i) => timing('https://crowded.example/chunk-' + i, 'script', { duration: 900 + i })),
  ...Array.from({ length: 15 }, (_, i) => timing('https://cdn' + i + '.example/app.js', 'script', { duration: i + 1 }))
]).r;
assert.equal(diverse.length, 16);
assert.equal(new Set(diverse.map(x => new URL(x.u).origin)).size, 16, 'distinct origins take priority over repeated URLs from one origin');
assert.equal(diverse.filter(x => new URL(x.u).origin === 'https://crowded.example').length, 1);
checks += 3;

const backups = collect([
  ...Array.from({ length: 30 }, (_, i) => timing('https://crowded.example/chunk-' + i, 'script', { duration: 900 + i })),
  ...Array.from({ length: 5 }, (_, i) => timing('https://second.example/chunk-' + i, 'script', { duration: i + 1 })),
  ...slowImageEntries
], slowImageDom).r;
assert.equal(backups.filter(x => x.t === 'script').length, 8);
assert.equal(backups.filter(x => new URL(x.u).origin === 'https://second.example').length, 4, 'backup URLs receive origin-fair rounds before slower origins are crowded out');
assert.equal(backups.filter(x => new URL(x.u).origin === 'https://crowded.example').length, 4);
checks += 3;

const blockedPath = 'https://shared.example/ads.js';
const allowedPath = 'https://shared.example/content.js';
const pathAlternatives = collect([
  ...Array.from({ length: 20 }, (_, i) => timing(blockedPath, 'script', { duration: 900 + i })),
  timing(allowedPath, 'script', { duration: 1 }),
  ...slowImageEntries
], slowImageDom).r;
assert.equal(pathAlternatives.filter(x => x.u === blockedPath).length, 1, 'repeated timings cannot consume backup slots');
assert.ok(pathAlternatives.some(x => x.u === allowedPath), 'full-URL alternative survives for Main path-based ad-block filtering');
assert.equal(pathAlternatives.find(x => x.u === blockedPath).d, 919, 'deduplication retains the slowest completed sample');
assert.ok(pathAlternatives.every(x => Object.keys(x).sort().join(',') === 'd,e,t,u'), 'internal candidate state and origins never leak into payload');
checks += 4;

const sharedUrl = 'https://shared.example/resource';
const mixedUse = collect([timing(sharedUrl, 'script', { duration: 20 }), timing(sharedUrl, 'img', { duration: 10 })], [image(sharedUrl)]).r;
assert.deepEqual(mixedUse.map(x => x.t), ['script', 'image'], 'same URL with distinct request classes retains real type-specific filter candidates');
const imageDiverseUrls = Array.from({ length: 16 }, (_, i) => 'https://image' + i + '.example/visible');
const imageDiverse = collect([
  ...imageDiverseUrls.map((url, i) => timing(url, 'img', { duration: i + 1 })),
  ...Array.from({ length: 20 }, (_, i) => timing('https://crowded.example/chunk-' + i, 'script', { duration: 900 + i }))
], imageDiverseUrls.map(url => image(url))).r;
assert.equal(new Set(imageDiverse.filter(x => x.t === 'image').map(x => new URL(x.u).origin)).size, 8, 'both class selection passes prioritize distinct origins');
checks += 2;

for (let sample = 0; sample < 24; sample++) {
  const resources = [], pictures = [], expected = { image: 0, critical: 0 };
  for (let i = 0; i < 80; i++) {
    const isImage = (i * 17 + sample * 13) % 7 < sample % 8;
    const url = 'https://origin' + ((i * 11 + sample) % 13) + '.example/resource-' + i;
    const duration = (i * 97 + sample * 19) % 1000;
    resources.push(timing(url, isImage ? 'img' : 'script', { duration }));
    expected[isImage ? 'image' : 'critical']++;
    if (isImage) pictures.push(image(url));
  }
  const selected = collect(resources, pictures).r;
  const imageCount = selected.filter(x => x.t === 'image').length;
  const criticalCount = selected.length - imageCount;
  assert.equal(selected.length, 16);
  assert.ok(imageCount >= Math.min(8, expected.image));
  assert.ok(criticalCount >= Math.min(8, expected.critical));
  assert.equal(new Set(selected.map(x => x.t + ':' + x.u)).size, selected.length);
  assert.deepEqual(selected.map(x => x.d), [...selected.map(x => x.d)].sort((a, b) => b - a));
  assert.deepEqual(collect(resources, pictures).r, selected, 'selection must be deterministic');
  checks++;
}

console.log('Connection hints script behavior passed: ' + checks + ' scenarios (production payload, image proof, failures, privacy, duration order, bounds, no layout/network/observers)');
