package com.xinyv.median;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Regression guards for exact, bounded bucket suppression in the pure matcher. */
public final class AdBlockBucketSelfTest {
    private static final int SCRIPT = 1;
    private static final int IMAGE = 2;
    private static final int DOCUMENT = 128;
    private static final Method SITE_KEY = method(AdBlockEngine.class, "siteKeyNormalized", String.class);
    private static final Method BEST_KEYWORD = method(AdBlockEngine.class, "bestKeyword", String.class);
    private static final Method KEYWORD_HASH = method(AdBlockEngine.class, "keywordHash", String.class, int.class);

    public static void main(String[] args) throws Exception {
        checkHighIdAndRepeatedKeywords();
        checkAllowedAndBlockedIsolation();
        checkConditionedCandidates();
        checkScratchCollisionsAndSaturation();
        checkSaturatedMatcher();
        checkConcurrentRequests();
        System.out.println("AdBlockBucketSelfTest passed");
    }

    private static void checkHighIdAndRepeatedKeywords() throws Exception {
        StringBuilder source = new StringBuilder(720000);
        for (int i = 0; i < 40000; i++) {
            String value = Integer.toString(i, 36);
            source.append("*x");
            for (int padding = value.length(); padding < 5; padding++) source.append('0');
            source.append(value).append("*$script\n");
        }
        String pattern = "*repeatedkeyword*/landing";
        source.append(pattern).append("$script,third-party,domain=page.example|~safe.page.example");
        AdBlockEngine engine = new AdBlockEngine();
        expect(engine.updateRules(source.toString(), Collections.<String>emptyList()) == 40001,
                "all indexed fillers and the target compile");
        IndexView blocked = new IndexView(engine, false);
        Object target = blocked.rule(pattern);
        expect(((Integer) field(target, "id")).intValue() >= 40000,
                "repeated-keyword target has a rule id above the former scratch limit");

        String repeated = repeatedPath("repeatedkeyword", 96);
        String miss = "https://cdn.example/" + repeated + "missing";
        expectIndexed(blocked, miss, "cdn.example", "page.example", SCRIPT, false,
                "repeating a high-id bucket does not invent a pattern match");
        Object scratch = currentScratch();
        int bucket = blocked.bucket(pattern);
        expect(((Boolean) method(scratch.getClass(), "alreadySeenBucket", int.class)
                .invoke(scratch, bucket)).booleanValue(),
                "high-id rule's bucket is remembered after the complete miss");

        String hit = "https://cdn.example/" + repeated + "landing";
        expectIndexed(blocked, hit, "cdn.example", "page.example", SCRIPT, true,
                "a later request can match a previously seen bucket");
        expectIndexed(blocked, hit, "cdn.example", "page.example", IMAGE, false,
                "high-id bucket retains the resource type constraint");
        expectIndexed(blocked, hit, "cdn.example", "safe.page.example", SCRIPT, false,
                "high-id bucket retains the negative page domain");
        expectIndexed(blocked, hit, "cdn.example", "page.example.evil", SCRIPT, false,
                "high-id bucket retains the positive domain boundary");
        expectIndexed(blocked, hit, "cdn.example", "shop.cdn.example", SCRIPT, false,
                "high-id bucket retains the third-party boundary");
        expectIndexed(blocked, hit, "cdn.example", "news.page.example", SCRIPT, true,
                "high-id bucket accepts a subdomain of the included page");
    }

