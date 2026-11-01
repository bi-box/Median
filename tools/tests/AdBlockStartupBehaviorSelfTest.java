package com.xinyv.median;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class AdBlockStartupBehaviorSelfTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        customBeforeFirstNavigation();
        globalOffDoesNotWait();
        latestGenerationWins();
        supersededWhileReading();
        destinationsSurviveBootstrap();
        lifecycleAndFailure();
        closeDropsQueuedCallbacks();
        closeWhileReading();
        failedReloadRetainsLastSnapshot();
        networkRefreshWaitsForIdle();
        interruptedCompilationReleasesStartupGate();
        System.out.println("AdBlockStartupBehaviorSelfTest passed (" + checks + " checks)");
    }

    private static void interruptedCompilationReleasesStartupGate() throws Exception {
        final AdBlockStartupBehaviorHarness h = new AdBlockStartupBehaviorHarness();
        h.startRules(); h.nativeReady();
        final Runnable task = h.worker.queue.poll();
        Thread worker = new RuleWorker(new Runnable() {
            @Override public void run() {
                Thread.currentThread().interrupt();
                try { task.run(); } finally { Thread.interrupted(); }
            }
        });
        worker.start(); join(worker); h.runUi();
        expect(h.failureCallbacks == 1 && h.navigations == 1,
                "a current interrupted compilation reports failure and releases the startup gate");
        expect(h.publishedCallbacks == 0,
                "interrupted compilation never publishes a partial custom snapshot");
    }

    private static void customBeforeFirstNavigation() throws Exception {
        AdBlockStartupBehaviorHarness h = new AdBlockStartupBehaviorHarness();
        h.prefs.custom = "||custom.example^\npage.example##.manual-ad";
        h.startupExternalUrl = "https://page.example/";
        h.services.store.entered = new CountDownLatch(1);
        h.services.store.release = new CountDownLatch(1);
        h.startRules(); h.nativeReady();
        expect(h.navigations == 0 && h.services.store.reads == 0, "UI does not compile or read rules; custom gate holds");
        Thread thread = h.runWorker();
        expect(h.services.store.entered.await(2, TimeUnit.SECONDS), "worker reached subscription read after custom publish");
        expect(h.publishedCallbacks == 0 && h.failureCallbacks == 0,
                "worker never invokes a listener before the UI queue dispatches");
        expect(android.os.Process.lastWorker == thread,
                "rule compilation explicitly preserves background worker priority");
        h.runUi();
        expect(h.navigations == 1 && h.navigated.equals("https://page.example/"), "subscription I/O cannot hold first navigation");
        expect(h.adBlock.shouldBlock("https://custom.example/a", "page.example"), "custom network rule is active at first navigation");
        expect(h.adBlock.cosmeticCssForHost("page.example").contains(".manual-ad"), "saved manual hiding is active at first navigation");
        expect(!h.adBlock.shouldBlock("https://subscription.example/a", "page.example"), "full snapshot has not been published while read is blocked");
        h.services.store.release.countDown(); join(thread); h.runUi();
        expect(h.adBlock.shouldBlock("https://subscription.example/a", "page.example"), "cached subscription becomes active without waiting for load");
        expect(h.adBlock.cosmeticCssForHost("page.example").contains(".manual-ad"), "full snapshot retains custom hiding");
        expect(h.navigations == 1 && h.updates == 0, "full publish neither reloads nor fetches subscriptions");
        expect(h.publishedCallbacks == 2, "custom and full snapshots each publish on the UI queue");
    }

    private static void globalOffDoesNotWait() throws Exception {
        AdBlockStartupBehaviorHarness h = new AdBlockStartupBehaviorHarness();
        h.prefs.enabled = false; h.applyStartupProtection();
        h.startRules(); h.startupExternalUrl = "https://off.example/"; h.nativeReady();
        expect(h.navigations == 1 && h.worker.queue.size() == 1, "global-off navigation does not wait for any rule worker");
        join(h.runWorker()); h.runUi();
        expect(h.navigations == 1, "global-off background completion does not duplicate navigation");
    }

    private static void latestGenerationWins() throws Exception {
        AdBlockEngine engine = new AdBlockEngine();
        AdBlockRuleReload reload = new AdBlockRuleReload(engine);
        long old = reload.request();
        AdBlockEngine.CompiledRules oldFull = engine.compileRules("||old.example^", Collections.<String>emptyList());
        long latest = reload.request();
        AdBlockEngine.CompiledRules current = engine.compileRules("||latest.example^", Collections.<String>emptyList());
        expect(reload.publish(latest, AdBlockRuleReload.FULL, current), "new full snapshot publishes");
        expect(!reload.publish(old, AdBlockRuleReload.FULL, oldFull), "old full compilation cannot replace latest edit");
        expect(!reload.publish(latest, AdBlockRuleReload.CUSTOM, oldFull), "late custom phase cannot replace full phase");
        expect(engine.shouldBlock("https://latest.example/a", "page.example") && !engine.shouldBlock("https://old.example/a", "page.example"),
                "rejected stale snapshots never alter the production matcher");

        AdBlockStartupBehaviorHarness h = new AdBlockStartupBehaviorHarness();
        h.startRules(); join(h.runWorker()); // Old callbacks are still queued on the UI.
        h.editRules("page.example##.latest", true);
        h.nativeReady(); h.runUi();
        expect(h.navigations == 0 && h.notifications == 0, "stale startup completion cannot open the gate or notify");
        join(h.runWorker()); h.runUi();
        expect(h.navigations == 1 && h.adBlock.cosmeticCssForHost("page.example").contains(".latest"),
                "latest custom snapshot opens the gate");
        expect(h.notifications == 1 && h.reloads == 1, "latest manual request alone notifies and reloads");
        h.editRules("page.example##.newest", false); join(h.runWorker()); h.runUi();
        expect(h.adBlock.shouldBlock("https://subscription.example/a", "page.example"), "later edit does not leave a custom-only snapshot");
    }

    private static void destinationsSurviveBootstrap() throws Exception {
        for (String route : new String[]{"typed", "intent", "home", "custom-home", "session", "saved"}) {
            AdBlockStartupBehaviorHarness h = new AdBlockStartupBehaviorHarness();
            h.startupExternalUrl = route.equals("intent") || route.equals("typed") ? "https://intent.example/" : "";
            h.startupSavedState = new Bundle();
            if (route.equals("saved")) h.startupSavedState.url = "https://saved.example/";
            if (route.equals("session")) h.restoredSessionUrl = "https://session.example/";
            if (route.equals("custom-home")) h.configuredHome = "https://custom-home.example/";
            h.startRules(); h.nativeReady();
            if (route.equals("typed")) h.pendingStartupInput = "https://new-intent-or-typed.example/";
            join(h.runWorker()); h.runUi();
            String expected = route.equals("typed") ? "https://new-intent-or-typed.example/" :
                    route.equals("intent") ? "https://intent.example/" :
                    route.equals("saved") ? "https://saved.example/" :
                    route.equals("session") ? "https://session.example/" : h.configuredHome;
            expect(h.navigations == 1 && h.navigated.equals(expected), "bootstrap preserves selected destination: " + route);
        }
    }

    private static void supersededWhileReading() throws Exception {
        AdBlockStartupBehaviorHarness h = new AdBlockStartupBehaviorHarness();
        h.prefs.custom = "||old-custom.example^";
        h.services.store.entered = new CountDownLatch(1);
        h.services.store.release = new CountDownLatch(1);
        h.startRules(); h.nativeReady();
        Thread old = h.runWorker();
        expect(h.services.store.entered.await(2, TimeUnit.SECONDS), "old request is blocked in subscription read");
        h.editRules("||current-custom.example^", true);
        h.services.store.release.countDown(); join(old); h.runUi();
        expect(!h.adBlock.shouldBlock("https://subscription.example/a", "page.example"),
                "superseded read cannot publish a full snapshot");
        expect(h.navigations == 0 && h.publishedCallbacks == 0 && h.failureCallbacks == 0,
                "superseded custom notification cannot open startup or invoke the listener");
        join(h.runWorker()); h.runUi();
        expect(h.navigations == 1 && h.notifications == 1 && h.reloads == 1,
                "latest in-flight edit alone releases startup and reports success");
        expect(h.adBlock.shouldBlock("https://current-custom.example/a", "page.example") &&
                        !h.adBlock.shouldBlock("https://old-custom.example/a", "page.example") &&
                        h.adBlock.shouldBlock("https://subscription.example/a", "page.example"),
                "latest request retains both current custom and subscription rules");
    }

    private static void lifecycleAndFailure() throws Exception {
        AdBlockStartupBehaviorHarness destroyed = new AdBlockStartupBehaviorHarness();
        destroyed.startRules(); destroyed.destroy();
        join(destroyed.runWorker()); destroyed.runUi();
        expect(!destroyed.startupCustomRulesReady && destroyed.notifications == 0 && destroyed.navigations == 0 &&
                        destroyed.services.store.reads == 0,
                "destroyed Activity ignores completion and never navigates or reports");

        AdBlockStartupBehaviorHarness rejected = new AdBlockStartupBehaviorHarness();
        rejected.worker.reject = true; rejected.startRules(); rejected.nativeReady(); rejected.runUi();
        expect(rejected.startupCustomRulesReady && rejected.navigations == 1 && rejected.notifications > 0,
                "rejected worker releases startup gate and reports failure");
        expect(rejected.failureCallbacks == 1, "bootstrap rejection reports exactly one loader failure");

        AdBlockStartupBehaviorHarness laterRejected = new AdBlockStartupBehaviorHarness();
        laterRejected.startupReady = true; laterRejected.worker.reject = true;
        laterRejected.editRules("||new.example^", true); laterRejected.runUi();
        expect(laterRejected.failureCallbacks == 0 && laterRejected.publishedCallbacks == 0 &&
                        laterRejected.notifications == 1,
                "later queue rejection uses the shared busy notification without a bootstrap failure");

        AdBlockStartupBehaviorHarness failed = new AdBlockStartupBehaviorHarness();
        failed.services.store.fail = true; failed.startRules(); failed.nativeReady();
        join(failed.runWorker()); failed.runUi();
        expect(failed.navigations == 1 && failed.startupCustomRulesReady && failed.notifications == 1,
                "subscription read exception cannot leave startup gate waiting");

        AdBlockStartupBehaviorHarness paused = new AdBlockStartupBehaviorHarness();
        paused.startRules(); paused.nativeReady(); paused.activityResumed = false;
        join(paused.runWorker()); paused.runUi();
        expect(paused.navigations == 0, "paused Activity does not navigate when custom rules finish");
        paused.resume(); paused.runUi();
        expect(paused.navigations == 1, "resume continues selected navigation exactly once");
    }

    private static void closeDropsQueuedCallbacks() throws Exception {
        AdBlockStartupBehaviorHarness published = new AdBlockStartupBehaviorHarness();
        published.startRules(); published.nativeReady(); join(published.runWorker());
        expect(published.uiHandler.queue.size() == 2, "published notifications are pending before close");
        published.destroy(); published.runUi();
        expect(published.publishedCallbacks == 0 && published.failureCallbacks == 0 && published.navigations == 0,
                "close suppresses successful notifications already queued on UI");
        int acquisitions = published.services.filterAcquisitions;
        published.adBlockRuleLoader.close();
        published.adBlockRuleLoader.request(true, true); published.runUi();
        expect(published.worker.queue.isEmpty() && published.services.filterAcquisitions == acquisitions,
                "close is idempotent and later requests never enqueue work or reopen services");

        AdBlockStartupBehaviorHarness failed = new AdBlockStartupBehaviorHarness();
        failed.services.store.fail = true; failed.startRules(); join(failed.runWorker());
        failed.destroy(); failed.runUi();
        expect(failed.publishedCallbacks == 0 && failed.failureCallbacks == 0 && failed.notifications == 0,
                "close suppresses failure notifications already queued on UI");
    }

    private static void closeWhileReading() throws Exception {
        AdBlockStartupBehaviorHarness h = new AdBlockStartupBehaviorHarness();
        h.prefs.custom = "||retained.example^";
        h.services.store.entered = new CountDownLatch(1);
        h.services.store.release = new CountDownLatch(1);
        h.startRules(); h.nativeReady();
        Thread worker = h.runWorker();
        expect(h.services.store.entered.await(2, TimeUnit.SECONDS), "close scenario enters background disk read");
        h.destroy(); h.services.store.release.countDown(); join(worker); h.runUi();
        expect(h.adBlock.shouldBlock("https://retained.example/a", "page.example") &&
                        !h.adBlock.shouldBlock("https://subscription.example/a", "page.example"),
                "close during read retains the published custom snapshot and rejects the late full snapshot");
        expect(h.publishedCallbacks == 0 && h.failureCallbacks == 0 && h.navigations == 0,
                "close during read never calls the owner or opens a page");
    }

    private static void failedReloadRetainsLastSnapshot() throws Exception {
        AdBlockStartupBehaviorHarness h = new AdBlockStartupBehaviorHarness();
        h.prefs.custom = "||retained.example^";
        h.startRules(); h.nativeReady(); join(h.runWorker()); h.runUi();
        h.services.store.fail = true;
        h.editRules("||unpublished.example^", true); join(h.runWorker()); h.runUi();
        expect(h.adBlock.shouldBlock("https://retained.example/a", "page.example") &&
                        h.adBlock.shouldBlock("https://subscription.example/a", "page.example") &&
                        !h.adBlock.shouldBlock("https://unpublished.example/a", "page.example"),
                "failed later read retains the last complete network snapshot");
        expect(h.failureCallbacks == 1 && h.navigations == 1 && h.reloads == 0,
                "failed later reload reports once without reloading the page");

        // A failure already queued on UI belongs to the old generation, just like success.
        h.editRules("||failed-again.example^", false); join(h.runWorker());
        h.services.store.fail = false;
        h.editRules("||latest.example^", false); h.runUi();
        expect(h.failureCallbacks == 1, "new request invalidates an older queued failure callback");
        join(h.runWorker()); h.runUi();
        expect(h.adBlock.shouldBlock("https://latest.example/a", "page.example"),
                "a successful retry replaces the retained snapshot");
    }

    private static void networkRefreshWaitsForIdle() {
        AdBlockStartupBehaviorHarness h = new AdBlockStartupBehaviorHarness();
        h.startupReady = true; h.activityResumed = true; h.finishPage();
        expect(h.updates == 0 && h.uiHandler.delay == 3200L, "network refresh remains outside page load");
        h.pageFinished = false; h.runIdle();
        expect(h.updates == 0 && !h.deferredStartupComplete, "next navigation cancels the old idle opportunity");
        h.finishPage(); h.runIdle();
        expect(h.updates == 1 && h.worker.queue.isEmpty(), "idle update does not trigger another startup compilation");
    }

    private static void join(Thread thread) throws Exception {
        thread.join(5000L); expect(!thread.isAlive(), "worker completed");
        if (thread instanceof RuleWorker && ((RuleWorker) thread).failure != null)
            throw new AssertionError("Rule worker failed", ((RuleWorker) thread).failure);
    }
    private static void expect(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
