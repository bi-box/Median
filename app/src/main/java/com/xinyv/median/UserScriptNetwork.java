// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Median Browser contributors

package com.xinyv.median;

import android.os.Handler;
import android.webkit.CookieManager;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/** Owns privileged userscript requests, cookie operations and their Activity lifetime. */
final class UserScriptNetwork {
    private static final int SCRIPT_XHR_MAX_REQUEST_BYTES = 4 * 1024 * 1024;
    private static final int SCRIPT_XHR_MAX_RESPONSE_BYTES = 16 * 1024 * 1024;
    private static final int SCRIPT_XHR_MAX_RESPONSE_HEADERS = 128 * 1024;
    private static final int SCRIPT_XHR_MAX_TIMEOUT_MS = 120000;
    private static final int SCRIPT_XHR_MAX_CONCURRENT_PER_SCRIPT = 6;

    interface Host {
        /** Returns false when saturated; throws when the Activity's executor is unavailable. */
        boolean executeNetworkTask(Runnable task);
        boolean canConnect(String scriptId, String url, String pageUrl);
        boolean isCurrentRequestDocument(WebView source, String token, String scriptId,
                                         long documentGeneration);
        void scheduleCookieFlush();
    }

    private final Handler uiHandler;
    private final Host host;
    private final Object lifecycle = new Object();
    private volatile boolean closed;
    private final ConcurrentHashMap<String, HttpURLConnection> scriptConnections =
            new ConcurrentHashMap<String, HttpURLConnection>();
    private final ConcurrentHashMap<String, AtomicInteger> scriptRequestCounts =
            new ConcurrentHashMap<String, AtomicInteger>();
    private final Set<Request> pendingRequests = ConcurrentHashMap.newKeySet();

    private static final class Request {
        final String token;
        final String callbackId;
        volatile boolean cancelled;
        Request(String token, String callbackId) { this.token = token; this.callbackId = callbackId; }
    }

    UserScriptNetwork(Handler uiHandler, Host host) {
        this.uiHandler = uiHandler;
        this.host = host;
    }

    /** Authentication and grant checks are performed by the bridge before calling this method. */
    /**
     * The caller supplies the current main-document generation. It intentionally is not derived
     * from the URL: history.pushState/replaceState keeps the same document while changing it.
     */
    String request(WebView source, String token, String scriptId, JSONObject args, String pageUrl,
                   long documentGeneration) {
        String url = args.optString("u", "");
        String callbackId = args.optString("i", "");
        String requestData = args.optString("d", "");
        int requestedTimeout = args.optInt("to", 0);
        boolean bodyShapeOkay = requestData.length() <= (SCRIPT_XHR_MAX_REQUEST_BYTES * 4 / 3 + 4096);
        boolean timeoutOkay = requestedTimeout <= SCRIPT_XHR_MAX_TIMEOUT_MS;
        boolean allowed = !closed && callbackId.matches("[A-Za-z0-9_-]{1,96}") && isHttpUrl(url) &&
                bodyShapeOkay && timeoutOkay && host.canConnect(scriptId, url, pageUrl) &&
                tryAcquireScriptRequest(token, scriptId);
        if (allowed) startScriptRequest(source, token, scriptId, callbackId, args, pageUrl,
                documentGeneration);
        return allowed ? bridgeOk(true) : bridgeError(bodyShapeOkay && timeoutOkay ?
                "@connect denied or too many requests" : "request limits exceeded");
    }

    void abort(String token, String callbackId) {
        synchronized (lifecycle) {
            for (Request request : pendingRequests)
                if (request.token.equals(token) && request.callbackId.equals(callbackId)) request.cancelled = true;
            HttpURLConnection connection = scriptConnections.remove(token + "|" + callbackId);
            if (connection != null) connection.disconnect();
        }
    }

    /** Cancels outstanding work without rejecting new requests using the same document token. */
    void cancel(String token) {
        if (token == null || token.length() == 0) return;
        synchronized (lifecycle) {
            for (Request request : pendingRequests)
                if (request.token.equals(token)) request.cancelled = true;
            String prefix = token + "|";
            for (Map.Entry<String, HttpURLConnection> entry :
                    new ArrayList<Map.Entry<String, HttpURLConnection>>(scriptConnections.entrySet())) {
                if (!entry.getKey().startsWith(prefix)) continue;
                HttpURLConnection connection = scriptConnections.remove(entry.getKey());
                if (connection != null) try { connection.disconnect(); } catch (RuntimeException ignored) {}
            }
        }
    }

