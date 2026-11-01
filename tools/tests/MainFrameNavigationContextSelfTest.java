package com.xinyv.median;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Request-source boundaries across asynchronous WebView navigation callbacks. */
public final class MainFrameNavigationContextSelfTest {
    private static final String TARGET = "https://allowed.example/next?episode=1";
    private static final String OTHER_TARGET = "https://other.example/next?episode=1";
    private static final String SOURCE = "page.example";

    public static void main(String[] args) throws Exception {
        mainFrameConsumesSourceOnce();
        pageStartedCanPrecedeOrFollowInterception();
        subresourcesAndUnrelatedRequestsDoNotConsumeSource();
        newerNavigationReplacesEarlierSource();
        callbacksOnlyClearTheNavigationTheyDescribe();
        invalidRequestsCannotObtainSource();
        normalizationPreservesRequestBoundaries();
        disabledSourceIsBoundToOneRequest();
        concurrentConsumersCannotReuseSource();
        System.out.println("MainFrameNavigationContextSelfTest passed");
    }

    private static void mainFrameConsumesSourceOnce() {
        MainFrameNavigationContext context = new MainFrameNavigationContext();
        expect(context.take(TARGET, true) == null, "a request with no navigation has no source grant");
        context.arm(TARGET, SOURCE, true);
        expectSource(context.take(TARGET, true), SOURCE, true, "the initiated main frame uses its source");
        expect(context.take(TARGET, true) == null, "reload of the same URL cannot reuse a consumed source");
    }

    private static void pageStartedCanPrecedeOrFollowInterception() {
        MainFrameNavigationContext startFirst = new MainFrameNavigationContext();
        startFirst.arm(TARGET, SOURCE, true);
        startFirst.onPageStarted(TARGET);
        expectSource(startFirst.take(TARGET, true), SOURCE, true,
                "pageStarted does not erase a pending request's source");

        MainFrameNavigationContext interceptFirst = new MainFrameNavigationContext();
        interceptFirst.arm(TARGET, SOURCE, true);
        expectSource(interceptFirst.take(TARGET, true), SOURCE, true,
                "request before pageStarted uses its source");
        interceptFirst.onPageStarted(TARGET);
        expect(interceptFirst.take(TARGET, true) == null,
                "pageStarted after interception cannot recreate the consumed source");
    }

    private static void subresourcesAndUnrelatedRequestsDoNotConsumeSource() {
        MainFrameNavigationContext context = new MainFrameNavigationContext();
        context.arm(TARGET, SOURCE, true);
        expect(context.take(TARGET, false) == null,
                "a subresource with the identical URL must still use destination policy");
        expect(context.take("https://allowed.example/poster.jpg", false) == null,
                "an ordinary subresource has no main-frame source");
        expect(context.take(OTHER_TARGET, true) == null,
                "another host cannot obtain the pending source's exception");
        expect(context.take("https://allowed.example/other?episode=1", true) == null,
                "another path cannot obtain the pending source's exception");
        expect(context.take("https://allowed.example/next?episode=2", true) == null,
                "another query cannot obtain the pending source's exception");
        expectSource(context.take(TARGET, true), SOURCE, true,
                "unrelated callbacks do not swallow the accepted navigation's source");
    }

    private static void newerNavigationReplacesEarlierSource() {
        MainFrameNavigationContext context = new MainFrameNavigationContext();
        context.arm(TARGET, SOURCE, true);
        context.arm(OTHER_TARGET, "new-source.example", false);
        expect(context.take(TARGET, true) == null, "late old request cannot obtain the new source");
        expectSource(context.take(OTHER_TARGET, true), "new-source.example", false,
                "late old request does not consume the new request's source");
        expect(context.take(OTHER_TARGET, true) == null, "replacement source is also single use");

        context.arm(TARGET, SOURCE, true);
        context.arm(TARGET, "replacement.example", false);
        expectSource(context.take(TARGET, true), "replacement.example", false,
                "a newer navigation to the same URL replaces the previous source policy");
    }

    private static void callbacksOnlyClearTheNavigationTheyDescribe() {
        MainFrameNavigationContext context = new MainFrameNavigationContext();
        context.arm(OTHER_TARGET, "new-source.example", true);
        context.clearForUrl(TARGET);
        expectSource(context.take(OTHER_TARGET, true), "new-source.example", true,
                "late old commit or finish does not retire the new navigation");

        context.arm(TARGET, SOURCE, true);
        context.clearForUrl(TARGET + "#finished");
        expect(context.take(TARGET, true) == null, "matching finish retires an unused source");

        context.arm(TARGET, SOURCE, true);
        context.onPageStarted(OTHER_TARGET);
        expect(context.take(TARGET, true) == null,
                "redirect or replacement page does not leave the previous request's source available");
        expect(context.take(OTHER_TARGET, true) == null,
                "redirect page does not inherit an unrelated pending source");

        context.arm(TARGET, SOURCE, true);
        context.clear();
        expect(context.take(TARGET, true) == null, "explicit native navigation or destruction clears source");
    }