    private static void checkAllowedAndBlockedIsolation() throws Exception {
        AdBlockEngine engine = new AdBlockEngine();
        String pattern = "*sharedkeyword*";
        engine.updateRules(pattern + "$script\n" +
                "@@" + pattern + "$image\n" +
                "@@" + pattern + "$script,domain=safe.page.example", Collections.<String>emptyList());
        IndexView allowed = new IndexView(engine, true);
        IndexView blocked = new IndexView(engine, false);
        expect(allowed.bucket(pattern) == blocked.bucket(pattern),
                "independent allowed and blocked indexes deliberately use the same bucket slot");
        String url = "https://cdn.example/" + repeatedPath("sharedkeyword", 24) + "asset";

        // A failed allowed bucket is still recorded, so the blocked call must start a
        // separate generation even when its numeric slot is identical.
        expectIndexed(allowed, url, "cdn.example", "page.example", SCRIPT, false,
                "unscoped script has no exception");
        expectIndexed(blocked, url, "cdn.example", "page.example", SCRIPT, true,
                "a failed exception cannot suppress the blocked index's same slot");
        expectIndexed(blocked, url, "cdn.example", "page.example", IMAGE, false,
                "blocked script bucket does not match an image");
        expectIndexed(allowed, url, "cdn.example", "page.example", IMAGE, true,
                "a failed blocked bucket cannot suppress the allowed index's same slot");

        expectIndexed(blocked, url, "cdn.example", "safe.page.example", SCRIPT, true,
                "scoped allowed request also matches the underlying blocked rule");
        expectIndexed(allowed, url, "cdn.example", "safe.page.example", SCRIPT, true,
                "the scoped exception survives an already matched blocked bucket");
        Object snapshot = field(engine, "rules");
        Method explicit = method(AdBlockEngine.class, "matchesExplicitException", snapshot.getClass(),
                String.class, String.class, String.class, int.class);
        expect(((Boolean) explicit.invoke(null, snapshot, url, "cdn.example", "SAFE.PAGE.EXAMPLE", SCRIPT))
                .booleanValue(), "the production exception query preserves allowed priority and normalization");
        expect(!((Boolean) explicit.invoke(null, snapshot, url, "cdn.example", "safe.page.example.evil", SCRIPT))
                .booleanValue(), "an exception cannot cross its source-domain boundary");
    }

    private static AdBlockEngine conditionedEngine() {
        AdBlockEngine engine = new AdBlockEngine();
        engine.updateRules("*conditionkeyword*/typed$script\n" +
                "*conditionkeyword*/scoped$document,domain=page.example|~safe.page.example\n" +
                "*conditionkeyword*/third$third-party\n" +
                "*conditionkeyword*/first$~third-party\n" +
                "*conditionkeyword*/excluded$~image\n" +
                "*conditionkeyword*/tail^$image\n" +
                "@@*conditionkeyword*/permit$document,domain=page.example,third-party",
                Collections.<String>emptyList());
        return engine;
    }

    private static void checkConditionedCandidates() throws Exception {
        AdBlockEngine engine = conditionedEngine();
        IndexView blocked = new IndexView(engine, false);
        int bucket = blocked.bucket("*conditionkeyword*/typed");
        for (String suffix : new String[] { "scoped", "third", "first", "excluded", "tail^" })
            expect(blocked.bucket("*conditionkeyword*/" + suffix) == bucket,
                    "conditioned candidates share a bucket: " + suffix);
        String base = "https://cdn.example/" + repeatedPath("conditionkeyword", 12);
        expectIndexed(blocked, base + "typed", "cdn.example", "page.example", SCRIPT, true,
                "script candidate matches after repeated keyword windows");
        expectIndexed(blocked, base + "typed", "cdn.example", "page.example", IMAGE, false,
                "script candidate rejects an image");
        expectIndexed(blocked, base + "scoped", "cdn.example", "www.page.example", DOCUMENT, true,
                "document candidate retains its included source subdomain");
        expectIndexed(blocked, base + "scoped", "cdn.example", "safe.page.example", DOCUMENT, false,
                "negative source subdomain wins over an included parent");
        expectIndexed(blocked, base + "scoped", "cdn.example", "page.example.evil", DOCUMENT, false,
                "source-domain inclusion stays exact");
        expectIndexed(blocked, base + "third", "cdn.example", "page.example", IMAGE, true,
                "third-party candidate still matches");
        expectIndexed(blocked, base + "third", "cdn.example", "shop.cdn.example", IMAGE, false,
                "third-party candidate rejects same-site source");
        expectIndexed(blocked, base + "third", "cdn.example", "", IMAGE, true,
                "missing source remains third party in both matcher overloads");
        expectIndexed(blocked, base + "first", "cdn.example", "shop.cdn.example", IMAGE, true,
                "first-party candidate survives earlier failed candidates in the same bucket");
        expectIndexed(blocked, base + "first", "cdn.example", "page.example", IMAGE, false,
                "first-party candidate rejects a third-party source");
        expectIndexed(blocked, base + "excluded", "cdn.example", "page.example", SCRIPT, true,
                "excluded-type rule still accepts other types");
        expectIndexed(blocked, base + "excluded", "cdn.example", "page.example", IMAGE, false,
                "excluded resource type stays excluded");
        expectIndexed(blocked, base + "tail/image", "cdn.example", "page.example", IMAGE, true,
                "last candidate can match after every earlier candidate fails");
        expectIndexed(blocked, base + "tailored", "cdn.example", "page.example", IMAGE, false,
                "last candidate's separator boundary remains exact");

        IndexView allowed = new IndexView(engine, true);
        expectIndexed(allowed, base + "permit", "cdn.example", "news.page.example", DOCUMENT, true,
                "allowed bucket combines document, source domain, and party constraints");
        expectIndexed(allowed, base + "permit", "cdn.example", "news.page.example", SCRIPT, false,
                "document exception does not broaden to scripts");
        expectIndexed(allowed, base + "permit", "cdn.example", "shop.cdn.example", DOCUMENT, false,
                "third-party exception does not broaden to same-site requests");
    }

