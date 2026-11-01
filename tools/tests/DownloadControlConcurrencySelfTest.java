package com.xinyv.median;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/** JVM regression check compiling the actual service Control, rather than a copied model. */
public final class DownloadControlConcurrencySelfTest {
    public interface Probe {
        Object monitor();
        boolean cancel();
        boolean pause(String reason);
        void complete(Runnable commit) throws Exception;
        boolean completed();
        String checkResult();
        void attach(HttpURLConnection connection);
        void detach(HttpURLConnection connection);
    }

    public interface LifecycleProbe {
        void cancelAction();
        void finish();
        void completeStored() throws Exception;
        void seedStatus(String status);
        void setLookupHook(Runnable hook);
        String status();
        String reason();
        boolean hasPartial();
        boolean scheduled();
    }

    private static Class<?> fixture;
    private static Class<?> lifecycleFixture;
    private static int completedWinners;
    private static int stoppedWinners;

    public static void main(String[] args) throws Exception {
        Path source = Paths.get(args.length == 0 ?
                "app/src/main/java/com/xinyv/median/AdaptiveDownloadService.java" : args[0]);
        compileActualControl(source);
        normalAndOrderedTransitions();
        completionHoldsCommitBoundary();
        stopHoldsCommitBoundary();
        cancelAfterCatchBeforeFinally();
        staleLookupAfterRetirement(false);
        staleLookupAfterRetirement(true);
        ExecutorService pool = Executors.newFixedThreadPool(2, task -> {
            Thread worker = new Thread(task, "download-control-race");
            worker.setDaemon(true);
            return worker;
        });
        try {
            for (int i = 0; i < 4000; i++) race(pool, false, i);
            for (int i = 0; i < 4000; i++) race(pool, true, i);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "executor termination");
        }
        System.out.println("DownloadControlConcurrencySelfTest passed: lifecycle boundaries and 8000 races, " +
                completedWinners + " completion winners, " + stoppedWinners + " stop winners");
    }

    private static void compileActualControl(Path serviceSource) throws Exception {
        String service = new String(Files.readAllBytes(serviceSource), StandardCharsets.UTF_8);
        String generated = "package com.xinyv.median;\n" +
                "import java.util.concurrent.atomic.AtomicBoolean;\n" +
                "import java.net.HttpURLConnection;\n" +
                "public final class ActualDownloadControlProbe implements " +
                "DownloadControlConcurrencySelfTest.Probe {\n" +
                extractClass(service, "Control") + "\n" +
                extractClass(service, "Cancelled") + "\n" +
                extractClass(service, "Paused") + "\n" +
                "private static String value(String text) { return text == null ? \"\" : text; }\n" +
                "private final Control control = new Control();\n" +
                "public Object monitor() { return control; }\n" +
                "public boolean cancel() { return control.cancel(); }\n" +
                "public boolean pause(String text) { return control.pause(text); }\n" +
                "public void complete(Runnable commit) throws Exception { control.complete(commit); }\n" +
                "public boolean completed() { return control.completed; }\n" +
                "public String checkResult() { try { control.check(); return \"ready\"; } " +
                "catch (Cancelled error) { return \"cancelled\"; } " +
                "catch (Paused error) { return \"paused:\" + error.reason; } }\n" +
                "public void attach(HttpURLConnection connection) { control.attach(connection); }\n" +
                "public void detach(HttpURLConnection connection) { control.detach(connection); }\n" +
                "}\n";
        Path output = Files.createTempDirectory("median-download-control-test-");
        Path fixtureSource = output.resolve("ActualDownloadControlProbe.java");
        Files.write(fixtureSource, generated.getBytes(StandardCharsets.UTF_8));
        Path lifecycleSource = output.resolve("ActualDownloadLifecycleProbe.java");
        Files.write(lifecycleSource, lifecycleSource(service).getBytes(StandardCharsets.UTF_8));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "run using a JDK, rather than a JRE");
        int result = compiler.run(null, null, null, "-encoding", "UTF-8", "-classpath",
                System.getProperty("java.class.path"), "-d", output.toString(), fixtureSource.toString(),
                lifecycleSource.toString());
        assertTrue(result == 0, "actual Control compilation");
        URLClassLoader loader = new URLClassLoader(new URL[] { output.toUri().toURL() },
                DownloadControlConcurrencySelfTest.class.getClassLoader());
        fixture = Class.forName("com.xinyv.median.ActualDownloadControlProbe", true, loader);
        lifecycleFixture = Class.forName("com.xinyv.median.ActualDownloadLifecycleProbe", true, loader);
    }

    private static String extractClass(String source, String name) {
        return extractBlock(source, "private static final class " + name);
    }

    private static String extractBlock(String source, String marker) {
        int start = source.indexOf(marker);
        assertTrue(start >= 0, "missing actual " + marker);
        int opening = source.indexOf('{', start);
        int depth = 1;
        boolean string = false;
        boolean character = false;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int i = opening + 1; i < source.length(); i++) {
            char ch = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (lineComment) { if (ch == '\n') lineComment = false; continue; }
            if (blockComment) { if (ch == '*' && next == '/') { blockComment = false; i++; } continue; }
            if (string || character) {
                if (ch == '\\') { i++; continue; }
                if (string && ch == '"') string = false;
                else if (character && ch == '\'') character = false;
                continue;
            }
            if (ch == '/' && next == '/') { lineComment = true; i++; continue; }
            if (ch == '/' && next == '*') { blockComment = true; i++; continue; }
            if (ch == '"') { string = true; continue; }
            if (ch == '\'') { character = true; continue; }
            if (ch == '{') depth++;
            else if (ch == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        throw new AssertionError("unterminated actual " + marker);
    }

    private static String lifecycleSource(String service) {
        String cancel = extractBlock(service, "if (ACTION_CANCEL.equals(action))");
        String lookup = "Control control = CONTROLS.get(Long.valueOf(id));";
        assertTrue(cancel.contains(lookup), "actual cancel must perform Control lookup");
        // Stop only after the real map lookup, so a deterministic test can retire that worker.
        cancel = cancel.replace(lookup, lookup + "\nlookupHook.run();");
        String worker = extractBlock(service, "private void runTask(");
        String workerFinally = extractBlock(worker, "finally {");
        return "package com.xinyv.median;\n" +
                "import java.io.File;\nimport java.net.HttpURLConnection;\n" +
                "import java.util.concurrent.ConcurrentHashMap;\n" +
                "import java.util.concurrent.atomic.AtomicBoolean;\n" +
                "public final class ActualDownloadLifecycleProbe implements " +
                "DownloadControlConcurrencySelfTest.LifecycleProbe {\n" +
                extractClass(service, "Control") + "\n" +
                extractClass(service, "Cancelled") + "\n" +
                extractClass(service, "Paused") + "\n" +
                "private static String value(String text) { return text == null ? \"\" : text; }\n" +
                "private static final String ACTION_CANCEL = \"cancel\";\n" +
                "private static final int START_NOT_STICKY = 2;\n" +
                "private final ConcurrentHashMap<Long, Control> CONTROLS = new ConcurrentHashMap<>();\n" +
                "private final Control control = new Control();\n" +
                "private final DownloadStore store = new DownloadStore();\n" +
                "private volatile Runnable lookupHook = () -> {};\n" +
                "private volatile boolean partial = true;\n" +
                "public ActualDownloadLifecycleProbe() { CONTROLS.put(Long.valueOf(-1L), control); }\n" +
                "public void setLookupHook(Runnable hook) { lookupHook = hook; }\n" +
                "public void seedStatus(String status) { store.updateAdaptive(-1L, status, status, 0L, 5L, 0L, null); }\n" +
                "public String status() { return store.get(-1L).status; }\n" +
                "public String reason() { return store.get(-1L).reason; }\n" +
                "public boolean hasPartial() { return partial; }\n" +
                "public boolean scheduled() { return CONTROLS.containsKey(Long.valueOf(-1L)); }\n" +
                "public void cancelAction() { cancelCommand(); }\n" +
                "private int cancelCommand() { final long id = -1L; final int startId = 1; " +
                "String action = ACTION_CANCEL; " + cancel + " return START_NOT_STICKY; }\n" +
                "public void completeStored() throws Exception { control.complete(() -> " +
                "store.updateAdaptive(-1L, DownloadStore.STATUS_COMPLETED, \"已完成\", 5L, 5L, 0L, \"uri\")); }\n" +
                "public void finish() { Task task = new Task(); File temp = new File(\"part\"); " +
                "File stateFile = new File(\"state\"); Locks locks = null; " +
                "boolean completed = false; boolean cancelled = false; try {} " + workerFinally + " }\n" +
                "private boolean stopSelfResult(int id) { return true; }\n" +
                "private void deletePartialFiles(long id) { partial = false; }\n" +
                "private void deleteQuietly(File file) { partial = false; }\n" +
                "private void deleteStateFile(File file) {}\n" +
                "private void notifyTask(Task task, String text, int percent, long downloaded, long total, " +
                "boolean ongoing, boolean resumable) {}\n" +
                "private static final class Locks { void release() {} }\n" +
                "private static final class Task { long id = -1L; " +
                "static Task fromStore(Object context, DownloadStore.Item item) { return new Task(); } }\n" +
                "private static final class DownloadStore {\n" +
                "static final String STATUS_COMPLETED = \"completed\", STATUS_CANCELLED = \"cancelled\";\n" +
                "private Item item = new Item();\n" +
                "synchronized Item get(long id) { Item copy = new Item(); copy.status = item.status; " +
                "copy.reason = item.reason; copy.downloadedBytes = item.downloadedBytes; " +
                "copy.totalBytes = item.totalBytes; return copy; }\n" +
                "synchronized void updateAdaptive(long id, String status, String reason, long bytes, " +
                "long total, long speed, String uri) { item.status = status; item.reason = reason; " +
                "item.downloadedBytes = bytes; item.totalBytes = total; }\n" +
                "static final class Item { String status = \"paused\"; String reason = \"paused\"; " +
                "long downloadedBytes; long totalBytes = 5L; }\n}\n}\n";
    }

    private static Probe fresh() throws Exception {
        return (Probe) fixture.getDeclaredConstructor().newInstance();
    }

    private static LifecycleProbe freshLifecycle() throws Exception {
        return (LifecycleProbe) lifecycleFixture.getDeclaredConstructor().newInstance();
    }

    private static void cancelAfterCatchBeforeFinally() throws Exception {
        for (String status : new String[] { "paused", "failed" }) {
            LifecycleProbe probe = freshLifecycle();
            probe.seedStatus(status);
            probe.cancelAction();
            assertTrue(probe.hasPartial(), "active worker retains temp until its cleanup");
            probe.finish();
            assertEquals("cancelled", probe.status(), "catch-to-finally cancel remains final");
            assertEquals("已取消", probe.reason(), "catch-to-finally cancel settles its message");
            assertTrue(!probe.hasPartial() && !probe.scheduled(), "late cancel retires and cleans worker");
        }
    }

    private static void staleLookupAfterRetirement(boolean complete) throws Exception {
        LifecycleProbe probe = freshLifecycle();
        CountDownLatch lookedUp = new CountDownLatch(1);
        CountDownLatch continueAction = new CountDownLatch(1);
        probe.setLookupHook(() -> { lookedUp.countDown(); await(continueAction); });
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread cancel = new Thread(() -> {
            try { probe.cancelAction(); }
            catch (Throwable failure) { error.set(failure); }
        }, "stale-worker-lookup");
        cancel.setDaemon(true);
        cancel.start();
        assertTrue(lookedUp.await(2, TimeUnit.SECONDS), "cancel captures active worker");
        try {
            if (complete) probe.completeStored();
            probe.finish();
            assertTrue(!probe.scheduled(), "worker retired before stale lookup resumes");
        } finally { continueAction.countDown(); }
        join(cancel);
        assertTrue(error.get() == null, "stale lookup settles without exception");
        assertEquals(complete ? "completed" : "cancelled", probe.status(), "retired worker final state");
        assertEquals(complete ? "已完成" : "已取消", probe.reason(), "retired worker settled reason");
        assertTrue(!probe.hasPartial(), "retired cancellation cleans temp; completion cleans its own temp");
    }

    private static void normalAndOrderedTransitions() throws Exception {
        Probe normal = fresh();
        CountingConnection connection = new CountingConnection();
        normal.attach(connection);
        assertEquals("ready", normal.checkResult(), "new task");
        AtomicInteger commits = new AtomicInteger();
        normal.complete(commits::incrementAndGet);
        assertTrue(normal.completed() && commits.get() == 1, "normal completion");
        assertTrue(!normal.cancel() && !normal.pause("late"), "late actions are rejected");
        assertTrue(connection.disconnects.get() == 0, "late actions leave completed connection alone");

        Probe cancelled = fresh();
        cancelled.attach(connection);
        assertTrue(cancelled.cancel(), "cancel accepted before completion");
        assertEquals("cancelled", cancelled.checkResult(), "cancel visible to worker");
        assertBlockedCompletion(cancelled, "Cancelled");
        assertTrue(connection.disconnects.get() == 1, "cancel disconnects active transfer");

        Probe paused = fresh();
        assertTrue(paused.pause("user pause"), "pause accepted before completion");
        assertEquals("paused:user pause", paused.checkResult(), "pause reason visible");
        assertBlockedCompletion(paused, "Paused");
        assertTrue(paused.cancel(), "a paused task can still be cancelled");
        assertEquals("cancelled", paused.checkResult(), "cancel takes precedence over pause");

        Probe callbackFailure = fresh();
        try { callbackFailure.complete(() -> { throw new IllegalStateException("store unavailable"); });
            throw new AssertionError("callback failure was swallowed");
        } catch (IllegalStateException expected) { /* Completion was not committed. */ }
        assertTrue(!callbackFailure.completed(), "failed callback does not latch completed");
        assertTrue(callbackFailure.cancel(), "lock released after callback failure");

        Probe detached = fresh();
        CountingConnection detachedConnection = new CountingConnection();
        detached.attach(detachedConnection);
        detached.detach(detachedConnection);
        assertTrue(detached.pause("pause"), "pause detached transfer");
        assertTrue(detachedConnection.disconnects.get() == 0, "detached transfer stays untouched");
    }

    private static void completionHoldsCommitBoundary() throws Exception {
        Probe probe = fresh();
        CountDownLatch callbackEntered = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Boolean> cancelAccepted = new AtomicReference<>();
        Thread complete = new Thread(() -> {
            try { probe.complete(() -> { callbackEntered.countDown(); await(releaseCallback); }); }
            catch (Throwable failure) { error.set(failure); }
        }, "completion-boundary");
        complete.setDaemon(true);
        complete.start();
        assertTrue(callbackEntered.await(2, TimeUnit.SECONDS), "completion enters callback");
        Thread cancel = new Thread(() -> {
            synchronized (probe.monitor()) { cancelAccepted.set(probe.cancel()); }
        }, "cancel-boundary");
        cancel.setDaemon(true);
        cancel.start();
        try { awaitBlocked(cancel); }
        finally { releaseCallback.countDown(); }
        join(complete); join(cancel);
        assertTrue(error.get() == null, "completion callback succeeds");
        assertTrue(probe.completed() && Boolean.FALSE.equals(cancelAccepted.get()),
                "cancel cannot enter between commit and completed latch");
    }

    private static void stopHoldsCommitBoundary() throws Exception {
        Probe probe = fresh();
        AtomicInteger commits = new AtomicInteger();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread complete = new Thread(() -> {
            try { probe.complete(commits::incrementAndGet); }
            catch (Throwable failure) { error.set(failure); }
        }, "complete-after-cancel");
        complete.setDaemon(true);
        synchronized (probe.monitor()) {
            assertTrue(probe.cancel(), "stop enters boundary first");
            complete.start();
            awaitBlocked(complete);
        }
        join(complete);
        assertTrue(error.get() != null && error.get().getClass().getSimpleName().equals("Cancelled"),
                "waiting completion observes stop");
        assertTrue(commits.get() == 0 && !probe.completed(), "stopped task cannot commit");
    }

    private static void race(ExecutorService pool, boolean pause, int iteration) throws Exception {
        Probe probe = fresh();
        CyclicBarrier start = new CyclicBarrier(3);
        AtomicReference<String> persisted = new AtomicReference<>("downloading");
        AtomicInteger commits = new AtomicInteger();
        AtomicReference<Boolean> stopAccepted = new AtomicReference<>();
        Runnable completion = () -> {
            await(start);
            try { probe.complete(() -> { commits.incrementAndGet(); persisted.set("completed"); }); }
            catch (Exception stopped) {
                String expected = pause ? "Paused" : "Cancelled";
                assertTrue(stopped.getClass().getSimpleName().equals(expected), "expected stop exception");
            }
        };
        Runnable stop = () -> {
            await(start);
            synchronized (probe.monitor()) {
                boolean accepted = pause ? probe.pause("race pause") : probe.cancel();
                stopAccepted.set(accepted);
                if (accepted) persisted.set(pause ? "paused" : "cancelled");
            }
        };
        Future<?> first = pool.submit(iteration % 2 == 0 ? completion : stop);
        Future<?> second = pool.submit(iteration % 2 == 0 ? stop : completion);
        await(start);
        first.get(2, TimeUnit.SECONDS); second.get(2, TimeUnit.SECONDS);
        if (probe.completed()) {
            completedWinners++;
            assertTrue(commits.get() == 1 && Boolean.FALSE.equals(stopAccepted.get()), "one completed winner");
            assertEquals("completed", persisted.get(), "late stop cannot overwrite completion");
            assertTrue(!probe.cancel() && !probe.pause("stale notification"), "stale stop is rejected");
        } else {
            stoppedWinners++;
            assertTrue(commits.get() == 0 && Boolean.TRUE.equals(stopAccepted.get()), "one stop winner");
            assertEquals(pause ? "paused" : "cancelled", persisted.get(), "completion cannot overwrite stop");
            assertBlockedCompletion(probe, pause ? "Paused" : "Cancelled");
        }
    }

    private static void assertBlockedCompletion(Probe probe, String expectedException) throws Exception {
        AtomicInteger commits = new AtomicInteger();
        try { probe.complete(commits::incrementAndGet); throw new AssertionError("stopped task completed"); }
        catch (Exception stopped) {
            assertEquals(expectedException, stopped.getClass().getSimpleName(), "stop rejects completion");
        }
        assertTrue(commits.get() == 0 && !probe.completed(), "no callback for stopped task");
    }

    private static void awaitBlocked(Thread thread) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (thread.getState() != Thread.State.BLOCKED && thread.isAlive() && System.nanoTime() < deadline)
            Thread.sleep(1);
        assertTrue(thread.getState() == Thread.State.BLOCKED, "contender waits for commit monitor");
    }

    private static void join(Thread thread) throws Exception {
        thread.join(2000);
        assertTrue(!thread.isAlive(), "thread releases monitor and finishes");
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(2, TimeUnit.SECONDS), "latch deadline"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }

    private static void await(CyclicBarrier barrier) {
        try { barrier.await(2, TimeUnit.SECONDS); }
        catch (Exception error) { throw new AssertionError(error); }
    }

    private static final class CountingConnection extends HttpURLConnection {
        final AtomicInteger disconnects = new AtomicInteger();
        CountingConnection() throws Exception { super(new URL("https://example.invalid/test")); }
        @Override public void disconnect() { disconnects.incrementAndGet(); }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() {}
    }

    private static void assertEquals(String expected, String actual, String message) {
        assertTrue(expected.equals(actual), message + ": expected " + expected + ", got " + actual);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
