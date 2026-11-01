package com.xinyv.median;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class AdBlockScaleSelfTest {
    public static void main(String[] args) throws Exception {
        int count = args.length == 0 ? 100000 : Integer.parseInt(args[0]);
        if (count < 1 || count > 1000000) throw new IllegalArgumentException("count out of range");
        StringBuilder source = new StringBuilder(count * 22);
        for (int i = 0; i < count; i++) {
            if (i > 0) source.append('\n');
            source.append("||ad");
            appendPadded(source, i);
            source.append(".example^");
        }

        long started = System.nanoTime();
        AdBlockEngine engine = new AdBlockEngine();
        int compiled = engine.updateRules(source.toString(), Collections.<String>emptyList());
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        if (compiled != count) throw new AssertionError("expected " + count + " rules, got " + compiled);

        Object ruleSet = field(engine, "rules");
        Object packed = field(ruleSet, "blockedDomains");
        int packedCount = ((Integer) invoke(packed, "size")).intValue();
        if (packedCount != count) throw new AssertionError("packed table lost rules: " + packedCount);
        if (!((Boolean) invoke(packed, "matches", new Class<?>[] { String.class },
                new Object[] { "cdn.ad000042.example" })).booleanValue())
            throw new AssertionError("subdomain lookup failed");
        if (((Boolean) invoke(packed, "matches", new Class<?>[] { String.class },
                new Object[] { "safe.example" })).booleanValue())
            throw new AssertionError("unlisted domain matched");

        // Exercise the same domain decision used by WebView's request hot path. The unit-test
        // classpath contains the Android API stubs, so invoke this pure part directly instead
        // of constructing an android.net.Uri.
        Method domainDecision = AdBlockEngine.class.getDeclaredMethod("domainDecision",
                ruleSet.getClass(), String.class);
        domainDecision.setAccessible(true);
        for (int i = 0; i < 2000; i++) domainDecision.invoke(null, ruleSet, "cdn.ad000042.example");
        long lookupStarted = System.nanoTime();
        for (int i = 0; i < 20000; i++) {
            if (!((Boolean) domainDecision.invoke(null, ruleSet, "cdn.ad000042.example")).booleanValue())
                throw new AssertionError("hot-path domain lookup failed");
        }
        long lookupMs = (System.nanoTime() - lookupStarted) / 1_000_000L;

        // A fixed table must remain exact when unrelated hosts land in the same slot. Exercise
        // both cache directions, then verify that replacing a snapshot drops every old decision.
        AdBlockEngine cacheEngine = new AdBlockEngine();
        cacheEngine.updateRules("||blocked-cache.example^", Collections.<String>emptyList());
        Object cacheSet = field(cacheEngine, "rules");
        Method cacheDecision = AdBlockEngine.class.getDeclaredMethod("domainDecision",
                cacheSet.getClass(), String.class);
        cacheDecision.setAccessible(true);
        String blockedHost = "blocked-cache.example";
        String collisionHost = findCollision(blockedHost);
        expect(((Boolean) cacheDecision.invoke(null, cacheSet, blockedHost)).booleanValue(),
                "blocked cache decision");
        expect(!((Boolean) cacheDecision.invoke(null, cacheSet, collisionHost)).booleanValue(),
                "colliding unknown host is not blocked");
        expect(((Boolean) cacheDecision.invoke(null, cacheSet, blockedHost)).booleanValue(),
                "blocked decision survives a colliding lookup");

        cacheEngine.updateRules("||new-cache.example^", Collections.<String>emptyList());
        cacheSet = field(cacheEngine, "rules");
        expect(!((Boolean) cacheDecision.invoke(null, cacheSet, blockedHost)).booleanValue(),
                "replaced snapshot forgets blocked host");
        expect(((Boolean) cacheDecision.invoke(null, cacheSet, "new-cache.example")).booleanValue(),
                "replaced snapshot accepts new host");
        runConcurrentCacheCheck(cacheEngine, cacheDecision);

        char[] characters = (char[]) field(packed, "characters");
        int[] offsets = (int[]) field(packed, "offsets");
        long packedBytes = characters.length * 2L + offsets.length * 4L;
        if (packedBytes >= source.length() * 3L)
            throw new AssertionError("packed representation unexpectedly large: " + packedBytes);
        System.out.println("AdBlockScaleSelfTest passed: " + count + " rules, " + elapsedMs +
                " ms compile, " + lookupMs + " ms / 20k cached lookups, approximately " + packedBytes + " packed bytes");
    }

    private static void appendPadded(StringBuilder out, int value) {
        String text = Integer.toString(value);
        for (int i = text.length(); i < 6; i++) out.append('0');
        out.append(text);
    }

    private static String findCollision(String host) {
        int slot = cacheSlot(host);
        for (int i = 0; i < 200000; i++) {
            String candidate = "unknown-cache-" + i + ".example";
            if (candidate.hashCode() == host.hashCode()) continue;
            if (cacheSlot(candidate) == slot) return candidate;
        }
        throw new AssertionError("could not find a cache-slot collision");
    }

    private static int cacheSlot(String host) {
        int hash = host.hashCode();
            return (hash ^ (hash >>> 16)) & 1023;
    }

    private static void runConcurrentCacheCheck(final AdBlockEngine engine, final Method decision)
            throws Exception {
        final Object set = field(engine, "rules");
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(4);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        for (int worker = 0; worker < 4; worker++) {
            final int offset = worker;
            new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        start.await();
                        for (int i = 0; i < 5000; i++) {
                            String host = (i & 1) == 0 ? "new-cache.example" :
                                    "parallel-unknown-" + (offset * 5000 + i) + ".example";
                            boolean blocked = ((Boolean) decision.invoke(null, set, host)).booleanValue();
                            if (blocked != ((i & 1) == 0))
                                throw new AssertionError("concurrent cache decision mismatch");
                        }
                    } catch (Throwable error) {
                        failure.compareAndSet(null, error);
                    } finally {
                        done.countDown();
                    }
                }
            }, "adblock-cache-" + worker).start();
        }
        start.countDown();
        done.await();
        if (failure.get() != null) throw new AssertionError("concurrent cache check failed", failure.get());
    }

    private static void expect(boolean value, String label) {
        if (!value) throw new AssertionError("Expected: " + label);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object invoke(Object target, String name) throws Exception {
        return invoke(target, name, new Class<?>[0], new Object[0]);
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object[] arguments) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, arguments);
    }
}
