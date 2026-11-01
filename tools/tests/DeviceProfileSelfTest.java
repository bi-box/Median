package com.xinyv.median;

public final class DeviceProfileSelfTest {
    public static void main(String[] args) {
        equals(2, DeviceProfile.hotWebViewLimit(false, 1024, "standard"),
                "large-heap standard mode preserves one recent renderer");
        equals(3, DeviceProfile.hotWebViewLimit(false, 512, "performance"),
                "large-heap flow mode preserves two recent renderers");
        equals(2, DeviceProfile.hotWebViewLimit(false, 256, "performance"),
                "mid-heap flow mode preserves one recent renderer");
        equals(1, DeviceProfile.hotWebViewLimit(true, 1024, "performance"),
                "low-RAM devices always use one renderer");
        equals(4, DeviceProfile.coldTabStateLimit(false, 512, "standard"),
                "standard history hit window is preserved");
        equals(8, DeviceProfile.coldTabStateLimit(false, 512, "performance"),
                "flow mode preserves its prior recent history set");
        System.out.println("DeviceProfileSelfTest passed");
    }

    private static void equals(int expected, int actual, String label) {
        if (expected != actual) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
}
