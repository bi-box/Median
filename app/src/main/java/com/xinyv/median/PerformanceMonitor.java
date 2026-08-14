package com.xinyv.median;

import android.app.Activity;
import android.os.Debug;

import java.util.Locale;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

final class PerformanceMonitor {
    private final AtomicLong frames = new AtomicLong();
    private final AtomicLong slow16 = new AtomicLong();
    private final AtomicLong slow32 = new AtomicLong();
    private final AtomicLong slow100 = new AtomicLong();
    private final AtomicLong maxNanos = new AtomicLong();
    private final AtomicLong droppedCallbacks = new AtomicLong();
    private final long[] recentFrames = new long[4096];
    private int recentCount;
    private int recentCursor;
    private Object platformMonitor;

    void start(Activity activity) {
        if (platformMonitor == null) platformMonitor = new Api24Monitor(activity, this);
    }

    void stop(Activity activity) {
        if (platformMonitor instanceof Api24Monitor) {
            ((Api24Monitor) platformMonitor).stop(activity);
        }
        platformMonitor = null;
    }

    private void record(long duration) {
        if (duration <= 0) return;
        frames.incrementAndGet();
        if (duration > 16_700_000L) slow16.incrementAndGet();
        if (duration > 33_400_000L) slow32.incrementAndGet();
        if (duration > 100_000_000L) slow100.incrementAndGet();
        long old;
        do {
            old = maxNanos.get();
            if (duration <= old) break;
        } while (!maxNanos.compareAndSet(old, duration));
        synchronized (recentFrames) {
            recentFrames[recentCursor] = duration;
            recentCursor = (recentCursor + 1) % recentFrames.length;
            if (recentCount < recentFrames.length) recentCount++;
        }
    }

    private void recordDropped(int count) {
        if (count > 0) droppedCallbacks.addAndGet(count);
    }

    String snapshot(String modeLabel) {
        long count = frames.get();
        long jank = slow16.get();
        long severe = slow32.get();
        long frozen = slow100.get();
        double percent = count == 0 ? 0.0 : (100.0 * jank / count);
        long pssKb = Debug.getPss();
        Runtime runtime = Runtime.getRuntime();
        long javaUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
        long javaLimitMb = runtime.maxMemory() / (1024L * 1024L);
        long[] sample;
        synchronized (recentFrames) {
            sample = new long[recentCount];
            for (int i = 0; i < recentCount; i++) sample[i] = recentFrames[i];
        }
        Arrays.sort(sample);
        double p50 = percentileMs(sample, 0.50d);
        double p95 = percentileMs(sample, 0.95d);
        double p99 = percentileMs(sample, 0.99d);
        return "当前档位：" + modeLabel +
                "\n进程 PSS：约 " + (pssKb / 1024L) + " MB" +
                "\nJava 堆：" + javaUsedMb + " / " + javaLimitMb + " MB" +
                "\n统计帧数：" + count +
                "\n>16.7ms：" + jank + "（" + String.format(Locale.US, "%.1f", percent) + "%）" +
                "\n>33.4ms：" + severe +
                "\n>100ms：" + frozen +
                "\nP50 / P95 / P99：" + String.format(Locale.US, "%.1f / %.1f / %.1f ms", p50, p95, p99) +
                "\n指标回调丢帧：" + droppedCallbacks.get() +
                "\n最慢帧：" + String.format(Locale.US, "%.1f", maxNanos.get() / 1_000_000.0) + " ms";
    }

    private static double percentileMs(long[] sorted, double fraction) {
        if (sorted.length == 0) return 0d;
        int index = (int) Math.ceil(fraction * sorted.length) - 1;
        index = Math.max(0, Math.min(sorted.length - 1, index));
        return sorted[index] / 1_000_000d;
    }

    private static final class Api24Monitor {
        private final android.os.HandlerThread thread;
        private final android.view.Window.OnFrameMetricsAvailableListener listener;

        Api24Monitor(Activity activity, final PerformanceMonitor owner) {
            thread = new android.os.HandlerThread("median-frame-metrics", android.os.Process.THREAD_PRIORITY_BACKGROUND);
            thread.start();
            listener = new android.view.Window.OnFrameMetricsAvailableListener() {
                @Override public void onFrameMetricsAvailable(android.view.Window window, android.view.FrameMetrics frameMetrics, int dropCountSinceLastInvocation) {
                    owner.record(frameMetrics.getMetric(android.view.FrameMetrics.TOTAL_DURATION));
                    owner.recordDropped(dropCountSinceLastInvocation);
                }
            };
            activity.getWindow().addOnFrameMetricsAvailableListener(listener, new android.os.Handler(thread.getLooper()));
        }

        void stop(Activity activity) {
            activity.getWindow().removeOnFrameMetricsAvailableListener(listener);
            thread.quitSafely();
        }
    }
}
