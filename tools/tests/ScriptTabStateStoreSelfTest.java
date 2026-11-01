package com.xinyv.median;

public final class ScriptTabStateStoreSelfTest {
    public static void main(String[] args) {
        ScriptTabStateStore store = new ScriptTabStateStore();
        String oneMiB = repeat('x', 1024 * 1024);
        for (int i = 0; i < 5; i++) expect(store.put("script|tab" + i, oneMiB), "put tab " + i);
        reject(store.get("script|tab0") != null, "eldest entry must be evicted at the global cap");
        expect(store.get("script|tab4") != null, "newest entry remains available");
        expect(store.charCount() <= ScriptTabStateStore.MAX_CHARS, "character budget");

        store.removeTab("tab4");
        reject(store.get("script|tab4") != null, "closed-tab state removed");
        store.trimMemory();
        expect(store.charCount() <= ScriptTabStateStore.MAX_CHARS / 4, "pressure trim budget");
        store.clear();
        equals(0, store.size(), "clear count");
        equals(0, store.charCount(), "clear chars");
        System.out.println("ScriptTabStateStoreSelfTest passed");
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, value);
        return new String(chars);
    }

    private static void expect(boolean value, String label) {
        if (!value) throw new AssertionError("Expected: " + label);
    }

    private static void reject(boolean value, String label) {
        if (value) throw new AssertionError("Unexpected: " + label);
    }

    private static void equals(int expected, int actual, String label) {
        if (expected != actual) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
}