    private static void checkScratchCollisionsAndSaturation() throws Exception {
        Class<?> type = Class.forName("com.xinyv.median.AdBlockEngine$RuleMatchScratch");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object scratch = constructor.newInstance();
        Method begin = method(type, "begin");
        Method seen = method(type, "alreadySeenBucket", int.class);
        Method slot = method(type, "bucketSlot", int.class, int.class);
        begin.invoke(scratch);
        int mask = ((int[]) field(scratch, "marks")).length - 1;
        int first = 0;
        int wanted = ((Integer) slot.invoke(null, first, mask)).intValue();
        int collision = -1;
        for (int value = 1; value < 100000; value++) {
            if (((Integer) slot.invoke(null, value, mask)).intValue() == wanted) {
                collision = value;
                break;
            }
        }
        expect(collision >= 0, "a deliberate scratch-slot collision exists");
        expect(!seen(seen, scratch, first) && !seen(seen, scratch, collision),
                "different bucket identities sharing a hash slot are both checked");
        expect(seen(seen, scratch, first) && seen(seen, scratch, collision),
                "both colliding buckets are independently remembered");
        expect(!seen(seen, scratch, -1) && !seen(seen, scratch, -1),
                "invalid slots never become seen buckets");
        begin.invoke(scratch);
        expect(!seen(seen, scratch, first) && !seen(seen, scratch, collision),
                "a new request forgets both colliding buckets");

        begin.invoke(scratch);
        int limit = ((Integer) staticField(AdBlockEngine.class, "RULE_SCRATCH_LIMIT")).intValue();
        for (int value = 0; value < limit; value++)
            expect(!seen(seen, scratch, value), "every new bucket remains checkable through saturation: " + value);
        expect(((int[]) field(scratch, "marks")).length == limit &&
                ((int[]) field(scratch, "bucketKeys")).length == limit,
                "scratch growth stops at the bounded primitive-table limit");
        expect(((Integer) field(scratch, "bucketCount")).intValue() == limit / 2,
                "the saturated table preserves an empty probing slot");
        expect(seen(seen, scratch, 0), "a remembered bucket stays suppressed at capacity");
        expect(!seen(seen, scratch, limit + 7) && !seen(seen, scratch, limit + 7),
                "an unremembered saturated bucket is checked again instead of falsely skipped");
        begin.invoke(scratch);
        expect(!seen(seen, scratch, limit + 7) && seen(seen, scratch, limit + 7),
                "the next request can remember a formerly unremembered bucket");

        setField(scratch, "generation", Integer.valueOf(-1));
        int[] marks = (int[]) field(scratch, "marks");
        java.util.Arrays.fill(marks, 1);
        begin.invoke(scratch);
        expect(((Integer) field(scratch, "generation")).intValue() == 1,
                "generation rollover starts a valid fresh generation");
        expect(!seen(seen, scratch, first) && seen(seen, scratch, first),
                "generation rollover clears stale stamps before checking a bucket");
    }

