package com.xinyv.median;

public final class StartupScriptRegistrationSelfTest {
    private static int assertions;

    public static void main(String[] args) {
        dataBeforeView();
        viewBeforeData(false);
        viewBeforeData(true);
        withoutScripts(true);
        withoutScripts(false);
        unsupportedProvider();
        System.out.println("StartupScriptRegistrationSelfTest passed: " + assertions +
                " assertions / 6 startup orderings");
    }

    private static void dataBeforeView() {
        StartupScriptRegistrationHarness app = new StartupScriptRegistrationHarness();
        app.dataReady(true);
        expect(app.retainedPayloads() == 1 && app.retainedToken().length() >= 32,
                "data arriving without a view must retain its prepared payload");
        WebView first = app.configureFirst();
        expect(first.adds == 1 && first.active == 1, "first view installs the prepared registration");
        expect(app.builds() == 0, "first view must not rebuild the background payload on UI");
        String token = app.token(first);
        app.complete();
        app.repeatPrepared();
        app.complete();
        expect(first.adds == 1 && first.removes == 0 && first.active == 1,
                "completion and duplicate callbacks must retain the first registration");
        expect(token.equals(app.token(first)), "duplicate callbacks must retain the bridge token");
        expect(app.retainedPayloads() == 0 && app.retainedToken().isEmpty(),
                "consumed prepared source and token must be released");
        assertNewTab(app, first);
    }

    private static void viewBeforeData(boolean completeBeforeData) {
        StartupScriptRegistrationHarness app = new StartupScriptRegistrationHarness();
        WebView first = app.configureFirst();
        expect(first.adds == 0 && !app.registered(first), "view must not consume an unread payload");
        if (completeBeforeData) app.complete();
        app.dataReady(true);
        String token = app.token(first);
        app.complete();
        app.repeatPrepared();
        expect(first.adds == 1 && first.removes == 0 && first.active == 1,
                "late data must register exactly once across startup completion");
        expect(token != null && token.equals(app.token(first)), "late prepared token must survive completion");
        expect(app.builds() == 0, "late data must use background payload without a UI rebuild");
        assertNewTab(app, first);
    }

    private static void withoutScripts(boolean dataFirst) {
        StartupScriptRegistrationHarness app = new StartupScriptRegistrationHarness();
        if (dataFirst) app.dataReady(false);
        WebView first = app.configureFirst();
        if (!dataFirst) app.dataReady(false);
        app.complete();
        app.repeatPrepared();
        expect(first.adds == 0 && first.removes == 0 && first.active == 0,
                "empty startup must not create registrations");
        expect(app.token(first) == null && !app.registered(first), "empty startup must not enable bridge access");
        WebView second = app.newTab();
        expect(second.adds == 0 && app.builds() == 0, "empty ordinary tabs must remain cheap");
    }

    private static void unsupportedProvider() {
        WebViewFeature.supported = false;
        try {
            StartupScriptRegistrationHarness app = new StartupScriptRegistrationHarness();
            app.dataReady(true);
            WebView first = app.configureFirst();
            app.complete();
            app.repeatPrepared();
            expect(first.adds == 0 && !app.registered(first), "unsupported providers retain the fallback path");
            expect(app.token(first) == null, "unsupported providers must not expose a registered bridge token");
        } finally {
            WebViewFeature.supported = true;
        }
    }

    private static void assertNewTab(StartupScriptRegistrationHarness app, WebView first) {
        WebView second = app.newTab();
        expect(second.adds == 1 && second.active == 1 && app.registered(second),
                "ordinary new tabs must still register their own payload");
        expect(app.builds() == 1, "ordinary new tabs must use the normal builder");
        expect(!app.token(first).equals(app.token(second)), "new tabs must have a separate bridge token");
        expect(first.removes == 0 && first.active == 1, "new tabs must preserve the first view registration");
    }

    private static void expect(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
