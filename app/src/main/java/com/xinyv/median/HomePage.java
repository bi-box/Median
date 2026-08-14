package com.xinyv.median;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Fully local start page: zero startup network requests and a token-authenticated command channel. */
final class HomePage {
    static final class Shortcut {
        final boolean folder;
        final String id;
        final String title;
        final String url;
        final long createdAt;

        Shortcut(boolean folder, String id, String title, String url, long createdAt) {
            this.folder = folder;
            this.id = id == null ? "" : id;
            this.title = title == null ? "" : title;
            this.url = url == null ? "" : url;
            this.createdAt = createdAt;
        }
    }

    private static final String BASE_CSS =
            "*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}" +
            "html,body{margin:0;min-height:100%;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;background:var(--bg);color:var(--fg)}" +
            "body{display:flex;justify-content:center;overflow-x:hidden}" +
            ".wall,.shade{position:fixed;z-index:0;top:0;right:0;bottom:0;left:0;pointer-events:none}" +
            ".wall{background-position:center;background-repeat:no-repeat;background-size:var(--fit);transform:scale(var(--scale));filter:blur(var(--blur))}" +
            ".shade{background:rgba(0,0,0,var(--dim))}" +
            ".wrap{position:relative;z-index:1;width:100%;max-width:680px;padding:10vh 20px 44px;text-align:center;opacity:0;transform:translateY(10px);animation:enter .38s cubic-bezier(.2,0,0,1) forwards}" +
            ".wrap.compact{padding-top:4.5vh}.logo-gradient{display:inline-block;background:var(--logo-gradient);-webkit-background-clip:text;background-clip:text;color:transparent;-webkit-text-fill-color:transparent}" +
            ".logo-space{display:inline-block;height:1px;letter-spacing:0}" +
            ".logo{object-fit:contain;margin:0 auto;filter:drop-shadow(0 2px 12px rgba(0,0,0,.32))}" +
            ".subtitle{min-height:20px;margin:0 0 18px;color:var(--secondary);font-size:14px;text-shadow:var(--shadow)}" +
            ".clock{margin:0 0 16px;text-shadow:0 2px 12px rgba(0,0,0,.35)}.time{font-size:35px;font-weight:650;letter-spacing:-1px}.date{font-size:12px;color:var(--secondary);margin-top:2px}" +
            ".search{height:54px;transform:translateZ(0);border:1px solid var(--border);border-radius:28px;display:flex;align-items:center;padding:0 18px;background:var(--search-bg);box-shadow:0 2px 10px rgba(0,0,0,.16);transition:box-shadow .18s,transform .16s;-webkit-backdrop-filter:blur(14px);backdrop-filter:blur(14px)}" +
            ".search:focus-within{transform:scale(1.009);box-shadow:0 4px 16px rgba(0,0,0,.22)}" +
            ".mag{width:15px;height:15px;border:2px solid var(--search-muted);border-radius:50%;position:relative;flex:none;margin:0 15px 0 2px}.mag:after{content:'';position:absolute;width:7px;height:2px;background:var(--search-muted);right:-6px;bottom:-3px;transform:rotate(45deg);border-radius:2px}" +
            ".search input{border:0;outline:0;font-size:17px;flex:1;min-width:0;background:transparent;color:var(--search-text)}.search input::placeholder{color:var(--search-muted)}" +
            ".engines{display:flex;justify-content:center;margin-top:15px;flex-wrap:wrap}.chip{border:0;background:transparent;border-radius:18px;padding:8px 14px;margin:3px;font-size:13px;color:var(--secondary)}.chip:active{transform:scale(.9)}.chip.active{background:var(--accent);font-weight:650;color:#fff}" +
            ".shortcuts{display:grid;gap:16px 8px;margin:28px auto 0;max-width:440px}.shortcut{text-decoration:none;color:var(--fg);min-width:0;display:flex;flex-direction:column;align-items:center}" +
            ".tile{display:grid;place-items:center;width:50px;height:50px;border-radius:var(--tile-radius);background:var(--surface);font-size:19px;font-weight:650;box-shadow:0 1px 5px rgba(0,0,0,.13);transition:transform .14s;-webkit-backdrop-filter:blur(10px);backdrop-filter:blur(10px)}" +
            ".favicon{width:30px;height:30px;object-fit:contain;border-radius:7px}.fallback[hidden]{display:none}.folder-mark{position:relative;width:29px;height:21px;border:2px solid var(--secondary);border-radius:4px}.folder-mark:before{content:'';position:absolute;left:2px;top:-7px;width:12px;height:7px;border:2px solid var(--secondary);border-bottom:0;border-radius:4px 4px 0 0}" +
            ".shortcut:active .tile{transform:scale(.86)}.label{width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;margin-top:7px;font-size:12px;text-shadow:var(--label-shadow)}" +
            ".empty{grid-column:1/-1;border:1px dashed var(--border);background:transparent;color:var(--secondary);border-radius:16px;padding:16px}.corner{position:fixed;z-index:2;top:16px;left:18px;font-size:13px;font-weight:650;color:var(--secondary);text-shadow:var(--corner-shadow)}" +
            ".custom-home{position:fixed;z-index:1;inset:0;width:100%;height:100%;border:0;background:transparent}" +
            "@keyframes enter{to{opacity:1;transform:none}}@media(prefers-reduced-motion:reduce){.wrap{animation:none;opacity:1;transform:none}.search,.tile{transition:none}}@media(max-height:600px){.wrap{padding-top:5vh}.wrap.compact{padding-top:2vh}.shortcuts{margin-top:18px}}";

