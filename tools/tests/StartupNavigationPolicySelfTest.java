package com.xinyv.median;

public final class StartupNavigationPolicySelfTest {
    public static void main(String[] args) {
        equals("https://typed.example/", StartupNavigationPolicy.preferredInput(
                "https://typed.example/", "https://intent.example/"), "newest typed input wins");
        equals("https://intent.example/", StartupNavigationPolicy.preferredInput(
                "  ", "https://intent.example/"), "external intent fallback");
        equals("search words", StartupNavigationPolicy.preferredInput(
                "search words", ""), "search input preserved");
        equals("", StartupNavigationPolicy.preferredInput(null, null), "no direct navigation");
        System.out.println("StartupNavigationPolicySelfTest passed");
    }

    private static void equals(String expected, String actual, String label) {
        if (!expected.equals(actual))
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
}
