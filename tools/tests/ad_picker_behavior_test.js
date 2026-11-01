'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const pickerScript = fs.readFileSync(process.argv[2], 'utf8');
const hideScript = process.argv[3] && fs.readFileSync(process.argv[3], 'utf8');
const pickedValidateScript = process.argv[4] && fs.readFileSync(process.argv[4], 'utf8');
const pickedHideScript = process.argv[5] && fs.readFileSync(process.argv[5], 'utf8');

// Dispatch through the capture path as browsers do, including listeners installed
// before picker entry. Cross-origin frame contents never receive overlay events.
class Node {
  constructor(tag, doc) {
    this.tagName = tag.toUpperCase(); this.doc = doc; this.parentNode = null;
    this.children = []; this.attrs = {}; this.listeners = {}; this.textContent = '';
    this.style = {
      setProperty(name, value, priority) { this[name] = value; this[name + 'Priority'] = priority; },
      getPropertyValue(name) { return this[name] || ''; },
      getPropertyPriority(name) { return this[name + 'Priority'] || ''; },
      removeProperty(name) { delete this[name]; delete this[name + 'Priority']; }
    };
    this.classList = [];
    this.classList.add = value => { if (!this.classList.includes(value)) this.classList.push(value); };
    this.classList.remove = value => { const i = this.classList.indexOf(value); if (i >= 0) this.classList.splice(i, 1); };
  }
  get parentElement() { return this.parentNode instanceof Node ? this.parentNode : null; }
  get id() { return this.attrs.id || ''; }
  set id(value) { this.attrs.id = value; }
  get className() { return this.classList.join(' '); }
  appendChild(node) { node.parentNode = this; this.children.push(node); return node; }
  removeChild(node) { this.children.splice(this.children.indexOf(node), 1); node.parentNode = null; }
  contains(node) { return node === this || this.children.some(child => child instanceof Node && child.contains(node)); }
  setAttribute(name, value) {
    this.attrs[name] = String(value);
    if (name === 'style') String(value).split(';').forEach(declaration => {
      const split = declaration.indexOf(':');
      if (split < 0) return;
      const property = declaration.slice(0, split).trim();
      let cssValue = declaration.slice(split + 1).trim();
      const priority = /\s*!important$/i.test(cssValue) ? 'important' : '';
      cssValue = cssValue.replace(/\s*!important$/i, '').trim();
      // CSSOM canonicalizes unitless positional zero while getAttribute keeps
      // the original declaration until a later inline-style mutation.
      if (/^(top|right|bottom|left)$/.test(property) && cssValue === '0') cssValue = '0px';
      this.style.setProperty(property, cssValue, priority);
    });
  }
  getAttribute(name) { return Object.hasOwn(this.attrs, name) ? this.attrs[name] : null; }
  getBoundingClientRect() { return this.rect || { width: 100, height: 80 }; }
  addEventListener(type, fn, options) { (this.listeners[type] ||= []).push({ fn, capture: options === true || !!(options && options.capture) }); }
  removeEventListener(type, fn) { this.listeners[type] = (this.listeners[type] || []).filter(item => item.fn !== fn); }
}

function setup() {
  const win = new Node('window');
  const doc = new Node('document');
  doc.createElement = tag => new Node(tag, doc);
  doc.createTextNode = text => ({ textContent: text, parentNode: null });
  doc.documentElement = doc.appendChild(doc.createElement('html'));
  doc.head = doc.documentElement.appendChild(doc.createElement('head'));
  doc.body = doc.documentElement.appendChild(doc.createElement('body'));
  function descendants(root) { return root.children.flatMap(child => child instanceof Node ? [child, ...descendants(child)] : []); }
  function matches(node, part) {
    let alternativesMatch = true;
    part = part.replace(/:is\(([^()]*)\)/g, (_, alternatives) => {
      if (!alternatives.split(',').some(alternative => matches(node, alternative))) alternativesMatch = false;
      return '';
    });
    if (!alternativesMatch) return false;
    const nth = part.match(/:nth-of-type\((\d+)\)$/);
    if (nth) {
      const siblings = node.parentNode.children.filter(child => child.tagName === node.tagName);
      if (siblings.indexOf(node) + 1 !== Number(nth[1])) return false;
      part = part.slice(0, nth.index);
    }
    let attrMatches = true;
    part = part.replace(/\[([a-z-]+)(\*=|\$=|=)("(?:\\.|[^"])*")\]/g, (_, name, operator, value) => {
      const actual = node.getAttribute(name);
      const expected = JSON.parse(value);
      if (operator === '*=' ? actual == null || !actual.includes(expected)
        : operator === '$=' ? actual == null || !actual.endsWith(expected) : actual !== expected) attrMatches = false;
      return '';
    });
    if (!attrMatches) return false;
    if (part[0] === '#') return node.id === part.slice(1);
    const bits = part.split('.');
    return (!bits[0] || node.tagName.toLowerCase() === bits[0]) && bits.slice(1).every(cls => node.classList.includes(cls));
  }
  doc.querySelectorAll = selector => {
    if (selector === '[') throw new Error('invalid selector');
    const parts = selector.split(/\s*>\s*/);
    return descendants(doc).filter(node => {
      let current = node;
      for (let i = parts.length - 1; i >= 0; i--) {
        if (!current || !matches(current, parts[i])) return false;
        current = current.parentNode;
      }
      return true;
    });
  };
  doc.getElementById = id => descendants(doc).find(node => node.id === id) || null;
  let hit = null; let now = 1000; let styleReadCount = 0;
  doc.elementFromPoint = () => Array.isArray(hit) ? hit.find(node => node.style['pointer-events'] !== 'none') : hit;
  win.getComputedStyle = node => {
    styleReadCount++;
    return Object.assign({ position: 'static', opacity: '1' }, node.computedStyle || {}, node.style);
  };
  const messages = [];
  win.prompt = (token, data) => { messages.push(JSON.parse(data)); };
  const context = vm.createContext({ window: win, document: doc, innerWidth: 400, innerHeight: 800, Date: { now: () => now } });
  function fire(type, target, extra = {}) {
    const event = { type, target, cancelable: true, clientX: 30, clientY: 100,
      preventDefault() { this.defaultPrevented = true; },
      stopImmediatePropagation() { this.stopped = true; },
      stopPropagation() { this.stopped = true; }, ...extra };
    const path = [win, doc];
    let parent = target;
    const nodes = [];
    while (parent && parent !== doc) { nodes.unshift(parent); parent = parent.parentNode; }
    path.push(...nodes);
    for (const node of path) {
      for (const listener of node.listeners[type] || []) {
        if (listener.capture) listener.fn(event);
        if (event.stopped) return event;
      }
    }
    for (const node of [...path].reverse()) {
      if (type === 'click' && node.onclick) node.onclick(event);
      if (event.stopped) return event;
      for (const listener of node.listeners[type] || []) {
        if (!listener.capture) listener.fn(event);
        if (event.stopped) return event;
      }
    }
    return event;
  }
  return { win, doc, context, messages, fire, setHit: node => { hit = node; }, advance: () => { now += 1000; },
    styleReadCount: () => styleReadCount,
    enter() { assert.equal(vm.runInContext(pickerScript, context), true); },
    shield() { return doc.documentElement.children.find(node => node.attrs['aria-hidden'] === 'true'); },
    bar() { return doc.documentElement.children.find(node => node.attrs.role === 'dialog'); },
    buttons() { return this.bar().children[1].children; } };
}

