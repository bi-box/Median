package com.xinyv.median;

/** Small document-context hints; Chromium retains control of DNS, TLS, cookies and fetching. */
final class ConnectionPreconnectScript {
    private ConnectionPreconnectScript() {}

    static String build(String navigationUrl, String[] origins) {
        String page = ConnectionHintsStore.origin(navigationUrl);
        if (page.length() == 0 || origins == null) return "";
        String[] selected = new String[ConnectionHintsStore.MAX_HINTS];
        int count = 0;
        for (int i = 0; i < origins.length && i < 20 && count < selected.length; i++) {
            String origin = ConnectionHintsStore.origin(origins[i]);
            if (!origin.equals(origins[i]) || origin.length() == 0 || origin.equals(page) ||
                    (page.startsWith("https://") && !origin.startsWith("https://"))) continue;
            boolean duplicate = false;
            for (int j = 0; j < count; j++) if (origin.equals(selected[j])) duplicate = true;
            if (!duplicate) selected[count++] = origin;
        }
        if (count == 0) return "";
        // Validated origins are ASCII scheme/host/port only, without quotes or URL components.
        StringBuilder script = new StringBuilder(1300);
        script.append("(function(){try{if(window.top!==window||location.origin!=='")
                .append(page).append("')return;var s=window.__medianConnectionHintsDone;")
                .append("if(!Array.isArray(s))s=window.__medianConnectionHintsDone=[];var a=[");
        for (int i = 0; i < count; i++) {
            if (i > 0) script.append(',');
            script.append('\'').append(selected[i]).append('\'');
        }
        script.append("],m=null,t=0;function stop(){if(m)m.disconnect();if(t)clearTimeout(t);}")
                .append("function add(){var h=document.head;if(!h)return false;stop();")
                .append("for(var i=0;i<a.length;i++){if(s.length>=4||s.indexOf(a[i])>=0)continue;s.push(a[i]);var p=document.createElement('link');")
                .append("p.rel='preconnect';p.href=a[i];p.setAttribute('data-median-connection','');h.appendChild(p);")
                .append("var d=document.createElement('link');d.rel='dns-prefetch';d.href=a[i];h.appendChild(d);}return true;}")
                .append("if(!add()){m=new MutationObserver(add);m.observe(document,{childList:true,subtree:true});")
                .append("t=setTimeout(stop,2000);}}catch(_){}})();");
        return script.toString();
    }
}
