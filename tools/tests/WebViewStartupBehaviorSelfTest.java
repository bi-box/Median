package com.xinyv.median;

public final class WebViewStartupBehaviorSelfTest {
    private static int assertions;
    private static int cases;

    public static void main(String[] args) {
        success(false, null, null, "兼容初始化");
        success(true, 31L, 17L, "异步初始化 · UI 31 ms · 最长任务 17 ms");
        success(true, 0L, null, "异步初始化 · UI 0 ms");
        success(true, null, 8L, "异步初始化 · 最长任务 8 ms");
        success(true, null, null, "异步初始化");
        failureWithNativeSurface();
        failureBeforeNativeSurface();
        terminalAfterDestroyed(false);
        terminalAfterDestroyed(true);
        terminalAfterFinishing(false);
        terminalAfterFinishing(true);
        destroyedAfterSuccessBeforeFrame(false);
        destroyedAfterSuccessBeforeFrame(true);
        detachedContainerBeforeFrame();
        recreatedActivityCompletesBeforeOldCallback(false);
        recreatedActivityCompletesBeforeOldCallback(true);
        oldCallbackCompletesWhileNewActivityIsPending();
        System.out.println("WebViewStartupBehaviorSelfTest passed: " + assertions +
                " assertions / " + cases + " startup lifecycles");
    }

    private static MainActivityWebViewStartupHarness start() {
        MainActivityWebViewStartupHarness app = new MainActivityWebViewStartupHarness();
        app.start();
        app.buildNativeSurface();
        return app;
    }

    private static void success(boolean async, Long total, Long max, String diagnostic) {
        MainActivityWebViewStartupHarness app = start();
        RecordingExecutor executor = app.executor();
        expect(executor != null && WebViewStartupCompat.executor == executor,
                "startup passes its dedicated executor to the provider");
        expect(!app.executorCleared() && app.posted == 0 && app.initialized == 0,
                "native surface stays usable while provider completion is pending");
        app.callback().onSuccess(new WebViewStartupCompat.Result(async, total, max));
        assertRetired(app);
        expect(diagnostic.equals(app.diagnostic()), "success preserves startup mode and optional timings");
        expect(app.posted == 1 && app.initialized == 0,
                "success posts through the native view frame before creating WebView");
        expect("正在准备网页引擎…".equals(app.status()), "success does not show the failure state");
        app.runPosted();
        expect(app.initialized == 1, "the frame callback creates the initial WebView once");
        app.initializeAgain();
        expect(app.initialized == 1, "the actual constructor guard prevents duplicate initial views");
        app.finishExecutorAgain();
        expect(executor.shutdownCalls == 1, "repeated cleanup does not shut down an already-retired executor");
        cases++;
    }

    private static void failureWithNativeSurface() {
        MainActivityWebViewStartupHarness app = start();
        app.callback().onFailure(new IllegalStateException("native startup failed"));
        assertRetired(app);
        expect(app.posted == 0 && app.initialized == 0,
                "provider failure never constructs a WebView or retries provider APIs");
        expect("网页引擎启动失败，请关闭后重新打开 Median".equals(app.status()),
                "provider failure reports the native restart status");
        app.runPosted();
        expect(app.initialized == 0, "failure leaves no delayed constructor callback");
        cases++;
    }

    private static void failureBeforeNativeSurface() {
        MainActivityWebViewStartupHarness app = new MainActivityWebViewStartupHarness();
        app.start();
        app.callback().onFailure(new IllegalStateException("provider failed before native UI"));
        assertRetired(app);
        expect(app.status() == null && app.posted == 0 && app.initialized == 0,
                "a missing native status cannot throw or initiate provider fallback");
        cases++;
    }

    private static void terminalAfterDestroyed(boolean failure) {
        MainActivityWebViewStartupHarness app = start();
        app.destroyBeforeCallback();
        expect(app.executor().shutdownCalls == 0 && app.executor().shutdownNowCalls == 0,
                "Activity destruction leaves process-wide provider work uninterrupted");
        complete(app, failure);
        assertRetired(app);
        expect(app.posted == 0 && app.initialized == 0,
                "destroyed Activity callbacks cannot create or enqueue a WebView");
        expect("正在准备网页引擎…".equals(app.status()), "destroyed Activity callbacks do not mutate its native UI");
        cases++;
    }

