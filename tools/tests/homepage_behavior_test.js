'use strict';

const fs = require('fs');
const vm = require('vm');

const script = fs.readFileSync(process.argv[2], 'utf8');

function requireTrue(value, message) {
  if (!value) throw new Error(message);
}

function run(token) {
  const calls = [];
  const form = {};
  const query = {value: 'cold start'};
  const brand = {style: {}};
  const chips = ['google', 'bing'].map(function (engine) {
    return {
      classList: {toggle: function () {}},
      getAttribute: function (name) { return name === 'data-e' ? engine : ''; }
    };
  });
  const links = ['median://folder?id=f-work', 'median://open?url=https%3A%2F%2Fexample.com%2F']
    .map(function (href) {
      return {getAttribute: function (name) { return name === 'href' ? href : ''; }};
    });
  const location = {href: ''};
  const document = {
    querySelector: function (selector) {
      if (selector === '[name=median-home-token]') return {content: token};
      if (selector === '.brand') return brand;
      return null;
    },
    querySelectorAll: function (selector) {
      if (selector === '.chip') return chips;
      if (selector === 'a[href^="median:"]') return links;
      return [];
    },
    getElementById: function (id) {
      if (id === 'form') return form;
      if (id === 'q') return query;
      return null;
    }
  };
  const context = {
    document: document,
    location: location,
    prompt: function (message, command) { calls.push({message: message, command: command}); return ''; },
    encodeURIComponent: encodeURIComponent,
    setTimeout: function (callback) { callback(); return 1; },
    clearTimeout: function () {},
    setInterval: function () {},
    Date: Date
  };
  vm.runInNewContext(script, context);
  return {calls: calls, form: form, query: query, brand: brand, chips: chips, links: links, location: location};
}

function event() { return {preventDefault: function () {}}; }

const normal = run('token');
requireTrue(typeof normal.form.onsubmit === 'function', 'search submit handler missing');
normal.form.onsubmit(event());
requireTrue(normal.calls.length === 1 && normal.calls[0].message === 'token' &&
  normal.calls[0].command.indexOf('median://search?engine=google&q=cold%20start') === 0,
  'search command was not sent through the token channel');
normal.links[1].onclick.call(normal.links[1], event());
requireTrue(normal.calls[1].command === 'median://open?url=https%3A%2F%2Fexample.com%2F',
  'bookmark command missing');
normal.chips[1].onclick.call(normal.chips[1], event());
requireTrue(normal.calls[2].command === 'median://engine?name=bing', 'engine command missing');
normal.brand.oncontextmenu(event());
requireTrue(normal.calls[3].command === 'median://folders', 'folder command missing');
requireTrue(normal.location.href === '', 'normal homepage unexpectedly started a scheme navigation');

console.log('Homepage behavior test passed');
