'use strict';
const fs = require('fs');
const vm = require('vm');
const [detectPath, fillPath, capturePath, overwritePath, saveFieldsPath] = process.argv.slice(2);
if (!capturePath) throw new Error('expected detect, fill and capture scripts');
const detect = fs.readFileSync(detectPath, 'utf8');
const fill = fs.readFileSync(fillPath, 'utf8');
const capture = fs.readFileSync(capturePath, 'utf8');
const overwrite = overwritePath ? fs.readFileSync(overwritePath, 'utf8') : '';
const saveFields = saveFieldsPath ? fs.readFileSync(saveFieldsPath, 'utf8') : '';

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
    addEventListener(type, listener) { (handlers[type] || (handlers[type] = [])).push(listener); },
    removeEventListener(type, listener) {
      const list = handlers[type] || [];
      const index = list.indexOf(listener);
      if (index >= 0) list.splice(index, 1);
    }
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
if (overwrite) {
  state = JSON.parse(run(overwrite, env));
  assert(state.filled && username.value === 'chosen@example.com' && password.value === 'chosen-secret', 'explicit account choice did not overwrite stale fields');
}

// A global page scan must not choose an unrelated subscription/search email field.
const subscribe = new FakeInput('email', '', 'newsletter_email');
const unrelatedPassword = new FakeInput('password', '', 'password');
env = environment([subscribe, unrelatedPassword], '/checkout');
state = JSON.parse(run(fill, env));
assert(state.filled && !state.username && subscribe.value === '', 'unrelated page email field was selected as username');

// A username field explicitly associated with the password form wins over other forms.
const loginForm = {};
const subscribeForm = {};
const otherSubscribe = new FakeInput('email', 'email', 'subscribe_email');
otherSubscribe.form = subscribeForm;
const loginName = new FakeInput('text', 'username', 'login_name');
loginName.form = loginForm;
const formPassword = new FakeInput('password', 'current-password', 'password');
formPassword.form = loginForm;
env = environment([otherSubscribe, loginName, formPassword]);
state = JSON.parse(run(fill, env));
assert(state.filled && state.username && loginName.value === 'a"b@example.com' && otherSubscribe.value === '', 'username selection escaped the password form');

// Username-only pages may be detected, but a stored password fill must never write into them.
const genericEmail = new FakeInput('email', '', 'email');
env = environment([genericEmail], '/signin/identifier');
state = JSON.parse(run(fill, env));
assert(!state.filled && !state.username && genericEmail.value === '', 'password fill wrote username without a password field');

if (saveFields) {
  const otpOnly = new FakeInput('password', 'one-time-code', 'otp');
  const otpEmail = new FakeInput('email', '', 'email');
  env = environment([otpEmail, otpOnly], '/verify');
  const capturedOtp = JSON.parse(run(saveFields, env));
  assert(capturedOtp[0] === '' && capturedOtp[1] === '', 'save-field probe captured an OTP-only page');

  const newPassword = new FakeInput('password', 'new-password', 'new_password');
  const confirmPassword = new FakeInput('password', '', 'confirm_password');
  const repeatPassword = new FakeInput('password', '', 'repeat_password');
  const verification = new FakeInput('text', '', 'verification_code');
  env = environment([verification, newPassword, confirmPassword, repeatPassword], '/register');
  const capturedRegistration = JSON.parse(run(saveFields, env));
  assert(capturedRegistration[0] === '' && capturedRegistration[1] === '', 'save-field probe captured a new/confirm/repeat password or verification code');

  const loginPassword = new FakeInput('password', 'current-password', 'password');
  loginPassword.value = 'login-secret';
  const otpPassword = new FakeInput('password', 'one-time-code', 'otp');
  otpPassword.value = '123456';
  env = environment([otpPassword, loginPassword]);
  const capturedLogin = JSON.parse(run(saveFields, env));
  assert(capturedLogin[1] === 'login-secret' && capturedLogin[1] !== '123456', 'save-field probe selected an OTP over a login password');
}

username.value = 'saved@example.com';
password.value = 'secret-one';
env = environment([username, password]);
run(capture, env);
assert(!capture.includes('prompt'), 'page credential flow must not use window.prompt');
assert(!capture.includes('0123456789abcdef0123456789abcdef'), 'capture script must not embed a page-visible token');
const listenerCount = Object.values(env.handlers).reduce((sum, list) => sum + list.length, 0);
assert(listenerCount === 0, 'disabled capture script must not install page listeners');
fire(env, 'focusin', password);
fire(env, 'submit', password);
assert(env.prompts.length === 0, 'page events must not trigger native credential prompts');
assert(!env.context.__medianCredentialCapture, 'page-visible credential state must not be created');
console.log('Credential autofill behavior passed');
