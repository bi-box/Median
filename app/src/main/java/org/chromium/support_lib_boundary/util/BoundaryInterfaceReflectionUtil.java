/* Copyright 2018 The Chromium Authors. BSD-style license. */
package org.chromium.support_lib_boundary.util;

import android.os.Build;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collection;

/** Focused copy of Chromium's support-library boundary reflection utility. */
public final class BoundaryInterfaceReflectionUtil {
    private BoundaryInterfaceReflectionUtil() {}

    public static <T> T castToSuppLibClass(Class<T> type, InvocationHandler handler) {
        if (handler == null) return null;
        Object proxy = Proxy.newProxyInstance(
                BoundaryInterfaceReflectionUtil.class.getClassLoader(),
                new Class<?>[] { type }, handler);
        return type.cast(proxy);
    }

    public static boolean containsFeature(Collection<String> features, String soughtFeature) {
        if (features == null || soughtFeature == null || soughtFeature.endsWith(":dev")) {
            return false;
        }
        if (features.contains(soughtFeature)) return true;
        return isDebuggable() && features.contains(soughtFeature + ":dev");
    }

    private static boolean isDebuggable() {
        return "eng".equals(Build.TYPE) || "userdebug".equals(Build.TYPE);
    }
}