function touch(env, type, x, y) {
  const point = { clientX: x, clientY: y };
  return env.fire(type, env.shield(), { touches: type === 'touchend' ? [] : [point], changedTouches: [point] });
}

function gridFixture(count = 40) {
  const env = setup(); const { doc } = env;
  const card = doc.body.appendChild(doc.createElement('div')); card.id = 'movie-card';
  const cover = card.appendChild(doc.createElement('div')); cover.classList.add('vod-cover');
  const tiles = [];
  for (let i = 0; i < count; i++) {
    const row = Math.floor(i / 10), column = i % 10;
    const tile = doc.body.appendChild(doc.createElement('div'));
    tile.id = `imYJtvBi_${row}_${column}`; tile.classList.add('imYJtvBi');
    tile.rect = { left: column * 40, top: 676 + row * 31, width: 40, height: 31 };
    tile.setAttribute('style', `height:31px;bottom:${(3 - row) * 31}px;left:${column * 40}px;background-position:${-column * 40}px ${-row * 31}px;background-size:400px 124px!important;`);
    tile.computedStyle = { position: 'fixed', opacity: '1', display: 'block', visibility: 'visible',
      'background-image': 'url(https://random.example/ad-image.png)', 'z-index': '2147483646' };
    tiles.push(tile);
  }
  return { env, tiles, card, cover };
}

function liveGridFixture() {
  const fixture = gridFixture();
  fixture.env.context.innerWidth = 392; fixture.env.context.innerHeight = 714;
  // Geometry and inline declarations recorded from xmfyy-group-live.json.
  for (let i = 0; i < fixture.tiles.length; i++) {
    const row = Math.floor(i / 10), column = i % 10, tile = fixture.tiles[i];
    tile.id = `dJmeCi_${row}_${column}`; tile.classList.remove('imYJtvBi'); tile.classList.add('dJmeCi');
    tile.rect = { left: column * 39.2, top: 589.8181762695312 + row * 31, width: 39.272727966308594, height: 31 };
    tile.setAttribute('style', `height: 31px; bottom: ${(3 - row) * 31}px; left: ${column * 39.2}px; background-position: ${-column * 39.2}px ${-row * 31}px; background-size: 392px 124px !important;`);
  }
  return fixture;
}

function rebuildGrid(fixture, prefix = 'QqEi', reverse = false) {
  const { doc } = fixture.env, originals = fixture.tiles;
  const replacements = originals.map(original => {
    const tile = doc.createElement('div');
    tile.id = original.id.replace(/^[A-Za-z]+_/, prefix + '_'); tile.classList.add(prefix);
    tile.rect = { ...original.rect }; tile.setAttribute('style', original.getAttribute('style'));
    tile.computedStyle = { ...original.computedStyle, 'background-image': `url(https://changed.example/${prefix}.png)` };
    return tile;
  });
  originals.forEach(original => doc.body.removeChild(original));
  for (const tile of reverse ? [...replacements].reverse() : replacements) doc.body.appendChild(tile);
  fixture.tiles = replacements;
  return originals;
}

