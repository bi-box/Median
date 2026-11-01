'use strict';
// Production method extraction only supplies Android stubs; the behavior under test stays Java.
const fs = require('fs'), path = require('path'), os = require('os'), cp = require('child_process');
const args = process.argv.slice(2), option = (n, d) => args.includes(n) ? args[args.indexOf(n) + 1] : d;
const repo = path.resolve(__dirname, '../..'), src = path.join(repo, 'app/src/main/java/com/xinyv/median');
const out = path.resolve(args.includes('--out') ? option('--out') : fs.mkdtempSync(path.join(os.tmpdir(), 'median-release-performance-')));
const jdk = option('--jdk', process.env.JAVA_HOME || '');
const executable = name => jdk ? path.join(jdk,'bin',name+(process.platform==='win32'?'.exe':'')) : name;
fs.mkdirSync(out, {recursive:true});
const activity = fs.readFileSync(path.join(src, 'MainActivity.java'), 'utf8');
function captureMethod(text, signature = '    private static Bundle captureWebViewState(WebView view)') {
const start = text.indexOf(signature);
if (start < 0) throw new Error('Missing production state capture');
let depth = 0, end = -1;
for (let at = text.indexOf('{', start); at < text.length; at++) {
  if (text[at] === '{') depth++;
  else if (text[at] === '}' && --depth === 0) { end = at + 1; break; }
}
if (end < 0) throw new Error('Unterminated production state capture');
return text.slice(start,end);
}
const productionMethod = captureMethod(activity);
const productionRestore = captureMethod(activity, '    private static boolean restoreWebViewState(WebView view, Bundle state)');
const productionBounded = captureMethod(activity, '    private static boolean isBoundedTabState(Bundle state)');
const harness = `package com.xinyv.median;
public final class WebViewStateCaptureSelfTest {
 static final int MAX_WEBVIEW_STATE_BYTES = 100;
 static int savedSize, recycled, obtains; static boolean failSave, empty, failWrite, failObtain, failRestore;
 static class WebView {
  Object saveState(Bundle b) { if(failSave)throw new IllegalStateException("provider unavailable");return empty?null:new Object(); }
  Object restoreState(Bundle b) { if(failRestore)throw new IllegalStateException("invalid provider history");return empty?null:new Object(); }
 }
 static class Bundle { void writeToParcel(Parcel p,int flags) {if(failWrite)throw new IllegalStateException("invalid parcel");} }
 static class Parcel { static Parcel obtain(){obtains++;if(failObtain)throw new IllegalStateException("no parcel");return new Parcel();}int dataSize(){return savedSize;}void recycle(){recycled++;} }
 ${productionMethod}
 ${productionRestore}
 ${productionBounded}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){
  check(captureWebViewState(null)==null,"null view");
  savedSize=100;check(captureWebViewState(new WebView())!=null&&recycled==1,"bounded state retained and parcel recycled");
  savedSize=101;check(captureWebViewState(new WebView())==null&&recycled==2,"oversized state rejected and parcel recycled");
  failSave=true;check(captureWebViewState(new WebView())==null&&recycled==2,"provider failure cannot crash tab freezing");failSave=false;
  empty=true;check(captureWebViewState(new WebView())==null&&obtains==2,"no history avoids allocating parcel");empty=false;
  failWrite=true;check(captureWebViewState(new WebView())==null&&recycled==3,"serialization failure recycles parcel");failWrite=false;
  failObtain=true;check(captureWebViewState(new WebView())==null&&recycled==3,"allocation failure does not recycle missing parcel");
  check(!restoreWebViewState(null,new Bundle())&&!restoreWebViewState(new WebView(),null),"missing restore state falls back to URL");
  check(restoreWebViewState(new WebView(),new Bundle()),"valid provider history restored");
  empty=true;check(!restoreWebViewState(new WebView(),new Bundle()),"empty provider history falls back to URL");empty=false;
  failRestore=true;check(!restoreWebViewState(new WebView(),new Bundle()),"provider restore failure falls back to URL");
  failObtain=false;savedSize=100;check(isBoundedTabState(new Bundle()),"combined state at budget accepted");
  savedSize=101;check(!isBoundedTabState(new Bundle()),"combined metadata and history above budget rejected");
  failWrite=true;check(!isBoundedTabState(new Bundle()),"combined serialization failure requests URL fallback");
  System.out.println("WebViewStateCaptureSelfTest passed (14 cases)");
 }
}`;
fs.writeFileSync(path.join(out, 'WebViewStateCaptureSelfTest.java'), harness);
const before = option('--before-state');
if(before)fs.writeFileSync(path.join(out, 'BeforeWebViewStateCaptureSelfTest.java'),harness
 .replaceAll('WebViewStateCaptureSelfTest','BeforeWebViewStateCaptureSelfTest')
 .replace(productionMethod,captureMethod(fs.readFileSync(before,'utf8'))));
function run(command, argv) {
 const r=cp.spawnSync(executable(command),argv,{encoding:'utf8',windowsHide:true});
 if(r.error||r.status!==0)throw new Error(r.error||r.stdout+r.stderr);
 process.stdout.write(r.stdout);
}
const engineSuites=['AdBlockBucketSelfTest','AdBlockMemorySelfTest','AdBlockRequestMetadataSelfTest',
 'AdBlockScaleSelfTest','AdBlockSemanticsSelfTest','AdBlockTrackingEndpointSelfTest'];
let successful = false;
try {
run('javac',['-encoding','UTF-8','--release','17','-d',out,
 path.join(__dirname,'adblock_stubs/android/net/Uri.java'),path.join(src,'AdBlockEngine.java'),path.join(src,'AdBlockRuleReload.java'),
 path.join(__dirname,'AdBlockCancellationSelfTest.java'),path.join(out,'WebViewStateCaptureSelfTest.java'),
 ...engineSuites.map(n=>path.join(__dirname,n+'.java')),
 ...before?[path.join(out,'BeforeWebViewStateCaptureSelfTest.java')]:[]]);
if(before){
 const b=cp.spawnSync(executable('java'),
  ['-cp',out,'com.xinyv.median.BeforeWebViewStateCaptureSelfTest'],{encoding:'utf8',windowsHide:true});
 if(b.error||b.status===0||!b.stderr.includes('provider unavailable'))throw new Error('Baseline did not reproduce the expected provider crash: '+b.stderr);
 process.stdout.write('Baseline reproduced uncaught saveState provider exception.\n');
}
run('java',['-Xmx768m','-cp',out,'com.xinyv.median.WebViewStateCaptureSelfTest']);
run('java',['-Xmx768m','-cp',out,'com.xinyv.median.AdBlockCancellationSelfTest']);
for(const suite of engineSuites)run('java',['-Xmx768m','-cp',out,'com.xinyv.median.'+suite]);
successful = true;
} finally {
 if(successful && !args.includes('--out')) {
  if(path.dirname(out)!==path.resolve(os.tmpdir()) || !path.basename(out).startsWith('median-release-performance-'))
   throw new Error('Refusing cleanup outside generated test directory');
  fs.rmSync(out,{recursive:true,force:true});
 }
}
