// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Median Browser contributors

package com.xinyv.median;

import android.os.Process;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared bounded background executor with predictable thread priority and idle teardown. */
final class BackgroundExecutor {
    private static final long THREAD_STACK_BYTES = 512L * 1024L;

    private BackgroundExecutor() {}

    static ThreadPoolExecutor create(int threads, int capacity, final String name,
                                     boolean latestWins) {
        return create(threads, capacity, name, latestWins, Process.THREAD_PRIORITY_BACKGROUND);
    }

    /** Provider initialization is required by the visible page, so do not throttle it as idle work. */
    static ThreadPoolExecutor createStartup(int threads, int capacity, final String name) {
        return create(threads, capacity, name, false, Process.THREAD_PRIORITY_DEFAULT);
    }

    private static ThreadPoolExecutor create(int threads, int capacity, final String name,
                                             boolean latestWins, final int priority) {
        final int count = Math.max(1, threads);
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger sequence = new AtomicInteger();
            @Override public Thread newThread(final Runnable task) {
                Thread thread = new Thread(null, new Runnable() {
                    @Override public void run() {
                        try { Process.setThreadPriority(priority); }
                        catch (RuntimeException ignored) {}
                        task.run();
                    }
                }, name + "-" + sequence.incrementAndGet(), THREAD_STACK_BYTES);
                thread.setDaemon(false);
                return thread;
            }
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(count, count, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(Math.max(count, capacity)), factory,
                latestWins ? new ThreadPoolExecutor.DiscardOldestPolicy() :
                        new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
