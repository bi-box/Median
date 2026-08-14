package com.xinyv.median;

public final class CustomHomeCssSelfTest {
    public static void main(String[] args) {
        require(CustomHomeCss.valid(".brand{color:#123456}"), "safe CSS rejected");
        require(!CustomHomeCss.valid("@import 'https://example.com/x.css';"), "import accepted");
        require(!CustomHomeCss.valid(".x{background:url(https://example.com/x)}"), "URL accepted");
        require(!CustomHomeCss.valid(".x{background:u/**/rl(https://example.com/x)}"), "comment-obscured URL accepted");
        require(!CustomHomeCss.valid(".x{background:u\\72l(https://example.com/x)}"), "escaped URL accepted");
        require(!CustomHomeCss.valid("</style><script>alert(1)</script>"), "style escape accepted");
        require(!CustomHomeCss.valid("/* </style><script>alert(1)</script> */"), "commented style escape accepted");
        require(CustomHomeCss.clean(repeat('x', CustomHomeCss.MAX_BYTES + 1)).length() == 0, "size bound");
        System.out.println("CustomHomeCssSelfTest passed");
    }

    private static String repeat(char value, int count) {
        StringBuilder out = new StringBuilder(count);
        for (int i = 0; i < count; i++) out.append(value);
        return out.toString();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
