package com.xinyv.median;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Bounds user-authored homepage markup and injects top-level link behavior. */
final class CustomHomeHtml {
    static final int MAX_LENGTH = 64 * 1024;
    static final String EXAMPLE =
            "<style>\n" +
            "html,body{height:100%;margin:0;font-family:system-ui,sans-serif}\n" +
            ".home{min-height:100%;display:grid;place-content:center;text-align:center;padding:24px;box-sizing:border-box}\n" +
            ".card{padding:32px 28px;border-radius:24px;background:rgba(255,255,255,.86);color:#202124;box-shadow:0 12px 38px rgba(0,0,0,.18);backdrop-filter:blur(16px)}\n" +
            "h1{font-size:46px;letter-spacing:1px;margin:0 0 10px}p{color:#5f6368;margin:0 0 22px}a{display:inline-block;padding:11px 18px;border-radius:22px;background:#1a73e8;color:#fff;text-decoration:none}\n" +
            "</style>\n" +
            "<main class=\"home\">\n" +
            "  <section class=\"card\">\n" +
            "    <h1>My Home</h1>\n" +
            "    <p id=\"clock\">这是你的自定义主页</p>\n" +
            "    <a href=\"https://www.google.com\">打开 Google</a>\n" +
            "  </section>\n" +
            "</main>\n" +
            "<script>\n" +
            "function tick(){document.getElementById('clock').textContent=new Date().toLocaleTimeString()}\n" +
            "tick();setInterval(tick,1000)\n" +
            "</script>";

    private static final String HEAD_INJECTION =
            "<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; connect-src 'none'; form-action http: https:\">" +
            "<base target=\"_top\"><style>html,body{background:transparent!important}</style>";

    private CustomHomeHtml() {}

    static String clean(String value) {
        if (value == null) return "";
        String clean = value.trim();
        return clean.getBytes(StandardCharsets.UTF_8).length <= MAX_LENGTH ? clean : "";
    }

    static boolean valid(String value) { return clean(value).length() > 0; }

    static String document(String value) {
        String html = clean(value);
        if (html.length() == 0) return "<!doctype html><html><head>" + HEAD_INJECTION + "</head><body></body></html>";
        String lower = html.toLowerCase(Locale.US);
        int head = lower.indexOf("<head");
        if (head >= 0) {
            int end = html.indexOf('>', head + 5);
            if (end >= 0) return html.substring(0, end + 1) + HEAD_INJECTION + html.substring(end + 1);
        }
        int root = lower.indexOf("<html");
        if (root >= 0) {
            int end = html.indexOf('>', root + 5);
            if (end >= 0) return html.substring(0, end + 1) + "<head>" + HEAD_INJECTION + "</head>" + html.substring(end + 1);
        }
        return "<!doctype html><html><head>" + HEAD_INJECTION + "</head><body>" + html + "</body></html>";
    }
}
