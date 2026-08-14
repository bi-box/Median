package com.xinyv.median;

/** Bounded media discovery scripts. Generated JavaScript is syntax and behavior tested. */
final class MediaProbeScript {
    private static final String URL_PATTERN =
            "(?:\\.(?:m3u8?|mpd|ism|isml|mp4|webm|mkv|mov|m4v|flv|ogv|3g[p2]|avi|wmv|asf|mpg|mpeg|m2ts|mts|vob|rmvb|m4s|cmfv|cmfa|mp2t|ts|mp3|m4a|aac|ogg|oga|opus|flac|wav|weba|ape|wma|alac|ac3|eac3)(?:[?#&/=;%]|$)|(?:mime|mime_type|content_type|type|format|ext)=(?:video|audio|hls|dash|smooth|mp4|webm|mp3|m3u8|mpd|ism)|(?:playback|play|video|audio|stream|manifest|playlist)(?:_|-)?url=|\\/(?:manifest|playlist|master|video|audio|segment|smooth|hls|dash)(?:[/?#]|$))";

    /** Installs a passive, bounded Resource Timing/media-event collector for dynamic players. */
    static String install() {
        return "(function(){if(window.__medianMediaLog)return;var L=[],S={},M=240,R=/" + URL_PATTERN + "/i;" +
                "function a(u,t,v,w,h,d){u=String(u||'').replace(/&amp;/g,'&');if(!u)return;" +
                "if(/^blob:/i.test(u)){if(!S['@'+u]){S['@'+u]=1;window.__medianMediaOpaque=Math.min(99,(window.__medianMediaOpaque||0)+1);}return;}" +
                "try{u=new URL(u,location.href).href;}catch(e){return;}if(!/^https?:/i.test(u)||S[u]||L.length>=M)return;" +
                "if(!v&&!R.test(u)&&!/^video\\/|^audio\\/|mpegurl|dash\\+xml|vnd\\.ms-sstr/i.test(t||''))return;S[u]=1;L.push({url:u,mime:t||'',source:v||'live',width:w||0,height:h||0,duration:d||0});}" +
                "try{Object.defineProperty(window,'__medianMediaLog',{value:L,configurable:false,enumerable:false});}catch(e){window.__medianMediaLog=L;}" +
                "function m(e){var x=e&&e.target;if(!x)return;a(x.currentSrc||x.src,x.type||'',x.tagName==='VIDEO'?'video':'audio',x.videoWidth||0,x.videoHeight||0,isFinite(x.duration)?x.duration:0);}" +
                "document.addEventListener('loadedmetadata',m,true);document.addEventListener('play',m,true);" +
                "try{if(typeof PerformanceObserver==='function'){var p=new PerformanceObserver(function(x){var q=x.getEntries(),i,e,v;for(i=0;i<q.length;i++){e=q[i];v=e.initiatorType==='video'||e.initiatorType==='audio';a(e.name,v?e.initiatorType+'/*':'',v?e.initiatorType:'performance');}});p.observe({entryTypes:['resource']});}}catch(e){}" +
                "try{var q=performance.getEntriesByType('resource'),i,e,v;for(i=0;i<q.length;i++){e=q[i];v=e.initiatorType==='video'||e.initiatorType==='audio';a(e.name,v?e.initiatorType+'/*':'',v?e.initiatorType:'performance');}}catch(e){}" +
                "})();";
    }

