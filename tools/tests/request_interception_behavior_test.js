'use strict';
// Compile the real request callbacks with the real ad/media/navigation engines. The before
// source is optional evidence from a frozen candidate, never part of the shipped application.
const fs = require('fs'), path = require('path'), cp = require('child_process');
const args = process.argv.slice(2), option = (n, d) => args.includes(n) ? args[args.indexOf(n) + 1] : d;
const repo = path.resolve(__dirname, '../..'), src = path.join(repo, 'app/src/main/java/com/xinyv/median');
const out = path.resolve(option('--out', path.join(repo, '_eval_tmp/request-interception')));
const jdk = option('--jdk', process.env.JAVA_HOME || 'D:/MedianToolchain/jdk17');
fs.mkdirSync(out, { recursive: true });
function extract(source, signature) {
  const begin = source.indexOf(signature); if (begin < 0) throw new Error('Missing ' + signature);
  let depth = 0;
  for (let i = source.indexOf('{', begin); i < source.length; i++) {
    if (source[i] === '{') depth++;
    else if (source[i] === '}' && --depth === 0) return source.slice(begin, i + 1);
  }
  throw new Error('Unterminated ' + signature);
}
const production = fs.readFileSync(path.join(src, 'MainActivity.java'), 'utf8');
const baseline = option('--before', null), before = baseline ? fs.readFileSync(baseline, 'utf8') : production;
const beforeMedia = option('--before-media', null);
if (beforeMedia) fs.writeFileSync(path.join(out, 'BeforeMediaResourceSniffer.java'),
 fs.readFileSync(beforeMedia, 'utf8').replaceAll('MediaResourceSniffer', 'BeforeMediaResourceSniffer'));
// Android Uri accepts raw JSON punctuation in queries; java.net.URI does not. Keep the
// captured query unchanged while using the existing JVM stub for scheme/host/path only.
const uriStub = path.join(out, 'Uri.java');
fs.writeFileSync(uriStub, fs.readFileSync(path.join(__dirname, 'adblock_stubs/android/net/Uri.java'), 'utf8')
 .replace('URI.create(raw);', 'URI.create(raw.split("[?#]", 2)[0]);')
 .replace('return parsed.getRawQuery();', 'int q=raw.indexOf(\'?\'),f=raw.indexOf(\'#\'); return q<0||(f>=0&&f<q)?null:raw.substring(q+1,f<0?raw.length():f);'));
