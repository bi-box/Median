'use strict';
const fs = require('fs');
const vm = require('vm');
const assert = require('node:assert/strict');
const [installPath, buildPath] = process.argv.slice(2);
if (!buildPath) throw new Error('expected install and build scripts');
const install = fs.readFileSync(installPath, 'utf8');
const build = fs.readFileSync(buildPath, 'utf8');
const listeners = Object.create(null);
const video = {
  tagName: 'VIDEO', currentSrc: 'https://cdn.example/video/main.mp4', src: '', type: 'video/mp4',
  videoWidth: 1920, videoHeight: 1080, duration: 120,
  getAttribute() { return null; }
};
const jsonLd = { textContent: JSON.stringify({ contentUrl: 'https://cdn.example/audio/theme.flac' }) };
const document = {
  addEventListener(type, listener) { (listeners[type] || (listeners[type] = [])).push(listener); },
  removeEventListener() {},
  querySelectorAll(selector) {
    if (selector.startsWith('video,audio')) return [video];
    if (selector.includes('application/ld+json')) return [jsonLd];
    return [];
  }
};
const entries = [{ name: 'https://cdn.example/live/master.m3u8', initiatorType: 'fetch' }];
let observerCallback;
function PerformanceObserver(callback) { observerCallback = callback; }
PerformanceObserver.prototype.observe = function() {};
const context = {
  document,
  location: { href: 'https://page.example/watch', protocol: 'https:', hostname: 'page.example' },
  performance: { getEntriesByType() { return entries; } },
  PerformanceObserver,
  URL,
  JSON,
  String,
  Number,
  Object,
  Array,
  RegExp,
  isFinite,
  console
};
context.window = context;
vm.createContext(context);
vm.runInContext(install, context);
const listenerCount = Object.values(listeners).reduce((sum, list) => sum + list.length, 0);
vm.runInContext(install, context);
if (Object.values(listeners).reduce((sum, list) => sum + list.length, 0) !== listenerCount)
  throw new Error('live collector listeners duplicated');
for (const listener of listeners.loadedmetadata || []) listener({ target: video });
video.currentSrc = 'blob:https://page.example/player';
for (const listener of listeners.play || []) { listener({ target: video }); listener({ target: video }); }
if (context.__medianMediaOpaque !== 1) throw new Error('blob source was not deduplicated');
observerCallback({ getEntries() { return [{ name: 'https://cdn.example/live/manifest.mpd', initiatorType: 'xmlhttprequest' }]; } });
if (!context.__medianMediaLog.some(item => item.url.includes('main.mp4')) ||
    !context.__medianMediaLog.some(item => item.url.includes('manifest.mpd')))
  throw new Error('dynamic media was not retained');
video.currentSrc = 'https://cdn.example/video/main.mp4';
const result = JSON.parse(vm.runInContext(build, context));
if (!result.some(item => item.url && item.url.includes('master.m3u8')) ||
    !result.some(item => item.url && item.url.includes('theme.flac')) ||
    !result.some(item => item.width === 1920 && item.height === 1080))
  throw new Error('on-demand media probe missed a source');

function collectorFixture(page = 'https://page.example/watch', initialEntries = []) {
  const stats = { urls: 0, headers: 0, nativeOpens: 0, nativeSends: 0, fetches: 0, observers: 0 };
  const eventListeners = Object.create(null);
  let callback;
  class CountedURL extends URL {
    constructor(...args) { super(...args); stats.urls++; }
  }
  class XHR {
    constructor() { this.listeners = []; this.responseURL = ''; this.mime = ''; }
    open(...args) {
      if (this.openError) throw this.openError;
      stats.nativeOpens++; this.openArgs = args; this.responseURL = '';
      return 'native-open';
    }
    send(...args) {
      if (this.sendError) throw this.sendError;
      stats.nativeSends++; this.sendArgs = args; return 'native-send';
    }
    addEventListener(type, fn) { if (type === 'load') this.listeners.push(fn); }
    getResponseHeader(name) { assert.equal(name, 'content-type'); stats.headers++; return this.mime; }
    finish(mime, responseURL = '') {
      this.mime = mime; this.responseURL = responseURL;
      if (this.onload) this.onload.call(this);
      for (const listener of this.listeners) listener.call(this);
    }
  }
  function Observer(fn) { callback = fn; stats.observers++; }
  Observer.prototype.observe = function() {};
  const state = {
    location: new URL(page), URL: CountedURL, PerformanceObserver: Observer, XMLHttpRequest: XHR,
    document: {
      addEventListener(type, fn) { (eventListeners[type] ||= []).push(fn); },
      querySelectorAll() { return []; }
    },
    performance: { getEntriesByType() { return initialEntries; } },
    fetch(...args) {
      stats.fetches++; state.fetchThis = this; state.fetchArgs = args;
      if (state.fetchError) return Promise.reject(state.fetchError);
      return Promise.resolve(state.fetchResponse);
    },
    console
  };
  state.window = state;
  vm.createContext(state);
  vm.runInContext(install, state);
  return {
    state, stats, XHR,
    resource(entries) { callback({ getEntries() { return entries; } }); },
    media(url, tagName = 'VIDEO') {
      for (const listener of eventListeners.loadedmetadata || [])
        listener({ target: { tagName, currentSrc: url, type: '', duration: 0 } });
    },
    listenerCount() { return Object.values(eventListeners).reduce((sum, list) => sum + list.length, 0); }
  };
}

