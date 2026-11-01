package com.xinyv.median;

import android.os.Handler;
import android.os.HandlerThread;
import android.webkit.CookieManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class CookieFlushSchedulerSelfTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        coalescesBurstAndUsesBackground();
        acceptsRequestsDuringBlockedFlush();
        exceptionAndRejectedPostReleaseQueue();
        retainsSharedQueueUntilOtherUsersRelease();
        System.out.println("CookieFlushSchedulerSelfTest passed: " + assertions + " assertions / 4 scenarios");
    }

    private static void coalescesBurstAndUsesBackground() throws Exception {
        int started = HandlerThread.started;
        int stopped = HandlerThread.stopped;
        CookieFlushScheduler scheduler = new CookieFlushScheduler();
        for (int i = 0; i < 200; i++) scheduler.schedule();
        check(Handler.pending() == 1, "burst must enqueue only one write");
        check(Handler.delay == 1000L, "preserve one-second coalescing window");
        check(CookieManager.flushedOn == null, "scheduling must not flush inline");
        check(HandlerThread.started == started + 1, "one lazy I/O acquisition");
        join(Handler.dispatchNext());
        check(CookieManager.flushedOn != Thread.currentThread(), "cookie flush must use worker thread");
        check("test-local-io".equals(CookieManager.flushedOn.getName()), "production CookieManager action must use I/O queue");
        check(Handler.pending() == 0, "no polling or periodic writes after idle");
        check(HandlerThread.stopped == stopped + 1, "idle scheduler releases shared I/O ownership");
    }

    private static void acceptsRequestsDuringBlockedFlush() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch unblock = new CountDownLatch(1);
        int stopped = HandlerThread.stopped;
        CookieFlushScheduler scheduler = new CookieFlushScheduler(new Runnable() {
            @Override public void run() {
                if (calls.incrementAndGet() == 1) {
                    entered.countDown();
                    try {
                        if (!unblock.await(5L, TimeUnit.SECONDS)) throw new AssertionError("test did not unblock flush");
                    } catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
                }
            }
        });
        scheduler.schedule();
        Thread writer = Handler.dispatchNext();
        check(entered.await(2L, TimeUnit.SECONDS), "worker must begin flush");
        CountDownLatch scheduled = new CountDownLatch(1);
        Thread caller = new Thread(new Runnable() {
            @Override public void run() {
                for (int i = 0; i < 200; i++) scheduler.schedule();
                scheduled.countDown();
            }
        }, "test-ui-caller");
        caller.setDaemon(true);
        caller.start();
        boolean responsive = scheduled.await(1L, TimeUnit.SECONDS);
        unblock.countDown();
        join(writer);
        join(caller);
        check(responsive, "a blocked flush must not block the caller or hold the scheduler lock");
        check(calls.get() == 1, "new requests must not start concurrent cookie flushes");
        check(Handler.pending() == 1, "changes during flush need exactly one following write");
        check(HandlerThread.stopped == stopped, "keep I/O ownership until trailing write finishes");
        join(Handler.dispatchNext());
        check(calls.get() == 2, "trailing write must not lose changes made during flush");
        check(Handler.pending() == 0, "trailing write must become idle");
        check(HandlerThread.stopped == stopped + 1, "release after last accepted write without an Activity callback");
    }

    private static void exceptionAndRejectedPostReleaseQueue() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CookieFlushScheduler scheduler = new CookieFlushScheduler(new Runnable() {
            @Override public void run() {
                if (calls.incrementAndGet() == 1) throw new IllegalStateException("fake cookie provider failure");
            }
        });
        int stopped = HandlerThread.stopped;
        scheduler.schedule();
        join(Handler.dispatchNext());
        check(HandlerThread.stopped == stopped + 1, "provider exception must release I/O ownership");
        scheduler.schedule();
        join(Handler.dispatchNext());
        check(calls.get() == 2, "provider exception must not poison future scheduling");
        stopped = HandlerThread.stopped;
        Handler.rejectNext = true;
        scheduler.schedule();
        check(Handler.pending() == 0, "rejected delayed post must not leave queued work");
        check(HandlerThread.stopped == stopped + 1, "rejected post must release I/O ownership");
        scheduler.schedule();
        join(Handler.dispatchNext());
        check(calls.get() == 3, "rejected post must allow a later retry");
    }

    private static void retainsSharedQueueUntilOtherUsersRelease() throws Exception {
        int stopped = HandlerThread.stopped;
        LocalDataIo.acquire();
        CookieFlushScheduler scheduler = new CookieFlushScheduler();
        scheduler.schedule();
        join(Handler.dispatchNext());
        check(HandlerThread.stopped == stopped, "cookie completion must not stop another store's I/O queue");
        LocalDataIo.release();
        check(HandlerThread.stopped == stopped + 1, "last store release must stop shared queue");
    }

    private static void join(Thread thread) throws Exception {
        thread.join(2500L);
        check(!thread.isAlive(), "test worker must finish");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