    private static void checkConcurrentRequests() throws Exception {
        final AdBlockEngine engine = conditionedEngine();
        final IndexView blocked = new IndexView(engine, false);
        final IndexView allowed = new IndexView(engine, true);
        final CountDownLatch ready = new CountDownLatch(8);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(8);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        final Object[] scratches = new Object[8];
        for (int worker = 0; worker < scratches.length; worker++) {
            final int id = worker;
            new Thread(new Runnable() {
                @Override public void run() {
                    ready.countDown();
                    try {
                        start.await();
                        for (int i = 0; i < 600; i++) {
                            String base = "https://cdn.example/worker-" + id + "/request-" + i + "/" +
                                    repeatedPath("conditionkeyword", 6);
                            boolean hit = (i & 1) == 0;
                            expectIndexed(blocked, base + (hit ? "tail/image" : "tailored"),
                                    "cdn.example", "page.example", IMAGE, hit,
                                    "concurrent blocked decision for worker " + id);
                            expectIndexed(allowed, base + "permit", "cdn.example",
                                    hit ? "page.example" : "unrelated.example", DOCUMENT, hit,
                                    "concurrent allowed decision for worker " + id);
                        }
                        scratches[id] = currentScratch();
                    } catch (Throwable error) {
                        failure.compareAndSet(null, error);
                    } finally {
                        done.countDown();
                    }
                }
            }, "adblock-bucket-" + worker).start();
        }
        boolean allReady = ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        expect(allReady, "all concurrent workers were ready together");
        expect(done.await(30, TimeUnit.SECONDS), "all concurrent requests completed");
        if (failure.get() != null) throw new AssertionError("concurrent bucket matching failed", failure.get());
        for (int i = 0; i < scratches.length; i++) {
            expect(scratches[i] != null, "each matcher thread owns scratch state");
            for (int j = 0; j < i; j++)
                expect(scratches[i] != scratches[j], "different matcher threads never share scratch state");
        }
    }

    private static void checkSaturatedMatcher() throws Exception {
        int limit = ((Integer) staticField(AdBlockEngine.class, "RULE_SCRATCH_LIMIT")).intValue();
        int count = limit / 2 + 32;
        StringBuilder source = new StringBuilder(count * 18);
        StringBuilder url = new StringBuilder("https://cdn.example/");
        for (int i = 0; i < count; i++) {
            // Decimal digits span less than the rolling hash's radix, so these six
            // character keys are distinct hashes as well as distinct text tokens.
            String value = Integer.toString(i);
            StringBuilder keyword = new StringBuilder();
            for (int padding = value.length(); padding < 6; padding++) keyword.append('0');
            keyword.append(value);
            source.append('*').append(keyword).append("*$script\n");
            url.append(keyword).append('/');
        }
        source.append("*zzzzzz*$image");
        AdBlockEngine engine = new AdBlockEngine();
        expect(engine.updateRules(source.toString(), Collections.<String>emptyList()) == count + 1,
                "saturation scenario compiles every distinct keyword bucket");
        IndexView blocked = new IndexView(engine, false);
        String hit = url.toString() + "zzzzzz";
        expect(hit.length() <= 16384, "saturation scenario stays within the public request URL budget");
        expectIndexed(blocked, hit, "cdn.example", "page.example", IMAGE, true,
                "a new matching bucket is checked after earlier type mismatches saturate scratch");
        Object scratch = currentScratch();
        int remembered = ((Integer) field(scratch, "bucketCount")).intValue();
        expect(remembered == limit / 2,
                "the real matching call saturated scratch before its final matching bucket: " + remembered);
        expect(!seen(method(scratch.getClass(), "alreadySeenBucket", int.class), scratch,
                blocked.bucket("*zzzzzz*")),
                "the successful bucket was matched despite being unremembered at capacity");
        expectIndexed(blocked, url.toString() + "missing", "cdn.example", "page.example", IMAGE, false,
                "scratch saturation cannot create a match on a subsequent request");
    }