{
  const env = setup(); const { doc } = env;
  const ad = doc.body.appendChild(doc.createElement('div'));
  ad.id = 'FUGaPPi_3_9'; ad.classList.add('banner');
  let jumps = 0;
  for (const type of ['pointerdown', 'touchstart', 'touchend', 'click']) {
    ad.addEventListener(type, () => { jumps++; });
    doc.addEventListener(type, () => { jumps++; }, true);
  }
  env.setHit(ad); env.enter();
  const down = env.fire('pointerdown', env.shield(), { pointerType: 'touch' });
  assert.equal(down.stopped, true); assert.equal(down.defaultPrevented, undefined, 'touch pointerdown must allow native scrolling');
  const start = touch(env, 'touchstart', 30, 100);
  assert.equal(start.stopped, true); assert.equal(start.defaultPrevented, undefined, 'touchstart must allow native scrolling');
  const end = touch(env, 'touchend', 30, 100);
  assert.equal(end.defaultPrevented, true);
  assert.equal(env.fire('click', env.shield()).defaultPrevented, true, 'ignored synthetic click must still be cancelled');
  assert.equal(jumps, 0, 'ad handlers and earlier document capture handlers must not see picker touches');
  assert.equal(env.buttons()[0].disabled, false);
  env.fire('click', env.buttons()[0]);
  assert.equal(env.messages.length, 1);
  const selector = env.messages[0].selector;
  assert.equal(env.win.__medianAdSelection.selector, selector);
  assert.equal(env.win.__medianAdSelection.nodes.length, 1);
  assert.equal(env.win.__medianAdSelection.nodes[0], ad, 'confirmed picker state must retain the selected node itself');
  assert(!selector.includes('__median'), 'picker temporary classes must not be persisted');
  assert(!selector.includes('FUGaPPi'), 'generated ad ID must not be persisted');
  assert.equal(doc.querySelectorAll(selector)[0], ad, 'selector must remain valid after cleanup');
  ad.id = 'ZAbcDef_3_10';
  assert.equal(doc.querySelectorAll(selector)[0], ad, 'same layout with a regenerated ad ID must still match');
  assert.equal(env.shield(), undefined); assert.equal(env.bar(), undefined);
  assert.equal(env.win.__medianAdPicker, undefined);
  assert.equal((env.win.listeners.touchstart || []).length, 0, 'capture handlers must be removed');
}

{
  const env = setup(); const ad = env.doc.body.appendChild(env.doc.createElement('div'));
  env.setHit(ad); env.enter();
  touch(env, 'touchstart', 30, 100);
  const move = touch(env, 'touchmove', 30, 160);
  assert.equal(move.defaultPrevented, undefined, 'scrolling must not be prevented');
  touch(env, 'touchend', 30, 160);
  assert.equal(env.buttons()[0].disabled, true, 'scroll gesture must not select an element');
  assert.equal(env.fire('click', env.shield()).defaultPrevented, true);
  assert.equal(env.buttons()[0].disabled, true, 'synthetic click after scroll must not select');
  env.fire('click', env.buttons()[3]);
  assert.deepEqual(env.messages, [{ cancel: true }]);
}

{
  const env = setup(); const section = env.doc.body.appendChild(env.doc.createElement('section'));
  const frame = section.appendChild(env.doc.createElement('iframe'));
  Object.defineProperty(frame, 'contentDocument', { get() { throw new Error('cross-origin'); } });
  env.setHit(frame); env.enter();
  touch(env, 'touchstart', 30, 100); touch(env, 'touchend', 30, 100);
  env.fire('click', env.buttons()[0]);
  assert.equal(env.messages[0].tag, 'iframe', 'cross-origin iframe itself must remain selectable');
  assert.equal(env.doc.querySelectorAll(env.messages[0].selector)[0], frame);
}

{
  const env = setup(); const parent = env.doc.body.appendChild(env.doc.createElement('section'));
  const sibling = parent.appendChild(env.doc.createElement('div'));
  const ad = parent.appendChild(env.doc.createElement('div'));
  ad.classList.add('__median_ad_target'); ad.classList.add('hover');
  env.setHit(ad); env.enter();
  env.fire('pointerdown', env.shield(), { pointerType: 'mouse' });
  assert.equal(env.fire('pointerup', env.shield(), { pointerType: 'mouse' }).defaultPrevented, true);
  env.fire('click', env.buttons()[0]);
  assert.equal(env.doc.querySelectorAll(env.messages[0].selector)[0], ad);
  assert(!env.messages[0].selector.includes('__median'));
  assert.notEqual(env.doc.querySelectorAll(env.messages[0].selector)[0], sibling);
}

{
  const env = setup(); env.enter();
  assert.equal(vm.runInContext(pickerScript, env.context), false, 'second entry toggles picker off');
  assert.deepEqual(env.messages, [{ cancel: true }]);
  assert.equal(env.shield(), undefined);
  assert.equal(env.win.__medianAdSelection, undefined, 'toggle cancellation must not retain a picked selection');
}

for (const action of [2, 3]) {
  const env = setup(); const ad = env.doc.body.appendChild(env.doc.createElement('div'));
  env.win.__medianAdSelection = { selector: 'old', nodes: [ad] };
  env.setHit(ad); env.enter();
  assert.equal(env.win.__medianAdSelection, undefined, 'new picker entry must discard earlier node identity');
  touch(env, 'touchstart', 30, 100); touch(env, 'touchend', 30, 100);
  env.fire('click', env.buttons()[action]);
  assert.equal(env.win.__medianAdSelection, undefined, 'manual entry and cancellation must not retain node identity');
}

