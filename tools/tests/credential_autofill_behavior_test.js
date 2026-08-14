'use strict';
const fs = require('fs');
const vm = require('vm');
const [detectPath, fillPath, capturePath] = process.argv.slice(2);
if (!capturePath) throw new Error('expected detect, fill and capture scripts');
const detect = fs.readFileSync(detectPath, 'utf8');
const fill = fs.readFileSync(fillPath, 'utf8');
const capture = fs.readFileSync(capturePath, 'utf8');

class FakeEvent {
  constructor(type, options) { this.type = type; Object.assign(this, options || {}); }
}
class FakeInput {
  constructor(type, autocomplete, name) {
    this._value = '';
    this.type = type;
    this.autocomplete = autocomplete || '';
    this.name = name || '';
    this.id = '';
    this.placeholder = '';
    this.tagName = 'INPUT';
    this.nodeType = 1;
    this.disabled = false;
    this.readOnly = false;
    this.offsetParent = {};
    this.form = null;
    this.events = [];
  }
  get value() { return this._value; }
  set value(value) { this._value = String(value); }
  getClientRects() { return [1]; }
  getAttribute(name) { return name === 'aria-label' ? '' : null; }
  dispatchEvent(event) { this.events.push(event.type); return true; }
  focus() { this.focused = true; }
}
function environment(inputs, path) {
  const handlers = Object.create(null);
  const prompts = [];
  const storage = new Map();
  const document = {
    querySelectorAll(selector) { return selector === 'input' ? inputs : []; },
    addEventListener(type, listener) { (handlers[type] || (handlers[type] = [])).push(listener); }
  };
  const context = {
    document,
    location: { protocol: 'https:', hostname: 'accounts.example.com', pathname: path || '/signin', href: 'https://accounts.example.com' + (path || '/signin') },
    sessionStorage: {
      setItem(key, value) { storage.set(key, String(value)); },
      getItem(key) { return storage.has(key) ? storage.get(key) : null; }
    },
    prompt(message) { prompts.push(message); return ''; },
    Event: FakeEvent,
    InputEvent: FakeEvent,
    console,
    Date,
    JSON,
    String,
    Number,
    Object,
    Array,
    RegExp,
    isFinite
  };
  context.window = context;
  vm.createContext(context);
  return { context, handlers, prompts };
}
function run(script, env) { return vm.runInContext(script, env.context); }
function fire(env, type, target) {
  for (const listener of env.handlers[type] || []) listener({ type, target, isTrusted: true });
}
function assert(value, message) { if (!value) throw new Error(message); }

const username = new FakeInput('email', 'username', 'email');
const password = new FakeInput('password', 'current-password', 'password');
const newPassword = new FakeInput('password', 'new-password', 'new_password');
const otp = new FakeInput('password', 'one-time-code', 'otp');
let env = environment([username, password, newPassword, otp]);
let state = JSON.parse(run(detect, env));
assert(state.login && state.passwords === 1, 'must select only the current password field');

const emailOnly = new FakeInput('email', '', 'email');
env = environment([emailOnly], '/v3/signin/identifier');
state = JSON.parse(run(detect, env));
assert(state.usernameOnly, 'multi-step sign-in username was not detected');

env = environment([username, password]);
username.value = '';
password.value = '';
state = JSON.parse(run(fill, env));
assert(state.filled && state.username, 'login form was not filled');
assert(username.value === 'a"b@example.com' && password.value === 'line1\nline2', 'quoted credential changed');
assert(username.events.includes('input') && password.events.includes('change'), 'framework-compatible events missing');
username.value = 'person-typed';
password.value = 'person-secret';
state = JSON.parse(run(fill, env));
assert(!state.filled && username.value === 'person-typed' && password.value === 'person-secret', 'existing user input was overwritten');

username.value = 'saved@example.com';
password.value = 'secret-one';
env = environment([username, password]);
run(capture, env);
const listenerCount = Object.values(env.handlers).reduce((sum, list) => sum + list.length, 0);
fire(env, 'focusin', password);
fire(env, 'submit', password);
assert(env.prompts.some(value => value.startsWith('__MEDIAN_AUTOFILL__')), 'late autofill signal missing');
assert(env.prompts.some(value => value.startsWith('__MEDIAN_CREDENTIAL__')), 'trusted login capture missing');

const updatedCapture = capture.replace('0123456789abcdef0123456789abcdef', 'fedcba9876543210fedcba9876543210');
run(updatedCapture, env);
assert(Object.values(env.handlers).reduce((sum, list) => sum + list.length, 0) === listenerCount, 'capture listeners duplicated');
password.value = 'secret-two';
fire(env, 'submit', password);
const lastCapture = env.prompts.filter(value => value.startsWith('__MEDIAN_CREDENTIAL__')).pop();
assert(lastCapture.includes('fedcba9876543210fedcba9876543210'), 'reinstalled capture did not rotate its token');
console.log('Credential autofill behavior passed');
