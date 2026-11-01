package com.xinyv.median;

import android.net.Uri;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Metadata inferred by a failed exception must retain its meaning in the blocking index. */
public final class AdBlockRequestMetadataSelfTest {
    private static final String RULES =
            "@@*sharedkeyword*/permit$image,third-party,domain=page.example\n" +
            "@@*sharedkeyword*/permit$script,~third-party,domain=page.example\n" +
            "*sharedkeyword*/blocked$script,third-party,domain=page.example|~safe.page.example\n" +
            "*sharedkeyword*/blocked$image,~third-party\n" +
            "*sharedkeyword*/blocked$document,third-party\n" +
            "*sharedkeyword*/permit$script\n" +
            "*sharedkeyword*/permit$image\n" +
            "*sharedkeyword*/excluded$~image\n";
    private static int assertions;

    public static void main(String[] args) throws Exception {
        AdBlockEngine engine = new AdBlockEngine();
        engine.updateRules(RULES, Collections.<String>emptyList());
        expect(engine, "https://cdn.example/sharedkeyword/blocked.js", "page.example", "", false, true, false);
        expect(engine, "https://cdn.example/sharedkeyword/blocked%2Ejs", "  PAGE.EXAMPLE  ", "", false, true, false);
        expect(engine, "https://cdn.example/sharedkeyword/blocked.js", "safe.page.example", "", false, false, false);
        expect(engine, "https://cdn.example/sharedkeyword/blocked.js", "shop.cdn.example", "", false, false, false);
        expect(engine, "https://cdn.example/sharedkeyword/blocked.jpg", "shop.cdn.example", "", false, true, false);
        expect(engine, "https://cdn.example/sharedkeyword/blocked.jpg", "page.example", "", false, false, false);
        expect(engine, "https://cdn.example/sharedkeyword/blocked", null, "", true, true, false);
        expect(engine, "https://cdn.example/sharedkeyword/blocked", "", "", true, true, false);
        expect(engine, "https://cdn.example/sharedkeyword/permit.js", "page.example", "", false, true, false);
        expect(engine, "https://cdn.example/sharedkeyword/permit.jpg", "page.example", "", false, false, true);
        expect(engine, "https://cdn.page.example/sharedkeyword/permit.js", "page.example", "", false, false, true);
        expect(engine, "https://cdn.page.example/sharedkeyword/permit.jpg", "page.example", "", false, true, false);
        expect(engine, "https://cdn.example/sharedkeyword/permit", "page.example", "IMAGE/WEBP", false, false, true);
        expect(engine, "https://cdn.example/sharedkeyword/permit", "page.example", "application/javascript", false, true, false);
        expect(engine, "https://cdn.example/sharedkeyword/excluded", "page.example", "", false, true, false);
        expect(engine, "https://cdn.example/sharedkeyword/excluded", "page.example", "image/webp", false, false, false);
        StringBuilder longUrl = new StringBuilder("https://cdn.example/sharedkeyword/permit.js?");
        for (int i = 0; i < 16400; i++) longUrl.append('x');
        expect(engine, longUrl.toString(), "page.example", "image/webp", false, false, false);
        engine.updateRules("||cdn.example^", Collections.<String>emptyList());
        expect(engine, longUrl.toString(), "page.example", "image/webp", false, true, false);
        engine.updateRules(RULES, Collections.<String>emptyList());
        checkConcurrent(engine);
        System.out.println("AdBlockRequestMetadataSelfTest passed: " + assertions + " assertions, 6 concurrent workers");
    }

    private static void expect(AdBlockEngine engine, String url, String page, String accept,
                               boolean mainFrame, boolean blocked, boolean explicitlyAllowed) {
        Uri uri = Uri.parse(url);
        if (engine.shouldBlock(uri, page, accept, mainFrame) != blocked)
            throw new AssertionError("wrong block decision at case " + assertions);
        assertions++;
        if (engine.isExplicitlyAllowed(uri, page, accept, mainFrame) != explicitlyAllowed)
            throw new AssertionError("wrong explicit exception at case " + assertions);
        assertions++;
    }

    private static void checkConcurrent(final AdBlockEngine engine) throws Exception {
        final CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(6);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        for (int worker = 0; worker < 6; worker++) {
            final int number = worker;
            new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        start.await();
                        Uri request = Uri.parse("https://cdn.example/sharedkeyword/permit");
                        String accept = (number & 1) == 0 ? "application/javascript" : "image/webp";
                        boolean blocked = (number & 1) == 0;
                        for (int i = 0; i < 10000; i++) {
                            if (engine.shouldBlock(request, "page.example", accept, false) != blocked ||
                                    engine.isExplicitlyAllowed(request, "page.example", accept, false) == blocked)
                                throw new AssertionError("request metadata crossed concurrent requests");
                        }
                    } catch (Throwable error) { failure.compareAndSet(null, error); }
                    finally { done.countDown(); }
                }
            }, "adblock-metadata-" + worker).start();
        }
        start.countDown();
        done.await();
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