    private static final String PAGE_SCRIPT =
            "';var d=document,t=d.querySelector('[name=median-home-token]').content;" +
            "function m(u){prompt(t,u);return false}" +
            "function draw(){var a=d.querySelectorAll('.chip'),i,x;for(i=0;i<a.length;i++){x=a[i];x.classList.toggle('active',x.getAttribute('data-e')===e)}}" +
            "var a=d.querySelectorAll('.chip'),i;for(i=0;i<a.length;i++)a[i].onclick=function(v){v.preventDefault();e=this.getAttribute('data-e');draw();m('median://engine?name='+encodeURIComponent(e))};" +
            "a=d.querySelectorAll('a[href^=\"median:\"]');for(i=0;i<a.length;i++)a[i].onclick=function(v){v.preventDefault();m(this.getAttribute('href'))};" +
            "var f=d.getElementById('form');if(f)f.onsubmit=function(v){v.preventDefault();var q=d.getElementById('q').value.trim();if(q)m('median://search?engine='+encodeURIComponent(e)+'&q='+encodeURIComponent(q))};" +
            "var c=d.getElementById('clock');if(c){function tick(){var x=new Date();c.textContent=x.getHours()+':'+('0'+x.getMinutes()).slice(-2);try{d.getElementById('date').textContent=x.toLocaleDateString(undefined,{month:'long',day:'numeric',weekday:'short'})}catch(y){d.getElementById('date').textContent=x.toLocaleDateString()}}tick();setInterval(tick,30000)}" +
            "var b=d.querySelector('.brand'),hold;if(b){b.style.userSelect='none';b.onpointerdown=function(){hold=setTimeout(function(){m('median://folders')},520)};b.onpointerup=b.onpointercancel=b.onpointerleave=function(){clearTimeout(hold)};b.oncontextmenu=function(v){v.preventDefault();m('median://folders')}}draw()";

    static String html(String selectedEngine, List<BrowserDataStore.Bookmark> bookmarks, boolean dark, String trustToken) {
        return html(selectedEngine, bookmarks, dark, trustToken, HomePageConfig.defaults(),
                java.util.Collections.<SearchEngineStore.Engine>emptyList());
    }

    static String html(String selectedEngine, List<BrowserDataStore.Bookmark> bookmarks, boolean dark,
                       String trustToken, HomePageConfig options) {
        return html(selectedEngine, bookmarks, dark, trustToken, options,
                java.util.Collections.<SearchEngineStore.Engine>emptyList());
    }

    static String html(String selectedEngine, List<BrowserDataStore.Bookmark> bookmarks, boolean dark,
                       String trustToken, HomePageConfig options, List<SearchEngineStore.Engine> customEngines) {
        return html(selectedEngine, bookmarks, dark, trustToken, options, customEngines,
                bookmarkShortcuts(bookmarks));
    }

