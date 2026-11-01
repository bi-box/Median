package com.xinyv.median;

public final class UiTextSelfTest {
    public static void main(String[] args) {
        check("A😀…".equals(UiText.ellipsize("A😀B", 2)), "emoji ellipsis");
        check("👨‍💻".equals(UiText.firstCodePoint("👨‍💻", "•")), "first grapheme");
        check("🇨🇳".equals(UiText.firstCodePoint("🇨🇳中国", "•")), "regional indicator flag");
        check("1️⃣".equals(UiText.firstCodePoint("1️⃣ item", "•")), "keycap grapheme");
        check("…".equals(UiText.ellipsize("😀", 0)), "zero limit");
        check("😀".equals(UiText.truncate("😀B", 1)), "safe truncate");
        check("•".equals(UiText.firstCodePoint("", "•")), "fallback");
        System.out.println("UiText self-test passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
