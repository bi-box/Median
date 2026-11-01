package com.xinyv.median;

/** Latest-request publication guard; compilation and disk reads never hold this monitor. */
final class AdBlockRuleReload {
    static final int CUSTOM = 1;
    static final int FULL = 2;

    private final AdBlockEngine engine;
    private long generation;
    private int publishedPhase;

    AdBlockRuleReload(AdBlockEngine engine) { this.engine = engine; }

    synchronized long request() {
        publishedPhase = 0;
        return ++generation;
    }

    synchronized boolean publish(long ticket, int phase, AdBlockEngine.CompiledRules compiled) {
        if (ticket != generation || phase <= publishedPhase || compiled == null) return false;
        engine.applyCompiledRules(compiled);
        publishedPhase = phase;
        return true;
    }

    synchronized boolean isCurrent(long ticket) { return ticket == generation; }

    synchronized void cancel() { generation++; publishedPhase = 0; }
}