    private static void expectIndexed(IndexView view, String url, String host, String page, int type,
                                      boolean expected, String label) throws Exception {
        expect(view.reference(url, host, page, type) == expected, label + " (exhaustive rule predicate)");
        expect(view.matches(url, host, page, type, false) == expected, label + " (six arguments)");
        expect(view.matches(url, host, page, type, true) == expected, label + " (cached site keys)");
    }

    private static final class IndexView {
        final Object index;
        final List<?> generic;
        final Object[] candidates;
        final Method six;
        final Method eight;
        final Method ruleMatch;

        IndexView(AdBlockEngine engine, boolean allowed) throws Exception {
            Object snapshot = field(engine, "rules");
            index = field(snapshot, allowed ? "allowedIndex" : "blockedIndex");
            generic = (List<?>) field(snapshot, allowed ? "genericAllowed" : "genericBlocked");
            candidates = (Object[]) field(index, "candidates");
            six = method(AdBlockEngine.class, "matchesIndexed", index.getClass(), List.class,
                    String.class, String.class, String.class, int.class);
            eight = method(AdBlockEngine.class, "matchesIndexed", index.getClass(), List.class,
                    String.class, String.class, String.class, int.class, String.class, String.class);
            Class<?> ruleType = Class.forName("com.xinyv.median.AdBlockEngine$NetworkRule");
            ruleMatch = method(AdBlockEngine.class, "matchesRule", ruleType,
                    String.class, String.class, String.class, int.class, String.class, String.class);
        }

        boolean matches(String url, String host, String page, int type, boolean cachedSites) throws Exception {
            if (!cachedSites)
                return ((Boolean) six.invoke(null, index, generic, url, host, page, type)).booleanValue();
            return ((Boolean) eight.invoke(null, index, generic, url, host, page, type,
                    SITE_KEY.invoke(null, host), SITE_KEY.invoke(null, page))).booleanValue();
        }

        boolean reference(String url, String host, String page, int type) throws Exception {
            String requestSite = (String) SITE_KEY.invoke(null, host);
            String pageSite = (String) SITE_KEY.invoke(null, page);
            for (Object rule : candidates)
                if (((Boolean) ruleMatch.invoke(null, rule, url, host, page, type, requestSite, pageSite))
                        .booleanValue()) return true;
            for (Object rule : generic)
                if (((Boolean) ruleMatch.invoke(null, rule, url, host, page, type, requestSite, pageSite))
                        .booleanValue()) return true;
            return false;
        }

        Object rule(String pattern) throws Exception {
            for (Object candidate : candidates)
                if (pattern.equals(field(candidate, "pattern"))) return candidate;
            throw new AssertionError("Missing indexed rule: " + pattern);
        }

        int bucket(String pattern) throws Exception {
            String keyword = (String) BEST_KEYWORD.invoke(null, pattern);
            int hash = ((Integer) KEYWORD_HASH.invoke(null, keyword, 0)).intValue();
            int slot = ((Integer) method(index.getClass(), "find", int.class).invoke(index, hash)).intValue();
            expect(slot >= 0, "rule is retrieved from its keyword bucket: " + pattern);
            return slot;
        }
    }

    private static String repeatedPath(String keyword, int count) {
        StringBuilder result = new StringBuilder(count * (keyword.length() + 1));
        for (int i = 0; i < count; i++) result.append(keyword).append('/');
        return result.toString();
    }

    private static boolean seen(Method method, Object scratch, int bucket) throws Exception {
        return ((Boolean) method.invoke(scratch, bucket)).booleanValue();
    }

    private static Object currentScratch() throws Exception {
        return ((ThreadLocal<?>) staticField(AdBlockEngine.class, "RULE_MATCH_SCRATCH")).get();
    }

    private static Method method(Class<?> type, String name, Class<?>... arguments) {
        try {
            Method result = type.getDeclaredMethod(name, arguments);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Missing method: " + name, error);
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field result = target.getClass().getDeclaredField(name);
        result.setAccessible(true);
        return result.get(target);
    }

    private static Object staticField(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result.get(null);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field result = target.getClass().getDeclaredField(name);
        result.setAccessible(true);
        result.set(target, value);
    }

    private static void expect(boolean value, String label) {
        if (!value) throw new AssertionError("Expected: " + label);
    }
}
