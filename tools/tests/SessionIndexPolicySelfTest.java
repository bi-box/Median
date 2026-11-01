package com.xinyv.median;

public final class SessionIndexPolicySelfTest {
    public static void main(String[] args) {
        check(1, SessionIndexPolicy.remap(new boolean[] { false, true, true }, 2), "filtered before selected");
        check(1, SessionIndexPolicy.remap(new boolean[] { true, false, true }, 1), "rejected selected prefers next");
        check(0, SessionIndexPolicy.remap(new boolean[] { true, false }, 1), "rejected last falls left");
        check(0, SessionIndexPolicy.remap(new boolean[] { false, false }, 1), "all rejected");
        check(1, SessionIndexPolicy.remap(new boolean[] { true, true }, 99), "clamp high");
        System.out.println("Session index policy self-test passed");
    }

    private static void check(int expected, int actual, String label) {
        if (expected != actual) throw new AssertionError(label + ": " + actual);
    }
}