    private static void terminalAfterFinishing(boolean failure) {
        MainActivityWebViewStartupHarness app = start();
        app.finishBeforeCallback();
        complete(app, failure);
        assertRetired(app);
        expect(app.posted == 0 && app.initialized == 0,
                "finishing Activity callbacks cannot create or enqueue a WebView");
        expect("正在准备网页引擎…".equals(app.status()), "finishing Activity callbacks do not mutate its native UI");
        cases++;
    }

    private static void destroyedAfterSuccessBeforeFrame(boolean finishing) {
        MainActivityWebViewStartupHarness app = start();
        complete(app, false);
        expect(app.posted == 1 && app.initialized == 0, "the pending frame callback exists before teardown");
        if (finishing) app.finishBeforeCallback();
        else app.destroyBeforeCallback();
        app.runPosted();
        assertRetired(app);
        expect(app.initialized == 0, "the actual initial-view guard blocks creation after posted-success teardown");
        cases++;
    }

    private static void detachedContainerBeforeFrame() {
        MainActivityWebViewStartupHarness app = start();
        complete(app, false);
        app.detachContainer();
        app.runPosted();
        expect(app.initialized == 0, "the actual initial-view guard requires a native container");
        assertRetired(app);
        cases++;
    }

    private static void recreatedActivityCompletesBeforeOldCallback(boolean oldFailure) {
        MainActivityWebViewStartupHarness oldActivity = start();
        oldActivity.destroyBeforeCallback();
        MainActivityWebViewStartupHarness newActivity = start();
        expect(newActivity.executor() != oldActivity.executor(), "recreation creates an independent startup executor");
        complete(newActivity, false);
        newActivity.runPosted();
        assertRetired(newActivity);
        expect(newActivity.initialized == 1, "recreated Activity can complete while old startup is pending");
        expect(oldActivity.executor().shutdownCalls == 0, "new Activity completion cannot shut down old provider work");
        complete(oldActivity, oldFailure);
        assertRetired(oldActivity);
        expect(oldActivity.posted == 0 && oldActivity.initialized == 0,
                "late old success/failure never creates a stale WebView");
        expect(newActivity.initialized == 1 && newActivity.executor().shutdownCalls == 1,
                "late old completion cannot change the recreated Activity or executor");
        cases++;
    }

    private static void oldCallbackCompletesWhileNewActivityIsPending() {
        MainActivityWebViewStartupHarness oldActivity = start();
        oldActivity.destroyBeforeCallback();
        MainActivityWebViewStartupHarness newActivity = start();
        complete(oldActivity, false);
        assertRetired(oldActivity);
        expect(!newActivity.executorCleared() && newActivity.executor().shutdownCalls == 0,
                "old completion leaves the new Activity executor pending");
        expect(newActivity.posted == 0 && newActivity.initialized == 0,
                "old completion cannot grant new Activity provider readiness");
        newActivity.callback().onSuccess(new WebViewStartupCompat.Result(false, null, null));
        newActivity.runPosted();
        assertRetired(newActivity);
        expect(newActivity.initialized == 1 && "兼容初始化".equals(newActivity.diagnostic()),
                "recreated Activity keeps its own fallback mode and successful constructor");
        cases++;
    }

    private static void complete(MainActivityWebViewStartupHarness app, boolean failure) {
        if (failure) app.callback().onFailure(new IllegalStateException("late provider failure"));
        else app.callback().onSuccess(new WebViewStartupCompat.Result(true, null, null));
    }

    private static void assertRetired(MainActivityWebViewStartupHarness app) {
        expect(app.executor().shutdownCalls == 1 && app.executor().shutdownNowCalls == 0 && app.executorCleared(),
                "every terminal callback retires its executor once without interrupting provider tasks");
    }

    private static void expect(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
