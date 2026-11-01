'use strict';
// Compile the actual MainActivity scheduler against a deterministic UI/WebView host,
// then execute the Java-generated gate payloads in a browser-like JS context.
const fs = require('node:fs');
const path = require('node:path');
const cp = require('node:child_process');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const args = process.argv.slice(2);
const option = (name, fallback) => args.includes(name) ? args[args.indexOf(name) + 1] : fallback;
const project = path.resolve(__dirname, '../..');
const out = path.resolve(option('--out', path.join(require('node:os').tmpdir(), 'median-main-media-' + process.pid)));
const jdk = option('--jdk', process.env.JAVA_HOME || '');
fs.mkdirSync(out, { recursive: true });
const source = fs.readFileSync(path.join(project, 'app/src/main/java/com/xinyv/median/MainActivity.java'), 'utf8');
function extract(signature) {
  const begin = source.indexOf(signature);
  assert.ok(begin >= 0, 'missing production method ' + signature);
  let depth = 0, mode = '', escaped = false;
  for (let i = source.indexOf('{', begin); i < source.length; i++) {
    const c = source[i], next = source[i + 1];
    if (mode === 'line') { if (c === '\n') mode = ''; continue; }
    if (mode === 'block') { if (c === '*' && next === '/') { mode = ''; i++; } continue; }
    if (mode) {
      if (escaped) escaped = false;
      else if (c === '\\') escaped = true;
      else if (c === mode) mode = '';
      continue;
    }
    if (c === '/' && next === '/') { mode = 'line'; i++; }
    else if (c === '/' && next === '*') { mode = 'block'; i++; }
    else if (c === '"' || c === "'") mode = c;
    else if (c === '{') depth++;
    else if (c === '}' && --depth === 0) return source.slice(begin, i + 1);
  }
  throw new Error('Unterminated ' + signature);
}
function save(name, content) {
  const file = path.join(out, name);
  fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, content); return file;
}
const json = save('org/json/JSONObject.java', String.raw`package org.json;
public final class JSONObject {
 public static String quote(String value) {
  StringBuilder s=new StringBuilder("\"");
  for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c=='\"'||c=='\\')s.append('\\');
   if(c<32)s.append(String.format("\\u%04x",(int)c));else s.append(c);}
  return s.append('\"').toString();
 }
}
`);
const methods = [
  '    private boolean isCurrentLiveMediaDocument(',
  '    private static String liveMediaLoadGateScript(',
  '    private void installLiveMediaCapture(',
  '    private void scheduleLiveMediaCapture(',
  '    private static String navigationDocumentUrl('
].map(extract).join('\n');
const harness = save('MainMediaRegression.java', String.raw`package com.xinyv.median;
import java.util.*; import org.json.JSONObject;
public final class MainMediaRegression {
 private WebView webView=new WebView();
 private final Handler uiHandler=new Handler();
 private final Map<WebView,String> startedPageUrls=new HashMap<>();
 private long navigationSequence=1L,liveMediaCaptureSequence=-1L;
 private boolean activityDestroyed,pageFinished=true;
 MainMediaRegression(){startedPageUrls.put(webView,webView.url);}
 private boolean isCurrentPageCallback(WebView v,long s){return !activityDestroyed&&v!=null&&v==webView&&s==navigationSequence;}
 private boolean isNetworkPage(String u){return u!=null&&(u.startsWith("https://")||u.startsWith("http://"));}
` + methods + String.raw`
 void schedule(){scheduleLiveMediaCapture(webView,webView.url,navigationSequence);}
 void tick(){uiHandler.tasks.remove().run();}
 void result(String s){webView.callback.onReceiveValue(s);}
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  if(args.length>0){
   if("query".equals(args[0]))System.out.print(liveMediaLoadGateScript("https://page.example/watch",1L,false));
   else if("install".equals(args[0]))System.out.print(liveMediaLoadGateScript("https://page.example/watch",1L,true));
   else if("next-query".equals(args[0]))System.out.print(liveMediaLoadGateScript("https://page.example/watch",2L,false));
   else throw new IllegalArgumentException(args[0]);
   return;
  }
  int cases=0;
  MainMediaRegression h=new MainMediaRegression();h.schedule();
  check(h.webView.queries==0&&h.uiHandler.tasks.size()==1&&h.uiHandler.delays.get(0)==250L,"onPageFinished only queues the actual-load query");cases++;
  h.schedule();check(h.uiHandler.tasks.size()==1,"duplicate completion callback schedules once");cases++;
  h.tick();check(h.webView.queries==1&&h.webView.installs==0,"first query never submits media install");
  h.result("0");check(h.uiHandler.tasks.size()==1&&h.uiHandler.delays.get(1)==500L,"pending real load polls later");
  h.tick();h.result("1");check(h.webView.installs==1&&h.uiHandler.tasks.isEmpty(),"ready idle gate installs once and stops");cases++;
  MainMediaRegression loading=new MainMediaRegression();loading.pageFinished=false;loading.schedule();loading.tick();
  check(loading.webView.queries==1&&loading.webView.installs==0,"native pageFinished never substitutes for JS readiness");cases++;
  MainMediaRegression nav=new MainMediaRegression();nav.schedule();nav.navigationSequence++;nav.tick();
  check(nav.webView.queries==0,"navigation cancels queued query");cases++;
  MainMediaRegression resultNav=new MainMediaRegression();resultNav.schedule();resultNav.tick();resultNav.navigationSequence++;resultNav.result("1");
  check(resultNav.webView.installs==0&&resultNav.uiHandler.tasks.isEmpty(),"navigation cancels ready callback");cases++;
  MainMediaRegression switchTab=new MainMediaRegression();switchTab.schedule();switchTab.tick();WebView old=switchTab.webView;switchTab.webView=new WebView();old.callback.onReceiveValue("1");
  check(old.installs==0&&switchTab.webView.queries==0,"inactive view cannot install from late callback");cases++;
  MainMediaRegression switched=new MainMediaRegression();switched.schedule();switched.webView.url="https://changed.example/";switched.tick();
  check(switched.webView.queries==0,"URL mismatch cancels delayed query");cases++;
  MainMediaRegression started=new MainMediaRegression();started.schedule();started.tick();started.startedPageUrls.put(started.webView,"https://new.example/");started.result("1");
  check(started.webView.installs==0,"started document mismatch cancels ready callback");cases++;
  MainMediaRegression missing=new MainMediaRegression();missing.startedPageUrls.clear();missing.schedule();
  check(missing.uiHandler.tasks.isEmpty(),"missing started document cannot queue");cases++;
  MainMediaRegression destroy=new MainMediaRegression();destroy.schedule();destroy.tick();destroy.activityDestroyed=true;destroy.result("1");
  check(destroy.webView.installs==0&&destroy.uiHandler.tasks.isEmpty(),"destroy invalidates in-flight callback");cases++;
  MainMediaRegression destroyQueued=new MainMediaRegression();destroyQueued.schedule();destroyQueued.activityDestroyed=true;destroyQueued.tick();
  check(destroyQueued.webView.queries==0,"destroy invalidates queued query");cases++;
  MainMediaRegression retry=new MainMediaRegression();retry.schedule();while(!retry.uiHandler.tasks.isEmpty()){retry.tick();retry.result("0");}
  check(retry.webView.queries==24&&retry.webView.installs==0&&retry.uiHandler.delays.size()==24&&retry.uiHandler.delays.get(4)==2000L,"pending load and never-fired idle callbacks have finite backoff");cases++;
  for(String value:new String[]{null,"null","\"1\"","false","unexpected"}){
   MainMediaRegression invalid=new MainMediaRegression();invalid.schedule();invalid.tick();invalid.result(value);
   check(invalid.webView.installs==0&&invalid.uiHandler.tasks.isEmpty(),"invalid callback stops safely");
  }cases++;
  MainMediaRegression error=new MainMediaRegression();error.webView.fail=true;error.schedule();error.tick();
  check(error.uiHandler.tasks.isEmpty()&&error.webView.installs==0,"provider evaluation exception stops safely");cases++;
  MainMediaRegression fragment=new MainMediaRegression();fragment.webView.url+="#player";fragment.schedule();fragment.tick();fragment.result("1");
  check(fragment.webView.installs==1,"same-document fragment does not cancel installation");cases++;
  MainMediaRegression next=new MainMediaRegression();next.schedule();next.tick();next.result("1");next.navigationSequence++;next.schedule();
  check(next.uiHandler.tasks.size()==1,"new navigation can schedule after prior installation");cases++;
  MainMediaRegression internal=new MainMediaRegression();internal.webView.url="about:blank";internal.schedule();
  check(internal.uiHandler.tasks.isEmpty(),"internal pages never schedule");cases++;
  System.out.println("MainMediaRegression passed: "+cases+" production-method scheduling/navigation cases");
 }
}
interface ValueCallback<T>{void onReceiveValue(T value);}
final class WebView {
 String url="https://page.example/watch",lastScript;int queries,installs;boolean fail;ValueCallback<String> callback;
 String getUrl(){return url;}
 void evaluateJavascript(String script,ValueCallback<String> c){
  if(fail)throw new IllegalStateException("destroyed provider");queries++;lastScript=script;
  if(c==null)installs++;else callback=c;
 }
}
final class Handler {
 final Queue<Runnable> tasks=new ArrayDeque<>();final List<Long> delays=new ArrayList<>();
 void postDelayed(Runnable r,long delay){tasks.add(r);delays.add(delay);}
}
`);
function run(name, arguments_) {
  const executable = jdk ? path.join(jdk, 'bin', name + (process.platform === 'win32' ? '.exe' : '')) : name;
  const result = cp.spawnSync(executable, arguments_, { encoding: 'utf8', windowsHide: true });
  if (result.error || result.status !== 0) throw result.error || new Error(name + ' exit ' + result.status + '\n' + result.stdout + result.stderr);
  return result.stdout;
}
run('javac', ['-encoding', 'UTF-8', '--release', '17', '-d', out, json, harness,
  path.join(project, 'app/src/main/java/com/xinyv/median/MediaProbeScript.java'),
  path.join(project, 'tools/tests/MediaProbeScriptSelfTest.java')]);
