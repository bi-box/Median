# Median Browser


## 项目介绍

Median Browser 是一款面向 Android 的轻量浏览器，目标不是堆叠大量在线服务，而是在尽可能小的体积内，提供快速、稳定、可控的网页浏览体验。它基于设备自带的 Android System WebView，尽量复用系统已经提供的渲染、安全与媒体能力，把更多精力放在标签管理、隐私控制、下载、用户脚本和低内存设备适配上。

项目采用本地优先设计：书签、历史记录、站点设置、用户脚本、过滤规则和备份默认保存在用户自己的设备中，不依赖账号体系、云同步、广告 SDK 或分析 SDK。对于希望浏览器保持简单、透明、低占用，并愿意自行管理数据的用户，Median 提供了一套比普通 WebView 套壳更完整的浏览器功能。

Median 的核心取向是 **快、稳、轻、可控**：

- **快**：缩短网页请求热路径，按设备能力预热和保留 WebView，减少主线程同步 I/O。
- **稳**：针对低内存、renderer 崩溃、后台标签和下载中断设计恢复策略。
- **轻**：控制 APK 体积和常驻资源，不依赖大型第三方运行库。
- **可控**：广告过滤、第三方 Cookie、弹窗、脚本、兼容模式等均可按网站调整。

它适合以下用户：

- 希望使用轻量浏览器，而不需要账号、推荐流或云服务的人；
- 在意本地数据、隐私控制和源码可审计性的人；
- 经常使用用户脚本、广告过滤、下载或多标签功能的人；
- 使用 Android 8.0 及以上设备，希望浏览器在低内存环境下仍保持稳定的人。

Median 不是独立浏览器内核。网页兼容性、JavaScript 性能、媒体格式和安全补丁仍取决于设备上的 Android System WebView。项目的目标是在这一基础上，提供一个更完整、更轻量、更可控的浏览器外壳。

当前版本：**2.1.9** 
最低系统：Android 8.0（API 26）  
目标系统：Android 16（API 36）

> Median 使用设备上的 Android System WebView。网页渲染能力、编解码器、部分网站兼容性和安全补丁取决于设备所安装的 WebView 版本。

## 核心能力

### 浏览与标签页

- 多标签浏览、固定标签、标签冻结和会话恢复。
- 书签、历史记录、站点设置及离线 MHTML 保存。
- 阅读模式、系统朗读、翻译、页内查找和桌面模式。
- 标签页根据设备内存分级保留，后台页面可冻结并按需重建。

### 隐私与安全

- 广告过滤订阅、元素隐藏和常见跟踪参数清理。
- HTTPS Only、Safe Browsing、严格混合内容策略和站点级兼容模式。
- 第三方 Cookie、弹窗、媒体自动播放等权限可按站点控制。
- 独立进程隐私窗口；无法可靠隔离时不伪装成无痕模式。
- Android Keystore 密码库及加密完整备份。
- 默认关闭危险的 `file://` 跨域访问和不受控本地文件访问。

### 用户脚本

- 支持用户脚本匹配、权限声明、菜单命令和受限 GM 网络请求。
- 高权限能力要求显式 `@grant`，并使用随机令牌、来源校验和脚本匹配范围复核。
- 跨域请求逐跳检查 `@connect`，阻止 HTTPS 降级及远程页面访问本机或私网地址。
- 脚本值先更新内存并异步落盘，减少 UI 线程同步 I/O。

### 下载

- Median 内部单连接下载，不依赖 Android DownloadManager。
- 支持断点续传、自动重试、真实进度、速度和预计剩余时间。
- 保留 Cookie、User-Agent 和安全的 Referer 策略。
- 自动识别最终文件名、MIME 类型和 APK 文件结构。
- 下载索引合并写入，暂停、完成和失败状态立即保存。

### 性能调度

提供低功耗、标准和极限三档策略：

- WebView 预热和热标签数量按设备内存动态调整。
- 当前页面 renderer 保持高优先级，后台页面主动降级或冻结。
- Android 12 及以上使用 Performance Hint API 提示主线程与 RenderThread 负载。
- 极限档可启用“网络直通”，跳过 Median 广告请求扫描，缩短请求热路径；开启后广告和跟踪过滤会减弱。
- 内存压力后暂时停止预热，避免低内存设备反复抖动。

### 个性化主页

