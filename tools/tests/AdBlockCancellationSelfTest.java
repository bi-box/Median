package com.xinyv.median;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Tests production compilation cancellation without allowing a partial filtering snapshot. */
public final class AdBlockCancellationSelfTest {
    private static int checks;

    public static void main(String[] args) {
        AdBlockEngine engine = new AdBlockEngine();
        engine.updateCustomRules("||retained.example^\npage.example##.manual");
        StringBuilder source = new StringBuilder(8000000);
        for (int i = 0; i < 300000; i++) source.append("||tracker").append(i).append(".example^\n");
        final String rules = source.toString();
        AtomicInteger polls = new AtomicInteger();
        BooleanSupplier stopDuringParse = () -> polls.incrementAndGet() >= 6;
        AdBlockEngine.CompiledRules abandoned = engine.compileRules("", Collections.singletonList(rules), stopDuringParse);
        check(abandoned == null && polls.get() == 6, "compilation stops during source parsing");
        check(engine.shouldBlock("https://retained.example/a", "page.example"), "old network protection stays active");
        check(engine.cosmeticCssForHost("page.example").contains(".manual"), "old cosmetic protection stays active");
        check(!engine.shouldBlock("https://tracker0.example/a", "page.example"), "partial subscriptions never published");
        check(engine.compileRules("||new.example^", null, () -> true) == null, "pre-cancelled custom phase is discarded");

        AdBlockRuleReload reload = new AdBlockRuleReload(engine);
        long oldTicket = reload.request();
        AtomicInteger editPolls = new AtomicInteger();
        AdBlockEngine.CompiledRules superseded = engine.compileRules("", Collections.singletonList(rules), () -> {
            if (editPolls.incrementAndGet() == 5) reload.request();
            return !reload.isCurrent(oldTicket);
        });
        check(superseded == null && !reload.publish(oldTicket, AdBlockRuleReload.FULL, superseded),
                "superseded compilation cannot publish or clear the current snapshot");
        long latest = reload.request();
        AdBlockEngine.CompiledRules current = engine.compileRules("||new.example^", null, () -> !reload.isCurrent(latest));
        check(current != null && reload.publish(latest, AdBlockRuleReload.FULL, current), "latest edit still publishes");
        check(engine.shouldBlock("https://new.example/a", "page.example"), "latest protection is active");

        AtomicInteger interruptPolls = new AtomicInteger();
        AdBlockEngine.CompiledRules interrupted = engine.compileRules("", Collections.singletonList(rules), () -> {
            if (interruptPolls.incrementAndGet() == 5) Thread.currentThread().interrupt();
            return false;
        });
        check(interrupted == null && Thread.interrupted(), "worker interruption stops parsing and remains observable");

        AdBlockEngine.CompiledRules uncancelled = engine.compileRules("||custom.example^", Collections.singletonList(rules), () -> false);
        check(uncancelled != null && uncancelled.count == 300001, "uncancelled compile retains every rule");
        engine.applyCompiledRules(uncancelled);
        check(engine.shouldBlock("https://tracker299999.example/a", "page.example"), "last subscription rule retained");
        check(engine.shouldBlock("https://custom.example/a", "page.example"), "custom rule retained");
        System.out.println("AdBlockCancellationSelfTest passed (" + checks + " checks)");

        // A JVM workload, not an Android page-load claim. Both paths receive identical input;
        // full builds a valid snapshot and cancelled exits after several parsing batches.
        long[] full = new long[5], cancelled = new long[5];
        for (int i = 0; i < 5; i++) {
            long began = System.nanoTime();
            AdBlockEngine.CompiledRules complete = engine.compileRules("", Collections.singletonList(rules), () -> false);
            full[i] = System.nanoTime() - began;
            check(complete.count == 300000, "benchmark complete snapshot");
            AtomicInteger p = new AtomicInteger();
            began = System.nanoTime();
            check(engine.compileRules("", Collections.singletonList(rules), () -> p.incrementAndGet() >= 6) == null,
                    "benchmark cancelled snapshot");
            cancelled[i] = System.nanoTime() - began;
        }
        java.util.Arrays.sort(full); java.util.Arrays.sort(cancelled);
        System.out.println("CancellationBenchmark rules=300000 rounds=5 fullMedianMs=" + full[2] / 1e6 +
                " cancelledMedianMs=" + cancelled[2] / 1e6);
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
