package com.xinyv.median;

import java.util.Arrays;
import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Semantic guards for network matching and bounded page-specific cosmetic filtering. */
public final class AdBlockSemanticsSelfTest {
    public static void main(String[] args) throws Exception {
        AdBlockEngine engine = new AdBlockEngine();
        int compiled = engine.updateRules(String.join("\n", Arrays.asList(
                "||ads.example^",
                "|https://start.example/path",
                "end.example/path|",
                "*wild*card^$script",
                "||tracker.example^$third-party",
                "||first.example^$~third-party",
                "||scoped.example^$domain=page.example|~safe.example",
                "||allowed.example^",
                "@@||allowed.example^",
                "||prefix.example",
                "||bad.example^$domain=invalid-domain")), null);
        expect(compiled == 10, "invalid domain constraint is rejected without counting");

        expect(packedMatches(engine, "cdn.ads.example"),
                "domain anchor and subdomain");
        expect(!packedMatches(engine, "ads.example.evil"),
                "domain boundary is preserved");
        expect(matches(engine, "|https://start.example/path", "https://start.example/path?q=1", "start.example", "news.example", 0),
                "start anchor");
        expect(!matches(engine, "|https://start.example/path", "https://other.example/https://start.example/path", "other.example", "news.example", 0),
                "start anchor does not match a later segment");
        expect(matches(engine, "end.example/path|", "https://end.example/path", "end.example", "news.example", 0), "end anchor");
        expect(!matches(engine, "end.example/path|", "https://end.example/path?x=1", "end.example", "news.example", 0),
                "end anchor");
        expect(matches(engine, "*wild*card^", "https://cdn.example/wild--card/js", "cdn.example", "news.example", 1),
                "wildcard separator and type");
        expect(!matches(engine, "*wild*card^", "https://cdn.example/wild--card.png", "cdn.example", "news.example", 2),
                "script type constraint");
        checkSeparatorAnchorSemantics();
        expect(matches(engine, "||tracker.example^", "https://tracker.example/pixel", "tracker.example", "news.example", 0),
                "third-party rule");
        expect(!matches(engine, "||tracker.example^", "https://tracker.example/pixel", "tracker.example", "shop.tracker.example", 0),
                "third-party exclusion for first party");
        expect(matches(engine, "||first.example^", "https://first.example/pixel", "first.example", "first.example", 0),
                "first-party rule");
        expect(!matches(engine, "||first.example^", "https://first.example/pixel", "first.example", "other.example", 0),
                "first-party exclusion");
        expect(matches(engine, "||scoped.example^", "https://scoped.example/ad", "scoped.example", "page.example", 0),
                "positive page domain");
        expect(!matches(engine, "||scoped.example^", "https://scoped.example/ad", "scoped.example", "safe.example", 0),
                "negative page domain");
        expect(packedMatches(engine, "allowed.example"),
                "blocked rule is present before exception");
        expect(packedMatches(engine, "allowed.example", "allowedDomains"),
                "exception domain");
        expect(matches(engine, "||prefix.example", "https://prefix.example.evil/ad", "prefix.example.evil", "news.example", 0),
                "domain anchor without caret remains a prefix rule");
        expect(!packedMatches(engine, "unlisted.example"),
                "Bloom false positive never blocks");
        expect(!hasRule(engine, "||bad.example^"),
                "invalid domain option never broadens a rule");
        checkCosmeticRules();
        checkFilmAdHosts();
        checkExplicitNavigationExceptions();
        System.out.println("AdBlockSemanticsSelfTest passed");
    }