- 自定义标题、副标题、Logo、壁纸、搜索框和快捷网站。
- 支持自定义文字配色、渐变、字距和本地 Logo 图片。
- 支持受限自定义 CSS，以及无联网、无同源权限的本地 HTML 模式。
- 用户图片保存在应用私有目录，不上传，也不嵌入 APK。

## 2.1.9 更新内容

- 修复部分设备上标签卡片突然无法点击的问题。
- 标签卡片直接处理点击和长按，不再依赖不稳定的 `ListView.onItemClick()`。
- 关闭按钮不再抢占整行焦点。
- 点击时根据标签对象重新定位索引，避免列表复用或标签变化造成位置失效。

本版本还包含以下快稳改进：

- 网站级兼容模式及 HTTPS 加载失败后的安全重试提示。
- 广告过滤状态缓存、按需读取请求头和可选媒体嗅探，减少请求热路径开销。
- Cookie 合并刷新和用户脚本数据异步写入。
- 更保守的 WebView 预热时机和 renderer 崩溃恢复。
- 后台任务拒绝时不再静默丢弃关键工作。

## 网站兼容性

Median 默认策略偏向隐私和安全，以下网站可能需要启用站点级兼容模式：

- 仍依赖 HTTP 或混合内容的旧网站。
- 依赖第三方 Cookie 的登录、支付或企业 SSO 页面。
- 被广告规则误伤的脚本、验证码或跳转页面。
- 依赖用户触发弹窗完成 OAuth 或支付授权的网站。

兼容模式只应对可信网站开启。Safe Browsing 和证书校验不应为追求兼容性而关闭。

## 本地构建

### 环境要求

- JDK 17
- Android SDK Platform 36
- Android Build Tools
- Gradle 8.13，或项目提供的 Gradle Wrapper

### 检查与构建

```bash
./tools/static_checks.sh
./build.sh
```

输出目录：

```text
app/build/outputs/apk/
app/build/outputs/bundle/
```

### 正式签名

```bash
export MEDIAN_KEYSTORE=/absolute/path/release-key.p12
export MEDIAN_STOREPASS='your-store-password'
export MEDIAN_KEY_ALIAS='your-key-alias'
export MEDIAN_KEYPASS='your-key-password'

./build.sh --release
```

仓库不包含发布密钥。要让未来正式发布版覆盖安装现有版本，必须同时满足：

1. `applicationId` 保持为 `com.xinyv.median.compat`；
2. `versionCode` 高于已安装版本；
3. 使用与旧版本相同的发布证书签名。

## 500 KiB 构建

项目提供严格体积构建脚本：

```bash
./tools/build_500kb_apk.sh
```

该脚本会构建确定性的签名 APK，并在最终 APK 超过 **500 KiB** 时使构建失败。项目内置精简的 AndroidX WebKit 兼容实现，以支持 document-start 脚本，同时避免引入无关运行库。

## 发布验证

正式发布前，至少应完成：

- `tools/static_checks.sh`
- Java、资源、DEX 和签名完整构建
- 多 Android 版本真机测试
- 多标签、视频、下载和用户脚本压力测试
- Google Play 预发布报告与封闭测试

相关文档：

- `RELEASE_CHECKLIST.md`：发布检查清单
- `VALIDATION.md`：当前验证状态
- `SECURITY.md`：安全边界与漏洞报告
- `PRIVACY_POLICY.md`：隐私政策
- `PLAY_DATA_SAFETY.md`：Google Play 数据安全申报参考
- `WEBKIT_COMPATIBILITY.md`：WebKit 兼容层说明
- `THIRD_PARTY_NOTICES.md`：第三方声明

## 隐私说明

Median 的书签、历史、站点设置、脚本、过滤规则和备份默认保存在本机。项目运行时不依赖广告或分析 SDK。网站本身、用户安装的脚本、搜索引擎和 Android System WebView 仍可能按照各自政策处理网络数据。

## 安全边界

- 用户脚本属于第三方代码，只应安装可信来源的脚本。
- 密码库使用 Android Keystore 和 AES-GCM，但未经独立安全审计，不应替代经过长期审计的专业密码管理器。
- 网站证书错误不会被自动忽略。
- 兼容模式会降低部分隐私保护，仅建议用于可信网站。

## 许可

Copyright © 2026 Median Browser contributors. All rights reserved.

源码仅供授权所有者审阅和构建。未经版权持有者书面许可，不授予复制、修改、发布、分发、再许可或销售权限。完整条款见 `LICENSE`。