    void close() {
        synchronized (lifecycle) {
            if (closed) return;
            closed = true;
            for (Request request : pendingRequests) request.cancelled = true;
            for (HttpURLConnection connection : scriptConnections.values())
                try { connection.disconnect(); } catch (RuntimeException ignored) {}
            scriptConnections.clear();
            scriptRequestCounts.clear();
        }
    }

    private void requireActive(Request request) throws IOException {
        if (closed || request.cancelled) throw new IOException("脚本读取已取消");
    }

    private void registerConnection(Request request, String key, HttpURLConnection connection) throws IOException {
        synchronized (lifecycle) {
            requireActive(request);
            scriptConnections.put(key, connection);
        }
    }

    String handleCookie(String pageUrl, JSONObject args) {
        if (closed) return bridgeError("cookie failed");
        try {
            URL page = NetworkSecurity.parseHttpUrl(pageUrl);
            String host = NetworkSecurity.normalizedHost(page);
            String operation = args.optString("op", "list");
            JSONObject details = args.optJSONObject("d");
            if (details == null) details = new JSONObject();
            CookieManager manager = CookieManager.getInstance();
            if ("list".equals(operation)) {
                JSONArray cookies = new JSONArray();
                String filter = details.optString("name", "");
                String header = manager.getCookie(pageUrl);
                if (header != null) for (String part : header.split(";\\s*")) {
                    int equals = part.indexOf('=');
                    String name = equals < 0 ? part.trim() : part.substring(0, equals).trim();
                    if (name.length() == 0 || (filter.length() > 0 && !filter.equals(name))) continue;
                    String value = equals < 0 ? "" : part.substring(equals + 1);
                    cookies.put(new JSONObject().put("name", name).put("value", value).put("domain", host)
                            .put("path", "/").put("secure", "https".equalsIgnoreCase(page.getProtocol()))
                            .put("session", true));
                }
                return new JSONObject().put("ok", true).put("v", cookies).toString();
            }
            String name = details.optString("name", "").trim();
            String value = details.optString("value", "");
            String path = details.optString("path", "/");
            String domain = details.optString("domain", "").trim().toLowerCase(Locale.US);
            if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,256}") || !safeCookiePart(value, false) ||
                    !safeCookiePart(path, true)) return bridgeError("invalid cookie");
            if (domain.startsWith(".")) domain = domain.substring(1);
            if (domain.length() > 0 && !(host.equals(domain) || host.endsWith("." + domain)))
                return bridgeError("cookie domain denied");
            StringBuilder cookie = new StringBuilder(name).append('=');
            if (!"delete".equals(operation)) cookie.append(value);
            cookie.append("; Path=").append(path);
            if (domain.length() > 0) cookie.append("; Domain=").append(domain);
            if ("delete".equals(operation)) cookie.append("; Max-Age=0");
            else {
                if (details.optBoolean("secure", false)) cookie.append("; Secure");
                if (details.has("expirationDate")) {
                    long maxAge = Math.max(0L, (long) details.optDouble("expirationDate", 0) - System.currentTimeMillis() / 1000L);
                    cookie.append("; Max-Age=").append(maxAge);
                }
                String sameSite = details.optString("sameSite", "");
                if ("strict".equalsIgnoreCase(sameSite)) cookie.append("; SameSite=Strict");
                else if ("lax".equalsIgnoreCase(sameSite)) cookie.append("; SameSite=Lax");
                else if ("none".equalsIgnoreCase(sameSite) && details.optBoolean("secure", false)) cookie.append("; SameSite=None");
            }
            if (!"set".equals(operation) && !"delete".equals(operation)) return bridgeError("unknown cookie action");
            manager.setCookie(pageUrl, cookie.toString());
            this.host.scheduleCookieFlush();
            return bridgeOk(true);
        } catch (Exception error) {
            return bridgeError("cookie failed");
        }
    }

    private static boolean safeCookiePart(String value, boolean path) {
        if (value == null || value.length() > 4096 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.indexOf(';') >= 0) return false;
        return !path || value.startsWith("/");
    }

    private boolean tryAcquireScriptRequest(String token, String scriptId) {
        String key = token + "|" + scriptId;
        synchronized (lifecycle) {
            if (closed) return false;
            AtomicInteger count = scriptRequestCounts.get(key);
            if (count == null) {
                count = new AtomicInteger();
                scriptRequestCounts.put(key, count);
            }
            if (count.get() >= SCRIPT_XHR_MAX_CONCURRENT_PER_SCRIPT) return false;
            count.incrementAndGet();
            return true;
        }
    }

    private void releaseScriptRequest(String token, String scriptId) {
        String key = token + "|" + scriptId;
        synchronized (lifecycle) {
            // Acquiring a new slot and unlinking an empty counter must be atomic:
            // otherwise a new request can increment a counter that is then removed.
            AtomicInteger count = scriptRequestCounts.get(key);
            if (count != null && count.decrementAndGet() <= 0) scriptRequestCounts.remove(key, count);
        }
    }

    private void startScriptRequest(final WebView source, final String token, final String scriptId,
                                    final String callbackId, final JSONObject args, final String pageUrl,
                                    final long requestDocumentGeneration) {
        final Request request = new Request(token, callbackId);
        synchronized (lifecycle) {
            if (closed) {
                releaseScriptRequest(token, scriptId);
                return;
            }
            pendingRequests.add(request);
        }
        Runnable task = new Runnable() {
            @Override public void run() {
                HttpURLConnection connection = null;
                String key = token + "|" + callbackId;
                try {
                    requireActive(request);
                    URL current = NetworkSecurity.parseHttpUrl(args.optString("u", ""));
                    URL initial = current;
                    URL page = NetworkSecurity.parseHttpUrl(pageUrl);
                    int requestedTimeout = args.optInt("to", 0);
                    int timeout = requestedTimeout <= 0 ? 20000 : Math.min(SCRIPT_XHR_MAX_TIMEOUT_MS, requestedTimeout);
                    String method = args.optString("m", "GET").toUpperCase(Locale.US);
                    if (!method.matches("GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS")) throw new IllegalArgumentException("unsupported method");
                    byte[] requestBody = args.optBoolean("b64", false) ?
                            android.util.Base64.decode(args.optString("d", ""), android.util.Base64.DEFAULT) :
                            args.optString("d", "").getBytes("UTF-8");
                    if (requestBody.length > SCRIPT_XHR_MAX_REQUEST_BYTES)
                        throw new IllegalArgumentException("request body exceeds limit");
                    JSONObject headerObject = args.optJSONObject("h");
                    boolean anonymous = args.optBoolean("anon", false);
                    int status = 0;
                    for (int redirects = 0; redirects <= NetworkSecurity.MAX_REDIRECTS; redirects++) {
                        requireActive(request);
                        if (!host.canConnect(scriptId, current.toString(), pageUrl)) throw new IllegalArgumentException("@connect denied after redirect");
                        boolean pageIsLocal = NetworkSecurity.isConfirmedLocalOrPrivateHost(NetworkSecurity.normalizedHost(page));
                        boolean targetIsLocal = NetworkSecurity.isLocalOrPrivateHost(NetworkSecurity.normalizedHost(current));
                        if (targetIsLocal && !pageIsLocal) throw new IllegalArgumentException("local network target denied");

                        requireActive(request);
                        connection = (HttpURLConnection) current.openConnection();
                        registerConnection(request, key, connection);
                        connection.setInstanceFollowRedirects(false);
                        connection.setConnectTimeout(timeout);
                        connection.setReadTimeout(timeout);
                        connection.setUseCaches(false);
                        connection.setRequestProperty("Accept-Encoding", "identity");
                        connection.setRequestMethod(method);
                        boolean sameInitialOrigin = NetworkSecurity.sameOrigin(initial, current);
                        if (headerObject != null) {
                            Iterator<String> names = headerObject.keys();
                            while (names.hasNext()) {
                                String name = names.next();
                                String value = headerObject.optString(name, "");
                                if (!NetworkSecurity.validHeader(name, value) || NetworkSecurity.isForbiddenRequestHeader(name)) continue;
                                if (!sameInitialOrigin && NetworkSecurity.isCredentialHeader(name)) continue;
                                connection.setRequestProperty(name, value);
                            }
                        }
                        if (!anonymous) {
                            String cookie = CookieManager.getInstance().getCookie(current.toString());
                            if (cookie != null && cookie.length() > 0) connection.setRequestProperty("Cookie", cookie);
                        }
                        // Configure output before connect(). Android's HttpURLConnection rejects
                        // setDoOutput(true) after the connection has been established; that made
                        // POST/PUT/PATCH requests from translation and other userscripts fail with
                        // "Already connected". The body is still written only after the second
                        // DNS/private-network check below.
                        boolean writesBody = requestBody.length > 0 && !"GET".equals(method) && !"HEAD".equals(method);
                        if (writesBody) connection.setDoOutput(true);
                        // This second DNS check is defense in depth. HttpURLConnection
                        // does not expose the socket peer, so re-resolution cannot prove
                        // the connected address or fully prevent DNS rebinding.
                        requireActive(request);
                        connection.connect();
                        requireActive(request);
                        boolean connectedTargetIsLocal = NetworkSecurity.isLocalOrPrivateHost(
                                NetworkSecurity.normalizedHost(current));
                        if (connectedTargetIsLocal && !pageIsLocal)
                            throw new IllegalArgumentException("local network target denied after connect");
                        if (writesBody) {
                            OutputStream requestOutput = connection.getOutputStream();
                            try { requestOutput.write(requestBody); } finally { requestOutput.close(); }
                        }

                        status = connection.getResponseCode();
                        if (!anonymous) storeResponseCookies(current, connection);
                        if (!NetworkSecurity.isRedirect(status)) break;
                        if (redirects == NetworkSecurity.MAX_REDIRECTS) throw new IllegalArgumentException("too many redirects");
                        URL next = NetworkSecurity.resolveRedirect(current, connection.getHeaderField("Location"), false);
                        connection.disconnect();
                        connection = null;
                        if ((status == 301 || status == 302 || status == 303) && !"GET".equals(method) && !"HEAD".equals(method)) {
                            method = "GET";
                            requestBody = new byte[0];
                        }
                        current = next;
                    }
                    if (connection == null) throw new IllegalStateException("request failed");
                    InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                    ByteArrayOutputStream output = new ByteArrayOutputStream(32768);
                    byte[] buffer = new byte[16384];
                    int read, total = 0;
                    long declared = connection.getContentLengthLong();
                    if (declared > SCRIPT_XHR_MAX_RESPONSE_BYTES)
                        throw new IllegalArgumentException("response body exceeds limit");
                    long lastProgress = 0;
                    if (input != null) try {
                        while ((read = input.read(buffer)) != -1) {
                            requireActive(request);
                            total += read;
                            if (total > SCRIPT_XHR_MAX_RESPONSE_BYTES)
                                throw new IllegalArgumentException("response body exceeds limit");
                            output.write(buffer, 0, read);
                            if (total - lastProgress >= 262144) {
                                lastProgress = total;
                                JSONObject progress = new JSONObject().put("loaded", total).put("total", Math.max(0, declared)).put("lengthComputable", declared > 0);
                                dispatchScriptEvent(source, token, scriptId, callbackId, requestDocumentGeneration,
                                        "progress", progress);
                            }
                        }
                    } finally { input.close(); }
                    requireActive(request);
                    byte[] bytes = output.toByteArray();
                    String responseType = args.optString("rt", "text");
                    String contentType = connection.getContentType() == null ? "" : connection.getContentType();
                    boolean binary = "arraybuffer".equalsIgnoreCase(responseType) || "blob".equalsIgnoreCase(responseType);
                    // Binary responses never expose responseText; avoid decoding up to
                    // 16 MiB into a temporary String that would immediately be discarded.
                    String text = binary ? "" : decodeResponseText(bytes, contentType);
                    JSONObject payload = new JSONObject();
                    payload.put("status", status);
                    payload.put("statusText", connection.getResponseMessage() == null ? "" : connection.getResponseMessage());
                    payload.put("finalUrl", connection.getURL().toString());
                    payload.put("responseHeaders", responseHeaders(connection));
                    payload.put("contentType", contentType);
                    payload.put("responseText", binary ? "" : text);
                    payload.put("response", binary ? android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP) : text);
                    dispatchScriptEvent(source, token, scriptId, callbackId, requestDocumentGeneration, "load", payload);
                } catch (java.net.SocketTimeoutException timeoutError) {
                    dispatchScriptEvent(source, token, scriptId, callbackId, requestDocumentGeneration,
                            request.cancelled ? "abort" : "timeout",
                            errorPayload(timeoutError));
                } catch (Exception error) {
                    String event = request.cancelled ? "abort" : "error";
                    dispatchScriptEvent(source, token, scriptId, callbackId, requestDocumentGeneration, event,
                            errorPayload(error));
                } finally {
                    scriptConnections.remove(key);
                    if (connection != null) connection.disconnect();
                    pendingRequests.remove(request);
                    releaseScriptRequest(token, scriptId);
                }
            }
        };
        final boolean submitted;
        try {
            submitted = host.executeNetworkTask(task);
        } catch (RejectedExecutionException unavailable) {
            pendingRequests.remove(request);
            releaseScriptRequest(token, scriptId);
            dispatchScriptEvent(source, token, scriptId, callbackId, requestDocumentGeneration, "error",
                    errorPayload(unavailable));
            return;
        }
        if (!submitted) {
            pendingRequests.remove(request);
            releaseScriptRequest(token, scriptId);
            // The bridge already acknowledged this request. Complete it asynchronously even
            // when the bounded executor is saturated, otherwise GM_xmlhttpRequest remains
            // pending forever with no onerror/onloadend notification.
            dispatchScriptEvent(source, token, scriptId, callbackId, requestDocumentGeneration, "error",
                    errorPayload(new RejectedExecutionException("脚本网络服务繁忙")));
        }
    }

    private JSONObject errorPayload(Exception error) {
        JSONObject payload = new JSONObject();
        try { payload.put("error", ErrorText.message(error, 180)); } catch (Exception ignored) {}
        return payload;
    }

    private void dispatchScriptEvent(final WebView source, final String token, final String scriptId,
                                     final String callbackId, final long requestDocumentGeneration,
                                     final String event, final JSONObject payload) {
        uiHandler.post(new Runnable() {
            @Override public void run() {
                if (closed || !host.isCurrentRequestDocument(source, token, scriptId,
                        requestDocumentGeneration)) return;
                String objectName = UserScriptStore.dispatchObjectName(token, scriptId);
                String js = "(function(){var d=window[" + JSONObject.quote(objectName) + "];if(d&&typeof d.dispatch==='function')d.dispatch(" +
                        JSONObject.quote(token) + "," + JSONObject.quote(callbackId) + "," + JSONObject.quote(event) + "," +
                        (payload == null ? "{}" : payload.toString()) + ");})();";
                try { source.evaluateJavascript(js, null); } catch (RuntimeException ignored) {}
            }
        });
    }

    private void storeResponseCookies(URL url, HttpURLConnection connection) {
        Map<String, List<String>> fields = connection.getHeaderFields();
        if (fields == null) return;
        CookieManager manager = CookieManager.getInstance();
        boolean changed = false;
        for (Map.Entry<String, List<String>> field : fields.entrySet()) {
            String name = field.getKey();
            if (name == null || field.getValue() == null ||
                    !("set-cookie".equalsIgnoreCase(name) || "set-cookie2".equalsIgnoreCase(name))) continue;
            for (String value : field.getValue()) {
                if (value != null && value.length() > 0) {
                    manager.setCookie(url.toString(), value);
                    changed = true;
                }
            }
        }
        if (changed) host.scheduleCookieFlush();
    }

    private static String decodeResponseText(byte[] bytes, String contentType) {
        Charset charset = StandardCharsets.UTF_8;
        if (contentType != null) {
            String[] parts = contentType.split(";");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.toLowerCase(Locale.US).startsWith("charset=")) continue;
                String name = trimmed.substring(8).trim().replace("\"", "");
                try { charset = Charset.forName(name); } catch (Exception ignored) {}
                break;
            }
        }
        return new String(bytes, charset);
    }

    private static String responseHeaders(HttpURLConnection connection) {
        StringBuilder out = new StringBuilder();
        Map<String, List<String>> fields = connection.getHeaderFields();
        if (fields == null) return "";
        int length = 0;
        for (Map.Entry<String, List<String>> field : fields.entrySet()) {
            if (field.getKey() == null || field.getValue() == null) continue;
            String lower = field.getKey().toLowerCase(Locale.US);
            if (lower.equals("set-cookie") || lower.equals("set-cookie2") || lower.equals("proxy-authenticate")) continue;
            for (String value : field.getValue()) {
                String line = field.getKey() + ": " + (value == null ? "" : value) + "\r\n";
                if (length + line.length() > SCRIPT_XHR_MAX_RESPONSE_HEADERS) {
                    out.append("X-Median-Headers-Truncated: true\r\n");
                    return out.toString();
                }
                out.append(line);
                length += line.length();
            }
        }
        return out.toString();
    }

    private static String bridgeOk(boolean ok) {
        try { return new JSONObject().put("ok", ok).toString(); }
        catch (Exception ignored) { return ok ? "{\"ok\":true}" : "{\"ok\":false}"; }
    }

    private static String bridgeError(String error) {
        try { return new JSONObject().put("ok", false).put("error", error).toString(); }
        catch (Exception ignored) { return "{\"ok\":false}"; }
    }

    private static boolean isHttpUrl(String value) {
        try {
            URL parsed = NetworkSecurity.parseHttpUrl(value);
            return !"median.invalid".equalsIgnoreCase(parsed.getHost());
        } catch (Exception ignored) { return false; }
    }

}
