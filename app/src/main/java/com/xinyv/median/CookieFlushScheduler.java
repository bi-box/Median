package com.xinyv.median;

import android.os.Handler;
import android.webkit.CookieManager;

/** Coalesces blocking cookie persistence on the shared local I/O queue. */
final class CookieFlushScheduler {
    private static final long DELAY_MS = 1000L;
    private final Runnable flush;
    private Handler io;
    private boolean pending;
    private boolean running;
    private boolean requestedWhileRunning;

    private final Runnable writer = new Runnable() {
        @Override public void run() { writePending(); }
    };

    CookieFlushScheduler() {
        this(new Runnable() {
            @Override public void run() { CookieManager.getInstance().flush(); }
        });
    }

    CookieFlushScheduler(Runnable flush) { this.flush = flush; }

    synchronized void schedule() {
        if (pending) return;
        if (running) {
            requestedWhileRunning = true;
            return;
        }
        if (io == null) io = LocalDataIo.acquire();
        pending = true;
        if (!io.postDelayed(writer, DELAY_MS)) {
            pending = false;
            releaseIo();
        }
    }

    private void writePending() {
        synchronized (this) {
            pending = false;
            running = true;
        }
        try { flush.run(); }
        catch (RuntimeException ignored) {}
        finally {
            synchronized (this) {
                running = false;
                if (requestedWhileRunning) {
                    requestedWhileRunning = false;
                    schedule();
                } else {
                    releaseIo();
                }
            }
        }
    }

    /** No Activity is retained: accepted writes survive destruction, then release the queue. */
    private void releaseIo() {
        io = null;
        LocalDataIo.release();
    }
}
