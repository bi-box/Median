package com.xinyv.median;

import static com.xinyv.median.ByteFormat.humanBytes;
import static com.xinyv.median.ByteFormat.humanSpeed;
import static com.xinyv.median.NetworkSecurity.closeQuietly;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentCallbacks2;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.DocumentsContract;
import android.util.AtomicFile;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Resumable, range-aware foreground downloader. A compact cursor is persisted so a
 * manual pause, process restart or transient failure does not discard completed data.
 */
public final class AdaptiveDownloadService extends Service {
    static final String ACTION_DOWNLOAD = "com.xinyv.median.action.ADAPTIVE_DOWNLOAD";
    static final String ACTION_PAUSE = "com.xinyv.median.action.PAUSE_ADAPTIVE_DOWNLOAD";
    static final String ACTION_RESUME = "com.xinyv.median.action.RESUME_ADAPTIVE_DOWNLOAD";
    static final String ACTION_CANCEL = "com.xinyv.median.action.CANCEL_ADAPTIVE_DOWNLOAD";
    static final String EXTRA_ID = "id";
    static final String EXTRA_URL = "url";
    static final String EXTRA_NAME = "name";
    static final String EXTRA_MIME = "mime";
    static final String EXTRA_USER_AGENT = "user_agent";
    static final String EXTRA_COOKIE = "cookie";
    static final String EXTRA_HEADERS = "headers";
    static final String EXTRA_WIFI_ONLY = "wifi_only";
    static final String EXTRA_ALLOW_ROAMING = "allow_roaming";
    static final String EXTRA_CHARGING_ONLY = "charging_only";
    static final String EXTRA_PUBLIC_ONLY = "public_only";
    static final String EXTRA_TOTAL_BYTES = "total_bytes";

