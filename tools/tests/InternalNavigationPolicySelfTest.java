package com.xinyv.median;

public final class InternalNavigationPolicySelfTest {
    public static void main(String[] args) {
        require(InternalNavigationPolicy.canHandleCommand(true, false),
                "built-in homepage command was rejected by a mutable URL check");
        require(!InternalNavigationPolicy.canHandleCommand(false, false),
                "untrusted webpage received internal command access");
        require(!InternalNavigationPolicy.canHandleCommand(true, true),
                "custom homepage received internal command access");
        require(!InternalNavigationPolicy.shouldClearHomeTrust(false, "about:blank"),
                "transient blank callback cleared homepage trust");
        require(!InternalNavigationPolicy.shouldClearHomeTrust(false, "median://search?q=test"),
                "intercepted command cleared homepage trust");
        require(InternalNavigationPolicy.shouldClearHomeTrust(false, "https://example.com/"),
                "network navigation retained homepage trust");
        System.out.println("InternalNavigationPolicySelfTest passed");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
