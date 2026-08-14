# 🚀 MedianBrowser


<p align="center">
  <b>一个轻量、高性能、注重隐私与兼容性的 Android WebView 浏览器</b>
</p>

<p align="center">
  <i>Small footprint · Native Android · Powerful Web compatibility</i>
</p>

---

## 📢 官方社区

加入 MedianBrowser Beta Telegram 频道：

👉 https://t.me/MedianBeta

## 简介

MedianBrowser 是一款基于 Android 原生 WebView 构建的轻量级浏览器。

项目目标不是简单封装网页浏览能力，而是在极小体积下提供接近现代浏览器的核心体验，包括：

- 多标签浏览
- 独立隐私窗口
- 下载管理
- UserScript 用户脚本支持
- 密码自动填充
- 媒体资源检测
- HLS / DASH 清单分析
- 自定义主页
- 网站权限控制
- 高兼容 WebView 增强

项目针对 Android System WebView 的实际运行环境进行了大量优化，重点解决不同厂商 WebView 行为差异、冷启动失败、脚本注入不稳定等问题。
本项目对标（碰瓷）via，希望大家多提意见，一起助力median的伟大！！
---

## 项目特点

### 🚀 极致轻量

MedianBrowser 追求小体积和高性能：

- 原生 Android Java 开发
- 不依赖大型浏览器内核
- Release 构建开启代码压缩与资源优化
- 针对 APK 大小进行了持续优化

当前版本：

```
Version: 2.3.0
VersionCode: 92
Min SDK: Android 8.0 (API 26)
Target SDK: Android 16 (API 36)
```

---

## 核心功能

## 🌐 浏览体验

支持：

- 多标签页浏览
- 标签状态恢复
- 外部 HTTP/HTTPS 链接打开
- 前进、后退、刷新
- 页面缩放
- 桌面模式
- Picture-in-Picture 支持

浏览核心基于 Android WebView，并通过兼容层增强：

- WebView 特性检测
- ScriptHandler 支持
- WebViewCompat 扩展
- 厂商 WebView 行为适配

---

# 📜 UserScript 支持

MedianBrowser 内置用户脚本运行环境。

支持：

- `document-start`
- `document-body`
- URL 匹配规则
- 正则 flags
- 相对资源 URL
- GM API 兼容

包含：

- 脚本安装
- 脚本更新
- 脚本隔离运行
- 兼容旧 WebView 的降级方案

适合运行：

- 页面增强脚本
- 自动化脚本
- 网页功能修改脚本

---

# 🔐 密码自动填充

内置密码管理能力：

支持：

- HTTPS 自动填充
- 登录信息保存提示
- 多账号管理
- 多步骤登录
- Shadow DOM 输入框
- iframe 输入框

安全策略：

- 严格主机匹配
- 不覆盖已有输入
- 跳过 OTP 验证码输入
- 不自动提交表单
- 保存需要可信用户操作确认

---

# 🎬 媒体检测系统

MedianBrowser 可以分析网页中的媒体资源。

支持检测：

- 视频资源
- 音频资源
- Media 元素
- Shadow DOM
- 同源 iframe
- Resource Timing
- JSON 状态数据

支持媒体协议：

- HLS (`.m3u8`)
- MPEG-DASH (`.mpd`)
- Smooth Streaming

可显示：

- 分辨率
- 码率
- 轨道信息
- 分片信息
- 直播 / 点播状态
- 加密状态

---

# 📥 下载管理

内置下载中心：

支持：

- 文件下载
- 后台下载服务
- 下载状态管理
- 文件类型识别
- 下载权限控制

使用 Android 原生 DownloadService 架构。

---

# 🏠 自定义主页

主页采用本地生成模式：

特点：

- 无远程依赖
- 加载速度快
- 可自定义壁纸
- 支持快捷入口
- 支持搜索引擎切换

主页通信采用安全 token 校验机制，避免普通网页伪造内部命令。

---

# 🕵️ 隐私窗口

支持独立隐私浏览环境：

特点：

- 独立进程
- 不进入普通任务列表
- 独立 Cookie / 状态环境

---

# 🛠 技术架构

## 开发环境

| 项目 | 信息 |
|-|-|
| 语言 | Java |
| 构建工具 | Gradle |
| Android Gradle Plugin | 8.13.2 |
| Java | 17 |
| Compile SDK | 36 |

---

## 项目结构

```
MedianBrowser
│
├── app
│   ├── src/main/java/com/xinyv/median
│   │
│   ├── WebView 兼容层
│   ├── 下载系统
│   ├── UserScript 引擎
│   ├── 密码管理
│   ├── 媒体分析
│   ├── 标签管理
│   └── 隐私窗口
│
├── tools
│   ├── 自动测试
│   ├── APK 构建工具
│   └── 发布验证脚本
│
└── .github/workflows
    └── CI/CD
```

---

# 测试体系

项目包含大量自动化验证：

覆盖：

- 网络安全测试
- WebView 行为测试
- UserScript 测试
- 下载策略测试
- 主页交互测试
- 导航策略测试
- 密码填充测试
- 媒体解析测试

测试目标：

> 保证不同 Android 厂商 WebView 环境下仍保持稳定运行。

---

# 构建方式

## 环境要求

需要：

- Android Studio
- JDK 17
- Android SDK 36

---

## Debug 构建

```bash
./gradlew assembleDebug
```

---

## Release 构建

```bash
./gradlew assembleRelease
```

如需签名：

设置环境变量：

```
MEDIAN_KEYSTORE
MEDIAN_STOREPASS
MEDIAN_KEY_ALIAS
MEDIAN_KEYPASS
```

---

# 安全设计

项目关注：

- HTTPS 优先
- 权限最小化
- 页面来源验证
- 内部命令 token 校验
- 自动填充安全限制
- 用户操作确认

---

# 更新记录

## 2.3.0

主要更新：

- 重构 UserScript 启动可靠性
- 增强 GM API 兼容
- 改进密码自动填充
- 增强媒体资源发现
- 支持 DASH / Smooth Streaming 分析
- 优化请求拦截性能
- 增加大量行为回归测试

---

# 开源协议

本项目遵循 LICENSE 文件中的协议。

---

# 致谢

感谢：

- Android WebView 项目
- AndroidX WebKit
- Chromium 社区

MedianBrowser 希望成为一个：

> 小而强、快而稳、尊重用户控制权的 Android 浏览器。
:::