    private static void checkExplicitNavigationExceptions() throws Exception {
        AdBlockEngine engine = new AdBlockEngine();
        engine.updateCustomRules("@@||allowed.example^\n" +
                "@@||scoped.example^$document,domain=page.example|~safe.page.example\n" +
                "@@||third.example^$document,third-party\n" +
                "@@||script.example^$script\n");
        Object snapshot = field(engine, "rules");
        Method method = AdBlockEngine.class.getDeclaredMethod("matchesExplicitException", snapshot.getClass(),
                String.class, String.class, String.class, int.class);
        method.setAccessible(true);
        long inspected = engine.getStats().inspectedRequests;
        long allowed = engine.getStats().allowedRequests;
        expect(exception(method, snapshot, "cdn.allowed.example", "page.example", 128),
                "unconditional domain exception permits navigation to a subdomain");
        expect(!exception(method, snapshot, "allowed.example.evil", "page.example", 128),
                "exception preserves domain boundaries");
        expect(exception(method, snapshot, "scoped.example", "WWW.PAGE.EXAMPLE", 128),
                "document exception uses the normalized source domain");
        expect(!exception(method, snapshot, "scoped.example", "safe.page.example", 128),
                "negative source domain retains protection");
        expect(!exception(method, snapshot, "scoped.example", "other.example", 128),
                "unrelated source is not explicitly allowed");
        expect(!exception(method, snapshot, "scoped.example", "page.example", 1),
                "document exception does not permit a script");
        expect(!exception(method, snapshot, "script.example", "page.example", 128),
                "script exception does not permit a top-level navigation");
        expect(exception(method, snapshot, "third.example", "page.example", 128) &&
                !exception(method, snapshot, "third.example", "www.third.example", 128),
                "third-party exception retains party constraints");
        expect(!exception(method, snapshot, "unlisted.example", "page.example", 128),
                "an unmatched URL is not an explicit exception");
        expect(engine.getStats().inspectedRequests == inspected && engine.getStats().allowedRequests == allowed,
                "exception query does not change request counters");
    }

    private static boolean exception(Method method, Object snapshot, String host, String pageHost, int type)
            throws Exception {
        return ((Boolean) method.invoke(null, snapshot, "https://" + host + "/next", host, pageHost, type)).booleanValue();
    }

    private static void checkFilmAdHosts() throws Exception {
        Field field = AdBlockEngine.class.getDeclaredField("BUILTIN_BLOCKED_PACKED");
        field.setAccessible(true);
        Object domains = field.get(null);
        Method matches = domains.getClass().getDeclaredMethod("matches", String.class);
        matches.setAccessible(true);
        for (String host : Arrays.asList("3vvjqio.com", "148228223c.3vvjqio.com", "lq4y0kr.com", "148223t.lq4y0kr.com"))
            expect(((Boolean) matches.invoke(domains, host)).booleanValue(), "verified rotating film-ad host is blocked: " + host);
        for (String host : Arrays.asList("www.xmfyy.com", "3vvjqio.com.example", "notlq4y0kr.com"))
            expect(!((Boolean) matches.invoke(domains, host)).booleanValue(), "film content and domain boundaries stay allowed: " + host);
    }

    private static void checkSeparatorAnchorSemantics() throws Exception {
        AdBlockEngine engine = new AdBlockEngine();
        engine.updateCustomRules("foo^bar\nfoo*^bar\nfoo^*bar\n^leading");
        expect(matches(engine, "foo^bar", "https://x.example/foo/bar", "x.example", "page.example", 0),
                "separator anchor matches exactly one separator between literals");
        expect(matches(engine, "foo^bar", "https://x.example/foo?bar", "x.example", "page.example", 0),
                "question mark is a valid ABP separator");
        expect(!matches(engine, "foo^bar", "https://x.example/foobar", "x.example", "page.example", 0),
                "separator anchor rejects adjacent alphanumeric text");
        expect(!matches(engine, "foo^bar", "https://x.example/foo//bar", "x.example", "page.example", 0),
                "separator anchor does not silently skip extra URL characters");
        expect(matches(engine, "foo*^bar", "https://x.example/foo/any/path/bar", "x.example", "page.example", 0),
                "wildcard before separator can consume text up to the separator");
        expect(matches(engine, "foo^*bar", "https://x.example/foo/any/path/bar", "x.example", "page.example", 0),
                "wildcard after separator can consume arbitrary text");
        expect(matches(engine, "^leading", "https://x.example/leading", "x.example", "page.example", 0),
                "leading separator anchor matches after a URL separator");
        expect(matches(engine, "^leading", "leading", "", "page.example", 0),
                "leading separator anchor also matches at URL start");
    }

