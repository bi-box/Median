'use strict';
const fs = require('fs');
const vm = require('vm');
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
console.log('Media probe behavior passed');
