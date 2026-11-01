// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Median Browser contributors

package com.xinyv.median;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Process;

import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Owns asynchronous rule compilation and rejects stale work across edits and teardown. */
final class AdBlockRuleLoader {
    interface TaskQueue {
        boolean execute(Runnable task);
    }

    interface Listener {
        void onPublished(boolean notifyUser);
        void onFailed();
    }

    private final AdBlockEngine engine;
    private final SharedPreferences prefs;
    private final BrowserServices services;
    private final Handler uiHandler;
    private final TaskQueue tasks;
    private final Listener listener;
    private final AdBlockRuleReload reload;
    private volatile boolean closed;

    AdBlockRuleLoader(AdBlockEngine engine, SharedPreferences prefs, BrowserServices services,
                      Handler uiHandler, TaskQueue tasks, Listener listener) {
        this.engine = engine;
        this.prefs = prefs;
        this.services = services;
        this.uiHandler = uiHandler;
        this.tasks = tasks;
        this.listener = listener;
        reload = new AdBlockRuleReload(engine);
    }

    void request(final boolean bootstrapCustom, final boolean notifyUser) {
        final long generation;
        synchronized (reload) {
            if (closed) return;
            generation = reload.request();
        }
        if (!tasks.execute(new Runnable() {
            @Override public void run() {
                // Rule parsing stays background work regardless of the performance preset.
                try { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); }
                catch (RuntimeException ignored) {}
                try {
                    rebuild(generation, bootstrapCustom, notifyUser);
                } catch (RuntimeException loadError) {
                    dispatch(generation, false, true);
                }
            }
        }) && bootstrapCustom) dispatch(generation, false, true);
    }

    private void rebuild(final long generation, boolean bootstrapCustom, boolean notifyUser) {
        if (!isCurrent(generation)) return;
        BooleanSupplier cancelled = new BooleanSupplier() {
            @Override public boolean getAsBoolean() { return !isCurrent(generation); }
        };
        String custom = prefs.getString("custom_filter_rules", "");
        if (bootstrapCustom) {
            AdBlockEngine.CompiledRules compiled = engine.compileRules(custom, Collections.<String>emptyList(), cancelled);
            if (compiled == null) { dispatch(generation, false, true); return; }
            if (!publish(generation, AdBlockRuleReload.CUSTOM, compiled)) return;
            dispatch(generation, false, false);
        }
        FilterSubscriptionStore store;
        synchronized (services) {
            // BrowserServices.close() uses this monitor. A closed loader must never
            // recreate the lazy filter store after its owner has been torn down.
            if (!isCurrent(generation)) return;
            store = services.filters();
        }
        if (!isCurrent(generation)) return;
        List<String> sources = store.readEnabledRuleSources();
        if (!isCurrent(generation)) return;
        AdBlockEngine.CompiledRules compiled = engine.compileRules(custom, sources, cancelled);
        if (compiled == null) { dispatch(generation, false, true); return; }
        if (publish(generation, AdBlockRuleReload.FULL, compiled))
            dispatch(generation, notifyUser, false);
    }

    private boolean publish(long generation, int phase, AdBlockEngine.CompiledRules compiled) {
        synchronized (reload) {
            return !closed && reload.publish(generation, phase, compiled);
        }
    }

    private boolean isCurrent(long generation) {
        return !closed && reload.isCurrent(generation);
    }

    private void dispatch(final long generation, final boolean notifyUser, final boolean failed) {
        uiHandler.post(new Runnable() {
            @Override public void run() {
                if (!isCurrent(generation)) return;
                if (failed) listener.onFailed();
                else listener.onPublished(notifyUser);
            }
        });
    }

    void close() {
        synchronized (reload) {
            if (closed) return;
            closed = true;
            reload.cancel();
        }
    }
}