    private static void normalizationPreservesRequestBoundaries() {
        String[][] equivalents = {
                {"HTTPS://ALLOWED.EXAMPLE:443/next?episode=1#top", TARGET},
                {"https://allowed.example", "https://allowed.example/"},
                {"http://allowed.example:80/next#top", "http://ALLOWED.EXAMPLE/next#bottom"},
                {"https://allowed.example/a/../next?episode=1", TARGET},
                {"https://[2001:db8::10]:443/next#top", "https://[2001:db8::10]/next"}
        };
        for (String[] urls : equivalents) {
            MainFrameNavigationContext context = new MainFrameNavigationContext();
            context.arm(urls[0], SOURCE, true);
            context.onPageStarted(urls[1]);
            expectSource(context.take(urls[1], true), SOURCE, true,
                    "provider-normalized URL retains the accepted source: " + urls[0]);
        }

        String[][] distinct = {
                {TARGET, "http://allowed.example/next?episode=1"},
                {TARGET, "https://allowed.example:444/next?episode=1"},
                {TARGET, "https://allowed.example/NEXT?episode=1"},
                {TARGET, "https://allowed.example/next?Episode=1"},
                {TARGET, "https://allowed.example/next?episode=1&extra=1"},
                {"https://allowed.example/next?q=a%2Fb", "https://allowed.example/next?q=a%2fb"},
                {"https://allowed.example/a%2Fb", "https://allowed.example/a/b"}
        };
        for (String[] urls : distinct) {
            MainFrameNavigationContext context = new MainFrameNavigationContext();
            context.arm(urls[0], SOURCE, true);
            expect(context.take(urls[1], true) == null,
                    "normalization cannot broaden a source grant: " + urls[1]);
            expectSource(context.take(urls[0], true), SOURCE, true,
                    "rejected URL cannot consume the exact accepted source");
        }
    }

    private static void invalidRequestsCannotObtainSource() {
        String[] invalid = { null, "", "about:blank", "https:/allowed.example/next", "https://[broken" };
        for (String url : invalid) {
            MainFrameNavigationContext context = new MainFrameNavigationContext();
            context.arm(TARGET, SOURCE, true);
            expect(context.take(url, true) == null, "invalid request cannot obtain an existing source");
            expectSource(context.take(TARGET, true), SOURCE, true,
                    "invalid request does not consume the accepted navigation");
            context.arm(TARGET, SOURCE, true);
            context.arm(url, SOURCE, false);
            expect(context.take(TARGET, true) == null, "invalid replacement retires the earlier source");
            expect(context.take(url, true) == null, "invalid replacement cannot create a permissive source");
        }
    }

    private static void disabledSourceIsBoundToOneRequest() {
        MainFrameNavigationContext context = new MainFrameNavigationContext();
        context.arm(TARGET, SOURCE, false);
        expect(context.take(OTHER_TARGET, true) == null,
                "a paused source does not disable filtering for another request");
        expect(context.take(TARGET, false) == null,
                "a paused initiating page does not disable destination subresource filtering");
        expectSource(context.take(TARGET, true), SOURCE, false,
                "the matching main frame retains its captured paused policy");
        expect(context.take(TARGET, true) == null, "paused source policy cannot leak to a later reload");
    }

    private static void concurrentConsumersCannotReuseSource() throws Exception {
        final MainFrameNavigationContext context = new MainFrameNavigationContext();
        context.arm(TARGET, SOURCE, true);
        final CountDownLatch ready = new CountDownLatch(8);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger consumed = new AtomicInteger();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        Thread[] workers = new Thread[8];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(new Runnable() {
                @Override public void run() {
                    ready.countDown();
                    try {
                        start.await();
                        MainFrameNavigationContext.Snapshot snapshot = context.take(TARGET, true);
                        if (snapshot != null) {
                            expectSource(snapshot, SOURCE, true, "concurrent winning request has the right source");
                            consumed.incrementAndGet();
                        }
                    } catch (Throwable failure) {
                        error.compareAndSet(null, failure);
                    }
                }
            }, "main-frame-source-" + i);
            workers[i].start();
        }
        ready.await();
        start.countDown();
        for (Thread worker : workers) worker.join(5_000L);
        for (Thread worker : workers) expect(!worker.isAlive(), "concurrent request finished");
        if (error.get() != null) throw new AssertionError("Concurrent source lookup failed", error.get());
        expect(consumed.get() == 1, "only one concurrent main-frame request can consume source");
    }

    private static void expectSource(MainFrameNavigationContext.Snapshot snapshot, String sourceHost,
                                     boolean protectionEnabled, String label) {
        expect(snapshot != null, label + " (source exists)");
        expect(sourceHost.equals(snapshot.sourceHost), label + " (source host)");
        expect(snapshot.protectionEnabled == protectionEnabled, label + " (source policy)");
    }

    private static void expect(boolean value, String label) {
        if (!value) throw new AssertionError("Expected: " + label);
    }
}