{
  const env = setup(); const original = env.doc.body.appendChild(env.doc.createElement('div')); original.id = 'banner-slot';
  env.setHit(original); env.enter();
  touch(env, 'touchstart', 30, 100); touch(env, 'touchend', 30, 100);
  env.doc.body.removeChild(original);
  const replacement = env.doc.body.appendChild(env.doc.createElement('div')); replacement.id = 'banner-slot';
  env.fire('click', env.buttons()[0]);
  assert.equal(env.win.__medianAdSelection.nodes[0], original,
    'confirmation must save the originally selected node, never a replacement found by querying its selector');
  assert.notEqual(env.win.__medianAdSelection.nodes[0], replacement);
}

{
  const env = setup(); const { doc } = env;
  const ad = doc.body.appendChild(doc.createElement('div'));
  ad.id = 'FUGaPPi_3_9'; ad.classList.add('qfLMXni');
  const tile = doc.body.appendChild(doc.createElement('div'));
  tile.style.position = 'fixed'; tile.style.opacity = '0.01'; tile.classList.add('nzgoougl_b');
  const tile2 = doc.body.appendChild(doc.createElement('div'));
  tile2.style.position = 'fixed'; tile2.style.opacity = '0.01'; tile2.style.setProperty('pointer-events', 'auto', 'important');
  env.setHit([tile2, tile, ad]); env.enter();
  touch(env, 'touchstart', 30, 100); touch(env, 'touchend', 30, 100);
  assert.equal(tile.style.getPropertyValue('pointer-events'), '', 'transparent tile inline state must be restored');
  assert.equal(tile2.style.getPropertyValue('pointer-events'), 'auto');
  assert.equal(tile2.style.getPropertyPriority('pointer-events'), 'important');
  env.fire('click', env.buttons()[0]);
  assert.equal(doc.querySelectorAll(env.messages[0].selector)[0], ad, 'picker must reach visible banner beneath transparent ad hit tiles');
  assert(!env.messages[0].selector.includes('qfLMXni'), 'mixed-case random classes must not be persisted');
  assert(!env.messages[0].selector.includes('nzgoougl'), 'click trap must not be mistaken for the visible banner');
}

{
  const env = setup(); const { doc } = env;
  doc.body.appendChild(doc.createElement('div')).id = 'movie-content';
  const ad = doc.body.appendChild(doc.createElement('div'));
  ad.id = 'FUGaPPi_3_9'; ad.classList.add('qfLMXni'); ad.rect = { width: 400, height: 130 };
  // The real page assigns fixed/transparent styles through generated classes;
  // none of these layout properties are present on element.style.
  ad.computedStyle = { position: 'fixed', opacity: '1' };
  const piece = ad.appendChild(doc.createElement('div'));
  piece.rect = { width: 40, height: 32 };
  const tiles = Array.from({ length: 20 }, () => {
    const tile = doc.body.appendChild(doc.createElement('div'));
    tile.computedStyle = { position: 'fixed', opacity: '.01', 'pointer-events': 'auto' };
    tile.classList.add('nzgoougl_b');
    return tile;
  });
  env.setHit([...tiles, piece]); env.enter();
  assert.equal(env.styleReadCount(), 0, 'picker entry must not scan page styles');
  touch(env, 'touchstart', 30, 100);
  const startReads = env.styleReadCount();
  assert(startReads >= tiles.length && startReads <= 32, 'class style reads must be bounded to the hit path');
  touch(env, 'touchend', 30, 100);
  assert.equal(env.styleReadCount(), startReads, 'one gesture must reuse its bounded style cache');
  for (const tile of tiles) assert.equal(tile.style.getPropertyValue('pointer-events'), '', 'class click traps must restore their original inline state');
  env.fire('click', env.buttons()[0]);
  assert.equal(doc.querySelectorAll(env.messages[0].selector)[0], ad,
    'class-styled transparent traps must be penetrated and a class-styled fixed puzzle root selected');
  assert(!env.messages[0].selector.includes('nzgoougl'));
  assert(!env.messages[0].selector.includes('qfLMXni'));
}

