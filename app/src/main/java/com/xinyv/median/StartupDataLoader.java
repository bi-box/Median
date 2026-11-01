package com.xinyv.median;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

import java.util.Collections;
import java.util.List;

/** Loads local startup state without owning navigation, Activity state, or an executor. */
final class StartupDataLoader implements AutoCloseable {
    interface TaskQueue {
        boolean execute(Runnable task);
    }

    interface Listener {
        void onConnectionHintsReady(ConnectionHintsStore hints);
        /** Return true to take ownership of the stores, or false to have them closed. */
        boolean onLoaded(Result result);
        void onFailed();
        /** Called by the last worker (or rejected submission), independently of the UI queue. */
        void onWorkersFinished();
    }

    static final class Result implements AutoCloseable {
        final BrowserDataStore data;
        final BookmarkFolderStore folders;
        final SiteSettingsStore sites;
        final UserScriptStore scripts;
        final ConnectionHintsStore connectionHints;
        final String scriptToken;
        final List<UserScriptStore.DocumentStartPayload> scriptPayloads;

        private Result(BrowserDataStore data, BookmarkFolderStore folders, SiteSettingsStore sites,
                       UserScriptStore scripts, ConnectionHintsStore connectionHints, String scriptToken,
                       List<UserScriptStore.DocumentStartPayload> scriptPayloads) {
            this.data = data;
            this.folders = folders;
            this.sites = sites;
            this.scripts = scripts;
            this.connectionHints = connectionHints;
            this.scriptToken = scriptToken;
            this.scriptPayloads = scriptPayloads;
        }

        private boolean isComplete() {
            return data != null && folders != null && sites != null && scripts != null;
        }

        @Override public void close() {
            if (data != null) data.close();
            if (sites != null) sites.close();
            if (scripts != null) scripts.close();
        }
    }

    private final Object lock = new Object();
    private final Context context;
    private final SharedPreferences prefs;
    private final Handler uiHandler;
    private final TaskQueue queue;
    private final Listener listener;
    private boolean started;
    private boolean closed;
    private int remaining = 2;
    // Retain provisional stores here, rather than only inside a posted message. Activity teardown
    // may clear the Handler queue before that message runs; close() must still find every store.
    private BrowserDataStore data;
    private BookmarkFolderStore folders;
    private SiteSettingsStore sites;
    private UserScriptStore scripts;
    private ConnectionHintsStore connectionHints;
    private String scriptToken = "";
    private List<UserScriptStore.DocumentStartPayload> scriptPayloads = Collections.emptyList();

    StartupDataLoader(Context applicationContext, SharedPreferences prefs, Handler uiHandler,
                      TaskQueue queue, Listener listener) {
        this.context = applicationContext.getApplicationContext();
        this.prefs = prefs;
        this.uiHandler = uiHandler;
        this.queue = queue;
        this.listener = listener;
    }

    void start() {
        synchronized (lock) {
            if (started || closed) return;
            started = true;
        }
        submit(new Runnable() {
            @Override public void run() { loadBrowserData(); }
        });
        submit(new Runnable() {
            @Override public void run() { loadScriptsAndSettings(); }
        });
    }

    private void submit(Runnable task) {
        boolean accepted = false;
        try {
            if (!isClosed()) accepted = queue.execute(task);
        } catch (RuntimeException ignored) {}
        if (!accepted) finishPart();
    }

    private boolean isClosed() {
        synchronized (lock) { return closed; }
    }

    private void loadBrowserData() {
        BrowserDataStore unclaimed = null;
        try {
            if (isClosed()) return;
            unclaimed = new BrowserDataStore(context);
            synchronized (lock) {
                if (closed) return;
                data = unclaimed;
                unclaimed = null;
            }
            if (isClosed()) return;
            BookmarkFolderStore decodedFolders = new BookmarkFolderStore(context);
            synchronized (lock) {
                if (!closed) folders = decodedFolders;
            }
        } catch (RuntimeException ignored) {
        } finally {
            if (unclaimed != null) unclaimed.close();
            finishPart();
        }
    }

    private void loadScriptsAndSettings() {
        SiteSettingsStore unclaimedSites = null;
        UserScriptStore unclaimedScripts = null;
        try {
            if (isClosed()) return;
            final ConnectionHintsStore hints = new ConnectionHintsStore();
            try {
                hints.restore(prefs.getString(NavigationConnectionHints.PREF_CONNECTION_HINTS, ""),
                        System.currentTimeMillis());
            } catch (RuntimeException ignored) {}
            synchronized (lock) {
                if (closed) return;
                connectionHints = hints;
                // Preserve the early publication before either settings or scripts are decoded.
                uiHandler.post(new Runnable() {
                    @Override public void run() {
                        synchronized (lock) {
                            if (!closed) listener.onConnectionHintsReady(hints);
                        }
                    }
                });
            }
            if (isClosed()) return;
            unclaimedSites = new SiteSettingsStore(context);
            synchronized (lock) {
                if (closed) return;
                sites = unclaimedSites;
                unclaimedSites = null;
            }
            if (isClosed()) return;
            unclaimedScripts = new UserScriptStore(context);
            if (isClosed()) return;
            String token = "";
            List<UserScriptStore.DocumentStartPayload> payloads = Collections.emptyList();
            try {
                if (unclaimedScripts.hasEnabledScripts()) {
                    token = UrlCleaner.randomToken();
                    payloads = unclaimedScripts.buildDocumentStartPayloads(token);
                }
            } catch (RuntimeException ignored) {
                token = "";
                payloads = Collections.emptyList();
            }
            synchronized (lock) {
                if (closed) return;
                scripts = unclaimedScripts;
                unclaimedScripts = null;
                scriptToken = token;
                scriptPayloads = payloads;
            }
        } catch (RuntimeException ignored) {
        } finally {
            if (unclaimedSites != null) unclaimedSites.close();
            if (unclaimedScripts != null) unclaimedScripts.close();
            finishPart();
        }
    }

    private void finishPart() {
        synchronized (lock) {
            if (--remaining != 0) return;
        }
        try {
            listener.onWorkersFinished();
        } finally {
            synchronized (lock) {
                if (!closed && !uiHandler.post(new Runnable() {
                    @Override public void run() { deliver(); }
                })) close();
            }
        }
    }

    private void deliver() {
        synchronized (lock) {
            if (closed) return;
            Result result = takeResultLocked();
            if (!result.isComplete()) {
                result.close();
                listener.onFailed();
                return;
            }
            // Remove provisional ownership before entering the listener: it may call close()
            // during Activity teardown. A refused or exceptional delivery still belongs to us.
            boolean accepted = false;
            try {
                accepted = listener.onLoaded(result);
            } finally {
                if (!accepted) result.close();
            }
        }
    }

    private Result takeResultLocked() {
        Result result = new Result(data, folders, sites, scripts, connectionHints, scriptToken,
                scriptPayloads);
        data = null;
        folders = null;
        sites = null;
        scripts = null;
        connectionHints = null;
        scriptToken = "";
        scriptPayloads = Collections.emptyList();
        return result;
    }

    /** Cancels publication; in-flight decodes dispose their result when they return. */
    @Override public void close() {
        Result abandoned;
        synchronized (lock) {
            if (closed) return;
            closed = true;
            abandoned = takeResultLocked();
        }
        abandoned.close();
    }
}
