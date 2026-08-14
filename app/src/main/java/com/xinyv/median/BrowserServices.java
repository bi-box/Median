package com.xinyv.median;

import android.content.Context;

/**
 * Owns feature services that are not needed to render the first page.
 *
 * Keeping these services lazy avoids file scans, preference decoding and worker-pool
 * allocation on the browser's startup path. MainActivity remains responsible for the
 * always-on navigation stores; this class owns only optional feature lifecycles.
 */
final class BrowserServices {
    private final Context context;
    private FilterSubscriptionStore filters;
    private ScriptValueStore scriptValues;
    private DownloadStore downloads;
    private OfflinePageStore offlinePages;
    private PageAssistant assistant;
    private PasswordVault passwords;
    private PerformanceMonitor performance;

    BrowserServices(Context context) {
        this.context = context.getApplicationContext();
    }

    synchronized ScriptValueStore scriptValues() {
        if (scriptValues == null) scriptValues = new ScriptValueStore(context);
        return scriptValues;
    }

    synchronized FilterSubscriptionStore filters() {
        if (filters == null) filters = new FilterSubscriptionStore(context);
        return filters;
    }

    synchronized DownloadStore downloads() {
        if (downloads == null) downloads = new DownloadStore(context);
        return downloads;
    }

    synchronized OfflinePageStore offlinePages() {
        if (offlinePages == null) offlinePages = new OfflinePageStore(context);
        return offlinePages;
    }

    synchronized PageAssistant assistant() {
        if (assistant == null) assistant = new PageAssistant(context);
        return assistant;
    }

    synchronized PasswordVault passwords() {
        if (passwords == null) passwords = new PasswordVault(context);
        return passwords;
    }

    synchronized PerformanceMonitor performance() {
        if (performance == null) performance = new PerformanceMonitor();
        return performance;
    }

    /** Decodes local list indexes after the first page, before their menus are opened. */
    synchronized void warmLocalIndexes() {
        if (downloads == null) downloads = new DownloadStore(context);
        if (offlinePages == null) offlinePages = new OfflinePageStore(context);
    }

    synchronized void trimMemory() {
        if (passwords != null) passwords.trimMemory();
    }

    synchronized void close() {
        if (filters != null) filters.close();
        if (assistant != null) assistant.shutdown();
        if (passwords != null) passwords.close();
        if (scriptValues != null) scriptValues.shutdown();
        assistant = null;
        passwords = null;
        filters = null;
        scriptValues = null;
        downloads = null;
        offlinePages = null;
        performance = null;
    }
}
