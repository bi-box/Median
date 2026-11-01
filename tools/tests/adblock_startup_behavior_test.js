'use strict';

// Execute the production loader and startup methods with controllable worker/UI queues and real
// filtering snapshots. A stalled subscription read must not delay custom protection/navigation.
const fs = require('fs');
const path = require('path');
const os = require('os');
const cp = require('child_process');
const args = process.argv.slice(2);
const option = (name, fallback) => args.includes(name) ? args[args.indexOf(name) + 1] : fallback;
const repo = path.resolve(__dirname, '../..');
const out = path.resolve(option('--out', fs.mkdtempSync(path.join(os.tmpdir(), 'median-adblock-startup-'))));
const jdk = option('--jdk', process.env.JAVA_HOME || '');
const executable = name => jdk ? path.join(jdk, 'bin', name + (process.platform === 'win32' ? '.exe' : '')) : name;
const src = path.join(repo, 'app/src/main/java/com/xinyv/median');
const activity = fs.readFileSync(path.join(src, 'MainActivity.java'), 'utf8');
function extract(signature) {
  const start = activity.indexOf(signature);
  if (start < 0) throw new Error('Missing production method: ' + signature);
  const brace = activity.indexOf('{', start);
  let depth = 0;
  for (let at = brace; at < activity.length; at++) {
    if (activity[at] === '{') depth++;
    else if (activity[at] === '}' && --depth === 0) return activity.slice(start, at + 1);
  }
  throw new Error('Unterminated production method: ' + signature);
}
const methods = [
  '    private void rebuildAdBlockRulesAsync(final boolean notifyUser)',
  '    private void onAdBlockRulesLoaded(final boolean notifyUser)',
  '    private void onAdBlockRulesLoadFailed()',
  '    private void maybeStartFastStartup()',
  '    private void maybeCompleteStartup()',
  '    private void completeStartup(Bundle savedState, String externalUrl)',
  '    private static boolean restoreWebViewState(WebView view, Bundle state)',
  '    private void scheduleDeferredStartupWork()',
  '    private boolean executeTask(ExecutorService executor, Runnable task)'
].map(extract).join('\n');
const deferred = extract('    private final Runnable deferredStartup = new Runnable()') + ';';
const stateStart = activity.indexOf('    private final StartupReadiness startupReadiness =');
const stateEnd = activity.indexOf(';', stateStart);
const readiness = activity.slice(stateStart, stateEnd + 1);
const onCreate = extract('    protected void onCreate(Bundle state)');
if (!(onCreate.indexOf('rebuildAdBlockRulesAsync(false);') > onCreate.indexOf('scriptExecutor =') &&
      onCreate.indexOf('rebuildAdBlockRulesAsync(false);') < onCreate.indexOf('beginStartupLoad(') &&
      onCreate.indexOf('beginStartupLoad(') < onCreate.indexOf('buildUi();')))
  throw new Error('Local rules must start on the worker before store decoding/native UI construction');
const protectionStart = onCreate.indexOf('adBlockEnabled = prefs.getBoolean("adblock", true);');
const protectionEnd = onCreate.indexOf(';', onCreate.indexOf('startupReadiness.set(', protectionStart));
const protection = onCreate.slice(protectionStart, protectionEnd + 1);
const onDestroy = extract('    protected void onDestroy()');
if (onDestroy.indexOf('adBlockRuleLoader.close();') < 0 ||
    onDestroy.indexOf('adBlockRuleLoader.close();') > onDestroy.indexOf('services.close();'))
  throw new Error('Pending rule publication must be invalidated before services close');
fs.mkdirSync(out, { recursive: true });
const harness = path.join(out, 'AdBlockStartupBehaviorHarness.java');
const template = fs.readFileSync(path.join(__dirname, 'AdBlockStartupBehaviorHarness.java.in'), 'utf8');
fs.writeFileSync(harness, template.replace('@@METHODS@@', methods).replace('@@DEFERRED@@', deferred)
  .replace('@@READINESS@@', readiness).replace('@@PROTECTION@@', protection));
function stub(relative, contents) {
  const file = path.join(out, relative);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, contents);
  return file;
}
const stubs = [
  stub('android/content/SharedPreferences.java', `package android.content;
public interface SharedPreferences {
  String getString(String key, String fallback);
  boolean getBoolean(String key, boolean fallback);
}`),
  stub('android/os/Handler.java', `package android.os;
public abstract class Handler { public abstract boolean post(Runnable action); }`),
  stub('android/os/Process.java', `package android.os;
public final class Process {
  public static final int THREAD_PRIORITY_BACKGROUND = 10;
  public static int priorityChanges;
  public static volatile Thread lastWorker;
  public static void setThreadPriority(int priority) {
    if (priority != THREAD_PRIORITY_BACKGROUND) throw new AssertionError("Rule worker priority");
    priorityChanges++;
    lastWorker = Thread.currentThread();
  }
}`)
];
function run(command, arguments_) {
  const result = cp.spawnSync(executable(command), arguments_, { encoding: 'utf8', windowsHide: true });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(result.stdout + result.stderr);
  if (result.stdout) process.stdout.write(result.stdout);
}
try {
  run('javac', ['-encoding', 'UTF-8', '--release', '17', '-d', out, harness, ...stubs,
    path.join(__dirname, 'adblock_stubs/android/net/Uri.java'),
    path.join(src, 'AdBlockEngine.java'), path.join(src, 'AdBlockRuleReload.java'), path.join(src, 'AdBlockRuleLoader.java'),
    path.join(src, 'StartupReadiness.java'), path.join(src, 'StartupNavigationPolicy.java'),
    path.join(__dirname, 'AdBlockStartupBehaviorSelfTest.java')]);
  run('java', ['-Xmx768m', '-cp', out, 'com.xinyv.median.AdBlockStartupBehaviorSelfTest']);
} finally {
  if (!args.includes('--out')) {
    if (path.dirname(out) !== path.resolve(os.tmpdir()) || !path.basename(out).startsWith('median-adblock-startup-'))
      throw new Error('Refusing cleanup outside generated test directory');
    fs.rmSync(out, { recursive: true, force: true });
  }
}