    private static void checkCosmeticRules() {
        AdBlockEngine engine = new AdBlockEngine();
        expect(engine.requiresEarlyCosmetic("film.example"),
                "built-in hiding is available before subscriptions compile");
        expect(engine.cosmeticCssForHost("film.example").contains(".adsbygoogle{display:none!important;}"),
                "first document receives built-in hiding");
        expect(!engine.requiresEarlyCosmetic(null) && !engine.requiresEarlyCosmetic(""),
                "internal pages do not request cosmetic injection");
        expect(!engine.requiresEarlyCosmetic("www.youtube.com") &&
                engine.cosmeticCssForHost("www.youtube.com").isEmpty(),
                "early gate respects video-site cosmetic exclusions");

        String custom = "film.example###manual-target\n##.custom-global\nfilm.example##.excepted";
        StringBuilder subscription = new StringBuilder();
        subscription.append("film.example##.subscription-site\n")
                .append("film.example###manual-target\n")
                .append("film.example#@#.excepted\n");
        for (int i = 0; i < 950; i++) subscription.append("##.generic-").append(i).append('\n');
        engine.updateRules(custom, Arrays.asList(subscription.toString()));
        String css = engine.cosmeticCssForHost("watch.film.example");
        expect(css.startsWith("#manual-target{display:none!important;}\n.custom-global{display:none!important;}\n"),
                "manual and custom rules precede a saturated generic subscription");
        expect(css.contains(".subscription-site{display:none!important;}"),
                "site subscription rules retain priority over generic rules");
        expect(!css.contains(".excepted{"), "cosmetic exceptions still suppress explicit rules");
        expect(occurrences(css, "#manual-target{") == 1, "duplicate selectors consume one slot");
        expect(occurrences(css, "{display:none!important;}") == 900,
                "prioritizing site rules preserves the per-page selector budget");
        expect(!engine.cosmeticCssForHost("film.example.evil").contains("#manual-target{"),
                "manual rules stay within their domain boundary");

        subscription.setLength(0);
        for (int i = 0; i < 950; i++)
            subscription.append("watch.film.example##.specific-").append(i).append('\n');
        engine.updateRules(custom, Arrays.asList(subscription.toString()));
        css = engine.cosmeticCssForHost("watch.film.example");
        expect(css.startsWith("#manual-target{display:none!important;}\n"),
                "parent-domain manual rule survives saturated subdomain subscriptions");
        expect(css.contains(".custom-global{display:none!important;}"),
                "custom generic rule survives saturated site subscriptions");
        expect(occurrences(css, "{display:none!important;}") == 900,
                "site-only subscriptions also stay bounded");

        engine.updateRules("film.example###new-target", Arrays.asList("film.example##.new-subscription"));
        css = engine.cosmeticCssForHost("watch.film.example");
        expect(css.contains("#new-target{") && css.contains(".new-subscription{"),
                "hot update returns current custom and subscription selectors");
        expect(!css.contains("#manual-target{") && !css.contains(".specific-0{"),
                "hot update invalidates previously cached cosmetic CSS");
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        for (int start = 0; (start = value.indexOf(needle, start)) >= 0; start += needle.length()) count++;
        return count;
    }

    private static boolean matches(AdBlockEngine engine, String pattern, String url, String requestHost,
                                   String pageHost, int type) throws Exception {
        Object rule = rule(engine, pattern);
        Method method = AdBlockEngine.class.getDeclaredMethod("matchesRule", rule.getClass(),
                String.class, String.class, String.class, int.class);
        method.setAccessible(true);
        return ((Boolean) method.invoke(null, rule, url, requestHost, pageHost, Integer.valueOf(type))).booleanValue();
    }

    private static boolean packedMatches(AdBlockEngine engine, String host) throws Exception {
        return packedMatches(engine, host, "blockedDomains");
    }

    private static boolean packedMatches(AdBlockEngine engine, String host, String fieldName) throws Exception {
        Object set = field(field(engine, "rules"), fieldName);
        Method method = set.getClass().getDeclaredMethod("matches", String.class);
        method.setAccessible(true);
        return ((Boolean) method.invoke(set, host)).booleanValue();
    }

    private static boolean hasRule(AdBlockEngine engine, String pattern) throws Exception {
        try { rule(engine, pattern); return true; }
        catch (AssertionError missing) { return false; }
    }

    private static Object rule(AdBlockEngine engine, String pattern) throws Exception {
        Object set = field(engine, "rules");
        Object index = field(set, "blockedIndex");
        Object[] values = (Object[]) field(index, "candidates");
        for (Object value : values) if (pattern.equals(field(value, "pattern"))) return value;
        for (Object value : (List<?>) field(set, "genericBlocked"))
            if (pattern.equals(field(value, "pattern"))) return value;
        Object allowed = field(set, "allowedIndex");
        for (Object value : (Object[]) field(allowed, "candidates"))
            if (pattern.equals(field(value, "pattern"))) return value;
        for (Object value : (List<?>) field(set, "genericAllowed"))
            if (pattern.equals(field(value, "pattern"))) return value;
        throw new AssertionError("Missing rule: " + pattern);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void expect(boolean value, String label) {
        if (!value) throw new AssertionError("Expected: " + label);
    }
}
