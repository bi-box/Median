package com.xinyv.median;

public final class EdgeNavigationPolicySelfTest {
    public static void main(String[] args) {
        equals(EdgeNavigationPolicy.BACK,
                EdgeNavigationPolicy.start(10f, 400f, 36f, true, true), "left edge");
        equals(EdgeNavigationPolicy.FORWARD,
                EdgeNavigationPolicy.start(390f, 400f, 36f, true, true), "right edge");
        equals(EdgeNavigationPolicy.NONE,
                EdgeNavigationPolicy.start(200f, 400f, 36f, true, true), "page center");
        equals(EdgeNavigationPolicy.NONE,
                EdgeNavigationPolicy.start(10f, 400f, 36f, false, true), "no back history");
        check(EdgeNavigationPolicy.shouldCancel(EdgeNavigationPolicy.BACK, -12f, 0f, 8f, 18f),
                "opposite direction cancels");
        check(EdgeNavigationPolicy.shouldCancel(EdgeNavigationPolicy.BACK, 15f, 36f, 8f, 28f),
                "vertical scroll cancels");
        check(EdgeNavigationPolicy.shouldArm(EdgeNavigationPolicy.BACK, 45f, 20f, 40f),
                "deliberate back swipe arms");
        check(!EdgeNavigationPolicy.shouldArm(EdgeNavigationPolicy.FORWARD, 60f, 0f, 52f),
                "wrong forward direction stays idle");
        equals(32f, EdgeNavigationPolicy.activationDistance(320f, 32f, 60f), "minimum threshold");
        equals(35f, EdgeNavigationPolicy.activationDistance(500f, 32f, 60f), "adaptive threshold");
        equals(60f, EdgeNavigationPolicy.activationDistance(1000f, 32f, 60f), "maximum threshold");
        check(EdgeNavigationPolicy.quickEnough(1600L) && !EdgeNavigationPolicy.quickEnough(1601L),
                "gesture timeout");
        System.out.println("EdgeNavigationPolicySelfTest passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    private static void equals(int expected, int actual, String label) {
        if (expected != actual) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }

    private static void equals(float expected, float actual, String label) {
        if (Math.abs(expected - actual) > .001f)
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
}