    private static final String CHANNEL_ID = "median_adaptive_downloads_v2";
    private static final int FOREGROUND_ID = 7101;
    private static final int BUFFER_BYTES = 128 * 1024;
    private static final int RETRIES = 4;
    private static final int CONNECT_TIMEOUT_MS = 20000;
    private static final int READ_TIMEOUT_MS = 45000;
    // A server-controlled Content-Length must never make the app preallocate an
    // unbounded sparse file. Four GiB is large enough for normal mobile downloads
    // while keeping a hostile response from exhausting storage in one task.
    private static final long MAX_DOWNLOAD_BYTES = 4L * 1024L * 1024L * 1024L;
    private static final long WAKE_LOCK_TIMEOUT_MS = 30L * 60L * 1000L;
    private static final long WAKE_LOCK_RENEW_MS = 10L * 60L * 1000L;
    private static final int TASK_THREADS = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors()));
    private static final ThreadPoolExecutor TASK_EXECUTOR = new ThreadPoolExecutor(TASK_THREADS, TASK_THREADS,
            30L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(48));
    private static final ConcurrentHashMap<Long, Control> CONTROLS = new ConcurrentHashMap<Long, Control>();
    // The app-private fallback publisher chooses a name by probing the directory.
    // Serialize the probe and copy so two simultaneous tasks cannot select the
    // same target and truncate each other's completed file.
    private static final Object PUBLISH_LOCK = new Object();
    private static final Object CLEANUP_LOCK = new Object();

    private final AtomicInteger activeTasks = new AtomicInteger();
    private NotificationManager notificationManager;
    private DownloadStore store;

    static { TASK_EXECUTOR.allowCoreThreadTimeOut(true); }

    static boolean isTaskScheduled(long id) { return CONTROLS.containsKey(Long.valueOf(id)); }

    @Override public void onCreate() {
        super.onCreate();
        store = new DownloadStore(this);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && notificationManager != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "专业下载", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Median 单连接下载、断点续传与任务控制");
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, final int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        final long id = intent.getLongExtra(EXTRA_ID, 0L);

        if (ACTION_PAUSE.equals(action)) {
            Control control = CONTROLS.get(Long.valueOf(id));
            boolean handled = false;
            if (control != null) {
                synchronized (control) {
                    if (CONTROLS.get(Long.valueOf(id)) == control) {
                        handled = true;
                        if (control.pause("已手动暂停")) markPaused(id, "已手动暂停");
                    }
                }
            }
            if (!handled) {
                markPaused(id, "已手动暂停");
                stopSelfResult(startId);
            }
            return START_NOT_STICKY;
        }
        if (ACTION_CANCEL.equals(action)) {
            DownloadStore.Item current = store.get(id);
            // A stale notification action can arrive after completion telemetry
            // was committed but while the worker is still cleaning up. Never turn
            // a completed record back into cancelled or hide its published URI.
            if (current != null && DownloadStore.STATUS_COMPLETED.equals(current.status)) {
                notifyTask(Task.fromStore(this, current), "已完成", 100, current.downloadedBytes,
                        current.totalBytes, false, false);
                stopSelfResult(startId);
                return START_NOT_STICKY;
            }
            Control control = CONTROLS.get(Long.valueOf(id));
            boolean handled = false;
            if (control != null) {
                synchronized (control) {
                    if (CONTROLS.get(Long.valueOf(id)) == control) {
                        handled = true;
                        if (control.cancel()) {
                            DownloadStore.Item item = store.get(id);
                            if (item != null) store.updateAdaptive(id, DownloadStore.STATUS_CANCELLED, "正在取消",
                                    item.downloadedBytes, item.totalBytes, 0L, null);
                        }
                    }
                }
            }
            if (!handled) {
                DownloadStore.Item item = store.get(id);
                // The worker may have committed and removed its Control after the
                // earlier lookup. Re-read before acting on a stale notification.
                if (item != null && DownloadStore.STATUS_COMPLETED.equals(item.status)) {
                    stopSelfResult(startId);
                    return START_NOT_STICKY;
                }
                deletePartialFiles(id);
                long total = item == null ? 0L : item.totalBytes;
                store.updateAdaptive(id, DownloadStore.STATUS_CANCELLED, "已取消", 0L, total, 0L, null);
                if (item != null) notifyTask(Task.fromStore(this, item), "已取消", 0, 0L, total, false, false);
                stopSelfResult(startId);
            }
            return START_NOT_STICKY;
        }
        if (!ACTION_DOWNLOAD.equals(action) && !ACTION_RESUME.equals(action)) return START_NOT_STICKY;

        DownloadStore.Item current = store.get(id);
        if (current == null || DownloadStore.STATUS_CANCELLED.equals(current.status)) {
            deletePartialFiles(id);
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }

        final Task task = ACTION_RESUME.equals(action) ? taskFromStore(id) : Task.fromIntent(intent);
        if (task == null || task.id >= 0L || task.url.length() == 0) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        try {
            startForegroundCompat(buildNotification(task, "正在准备下载", 0, 0L,
                    task.expectedTotalBytes, true, false));
        } catch (RuntimeException error) {
            store.updateAdaptive(task.id, DownloadStore.STATUS_FAILED,
                    "系统不允许启动后台下载", 0L, 0L, 0L, null);
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        final Control control = new Control();
        if (CONTROLS.putIfAbsent(Long.valueOf(task.id), control) != null) return START_NOT_STICKY;
        activeTasks.incrementAndGet();
        try {
            TASK_EXECUTOR.execute(new Runnable() {
                @Override public void run() {
                    runTask(task, control);
                    if (activeTasks.decrementAndGet() <= 0) {
                        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
                        else stopForeground(true);
                        stopSelf();
                    }
                }
            });
        } catch (RejectedExecutionException overloaded) {
            CONTROLS.remove(Long.valueOf(task.id));
            int remaining = activeTasks.decrementAndGet();
            store.updateAdaptive(task.id, DownloadStore.STATUS_FAILED,
                    "下载队列已满，请稍后重试", 0L, 0L, 0L, null);
            notifyTask(task, "下载队列已满，请稍后重试", 0, 0L, 0L, false, true);
            if (remaining <= 0) {
                if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
                else stopForeground(true);
                stopSelfResult(startId);
            }
            return START_NOT_STICKY;
        }
        // If Android kills the foreground service mid-transfer, redeliver the original
        // task intent. The persisted cursor/validator state resumes without duplicating bytes.
        return START_REDELIVER_INTENT;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level == ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            for (Control control : CONTROLS.values()) control.pause("系统内存紧张，已安全暂停");
        }
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        for (Control control : CONTROLS.values()) control.pause("系统内存不足，已安全暂停");
    }

    /** Android 15+ limits dataSync foreground services to a shared six-hour budget. */
    @Override public void onTimeout(int startId, int fgsType) {
        for (Control control : CONTROLS.values()) {
            control.pause("系统已达到后台下载时限，请返回应用后继续");
        }
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf(startId);
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else startForeground(FOREGROUND_ID, notification);
    }

    private Task taskFromStore(long id) {
        DownloadStore.Item item = store.get(id);
        if (item == null || !item.isAdaptive()) return null;
        return Task.fromStore(this, item);
    }

    private void markPaused(long id, String reason) {
        DownloadStore.Item item = store.get(id);
        if (item == null || !item.isAdaptive()) return;
        // Notification actions can outlive the task that created them. A late pause must
        // never turn a completed/cancelled record back into an active-looking state.
        if (item.isTerminal()) {
            notifyTask(Task.fromStore(this, item), item.reason, percent(item.downloadedBytes, item.totalBytes),
                    item.downloadedBytes, item.totalBytes, false, false);
            return;
        }
        store.updateAdaptiveTelemetry(id, DownloadStore.STATUS_PAUSED, reason,
                item.downloadedBytes, item.totalBytes, 0L, null, item.etaSeconds, item.retryCount);
        notifyTask(Task.fromStore(this, item), reason, percent(item.downloadedBytes, item.totalBytes),
                item.downloadedBytes, item.totalBytes, false, true);
    }

    private void runTask(Task task, Control control) {
        File temp = partialFile(task.id);
        File stateFile = stateFile(task.id);
        Locks locks = null;
        Uri publishedUri = null;
        boolean completed = false;
        boolean cancelled = false;
        try {
            // START_REDELIVER_INTENT can replay an original download intent after
            // completion telemetry was persisted but before the worker reached its
            // cleanup path. Publishing again would create a duplicate visible file.
            DownloadStore.Item persisted = store.get(task.id);
            if (persisted == null || DownloadStore.STATUS_CANCELLED.equals(persisted.status)) {
                cancelled = true;
                return;
            }
            if (DownloadStore.STATUS_COMPLETED.equals(persisted.status)) {
                completed = true;
                return;
            }
            control.check();
            DownloadState rollbackState = DownloadState.load(stateFile);
            if (rollbackState != null && rollbackState.publishRollback) {
                if (!rollbackPublished(task.id, rollbackState, stateFile, null))
                    throw new IllegalStateException("下载文件夹暂时无法清理未完成文件，请恢复文件夹访问后重试");
                persisted = store.get(task.id);
            }
            if (persisted != null && persisted.isAdaptive() && value(persisted.localUri).length() > 0 &&
                    recoverPublished(task, persisted, temp, stateFile, control)) return;
            if (task.expectedTotalBytes > MAX_DOWNLOAD_BYTES)
                throw new IllegalArgumentException("下载文件超过 4 GB 安全上限");
            DownloadState publishedState = DownloadState.load(stateFile);
            if (publishedState != null && publishedState.publishPending) {
                if (value(publishedState.publishUri).length() > 0 &&
                        recoverPublishedState(task, publishedState, temp, stateFile, control)) return;
                clearPublishingState(publishedState, stateFile);
            }
            waitForConstraints(task, control);
            control.check();

            DownloadState state = DownloadState.load(stateFile);
            boolean incompatible = state != null && (!task.url.equals(state.originalUrl) ||
                    state.totalBytes > MAX_DOWNLOAD_BYTES || state.downloaded() > MAX_DOWNLOAD_BYTES ||
                    state.downloaded() > temp.length());
            if (state == null || incompatible || !temp.exists()) {
                deleteQuietly(temp);
                deleteStateFile(stateFile);
                state = DownloadState.create(task);
                preparePartialFile(temp, state);
                state.persist(stateFile, true);
            }

            setCurrentThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            locks = acquireLocks();

            DownloadStore.Item knownItem = store.get(task.id);
            long knownTotal = state.totalBytes > 0L ? state.totalBytes : Math.max(task.expectedTotalBytes,
                    knownItem == null ? 0L : knownItem.totalBytes);
            final ProgressTracker tracker = new ProgressTracker(task, control, locks, knownTotal, state.downloaded());
            tracker.reportState(DownloadStore.STATUS_DOWNLOADING, "Median 单连接", state, null, true);

            boolean dataComplete = state.totalBytes > 0L && state.downloaded() == state.totalBytes;
            if (!dataComplete) downloadSingle(task, state, temp, stateFile, control, tracker);
            control.check();
            DownloadFileTypes.Metadata completedMetadata =
                    new DownloadFileTypes.Metadata(task.filename, task.mime);
            if (DownloadFileTypes.correctCompletedApk(temp, completedMetadata)) {
                task.filename = completedMetadata.filename;
                task.mime = completedMetadata.mime;
                store.updateMetadata(task.id, task.filename, task.mime);
            }
            locks.renewIfDue(SystemClock.elapsedRealtime());
            state.publishPending = true;
            state.publishUri = "";
            state.persist(stateFile, true);
            publishedUri = publish(task, temp, control, state, stateFile);
            final Uri uri = publishedUri;
            final DownloadState completeState = state;
            // This commit shares the cancel/pause monitor. Whichever action gets
            // it first wins, including the gap after the publisher's final check.
            control.complete(new Runnable() {
                @Override public void run() {
                    tracker.forceReport(DownloadStore.STATUS_COMPLETED, "已完成", completeState, uri.toString());
                }
            });
            completed = true;
            clearPublishingState(state, stateFile);
            notifyTask(task, "已完成", 100, tracker.downloaded, tracker.totalBytes, false, false);
        } catch (Paused paused) {
            DownloadState state = DownloadState.load(stateFile);
            rollbackPublished(task.id, state, stateFile, publishedUri);
            DownloadStore.Item current = store.get(task.id);
            long downloaded = state == null ? (current == null ? 0L : current.downloadedBytes) : state.downloaded();
            long total = DownloadCenterPolicy.resolvedTotal(current == null ? 0L : current.totalBytes,
                    state == null ? 0L : state.totalBytes);
            if (state != null) state.persist(stateFile, true);
            synchronized (control) {
                if (control.cancelled.get()) {
                    cancelled = true;
                    store.updateAdaptive(task.id, DownloadStore.STATUS_CANCELLED, "已取消", 0L, total, 0L, null);
                    notifyTask(task, "已取消", 0, 0L, total, false, false);
                } else {
                    store.updateAdaptiveTelemetry(task.id, DownloadStore.STATUS_PAUSED, paused.reason,
                            downloaded, total, 0L, null, 0L, current == null ? 0 : current.retryCount);
                    notifyTask(task, paused.reason, percent(downloaded, total), downloaded, total, false, true);
                }
            }
        } catch (Cancelled ignored) {
            cancelled = true;
            rollbackPublished(task.id, DownloadState.load(stateFile), stateFile, publishedUri);
            DownloadStore.Item current = store.get(task.id);
            long total = current == null ? 0L : current.totalBytes;
            store.updateAdaptive(task.id, DownloadStore.STATUS_CANCELLED, "已取消", 0L, total, 0L, null);
            notifyTask(task, "已取消", 0, 0L, total, false, false);
        } catch (Throwable error) {
            if (completed || control.completed) return;
            String reason = ErrorText.message(error, 140);
            DownloadState state = DownloadState.load(stateFile);
            rollbackPublished(task.id, state, stateFile, publishedUri);
            if (state != null) state.persist(stateFile, true);
            DownloadStore.Item current = store.get(task.id);
            long downloaded = state == null ? (current == null ? 0L : current.downloadedBytes) : state.downloaded();
            long total = DownloadCenterPolicy.resolvedTotal(current == null ? 0L : current.totalBytes,
                    state == null ? 0L : state.totalBytes);
            int retries = current == null ? 1 : current.retryCount + 1;
            synchronized (control) {
                if (control.cancelled.get()) {
                    cancelled = true;
                    store.updateAdaptive(task.id, DownloadStore.STATUS_CANCELLED, "已取消", 0L, total, 0L, null);
                    notifyTask(task, "已取消", 0, 0L, total, false, false);
                } else if (control.paused.get()) {
                    store.updateAdaptiveTelemetry(task.id, DownloadStore.STATUS_PAUSED, control.pauseReason,
                            downloaded, total, 0L, null, 0L, current == null ? 0 : current.retryCount);
                    notifyTask(task, control.pauseReason, percent(downloaded, total), downloaded, total, false, true);
                } else {
                    store.updateAdaptiveTelemetry(task.id, DownloadStore.STATUS_FAILED, reason, downloaded, total, 0L,
                            null, 0L, retries);
                    notifyTask(task, "失败 · " + reason, percent(downloaded, total), downloaded, total, false, true);
                }
            }
        } finally {
            DownloadStore.Item finalCancelled = null;
            synchronized (control) {
                // A cancel can arrive after a paused/failed catch wrote its final
                // status. Retire the Control and settle that last action together;
                // stale lookups then use the inactive-task cancellation path.
                cancelled = cancelled || control.cancelled.get();
                if (cancelled && !completed && !control.completed) {
                    finalCancelled = store.get(task.id);
                    if (finalCancelled != null) store.updateAdaptive(task.id, DownloadStore.STATUS_CANCELLED,
                            "已取消", 0L, finalCancelled.totalBytes, 0L, null);
                }
                CONTROLS.remove(Long.valueOf(task.id), control);
            }
            if (locks != null) locks.release();
            if (completed || control.completed) {
                deleteQuietly(temp);
                deleteStateFile(stateFile);
            } else if (cancelled) {
                deletePartialFiles(task.id);
                if (finalCancelled != null) notifyTask(task, "已取消", 0, 0L,
                        finalCancelled.totalBytes, false, false);
            }
        }
    }

    private void waitForConstraints(Task task, Control control) throws Cancelled, Paused {
        while (true) {
            control.check();
            String reason = constraintReason(task);
            if (reason.length() == 0) return;
            DownloadStore.Item current = store.get(task.id);
            long downloaded = current == null ? 0L : current.downloadedBytes;
            long total = current == null ? 0L : current.totalBytes;
            store.updateAdaptive(task.id, DownloadStore.STATUS_WAITING, reason, downloaded, total, 0L, null);
            notifyTask(task, reason, percent(downloaded, total), downloaded, total, true, false);
            for (int i = 0; i < 10; i++) {
                control.check();
                SystemClock.sleep(1000L);
            }
        }
    }

    private String constraintReason(Task task) {
        try {
            ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (manager == null) return "等待网络";
            Network network = manager.getActiveNetwork();
            NetworkCapabilities caps = manager.getNetworkCapabilities(network);
            if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return "等待网络";
            if (task.wifiOnly && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) return "等待非计费网络";
            if (!task.allowRoaming) {
                if (Build.VERSION.SDK_INT >= 28) {
                    if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)) return "等待非漫游网络";
                } else {
                    NetworkInfo info = manager.getActiveNetworkInfo();
                    if (info != null && info.isRoaming()) return "等待非漫游网络";
                }
            }
            if (task.chargingOnly) {
                BatteryManager battery = (BatteryManager) getSystemService(BATTERY_SERVICE);
                if (battery == null || !battery.isCharging()) return "等待充电";
            }
            return "";
        } catch (RuntimeException error) { return "等待网络"; }
    }

    private void applyResponseMetadata(Task task, HttpURLConnection connection) {
        task.filename = DownloadFileTypes.resolveName(connection.getURL().toString(),
                value(connection.getHeaderField("Content-Disposition")), task.mime,
                value(connection.getContentType()), task.filename);
        task.mime = DownloadFileTypes.resolveMime(task.filename, task.mime,
                value(connection.getContentType()));
        store.updateMetadata(task.id, task.filename, task.mime);
    }

    private void preparePartialFile(File temp, DownloadState state) throws Exception {
        File parent = temp.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IllegalStateException("无法创建临时下载目录");
        RandomAccessFile allocator = new RandomAccessFile(temp, "rw");
        try {
            if (state.totalBytes > 0L) allocator.setLength(state.totalBytes);
            else allocator.setLength(0L);
        } finally { allocator.close(); }
    }

    private void downloadSingle(Task task, DownloadState state, File temp, File stateFile,
                                Control control, ProgressTracker tracker) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt <= RETRIES; attempt++) {
            control.check();
            HttpURLConnection connection = null;
            InputStream input = null;
            RandomAccessFile output = null;
            try {
                long cursor = state.cursor;
                String range = cursor > 0L ? "bytes=" + cursor + "-" : null;
                connection = open(task, task.url, range, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS, control);
                int code = connection.getResponseCode();
                boolean restartFull = false;
                if (range != null && code == HttpURLConnection.HTTP_PARTIAL) {
                    long[] contentRange = parseContentRange(connection.getHeaderField("Content-Range"));
                    if (contentRange == null || contentRange[0] != cursor ||
                            (state.totalBytes > 0L && contentRange[2] != state.totalBytes) ||
                            validatorChanged(state.etag, connection.getHeaderField("ETag")) ||
                            validatorChanged(state.lastModified, connection.getHeaderField("Last-Modified")))
                        restartFull = true;
                }
                if (range != null && code != HttpURLConnection.HTTP_PARTIAL && code != HttpURLConnection.HTTP_OK) {
                    if (DownloadRetryPolicy.isRetryableHttp(code)) throw new HttpFailure(code);
                    restartFull = true;
                }
                if (restartFull) {
                    if (code >= 400) closeQuietly(connection.getErrorStream());
                    else closeQuietly(connection.getInputStream());
                    control.detach(connection);
                    connection.disconnect();
                    connection = open(task, task.url, null, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS, control);
                    code = connection.getResponseCode();
                }
                if (range != null && (restartFull || code == HttpURLConnection.HTTP_OK)) {
                    state.cursor = 0L;
                    tracker.reset(0L);
                    cursor = 0L;
                }
                if (code < 200 || code >= 300) throw new HttpFailure(code);
                long[] contentRange = parseContentRange(connection.getHeaderField("Content-Range"));
                long responseTotal = responseTotal(connection, cursor, contentRange);
                if (responseTotal > MAX_DOWNLOAD_BYTES)
                    throw new IllegalArgumentException("下载文件超过 4 GB 安全上限");
                if (responseTotal > 0L) {
                    state.totalBytes = responseTotal;
                    tracker.totalBytes = state.totalBytes;
                }
                state.resolvedUrl = connection.getURL().toString();
                // Some range endpoints omit validators on 206 responses. Do not
                // erase a validator learned from the initial response in that
                // case, otherwise a later resume loses the only identity check.
                String responseEtag = value(connection.getHeaderField("ETag"));
                if (responseEtag.length() > 0) state.etag = responseEtag;
                String responseLastModified = value(connection.getHeaderField("Last-Modified"));
                if (responseLastModified.length() > 0) state.lastModified = responseLastModified;
                applyResponseMetadata(task, connection);
                state.persist(stateFile, true);
                tracker.reportState(DownloadStore.STATUS_DOWNLOADING, "Median 单连接", state, null, true);
                input = connection.getInputStream();
                output = new RandomAccessFile(temp, "rw");
                if (cursor == 0L) output.setLength(state.totalBytes > 0L ? state.totalBytes : 0L);
                else if (state.totalBytes > 0L && output.length() < state.totalBytes) output.setLength(state.totalBytes);
                output.seek(cursor);
                byte[] buffer = new byte[BUFFER_BYTES];
                int read;
                while (true) {
                    long remaining = state.totalBytes > 0L ? state.totalBytes - state.cursor : buffer.length;
                    if (state.totalBytes > 0L && remaining <= 0L) break;
                    int request = (int) Math.min((long) buffer.length, Math.max(1L, remaining));
                    read = input.read(buffer, 0, request);
                    if (read == -1) break;
                    control.check();
                    if (state.downloaded() > MAX_DOWNLOAD_BYTES - read)
                        throw new IllegalArgumentException("下载文件超过 4 GB 安全上限");
                    output.write(buffer, 0, read);
                    state.advance(read);
                    tracker.add(read, state, stateFile);
                }
                if (state.totalBytes <= 0L) {
                    state.totalBytes = state.downloaded();
                    tracker.totalBytes = state.totalBytes;
                }
                if (state.totalBytes > 0L && state.downloaded() != state.totalBytes)
                    throw new EOFException("下载数据不完整");
                state.persist(stateFile, true);
                return;
            } catch (Paused paused) { throw paused;
            } catch (Cancelled cancelled) { throw cancelled;
            } catch (Exception error) {
                last = error;
                control.check();
                tracker.retryCount++;
                if (attempt >= RETRIES || !isRetryable(error)) throw error;
                SystemClock.sleep(400L * (attempt + 1L));
            } finally {
                closeQuietly(input);
                closeQuietly(output);
                if (connection != null) {
                    control.detach(connection);
                    connection.disconnect();
                }
            }
        }
        if (last != null) throw last;
    }

    private Uri publish(Task task, File temp, Control control, DownloadState state,
                        File stateFile) throws Exception {
        control.check();
        Uri tree = DownloadDirectory.selectedTree(this);
        if (tree != null) return publishToSelectedDirectory(task, temp, control, state, stateFile, tree);
        if (Build.VERSION.SDK_INT < 29) return publishToAppDownloads(task, temp, control, state, stateFile);
        try {
            return publishToMediaStore(task, temp, control, state, stateFile);
        } catch (Paused paused) { throw paused;
        } catch (Cancelled cancelled) { throw cancelled;
        } catch (Exception mediaStoreFailure) {
            if (state.publishRollback) throw mediaStoreFailure;
            return publishToAppDownloads(task, temp, control, state, stateFile);
        }
    }

    private Uri publishToSelectedDirectory(Task task, File temp, Control control, DownloadState state,
                                           File stateFile, Uri tree) throws Exception {
        DownloadDirectory.requireWritable(this, tree);
        synchronized (PUBLISH_LOCK) {
            control.check();
            ContentResolver resolver = getContentResolver();
            Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
            Uri uri = DocumentsContract.createDocument(resolver, parent,
                    task.mime.length() == 0 ? "application/octet-stream" : task.mime, task.filename);
            if (uri == null) throw new IllegalStateException("无法在所选下载文件夹创建文件");
            markPublishingState(state, stateFile, uri);
            markPublishing(task, uri);
            try {
                try (InputStream input = new BufferedInputStream(new FileInputStream(temp), 256 * 1024);
                     OutputStream output = selectedDirectoryOutput(resolver, uri)) {
                    byte[] buffer = new byte[256 * 1024];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        control.check();
                        output.write(buffer, 0, read);
                    }
                    output.flush();
                }
                control.check();
                try (Cursor cursor = resolver.query(uri, new String[] { DocumentsContract.Document.COLUMN_DISPLAY_NAME },
                        null, null, null)) {
                    if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                        task.filename = cursor.getString(0);
                        store.updateMetadata(task.id, task.filename, task.mime);
                    }
                } catch (RuntimeException ignored) {}
                control.check();
                return uri;
            } catch (Throwable error) {
                rollbackPublished(task.id, state, stateFile, uri);
                if (error instanceof Paused) throw (Paused) error;
                if (error instanceof Cancelled) throw (Cancelled) error;
                if (error instanceof SecurityException)
                    throw new IllegalStateException("下载文件夹授权已失效，请重新选择文件夹后重试", error);
                if (error instanceof Exception) throw (Exception) error;
                throw new IllegalStateException(ErrorText.message(error, 140));
            }
        }
    }

    private OutputStream selectedDirectoryOutput(ContentResolver resolver, Uri uri) throws Exception {
        OutputStream output = resolver.openOutputStream(uri, "w");
        if (output == null) throw new IllegalStateException("无法写入所选下载文件夹，请重新选择文件夹后重试");
        return new BufferedOutputStream(output, 256 * 1024);
    }

    private Uri publishToMediaStore(Task task, File temp, Control control, DownloadState state,
                                    File stateFile) throws Exception {
        // Also guard the API at its point of use, rather than relying only on publish().
        if (Build.VERSION.SDK_INT < 29)
            return publishToAppDownloads(task, temp, control, state, stateFile);
        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, task.filename);
        values.put(MediaStore.MediaColumns.MIME_TYPE, task.mime.length() == 0 ? "application/octet-stream" : task.mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Median");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("无法创建下载文件");
        markPublishingState(state, stateFile, uri);
        markPublishing(task, uri);
        InputStream input = null;
        OutputStream output = null;
        try {
            input = new BufferedInputStream(new FileInputStream(temp), 256 * 1024);
            OutputStream rawOutput = resolver.openOutputStream(uri, "w");
            if (rawOutput == null) throw new IllegalStateException("无法写入下载目录");
            output = new BufferedOutputStream(rawOutput, 256 * 1024);
            byte[] buffer = new byte[256 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                control.check();
                output.write(buffer, 0, read);
            }
            output.flush();
            output.close();
            output = null;
            input.close();
            input = null;
            control.check();
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            values.put(MediaStore.MediaColumns.SIZE, temp.length());
            if (resolver.update(uri, values, null, null) <= 0)
                throw new IllegalStateException("无法完成下载文件发布");
            control.check();
            return uri;
        } catch (Throwable error) {
            closeQuietly(input);
            input = null;
            closeQuietly(output);
            output = null;
            rollbackPublished(task.id, state, stateFile, uri);
            if (error instanceof Paused) throw (Paused) error;
            if (error instanceof Cancelled) throw (Cancelled) error;
            if (error instanceof Exception) throw (Exception) error;
            throw new IllegalStateException(ErrorText.message(error, 140));
        } finally {
            closeQuietly(input);
            closeQuietly(output);
        }
    }

    private Uri publishToAppDownloads(Task task, File temp, Control control, DownloadState state,
                                      File stateFile) throws Exception {
        synchronized (PUBLISH_LOCK) {
            control.check();
            File root = DownloadContentProvider.root(this);
            if (!root.isDirectory() && !root.mkdirs()) throw new IllegalStateException("无法创建应用下载目录");
            File target = uniquePublishedFile(root, task.filename);
            Uri targetUri = DownloadContentProvider.uriFor(target);
            markPublishingState(state, stateFile, targetUri);
            markPublishing(task, targetUri);
            InputStream input = null;
            OutputStream output = null;
            try {
                input = new BufferedInputStream(new FileInputStream(temp), 256 * 1024);
                output = new BufferedOutputStream(new FileOutputStream(target, false), 256 * 1024);
                byte[] buffer = new byte[256 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    control.check();
                    output.write(buffer, 0, read);
                }
                output.flush();
                output.close();
                output = null;
                input.close();
                input = null;
                control.check();
                return targetUri;
            } catch (Throwable error) {
                closeQuietly(input);
                input = null;
                closeQuietly(output);
                output = null;
                rollbackPublished(task.id, state, stateFile, targetUri);
                if (error instanceof Paused) throw (Paused) error;
                if (error instanceof Cancelled) throw (Cancelled) error;
                if (error instanceof Exception) throw (Exception) error;
                throw new IllegalStateException(ErrorText.message(error, 140));
            } finally {
                closeQuietly(input);
                closeQuietly(output);
            }
        }
    }

    private File uniquePublishedFile(File root, String requestedName) {
        String name = DownloadFileTypes.sanitize(requestedName);
        if (name.length() == 0) name = "download.bin";
        File candidate = new File(root, name);
        if (!candidate.exists()) return candidate;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i <= 999; i++) {
            candidate = new File(root, base + " (" + i + ")" + extension);
            if (!candidate.exists()) return candidate;
        }
        return new File(root, base + '-' + System.currentTimeMillis() + extension);
    }

    /** Persists the destination before bytes are published so a process restart can recover it. */
    private void markPublishing(Task task, Uri uri) {
        if (task == null || uri == null) return;
        DownloadStore.Item current = store.get(task.id);
        if (current == null || !current.isAdaptive()) return;
        String status = current.status == null || current.status.length() == 0 ?
                DownloadStore.STATUS_DOWNLOADING : current.status;
        store.updateAdaptive(task.id, status, "正在发布", current.downloadedBytes, current.totalBytes,
                current.bytesPerSecond, uri.toString());
    }

    private void markPublishingState(DownloadState state, File stateFile, Uri uri) {
        if (state == null || stateFile == null || uri == null) return;
        state.publishPending = true;
        state.publishRollback = false;
        state.publishUri = uri.toString();
        state.persist(stateFile, true);
    }

    private void clearPublishingState(DownloadState state, File stateFile) {
        if (state == null || stateFile == null) return;
        state.publishPending = false;
        state.publishRollback = false;
        state.publishUri = "";
        state.persist(stateFile, true);
    }

    /** Completes or rolls back a destination left behind by a killed publisher. */
    private boolean recoverPublished(final Task task, final DownloadStore.Item persisted,
                                     File temp, File stateFile, Control control) throws Exception {
        Uri uri;
        try { uri = Uri.parse(value(persisted.localUri)); }
        catch (RuntimeException ignored) { uri = null; }
        if (uri == null || !hasCompletePublishedData(uri,
                Math.max(persisted.totalBytes, persisted.downloadedBytes))) {
            if (!rollbackPublished(task.id, DownloadState.load(stateFile), stateFile, uri))
                throw new IllegalStateException("下载文件夹暂时无法清理未完成文件，请恢复文件夹访问后重试");
            return false;
        }
        long size = persisted.totalBytes;
        try {
            android.os.ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(uri, "r");
            if (descriptor != null) {
                long stat = descriptor.getStatSize();
                if (stat > size) size = stat;
                descriptor.close();
            }
        } catch (Exception ignored) {}
        final long recoveredSize = size;
        final Uri recoveredUri = uri;
        control.complete(new Runnable() {
            @Override public void run() {
                store.updateAdaptive(task.id, DownloadStore.STATUS_COMPLETED, "已完成",
                        Math.max(persisted.downloadedBytes, recoveredSize), Math.max(persisted.totalBytes, recoveredSize),
                        persisted.bytesPerSecond, recoveredUri.toString());
            }
        });
        deleteQuietly(temp);
        deleteStateFile(stateFile);
        return true;
    }

    private boolean recoverPublishedState(final Task task, DownloadState state,
                                          File temp, File stateFile, Control control) throws Exception {
        Uri uri;
        try { uri = Uri.parse(value(state.publishUri)); }
        catch (RuntimeException ignored) { uri = null; }
        final DownloadStore.Item current = store.get(task.id);
        if (current == null || !current.isAdaptive()) {
            deletePartialFiles(task.id);
            return true;
        }
        long expected = Math.max(state.totalBytes, current == null ? 0L :
                Math.max(current.totalBytes, current.downloadedBytes));
        if (uri == null || !hasCompletePublishedData(uri, expected)) {
            if (!rollbackPublished(task.id, state, stateFile, uri))
                throw new IllegalStateException("下载文件夹暂时无法清理未完成文件，请恢复文件夹访问后重试");
            return false;
        }
        long size = expected;
        try {
            android.os.ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(uri, "r");
            if (descriptor != null) {
                long stat = descriptor.getStatSize();
                if (stat > size) size = stat;
                descriptor.close();
            }
        } catch (Exception ignored) {}
        final long recoveredSize = size;
        final long expectedSize = expected;
        final Uri recoveredUri = uri;
        control.complete(new Runnable() {
            @Override public void run() {
                store.updateAdaptive(task.id, DownloadStore.STATUS_COMPLETED, "已完成",
                        recoveredSize, Math.max(expectedSize, recoveredSize), current.bytesPerSecond,
                        recoveredUri.toString());
            }
        });
        deleteQuietly(temp);
        deleteStateFile(stateFile);
        return true;
    }

    private boolean hasCompletePublishedData(Uri uri, long expectedBytes) {
        if (uri == null) return false;
        if (DownloadContentProvider.AUTHORITY.equals(uri.getAuthority())) {
            String name = uri.getLastPathSegment();
            if (name == null || name.length() == 0) return false;
            try {
                File root = DownloadContentProvider.root(this).getCanonicalFile();
                File file = new File(root, name).getCanonicalFile();
                return root.equals(file.getParentFile()) && file.isFile() &&
                        (expectedBytes <= 0L || file.length() >= expectedBytes);
            } catch (Exception ignored) { return false; }
        }
        if (DownloadDirectory.isTreeDocument(uri)) {
            long size = -1L;
            try (Cursor cursor = getContentResolver().query(uri,
                    new String[] { DocumentsContract.Document.COLUMN_SIZE }, null, null, null)) {
                if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) size = cursor.getLong(0);
            } catch (Exception ignored) {}
            if (size < 0L) try (android.os.ParcelFileDescriptor descriptor =
                    getContentResolver().openFileDescriptor(uri, "r")) {
                if (descriptor != null) size = descriptor.getStatSize();
            } catch (Exception ignored) {}
            // A cloud provider may not expose size; count only during crash recovery.
            if (size < 0L) try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) return false;
                byte[] buffer = new byte[128 * 1024];
                size = 0L;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    size += read;
                    if (size > MAX_DOWNLOAD_BYTES) return false;
                }
            } catch (Exception ignored) { return false; }
            return size >= 0L && (expectedBytes <= 0L || size == expectedBytes);
        }
        if (!"media".equals(uri.getAuthority())) return false;
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri,
                    new String[] { MediaStore.MediaColumns.IS_PENDING, MediaStore.MediaColumns.SIZE },
                    null, null, null);
            if (cursor == null || !cursor.moveToFirst()) return false;
            int pending = cursor.getInt(0);
            long size = cursor.isNull(1) ? 0L : Math.max(0L, cursor.getLong(1));
            return pending == 0 && (expectedBytes <= 0L || size >= expectedBytes);
        } catch (Exception ignored) { return false;
        } finally { if (cursor != null) cursor.close(); }
    }

    /** Retains the destination until deletion succeeds, so provider outages can be retried. */
    private boolean rollbackPublished(long taskId, DownloadState state, File stateFile, Uri explicitUri) {
        Uri uri = explicitUri;
        DownloadStore.Item current = store.get(taskId);
        if (uri == null && state != null && value(state.publishUri).length() > 0)
            uri = Uri.parse(state.publishUri);
        if (uri == null && current != null && value(current.localUri).length() > 0)
            uri = Uri.parse(current.localUri);
        if (uri == null) {
            clearPublishingState(state, stateFile);
            return true;
        }
        if (state == null) {
            state = current == null ? new DownloadState() : DownloadState.create(Task.fromStore(this, current));
        }
        state.publishPending = true;
        state.publishRollback = true;
        state.publishUri = uri.toString();
        state.persist(stateFile, true);
        if (!deletePublishedUri(uri)) return false;
        clearPublishingState(state, stateFile);
        store.clearAdaptiveLocalUri(taskId, uri.toString());
        return true;
    }

    private boolean deletePublishedUri(Uri uri) {
        if (uri == null) return true;
        if (DownloadContentProvider.AUTHORITY.equals(uri.getAuthority())) {
            String name = uri.getLastPathSegment();
            if (name == null || name.length() == 0) return false;
            try {
                File root = DownloadContentProvider.root(this).getCanonicalFile();
                File file = new File(root, name).getCanonicalFile();
                return root.equals(file.getParentFile()) && (!file.exists() || file.delete());
            } catch (Exception ignored) { return false; }
        } else if (DownloadDirectory.isTreeDocument(uri)) {
            try {
                if (DocumentsContract.deleteDocument(getContentResolver(), uri)) return true;
                return isPublishedUriAbsent(uri);
            } catch (Exception ignored) { return false; }
        } else if ("media".equals(uri.getAuthority())) {
            try { return getContentResolver().delete(uri, null, null) > 0 || isPublishedUriAbsent(uri); }
            catch (RuntimeException ignored) { return false; }
        }
        return false;
    }

    private boolean isPublishedUriAbsent(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            return cursor != null && !cursor.moveToFirst();
        } catch (Exception ignored) { return false; }
    }

    private HttpURLConnection open(Task task, String url, String range, int connectTimeout, int readTimeout,
                                   Control control) throws Exception {
        URL initial = NetworkSecurity.parseHttpUrl(url);
        URL original = NetworkSecurity.parseHttpUrl(task.url);
        URL current = initial;
        for (int redirects = 0; redirects <= NetworkSecurity.MAX_REDIRECTS; redirects++) {
            if (task.publicOnly && NetworkSecurity.isLocalOrPrivateHost(NetworkSecurity.normalizedHost(current)))
                throw new IllegalArgumentException("本地或私有网络下载目标已拒绝");
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            control.attach(connection);
            control.check();
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(connectTimeout);
                connection.setReadTimeout(readTimeout);
                connection.setUseCaches(false);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept-Encoding", "identity");
                if (task.userAgent.length() > 0) connection.setRequestProperty("User-Agent", task.userAgent);
                if (range != null) connection.setRequestProperty("Range", range);
                boolean sameOrigin = NetworkSecurity.sameOrigin(original, current);
                String cookie = "";
                try { cookie = value(CookieManager.getInstance().getCookie(current.toString())); }
                catch (RuntimeException ignored) {}
                if (sameOrigin && cookie.length() == 0) cookie = task.cookie;
                if (cookie.length() > 0) connection.setRequestProperty("Cookie", cookie);
                for (Map.Entry<String, String> header : task.headers.entrySet()) {
                    if (!NetworkSecurity.validHeader(header.getKey(), header.getValue()) ||
                            NetworkSecurity.isForbiddenRequestHeader(header.getKey())) continue;
                    if (!sameOrigin && NetworkSecurity.isCredentialHeader(header.getKey())) continue;
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
                // DNS is resolved by HttpURLConnection when connect() runs. Recheck
                // immediately after that resolution so a hostname that was rebound to a
                // private address between the preflight and socket setup is rejected.
                connection.connect();
                if (task.publicOnly && NetworkSecurity.isLocalOrPrivateHost(NetworkSecurity.normalizedHost(current)))
                    throw new IllegalArgumentException("本地或私有网络下载目标已拒绝");
                int status = connection.getResponseCode();
                storeResponseCookies(current, connection);
                if (!NetworkSecurity.isRedirect(status)) return connection;
                if (redirects == NetworkSecurity.MAX_REDIRECTS)
                    throw new IllegalStateException("重定向次数过多");
                URL next = NetworkSecurity.resolveRedirect(current, connection.getHeaderField("Location"), false);
                control.detach(connection);
                connection.disconnect();
                current = next;
            } catch (Exception error) {
                control.detach(connection);
                connection.disconnect();
                throw error;
            }
        }
        throw new IllegalStateException("重定向次数过多");
    }

    private static void storeResponseCookies(URL url, HttpURLConnection connection) {
        if (url == null || connection == null) return;
        try {
            CookieManager manager = CookieManager.getInstance();
            for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
                String name = header.getKey();
                if (name == null || !("set-cookie".equalsIgnoreCase(name) ||
                        "set-cookie2".equalsIgnoreCase(name)) || header.getValue() == null) continue;
                for (String cookie : header.getValue())
                    if (cookie != null && cookie.length() > 0) manager.setCookie(url.toString(), cookie);
            }
        } catch (RuntimeException ignored) {}
    }

    private File partialDirectory() {
        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) dir = new File(getCacheDir(), "downloads");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static String safeTaskId(long id) { return id == Long.MIN_VALUE ? "min" : Long.toString(Math.abs(id)); }
    private File partialFile(long id) { return new File(partialDirectory(), "median-" + safeTaskId(id) + ".part"); }
    private File stateFile(long id) { return new File(partialDirectory(), "median-" + safeTaskId(id) + ".state"); }

    private void deletePartialFiles(long id) {
        synchronized (CLEANUP_LOCK) {
            // Retirement and a stale cancellation can both request cleanup. Avoid
            // concurrent AtomicFile writes that could lose a retained rollback URI.
            File stateFile = stateFile(id);
            DownloadState pending = DownloadState.load(stateFile);
            boolean cleaned = rollbackPublished(id, pending, stateFile, null);
            deleteQuietly(partialFile(id));
            if (cleaned) deleteStateFile(stateFile);
        }
    }

    private static void deleteStateFile(File file) {
        if (file == null) return;
        try { new AtomicFile(file).delete(); } catch (RuntimeException ignored) {}
        deleteQuietly(new File(file.getAbsolutePath() + ".tmp"));
    }

    private Locks acquireLocks() {
        Locks locks = new Locks();
        try {
            PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
            if (power != null) {
                locks.wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Median:AdaptiveDownload");
                locks.wakeLock.setReferenceCounted(false);
                locks.renewIfDue(SystemClock.elapsedRealtime());
            }
        } catch (RuntimeException ignored) {}
        return locks;
    }

    private void notifyTask(Task task, String text, int percent, long downloaded, long total,
                            boolean ongoing, boolean resumable) {
        if (notificationManager == null || task == null) return;
        notificationManager.notify(notificationId(task.id),
                buildNotification(task, text, percent, downloaded, total, ongoing, resumable));
    }

    private Notification buildNotification(Task task, String text, int percent, long downloaded, long total,
                                           boolean ongoing, boolean resumable) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        Intent open = new Intent(this, DownloadCenterActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, notificationId(task.id), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        builder.setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(task.filename)
                .setContentText(text)
                .setContentIntent(content)
                .setOnlyAlertOnce(true)
                .setOngoing(ongoing)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setVisibility(Notification.VISIBILITY_PRIVATE);
        if (total > 0L) builder.setProgress(100, Math.max(0, Math.min(100, percent)), false);
        else if (ongoing) builder.setProgress(0, 0, true);
        if (ongoing) {
            builder.addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause, "暂停",
                    serviceAction(task.id, ACTION_PAUSE, 1)).build());
            builder.addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "取消",
                    serviceAction(task.id, ACTION_CANCEL, 2)).build());
        } else if (resumable) {
            builder.addAction(new Notification.Action.Builder(android.R.drawable.ic_media_play, "继续",
                    serviceAction(task.id, ACTION_RESUME, 3)).build());
            builder.addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "取消",
                    serviceAction(task.id, ACTION_CANCEL, 4)).build());
            builder.setAutoCancel(false);
        } else builder.setAutoCancel(true);
        return builder.build();
    }

    private PendingIntent serviceAction(long id, String action, int salt) {
        Intent intent = new Intent(this, AdaptiveDownloadService.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_ID, id);
        return PendingIntent.getService(this, notificationId(id) * 10 + salt, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int notificationId(long id) { return 7200 + (int) Math.abs(id % 100000L); }
    private static int percent(long downloaded, long total) {
        return DownloadCenterPolicy.progressPermille(downloaded, total) / 10;
    }

    private static long responseTotal(HttpURLConnection connection, long cursor, long[] contentRange) {
        if (contentRange != null && contentRange[2] > 0L) return contentRange[2];
        long content = connection == null ? -1L : connection.getContentLengthLong();
        if (content > 0L && cursor >= 0L && cursor <= Long.MAX_VALUE - content) return cursor + content;
        String[] totalHeaders = new String[] {
                "X-File-Size", "X-Content-Length", "X-Original-Content-Length",
                "X-Goog-Stored-Content-Length"
        };
        for (String header : totalHeaders) {
            long value = positiveLong(connection == null ? null : connection.getHeaderField(header));
            if (value >= cursor && value > 0L) return value;
        }
        return 0L;
    }

    private static long positiveLong(String value) {
        if (value == null) return 0L;
        try { return Math.max(0L, Long.parseLong(value.trim())); }
        catch (NumberFormatException ignored) { return 0L; }
    }

    private static long[] parseContentRange(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (!normalized.regionMatches(true, 0, "bytes ", 0, 6)) return null;
        int dash = normalized.indexOf('-', 6);
        int slash = normalized.indexOf('/', dash + 1);
        if (dash <= 6 || slash <= dash + 1 || slash + 1 >= normalized.length()) return null;
        try {
            long start = Long.parseLong(normalized.substring(6, dash).trim());
            long end = Long.parseLong(normalized.substring(dash + 1, slash).trim());
            long total = Long.parseLong(normalized.substring(slash + 1).trim());
            if (start < 0L || end < start || total <= end) return null;
            return new long[] { start, end, total };
        } catch (NumberFormatException ignored) { return null; }
    }

    private static void setCurrentThreadPriority(int priority) {
        try { Process.setThreadPriority(Process.myTid(), priority); }
        catch (RuntimeException ignored) {}
    }

    private static boolean isRetryable(Exception error) {
        return !(error instanceof HttpFailure) ||
                DownloadRetryPolicy.isRetryableHttp(((HttpFailure) error).status);
    }

    private static boolean validatorChanged(String stored, String received) {
        String left = value(stored);
        String right = value(received);
        return left.length() > 0 && right.length() > 0 && !left.equals(right);
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) try { file.delete(); } catch (RuntimeException ignored) {}
    }

    private static String value(String value) { return value == null ? "" : value; }

    private final class ProgressTracker {
        private static final long REPORT_INTERVAL_MS = 750L;
        final Task task;
        final Control control;
        final Locks locks;
        long downloaded;
        int retryCount;
        volatile long totalBytes;
        private long lastReportAt = SystemClock.elapsedRealtime();
        private long lastReportBytes;
        private long nextReportAt = lastReportAt + REPORT_INTERVAL_MS;
        private long nextStatePersistAt = lastReportAt + 2000L;

        ProgressTracker(Task task, Control control, Locks locks, long totalBytes, long initialDownloaded) {
            this.task = task;
            this.control = control;
            this.locks = locks;
            this.totalBytes = Math.max(0L, totalBytes);
            this.downloaded = Math.max(0L, initialDownloaded);
            this.lastReportBytes = this.downloaded;
        }

        void reset(long bytes) {
            downloaded = Math.max(0L, bytes);
            lastReportBytes = downloaded;
            lastReportAt = SystemClock.elapsedRealtime();
        }

        void add(int count, DownloadState state, File stateFile) throws Cancelled, Paused {
            control.check();
            downloaded += count;
            long now = SystemClock.elapsedRealtime();
            if (locks != null) locks.renewIfDue(now);
            if (now >= nextStatePersistAt) {
                nextStatePersistAt = now + 2000L;
                state.persist(stateFile, false);
            }
            if (now >= nextReportAt) {
                nextReportAt = now + REPORT_INTERVAL_MS;
                reportState(DownloadStore.STATUS_DOWNLOADING, "Median 单连接", state, null, false);
            }
        }

        synchronized void reportState(String status, String reason, DownloadState state, String uri, boolean force) {
            long now = SystemClock.elapsedRealtime();
            if (!force && now - lastReportAt < REPORT_INTERVAL_MS) return;
            long current = downloaded;
            long elapsed = Math.max(1L, now - lastReportAt);
            long speed = DownloadStore.STATUS_DOWNLOADING.equals(status) ?
                    Math.max(0L, (current - lastReportBytes) * 1000L / elapsed) : 0L;
            long eta = speed > 0L && totalBytes > current ? (totalBytes - current) / speed : 0L;
            store.updateAdaptiveTelemetry(task.id, status, reason, current, totalBytes, speed, uri,
                    eta, retryCount);
            if (DownloadStore.STATUS_DOWNLOADING.equals(status)) {
                int pct = percent(current, totalBytes);
                String progressText = totalBytes > 0L ? pct + "%" :
                        (current > 0L ? "已下载 " + humanBytes(current) + " · 大小未知" : "正在获取大小");
                notifyTask(task, "下载中 · " + progressText + " · " + humanSpeed(speed), pct,
                        current, totalBytes, true, false);
            }
            lastReportAt = now;
            lastReportBytes = current;
            nextReportAt = now + REPORT_INTERVAL_MS;
        }

        void forceReport(String status, String reason, DownloadState state, String uri) {
            reportState(status, reason, state, uri, true);
        }
    }

    private static final class Task {
        long id;
        String url;
        String filename;
        String mime;
        String userAgent;
        String cookie;
        boolean wifiOnly;
        boolean allowRoaming;
        boolean chargingOnly;
        boolean publicOnly;
        long expectedTotalBytes;
        final Map<String, String> headers = new HashMap<String, String>();

        static Task fromIntent(Intent intent) {
            Task task = new Task();
            task.id = intent.getLongExtra(EXTRA_ID, 0L);
            task.url = value(intent.getStringExtra(EXTRA_URL));
            task.filename = value(intent.getStringExtra(EXTRA_NAME));
            task.mime = value(intent.getStringExtra(EXTRA_MIME));
            task.userAgent = value(intent.getStringExtra(EXTRA_USER_AGENT));
            task.cookie = value(intent.getStringExtra(EXTRA_COOKIE));
            task.wifiOnly = intent.getBooleanExtra(EXTRA_WIFI_ONLY, false);
            task.allowRoaming = intent.getBooleanExtra(EXTRA_ALLOW_ROAMING, false);
            task.chargingOnly = intent.getBooleanExtra(EXTRA_CHARGING_ONLY, false);
            task.publicOnly = intent.getBooleanExtra(EXTRA_PUBLIC_ONLY, false);
            task.expectedTotalBytes = Math.max(0L, intent.getLongExtra(EXTRA_TOTAL_BYTES, 0L));
            parseHeaders(value(intent.getStringExtra(EXTRA_HEADERS)), task.headers);
            return task;
        }

        static Task fromStore(Context context, DownloadStore.Item item) {
            Task task = new Task();
            task.id = item.id;
            task.url = value(item.url);
            task.filename = value(item.filename);
            task.mime = value(item.mime);
            task.userAgent = value(item.userAgent);
            try {
                String cookie = CookieManager.getInstance().getCookie(task.url);
                task.cookie = value(cookie);
            } catch (RuntimeException ignored) { task.cookie = ""; }
            task.wifiOnly = item.wifiOnly;
            task.allowRoaming = item.allowRoaming;
            task.chargingOnly = item.chargingOnly;
            task.publicOnly = item.publicOnly;
            task.expectedTotalBytes = Math.max(0L, item.totalBytes);
            parseHeaders(item.headersJson, task.headers);
            return task;
        }

        private static void parseHeaders(String json, Map<String, String> target) {
            try {
                JSONObject object = new JSONObject(value(json));
                Iterator<String> keys = object.keys();
                while (keys.hasNext()) {
                    String name = keys.next();
                    String headerValue = object.optString(name, "");
                    if (!NetworkSecurity.validHeader(name, headerValue) || NetworkSecurity.isForbiddenRequestHeader(name) ||
                            NetworkSecurity.isCredentialHeader(name) || "user-agent".equalsIgnoreCase(name)) continue;
                    target.put(name, headerValue);
                }
            } catch (Exception ignored) {}
        }
    }

    private static final class Control {
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final AtomicBoolean paused = new AtomicBoolean(false);
        volatile String pauseReason = "已暂停";
        volatile HttpURLConnection connection;
        volatile boolean completed;
        synchronized boolean cancel() {
            if (completed) return false;
            cancelled.set(true);
            disconnect();
            return true;
        }
        synchronized boolean pause(String reason) {
            if (completed || cancelled.get()) return false;
            pauseReason = value(reason);
            paused.set(true);
            disconnect();
            return true;
        }
        synchronized void complete(Runnable commit) throws Cancelled, Paused {
            check();
            commit.run();
            completed = true;
        }
        void attach(HttpURLConnection value) { connection = value; }
        void detach(HttpURLConnection value) { if (connection == value) connection = null; }
        void disconnect() {
            HttpURLConnection active = connection;
            if (active != null) try { active.disconnect(); } catch (RuntimeException ignored) {}
        }
        void check() throws Cancelled, Paused {
            if (cancelled.get()) throw new Cancelled();
            if (paused.get()) throw new Paused(pauseReason);
            if (Thread.currentThread().isInterrupted()) {
                if (paused.get()) throw new Paused(pauseReason);
                throw new Cancelled();
            }
        }
    }

    private static final class Cancelled extends Exception { private static final long serialVersionUID = 1L; }
    private static final class HttpFailure extends Exception {
        private static final long serialVersionUID = 1L;
        final int status;
        HttpFailure(int status) {
            super(DownloadRetryPolicy.messageForHttp(status));
            this.status = status;
        }
    }
    private static final class Paused extends Exception {
        private static final long serialVersionUID = 1L;
        final String reason;
        Paused(String reason) { this.reason = value(reason).length() == 0 ? "已暂停" : value(reason); }
    }

    private static final class Locks {
        PowerManager.WakeLock wakeLock;
        long nextRenewAt;
        void renewIfDue(long now) {
            if (wakeLock == null || now < nextRenewAt) return;
            try {
                if (wakeLock.isHeld()) wakeLock.release();
                wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
                nextRenewAt = now + WAKE_LOCK_RENEW_MS;
            } catch (RuntimeException ignored) {}
        }
        void release() {
            try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (RuntimeException ignored) {}
        }
    }

    private static final class DownloadState {
        String originalUrl = "";
        String resolvedUrl = "";
        String etag = "";
        String lastModified = "";
        long totalBytes;
        long cursor;
        boolean publishPending;
        boolean publishRollback;
        String publishUri = "";
        private long lastPersistAt;

        static DownloadState create(Task task) {
            DownloadState state = new DownloadState();
            state.originalUrl = task.url;
            state.resolvedUrl = task.url;
            state.etag = "";
            state.lastModified = "";
            state.totalBytes = 0L;
            state.publishPending = false;
            state.publishRollback = false;
            state.publishUri = "";
            return state;
        }

        long downloaded() { return Math.max(0L, cursor); }

        void advance(int count) { cursor += Math.max(0, count); }

        synchronized void persist(File file, boolean force) {
            long now = SystemClock.elapsedRealtime();
            if (!force && now - lastPersistAt < 2000L) return;
            FileOutputStream output = null;
            AtomicFile atomic = null;
            try {
                JSONObject object = new JSONObject();
                object.put("version", 3);
                object.put("originalUrl", originalUrl);
                object.put("resolvedUrl", resolvedUrl);
                object.put("etag", etag);
                object.put("lastModified", lastModified);
                object.put("totalBytes", totalBytes);
                object.put("cursor", cursor);
                object.put("publishPending", publishPending);
                object.put("publishRollback", publishRollback);
                object.put("publishUri", publishUri);
                atomic = new AtomicFile(file);
                output = atomic.startWrite();
                output.write(object.toString().getBytes("UTF-8"));
                output.flush();
                if (force) output.getFD().sync();
                atomic.finishWrite(output);
                output = null;
                lastPersistAt = now;
            } catch (Exception ignored) {
                if (atomic != null && output != null) atomic.failWrite(output);
                output = null;
            } finally { closeQuietly(output); }
        }

        static DownloadState load(File file) {
            if (file == null) return null;
            FileInputStream input = null;
            try {
                input = new AtomicFile(file).openRead();
                long length = input.getChannel().size();
                if (length <= 0L || length > 512L * 1024L) return null;
                byte[] data = new byte[(int) length];
                int offset = 0;
                while (offset < data.length) {
                    int read = input.read(data, offset, data.length - offset);
                    if (read < 0) break;
                    offset += read;
                }
                JSONObject object = new JSONObject(new String(data, 0, offset, "UTF-8"));
                int version = object.optInt("version", 0);
                if (version != 1 && version != 2 && version != 3) return null;
                DownloadState state = new DownloadState();
                state.originalUrl = object.optString("originalUrl", "");
                state.resolvedUrl = object.optString("resolvedUrl", state.originalUrl);
                state.etag = object.optString("etag", "");
                state.lastModified = object.optString("lastModified", "");
                state.totalBytes = object.optLong("totalBytes", 0L);
                if (version == 2 || version == 3) state.cursor = Math.max(0L, object.optLong("cursor", 0L));
                else {
                    JSONArray array = object.optJSONArray("segments");
                    JSONObject first = array == null ? null : array.optJSONObject(0);
                    if (first == null || array.length() != 1) return null;
                    state.cursor = Math.max(0L, first.optLong("cursor", 0L));
                }
                state.publishPending = version >= 3 && object.optBoolean("publishPending", false);
                state.publishRollback = version >= 3 && object.optBoolean("publishRollback", false);
                state.publishUri = version >= 3 ? object.optString("publishUri", "") : "";
                if (state.totalBytes > 0L) state.cursor = Math.min(state.cursor, state.totalBytes);
                return state;
            } catch (Exception ignored) { return null;
            } finally { closeQuietly(input); }
        }
    }
}
