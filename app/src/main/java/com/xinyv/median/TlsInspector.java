package com.xinyv.median;

import java.net.URL;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;

/** On-demand independent TLS probe used only after an explicit user action. */
final class TlsInspector {
    static final class Result {
        String host;
        String cipher;
        String subject;
        String issuer;
        String validFrom;
        String validUntil;
        String sha256;
        int responseCode;

        String summary() {
            return "主机：" + host + "\n响应：HTTP " + responseCode + "\n加密套件：" + cipher +
                    "\n\n证书主体：" + subject + "\n签发者：" + issuer +
                    "\n有效期：" + validFrom + " 至 " + validUntil + "\nSHA-256：\n" + sha256;
        }
    }

    private TlsInspector() {}

    static Result inspect(String rawUrl) throws Exception {
        URL url = new URL(rawUrl);
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IllegalArgumentException("当前页面不是 HTTPS");
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("HEAD");
            connection.setRequestProperty("User-Agent", "MedianBrowser/2.0 TLS inspector");
            Result result = new Result();
            result.host = url.getHost();
            result.responseCode = connection.getResponseCode();
            result.cipher = value(connection.getCipherSuite());
            Certificate[] chain = connection.getServerCertificates();
            if (chain.length == 0 || !(chain[0] instanceof X509Certificate)) throw new IllegalStateException("服务器没有返回 X.509 证书");
            X509Certificate certificate = (X509Certificate) chain[0];
            result.subject = certificate.getSubjectX500Principal().getName();
            result.issuer = certificate.getIssuerX500Principal().getName();
            result.validFrom = format(certificate.getNotBefore());
            result.validUntil = format(certificate.getNotAfter());
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            result.sha256 = HexCodec.encode(digest, digest.length, true, true);
            return result;
        } finally { connection.disconnect(); }
    }

    private static String format(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(date);
    }

    private static String value(String value) { return value == null ? "未知" : value; }
}
