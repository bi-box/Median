package com.xinyv.median;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

final class DeviceProfile {
    final int sdk;
    final int memoryClassMb;
    final boolean lowRam;

    private DeviceProfile(int sdk, int memoryClassMb, boolean lowRam) {
        this.sdk = sdk;
        this.memoryClassMb = memoryClassMb;
        this.lowRam = lowRam;
    }

    static DeviceProfile detect(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        int memoryClass = manager == null ? 192 : manager.getMemoryClass();
        boolean low = manager != null && manager.isLowRamDevice();
        return new DeviceProfile(Build.VERSION.SDK_INT, memoryClass, low);
    }

    int hotWebViewLimit(String mode) {
        if (lowRam || memoryClassMb < 192 || "power_save".equals(mode)) return 1;
        if ("performance".equals(mode)) return memoryClassMb >= 512 ? 3 : 2;
        // A live WebView owns a renderer and is far more expensive than its Java object.
        // Standard mode favors a single hot renderer except on genuinely large heaps.
        return memoryClassMb >= 512 ? 2 : 1;
    }

    int coldTabStateLimit(String mode) {
        // saveState() can retain a sizeable back/forward tree. Keep recent navigation
        // continuity without allowing dozens of closed renderer snapshots to fill the heap.
        if (lowRam || memoryClassMb < 192 || "power_save".equals(mode)) return 1;
        if ("performance".equals(mode)) return memoryClassMb >= 512 ? 8 : 4;
        return memoryClassMb >= 384 ? 4 : 2;
    }

    String summary() {
        return "Android 8+ 优化路径 · API " + sdk + " · Java 堆上限 " + memoryClassMb + "MB" + (lowRam ? " · 低内存设备" : "");
    }
}
