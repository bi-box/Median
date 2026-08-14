package com.xinyv.median;

public final class StartupReadinessSelfTest {
    private static final int MAIN = StartupReadiness.VIEW | StartupReadiness.DATA | StartupReadiness.RESUMED;

    public static void main(String[] args) {
        testEverySignalOrder();
        testPauseBetweenPostAndDispatch();
        testExactlyOnce();
        testPrivateProfileSignals();
        System.out.println("StartupReadinessSelfTest passed");
    }

    private static void testEverySignalOrder() {
        int[][] orders = {
                { StartupReadiness.VIEW, StartupReadiness.DATA, StartupReadiness.RESUMED },
                { StartupReadiness.VIEW, StartupReadiness.RESUMED, StartupReadiness.DATA },
                { StartupReadiness.DATA, StartupReadiness.VIEW, StartupReadiness.RESUMED },
                { StartupReadiness.DATA, StartupReadiness.RESUMED, StartupReadiness.VIEW },
                { StartupReadiness.RESUMED, StartupReadiness.VIEW, StartupReadiness.DATA },
                { StartupReadiness.RESUMED, StartupReadiness.DATA, StartupReadiness.VIEW }
        };
        for (int[] order : orders) {
            StartupReadiness gate = new StartupReadiness(MAIN);
            gate.set(order[0], true);
            reject(gate.claimPost(), "posted with one signal");
            gate.set(order[1], true);
            reject(gate.claimPost(), "posted with two signals");
            gate.set(order[2], true);
            expect(gate.claimPost(), "all signal orders must post");
            expect(gate.begin(), "all signal orders must dispatch");
        }
    }

    private static void testPauseBetweenPostAndDispatch() {
        StartupReadiness gate = readyMain();
        expect(gate.claimPost(), "ready start posts");
        gate.set(StartupReadiness.RESUMED, false);
        reject(gate.begin(), "paused WebView must not navigate");
        gate.set(StartupReadiness.RESUMED, true);
        expect(gate.claimPost(), "resume reposts cancelled first frame");
        expect(gate.begin(), "resumed WebView navigates");
    }

    private static void testExactlyOnce() {
        StartupReadiness gate = readyMain();
        expect(gate.claimPost(), "first post");
        reject(gate.claimPost(), "duplicate post");
        expect(gate.begin(), "first dispatch");
        reject(gate.claimPost(), "post after completion");
        reject(gate.begin(), "dispatch after completion");
    }

    private static void testPrivateProfileSignals() {
        int required = StartupReadiness.VIEW | StartupReadiness.FILTERS |
                StartupReadiness.COOKIES | StartupReadiness.RESUMED;
        StartupReadiness gate = new StartupReadiness(required);
        gate.set(StartupReadiness.VIEW, true);
        gate.set(StartupReadiness.FILTERS, true);
        gate.set(StartupReadiness.COOKIES, true);
        reject(gate.claimPost(), "private page must wait for resume");
        gate.set(StartupReadiness.RESUMED, true);
        expect(gate.claimPost(), "private page posts after resume");
        expect(gate.begin(), "private page dispatches once");
    }

    private static StartupReadiness readyMain() {
        StartupReadiness gate = new StartupReadiness(MAIN);
        gate.set(StartupReadiness.VIEW, true);
        gate.set(StartupReadiness.DATA, true);
        gate.set(StartupReadiness.RESUMED, true);
        return gate;
    }

    private static void expect(boolean value, String label) {
        if (!value) throw new AssertionError("Expected: " + label);
    }

    private static void reject(boolean value, String label) {
        if (value) throw new AssertionError("Unexpected: " + label);
    }
}