{
  const env = setup(); const { doc } = env;
  const poster = doc.body.appendChild(doc.createElement('div'));
  poster.id = 'movie-card'; poster.rect = { width: 100, height: 160 };
  poster.appendChild(doc.createElement('img'));
  const ad = doc.body.appendChild(doc.createElement('div'));
  ad.id = 'FUGaPPi_3_9'; ad.classList.add('qfLMXni');
  ad.rect = { width: 400, height: 130 };
  ad.setAttribute('style', 'position:fixed;bottom:0;left:0;width:100vw;z-index:123456;background-image:url(https://random.example/a.png)');
  assert.equal(ad.style.getPropertyValue('bottom'), '0px', 'CSSOM must differ from the original unitless zero declaration');
  const offset = doc.body.appendChild(doc.createElement('div'));
  offset.id = 'ABcDeFG_4_9'; offset.classList.add('aBCdEFg'); offset.rect = { width: 400, height: 130 };
  offset.setAttribute('style', 'position:fixed;bottom:0.2px;left:0;width:100vw;z-index:123456;background-image:url(https://random.example/offset.png)');
  const piece = ad.appendChild(doc.createElement('div'));
  piece.rect = { width: 40, height: 32 }; piece.classList.add('banner');
  piece.setAttribute('style', 'position:absolute;left:40px;width:40px;background-image:url(https://random.example/piece.png)');
  env.setHit(piece); env.enter();
  touch(env, 'touchstart', 30, 100); touch(env, 'touchend', 30, 100);
  env.fire('click', env.buttons()[0]);
  const selector = env.messages[0].selector;
  assert.equal(doc.querySelectorAll(selector)[0], ad, 'picking a puzzle piece must select the bounded fixed banner');
  assert.equal(doc.querySelectorAll(selector).length, 1, 'unitless zero must distinguish otherwise identical fixed elements');
  assert(selector.includes('bottom:0'), 'stable selector must include the raw zero layout feature');
  assert(selector.includes('[style*='), 'random banner root must use stable layout features');
  assert(!selector.includes('random.example'), 'background URL must not be persisted');
  assert(!selector.includes('FUGaPPi') && !selector.includes('qfLMXni'));
  ad.id = 'ABcDeFG_4_10'; ad.classList.remove('qfLMXni'); ad.classList.add('aBCdEFg');
  ad.setAttribute('style', 'position: fixed; bottom: 0px; left: 0px; width: 100vw; z-index: 123456; background-image: url(https://different.example/b.png)');
  assert.equal(doc.querySelectorAll(selector)[0], ad, 'layout selector must survive randomized identifiers, URL and inline CSS whitespace');
  assert(!doc.querySelectorAll(selector).includes(offset), 'zero declaration must not match a nonzero fractional offset');
  assert(!doc.querySelectorAll(selector).includes(poster), 'film content must remain unaffected');
}

for (const boundary of [
  { className: 'artplayer-app' }, { className: 'dplayer' }, { id: 'player-container' },
  { id: 'video-player' }, { className: 'dialog-content' }, { tag: 'dialog' },
  { role: 'dialog' }, { role: 'alertdialog' }, { keep: '' }
]) {
  const env = setup(); const { doc } = env;
  const player = doc.body.appendChild(doc.createElement(boundary.tag || 'div'));
  player.rect = { width: 400, height: 300 };
  player.setAttribute('style', 'position:fixed;bottom:0;width:100vw;z-index:20');
  if (boundary.className) player.classList.add(boundary.className);
  if (boundary.id) player.id = boundary.id;
  if (boundary.role) player.setAttribute('role', boundary.role);
  if (Object.hasOwn(boundary, 'keep')) player.setAttribute('data-ad-keep', boundary.keep);
  const ad = player.appendChild(doc.createElement('div')); ad.classList.add('banner');
  ad.rect = { width: 120, height: 40 };
  const image = ad.appendChild(doc.createElement('img')); image.rect = { width: 120, height: 40 };
  env.setHit(image); env.enter();
  touch(env, 'touchstart', 30, 100); touch(env, 'touchend', 30, 100);
  env.fire('click', env.buttons()[0]);
  assert.equal(doc.querySelectorAll(env.messages[0].selector)[0], ad,
    `ad selection must stop inside preserved player/dialog boundary ${JSON.stringify(boundary)}`);
  assert(!player.classList.includes('__median_ad_target'), 'player itself must not be selected');
}

for (const tag of ['video', 'audio']) {
  const env = setup(); const player = env.doc.body.appendChild(env.doc.createElement(tag));
  player.rect = { width: 400, height: 300 }; player.style.position = 'fixed';
  const ad = player.appendChild(env.doc.createElement('img')); ad.id = 'inline-ad';
  env.setHit(ad); env.enter();
  touch(env, 'touchstart', 30, 100); touch(env, 'touchend', 30, 100);
  env.fire('click', env.buttons()[0]);
  assert.equal(env.doc.querySelectorAll(env.messages[0].selector)[0], ad,
    `media promotion must not cross the ${tag} boundary`);
}