const plainResources = Array.from({ length: 1000 }, (_, i) => ({
  name: 'https://static.example/assets/chunk-' + i + '.js?type=script', initiatorType: 'script'
}));
const plain = collectorFixture();
plain.resource(plainResources);
assert.equal(plain.stats.urls, 0, 'ordinary resources must be filtered before URL construction');
assert.equal(plain.state.__medianMediaLog.length, 0, 'ordinary resources must not consume media capacity');
plain.resource([{ name: 'https://cdn.example/master.m3u8?token=late', initiatorType: 'fetch' }]);
assert.equal(plain.state.__medianMediaLog.length, 1, 'late true media must survive an ordinary resource burst');
assert.equal(plain.state.__medianMediaLog[0].source, 'performance');

const nativeXHR = plain.state.XMLHttpRequest.prototype;
const originalFetchWrapper = plain.state.fetch;
const originalOpenWrapper = nativeXHR.open;
const originalSendWrapper = nativeXHR.send;
const initialListenerCount = plain.listenerCount();
vm.runInContext(install, plain.state);
assert.equal(plain.state.fetch, originalFetchWrapper, 'collector reinstall must not wrap fetch again');
assert.equal(nativeXHR.open, originalOpenWrapper);
assert.equal(nativeXHR.send, originalSendWrapper);
assert.equal(plain.listenerCount(), initialListenerCount);
assert.equal(plain.stats.observers, 1);

const request = new plain.XHR();
let siteLoads = 0;
const siteOnload = request.onload = function() { assert.equal(this, request); siteLoads++; };
let siteListenerLoads = 0;
request.addEventListener('load', () => siteListenerLoads++);
for (let i = 0; i < 30; i++) {
  assert.equal(request.open('POST', '/opaque-' + i, true), 'native-open');
  assert.equal(request.send('body-' + i), 'native-send');
  request.finish('video/mp4');
}
assert.equal(request.listeners.length, 2, 'XHR reuse must retain one site listener and one probe listener');
assert.equal(request.onload, siteOnload, 'probe must not replace the site onload handler');
assert.equal(siteLoads, 30);
assert.equal(siteListenerLoads, 30);
assert.equal(plain.stats.headers, 30, 'XHR reuse must not accumulate load callbacks');
assert.equal(plain.stats.nativeOpens, 30);
assert.equal(plain.stats.nativeSends, 30);
assert.equal(request.openArgs[1], '/opaque-29');
assert.equal(request.sendArgs[0], 'body-29');
assert.ok(plain.state.__medianMediaLog.some(item => item.url === 'https://page.example/opaque-29'));
assert.equal(Object.keys(request).some(key => key.startsWith('__median')), false, 'probe state must not be stored on a site XHR');
const openError = new Error('native open error');
request.openError = openError;
assert.throws(() => request.open('GET', '/broken', true), error => error === openError);
request.openError = null;
request.open('GET', '/redirected', true);
request.send(); request.finish('application/dash+xml', 'https://cdn.example/extensionless');
assert.ok(plain.state.__medianMediaLog.some(item => item.url === 'https://cdn.example/extensionless'));
const failedSend = new plain.XHR();
failedSend.open('GET', '/send-recovery', true);
const sendError = failedSend.sendError = new Error('native send error');
for (let i = 0; i < 5; i++) assert.throws(() => failedSend.send('unchanged'), error => error === sendError);
assert.equal(failedSend.listeners.length, 1, 'failed sends must not repeatedly attach a probe listener');
failedSend.sendError = null;
assert.equal(failedSend.send('retry body'), 'native-send');
failedSend.finish('audio/ogg');
assert.equal(failedSend.listeners.length, 1);
assert.ok(plain.state.__medianMediaLog.some(item => item.url === 'https://page.example/send-recovery'));

