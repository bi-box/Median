package com.xinyv.median;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

/** One serial background queue shared by the small local stores. */
final class LocalDataIo {
    private static HandlerThread thread;
    private static Handler handler;
    private static int users;

    static synchronized Handler acquire() {
        if (handler == null) {
            thread = new HandlerThread("median-local-io", Process.THREAD_PRIORITY_BACKGROUND);
            thread.start();
            handler = new Handler(thread.getLooper());
        }
        users++;
        return handler;
    }

    static synchronized void release() {
        if (--users != 0) return;
        thread.quitSafely();
        thread = null;
        handler = null;
    }

    private LocalDataIo() {}
}