    static String html(String selectedEngine, List<BrowserDataStore.Bookmark> bookmarks, boolean dark,
                       String trustToken, HomePageConfig options, List<SearchEngineStore.Engine> customEngines,
                       List<Shortcut> homeShortcuts) {
        if (options == null) options = HomePageConfig.defaults();
        String engine = selectedEngineId(selectedEngine, customEngines);
        boolean wallpaper = options.hasWallpaper;
        String background = dark ? "#111315" : "#fff";
        String foreground = wallpaper ? "#fff" : (dark ? "#edf0f2" : "#202124");
        String secondary = wallpaper ? "rgba(255,255,255,.82)" : (dark ? "#aeb4ba" : "#5f6368");
        String surface = wallpaper ? "rgba(255,255,255,.20)" : (dark ? "#202327" : "#f1f3f4");
        String border = wallpaper ? "rgba(255,255,255,.28)" : (dark ? "#34383d" : "#dfe1e5");
        boolean glass = "glass".equals(options.searchStyle);
        String searchBackground = wallpaper && glass ? "rgba(18,20,24,.38)" :
                (wallpaper ? "rgba(255,255,255,.94)" : (glass ? (dark ? "rgba(42,45,50,.72)" : "rgba(255,255,255,.72)") : background));
        String searchText = wallpaper && !glass ? "#202124" : foreground;
        String searchMuted = wallpaper && !glass ? "#5f6368" : secondary;
        String tileRadius = "circle".equals(options.tileShape) ? "50%" : ("square".equals(options.tileShape) ? "8px" : "16px");
        StringBuilder shortcuts = new StringBuilder();
        int count = options.showShortcuts ? Math.min(12, homeShortcuts == null ? 0 : homeShortcuts.size()) : 0;
        if (options.showShortcuts) {
            for (int i = 0; i < count; i++) {
                Shortcut item = homeShortcuts.get(i);
                String title = item.title.trim().length() == 0 ? (item.folder ? "文件夹" : host(item.url)) : item.title.trim();
                if (title.length() > 18) title = title.substring(0, 18) + "…";
                if (item.folder) {
                    shortcuts.append("<a class='shortcut' href='median://folder?id=")
                            .append(percentEncode(item.id)).append("'><span class='tile'><span class='folder-mark'></span></span><span class='label'>")
                            .append(escape(title)).append("</span></a>");
                } else {
                    String letter = title.length() == 0 ? "•" : title.substring(0, 1).toUpperCase(java.util.Locale.getDefault());
                    String shortcutHost = faviconHost(item.url);
                    shortcuts.append("<a class='shortcut' href='median://open?url=")
                            .append(percentEncode(item.url)).append("'><span class='tile'><img class='favicon' src='/favicon?host=")
                            .append(percentEncode(shortcutHost)).append("' alt='' onerror=\"this.hidden=true;this.nextElementSibling.hidden=false\">")
                            .append("<span class='fallback' hidden>").append(escape(letter)).append("</span></span><span class='label'>")
                            .append(escape(title)).append("</span></a>");
                }
            }
            if (count == 0) shortcuts.append("<button class='empty' onclick=\"return m('median://bookmarks')\">添加常用书签</button>");
        }

        StringBuilder page = new StringBuilder(8200 + shortcuts.length());
        page.append("<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'>")
                .append("<meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; img-src 'self' data:; frame-src 'self'; connect-src 'none'; form-action 'none'; base-uri 'none'\">")
                .append("<meta name='median-home-token' content='").append(escape(trustToken)).append("'><meta name='color-scheme' content='")
                .append(dark ? "dark" : "light").append("'><style>:root{--bg:")
                .append(background).append(";--fg:").append(foreground).append(";--secondary:").append(secondary)
                .append(";--surface:").append(surface).append(";--border:").append(border)
                .append(";--search-bg:").append(searchBackground).append(";--search-text:").append(searchText)
                .append(";--search-muted:").append(searchMuted).append(";--accent:").append(options.accentColor())
                .append(";--tile-radius:").append(tileRadius).append(";--fit:").append(options.wallpaperFit)
                .append(";--scale:").append(options.wallpaperBlur > 0 ? "1.035" : "1")
                .append(";--blur:").append(options.wallpaperBlur).append("px;--dim:").append(options.wallpaperDim / 100.0f)
                .append(";--shadow:").append(wallpaper ? "0 1px 6px rgba(0,0,0,.45)" : "none")
                .append(";--label-shadow:").append(wallpaper ? "0 1px 5px rgba(0,0,0,.55)" : "none")
                .append(";--corner-shadow:").append(wallpaper ? "0 1px 6px rgba(0,0,0,.55)" : "none").append("}")
                .append(".brand{font-size:").append(options.logoFontSize).append("px;font-weight:")
                .append(options.logoFontWeight).append(";letter-spacing:").append(options.logoLetterSpacing)
                .append("px;white-space:pre-wrap;margin:0 0 10px;text-shadow:")
                .append(wallpaper ? "0 2px 14px rgba(0,0,0,.45)" : "none").append("}")
                .append(".logo{display:block;width:").append(options.logoImageWidth).append("px;height:")
                .append(options.logoImageHeight).append("px;border-radius:").append(options.logoImageRadius).append("%}")
                .append(".shortcuts{grid-template-columns:repeat(").append(options.shortcutColumns).append(",1fr)}")
                .append(BASE_CSS)
                .append(options.customCss).append("</style></head><body>");
        if (wallpaper) page.append("<div class='wall' style=\"background-image:url('/home-wallpaper?v=").append(options.wallpaperVersion).append("')\"></div><div class='shade'></div>");
        if (options.customHtmlEnabled) {
            page.append("<iframe class='custom-home' title='自定义主页' sandbox='allow-scripts allow-forms allow-popups allow-popups-to-escape-sandbox allow-top-navigation-by-user-activation' src='/home-custom?v=")
                    .append(options.customHtmlVersion).append("'></iframe></body></html>");
            return page.toString();
        }
        if (options.showCornerBrand) page.append("<div class='corner'>").append(escape(options.title)).append("</div>");
        page.append("<main class='wrap ").append(escape(options.layout)).append("'>");
        if (options.showClock) page.append("<div class='clock'><div class='time' id='clock'>--:--</div><div class='date' id='date'></div></div>");
        if ("image".equals(options.logoMode) && options.hasLogo)
            page.append("<div class='brand'><img class='logo' src='/home-logo?v=").append(options.logoVersion).append("' alt=''></div>");
        else if (!"none".equals(options.logoMode))
            page.append("<div class='brand' aria-label='").append(escape(options.title)).append("'>")
                    .append(LogoMarkup.renderPreset(options.logoStyle, options.title, options.logoCode,
                            options.logoGradientAngle)).append("</div>");
        if (options.subtitle.length() > 0) page.append("<div class='subtitle'>").append(escape(options.subtitle)).append("</div>");
        else page.append("<div class='subtitle'></div>");
        if (options.showSearch) {
            page.append("<form id='form'><div class='search'><span class='mag'></span><input id='q' autocomplete='off' enterkeyhint='search' placeholder='搜索或输入网址'></div></form>");
            if (options.showEngines) {
                page.append("<div class='engines'><button class='chip' data-e='google'>Google</button><button class='chip' data-e='baidu'>百度</button><button class='chip' data-e='bing'>Bing</button>");
                if (customEngines != null) for (SearchEngineStore.Engine item : customEngines)
                    page.append("<button class='chip' data-e='").append(escape(item.id)).append("'>")
                            .append(escape(item.name)).append("</button>");
                page.append("</div>");
            }
        }
        if (options.showShortcuts) page.append("<div class='shortcuts'>").append(shortcuts).append("</div>");
        page.append("</main><script>var e='").append(engine).append(PAGE_SCRIPT)
                .append("</script></body></html>");
        return page.toString();
    }

