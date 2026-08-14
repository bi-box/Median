package com.xinyv.median;

/** Direct hexadecimal encoding without Formatter allocation or per-byte boxing. */
final class HexCodec {
    private HexCodec() {}

    static String encode(byte[] bytes, int limit, boolean upper, boolean colon) {
        int count = Math.min(bytes.length, Math.max(0, limit));
        char[] output = new char[count * 2 + (colon ? Math.max(0, count - 1) : 0)];
        String digits = upper ? "0123456789ABCDEF" : "0123456789abcdef";
        int position = 0;
        for (int i = 0; i < count; i++) {
            if (colon && i > 0) output[position++] = ':';
            int value = bytes[i] & 255;
            output[position++] = digits.charAt(value >>> 4);
            output[position++] = digits.charAt(value & 15);
        }
        return new String(output);
    }
}
