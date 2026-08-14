# Release checklist — Google Play + GitHub

## 1. Identity and legal

- [ ] 确认应用名、applicationId `com.xinyv.median.compat`、开发者名称和商标不会与他人冲突。
- [ ] 选择并确认源码许可证；当前 `LICENSE` 为保留全部权利。
- [ ] 将 `PRIVACY_POLICY.md` 发布到永久公开 HTTPS URL，并确认商店开发者联系邮箱有效。
- [ ] 完成 Google Play 内容分级、目标受众、广告声明、应用访问权限、Data Safety，以及 dataSync 前台服务用途声明。

## 2. Signing

- [ ] 在离线或受控环境生成强密码上传密钥；不要使用仓库、聊天记录或 CI 日志保存明文密码。
- [ ] 启用 Play App Signing，并安全备份上传密钥与恢复资料。
- [ ] 在 GitHub Actions Secrets 配置 `MEDIAN_KEYSTORE_BASE64`、`MEDIAN_STOREPASS`、`MEDIAN_KEY_ALIAS`、`MEDIAN_KEYPASS`。
- [ ] 确认签名证书 SHA-256 指纹并写入私有发布记录。

## 3. Build gates

- [ ] `./tools/static_checks.sh` 通过。
- [ ] `./tools/verify.sh` 在有 Android SDK 的干净环境通过。
- [ ] Release AAB 和 APK 均由同一 tag、同一 commit、同一密钥生成。
- [ ] 检查 R8 mapping、native symbols（如未来加入 native 库）和 `SHA256SUMS.txt` 的归档。
- [ ] 从 GitHub Release 下载产物并复验 SHA-256，不只测试本机构建。

## 4. Device and website matrix

至少覆盖：

- [ ] Android 8/9、10/11、12/13、14、15、16；低内存和主流厂商系统。
- [ ] 当前稳定 System WebView、一个较旧但仍受支持的 WebView、WebView 更新前后升级。
- [ ] 冷启动、热启动、20/50 标签、进程被杀恢复、旋转、分屏、深色模式和大字体。
- [ ] HTTP、HTTPS、证书错误、重定向环、下载重定向、登录态下载、超大文件、无 Range 服务器、断网/切网/磁盘不足。
- [ ] 摄像头、麦克风、位置、文件选择、外部 Intent、弹窗、多窗口、全屏视频和画中画。
- [ ] 隐私窗口与普通窗口数据隔离；不支持隔离的设备必须明确拒绝。
- [ ] 用户脚本权限、`@connect`、私网阻断、重定向、菜单命令、剪贴板和下载。
- [ ] 密码保存/读取/删除、生物认证取消、系统锁屏变化、备份导入错误密码和损坏文件。

## 5. Play Console

- [ ] 上传 AAB 到 Internal testing，解决所有预发布报告崩溃、ANR、安全和兼容警告。
- [ ] 完成适用于账号类型的封闭测试要求，不直接跳到 Production。
- [ ] 上传至少手机截图、512×512 图标、1024×500 feature graphic，并检查所有素材没有侵权内容。
- [ ] 商店文案不使用“最安全”“最快”“超过 Via”等无法证明的绝对表述。
- [ ] 先小比例 staged rollout；监控 crash-free users、ANR、评分和关键兼容问题后再扩大。

## 6. GitHub

- [ ] tag 必须与 `app/build.gradle` 的 versionName 完全一致，如 `v2.1.11`。
- [ ] GitHub Release 同时提供签名 APK、AAB（可选，仅供 Play 上传）、SHA256SUMS、变更日志和已知问题。
- [ ] 开启 Private Vulnerability Reporting、Dependabot 和 branch protection。
- [ ] 不上传 keystore、密码、Play service account JSON、用户数据、构建缓存或测试站点凭据。
