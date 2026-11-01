package com.xinyv.median;

import android.net.Uri;

import java.util.Arrays;

/** Calls the production engine, including its hosts-only shortcut and immutable domain caches. */
public final class AdBlockTrackingEndpointSelfTest {
    private static final String ENDPOINT = "https://ext.baidu.com/rest/id-mapping/cuid";
    private static int checks;

    public static void main(String[] args) {
        checkRuleModes();
        checkPixelModes();
        checkGoogleTagModes();
        checkBaiduLogModes();
        checkExceptions();
        checkLongUrlExceptions();
        checkUpdatesAndCounters();
        System.out.println("AdBlockTrackingEndpointSelfTest passed (" + checks + " checks)");
    }

    private static void checkRuleModes() {
        String[] modes = {
                null, // Brand-new engine before subscriptions finish loading.
                "||unrelated.example^", // Packed domains only.
                "||unrelated.example/advert.js", // Full matcher without exceptions.
                "||unrelated.example/advert.js\n@@||safe.example/path", // With exceptions.
                "||unrelated.example/advert.js$script,domain=page.example,third-party"
        };
        for (int mode = 0; mode < modes.length; mode++) {
            AdBlockEngine engine = new AdBlockEngine();
            if (modes[mode] != null) engine.updateCustomRules(modes[mode]);
            String label = "mode " + mode + ": ";
            expect(!blocked(engine, "https://ext.baidu.com/ordinary", false),
                    label + "ordinary path primes allowed domain cache");
            for (String request : Arrays.asList(
                    ENDPOINT,
                    ENDPOINT + "?callback=__jsonp_callback__0",
                    ENDPOINT + "?callback=second&other=value",
                    "http://ext.baidu.com/rest/id-mapping/cuid?callback=plain",
                    "https://EXT.BAIDU.COM/rest/id-mapping/cuid",
                    "https://ext.baidu.com:443/rest/id-mapping/cuid",
                    "https://ext.baidu.com:8443/rest/id-mapping/cuid",
                    "http://ext.baidu.com:8080/rest/id-mapping/cuid")) {
                expect(blocked(engine, request, false), label + "exact tracking endpoint " + request);
            }
            for (String request : Arrays.asList(
                    "https://ext.baidu.com/ordinary",
                    "https://ext.baidu.com/rest/id-mapping/cuid2",
                    "https://ext.baidu.com/rest/id-mapping/cuid/",
                    "https://ext.baidu.com/rest/id-mapping/CUID",
                    "https://ext.baidu.com/rest/id-mapping/other",
                    "https://ext.baidu.com/rest/id-mapping%2Fcuid",
                    "https://ext.baidu.com/rest/id-mapping/%63uid",
                    "https://ext.baidu.com/rest/id-mapping/./cuid",
                    "https://sub.ext.baidu.com/rest/id-mapping/cuid",
                    "https://ext.baidu.com.evil.example/rest/id-mapping/cuid",
                    "https://other.example/rest/id-mapping/cuid",
                    "https://other.example/?url=" + ENDPOINT,
                    "file://ext.baidu.com/rest/id-mapping/cuid")) {
                expect(!blocked(engine, request, false), label + "unrelated request stays allowed " + request);
            }
            expect(!blocked(engine, ENDPOINT, true), label + "main document stays allowed");
            expect(blocked(engine, "https://doubleclick.net/advert", false),
                    label + "built-in domain rules still apply");
            expect(!engine.isExplicitlyAllowed(Uri.parse(ENDPOINT), "www.baidu.com", "", false),
                    label + "unlisted request is not an explicit allow");
            expect(engine.requiresRequestHeaders() == (mode == 4),
                    label + "endpoint does not force request header IPC");
        }
    }

