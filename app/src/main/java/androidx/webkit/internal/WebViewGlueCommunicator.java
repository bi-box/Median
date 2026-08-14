/* Copyright 2018 The Android Open Source Project. Apache-2.0. */
package androidx.webkit.internal;

import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Looper;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.webkit.ScriptHandler;

import org.chromium.support_lib_boundary.ScriptHandlerBoundaryInterface;
import org.chromium.support_lib_boundary.WebSettingsBoundaryInterface;
import org.chromium.support_lib_boundary.WebViewProviderBoundaryInterface;
import org.chromium.support_lib_boundary.WebViewProviderFactoryBoundaryInterface;
import org.chromium.support_lib_boundary.WebkitToCompatConverterBoundaryInterface;
import org.chromium.support_lib_boundary.util.BoundaryInterfaceReflectionUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/** Minimal Chromium boundary used by Median for document-start and renderer darkening. */
public final class WebViewGlueCommunicator {
    private static final String DOCUMENT_START = "DOCUMENT_START_SCRIPT:1";
    private static volatile WebViewProviderFactoryBoundaryInterface factory;
    private static volatile String[] features;
    private static volatile String state = "尚未加载";
    private static volatile boolean registrationSucceeded;

    private WebViewGlueCommunicator() {}

    public static boolean isFeatureSupported(String feature) {
        load();
        if (has(feature)) return true;
        return DOCUMENT_START.equals(feature) && has("DOCUMENT_START_SCRIPT");
    }

    public static ScriptHandler addDocumentStartJavaScript(
            WebView webView, String script, String[] originRules) {
        if (Looper.myLooper() != Looper.getMainLooper())
            throw new IllegalStateException("document-start requires UI thread");
        load();
        if (factory == null) throw new UnsupportedOperationException(state);
        try {
            WebViewProviderBoundaryInterface provider = BoundaryInterfaceReflectionUtil.castToSuppLibClass(
                    WebViewProviderBoundaryInterface.class, factory.createWebView(webView));
            ScriptHandlerBoundaryInterface handler = provider == null ? null :
                    BoundaryInterfaceReflectionUtil.castToSuppLibClass(
                            ScriptHandlerBoundaryInterface.class,
                            provider.addDocumentStartJavaScript(script, originRules));
            if (handler == null) throw new UnsupportedOperationException("document-start handle unavailable");
            registrationSucceeded = true;
            return new Handler(handler);
        } catch (RuntimeException error) {
            state = error.getClass().getSimpleName();
            throw error;
        } catch (LinkageError error) {
            state = error.getClass().getSimpleName();
            throw new UnsupportedOperationException(state, error);
        }
    }

    public static void setAlgorithmicDarkeningAllowed(WebSettings settings, boolean allowed) {
        load();
        if (factory == null || !has("ALGORITHMIC_DARKENING"))
            throw new UnsupportedOperationException("algorithmic darkening unavailable");
        try {
            WebkitToCompatConverterBoundaryInterface converter =
                    BoundaryInterfaceReflectionUtil.castToSuppLibClass(
                            WebkitToCompatConverterBoundaryInterface.class,
                            factory.getWebkitToCompatConverter());
            WebSettingsBoundaryInterface boundary = converter == null ? null :
                    BoundaryInterfaceReflectionUtil.castToSuppLibClass(
                            WebSettingsBoundaryInterface.class, converter.convertSettings(settings));
            if (boundary == null) throw new UnsupportedOperationException("settings boundary unavailable");
            boundary.setAlgorithmicDarkeningAllowed(allowed);
        } catch (RuntimeException error) {
            state = error.getClass().getSimpleName();
            throw error;
        }
    }

    public static String diagnosticReport() {
        load();
        PackageInfo info = null;
        try { info = WebView.getCurrentWebViewPackage(); } catch (RuntimeException ignored) {}
        String provider = info == null ? "未知" : info.packageName + " " + info.versionName;
        return "System WebView: " + provider
                + "\nGlue: " + (factory == null ? "不可用" : "可用")
                + "\nDocument-start: " + (isFeatureSupported(DOCUMENT_START) ? "支持" : "不支持")
                + "\n注册验证: " + (registrationSucceeded ? "已成功" : "尚未成功")
                + "\n状态: " + state;
    }

    public static void invalidate() {
        synchronized (WebViewGlueCommunicator.class) {
            factory = null;
            features = null;
            state = "尚未加载";
            registrationSucceeded = false;
        }
    }

    private static boolean has(String feature) {
        String[] values = features;
        if (values == null) return false;
        for (String value : values) {
            if (feature.equals(value) || (("eng".equals(Build.TYPE) || "userdebug".equals(Build.TYPE))
                    && (feature + ":dev").equals(value))) return true;
        }
        return false;
    }

    private static void load() {
        if (features != null) return;
        synchronized (WebViewGlueCommunicator.class) {
            if (features != null) return;
            features = new String[0];
            try {
                ClassLoader loader;
                if (Build.VERSION.SDK_INT >= 28) loader = WebView.getWebViewClassLoader();
                else {
                    Method getFactory = WebView.class.getDeclaredMethod("getFactory");
                    getFactory.setAccessible(true);
                    Object provider = getFactory.invoke(null);
                    loader = provider == null ? null : provider.getClass().getClassLoader();
                }
                Class<?> glue = Class.forName(
                        "org.chromium.support_lib_glue.SupportLibReflectionUtil", false, loader);
                InvocationHandler handler = (InvocationHandler) glue
                        .getDeclaredMethod("createWebViewProviderFactory").invoke(null);
                factory = BoundaryInterfaceReflectionUtil.castToSuppLibClass(
                        WebViewProviderFactoryBoundaryInterface.class, handler);
                String[] advertised = factory == null ? null : factory.getSupportedFeatures();
                if (advertised != null) features = advertised;
                state = factory == null ? "factory unavailable" : "ok";
            } catch (Exception error) {
                state = error.getClass().getSimpleName();
            } catch (LinkageError error) {
                state = error.getClass().getSimpleName();
            }
        }
    }

    private static final class Handler implements ScriptHandler {
        private ScriptHandlerBoundaryInterface value;
        Handler(ScriptHandlerBoundaryInterface value) { this.value = value; }
        @Override public synchronized void remove() {
            ScriptHandlerBoundaryInterface current = value;
            value = null;
            if (current != null) try { current.remove(); } catch (RuntimeException ignored) {}
        }
    }
}
