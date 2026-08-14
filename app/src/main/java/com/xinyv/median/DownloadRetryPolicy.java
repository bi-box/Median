package com.xinyv.median;

/** Small, deterministic HTTP retry policy for the internal downloader. */
final class DownloadRetryPolicy {
    private DownloadRetryPolicy() {}

    static boolean isRetryableHttp(int status) {
        return status == 408 || status == 425 || status == 429 ||
                (status >= 500 && status <= 599);
    }

    static String messageForHttp(int status) {
        if (status == 401 || status == 403) return "服务器拒绝访问（HTTP " + status + "）";
        if (status == 404 || status == 410) return "文件不存在或链接已失效（HTTP " + status + "）";
        if (status == 408) return "服务器响应超时（HTTP 408）";
        if (status == 416) return "服务器拒绝断点续传（HTTP 416）";
        if (status == 429) return "请求过于频繁（HTTP 429）";
        if (status >= 500 && status <= 599) return "服务器暂时不可用（HTTP " + status + "）";
        return "下载请求失败（HTTP " + status + "）";
    }
}