    private static void checkPixelModes() {
        for (String rules : Arrays.asList("", "||unrelated.example^",
                "||unrelated.example/pattern", "||unrelated.example/pattern\n@@||safe.example/path")) {
            AdBlockEngine engine = new AdBlockEngine();
            engine.updateCustomRules(rules);
            for (String name : Arrays.asList("mwb", "webb")) {
                String path = "/5b1ZeDe5KgQFm2e88IuM_a/" + name + ".gif";
                String endpoint = "https://sp1.baidu.com" + path;
                expect(!blocked(engine, "https://sp1.baidu.com/ordinary", false),
                        "ordinary pixel-host path primes allowed cache");
                expect(blocked(engine, endpoint, false), "exact " + name + " pixel blocks");
                expect(blocked(engine, endpoint + "?type=disp&data=one", false), "pixel query is arbitrary");
                expect(blocked(engine, "http://sp1.baidu.com:8080" + path, false), "HTTP pixel on explicit port blocks");
                expect(blocked(engine, "https://SP1.BAIDU.COM:443" + path, false), "pixel host is case insensitive");
                expect(!blocked(engine, endpoint, true), "pixel path main document stays allowed");
                for (String request : Arrays.asList(
                        endpoint + "/", endpoint + "2", endpoint.replace(".gif", ".GIF"),
                        endpoint.replace("5b1ZeDe5KgQFm2e88IuM_a", "5b1zede5kgqfm2e88ium_a"),
                        endpoint.replace("/" + name + ".gif", "/%" + (name.equals("mwb") ? "6dwb" : "77ebb") + ".gif"),
                        endpoint.replace("sp1.baidu.com", "sub.sp1.baidu.com"),
                        endpoint.replace("sp1.baidu.com", "sp1.baidu.com.evil.example"),
                        endpoint.replace("sp1.baidu.com", "ext.baidu.com"),
                        "https://sp1.baidu.com/" + name + ".gif",
                        "https://sp1.baidu.com/other/" + name + ".gif",
                        "https://sp1.baidu.com/ordinary")) {
                    expect(!blocked(engine, request, false), "similar pixel path or host stays allowed " + request);
                }
                expect(!engine.requiresRequestHeaders(), "pixel rule does not force header IPC");
            }
        }

        for (String name : Arrays.asList("mwb", "webb")) {
            String path = "/5b1ZeDe5KgQFm2e88IuM_a/" + name + ".gif";
            String endpoint = "https://sp1.baidu.com" + path;
            AdBlockEngine engine = new AdBlockEngine();
            engine.updateCustomRules("@@||sp1.baidu.com^");
            expect(!blocked(engine, endpoint, false), "whole pixel-host exception takes priority");
            engine.updateCustomRules("@@||sp1.baidu.com" + path + "$image,domain=baidu.com|~safe.baidu.com");
            expect(!request(engine, endpoint, "www.baidu.com", "image/gif", false),
                    "scoped image exception takes priority");
            expect(request(engine, endpoint, "safe.baidu.com", "image/gif", false),
                    "excluded page retains pixel protection");
            expect(request(engine, endpoint, "other.example", "image/gif", false),
                    "unrelated page cannot use scoped pixel exception");
            engine.updateCustomRules("@@||sp1.baidu.com" + path + "$script");
            expect(blocked(engine, endpoint, false), "script exception cannot exempt GIF pixel");
            engine.updateCustomRules("");
            expect(blocked(engine, endpoint, false), "empty update retains pixel protection");
        }
    }

