package com.xinyv.median;

import android.os.Process;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared bounded background executor with predictable thread priority and idle teardown. */
final class BackgroundExecutor {
    private BackgroundExecutor() {}

    static ThreadPoolExecutor create(int threads, int capacity, final String name,
                                     boolean latestWins) {
        final int count = Math.max(1, threads);
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger sequence = new AtomicInteger();
            @Override public Thread newThread(final Runnable task) {
                Thread thread = new Thread(new Runnable() {
                    @Override public void run() {
                        try { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); }
                        catch (RuntimeException ignored) {}
                        task.run();
                    }
                }, name + "-" + sequence.incrementAndGet());
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
