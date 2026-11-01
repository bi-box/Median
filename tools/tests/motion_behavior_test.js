'use strict';

// Executes the current production methods, not a reimplementation of their decisions.
// The Java fixture models only asynchronous frame/alpha animation and translated-child input.
const fs = require('fs');
const path = require('path');
const os = require('os');
const crypto = require('crypto');
const childProcess = require('child_process');

const args = process.argv.slice(2);
function option(name, fallback) {
  const index = args.indexOf(name);
  if (index < 0) return fallback;
  if (!args[index + 1] || args[index + 1].startsWith('--')) throw new Error(`Missing ${name} value`);
  return args[index + 1];
}
const repo = path.resolve(__dirname, '../..');
const javaDir = path.join(repo, 'app/src/main/java/com/xinyv/median');
const mainPath = option('--main', path.join(javaDir, 'MainActivity.java'));
const motionPath = option('--motion', path.join(javaDir, 'Motion.java'));
const jdk = option('--jdk', process.env.JAVA_HOME || '');
const out = path.resolve(option('--out', fs.mkdtempSync(path.join(os.tmpdir(), 'median-motion-'))));
fs.mkdirSync(out, { recursive: true });
const javac = jdk ? path.join(jdk, 'bin', process.platform === 'win32' ? 'javac.exe' : 'javac') : 'javac';
const java = jdk ? path.join(jdk, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';

// Braces in comments/string/char literals must not truncate an anonymous listener or Runnable.
function extractMethod(source, signature) {
  const start = source.indexOf(signature);
  if (start < 0 || source.indexOf(signature, start + signature.length) >= 0)
    throw new Error(`Expected exactly one production method: ${signature}`);
  const open = source.indexOf('{', start);
  let depth = 0, mode = 'code', escaped = false;
  for (let i = open; i < source.length; i++) {
    const c = source[i], next = source[i + 1];
    if (mode === 'line') { if (c === '\n') mode = 'code'; continue; }
    if (mode === 'block') { if (c === '*' && next === '/') { mode = 'code'; i++; } continue; }
    if (mode === 'string' || mode === 'char') {
      if (escaped) escaped = false;
      else if (c === '\\') escaped = true;
      else if (c === (mode === 'string' ? '"' : "'")) mode = 'code';
      continue;
    }
    if (c === '/' && next === '/') { mode = 'line'; i++; continue; }
    if (c === '/' && next === '*') { mode = 'block'; i++; continue; }
    if (c === '"') { mode = 'string'; continue; }
    if (c === "'") { mode = 'char'; continue; }
    if (c === '{') depth++;
    if (c === '}' && --depth === 0) return source.slice(start, i + 1);
  }
  throw new Error(`Unclosed method: ${signature}`);
}

const mainSource = fs.readFileSync(mainPath, 'utf8');
const motionSource = fs.readFileSync(motionPath, 'utf8');
const methods = {
  sheet: extractMethod(mainSource, 'private void installSheetDrag('),
  progress: extractMethod(mainSource, 'private void scheduleProgressUpdate('),
  syncProgress: extractMethod(mainSource, 'private void syncChromeProgress('),
  cancelAnimation: extractMethod(mainSource, 'private static void cancelViewAnimationIfRunning('),
  drawable: extractMethod(motionSource, 'static void animateProgress(')
};
const immersiveMethods = {
  immersiveTouch: extractMethod(mainSource, 'private void observeImmersiveTouch('),
  hideChrome: extractMethod(mainSource, 'private void hideBrowserChrome('),
  showChrome: extractMethod(mainSource, 'private void showBrowserChrome('),
  settleChrome: extractMethod(mainSource, 'private void settleImmersiveChrome('),
  chromeOffset: extractMethod(mainSource, 'private float chromeOffsetFromViews('),
  cancelChrome: extractMethod(mainSource, 'private void cancelChromeAnimationsForGesture('),
  applyChrome: extractMethod(mainSource, 'private void applyChromeGestureOffset('),
  syncProgress: methods.syncProgress,
  cancelAnimation: methods.cancelAnimation,
  resetChrome: extractMethod(mainSource, 'private void resetImmersiveChromeVisual('),
  clamp: extractMethod(mainSource, 'private float clamp01('),
  updateLayout: extractMethod(mainSource, 'private void updateImmersivePageLayout('),
  pageMargins: extractMethod(mainSource, 'private void updatePageMargins('),
  systemBackgrounds: extractMethod(mainSource, 'private void updateSystemBarBackgrounds('),
  progress: methods.progress,
  drawable: methods.drawable
};
const template = fs.readFileSync(path.join(__dirname, 'MotionBehaviorHarness.java.in'), 'utf8');
const immersiveTemplate = fs.readFileSync(path.join(__dirname, 'ImmersiveBehaviorHarness.java.in'), 'utf8');
const allMethods = { ...methods, ...immersiveMethods };
const metadata = {
  mainPath, motionPath,
  mainSha256: crypto.createHash('sha256').update(mainSource).digest('hex'),
  motionSha256: crypto.createHash('sha256').update(motionSource).digest('hex'),
  methodSha256: Object.fromEntries(Object.entries(allMethods).map(([name, text]) =>
    [name, crypto.createHash('sha256').update(text).digest('hex')])),
  limitations: 'Host behavior model; no Android renderer, real frame timing, or phone input coverage.'
};
fs.writeFileSync(path.join(out, 'source-metadata.json'), JSON.stringify(metadata, null, 2));
for (const [name, method] of Object.entries(allMethods)) fs.writeFileSync(path.join(out, `${name}.extracted.java`), method);

function execute(name, selected, expectedFailure, selectedTemplate = template, className = 'MotionBehaviorSelfTest') {
  const suiteDir = path.join(out, name);
  fs.mkdirSync(suiteDir, { recursive: true });
  let fixture = selectedTemplate;
  for (const key of Object.keys(selected)) {
    if (key === 'cancelAnimation') {
      fixture = fixture.replace(`public final class ${className} {`,
        `public final class ${className} {\n${selected[key]}\n`);
      continue;
    }
    fixture = fixture.replace(`/* @${key.toUpperCase()}@ */`, selected[key]);
  }
  // Keep the production cancellation helper intact. The existing host animator models
  // Android's asynchronous property animation; expose its Android type and listener API.
  fixture = fixture.replaceAll(/\bAnimator\b/g, 'ViewPropertyAnimator');
  const animatorConstructor = 'ViewPropertyAnimator(View view) { this.view = view; }';
  if (!fixture.includes(animatorConstructor)) throw new Error('Property animator fixture boundary missing');
  fixture = fixture.replace(animatorConstructor, `${animatorConstructor}
        Object listener, updateListener;
        ViewPropertyAnimator setListener(Object value) { listener = value; return this; }
        ViewPropertyAnimator setUpdateListener(Object value) { updateListener = value; return this; }`);
  const fixturePath = path.join(suiteDir, `${className}.java`);
  fs.writeFileSync(fixturePath, fixture);
  const compile = childProcess.spawnSync(javac, ['-encoding', 'UTF-8', '-d', suiteDir, fixturePath],
    { encoding: 'utf8', timeout: 60000, windowsHide: true });
  if (compile.error || compile.status !== 0) {
    process.stdout.write(compile.stdout || ''); process.stderr.write(compile.stderr || '');
    throw compile.error || new Error(`${name}: javac failed (${compile.status})`);
  }
  const run = childProcess.spawnSync(java, ['-cp', suiteDir, className],
    { encoding: 'utf8', timeout: 30000, windowsHide: true });
  const log = (run.stdout || '') + (run.stderr || '');
  fs.writeFileSync(path.join(suiteDir, 'result.log'), log);
  process.stdout.write(`${name}:\n${log}`);
  if (run.error) throw run.error;
  if (expectedFailure && run.status === 0) throw new Error(`${name}: regression mutation escaped detection`);
  if (!expectedFailure && run.status !== 0) return false;
  if (expectedFailure) process.stdout.write(`${name}: expected regression detected\n`);
  return true;
}

process.stdout.write(`Production method extraction; evidence: ${out}\n`);
const passed = execute('current', methods, false);
const immersivePassed = execute('immersive-current', immersiveMethods, false,
  immersiveTemplate, 'ImmersiveBehaviorSelfTest');
if (passed && args.includes('--verify-mutations')) {
  execute('mutation-local-coordinates', { ...methods,
    sheet: methods.sheet.replaceAll('.getRawX()', '.getX()').replaceAll('.getRawY()', '.getY()') }, true);
  const withoutPanelCancel = methods.sheet.replace('cancelViewAnimationIfRunning(panel);', '/* mutation: no panel cancellation */');
  if (withoutPanelCancel === methods.sheet) throw new Error('Panel cancellation mutation could not be applied');
  execute('mutation-panel-animation-not-cancelled', { ...methods, sheet: withoutPanelCancel }, true);
  const withoutProgressCancel = methods.progress.replaceAll('cancelViewAnimationIfRunning(progressBar);',
    '/* mutation: no progress animation cancellation */');
  if (withoutProgressCancel === methods.progress) throw new Error('Progress cancellation mutation could not be applied');
  execute('mutation-stale-progress-fade', { ...methods, progress: withoutProgressCancel }, true);
}
if (immersivePassed && args.includes('--verify-mutations')) {
  function replaceRequired(source, before, after) {
    const changed = source.replace(before, after);
    if (changed === source) throw new Error(`Immersive mutation could not be applied: ${before}`);
    return changed;
  }
  function immersiveMutation(name, changes) {
    execute(name, { ...immersiveMethods, ...changes }, true,
      immersiveTemplate, 'ImmersiveBehaviorSelfTest');
  }
  immersiveMutation('mutation-immersive-no-slop', {
    immersiveTouch: replaceRequired(immersiveMethods.immersiveTouch,
      'if (Math.hypot(dx, dy) <= slop) return;', '/* mutation: react before touch slop */')
  });
  immersiveMutation('mutation-immersive-unlocked-direction', {
    immersiveTouch: replaceRequired(immersiveMethods.immersiveTouch,
      'if (!immersiveGestureDirectionLocked) {', 'if (true) { /* mutation: reclassify every MOVE */')
  });
  immersiveMutation('mutation-immersive-total-displacement', {
    immersiveTouch: replaceRequired(immersiveMethods.immersiveTouch,
      'immersiveGestureOffset = clamp01(immersiveGestureOffset - deltaY / travel);',
      'immersiveGestureOffset = clamp01(-dy / travel);')
  });
  immersiveMutation('mutation-immersive-target-offset-on-down', {
    chromeOffset: replaceRequired(immersiveMethods.chromeOffset,
      'if (topBar == null || bottomBar == null) return 0f;',
      'if (hidden) return 1f;\n        if (topBar == null || bottomBar == null) return 0f;')
  });
  immersiveMutation('mutation-immersive-cancel-loses-progress', {
    resetChrome: replaceRequired(immersiveMethods.resetChrome,
      'applyChromeGestureOffset(hidden ? 1f : 0f);',
      'applyChromeGestureOffset(hidden ? 1f : 0f);\n        progressBar.setVisibility(View.GONE);')
  });
  immersiveMutation('mutation-immersive-show-early-return', {
    showChrome: 'private void showBrowserChrome(boolean animate) { settleImmersiveChrome(false, false); }'
  });
}
if (!passed || !immersivePassed) process.exitCode = 1;