    private static void checkGoogleTagModes() {
        String endpoint = "https://www.googletagmanager.com/gtag/js";
        for (String rules : Arrays.asList(null, "", "||unrelated.example^",
                "||unrelated.example/pattern", "||unrelated.example/pattern\n@@||safe.example/path")) {
            AdBlockEngine engine = new AdBlockEngine();
            if (rules != null) engine.updateCustomRules(rules);
            expect(!blocked(engine, "https://www.googletagmanager.com/gtm.js", false),
                    "container script primes domain allow cache and remains allowed");
            for (String request : Arrays.asList(endpoint, endpoint + "?id=G-TEST&l=sampleLayer",
                    "http://www.googletagmanager.com/gtag/js",
                    "https://www.googletagmanager.com:8443/gtag/js",
                    "https://WWW.GOOGLETAGMANAGER.COM/gtag/js")) {
                expect(blocked(engine, request, false), "exact Google analytics tag endpoint blocks");
            }
            for (String request : Arrays.asList("https://www.googletagmanager.com/gtm.js",
                    "https://www.googletagmanager.com/ordinary", endpoint + "/", endpoint + "2",
                    "https://www.googletagmanager.com/GTAG/js", "https://www.googletagmanager.com/gtag%2Fjs",
                    "https://www.googletagmanager.com/gtag/%6as",
                    "https://googletagmanager.com/gtag/js", "https://sub.googletagmanager.com/gtag/js",
                    "https://www.googletagmanager.com.evil.example/gtag/js", "https://other.example/gtag/js")) {
                expect(!blocked(engine, request, false), "container and unrelated Google tag paths remain allowed");
            }
            expect(!blocked(engine, endpoint, true), "Google tag main document remains allowed");
            expect(!engine.requiresRequestHeaders(), "Google tag rule does not force request header IPC");
        }

        AdBlockEngine engine = new AdBlockEngine();
        engine.updateCustomRules("@@||www.googletagmanager.com^");
        expect(!blocked(engine, endpoint, false), "whole-host Google tag exception takes priority");
        engine.updateCustomRules("@@||www.googletagmanager.com/gtag/js");
        expect(!blocked(engine, endpoint + "?id=G-TEST", false), "Google tag URL exception takes priority");
        engine.updateCustomRules("@@||www.googletagmanager.com/gtag/js$script,domain=page.example,third-party");
        expect(!request(engine, endpoint, "page.example", "application/javascript", false),
                "Google tag scoped script exception takes priority");
        expect(request(engine, endpoint, "other.example", "application/javascript", false),
                "Google tag scoped exception does not exempt other page");
        expect(request(engine, endpoint, "page.example", "image/png", false),
                "Google tag scoped exception retains script type restriction");
        engine.updateCustomRules("");
        expect(blocked(engine, endpoint, false), "empty update retains built-in Google tag rule");
    }

    private static void checkExceptions() {
        AdBlockEngine engine = new AdBlockEngine();
        engine.updateCustomRules("@@||ext.baidu.com^");
        expect(!blocked(engine, ENDPOINT, false), "whole-host explicit allow overrides built-in endpoint");
        expect(!engine.requiresRequestHeaders(), "whole-host allow retains hosts-only path");

        engine.updateCustomRules("@@||ext.baidu.com/rest/id-mapping/cuid");
        expect(!blocked(engine, ENDPOINT + "?callback=one", false), "URL exception overrides endpoint");
        expect(engine.isExplicitlyAllowed(Uri.parse(ENDPOINT), "www.baidu.com", "", false),
                "exception query agrees with request decision");

        engine.updateCustomRules("@@||ext.baidu.com/rest/id-mapping/cuid$script,domain=baidu.com|~safe.baidu.com");
        expect(!request(engine, ENDPOINT, "www.baidu.com", "application/javascript", false),
                "page-domain and script exception applies");
        expect(request(engine, ENDPOINT, "safe.baidu.com", "application/javascript", false),
                "excluded page domain keeps endpoint blocked");
        expect(request(engine, ENDPOINT, "other.example", "application/javascript", false),
                "unrelated page domain cannot use scoped exception");
        expect(request(engine, ENDPOINT, "www.baidu.com", "image/png", false),
                "resource type mismatch cannot use script exception");
        expect(request(engine, ENDPOINT, "www.baidu.com", "", false),
                "unknown resource type cannot use script exception");
        expect(engine.requiresRequestHeaders(), "typed exception requests headers");

        engine.updateCustomRules("@@||ext.baidu.com/rest/id-mapping/cuid$third-party");
        expect(request(engine, ENDPOINT, "www.baidu.com", "", false),
                "third-party allow does not exempt same-site request");
        expect(!request(engine, ENDPOINT, "other.example", "", false),
                "third-party allow exempts third-party request");

        engine.updateCustomRules("@@||ext.baidu.com/rest/id-mapping/other\n@@||ext.baidu.com.evil.example^");
        expect(blocked(engine, ENDPOINT, false), "unmatched exceptions do not exempt endpoint");

        engine.updateCustomRules("@@||ext.baidu.com/rest/id-mapping/cuid$document");
        expect(blocked(engine, ENDPOINT, false), "document-only exception does not exempt subresource");
        expect(!blocked(engine, ENDPOINT, true), "main document is outside built-in rule");
    }

