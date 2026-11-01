package org.chromium.support_lib_glue;
import java.lang.reflect.InvocationHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Synthetic provider at the real reflection entry point; application classes remain unmodified. */
public final class SupportLibReflectionUtil {
    public static volatile String[] features = { "ASYNC_WEBVIEW_STARTUP_V2", "DOCUMENT_START_SCRIPT:1" };
    public static volatile int starts;
    public static volatile boolean throwOnStart;
    public static volatile CountDownLatch featureEntered;
    public static volatile CountDownLatch featureRelease;
    public static volatile Consumer<Consumer<BiConsumer<Integer, Object>>> success;
    public static volatile Consumer<Consumer<BiConsumer<Integer, Object>>> failure;
    public static volatile Map<Integer, Object> config;
    public static volatile boolean multiProcess = true;
    public static volatile boolean defaultProfileAvailable = true;
    public static volatile int staticsReads;
    public static volatile int profileStoreReads;
    public static volatile RuntimeException connectionFailure;
    public static volatile LinkageError connectionLinkageFailure;
    public static final List<String> preconnections = new ArrayList<String>();
    public static final List<String> enqueuedConnections = new ArrayList<String>();
    public static final List<String> profileNames = new ArrayList<String>();
    @SuppressWarnings("unchecked")
    public static InvocationHandler createWebViewProviderFactory() {
        return (proxy, method, args) -> {
            if (method.getName().equals("getSupportedFeatures")) {
                if (featureEntered != null) {
                    featureEntered.countDown();
                    if (!featureRelease.await(3, TimeUnit.SECONDS)) throw new AssertionError("feature gate timeout");
                }
                return features;
            }
            if (method.getName().equals("startUpWebView")) {
                starts++;
                config = new HashMap<Integer, Object>();
                ((Consumer<BiConsumer<Integer, Object>>) args[0]).accept((key, value) -> config.put(key, value));
                success = (Consumer<Consumer<BiConsumer<Integer, Object>>>) args[1];
                failure = (Consumer<Consumer<BiConsumer<Integer, Object>>>) args[2];
                if (throwOnStart) throw new IllegalStateException("provider start failed");
                return null;
            }
            if (method.getName().equals("getStatics")) {
                staticsReads++;
                return (InvocationHandler) (staticsProxy, staticsMethod, staticsArgs) -> {
                    if (staticsMethod.getName().equals("isMultiProcessEnabled")) return multiProcess;
                    throw new UnsupportedOperationException(staticsMethod.getName());
                };
            }
            if (method.getName().equals("getProfileStore")) {
                profileStoreReads++;
                return (InvocationHandler) (storeProxy, storeMethod, storeArgs) -> {
                    if (!storeMethod.getName().equals("getOrCreateProfile"))
                        throw new UnsupportedOperationException(storeMethod.getName());
                    profileNames.add((String) storeArgs[0]);
                    if (!defaultProfileAvailable) return null;
                    return (InvocationHandler) (profileProxy, profileMethod, profileArgs) -> {
                        if (connectionFailure != null) throw connectionFailure;
                        if (connectionLinkageFailure != null) throw connectionLinkageFailure;
                        if (profileMethod.getName().equals("preconnect")) {
                            preconnections.add((String) profileArgs[0]);
                            return null;
                        }
                        if (profileMethod.getName().equals("enqueuePreconnect")) {
                            enqueuedConnections.add((String) profileArgs[0]);
                            return null;
                        }
                        throw new UnsupportedOperationException(profileMethod.getName());
                    };
                };
            }
            throw new UnsupportedOperationException(method.getName());
        };
    }
}
