'use strict';

// Compile the production startup facade and boundary loader, then drive the real protocol with
// a synthetic provider and controllable Android main queue. No production code is extracted.
const fs = require('fs');
const path = require('path');
const os = require('os');
const cp = require('child_process');
const args = process.argv.slice(2);
const option = (name, fallback) => args.includes(name) ? args[args.indexOf(name) + 1] : fallback;
const repo = path.resolve(__dirname, '../..');
const persistentOut = args.includes('--out');
const out = path.resolve(option('--out', fs.mkdtempSync(path.join(os.tmpdir(), 'median-startup-compat-'))));
const jdk = option('--jdk', process.env.JAVA_HOME || '');
const executable = name => jdk ? path.join(jdk, 'bin', name + (process.platform === 'win32' ? '.exe' : '')) : name;
const sourceRoot = path.join(repo, 'app/src/main/java');
function javaFiles(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory() ? javaFiles(file) : entry.name.endsWith('.java') ? [file] : [];
  });
}
function run(name, arguments_) {
  const result = cp.spawnSync(executable(name), arguments_, { stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(name + ' failed (' + result.status + ')');
}
fs.mkdirSync(out, { recursive: true });
try {
  const sources = javaFiles(path.join(__dirname, 'startup_compat_stubs')).concat([
    path.join(sourceRoot, 'androidx/webkit/WebViewStartupCompat.java'),
    path.join(sourceRoot, 'androidx/webkit/WebViewNavigationWarmupCompat.java'),
    path.join(sourceRoot, 'androidx/webkit/ScriptHandler.java'),
    path.join(sourceRoot, 'androidx/webkit/internal/WebViewGlueCommunicator.java'),
    path.join(__dirname, 'WebViewStartupCompatSelfTest.java'),
    path.join(__dirname, 'WebViewNavigationWarmupCompatSelfTest.java')
  ], javaFiles(path.join(sourceRoot, 'org/chromium/support_lib_boundary')));
  run('javac', ['-encoding', 'UTF-8', '--release', '17', '-d', out].concat(sources));
  run('java', ['-cp', out, 'WebViewStartupCompatSelfTest']);
  run('java', ['-cp', out, 'androidx.webkit.WebViewNavigationWarmupCompatSelfTest']);
} finally {
  if (!persistentOut) fs.rmSync(out, { recursive: true, force: true });
}
