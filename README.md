# Median Browser

一款面向 Android 8.0 及以上系统的超轻量 WebView 浏览器，重点关注极小安装体积、本地隐私、安全边界与可调性能。

> **当前版本：1.4.0（Release Candidate）**  
> 当前版本适合源码审查和测试，不应视为已经完成大规模真机验证的稳定版。

## 核心特性

- 多标签、标签冻结、会话恢复、书签、历史与站点设置
- 广告过滤、元素隐藏与跟踪参数清理
- 用户脚本、权限声明、菜单命令与受限 GM 网络请求
- 独立进程隐私窗口；无法可靠隔离时拒绝伪无痕降级
- Android Keystore 密码库与加密完整备份
- 分段下载、断点续传、暂停、重试与下载中心
- 阅读模式、系统朗读、翻译、页内查找、桌面模式与媒体发现
- 低功耗、标准、极限三档运行策略

## 三档性能模式

| 模式 | 主要策略 | 适用场景 |
|---|---|---|
| 低功耗 | 单热 WebView、关闭预热、降低后台线程与下载并发、积极释放后台标签 | 续航优先、低内存设备 |
| 标准 | 按设备内存动态保留 1–2 个热 WebView，平衡预热、下载与内存占用 | 日常使用 |
| 极限 | 高刷新率请求、ADPF 性能提示、离屏预栅格化、更多热标签与高并发下载 | 高性能设备、峰值响应优先 |

## 体积

专用 RC2 兼容构建信息：

- APK：`MedianBrowser-1.4.0-500kb-rc2.apk`
- 包名：`com.xinyv.median.compat`
- `minSdk 26`，`targetSdk 36`
- 签名 APK：**189,054 字节，约 184.6 KiB**

## 安全与隐私

Median 采用本地优先设计，不包含账户、广告 SDK 或统计 SDK。主要安全措施包括：

- 高权限用户脚本默认拒绝，缺少可靠 document-start 支持时保持关闭
- 脚本桥接使用随机令牌，并复核来源、匹配范围和权限
- 跨域请求逐跳验证，拒绝 HTTPS 降级和远程页面访问本机或私网
- 下载与订阅跨源重定向时移除 Cookie、Authorization 等凭据
- 离线页面关闭 JavaScript 和网络访问
- 隐私窗口无法建立独立 WebView 数据目录时直接拒绝启动

用户脚本属于第三方代码，只应安装可信来源的脚本。

## 构建

要求：JDK 17、Android SDK Platform 36、Build Tools 和 Gradle 8.13。

```bash
./tools/static_checks.sh
./build.sh
```

构建 500 KiB 目标 APK：

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
export MEDIAN_KEYSTORE=/path/to/release.p12
export MEDIAN_STOREPASS='...'
export MEDIAN_KEY_ALIAS='...'
export MEDIAN_KEYPASS='...'

./tools/build_500kb_apk.sh
```

仓库不包含正式发布密钥。

## 当前验证状态

已完成静态检查、Java 17 编译、R8 收缩、单 DEX 打包、APK 完整性检查和 v2/v3 签名验证。


## 许可

当前源码为 **source-available**，并非开放源代码许可证。除非获得版权所有者的书面许可，不得复制、修改、发布或分发。详见 `LICENSE`。
