package com.xinyv.median;

import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Password-encrypted portable backup envelope. No server or device-bound key is involved. */
final class PortableBackupCodec {
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int ITERATIONS = 210000;
    private static final byte[] AAD = "MedianBackup:1".getBytes(UTF8);

    private PortableBackupCodec() {}

    static byte[] encrypt(JSONObject payload, char[] password) throws Exception {
        requirePassword(password);
        byte[] salt = new byte[16];
        byte[] iv = new byte[12];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        random.nextBytes(iv);
        SecretKey key = derive(password, salt, ITERATIONS);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD);
            byte[] plain = payload.toString().getBytes(UTF8);
            byte[] encrypted;
            try { encrypted = cipher.doFinal(plain); }
            finally { Arrays.fill(plain, (byte) 0); }
            JSONObject envelope = new JSONObject();
            envelope.put("format", "median-encrypted-backup");
            envelope.put("version", 1);
            envelope.put("kdf", "PBKDF2-HMAC-SHA256");
            envelope.put("iterations", ITERATIONS);
            envelope.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP));
            envelope.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
            envelope.put("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP));
            return envelope.toString().getBytes(UTF8);
        } finally { Arrays.fill(password, '\0'); }
    }

    static JSONObject decrypt(byte[] encoded, char[] password) throws Exception {
        requirePassword(password);
        if (encoded == null || encoded.length == 0 || encoded.length > 20 * 1024 * 1024) throw new IllegalArgumentException("备份文件大小无效");
        try {
            JSONObject envelope = new JSONObject(new String(encoded, UTF8));
            if (!"median-encrypted-backup".equals(envelope.optString("format")) || envelope.optInt("version", 0) != 1)
                throw new IllegalArgumentException("不是受支持的 Median 加密备份");
            int iterations = envelope.optInt("iterations", 0);
            if (iterations < 100000 || iterations > 1000000) throw new IllegalArgumentException("备份密钥参数无效");
            byte[] salt = Base64.decode(envelope.getString("salt"), Base64.NO_WRAP);
            byte[] iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP);
            if (salt.length != 16 || iv.length != 12) throw new IllegalArgumentException("备份加密参数无效");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, derive(password, salt, iterations), new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD);
            byte[] plain = cipher.doFinal(encrypted);
            try {
                if (plain.length > 16 * 1024 * 1024) throw new IllegalArgumentException("解密数据超过限制");
                return new JSONObject(new String(plain, UTF8));
            }
            finally { Arrays.fill(plain, (byte) 0); }
        } finally { Arrays.fill(password, '\0'); }
    }

    private static SecretKey derive(char[] password, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try {
            byte[] bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            try { return new SecretKeySpec(bytes, "AES"); }
            finally { Arrays.fill(bytes, (byte) 0); }
        } finally { spec.clearPassword(); }
    }

    private static void requirePassword(char[] password) {
        if (password == null || password.length < 10 || password.length > 256) throw new IllegalArgumentException("备份口令需为 10–256 个字符");
    }
}