{
  const { env, tiles, card, cover } = gridFixture();
  env.setHit(tiles[25]); env.enter();
  touch(env, 'touchstart', 215, 750); touch(env, 'touchend', 215, 750);
  for (const tile of tiles) assert(tile.classList.includes('__median_ad_target'), 'every verified grid tile must be highlighted');
  assert(!card.classList.includes('__median_ad_target')); assert(!cover.classList.includes('__median_ad_target'));
  assert(env.styleReadCount() <= 132, 'complete puzzle verification must keep computed style reads bounded');
  for (const gesture of ['scroll', 'cancel']) {
    env.setHit(cover); touch(env, 'touchstart', 30, 100);
    assert(cover.classList.includes('__median_ad_target'), 'a new gesture must preview its own target');
    if (gesture === 'scroll') {
      touch(env, 'touchmove', 30, 160); touch(env, 'touchend', 30, 160);
    } else env.fire('touchcancel', env.shield());
    for (const tile of tiles) assert(tile.classList.includes('__median_ad_target'),
      `all selected puzzle highlights must be restored after ${gesture}`);
    assert(!cover.classList.includes('__median_ad_target'), 'cancelled preview must not remain highlighted');
  }
  env.fire('click', env.buttons()[0]);
  const selector = env.messages[0].selector;
  assert.equal(env.messages[0].count, 40, 'picker must report the complete direct-body puzzle count');
  assert.equal(env.win.__medianAdSelection.nodes.length, 40);
  assert.deepEqual(env.doc.querySelectorAll(selector), tiles, 'stable puzzle selector must match exactly all selected tiles');
  for (let i = 0; i < tiles.length; i++) {
    assert.equal(env.win.__medianAdSelection.nodes[i], tiles[i], 'saved puzzle identities must preserve document order');
    assert(!tiles[i].classList.includes('__median_ad_target'), 'all puzzle highlights must be removed after confirmation');
  }
  assert(!selector.includes('imYJtvBi') && !selector.includes('ad-image') && !selector.includes('400px'),
    'puzzle selector must omit randomized identities, background URLs and viewport-specific dimensions');
  if (pickedValidateScript && pickedHideScript) {
    const oldSelector = JSON.stringify('#ad-slot > .banner:nth-of-type(2)');
    const validation = pickedValidateScript.replace(oldSelector, JSON.stringify(selector));
    const hiding = pickedHideScript.replace(oldSelector, JSON.stringify(selector));
    assert.equal(vm.runInContext(validation, env.context), 40);
    assert.equal(vm.runInContext(hiding, env.context), 40);
    for (const tile of tiles) assert.equal(tile.style.display, 'none', 'the complete puzzle must hide in one operation');
    assert.equal(card.style.display, undefined); assert.equal(cover.style.display, undefined, 'film cover behind the grid must remain visible');
  }
  for (let i = 0; i < tiles.length; i++) {
    const tile = tiles[i]; tile.id = `ABcDeFG_${Math.floor(i / 10)}_${i % 10}`;
    tile.classList.remove('imYJtvBi'); tile.classList.add('ABcDeFG');
    tile.setAttribute('style', `height: 33px; bottom: ${(3 - Math.floor(i / 10)) * 33}px; left: ${(i % 10) * 42}px; background-position: ${-(i % 10) * 42}px ${-Math.floor(i / 10) * 33}px; background-size: 420px 132px !important;`);
    tile.computedStyle['background-image'] = 'url(https://changed.example/another-image.png)';
  }
  assert.deepEqual(env.doc.querySelectorAll(selector), tiles,
    'the persistent group rule must survive randomized grid IDs/classes/images, dimensions and CSS whitespace');
}

for (const mismatch of ['background', 'no-image', 'family', 'position', 'boundary', 'coordinate', 'children', 'text']) {
  const { env, tiles } = gridFixture();
  if (mismatch === 'background') tiles[0].computedStyle['background-image'] = 'url(https://other.example/content.png)';
  if (mismatch === 'no-image') tiles.forEach(tile => { tile.computedStyle['background-image'] = 'none'; });
  if (mismatch === 'family') tiles[0].id = 'ABcDeFG_0_0';
  if (mismatch === 'position') tiles[0].computedStyle.position = 'static';
  if (mismatch === 'boundary') tiles[0].setAttribute('data-ad-keep', '');
  if (mismatch === 'coordinate') tiles[0].id = tiles[1].id;
  if (mismatch === 'children') tiles[0].appendChild(env.doc.createElement('img'));
  if (mismatch === 'text') tiles[0].textContent = 'Movie title';
  env.setHit(tiles[25]); env.enter();
  touch(env, 'touchstart', 215, 750); touch(env, 'touchend', 215, 750);
  env.fire('click', env.buttons()[0]);
  assert.equal(env.messages[0].count, 1, `an inconsistent ${mismatch} family must not be expanded into a group`);
  assert.equal(env.win.__medianAdSelection.nodes.length, 1);
  assert.equal(env.win.__medianAdSelection.nodes[0], tiles[25]);
}

for (const imagePresent of [true, false]) {
  const { env, tiles } = liveGridFixture();
  for (let i = 0; i < tiles.length; i++) {
    tiles[i].id = `QqEi_${Math.floor(i / 10)}_${i % 10}`;
    tiles[i].classList.remove('dJmeCi'); tiles[i].classList.add('QqEi');
    if (!imagePresent) tiles[i].computedStyle['background-image'] = 'none';
  }
  env.setHit(tiles[25]); env.enter();
  touch(env, 'touchstart', 210, 665); touch(env, 'touchend', 210, 665);
  env.fire('click', env.buttons()[0]);
  assert.equal(env.messages[0].count, imagePresent ? 40 : 1,
    'the observed four-character grid prefix must be supported without accepting the no-image carousel transition');
  assert(!env.messages[0].selector.includes('QqEi'), 'short randomized IDs/classes must not be persisted in either picker path');
}

{
  const { env, tiles } = gridFixture(101); env.setHit(tiles[25]); env.enter();
  touch(env, 'touchstart', 215, 750); touch(env, 'touchend', 215, 750);
  env.fire('click', env.buttons()[0]);
  assert.equal(env.messages[0].count, 1, 'oversized puzzle family must not be expanded');
  assert(env.styleReadCount() <= 32, 'oversized puzzle must be rejected before style verification');
}

{
  const env = setup(); const { doc } = env;
  const card = doc.body.appendChild(doc.createElement('div')); card.id = 'film-card';
  const poster = card.appendChild(doc.createElement('img')); poster.id = 'poster';
  env.setHit(poster); env.enter();
  touch(env, 'touchstart', 30, 100); touch(env, 'touchend', 30, 100);
  env.fire('click', env.buttons()[0]);
  assert.equal(doc.querySelectorAll(env.messages[0].selector)[0], card, 'normal media selection must keep its local card rather than widening to page content');
}