    private static void checkBaiduLogModes() {
        String endpoint = "https://m.baidu.com/tc?tcreq4log=sample";
        for (String rules : Arrays.asList(null, "", "||unrelated.example^",
                "||unrelated.example/pattern", "||unrelated.example/pattern\n@@||safe.example/path")) {
            AdBlockEngine engine = new AdBlockEngine();
            if (rules != null) engine.updateCustomRules(rules);
            expect(!blocked(engine, "https://m.baidu.com/tc?url=ordinary", false),
                    "ordinary /tc primes domain allow cache without blocking outbound resources");
            for (String query : Arrays.asList("tcreq4log=sample", "tcreq4log=", "tcreq4log",
                    "first=one&tcreq4log=sample&last=two", "first=one&tcreq4log",
                    "tcreq4log&last=two", "tcreq4log=one&tcreq4log=two",
                    "other=tcreq4log&tcreq4log=sample", "tcreq4log=%26other%3Dvalue")) {
                String url = "https://m.baidu.com/tc?" + query;
                expect(blocked(engine, url, false), "literal Baidu log marker blocks subresource");
                expect(!blocked(engine, url, true), "Baidu log marker never blocks main-frame navigation");
            }
            for (String url : Arrays.asList(
                    "http://m.baidu.com/tc?tcreq4log=sample", "https://M.BAIDU.COM:8443/tc?tcreq4log=sample"))
                expect(blocked(engine, url, false), "Baidu log uses existing HTTP(S) and host semantics");
            for (String url : Arrays.asList(
                    "https://m.baidu.com/tc", "https://m.baidu.com/tc?", "https://m.baidu.com/tc?other=value",
                    "https://m.baidu.com/tc?tcreq4log2=sample", "https://m.baidu.com/tc?xtcreq4log=sample",
                    "https://m.baidu.com/tc?TCREQ4LOG=sample", "https://m.baidu.com/tc?%74creq4log=sample",
                    "https://m.baidu.com/tc?tcreq4lo%67=sample", "https://m.baidu.com/tc?other=tcreq4log",
                    "https://m.baidu.com/tc?other=one%26tcreq4log%3Dsample",
                    "https://m.baidu.com/tc?other=one;tcreq4log=sample",
                    "https://m.baidu.com/tc#tcreq4log=sample", "https://m.baidu.com/TC?tcreq4log=sample",
                    "https://m.baidu.com/tc/?tcreq4log=sample", "https://m.baidu.com/tc2?tcreq4log=sample",
                    "https://m.baidu.com/%74c?tcreq4log=sample", "https://sub.m.baidu.com/tc?tcreq4log=sample",
                    "https://other.example/tc?tcreq4log=sample", "file://m.baidu.com/tc?tcreq4log=sample"))
                expect(!blocked(engine, url, false), "similar Baidu log key, path, host or scheme stays allowed");
            expect(!engine.requiresRequestHeaders(), "Baidu log marker does not force header IPC");
        }
        AdBlockEngine engine = new AdBlockEngine();
        engine.updateCustomRules("@@||m.baidu.com^");
        expect(!blocked(engine, endpoint, false), "whole-host exception wins over Baidu log marker");
        engine.updateCustomRules("@@||m.baidu.com/tc?tcreq4log=");
        expect(!blocked(engine, endpoint, false), "exact URL exception wins over Baidu log marker");
        engine.updateCustomRules("@@||m.baidu.com/tc$image,domain=baidu.com|~safe.baidu.com");
        expect(!request(engine, endpoint, "www.baidu.com", "image/png", false), "scoped image exception wins");
        expect(request(engine, endpoint, "safe.baidu.com", "image/png", false), "excluded page retains logging block");
        expect(request(engine, endpoint, "other.example", "image/png", false), "other page cannot use scoped exception");
        engine.updateCustomRules("@@||m.baidu.com/tc$script");
        expect(request(engine, endpoint, "www.baidu.com", "image/png", false), "script exception cannot exempt image log");
        engine.updateCustomRules("");
        expect(blocked(engine, endpoint, false), "empty update retains built-in Baidu log rule");
        expect(!blocked(engine, "https://m.baidu.com/tc?other=value", false),
                "logging path never contaminates domain decision cache");
    }

