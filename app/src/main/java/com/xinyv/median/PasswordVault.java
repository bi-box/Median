package com.xinyv.median;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class PasswordVault {
    static final class Credential implements Cloneable {
        String id;
        String host;
        String username;
        String password;
        long updatedAt;

        Credential copy() {
            try { return (Credential) super.clone(); }
            catch (CloneNotSupportedException impossible) { throw new AssertionError(impossible); }
        }
    }

    interface Callback<T> {
        void onComplete(T value, Exception error);
    }

    private interface Operation<T> {
        T run() throws Exception;
    }

    private static final String KEY_ALIAS = "median_password_vault_v2_auth";
    private static final String LEGACY_KEY_ALIAS = "median_password_vault_v1";
    private static final String PREFS = "median_vault";
    private static final String DATA = "ciphertext";
    private static final String IV = "iv";
    private static final String HOST_INDEX = "host_index_v1";
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int MAX_CREDENTIALS = 500;

    private final SharedPreferences prefs;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(
            1, 1, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(32));
    private final Handler main = new Handler(Looper.getMainLooper());
    // Accessed only by the single vault worker. Decrypt/JSON parse happens once per warm session.
    private ArrayList<Credential> memoryCache;

    PasswordVault(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        worker.allowCoreThreadTimeOut(true);
    }

    void saveCredential(final String host, final String username, final String password, Callback<Void> callback) {
        submit(new Operation<Void>() {
            @Override public Void run() throws Exception {
                saveCredentialBlocking(host, username, password);
                return null;
            }
        }, callback);
    }

    void forHost(final String host, Callback<List<Credential>> callback) {
        submit(new Operation<List<Credential>>() {
            @Override public List<Credential> run() throws Exception {
                String normalized = normalizeHost(host);
                ArrayList<Credential> result = new ArrayList<Credential>();
                for (Credential item : ensureLoaded()) if (item.host.equals(normalized)) result.add(copy(item));
                Collections.sort(result, new Comparator<Credential>() {
                    @Override public int compare(Credential left, Credential right) {
                        return left.updatedAt == right.updatedAt ? 0 : (left.updatedAt < right.updatedAt ? 1 : -1);
                    }
                });
                return result;
            }
        }, callback);
    }

    boolean mightHaveForHost(String host) {
        String normalized = normalizeHost(host);
        if (!validHost(normalized)) return false;
        Set<String> index = prefs.getStringSet(HOST_INDEX, null);
        // Existing encrypted vaults predate the privacy-preserving host hash index. One unlock
        // rebuilds it; until then, do not silently suppress autofill on every saved site.
        if (index == null) return prefs.contains(DATA);
        return index.contains(hostKey(normalized));
    }

    void getAll(Callback<List<Credential>> callback) {
        submit(new Operation<List<Credential>>() {
            @Override public List<Credential> run() throws Exception { return copyList(ensureLoaded()); }
        }, callback);
    }

    void exportJson(Callback<String> callback) {
        submit(new Operation<String>() {
            @Override public String run() throws Exception {
                JSONArray array = new JSONArray();
                for (Credential credential : ensureLoaded()) {
                    JSONObject object = new JSONObject();
                    object.put("host", credential.host);
                    object.put("username", credential.username);
                    object.put("password", credential.password);
                    object.put("updatedAt", credential.updatedAt);
                    array.put(object);
                }
                return array.toString();
            }
        }, callback);
    }

    void importJson(final String raw, Callback<Integer> callback) {
        submit(new Operation<Integer>() {
            @Override public Integer run() throws Exception {
                if (raw == null || raw.length() > 8 * 1024 * 1024) throw new IllegalArgumentException("密码备份超过限制");
                JSONArray array = new JSONArray(raw);
                if (array.length() > MAX_CREDENTIALS) throw new IllegalArgumentException("密码条目超过 500 条");
                ArrayList<Credential> restored = new ArrayList<Credential>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject object = array.optJSONObject(i);
                    if (object == null) continue;
                    String host = normalizeHost(object.optString("host", ""));
                    String username = object.optString("username", "");
                    String password = object.optString("password", "");
                    if (!validHost(host) || username.length() == 0 || username.length() > 512 ||
                            password.length() == 0 || password.length() > 8192) throw new IllegalArgumentException("密码条目无效");
                    Credential credential = new Credential();
                    credential.id = String.valueOf(System.currentTimeMillis()) + "-" + i + "-" + UrlCleaner.stableId(host + "\n" + username);
                    credential.host = host;
                    credential.username = username;
                    credential.password = password;
                    credential.updatedAt = object.optLong("updatedAt", System.currentTimeMillis());
                    restored.add(credential);
                }
                writeAll(restored);
                return Integer.valueOf(restored.size());
            }
        }, callback);
    }

    void delete(final String id, Callback<Void> callback) {
        submit(new Operation<Void>() {
            @Override public Void run() throws Exception {
                ArrayList<Credential> all = ensureLoaded();
                for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).id.equals(id)) all.remove(i);
                writeAll(all);
                return null;
            }
        }, callback);
    }

    void trimMemory() {
        if (worker.isShutdown()) return;
        try {
            worker.execute(new Runnable() {
                @Override public void run() { memoryCache = null; }
            });
        } catch (RejectedExecutionException ignored) {
            // A queued vault mutation is more important than an advisory cache trim.
        }
    }

    void close() {
        memoryCache = null;
        worker.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }

    private <T> void submit(final Operation<T> operation, final Callback<T> callback) {
        if (worker.isShutdown()) return;
        try {
            worker.execute(new Runnable() {
                @Override public void run() {
                    try { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); }
                    catch (RuntimeException ignored) {}
                    T value = null;
                    Exception error = null;
                    try { value = operation.run(); }
                    catch (Exception e) { error = e; }
                    final T result = value;
                    final Exception failure = error;
                    if (callback != null) {
                        main.post(new Runnable() {
                            @Override public void run() { callback.onComplete(result, failure); }
                        });
                    }
                }
            });
        } catch (RejectedExecutionException busy) {
            if (callback != null) {
                main.post(new Runnable() {
                    @Override public void run() {
                        callback.onComplete(null, new IllegalStateException("密码库任务过多，请稍后重试"));
                    }
                });
            }
        }
    }

    private void saveCredentialBlocking(String host, String username, String password) throws Exception {
        String normalized = normalizeHost(host);
        if (!validHost(normalized) || username == null || username.length() == 0 ||
                username.length() > 512 || password == null || password.length() == 0 || password.length() > 8192) {
            throw new IllegalArgumentException("账号数据超过安全限制");
        }
        ArrayList<Credential> all = ensureLoaded();
        Credential target = null;
        for (Credential item : all) {
            if (item.host.equals(normalized) && item.username.equals(username)) {
                target = item;
                break;
            }
        }
        if (target == null) {
            if (all.size() >= MAX_CREDENTIALS) throw new IllegalStateException("密码库已达到 500 条上限");
            target = new Credential();
            target.id = String.valueOf(System.currentTimeMillis()) + "-" + UrlCleaner.stableId(normalized + "\n" + username);
            all.add(target);
        }
        target.host = normalized;
        target.username = username;
        target.password = password;
        target.updatedAt = System.currentTimeMillis();
        writeAll(all);
    }

    private ArrayList<Credential> ensureLoaded() throws Exception {
        if (memoryCache != null) return memoryCache;
        ArrayList<Credential> result = new ArrayList<Credential>();
        if (prefs.contains(DATA) && prefs.contains(IV)) {
            String json = decryptAndMigrate(prefs.getString(DATA, ""), prefs.getString(IV, ""));
            JSONArray array = new JSONArray(json);
            if (array.length() > MAX_CREDENTIALS) throw new IllegalStateException("密码库条目超过安全限制");
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                Credential credential = new Credential();
                credential.host = normalizeHost(object.optString("host", ""));
                credential.username = object.optString("username", "");
                credential.password = object.optString("password", "");
                if (!validHost(credential.host) || credential.username.length() == 0 || credential.username.length() > 512 ||
                        credential.password.length() == 0 || credential.password.length() > 8192)
                    throw new IllegalStateException("密码库内容损坏");
                credential.id = object.optString("id", "");
                if (credential.id.length() == 0 || credential.id.length() > 160)
                    credential.id = String.valueOf(i) + '-' + UrlCleaner.stableId(credential.host + "\n" + credential.username);
                credential.updatedAt = object.optLong("updatedAt", 0L);
                result.add(credential);
            }
        }
        memoryCache = result;
        writeHostIndex(result);
        return memoryCache;
    }

    private void writeAll(List<Credential> all) throws Exception {
        JSONArray array = new JSONArray();
        for (Credential credential : all) {
            JSONObject object = new JSONObject();
            object.put("id", credential.id);
            object.put("host", credential.host);
            object.put("username", credential.username);
            object.put("password", credential.password);
            object.put("updatedAt", credential.updatedAt);
            array.put(object);
        }
        encryptAndStore(array.toString());
        memoryCache = copyList(all);
        writeHostIndex(all);
    }

    private void writeHostIndex(List<Credential> all) {
        HashSet<String> hosts = new HashSet<String>();
        if (all != null) for (Credential credential : all)
            if (credential != null && validHost(credential.host)) hosts.add(hostKey(credential.host));
        prefs.edit().putStringSet(HOST_INDEX, hosts).apply();
    }

    private static String hostKey(String host) {
        return UrlCleaner.stableId("credential-host\n" + host);
    }

    private static ArrayList<Credential> copyList(List<Credential> all) {
        ArrayList<Credential> copy = new ArrayList<Credential>(all.size());
        for (Credential item : all) copy.add(copy(item));
        return copy;
    }

    private static Credential copy(Credential source) {
        return source.copy();
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(false);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            builder.setUserAuthenticationParameters(120,
                    KeyProperties.AUTH_BIOMETRIC_STRONG | KeyProperties.AUTH_DEVICE_CREDENTIAL);
        } else {
            builder.setUserAuthenticationValidityDurationSeconds(120);
        }
        generator.init(builder.build());
        return generator.generateKey();
    }

    private SecretKey existingKey(String alias) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias(alias)) return null;
        return ((KeyStore.SecretKeyEntry) keyStore.getEntry(alias, null)).getSecretKey();
    }

    private void encryptAndStore(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] plain = plainText.getBytes(UTF8);
        try {
            byte[] encrypted = cipher.doFinal(plain);
            if (!prefs.edit()
                    .putString(DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString(IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .commit()) throw new IllegalStateException("无法保存密码库");
        } finally { Arrays.fill(plain, (byte) 0); }
    }

    private String decryptAndMigrate(String cipherText, String ivText) throws Exception {
        if (cipherText.length() == 0 || ivText.length() == 0) return "[]";
        if (cipherText.length() > 12 * 1024 * 1024 || ivText.length() > 256) throw new IllegalStateException("密码库大小无效");
        SecretKey key = existingKey(KEY_ALIAS);
        boolean legacy = false;
        if (key == null) {
            key = existingKey(LEGACY_KEY_ALIAS);
            legacy = key != null;
        }
        if (key == null) throw new IllegalStateException("密码库密钥不可用");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP));
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        byte[] plain = cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP));
        String value;
        try { value = new String(plain, UTF8); }
        finally { Arrays.fill(plain, (byte) 0); }
        if (legacy) encryptAndStore(value);
        return value;
    }

    private String normalizeHost(String host) {
        String value = host == null ? "" : host.trim().toLowerCase(Locale.US);
        while (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static boolean validHost(String host) {
        if (host == null || host.length() == 0 || host.length() > 253) return false;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c) || c == '/' || c == '\\' || c == '@') return false;
        }
        return true;
    }
}