if (hideScript) {
  const env = setup(); const slot = env.doc.body.appendChild(env.doc.createElement('div')); slot.id = 'ad-slot';
  slot.appendChild(env.doc.createElement('div')).classList.add('banner');
  const ad = slot.appendChild(env.doc.createElement('div')); ad.classList.add('banner');
  assert.equal(vm.runInContext(hideScript, env.context), 1);
  assert.equal(ad.style.display, 'none'); assert.equal(ad.style.displayPriority, 'important');
  const style = env.doc.getElementById('__median_manual_blocks'); assert(style);
  slot.removeChild(ad);
  assert.equal(vm.runInContext(hideScript, env.context), 0, 'missing element must report failure');
  const invalid = hideScript.replace('#ad-slot > .banner:nth-of-type(2)', '[');
  assert.equal(vm.runInContext(invalid, env.context), -1, 'invalid selector must report failure');

  for (const count of [200, 201]) {
    const limited = setup();
    for (let i = 0; i < count; i++) limited.doc.body.appendChild(limited.doc.createElement('div')).classList.add('banner');
    const script = hideScript.replace(JSON.stringify('#ad-slot > .banner:nth-of-type(2)'), JSON.stringify('.banner'));
    assert.equal(vm.runInContext(script, limited.context), count <= 200 ? count : 0,
      'manual selectors must respect the 1–200 element limit');
    if (count > 200) {
      assert.equal(limited.doc.getElementById('__median_manual_blocks'), null, 'oversized rule must not create a hide style');
      for (const node of limited.doc.body.children) assert.equal(node.style.display, undefined);
    }
  }
}

