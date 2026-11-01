'use strict';
const fs = require('fs'), path = require('path'), cp = require('child_process');
const args = process.argv.slice(2);
const option = (name, fallback) => args.includes(name) ? args[args.indexOf(name) + 1] : fallback;
const project = path.resolve(__dirname, '../..');
const out = path.resolve(option('--out', path.join(require('os').tmpdir(), 'median-main-hints-' + process.pid)));
const jdk = option('--jdk', process.env.JAVA_HOME || '');
fs.mkdirSync(out, { recursive: true });
const source = fs.readFileSync(path.join(project, 'app/src/main/java/com/xinyv/median/MainActivity.java'), 'utf8');
function extract(signature) {
  const begin = source.indexOf(signature); if (begin < 0) throw new Error(signature);
  let depth = 0;
  for (let i = source.indexOf('{', begin); i < source.length; i++) {
    if (source[i] === '{') depth++;
    else if (source[i] === '}' && --depth === 0) return source.slice(begin, i + 1);
  }
  throw new Error('Unterminated ' + signature);
}
const files = [];
function save(name, content) {
  const file = path.join(out, name); fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, content); files.push(file); return file;
}
for (const signature of ['    private void prepareHomeDestination(',
  '    private void prepareOfflineDestination(', '    private void destroyWebView(']) {
  if (!/removeConnectionHintScript\((?:target|view)\)/.test(extract(signature)))
    throw new Error('Connection hint lifecycle cleanup missing: ' + signature);
}
save('android/net/Uri.java', `package android.net;
public final class Uri {private final java.net.URI value;private Uri(String s){value=java.net.URI.create(s);}
public static Uri parse(String s){return new Uri(s);}public String getScheme(){return value.getScheme();}
public String getHost(){return value.getHost();}public String getEncodedPath(){return value.getRawPath();}
public String getEncodedQuery(){return value.getRawQuery();}public String getPath(){return value.getPath();}public String toString(){return value.toString();}}
`);
save('android/content/SharedPreferences.java', `package android.content;
public interface SharedPreferences {Editor edit();interface Editor {Editor putString(String k,String v);Editor remove(String k);void apply();}}
`);
save('android/os/Handler.java', `package android.os;
public final class Handler {public final java.util.Queue<Runnable> tasks=new java.util.ArrayDeque<>();public final java.util.List<Long> delays=new java.util.ArrayList<>();public boolean postDelayed(Runnable r,long delay){tasks.add(r);delays.add(delay);return true;}}
`);
save('android/webkit/ValueCallback.java', `package android.webkit;public interface ValueCallback<T>{void onReceiveValue(T value);}`);
save('android/webkit/WebSettings.java', `package android.webkit;public final class WebSettings {public boolean enabled=true;public boolean getJavaScriptEnabled(){return enabled;}}`);
save('android/webkit/WebView.java', `package android.webkit;
public final class WebView {public String url="https://page.example/page",lastScript;public int queries;public ValueCallback<String> callback;public final WebSettings settings=new WebSettings();public String getUrl(){return url;}public WebSettings getSettings(){return settings;}public void evaluateJavascript(String script,ValueCallback<String> c){queries++;lastScript=script;if(c!=null)callback=c;}}
`);
save('androidx/webkit/WebViewFeature.java', `package androidx.webkit;
public final class WebViewFeature {public static final String DOCUMENT_START_SCRIPT="document-start";public static boolean supported=true;public static boolean isFeatureSupported(String feature){return supported;}}
`);
save('androidx/webkit/WebViewCompat.java', `package androidx.webkit;
import java.util.*;import android.webkit.WebView;
public final class WebViewCompat {public static boolean failNext;private static final Map<WebView,TestScriptHandler> live=new IdentityHashMap<>();
public static TestScriptHandler handle(WebView view){return live.get(view);}
public static ScriptHandler addDocumentStartJavaScript(WebView target,String script,Set<String> origins){if(failNext){failNext=false;throw new RuntimeException("provider rejected registration");}TestScriptHandler handle=new TestScriptHandler(target,script,origins);live.put(target,handle);return handle;}
public static final class TestScriptHandler implements ScriptHandler {public final WebView view;public final String script;public final Set<String> origins;public int removed;TestScriptHandler(WebView v,String s,Set<String> o){view=v;script=s;origins=o;}public void remove(){removed++;if(live.get(view)==this)live.remove(view);}}}
`);
save('androidx/webkit/WebViewNavigationWarmupCompat.java', `package androidx.webkit;
import java.util.*;import java.net.URI;
public final class WebViewNavigationWarmupCompat {public static int calls;public static List<String> learned;public interface OriginFilter{boolean allow(String origin);}
public static void preconnectForNavigation(String url,String[] origins,OriginFilter filter){calls++;learned=new ArrayList<>();for(String origin:origins)if(filter.allow(origin))learned.add(origin);}
public static String[] preconnectOriginsForNavigation(String url,String[] origins,OriginFilter filter){ArrayList<String> selected=new ArrayList<>();try{URI u=URI.create(url);selected.add(u.getScheme()+"://"+u.getRawAuthority());}catch(RuntimeException ignored){}for(String origin:origins)if(filter.allow(origin))selected.add(origin);return selected.toArray(new String[selected.size()]);}}
`);
save('org/json/JSONObject.java', `package org.json;
public class JSONObject {public static JSONObject result;private final java.util.Map<String,Object> values=new java.util.HashMap<>();public JSONObject(){}public JSONObject(String s){values.putAll(result.values);}
public JSONObject put(String k,Object v){values.put(k,v);return this;}public String optString(String k){Object v=values.get(k);return v==null?"":v.toString();}public double optDouble(String k){Object v=values.get(k);return v instanceof Number?((Number)v).doubleValue():Double.NaN;}
public boolean optBoolean(String k){return Boolean.TRUE.equals(values.get(k));}public JSONArray optJSONArray(String k){return (JSONArray)values.get(k);}}
`);
save('org/json/JSONArray.java', `package org.json;
public class JSONArray {private final java.util.List<JSONObject> values=new java.util.ArrayList<>();public JSONArray put(JSONObject o){values.add(o);return this;}public int length(){return values.size();}public JSONObject optJSONObject(int i){return values.get(i);}}
`);
save('org/json/JSONTokener.java', `package org.json;
public final class JSONTokener {private final String value;public JSONTokener(String s){value=s;}public Object nextValue(){return "null".equals(value)?null:"decoded";}}
`);
save('MainHintsRegression.java', `package com.xinyv.median;
import java.util.*;import android.os.Handler;import android.content.SharedPreferences;import android.webkit.WebView;
import org.json.*;import androidx.webkit.*;import androidx.webkit.WebViewCompat.TestScriptHandler;
public final class MainHintsRegression implements NavigationConnectionHints.Host {
 private long navigationSequence=1;private boolean adBlockEnabled=true,activityDestroyed;
 private boolean startupSessionPending,pageFinished,userHasNavigated=true;private String currentPageUrl="https://page.example/page",currentPageHost="page.example";
 private int settingsApplied,homeShown,registrations;private final AdBlockEngine adBlock=new AdBlockEngine();
 private WebView webView=new WebView();private final Handler uiHandler=new Handler();private final Prefs prefs=new Prefs();
 private final ConnectionHintsStore seeded=new ConnectionHintsStore();
 private final NavigationConnectionHints navigationConnectionHints=new NavigationConnectionHints(uiHandler,prefs,adBlock,this);
 MainHintsRegression(){check(navigationConnectionHints.publishRestored(seeded),"initial restore accepted");}
 public boolean isCurrentPageCallback(WebView v,long seq){return !activityDestroyed&&v==webView&&seq==navigationSequence;}
 public boolean isAdBlockEnabled(){return adBlockEnabled;}
 private boolean isNetworkPage(String u){return u!=null&&(u.startsWith("https://")||u.startsWith("http://"));}
 private void installPreparedDocumentStartUserScripts(WebView v){registrations++;}
 private boolean isHomeUrl(String u){return "about:home".equals(u);}private boolean restoreStartupSession(){return false;}
 private void requestChromeUpdate(){}private void applySiteSettings(WebView v,String host){settingsApplied++;}private void showHome(){homeShown++;}
 private void prepareNavigationConnections(String url){navigationConnectionHints.prepareNavigationConnections(url);}
${extract('    private void onStartupDataReady()')}
 void query(){navigationConnectionHints.collectConnectionHints(webView,webView.url,navigationSequence);uiHandler.tasks.remove().run();}
 void seed(String url,String resource,String type){seeded.observe(url,Arrays.asList(new ConnectionHintsStore.ResourceHint(resource,type,12,11)),System.currentTimeMillis());}
 List<String> learned(){navigationConnectionHints.prepareNavigationConnections(webView.url);return WebViewNavigationWarmupCompat.learned;}
 void install(){navigationConnectionHints.installConnectionHintScript(webView,webView.url);}
 TestScriptHandler handle(){return WebViewCompat.handle(webView);}
 static JSONObject item(String url,String type,double duration){return new JSONObject().put("u",url).put("t",type).put("e",duration+1).put("d",duration);}
 void result(JSONArray a){JSONObject.result=new JSONObject().put("o","https://page.example").put("r",a);webView.callback.onReceiveValue("encoded");}
 static ConnectionHintsStore restored(String dependency){ConnectionHintsStore store=new ConnectionHintsStore();store.observe("https://page.example/page",Arrays.asList(new ConnectionHintsStore.ResourceHint(dependency,"script",12,11)),System.currentTimeMillis());return store;}
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 public static void main(String[] args){int cases=0;
  MainHintsRegression h=new MainHintsRegression();
  h.adBlock.updateCustomRules("||cdn.example/ads.js$script\\n||style.example/blocked$stylesheet\\n||fonts.example/bad$font\\n");
  h.query();h.result(new JSONArray().put(item("https://cdn.example/ads.js","script",100)).put(item("https://style.example/blocked","stylesheet",99)).put(item("https://fonts.example/bad","font",98)).put(item("https://cdn.example/ads.js","script",97)).put(item("https://allowed.example/critical.js?q=secret","script",50)));
  check(h.learned().equals(Arrays.asList("https://allowed.example")),"real path/type rules run before choosing origins");check(!h.prefs.saved.contains("critical")&&!h.prefs.saved.contains("secret")&&!h.prefs.saved.contains("ads.js"),"only origins persisted");cases++;
  MainHintsRegression empty=new MainHintsRegression();empty.query();empty.result(new JSONArray());
  check(empty.prefs.writes==1&&!empty.navigationConnectionHints.publishRestored(restored("https://late.example/a.js"))&&empty.learned().isEmpty(),"empty observation rejects late startup restore");cases++;
  MainHintsRegression stale=new MainHintsRegression();stale.query();stale.navigationConnectionHints.clearConnectionHints();stale.result(new JSONArray().put(item("https://stale.example/a.js","script",5)));
  check(stale.prefs.writes==0&&stale.learned().isEmpty(),"clear invalidates in-flight callback");cases++;
  check(!stale.navigationConnectionHints.publishRestored(restored("https://late.example/a.js"))&&stale.learned().isEmpty(),"clear rejects late startup restore");cases++;
  MainHintsRegression nav=new MainHintsRegression();nav.query();nav.navigationSequence++;nav.result(new JSONArray().put(item("https://old.example/a.js","script",5)));check(nav.prefs.writes==0,"navigation invalidates old document");cases++;
  MainHintsRegression wrong=new MainHintsRegression();wrong.query();JSONObject.result=new JSONObject().put("o","https://other.example").put("r",new JSONArray());wrong.webView.callback.onReceiveValue("encoded");check(wrong.prefs.writes==0,"JS origin matches accepted document");cases++;
  MainHintsRegression once=new MainHintsRegression();once.query();once.navigationConnectionHints.collectConnectionHints(once.webView,once.webView.url,once.navigationSequence);check(once.uiHandler.tasks.isEmpty()&&once.webView.queries==1,"query once per document");cases++;
  MainHintsRegression switched=new MainHintsRegression();switched.navigationConnectionHints.collectConnectionHints(switched.webView,switched.webView.url,switched.navigationSequence);switched.webView.url="https://changed.example";switched.uiHandler.tasks.remove().run();check(switched.webView.queries==0,"delayed task checks current URL");cases++;
  MainHintsRegression published=new MainHintsRegression();check(published.navigationConnectionHints.publishRestored(restored("https://restored.example/critical.js")),"late disk snapshot accepted before observation");published.webView.url="about:blank";WebViewNavigationWarmupCompat.calls=0;published.onStartupDataReady();
  check(WebViewNavigationWarmupCompat.calls==1&&WebViewNavigationWarmupCompat.learned.equals(Arrays.asList("https://restored.example")),"late stores warm accepted target while provider blank");check(published.webView.queries==0&&published.homeShown==0,"late stores do not replace navigation");cases++;
  published.pageFinished=true;published.onStartupDataReady();check(WebViewNavigationWarmupCompat.calls==1,"completed document does not warm late");cases++;
  MainHintsRegression home=new MainHintsRegression();home.currentPageUrl="about:home";home.webView.url="about:home";WebViewNavigationWarmupCompat.calls=0;home.onStartupDataReady();check(WebViewNavigationWarmupCompat.calls==0,"internal home is not warmed");cases++;
  MainHintsRegression error=new MainHintsRegression();error.seed(error.currentPageUrl,"https://cached-valid.example/a.js","script");error.query();error.webView.callback.onReceiveValue("null");check(error.learned().equals(Arrays.asList("https://cached-valid.example"))&&error.prefs.writes==0,"HTTP503 null preserves valid cache");check(error.navigationConnectionHints.publishRestored(error.seeded),"null result does not advance generation");cases++;
  MainHintsRegression images=new MainHintsRegression();images.adBlock.updateCustomRules("||blocked-images.example/promo$image\\n");images.query();images.result(new JSONArray().put(item("https://blocked-images.example/promo?token=secret","image",500)).put(item("https://allowed-images.example/opaque?token=private","image",100)));
  check(images.adBlock.getBlockedCount()==1&&images.learned().equals(Arrays.asList("https://allowed-images.example")),"image/* reaches real $image rules for extensionless image");check(images.webView.lastScript.equals(ConnectionHintsScript.collectScript()),"production collector helper evaluated");cases++;
  MainHintsRegression disabled=new MainHintsRegression();disabled.adBlockEnabled=false;disabled.adBlock.updateCustomRules("||images.example/promo$image\\n");disabled.query();disabled.result(new JSONArray().put(item("https://images.example/promo","image",100)));check(disabled.learned().equals(Arrays.asList("https://images.example")),"disabled blocking retains learning policy");cases++;
  MainHintsRegression document=new MainHintsRegression();document.seeded.observe(document.webView.url,Arrays.asList(new ConnectionHintsStore.ResourceHint("https://allowed-images.example/opaque","image",100,99),new ConnectionHintsStore.ResourceHint("https://blocked.example/app.js","script",50,49)),System.currentTimeMillis());document.adBlock.updateCustomRules("||blocked.example^\\n");document.install();TestScriptHandler first=document.handle();
  check(first!=null&&first.origins.equals(Collections.singleton("https://page.example"))&&first.script.contains("https://allowed-images.example")&&!first.script.contains("https://blocked.example")&&!first.script.contains("opaque"),"document hints filter rules and scope only origin data");cases++;
  document.navigationConnectionHints.installConnectionHintScript(document.webView,document.webView.url,true);check(first.removed==0&&document.handle()==first&&document.webView.queries==1&&document.webView.lastScript.contains("preconnect"),"same payload reuses registration and runs fallback");cases++;
  document.install();document.navigationConnectionHints.installConnectionHintScript(document.webView,document.webView.url,true);check(first.removed==0&&document.handle()==first&&document.webView.queries==2,"repeated callbacks keep registration and fallback");cases++;
  document.adBlock.updateCustomRules("");document.navigationConnectionHints.installConnectionHintScript(document.webView,document.webView.url,true);TestScriptHandler rulesChanged=document.handle();check(first.removed==1&&rulesChanged!=first&&rulesChanged.script.contains("https://blocked.example")&&document.webView.queries==3,"new rules replace registration with fallback");cases++;
  document.seed(document.webView.url,"https://new-learned.example/image","image");document.install();TestScriptHandler learnedChanged=document.handle();check(rulesChanged.removed==1&&learnedChanged!=rulesChanged&&learnedChanged.script.contains("https://new-learned.example"),"new learned origins replace exact payload");cases++;
  String nextPage="https://other-page.example/path";document.seed(nextPage,"https://new-learned.example/image","image");document.navigationConnectionHints.installConnectionHintScript(document.webView,nextPage);TestScriptHandler originChanged=document.handle();check(learnedChanged.removed==1&&originChanged.origins.equals(Collections.singleton("https://other-page.example")),"changed page origin replaces scope");cases++;
  WebView otherView=new WebView();document.navigationConnectionHints.installConnectionHintScript(otherView,nextPage);TestScriptHandler other=WebViewCompat.handle(otherView);check(other!=null&&other!=originChanged,"registrations isolated per WebView");cases++;
  document.navigationConnectionHints.clearConnectionHints();check(originChanged.removed==1&&other.removed==1&&document.handle()==null&&WebViewCompat.handle(otherView)==null&&document.learned().isEmpty()&&document.prefs.removes==1,"clear retires all registrations and learned data");cases++;
  MainHintsRegression unsupported=new MainHintsRegression();unsupported.seed(unsupported.webView.url,"https://allowed-images.example/opaque","image");unsupported.install();TestScriptHandler prior=unsupported.handle();WebViewFeature.supported=false;unsupported.install();WebViewFeature.supported=true;check(prior.removed==1&&unsupported.handle()==null,"unsupported provider retires registration");unsupported.install();check(unsupported.handle()!=null,"provider recovery re-registers same payload");cases++;
  for(int mode=0;mode<3;mode++){MainHintsRegression retired=new MainHintsRegression();retired.seed(retired.webView.url,"https://cached.example/a.js","script");retired.install();TestScriptHandler handle=retired.handle();String destination=retired.webView.url;if(mode==0)retired.webView.settings.enabled=false;else if(mode==1)destination="about:blank";else retired.seeded.clear();retired.navigationConnectionHints.installConnectionHintScript(retired.webView,destination,true);check(handle.removed==1&&retired.handle()==null&&retired.webView.queries==0,"disabled JS, no origin or empty payload retires handler: "+mode);cases++;}
  MainHintsRegression retry=new MainHintsRegression();retry.seed(retry.webView.url,"https://retry.example/a.js","script");WebViewCompat.failNext=true;retry.install();check(retry.handle()==null,"provider failure leaves no registration");cases++;retry.install();TestScriptHandler retryHandle=retry.handle();check(retryHandle!=null,"same payload retries after failure");cases++;retry.navigationConnectionHints.removeConnectionHintScript(retry.webView);retry.navigationConnectionHints.removeConnectionHintScript(retry.webView);check(retryHandle.removed==1&&retry.handle()==null,"lifecycle removal retires handler once");cases++;
  MainHintsRegression restore=new MainHintsRegression();check(restore.navigationConnectionHints.publishRestored(restored("https://late-disk.example/a.js"))&&restore.learned().equals(Arrays.asList("https://late-disk.example")),"unobserved component accepts startup restore");restore.query();restore.result(new JSONArray().put(item("https://new-live.example/a.js","script",500)));check(!restore.navigationConnectionHints.publishRestored(restored("https://obsolete.example/a.js"))&&!restore.learned().contains("https://obsolete.example"),"live observation rejects obsolete restore");cases++;
  MainHintsRegression clearDelayed=new MainHintsRegression();clearDelayed.navigationConnectionHints.collectConnectionHints(clearDelayed.webView,clearDelayed.webView.url,clearDelayed.navigationSequence);clearDelayed.navigationConnectionHints.clearConnectionHints();clearDelayed.uiHandler.tasks.remove().run();check(clearDelayed.webView.queries==0&&clearDelayed.prefs.writes==0,"clear cancels queued collector before JS");cases++;
  MainHintsRegression clearRetry=new MainHintsRegression();clearRetry.query();JSONObject.result=new JSONObject().put("o","https://page.example").put("p",true);clearRetry.webView.callback.onReceiveValue("encoded");clearRetry.navigationConnectionHints.clearConnectionHints();clearRetry.uiHandler.tasks.remove().run();check(clearRetry.webView.queries==1&&clearRetry.prefs.writes==0,"clear cancels pending readiness retry");cases++;
  MainHintsRegression closed=new MainHintsRegression();closed.query();closed.result(new JSONArray().put(item("https://persisted.example/a.js","script",50)));closed.install();TestScriptHandler closedHandle=closed.handle();String saved=closed.prefs.saved;int writes=closed.prefs.writes;closed.navigationSequence++;closed.query();closed.navigationConnectionHints.close();closed.result(new JSONArray().put(item("https://after-close.example/a.js","script",50)));check(closed.prefs.writes==writes&&saved.equals(closed.prefs.saved)&&closed.prefs.removes==0&&closedHandle.removed==1&&closed.handle()==null,"close invalidates JS callback and preserves cache");cases++;
  int queries=closed.webView.queries,calls=WebViewNavigationWarmupCompat.calls;closed.navigationConnectionHints.installConnectionHintScript(closed.webView,closed.webView.url,true);closed.navigationConnectionHints.prepareNavigationConnections(closed.webView.url);closed.navigationConnectionHints.collectConnectionHints(closed.webView,closed.webView.url,++closed.navigationSequence);check(closed.handle()==null&&closed.webView.queries==queries&&closed.uiHandler.tasks.isEmpty()&&WebViewNavigationWarmupCompat.calls==calls&&!closed.navigationConnectionHints.publishRestored(restored("https://late.example/a.js")),"closed component rejects installs, warming, collection and restore");closed.navigationConnectionHints.close();check(closedHandle.removed==1&&saved.equals(closed.prefs.saved),"close is idempotent");cases++;
  MainHintsRegression closeQueued=new MainHintsRegression();closeQueued.navigationConnectionHints.collectConnectionHints(closeQueued.webView,closeQueued.webView.url,closeQueued.navigationSequence);closeQueued.navigationConnectionHints.close();closeQueued.uiHandler.tasks.remove().run();check(closeQueued.webView.queries==0&&closeQueued.prefs.writes==0,"close cancels delayed collector");cases++;
  MainHintsRegression budget=new MainHintsRegression();budget.query();JSONObject.result=new JSONObject().put("o","https://page.example").put("p",true);for(int i=0;i<24;i++){budget.webView.callback.onReceiveValue("encoded");if(!budget.uiHandler.tasks.isEmpty())budget.uiHandler.tasks.remove().run();}check(budget.webView.queries==24&&budget.uiHandler.tasks.isEmpty()&&budget.prefs.writes==0&&budget.uiHandler.delays.get(0)==250L&&budget.uiHandler.delays.get(1)==500L&&budget.uiHandler.delays.get(2)==2000L,"readiness polling retains retry budget and delays");cases++;
  MainHintsRegression reserved=new MainHintsRegression();reserved.webView.url="https://median.invalid/";reserved.navigationConnectionHints.collectConnectionHints(reserved.webView,reserved.webView.url,reserved.navigationSequence);check(reserved.uiHandler.tasks.isEmpty(),"reserved browser host is never collected");cases++;
  System.out.println("MainHintsRegression passed: "+cases+" cases against production NavigationConnectionHints and real AdBlockEngine");
 }
 static final class Prefs implements SharedPreferences {String saved;int writes,removes;public Editor edit(){return new Edit();}private final class Edit implements SharedPreferences.Editor {public Editor putString(String k,String v){check(NavigationConnectionHints.PREF_CONNECTION_HINTS.equals(k),"preference key preserved");saved=v;writes++;return this;}public Editor remove(String k){check(NavigationConnectionHints.PREF_CONNECTION_HINTS.equals(k),"clear uses existing preference key");saved=null;removes++;return this;}public void apply(){}}}
}
`);
function run(name,args){const executable=jdk?path.join(jdk,'bin',name+(process.platform==='win32'?'.exe':'')):name;const r=cp.spawnSync(executable,args,{encoding:'utf8',windowsHide:true});if(r.stdout)process.stdout.write(r.stdout);if(r.stderr)process.stderr.write(r.stderr);if(r.error||r.status!==0)throw r.error||new Error(name+' exit '+r.status);}
run('javac',['-encoding','UTF-8','--release','17','-d',out,...files,
 path.join(project,'app/src/main/java/com/xinyv/median/NavigationConnectionHints.java'),
 path.join(project,'app/src/main/java/com/xinyv/median/AdBlockEngine.java'),path.join(project,'app/src/main/java/com/xinyv/median/ConnectionHintsStore.java'),
 path.join(project,'app/src/main/java/com/xinyv/median/ConnectionHintsScript.java'),path.join(project,'app/src/main/java/com/xinyv/median/ConnectionPreconnectScript.java'),
 path.join(project,'app/src/main/java/androidx/webkit/ScriptHandler.java')]);
run('java',['-cp',out,'com.xinyv.median.MainHintsRegression']);