const signatures = [
  '    private WebResourceResponse interceptRequest(WebView source, WebResourceRequest request)',
  '    private static String requestHeader(', '    private static String mediaRequestHint(',
  '    private static boolean shouldReadAcceptHeader(', '    private static boolean endsWithIgnoreCase(',
  '    private void observeMediaCandidate('
];
function methods(source) {
  return signatures.map(s => extract(source, s)).concat(source.includes('    private static boolean isMediaCandidate(') ?
    [extract(source, '    private static boolean isMediaCandidate(')] : []).join('\n');
}
const fixture = `
 static final class WebView {}
 static final class WebResourceResponse { final String kind; WebResourceResponse(String s){kind=s;} }
 static final class SystemClock { static long uptimeMillis(){return 1000L;} }
 static final class WebResourceRequest {
  final Uri uri; final boolean main,gesture,redirect; final Map<String,String> headers; int reads;
  WebResourceRequest(String u,boolean m,Map<String,String> h){uri=u==null?null:Uri.parse(u);main=m;headers=h;gesture=false;redirect=false;}
  Uri getUrl(){return uri;} boolean isForMainFrame(){return main;} boolean hasGesture(){return gesture;}
  boolean isRedirect(){return redirect;} Map<String,String> getRequestHeaders(){reads++;return headers;}
 }
 final WebView webView=new WebView(),background=new WebView(); boolean adBlockEnabled=true;
 final AdBlockEngine adBlock=new AdBlockEngine(); final MediaResourceSniffer mediaSniffer=new MediaResourceSniffer();
 final Set<WebView> customHomeViews=new HashSet<>();
 final Map<WebView,MainFrameNavigationContext> mainFrameNavigationContexts=new HashMap<>();
 final Map<WebView,NavigationGestureGuard> navigationGestureGuards=new HashMap<>();
 final Map<WebView,String> popupSourceDocuments=new HashMap<>(),navigationSourceDocuments=new HashMap<>();
 WebResourceResponse interceptHomeAsset(WebView v,Uri u){return null;}
 String pageHostFor(WebView v){return "page.example";}
 String hostOf(String u){try{return java.net.URI.create(u).getHost()==null?"":java.net.URI.create(u).getHost();}catch(Exception e){return "";}}
 boolean cachedAdBlockActive(WebView v,String h){return adBlockEnabled;}
 boolean navigationProtectionActive(String h){return adBlockEnabled;}
 WebResourceResponse blockedResponse(){return new WebResourceResponse("blocked");}
 WebResourceResponse blockedNavigationResponse(){return new WebResourceResponse("navigation");}
 long bench(String[] urls,int loops){
  WebResourceRequest[] requests=new WebResourceRequest[urls.length];
  for(int i=0;i<urls.length;i++)requests[i]=new WebResourceRequest(urls[i],false,Collections.emptyMap());
  adBlock.updateCustomRules("");mediaSniffer.beginPage("https://page.example");
  long began=System.nanoTime();
  for(int n=0;n<loops;n++)for(WebResourceRequest request:requests)interceptRequest(webView,request);
  return System.nanoTime()-began;
 }
 String run(String u,boolean main,boolean active,Map<String,String> headers,String rules,boolean custom,boolean context){
  adBlock.updateCustomRules(rules); WebView view=active?webView:background;
  if(custom)customHomeViews.add(view);
  if(context){MainFrameNavigationContext c=new MainFrameNavigationContext();c.arm(u,"source.example",true);mainFrameNavigationContexts.put(view,c);}
  navigationGestureGuards.put(view,new NavigationGestureGuard(8f));
  navigationSourceDocuments.put(view,"https://page.example/source");
  WebResourceRequest request=new WebResourceRequest(u,main,headers);
  WebResourceResponse response=interceptRequest(view,request);
  StringBuilder result=new StringBuilder(response==null?"allow":response.kind);result.append('|').append(request.reads);
  for(MediaResourceSniffer.Resource r:mediaSniffer.getAll())result.append('|').append(r.url).append(':').append(r.kind).append(':').append(r.mime);
  AdBlockEngine.Stats s=adBlock.getStats();return result.append('|').append(s.inspectedRequests).append('/').append(s.blockedRequests).append('/').append(s.allowedRequests).toString();
 }
`;
const imports = 'package com.xinyv.median; import java.util.*; import android.net.Uri;\n';
for (const [name, source] of [['BeforeRequestHarness', before], ['AfterRequestHarness', production]]) {
  let body = fixture + methods(source);
  if (name === 'BeforeRequestHarness' && beforeMedia) body = body.replaceAll('MediaResourceSniffer', 'BeforeMediaResourceSniffer');
  fs.writeFileSync(path.join(out, name + '.java'), imports + 'final class ' + name + '{\n' + body + '\n}');
}
const test = `${imports} public final class RequestInterceptionSelfTest {
 static int count;
 static void compare(String url,boolean main,boolean active,Map<String,String> headers,String rules,boolean custom,boolean context){
  String before=new BeforeRequestHarness().run(url,main,active,headers,rules,custom,context);
  String after=new AfterRequestHarness().run(url,main,active,headers,rules,custom,context);
  if(!before.equals(after))throw new AssertionError(url+" before="+before+" after="+after);count++;
 }
 public static void main(String[] args){
  List<Map<String,String>> headers=Arrays.asList(Collections.emptyMap(),Map.of("Accept","*/*","Range","bytes=0-10"),
   Map.of("accept"," video/mp4; codecs=avc1","sec-fetch-dest","video"),Map.of("Accept","application/json","Sec-Fetch-Dest","audio"),
   Map.of("Accept","text/html,application/xhtml+xml"),Map.of("ACCEPT","application/vnd.apple.mpegurl"));
  String[] urls={null,"https://cdn.example/assets/app.js","https://cdn.example/assets/app.js?source=movie.mp4",
   "https://cdn.example/api/resource?id=7","https://cdn.example/play?id=7","https://cdn.example/opaque",
   "https://cdn.example/manifest","https://cdn.example/MOVIE.MP4","https://cdn.example/api?url=movie%252Emp4",
   "https://blocked.example/stream?id=9","https://www.googletagmanager.com/gtag/js?id=X", "https://cdn.example/image.png",
   "https://cdn.example/app.js?type=VIDEO","https://cdn.example/app.js?type=v\\u0131deo","https://cdn.example/playlist"};
  for(String url:urls)for(boolean main:new boolean[]{false,true})for(boolean active:new boolean[]{false,true})
   for(Map<String,String> h:headers)for(String rules:new String[]{"","||blocked.example^","||cdn.example^\\n@@||cdn.example/play","||cdn.example$image"})
    compare(url,main,active,h,rules,false,url!=null&&main);
  compare("https://cdn.example/app.js",false,true,Collections.emptyMap(),"",true,false);
  compare("https://median.invalid/home-logo",false,true,Collections.emptyMap(),"",true,false);
  String staticResult=new AfterRequestHarness().run("https://cdn.example/assets/app.js",false,true,Collections.emptyMap(),"",false,false);
  if(!staticResult.equals("allow|0|1/0/0"))throw new AssertionError("Static asset avoids header IPC and media index: "+staticResult);
  String blockedResult=new AfterRequestHarness().run("https://blocked.example/movie.mp4",false,true,Collections.emptyMap(),"||blocked.example^",false,false);
  if(!blockedResult.equals("blocked|0|1/1/0"))throw new AssertionError("Blocked media never reaches index: "+blockedResult);
  String playback=new AfterRequestHarness().run("https://cdn.example/play?id=7",false,true,Map.of("Range","bytes=0-10","Accept","*/*"),"",false,false);
  if(!playback.contains("|1|https://cdn.example/play?id=7:范围媒体候选:application/x-median-range"))throw new AssertionError(playback);
  if(args.length>0)for(String url:java.nio.file.Files.readAllLines(java.nio.file.Path.of(args[0])))
    for(boolean main:new boolean[]{false,true})compare(url,main,true,Collections.emptyMap(),"",false,false);
  System.out.println("RequestInterceptionSelfTest passed ("+count+" before/after cases)");
  if(args.length>0){
   String[] replayUrls=java.nio.file.Files.readAllLines(java.nio.file.Path.of(args[0])).toArray(new String[0]);
   for(int n=0;n<4;n++){new BeforeRequestHarness().bench(replayUrls,2);new AfterRequestHarness().bench(replayUrls,2);}
   long[] b=new long[9],a=new long[9];int loops=8;
   for(int n=0;n<9;n++){if((n&1)==0){b[n]=new BeforeRequestHarness().bench(replayUrls,loops);a[n]=new AfterRequestHarness().bench(replayUrls,loops);}
    else{a[n]=new AfterRequestHarness().bench(replayUrls,loops);b[n]=new BeforeRequestHarness().bench(replayUrls,loops);}}
   Arrays.sort(b);Arrays.sort(a);
   System.out.println("RequestReplayBenchmark urls="+replayUrls.length+" loops="+loops+" rounds=9 beforeMedianMs="+b[4]/1e6+" afterMedianMs="+a[4]/1e6+
    " beforeNsPerRequest="+b[4]/(double)(replayUrls.length*loops)+" afterNsPerRequest="+a[4]/(double)(replayUrls.length*loops)+" ratio="+a[4]/(double)b[4]);
  }
 }
}`.replace('public static void main(String[] args){', 'public static void main(String[] args) throws Exception {');
fs.writeFileSync(path.join(out, 'RequestInterceptionSelfTest.java'), test);
function run(command, argv) {
  const r = cp.spawnSync(path.join(jdk, 'bin', command + (process.platform === 'win32' ? '.exe' : '')), argv, { encoding: 'utf8' });
  if (r.error || r.status !== 0) throw new Error(r.error || r.stdout + r.stderr);
  process.stdout.write(r.stdout);
}
run('javac', ['-encoding', 'UTF-8', '--release', '17', '-d', out,
 uriStub,
 ...['AdBlockEngine.java','MediaResourceSniffer.java','MainFrameNavigationContext.java','NavigationGestureGuard.java'].map(n => path.join(src,n)),
 ...['BeforeRequestHarness.java','AfterRequestHarness.java','RequestInterceptionSelfTest.java'].map(n => path.join(out,n)),
 ...beforeMedia ? [path.join(out, 'BeforeMediaResourceSniffer.java')] : []]);
run('java', ['-Xmx768m', '-cp', out, 'com.xinyv.median.RequestInterceptionSelfTest', ...args.includes('--urls') ? [option('--urls')] : []]);