if (pickedValidateScript && pickedHideScript) {
  const q = '#ad-slot > .banner:nth-of-type(2)';
  const execute = (script, env, selector = q) => vm.runInContext(script.replace(JSON.stringify(q), JSON.stringify(selector)), env.context);
  function selectGrid(fixture, beforeConfirm) {
    const { env, tiles } = fixture;
    env.setHit(tiles[25]); env.enter();
    touch(env, 'touchstart', 210, 665); touch(env, 'touchend', 210, 665);
    if (beforeConfirm) beforeConfirm();
    env.fire('click', env.buttons()[0]);
    assert.equal(env.messages[0].count, 40);
    assert.equal(typeof env.win.__medianAdSelection.revalidate, 'function', 'only a verified group must retain its family verifier');
    return env.messages[0].selector;
  }
  for (const phase of ['before-confirm', 'after-validation', 'twice', 'reordered']) {
    const fixture = liveGridFixture(), { env, card, cover } = fixture;
    const originals = fixture.tiles;
    const selector = selectGrid(fixture, phase === 'before-confirm' ? () => rebuildGrid(fixture) : null);
    if (phase !== 'before-confirm') {
      assert.equal(execute(pickedValidateScript, env, selector), 40);
      rebuildGrid(fixture, 'QqEi', phase === 'reordered');
    }
    assert.equal(env.win.__medianAdSelection.nodes[0], originals[0], 'the original references must remain intact for ordinary identity protection');
    const reads = env.styleReadCount();
    assert.equal(execute(pickedValidateScript, env, selector), 40, `full live grid replacement ${phase} must validate`);
    assert.equal(env.styleReadCount() - reads, 40, 'a revalidation must read every current tile with a fresh bounded cache');
    if (phase === 'twice') {
      fixture.tiles.forEach(tile => { tile.computedStyle['background-image'] = 'url(https://changed.example/second.png)'; });
      assert.equal(execute(pickedValidateScript, env, selector), 40, 'consistent image rotation on the existing references must remain valid');
      rebuildGrid(fixture, 'ABcDeFG');
    }
    const hideReads = env.styleReadCount();
    assert.equal(execute(pickedHideScript, env, selector), 40, `full live grid replacement ${phase} must hide atomically`);
    assert.equal(env.styleReadCount() - hideReads, 40, 'hide must independently revalidate instead of reusing validation styles');
    fixture.tiles.forEach(tile => assert.equal(tile.style.display, 'none'));
    assert.equal(card.style.display, undefined); assert.equal(cover.style.display, undefined);
  }
  for (const mutation of ['content-id', 'content-child', 'content-text', 'keep', 'mixed-image', 'mixed-prefix', 'no-image',
    'static', 'changed-cell', 'duplicate-cell', 'moved', 'expanded', 'image-position', 'size', 'extra', 'missing']) {
    const fixture = liveGridFixture(), { env, card, cover } = fixture;
    const selector = selectGrid(fixture);
    assert.equal(execute(pickedValidateScript, env, selector), 40);
    rebuildGrid(fixture);
    const { tiles } = fixture;
    if (mutation === 'content-id') tiles[0].id = 'movie-card';
    if (mutation === 'content-child') tiles[0].appendChild(env.doc.createElement('img'));
    if (mutation === 'content-text') tiles[0].textContent = 'Movie title';
    if (mutation === 'keep') tiles[0].setAttribute('data-ad-keep', '');
    if (mutation === 'mixed-image') tiles[0].computedStyle['background-image'] = 'url(https://content.example/poster.png)';
    if (mutation === 'mixed-prefix') tiles[0].id = 'ABcDeFG_0_0';
    if (mutation === 'no-image') tiles.forEach(tile => { tile.computedStyle['background-image'] = 'none'; });
    if (mutation === 'static') tiles[0].computedStyle.position = 'static';
    if (mutation === 'changed-cell') tiles[0].id = 'QqEi_4_0';
    if (mutation === 'duplicate-cell') tiles[0].id = 'QqEi_0_01';
    if (mutation === 'moved') tiles[0].rect.top -= 10;
    if (mutation === 'expanded') tiles[0].rect.width += 10;
    if (mutation === 'image-position') tiles[0].style['background-position'] = '-10px -20px';
    if (mutation === 'size') tiles.forEach(tile => { tile.style['background-size'] = '420px 124px'; });
    if (mutation === 'extra') {
      const tile = env.doc.body.appendChild(env.doc.createElement('div'));
      tile.setAttribute('style', tiles[0].getAttribute('style')); tile.id = 'QqEi_4_0';
      tile.rect = { ...tiles[0].rect }; tile.computedStyle = { ...tiles[0].computedStyle }; tiles.push(tile);
    }
    if (mutation === 'missing') env.doc.body.removeChild(tiles.pop());
    assert.equal(execute(pickedValidateScript, env, selector), 0, `replacement with ${mutation} must fail complete family validation`);
    assert.equal(execute(pickedHideScript, env, selector), 0, `replacement with ${mutation} must fail atomic hiding`);
    assert.equal(env.doc.getElementById('__median_manual_blocks'), null, 'rejected replacement must not persist a cosmetic rule');
    tiles.forEach(tile => assert.equal(tile.style.display, undefined));
    assert.equal(card.style.display, undefined); assert.equal(cover.style.display, undefined);
  }
  {
    const fixture = liveGridFixture(), { env } = fixture;
    const selector = selectGrid(fixture);
    assert.equal(execute(pickedValidateScript, env, selector), 40);
    fixture.tiles[0].computedStyle.position = 'static';
    assert.equal(execute(pickedHideScript, env, selector), 0,
      'unchanged node references with mutated content/style must still receive fresh group verification');
    assert.equal(env.doc.getElementById('__median_manual_blocks'), null);
  }
  function fixture() {
    const env = setup(); const slot = env.doc.body.appendChild(env.doc.createElement('div')); slot.id = 'ad-slot';
    const first = slot.appendChild(env.doc.createElement('div'));
    const ad = slot.appendChild(env.doc.createElement('div')); ad.classList.add('banner');
    env.win.__medianAdSelection = { selector: q, nodes: [ad] };
    return { env, slot, first, ad };
  }
  {
    const { env, ad } = fixture();
    assert.equal(execute(pickedValidateScript, env), 1);
    assert.equal(execute(pickedHideScript, env), 1);
    assert.equal(ad.style.display, 'none', 'unchanged selected identity must hide successfully');
  }
  for (const mutation of ['replace', 'reorder', 'clear', 'wrong-selector']) {
    const { env, slot, first, ad } = fixture();
    assert.equal(execute(pickedValidateScript, env), 1);
    if (mutation === 'replace') {
      slot.removeChild(ad);
      slot.appendChild(env.doc.createElement('div')).classList.add('banner');
    } else if (mutation === 'reorder') {
      first.classList.add('banner'); slot.removeChild(first); slot.appendChild(first);
    } else if (mutation === 'clear') delete env.win.__medianAdSelection;
    else env.win.__medianAdSelection.selector = '.banner';
    assert.equal(execute(pickedValidateScript, env), 0, `stale ${mutation} selection must fail validation`);
    assert.equal(execute(pickedHideScript, env), 0, `stale ${mutation} selection must fail the atomic hide check`);
    assert.equal(env.doc.getElementById('__median_manual_blocks'), null, 'stale selection must not write a cosmetic rule');
    for (const node of slot.children) assert.equal(node.style.display, undefined, 'stale selection must not hide any replacement/content node');
  }
  {
    const { env, slot, ad } = fixture(); env.win.__medianAdSelection.selector = '.banner';
    assert.equal(execute(pickedValidateScript, env, '.banner'), 1);
    const extra = slot.appendChild(env.doc.createElement('div')); extra.classList.add('banner');
    assert.equal(execute(pickedValidateScript, env, '.banner'), 0, 'a new matching node must invalidate the selected set');
    assert.equal(execute(pickedHideScript, env, '.banner'), 0);
    assert.equal(ad.style.display, undefined); assert.equal(extra.style.display, undefined);
  }
  {
    const { env, slot, first, ad } = fixture(); first.classList.add('banner');
    env.win.__medianAdSelection = { selector: '.banner', nodes: [first, ad] };
    assert.equal(execute(pickedValidateScript, env, '.banner'), 2, 'identity guard must accept an exact future multi-node set');
    slot.removeChild(first); slot.appendChild(first);
    assert.equal(execute(pickedValidateScript, env, '.banner'), 0, 'matched-set ordering must be checked node by node');
    assert.equal(execute(pickedHideScript, env, '.banner'), 0);
  }
}

process.stdout.write('Ad picker behavior: passed (touch navigation, scrolling, selectors, iframe, cleanup, hide result)\n');