const urls = collectorFixture();
urls.resource([
  { name: 'relative.mp4?token=one', initiatorType: 'fetch' },
  { name: 'video/extensionless', initiatorType: 'fetch' },
  { name: '//cdn.example/playlist/master.m3u8', initiatorType: 'fetch' },
  { name: 'https://cdn.example/get?mime=video%2Fmp4', initiatorType: 'fetch' },
  { name: 'https://cdn.example/video/../image.jpg', initiatorType: 'fetch' }
]);
assert.equal(urls.state.__medianMediaLog.length, 4);
assert.ok(urls.state.__medianMediaLog.some(item => item.url === 'https://page.example/relative.mp4?token=one'));
assert.ok(urls.state.__medianMediaLog.some(item => item.url === 'https://page.example/video/extensionless'));
const inherited = collectorFixture('https://page.example/video/current.mp4');
inherited.resource([{ name: '?token=refresh', initiatorType: 'fetch' }]);
assert.equal(inherited.state.__medianMediaLog[0].url, 'https://page.example/video/current.mp4?token=refresh');
const onDemand = collectorFixture();
onDemand.state.performance.getEntriesByType = () => plainResources.concat([
  { name: 'relative.mp4?token=on-demand', initiatorType: 'fetch' },
  { name: 'video/opaque', initiatorType: 'fetch' },
  { name: 'https://cdn.example/get?format=mpd', initiatorType: 'fetch' },
  { name: 'https://cdn.example/opaque-audio', initiatorType: 'audio' }
]);
const onDemandResult = JSON.parse(vm.runInContext(build, onDemand.state));
assert.equal(onDemand.stats.urls, 4, 'on-demand probe must filter ordinary resources before URL construction');
assert.equal(onDemandResult.filter(item => item.url).length, 4);
assert.ok(onDemandResult.some(item => item.url === 'https://page.example/relative.mp4?token=on-demand'));

const capacity = collectorFixture();
capacity.resource(Array.from({ length: 240 }, (_, i) => ({ name: 'https://cdn.example/' + i + '.mp4', initiatorType: 'fetch' })));
assert.equal(capacity.state.__medianMediaLog.length, 240);
const capacityUrls = capacity.stats.urls;
capacity.resource(plainResources);
capacity.media('https://cdn.example/overflow.mp4');
assert.equal(capacity.stats.urls, capacityUrls, 'full collector must avoid further URL construction');
capacity.media('blob:https://page.example/player'); capacity.media('blob:https://page.example/player');
assert.equal(capacity.state.__medianMediaOpaque, 1, 'full URL capacity must still deduplicate opaque media');
for (let i = 0; i < 120; i++) capacity.media('blob:https://page.example/' + i);
assert.equal(capacity.state.__medianMediaOpaque, 99, 'opaque count must remain bounded');

async function checkFetchHeaders() {
  const fixture = collectorFixture();
  const response = { headers: { get(name) { assert.equal(name, 'content-type'); return 'application/vnd.apple.mpegurl'; } } };
  fixture.state.fetchResponse = response;
  const input = { url: '/opaque-fetch?key=two' };
  const options = { method: 'POST', body: 'native body' };
  const result = await fixture.state.fetch(input, options);
  assert.equal(result, response, 'fetch must preserve the original response without consuming it');
  assert.equal(fixture.state.fetchThis, fixture.state);
  assert.equal(fixture.state.fetchArgs[0], input);
  assert.equal(fixture.state.fetchArgs[1], options);
  assert.equal(fixture.state.__medianMediaLog[0].url, 'https://page.example/opaque-fetch?key=two');
  fixture.state.fetchResponse = { headers: { get() { return 'application/json'; } } };
  const beforeUrls = fixture.stats.urls;
  await fixture.state.fetch('/ordinary-api');
  assert.equal(fixture.stats.urls, beforeUrls);
  const error = fixture.state.fetchError = new Error('original rejection');
  await assert.rejects(fixture.state.fetch('/failed'), actual => actual === error);
  console.log('Media probe behavior passed (candidate filtering, XHR reuse, fetch headers, relative/query/blob, capacity, idempotence)');
}
checkFetchHeaders().catch(error => { console.error(error); process.exitCode = 1; });
