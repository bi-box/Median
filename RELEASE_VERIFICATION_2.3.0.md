# Median Browser 2.3.0 发布核验

## 安装包

- 文件：`MedianBrowser-2.3.0.apk`
- applicationId：`com.xinyv.median.compat`
- versionCode / versionName：`92` / `2.3.0`
- minSdk / targetSdk：`26` / `36`
- 大小：`219,605` 字节
- SHA-256：`dfb419b9559d5007759f80ae6c8e75d2046bca27921ebe1ef37724d0c26e541e`
- 签名：APK Signature Scheme v2，单一 RSA-3072 签名者
- 证书 SHA-256：`80daf48c091d6174981c2a176360b42ac32463d23ddc9e5f3c95c0951e5e3da9`
- 打包：延续项目原有 raw-DEFLATE / Stored 路径，没有删减资源或功能。

## 已自动验证

- 71 个 Java 源文件语法检查与全部 Android XML 解析。
- 冷启动就绪状态、首次网络导航守卫、主页可信命令、前进/后退、网址输入等策略测试。
- 主页生成 JavaScript 的语法与搜索、网址、搜索引擎、快捷收藏、文件夹入口行为测试。
- UserScript 生成 JavaScript 的语法及实际执行、幂等去重、GM 默认值、资源读取、菜单和原生桥调用测试。
- 密码识别、OTP/新密码排除、框架输入事件、不覆盖已有输入、多步登录、延迟自动填充信号、保存捕获和令牌轮换测试。
- 媒体分类、动态事件、Resource Timing、blob 去重、DOM/JSON-LD 发现及 HLS/DASH/Smooth 清单解析测试。
- R8 release 编译、APK 内容解压、4 字节 zipalign、v2 签名验证、包名/版本/SDK 元数据和 APK 文件类型测试。

## 真机重点复测

1. 强行停止应用后冷启动，在主页直接搜索和点击收藏，确认首个网页无需手动刷新。
2. 在常用脚本站验证 document-start、菜单命令、GM 值、`GM.xmlHttpRequest`、`@require` 与 `@resource`。
3. 在单页、多步、弹窗和 React/Vue 登录页验证自动填充、账号选择、保存更新与不覆盖手输内容。
4. 在 HLS、DASH、普通 MP4、音频及 blob 播放页打开媒体中心，核对直链、清单、分辨率和加密提示。
5. 覆盖旧版安装后确认书签、脚本、密码库、标签和下载记录保留。

当前构建环境没有 Android 模拟器或已连接真机，因此最后五项必须在目标设备完成；自动测试不能替代不同厂商 System WebView 的真机矩阵。