    private static List<Shortcut> bookmarkShortcuts(List<BrowserDataStore.Bookmark> bookmarks) {
        java.util.ArrayList<Shortcut> result = new java.util.ArrayList<Shortcut>();
        if (bookmarks != null) for (BrowserDataStore.Bookmark item : bookmarks)
            result.add(new Shortcut(false, "", item.title, item.url, item.createdAt));
        return result;
    }

    private static String host(String url) {
        try {
            String value = new URI(url).getHost();
            if (value == null) return "书签";
            return value.startsWith("www.") ? value.substring(4) : value;
        } catch (Exception ignored) { return "书签"; }
    }

    private static String faviconHost(String url) {
        try {
            String value = new URI(url).getHost();
            return value == null ? "" : value.toLowerCase(java.util.Locale.US);
        } catch (Exception ignored) { return ""; }
    }

    private static String percentEncode(String value) {
        if (value == null || value.length() == 0) return "";
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        char[] hex = "0123456789ABCDEF".toCharArray();
        StringBuilder encoded = new StringBuilder(bytes.length + 16);
        for (byte raw : bytes) {
            int b = raw & 0xff;
            if ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') ||
                    (b >= '0' && b <= '9') || b == '-' || b == '_' || b == '.' || b == '~') {
                encoded.append((char) b);
            } else {
                encoded.append('%').append(hex[b >>> 4]).append(hex[b & 15]);
            }
        }
        return encoded.toString();
    }

    private static String selectedEngineId(String selected, List<SearchEngineStore.Engine> custom) {
        if (SearchEngineStore.isBuiltIn(selected)) return selected;
        if (custom != null) for (SearchEngineStore.Engine item : custom) if (item.id.equals(selected)) return selected;
        return "google";
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private HomePage() {}
}
