package com.xinyv.median;

/**
 * Order-independent cold-start gate. Android may deliver store callbacks before or after
 * onResume; the first WebView navigation is posted only when every required signal is present.
 */
final class StartupReadiness {
    static final int VIEW = 1;
    static final int DATA = 1 << 1;
    static final int FILTERS = 1 << 2;
    static final int COOKIES = 1 << 3;
    static final int RESUMED = 1 << 4;

    private final int required;
    private int ready;
    private boolean postClaimed;
    private boolean completed;

    StartupReadiness(int required) {
        if (required == 0) throw new IllegalArgumentException("required signals are empty");
        this.required = required;
    }

    void set(int signal, boolean value) {
        if (completed) return;
        if (value) ready |= signal;
        else ready &= ~signal;
    }

    boolean claimPost() {
        if (completed || postClaimed || (ready & required) != required) return false;
        postClaimed = true;
        return true;
    }

    /** Called from the posted first-frame callback, where readiness is checked a second time. */
    boolean begin() {
        postClaimed = false;
        if (completed || (ready & required) != required) return false;
        completed = true;
        return true;
    }

    boolean isCompleted() { return completed; }
}