    static String build() {
        return "(function(){var o=[],s={},B={},n=0,v=0,opaque=Number(window.__medianMediaOpaque||0),R=/" + URL_PATTERN + "/i;" +
                "function a(u,t,f,src,w,h,d){u=String(u||'').replace(/&amp;/g,'&').replace(/\\\\u002[fF]/g,'/').replace(/\\\\\\//g,'/');if(!u)return;" +
                "if(/^blob:/i.test(u)){if(!B[u]){B[u]=1;opaque++;}return;}try{u=new URL(u,location.href).href;}catch(e){return;}" +
                "if(!/^https?:/i.test(u)||s[u]||o.length>=240||(!f&&!R.test(u)&&!/^video\\/|^audio\\/|mpegurl|dash\\+xml|vnd\\.ms-sstr/i.test(t||'')))return;" +
                "s[u]=1;o.push({url:u,mime:t||'',source:src||'',width:Number(w||0),height:Number(h||0),duration:Number(d||0)});}" +
                "function scan(r,depth){if(!r||depth>3||o.length>=240)return;var E,i,e,t,f,z,A=['data-src','data-url','data-video-url','data-audio-url','data-stream-url','data-play-url','data-playback-url','data-hls','data-m3u8','data-mpd','data-manifest'];" +
                "try{E=r.querySelectorAll('video,audio,source,track,link[rel=preload],a[href],[data-src],[data-url],[data-video-url],[data-audio-url],[data-stream-url],[data-play-url],[data-playback-url],[data-hls],[data-m3u8],[data-mpd],[data-manifest]');}catch(x){E=[];}" +
                "for(i=0;i<E.length&&o.length<240;i++){e=E[i];t=e.type||e.getAttribute&&e.getAttribute('type')||'';f=/^(VIDEO|AUDIO|SOURCE|TRACK)$/.test(e.tagName);" +
                "a(e.currentSrc||e.src||e.href,t,f,'dom',e.videoWidth||0,e.videoHeight||0,isFinite(e.duration)?e.duration:0);" +
                "for(var j=0;j<A.length;j++)a(e.getAttribute&&e.getAttribute(A[j]),t,f,'attribute');z=e.getAttribute&&e.getAttribute('srcset');if(z){z=z.split(',');for(j=0;j<z.length;j++)a(z[j].trim().split(/\\s+/)[0],t,f,'srcset');}" +
                "}try{var Z=r.querySelectorAll('*');for(i=0;i<Z.length&&v++<3500&&o.length<240;i++){e=Z[i];if(e.shadowRoot)scan(e.shadowRoot,depth+1);if(e.tagName==='IFRAME'&&e.contentDocument)scan(e.contentDocument,depth+1);}}catch(x){}}" +
                "scan(document,0);" +
                "try{var E=document.querySelectorAll('meta[property^=\"og:video\"],meta[property^=\"og:audio\"],meta[name=\"twitter:player:stream\"],[itemprop=\"contentUrl\"]');for(var i=0;i<E.length;i++)a(E[i].content||E[i].href||E[i].getAttribute('content')||E[i].getAttribute('href'),' ',false,'metadata');}catch(e){}" +
                "try{var L=window.__medianMediaLog||[];for(var i=0;i<L.length;i++){var x=L[i]||{};a(x.url,x.mime,true,x.source||'live',x.width,x.height,x.duration);}}catch(e){}" +
                "try{var P=performance.getEntriesByType('resource');for(var i=0;i<P.length;i++){var e=P[i],f=e.initiatorType==='video'||e.initiatorType==='audio';a(e.name,f?e.initiatorType+'/*':'',f,e.initiatorType||'performance');}}catch(e){}" +
                "function walk(x,k,seen,depth){if(!x||depth>8||o.length>=240)return;if(typeof x==='string'){if(/^(?:contentUrl|embedUrl|streamingUrl|playbackUrl|manifestUrl|playlistUrl|videoUrl|audioUrl|url|src)$/i.test(k||'')||R.test(x))a(x,'',false,'json');return;}" +
                "if(typeof x!=='object')return;try{if(seen.indexOf(x)>=0)return;seen.push(x);if(seen.length>5000)return;}catch(e){return;}if(Array.isArray(x)){for(var i=0;i<x.length&&i<256;i++)walk(x[i],k,seen,depth+1);return;}var c=0;for(var q in x)if(Object.prototype.hasOwnProperty.call(x,q)&&c++<256)walk(x[q],q,seen,depth+1);}" +
                "try{var J=document.querySelectorAll('script[type=\"application/ld+json\"],script#__NEXT_DATA__');for(var i=0;i<J.length&&n<2097152;i++){var z=J[i].textContent||'';n+=z.length;try{walk(JSON.parse(z||'null'),'',[],0);}catch(x){}}}catch(e){}" +
                "try{var G=['__NEXT_DATA__','__NUXT__','__INITIAL_STATE__','__APOLLO_STATE__'];for(var i=0;i<G.length;i++)if(window[G[i]])walk(window[G[i]],G[i],[],0);}catch(e){}" +
                "try{var I=document.querySelectorAll('script:not([src])');for(var i=0;i<I.length&&n<2097152&&o.length<240;i++){var x=(I[i].textContent||'').slice(0,131072);n+=x.length;var M=x.match(/(?:https?:)?\\\\?\\/\\\\?\\/[^\\s\"'<>]+/g)||[];for(var j=0;j<M.length&&j<64;j++)a(M[j],'',false,'inline');}}catch(e){}" +
                "o.push({opaque:Math.min(99,opaque)});return JSON.stringify(o);})();";
    }

    private MediaProbeScript() {}
}
