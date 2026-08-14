# Median Browser 2.1.11 签名更新包

本次交付包含 1.4.1 以来的稳定签名备份 `MedianBrowser-signing-backup.zip`。其证书 SHA-256 为 `80daf48c091d6174981c2a176360b42ac32463d23ddc9e5f3c95c0951e5e3da9`。稳定签名线上的旧版可直接覆盖安装 2.1.11，不要先卸载。

## 一键构建签名更新 APK

把 `MedianBrowser-signing-backup.zip` 放在本目录、上一级目录，或直接传路径：

```bash
./build-update.sh /path/to/MedianBrowser-signing-backup.zip
```

生成文件：

```text
out/500kb/MedianBrowser-2.1.11.apk
```

要求本机已安装 Android SDK / Build Tools，并设置 `ANDROID_SDK_ROOT` 或 `ANDROID_HOME`。

## 版本信息

- `applicationId`: `com.xinyv.median.compat`
- `versionCode`: `75`
- `versionName`: `2.1.11`

后续发布需继续使用同一签名备份，并把 `versionCode` 提升到 75 以上。