process.stdout.write(run('java', ['-cp', out, 'com.xinyv.median.MainMediaRegression']));
const dump = mode => run('java', ['-cp', out, 'com.xinyv.median.MainMediaRegression', mode]);
const queryScript = dump('query'), installScript = dump('install'), nextQueryScript = dump('next-query');
for (const script of [queryScript, installScript, nextQueryScript]) new vm.Script(script);
let cases = 0;
function fixture(options = {}) {
  const stats = { installs: 0, idle: [], frames: [], reads: 0 };
  const document = {
    readyState: options.state || 'complete',
    addEventListener() { stats.installs++; },
    querySelectorAll() { throw new Error('automatic gate must not scan the DOM'); }
  };
  const performance = {
    timing: options.legacy === undefined ? { loadEventEnd: 100 } : options.legacy,
    getEntriesByType(type) {
      if (type === 'navigation') { stats.reads++; if (options.navigationThrows) throw new Error('unsupported'); return options.navigation === undefined ? [{ loadEventEnd: 100 }] : options.navigation; }
      assert.equal(type, 'resource'); assert.ok(stats.installs > 0, 'resources are read only inside the real installer'); return [];
    }
  };
  if (options.noEntries) delete performance.getEntriesByType;
  const state = {
    document, performance: options.noPerformance ? undefined : performance,
    location: { href: options.href || 'https://page.example/watch', protocol: 'https:', hostname: 'page.example' },
    URL, JSON, Number, Object, Array, RegExp, String, isFinite,
    setTimeout() { throw new Error('no persistent JS timers'); },
    setInterval() { throw new Error('no persistent JS timers'); },
    MutationObserver() { throw new Error('no new persistent observer'); }
  };
  if (options.idle !== false) state.requestIdleCallback = (callback, config) => { assert.equal(config.timeout, 1000); stats.idle.push(callback); };
  if (options.frames !== false) state.requestAnimationFrame = callback => { stats.frames.push(callback); };
  state.window = state;
  vm.createContext(state);
  return { state, stats, query: () => vm.runInContext(queryScript, state), install: () => vm.runInContext(installScript, state) };
}
function expectPending(options) {
  const f = fixture(options);
  assert.equal(f.query(), 0); assert.equal(f.install(), 0);
  assert.equal(f.stats.installs, 0); assert.equal(f.stats.idle.length + f.stats.frames.length, 0); cases++;
}
expectPending({ state: 'loading' });
expectPending({ state: 'interactive' });
for (const end of [0, -1, NaN, Infinity, undefined, '100']) expectPending({ navigation: [{ loadEventEnd: end }] });
expectPending({ noPerformance: true });
expectPending({ href: 'https://other.example/watch' });
// A modern incomplete entry must not fall back to a completed legacy timing.
expectPending({ navigation: [{ loadEventEnd: 0 }], legacy: { loadEventEnd: 900 } });
const idle = fixture();
assert.equal(idle.install(), 0, 'a ready load without a scheduled gate cannot install');
assert.equal(idle.query(), 0); assert.equal(idle.query(), 0);
assert.equal(idle.stats.idle.length, 1, 'pending polls do not duplicate idle callbacks');
assert.equal(idle.install(), 0); assert.equal(idle.stats.installs, 0);
idle.stats.idle.shift()();
assert.equal(idle.stats.installs, 0, 'deferred callback only marks ready; native callback must approve install');
assert.equal(idle.query(), 1); assert.equal(idle.install(), 1);
assert.equal(idle.stats.installs, 2);
assert.equal(idle.install(), 1); assert.equal(idle.stats.installs, 2, 'production media install remains idempotent');cases++;
const late = fixture({ state: 'loading', navigation: [{ loadEventEnd: 0 }] });
assert.equal(late.query(), 0);late.state.document.readyState = 'complete';
assert.equal(late.query(), 0);assert.equal(late.stats.idle.length, 0, 'complete is insufficient while load handlers still run');
late.state.performance.getEntriesByType = type => type === 'navigation' ? [{ loadEventEnd: 250 }] : [];
assert.equal(late.query(), 0);late.stats.idle.shift()();assert.equal(late.query(), 1);assert.equal(late.install(), 1);cases++;
const frames = fixture({ idle: false });
assert.equal(frames.query(), 0);assert.equal(frames.stats.frames.length, 1);frames.stats.frames.shift()();
assert.equal(frames.query(), 0);assert.equal(frames.stats.frames.length, 1);assert.equal(frames.install(), 0);
frames.stats.frames.shift()();assert.equal(frames.query(), 1);assert.equal(frames.install(), 1);cases++;
for (const options of [{ navigation: [] }, { noEntries: true }, { navigationThrows: true }]) {
  const old = fixture(options);assert.equal(old.query(), 0);old.stats.idle.shift()();
  assert.equal(old.query(), 1);assert.equal(old.install(), 1);cases++;
}
expectPending({ navigation: [], legacy: { loadEventEnd: 0 } });
expectPending({ noEntries: true, legacy: null });
const noSchedule = fixture({ idle: false, frames: false });
assert.equal(noSchedule.query(), 0);assert.equal(noSchedule.query(), 0);assert.equal(noSchedule.install(), 0);cases++;
const wrong = fixture();wrong.query();wrong.state.location.href = 'https://other.example/watch';wrong.stats.idle.shift()();
assert.equal(wrong.install(), 0);assert.equal(wrong.stats.installs, 0);cases++;
const reload = fixture();reload.query();const oldIdle = reload.stats.idle.shift();
reload.state.document = { ...reload.state.document };oldIdle();
assert.equal(reload.install(), 0, 'same-URL document replacement invalidates old gate');cases++;
const generation = fixture();generation.query();const previousIdle = generation.stats.idle.shift();
assert.equal(vm.runInContext(nextQueryScript, generation.state), 0);previousIdle();
assert.equal(generation.install(), 0, 'old navigation token cannot approve replacement gate');cases++;
const hash = fixture({ href: 'https://page.example/watch#video' });hash.query();hash.stats.idle.shift()();
assert.equal(hash.install(), 1);cases++;
const raced = fixture();raced.query();raced.stats.idle.shift()();assert.equal(raced.query(), 1);
raced.state.performance.getEntriesByType = () => [{ loadEventEnd: 0 }];
assert.equal(raced.install(), 0, 'install payload rechecks load state after native readiness callback');cases++;
const thrown = fixture();thrown.state.requestIdleCallback = () => { throw new Error('provider'); };
assert.equal(thrown.query(), 0);assert.equal(thrown.install(), 0);cases++;
console.log('MainMedia JavaScript gate passed: ' + cases + ' production-payload readiness/frame/navigation cases');
process.stdout.write(run('java', ['-cp', out, 'com.xinyv.median.MediaProbeScriptSelfTest']));
save('media-install.js', run('java', ['-cp', out, 'com.xinyv.median.MediaProbeScriptSelfTest', 'install']));
save('media-build.js', run('java', ['-cp', out, 'com.xinyv.median.MediaProbeScriptSelfTest', 'build']));
const media = cp.spawnSync(process.execPath, [path.join(__dirname, 'media_probe_behavior_test.js'), path.join(out, 'media-install.js'), path.join(out, 'media-build.js')], { encoding: 'utf8', windowsHide: true });
if (media.error || media.status !== 0) throw media.error || new Error(media.stdout + media.stderr);
process.stdout.write(media.stdout);
