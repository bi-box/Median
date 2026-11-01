'use strict';

// Exercise the actual MainActivity registration methods against a recording WebView provider.
// No Android framework is needed, and a regression in the production methods reaches this test.
const fs = require('fs');
const path = require('path');
const os = require('os');
const cp = require('child_process');
const args = process.argv.slice(2);
const option = (name, fallback) => args.includes(name) ? args[args.indexOf(name) + 1] : fallback;
const repo = path.resolve(__dirname, '../..');
const out = path.resolve(option('--out', fs.mkdtempSync(path.join(os.tmpdir(), 'median-startup-scripts-'))));
const jdk = option('--jdk', process.env.JAVA_HOME || '');
const executable = name => jdk ? path.join(jdk, 'bin', name + (process.platform === 'win32' ? '.exe' : '')) : name;
const activity = fs.readFileSync(path.join(repo, 'app/src/main/java/com/xinyv/median/MainActivity.java'), 'utf8');
const begin = activity.indexOf('    private void installPreparedDocumentStartUserScripts(WebView target) {');
const end = activity.indexOf('    private void refreshUserScriptRegistrations(', begin);
if (begin < 0 || end < begin) throw new Error('Production startup registration methods were not found');
const fields = ['startupScriptToken', 'startupScriptPayloads', 'startupScriptPayloadsApplied',
  'startupDataReady', 'startupReady', 'webView', 'scriptStore'];
const declarations = fields.map(name => {
  const line = activity.split(/\r?\n/).find(line => new RegExp('^    private (?:final )?[^;=]+\\b' + name + '(?: =|;)').test(line));
  if (!line) throw new Error('Production field was not found: ' + name);
  return line;
}).join('\n');
fs.mkdirSync(out, { recursive: true });
const harness = path.join(out, 'StartupScriptRegistrationHarness.java');
fs.writeFileSync(harness, `package com.xinyv.median;
import java.util.*;
import java.util.concurrent.*;
final class StartupScriptRegistrationHarness {
${declarations}
 private final Map<WebView, String> scriptBridgeTokens = new HashMap<WebView, String>();
 private final Map<WebView, List<ScriptHandler>> scriptHandlers = new HashMap<WebView, List<ScriptHandler>>();
 private final Set<WebView> documentStartScriptViews = new HashSet<WebView>();
 private void cancelScriptRequests(WebView target) {}
${activity.slice(begin, end)}
 void dataReady(boolean enabled) {
  scriptStore = new UserScriptStore(enabled);
  startupScriptToken = enabled ? "prepared-0123456789abcdef0123456789abcdef" : "";
  startupScriptPayloads = enabled ? Collections.singletonList(new UserScriptStore.DocumentStartPayload("prepared-source")) : Collections.<UserScriptStore.DocumentStartPayload>emptyList();
  startupDataReady = true;
  installPreparedDocumentStartUserScripts(webView);
 }
 WebView configureFirst() {
  WebView target = new WebView();
  installDocumentStartUserScripts(target);
  webView = target;
  return target;
 }
 void complete() {
  startupReady = true;
  if (startupDataReady) installPreparedDocumentStartUserScripts(webView);
 }
 void repeatPrepared() { installPreparedDocumentStartUserScripts(webView); }
 WebView newTab() { WebView target = new WebView(); installDocumentStartUserScripts(target); return target; }
 String token(WebView view) { return scriptBridgeTokens.get(view); }
 String retainedToken() { return startupScriptToken; }
 int retainedPayloads() { return startupScriptPayloads.size(); }
 int builds() { return scriptStore.builds; }
 boolean registered(WebView view) { return documentStartScriptViews.contains(view); }
}
final class WebView { int adds, removes, active; }
interface ScriptHandler { void remove(); }
final class WebViewFeature {
 static final String DOCUMENT_START_SCRIPT = "DOCUMENT_START_SCRIPT";
 static boolean supported = true;
 static boolean isFeatureSupported(String feature) { return supported; }
}
final class WebViewCompat {
 static ScriptHandler addDocumentStartJavaScript(final WebView target, String source, Set<String> origins) {
  target.adds++; target.active++;
  return new ScriptHandler() {
   private boolean removed;
   public void remove() { if (!removed) { removed = true; target.removes++; target.active--; } }
  };
 }
}
final class UrlCleaner {
 static int sequence;
 static String randomToken() { return "fresh-0123456789abcdef0123456789abcdef-" + (++sequence); }
}
final class UserScriptStore {
 final boolean enabled;
 int builds;
 UserScriptStore(boolean enabled) { this.enabled = enabled; }
 boolean hasEnabledScripts() { return enabled; }
 List<DocumentStartPayload> buildDocumentStartPayloads(String token) {
  builds++; return Collections.singletonList(new DocumentStartPayload("built-source-" + token));
 }
 static final class DocumentStartPayload {
  final String source;
  final Set<String> allowedOriginRules = Collections.singleton("*");
  DocumentStartPayload(String source) { this.source = source; }
 }
}
`);
function run(command, arguments_) {
  const result = cp.spawnSync(command, arguments_, { encoding: 'utf8', windowsHide: true });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(result.stdout + result.stderr);
  if (result.stdout) process.stdout.write(result.stdout);
}
run(executable('javac'), ['-encoding', 'UTF-8', '--release', '17', '-d', out,
  harness, path.join(__dirname, 'StartupScriptRegistrationSelfTest.java')]);
run(executable('java'), ['-cp', out, 'com.xinyv.median.StartupScriptRegistrationSelfTest']);
