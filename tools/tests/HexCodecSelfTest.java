package com.xinyv.median;

public final class HexCodecSelfTest {
    public static void main(String[] args) {
        byte[] value = { 0, 15, 16, (byte) 255 };
        if (!"000f10ff".equals(HexCodec.encode(value, value.length, false, false)))
            throw new AssertionError("lowercase hex mismatch");
        if (!"00:0F:10:FF".equals(HexCodec.encode(value, value.length, true, true)))
            throw new AssertionError("uppercase fingerprint mismatch");
        if (!"000f".equals(HexCodec.encode(value, 2, false, false)))
            throw new AssertionError("hex limit mismatch");
        System.out.println("HexCodecSelfTest passed");
    }
}
