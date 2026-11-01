package com.xinyv.median;

/** One bounded post-load query; it neither requests resources nor installs observers. */
final class ConnectionHintsScript {
    private static final String COLLECT_SCRIPT =
            "(function(){try{var n=performance.getEntriesByType('navigation')[0];if(!n||n.responseStatus>=400)return null;" +
            // Android's onPageFinished can precede the document load event. Until then,
            // only inspect navigation state; do not discard the image observation.
            "var cut=n.loadEventStart;if(document.readyState!=='complete'||typeof cut!=='number'||!isFinite(cut)||!(cut>0))return JSON.stringify({o:location.origin,p:true});" +
            "var a=performance.getEntriesByType('resource').slice(0,400),c=[],r=[],images=Object.create(null),urls=Object.create(null),uses=Object.create(null);" +
            // currentSrc is already the browser's selected absolute URL. Match it in full,
            // without inspecting src/srcset alternatives or forcing style/layout queries.
            "if(typeof cut==='number'&&isFinite(cut)&&cut>0){var list=document.images||[];" +
            "for(var j=0;j<list.length&&j<256;j++){var x=list[j],s=x&&x.currentSrc;" +
            "if(!x||!x.complete||!(x.naturalWidth>=16)||!(x.naturalHeight>=16)||" +
            "!isFinite(x.naturalWidth)||!isFinite(x.naturalHeight)||" +
            "String(x.loading||'').toLowerCase()==='lazy'||" +
            "String(x.getAttribute&&x.getAttribute('loading')||'').toLowerCase()==='lazy'||" +
            "typeof s!=='string'||!s||s.length>2048)continue;images[s]=1;}}" +
            "a.sort(function(x,y){return y.duration-x.duration;});" +
            "for(var i=0;i<a.length;i++){var e=a[i],t=e.initiatorType,u;" +
            "if(typeof e.responseEnd!=='number'||!isFinite(e.responseEnd)||!(e.responseEnd>0)||" +
            "typeof e.duration!=='number'||!isFinite(e.duration)||e.duration<0||e.responseStatus>=400||" +
            "typeof e.name!=='string'||e.name.length>2048)continue;" +
            "try{u=new URL(e.name);}catch(_){continue;}" +
            "if(t==='script')t='script';" +
            // A used image preload can have a link initiator. DOM decoding plus the load
            // boundary supplies the evidence; unused preloads never enter the URL set.
            "else if((t==='img'||t==='image'||t==='link')&&images[e.name]&&e.responseEnd<=cut)t='image';" +
            "else if(t==='link'&&(/\\.css$/i.test(u.pathname)||" +
            "String(e.contentType||'').toLowerCase().split(';',1)[0].trim()==='text/css'))t='stylesheet';" +
            "else if((t==='css'||t==='link')&&/\\.(woff2?|ttf|otf)$/i.test(u.pathname))t='font';" +
            "else continue;" +
            "if(!/^https?:$/.test(u.protocol)||u.username||u.password||u.origin===location.origin)continue;" +
            // A page can have many slow resources from one origin. Keep distinct-origin
            // candidates for both classes before using spare slots for full-URL backups.
            // Backups matter because Main applies the real ad-block rules to each URL.
            "var k=t+':'+e.name;if(urls[k])continue;urls[k]=1;" +
            "c.push({u:e.name,t:t,e:e.responseEnd,d:e.duration,o:u.origin});}" +
            // Each chosen URL scans at most 400 candidates: unused origins win before
            // second URLs, then second URLs before thirds. Duration breaks each tie.
            "function pick(image,limit){for(var j=0;j<limit;j++){var best=null,least=401;" +
            "for(var i=0;i<c.length;i++){var e=c[i];if(e.s||(image!==null&&(e.t==='image')!==image))continue;" +
            "var k=(e.t==='image'?'i:':'c:')+e.o,count=uses[k]||0;if(count<least){best=e;least=count;}}" +
            "if(!best)break;best.s=1;var k=(best.t==='image'?'i:':'c:')+best.o;uses[k]=(uses[k]||0)+1;" +
            "r.push({u:best.u,t:best.t,e:best.e,d:best.d});}}" +
            "pick(false,8);pick(true,8);pick(null,16-r.length);" +
            "r.sort(function(x,y){return y.d-x.d;});" +
            "return JSON.stringify({o:location.origin,r:r});}catch(_){return null;}})()";

    static String collectScript() { return COLLECT_SCRIPT; }

    private ConnectionHintsScript() {}
}
