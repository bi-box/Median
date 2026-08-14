package com.xinyv.median;

public final class InitialNavigationGuardSelfTest {
    public static void main(String[] args) {
        InitialNavigationGuard guard = new InitialNavigationGuard();
        reject(guard.arm("https://median.invalid/") == 0L, "network URL arms guard");
        long first = guard.pendingGeneration();
        expect(first > 0L, "pending generation");
        expect(guard.arm("https://median.invalid/") == 0L, "same URL does not duplicate watchdogs");
        String retry = guard.claimRetry(first);
        expect("https://median.invalid/".equals(retry), "first retry URL");
        expect(guard.claimRetry(first).length() == 0, "recovery is one-shot");

        InitialNavigationGuard replaced = new InitialNavigationGuard();
        long old = replaced.arm("https://old.example/");
        long current = replaced.arm("https://current.example/");
        expect(current > old, "new destination replaces old generation");
        expect(replaced.claimRetry(old).length() == 0, "stale retry rejected");
        expect("https://current.example/".equals(replaced.claimRetry(current)), "current retry retained");

        InitialNavigationGuard acknowledged = new InitialNavigationGuard();
        long armed = acknowledged.arm("https://example.com/");
        expect(acknowledged.acknowledge("https://redirect.example/"),
                "qualified network progress acknowledges first navigation");
        expect(acknowledged.isAcknowledged(), "redirect progress acknowledges first navigation");
        expect(acknowledged.claimRetry(armed).length() == 0, "acknowledged navigation never retries");
        expect(acknowledged.arm("https://later.example/") == 0L, "guard retires after first start");

        InitialNavigationGuard queued = new InitialNavigationGuard();
        long queuedGeneration = queued.arm("https://queued.example/");
        reject(queued.isAcknowledged(), "queued load is not a main-frame start");
        expect("https://queued.example/".equals(queued.claimRetry(queuedGeneration)),
                "10-percent stall remains eligible for reload recovery");

        InitialNavigationGuard local = new InitialNavigationGuard();
        expect(local.arm("about:blank") == 0L, "local page does not arm network guard");

        InitialNavigationGuard alreadyStarted = new InitialNavigationGuard();
        alreadyStarted.acknowledge("https://restored.example/");
        expect(alreadyStarted.arm("https://restored.example/") == 0L,
                "synchronous restored start must not schedule a duplicate load");
        System.out.println("InitialNavigationGuardSelfTest passed");
    }

    private static void expect(boolean value, String label) {
        if (!value) throw new AssertionError("Expected: " + label);
    }

    private static void reject(boolean value, String label) {
        if (value) throw new AssertionError("Unexpected: " + label);
    }
}