    private static void checkLongUrlExceptions() {
        String longData = "x".repeat(16400);
        for (String endpoint : Arrays.asList(ENDPOINT,
                "https://sp1.baidu.com/5b1ZeDe5KgQFm2e88IuM_a/mwb.gif",
                "https://sp1.baidu.com/5b1ZeDe5KgQFm2e88IuM_a/webb.gif",
                "https://www.googletagmanager.com/gtag/js",
                "https://m.baidu.com/tc?tcreq4log=sample")) {
            String longUrl = endpoint + (endpoint.indexOf('?') < 0 ? "?data=" : "&data=") + longData;
            AdBlockEngine engine = new AdBlockEngine();
            expect(blocked(engine, longUrl, false), "long query cannot bypass cold built-in rule");
            engine.updateCustomRules("||unrelated.example/path");
            expect(blocked(engine, longUrl, false), "long query cannot bypass mixed blocking rules");
            engine.updateCustomRules("@@|" + endpoint + "$domain=baidu.com");
            expect(!blocked(engine, longUrl, false),
                    "length-limited pattern matcher retains previous domain-only fallback with exceptions");
            expect(!blocked(engine, endpoint, false), "scoped URL exception also permits normal query length");
            String host = Uri.parse(endpoint).getHost();
            engine.updateCustomRules("@@||" + host + "^");
            expect(!blocked(engine, longUrl, false), "domain allow also permits long query");
            engine.updateCustomRules("||" + host + "^\n@@|" + endpoint);
            expect(blocked(engine, longUrl, false),
                    "length-limited matcher keeps previous explicit domain block decision");
        }
        AdBlockEngine engine = new AdBlockEngine();
        expect(blocked(engine, "https://m.baidu.com/tc?data=" + longData + "&tcreq4log=sample", false),
                "literal logging marker also matches after a long unrelated value");
        expect(!blocked(engine, "https://m.baidu.com/tc?data=" + longData + "&%74creq4log=sample", false),
                "encoded logging marker remains outside the conservative rule even after a long value");
    }

    private static void checkUpdatesAndCounters() {
        AdBlockEngine engine = new AdBlockEngine();
        expect(blocked(engine, ENDPOINT, false), "cold engine protects before rule update");
        engine.updateRules("", Arrays.asList("||unrelated.example^"));
        expect(blocked(engine, ENDPOINT, false), "subscription update retains built-in endpoint");
        engine.trimMemory();
        expect(blocked(engine, ENDPOINT, false), "memory trim retains endpoint");
        engine.updateRules("", null);
        expect(blocked(engine, ENDPOINT, false), "empty update retains endpoint");

        AdBlockEngine counters = new AdBlockEngine();
        expect(blocked(counters, ENDPOINT, false), "counter fixture blocks endpoint");
        expect(!blocked(counters, "https://ext.baidu.com/ordinary", false), "counter fixture permits ordinary path");
        counters.updateCustomRules("@@||ext.baidu.com/rest/id-mapping/cuid");
        expect(!blocked(counters, ENDPOINT, false), "counter fixture applies exception");
        counters.isExplicitlyAllowed(Uri.parse(ENDPOINT), "www.baidu.com", "", false);
        AdBlockEngine.Stats stats = counters.getStats();
        expect(stats.inspectedRequests == 3 && stats.blockedRequests == 1 && stats.allowedRequests == 1,
                "endpoint and exception increment counters once; explicit query changes none");
        expect(!counters.shouldBlock((Uri) null, "www.baidu.com", "", false), "null request is allowed");
    }

    private static boolean blocked(AdBlockEngine engine, String url, boolean mainFrame) {
        return request(engine, url, "www.baidu.com", "", mainFrame);
    }

    private static boolean request(AdBlockEngine engine, String url, String page,
                                   String accept, boolean mainFrame) {
        return engine.shouldBlock(Uri.parse(url), page, accept, mainFrame);
    }

    private static void expect(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
