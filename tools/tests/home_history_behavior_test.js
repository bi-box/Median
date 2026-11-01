'use strict';

// Compile the production homepage/history callbacks verbatim against a deterministic WebView.
// Probe completion is deferred so cache restoration and late renderer results can be exercised.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const cp = require('node:child_process');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const args = process.argv.slice(2);
const option = (name, fallback) => args.includes(name) ? args[args.indexOf(name) + 1] : fallback;
const repo = path.resolve(__dirname, '../..');
const sourcePath = path.resolve(option('--source', path.join(repo, 'app/src/main/java/com/xinyv/median/MainActivity.java')));
const source = fs.readFileSync(sourcePath, 'utf8');
const persistentOut = args.includes('--out');
const out = path.resolve(persistentOut ? option('--out') : fs.mkdtempSync(path.join(os.tmpdir(), 'median-home-history-')));
const jdk = option('--jdk', process.env.JAVA_HOME || '');
const executable = name => jdk ? path.join(jdk, 'bin', name + (process.platform === 'win32' ? '.exe' : '')) : name;
function extract(signature) {
  const begin = source.indexOf(signature);
  assert.ok(begin >= 0, 'Missing production method ' + signature);
  const tokens = /"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\r\n]*|\/\*[\s\S]*?\*\/|[{}]/g;
  tokens.lastIndex = source.indexOf('{', begin);
  let depth = 0, token;
  while ((token = tokens.exec(source))) {
    if (token[0] === '{') depth++;
    else if (token[0] === '}' && --depth === 0) return source.slice(begin, tokens.lastIndex);
  }
  throw new Error('Unterminated production method ' + signature);
}
const focusStart = source.indexOf('        addressBar.setOnFocusChangeListener(');
const focusEnd = source.indexOf('        refreshButton.setOnClickListener(', focusStart);
assert.ok(focusStart >= 0 && focusEnd > focusStart, 'Missing address focus listener');
const methods = [
  '    private void showHome(boolean resetHistory)',
  '    private void verifyTrustedHome(final WebView view)',
  '    private void scheduleRestoredHomeRepair(final WebView target)',
  '    private boolean isHomeUrl(String url)',
  '    private static String navigationDocumentUrl(String url)',
  '            public void onPageFinished(WebView view, String url)',
  '            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload)'
].map(extract).join('\n');
fs.mkdirSync(out, { recursive: true });
const harness = path.join(out, 'HomeHistoryRegression.java');
fs.writeFileSync(harness, `package com.xinyv.median;
import java.util.*;
import java.net.URI;

public final class HomeHistoryRegression extends CallbackBase {
    static final String HOME_URL="https://median.invalid/", NETWORK="https://search.example/?q=first";
    static final String TOKEN="home-token-\\"quoted\\\\value";
    WebView webView=new WebView();
    final Set<WebView> owned=new HashSet<>(),trustedHomeViews=new HashSet<>(),customHomeViews=new HashSet<>();
    final Map<WebView,String> startedPageUrls=new HashMap<>(),renderedHomeKeys=new HashMap<>(),pageHosts=new HashMap<>();
    final Map<WebView,Boolean> adBlockActiveByView=new HashMap<>();
    final Map<WebView,Object> cosmeticInjected=new HashMap<>();
    final Map<WebView,NavigationGestureGuard> navigationGestureGuards=new HashMap<>();
    final Map<WebView,InitialNavigationGuard> initialNavigationGuards=new HashMap<>();
    final HomePageConfig config=new HomePageConfig();
    final SearchEngines searchEngines=new SearchEngines();
    final AdBlock adBlock=new AdBlock();
    final EditText addressBar=new EditText();
    final Object addressPill=new Object();
    BrowserDataStore dataStore;
    Button refreshButton;
    boolean activityDestroyed,homeThemeRepairPending,startupReady,nightMode,pageFinished,pageCommitted;
    long startupNavigationAt,navigationSequence;
    String currentPageUrl=HOME_URL,currentPageHost="",cachedHomeKey,cachedHomeHtml,searchEngine="google";
    HomeHistoryRegression(){owned.add(webView);renderedHomeKeys.put(webView,"render-current");}
    Object tabForView(WebView view){return owned.contains(view)?view:null;}
    String homeToken(){return TOKEN;}
    HomePageConfig homePageConfig(){return config;}
    List<HomePage.Shortcut> homeShortcuts(List<BrowserDataStore.Bookmark> value){return Collections.emptyList();}
    String homeRenderKey(HomePageConfig value,List<HomePage.Shortcut> shortcuts){return "render-current";}
    boolean isHomeRenderCurrent(WebView view){return "render-current".equals(renderedHomeKeys.get(view));}
    void prepareHomeDestination(WebView view){}
    void scheduleHomeFaviconPrefetch(WebView view,List<HomePage.Shortcut> shortcuts,String key){}
    void updateCurrentTab(String url,String title){}
    void updateTabForView(WebView view,String url,String title){}
    void requestChromeUpdate(){}
    void retireMainFrameNavigation(WebView view,String url){}
    String hostOf(String url){try{String host=URI.create(url).getHost();return host==null?"":host;}catch(Exception e){return "";}}
    boolean isNetworkPage(String url){return url!=null&&(url.startsWith("https://")||url.startsWith("http://"))&&!isHomeUrl(url);}
    boolean isInternalBrowserUrl(String url){return url!=null&&url.startsWith(HOME_URL);}
    boolean isAdBlockActiveForHost(String host){return false;}
    void scheduleCosmeticInjection(WebView view,String host){}
    void recordStartupNavigation(String result,String url){}
    void scheduleLiveMediaCapture(WebView view,String url,long sequence){}
    void scheduleDeferredStartupWork(){}
    void collectConnectionHints(WebView view,String url,long sequence){}
    void injectPreparedStart(long sequence){}
    void injectPreparedEnd(long sequence){}
    void persistSession(){}
    void rememberNavigationSourceDocument(WebView view,String url){}
    void showBrowserChrome(boolean animate){}
    boolean reduceMotion(){return false;}
    void updateAddressBar(){}
    void showHome(){showHome(false);}
    void installAddressListener(){
${source.slice(focusStart, focusEnd)}
    }
${methods}
    static int cases;
    static final List<String> failures=new ArrayList<>();
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void scenario(String label,Runnable action){cases++;try{action.run();}catch(Throwable error){failures.add(label+": "+error.getMessage());}}
    static HomeHistoryRegression restored(){
        HomeHistoryRegression host=new HomeHistoryRegression();
        host.webView.url=HOME_URL;
        host.currentPageUrl=NETWORK;
        host.startedPageUrls.put(host.webView,NETWORK);
        host.trustedHomeViews.clear();
        return host;
    }
    static void expectProbeTrust(HomeHistoryRegression host,WebView view,String label){
        check(view.probes.size()>0,label+" never requested a token probe");
        check(!host.trustedHomeViews.contains(view),label+" trusted the document before its token result");
        view.answerAll("true");
        check(host.trustedHomeViews.contains(view),label+" failed to restore homepage trust");
        check(InternalNavigationPolicy.canHandleCommand(host.trustedHomeViews.contains(view),host.customHomeViews.contains(view)),label+" left homepage search disabled");
        check(view.loads==0,label+" needlessly replaced the current homepage or cleared forward history");
    }
    public static void main(String[] args){
        if(args.length>0&&args[0].equals("probe")){
            HomeHistoryRegression host=restored();host.verifyTrustedHome(host.webView);
            System.out.print(host.webView.lastScript);return;
        }
        scenario("page-finished cached back restoration",()->{
            HomeHistoryRegression host=restored();host.onPageFinished(host.webView,HOME_URL);
            expectProbeTrust(host,host.webView,"onPageFinished after omitted onPageStarted");
        });
        scenario("background page-finished cached restoration",()->{
            HomeHistoryRegression host=restored();WebView restored=host.webView;
            host.webView=new WebView();host.owned.add(host.webView);
            host.onPageFinished(restored,HOME_URL);expectProbeTrust(host,restored,"background onPageFinished");
            check(host.currentPageUrl.equals(NETWORK),"background completion overwrote active page identity");
        });
        scenario("visited-history only cached restoration",()->{
            HomeHistoryRegression host=restored();host.doUpdateVisitedHistory(host.webView,HOME_URL,false);
            expectProbeTrust(host,host.webView,"history-only restoration");
            check(host.currentPageUrl.equals(HOME_URL),"active history did not update page identity");
        });
        scenario("background visited-history restoration",()->{
            HomeHistoryRegression host=restored();WebView restored=host.webView;
            host.webView=new WebView();host.owned.add(host.webView);
            host.doUpdateVisitedHistory(restored,HOME_URL,false);expectProbeTrust(host,restored,"background history");
            check(host.currentPageUrl.equals(NETWORK),"background history overwrote active page identity");
        });
        scenario("null history URL uses provider homepage",()->{
            HomeHistoryRegression host=restored();host.doUpdateVisitedHistory(host.webView,null,false);
            expectProbeTrust(host,host.webView,"null history URL");
        });
        scenario("delayed repair with current rendered home",()->{
            HomeHistoryRegression host=restored();host.currentPageUrl=HOME_URL;
            host.scheduleRestoredHomeRepair(host.webView);host.webView.runPosted();
            expectProbeTrust(host,host.webView,"delayed current-render repair");
        });
        scenario("delayed repair stops after navigation",()->{
            HomeHistoryRegression host=restored();host.scheduleRestoredHomeRepair(host.webView);
            host.webView.url=NETWORK;host.webView.runPosted();
            check(host.webView.probes.isEmpty()&&host.webView.loads==0,"late repair touched the replacement network document");
        });
        scenario("delayed repair stops after tab switch",()->{
            HomeHistoryRegression host=restored();WebView previous=host.webView;
            host.scheduleRestoredHomeRepair(previous);host.webView=new WebView();host.owned.add(host.webView);
            previous.runPosted();check(previous.probes.isEmpty()&&previous.loads==0,"late repair touched inactive tab");
        });
        for(String response:new String[]{"false","null","TRUE"})scenario("invalid token result "+response,()->{
            HomeHistoryRegression host=restored();host.trustedHomeViews.add(host.webView);host.customHomeViews.add(host.webView);
            host.verifyTrustedHome(host.webView);host.webView.answerAll(response);
            check(!host.trustedHomeViews.contains(host.webView)&&!host.customHomeViews.contains(host.webView),"invalid probe retained homepage capability");
        });
        scenario("custom home remains excluded from built-in commands",()->{
            HomeHistoryRegression host=restored();host.config.customHtmlEnabled=true;
            host.verifyTrustedHome(host.webView);host.webView.answerAll("true");
            check(host.trustedHomeViews.contains(host.webView)&&host.customHomeViews.contains(host.webView),"custom home classification lost");
            check(!InternalNavigationPolicy.canHandleCommand(true,host.customHomeViews.contains(host.webView)),"custom HTML received built-in search commands");
        });
        for(String lifecycle:new String[]{"closed","frozen","destroyed","navigated"})scenario("late probe after "+lifecycle,()->{
            HomeHistoryRegression host=restored();WebView view=host.webView;
            host.verifyTrustedHome(view);host.trustedHomeViews.add(view);host.customHomeViews.add(view);
            if(lifecycle.equals("destroyed"))host.activityDestroyed=true;
            else if(lifecycle.equals("navigated"))view.url=NETWORK;
            else host.owned.remove(view);
            view.answerAll("true");
            check(!host.trustedHomeViews.contains(view)&&!host.customHomeViews.contains(view),"late renderer result resurrected homepage capabilities");
        });
        scenario("detached view cannot begin probe",()->{
            HomeHistoryRegression host=restored();host.owned.clear();host.verifyTrustedHome(host.webView);
            check(host.webView.probes.isEmpty(),"detached renderer was queried");
        });
        for(String origin:new String[]{HOME_URL,NETWORK})scenario("canonical homepage history from "+origin,()->{
            HomeHistoryRegression host=new HomeHistoryRegression();host.webView.url=origin;host.renderedHomeKeys.clear();
            host.showHome(false);
            check(host.webView.loads==1&&HOME_URL.equals(host.webView.historyUrl),"homepage created an about:blank history entry");
            check(host.webView.clears==0,"ordinary homepage rendering destroyed navigation history");
            host.showHome(false);check(host.webView.loads==1,"identical visible homepage rendered twice");
        });
        scenario("explicit home reset keeps canonical history",()->{
            HomeHistoryRegression host=new HomeHistoryRegression();host.renderedHomeKeys.clear();host.showHome(true);
            check(host.webView.clears==1&&host.webView.stops==1,"explicit reset stopped clearing stale themed history");
            check(HOME_URL.equals(host.webView.historyUrl),"reset used blank history identity");
        });
        for(String url:new String[]{"about:blank","ABOUT:BLANK","About:Blank",HOME_URL,NETWORK})scenario("focused address "+url,()->{
            HomeHistoryRegression host=new HomeHistoryRegression();host.installAddressListener();host.currentPageUrl=url;
            host.addressBar.text="old text";host.addressBar.listener.onFocusChange(new View(),true);
            check(host.addressBar.text.equals(url.equals(NETWORK)?NETWORK:""),"focused address exposed an internal blank URL or changed real address");
            check(host.addressBar.selections==1,"focus no longer selects the current address");
        });
        for(String failure:failures)System.err.println("FAIL "+failure);
        if(!failures.isEmpty())throw new AssertionError(failures.size()+" of "+cases+" homepage/history scenarios failed");
        System.out.println("Home history Java behavior passed: "+cases+" production callback/history/focus/lifecycle cases");
    }
}
class CallbackBase{public void onPageFinished(WebView view,String url){}public void doUpdateVisitedHistory(WebView view,String url,boolean reload){}}
interface ValueCallback<T>{void onReceiveValue(T value);}
class WebView{
    String url=HomeHistoryRegression.HOME_URL,title="Home",historyUrl,lastScript;
    int loads,clears,stops;
    final Queue<Runnable> posted=new ArrayDeque<>();
    final Queue<ValueCallback<String>> probes=new ArrayDeque<>();
    String getUrl(){return url;}String getTitle(){return title;}
    void evaluateJavascript(String script,ValueCallback<String> callback){lastScript=script;probes.add(callback);}
    void post(Runnable action){posted.add(action);}void postDelayed(Runnable action,long delay){posted.add(action);}
    void runPosted(){while(!posted.isEmpty())posted.remove().run();}
    void answerAll(String result){while(!probes.isEmpty())probes.remove().onReceiveValue(result);}
    void stopLoading(){stops++;}void clearHistory(){clears++;}
    void loadDataWithBaseURL(String base,String html,String mime,String encoding,String history){loads++;url=base;historyUrl=history==null?"about:blank":history;}
}
class HomePageConfig{boolean customHtmlEnabled;}
class HomePage{static class Shortcut{}static String html(String engine,List<BrowserDataStore.Bookmark> bookmarks,boolean night,String token,HomePageConfig config,Object engines,List<Shortcut> shortcuts){return "generated homepage";}}
class BrowserDataStore{static class Bookmark{}List<Bookmark> bookmarks(){return Collections.emptyList();}void recordVisit(String title,String url){}}
class SearchEngines{Object customEngines(){return null;}}
class AdBlock{boolean requiresEarlyCosmetic(String host){return false;}}
class NavigationGestureGuard{void onNavigationCommitted(String url){}}
class InitialNavigationGuard{boolean acknowledge(String url){return false;}}
class Button{Button animate(){return this;}Button rotation(float value){return this;}Button setDuration(long value){return this;}void start(){}}
class View{interface OnFocusChangeListener{void onFocusChange(View view,boolean hasFocus);}}
class EditText{String text="";int selections;View.OnFocusChangeListener listener;void setOnFocusChangeListener(View.OnFocusChangeListener value){listener=value;}void setText(String value){text=value;}String getText(){return text;}void selectAll(){selections++;}}
class Motion{static void focusPill(Object pill,boolean focused,boolean reduced){}}
class JSONObject{static String quote(String value){return "\\\""+value.replace("\\\\","\\\\\\\\").replace("\\\"","\\\\\\\"")+"\\\"";}}
class UrlCleaner{static boolean isInternalPage(String value,String host){try{URI uri=URI.create(value);return "https".equalsIgnoreCase(uri.getScheme())&&host.equalsIgnoreCase(uri.getHost())&&(uri.getPort()==-1||uri.getPort()==443)&&uri.getUserInfo()==null&&(uri.getPath().isEmpty()||uri.getPath().equals("/"))&&uri.getQuery()==null;}catch(Exception e){return false;}}}
`);
function run(name, arguments_) {
  const result = cp.spawnSync(executable(name), arguments_, { encoding: 'utf8', windowsHide: true });
  if (result.error || result.status !== 0) throw result.error || new Error(name + ' exit ' + result.status + '\n' + result.stdout + result.stderr);
  return result.stdout;
}
try {
  run('javac', ['-encoding', 'UTF-8', '--release', '17', '-d', out, harness,
    path.join(repo, 'app/src/main/java/com/xinyv/median/InternalNavigationPolicy.java')]);
  const probe = run('java', ['-cp', out, 'com.xinyv.median.HomeHistoryRegression', 'probe']);
  const token = 'home-token-"quoted\\value';
  const document = content => ({ querySelector(selector) {
    assert.equal(selector, 'meta[name=median-home-token]');
    return content === undefined ? null : { content };
  } });
  for (const [content, expected] of [[token, true], ['forged-token', false], ['', false], [undefined, false]]) {
    assert.equal(vm.runInNewContext(probe, { document: document(content) }), expected,
      'production home probe accepted a forged/missing token or rejected its issued token');
  }
  console.log('Home token JavaScript passed: issued, forged, empty and missing meta tokens');
  process.stdout.write(run('java', ['-cp', out, 'com.xinyv.median.HomeHistoryRegression']));
} finally {
  if (!persistentOut) {
    if (path.dirname(out) !== path.resolve(os.tmpdir()) || !path.basename(out).startsWith('median-home-history-'))
      throw new Error('Refusing cleanup outside generated home history harness directory');
    fs.rmSync(out, { recursive: true, force: true });
  }
}
