# 第三方组件声明

Median Browser 本体以 GNU General Public License v3.0 发布，完整条款见根目录 [`LICENSE`](LICENSE)。下列第三方组件保留各自的原始许可，随本项目一同分发时其许可条款继续适用。

Median Browser 的网页渲染由 Android 系统提供的 Android System WebView 完成。项目还包含用于 WebView document-start 能力的兼容接口，以及相应的 Chromium support-library boundary 接口。

## 运行时所含组件

- AndroidX WebKit document-start 兼容代码：基于 Apache License 2.0。
- Chromium support-library boundary 接口：适用 Chromium BSD-style license。

这些兼容接口只覆盖项目所需的边界，不代表应用打包了完整 AndroidX WebKit 库或 Chromium 内核。其余网页渲染组件由 Android System WebView 提供。

## 测试与构建工具

以下组件用于测试或构建，不随正式应用作为运行时依赖提供：

- JUnit 4.13.2：Eclipse Public License 1.0。
- AndroidX Test Core 1.7.0、Runner 1.7.0、Ext JUnit 1.3.0：Apache License 2.0。
- Gradle、Android Gradle Plugin、Android SDK Build Tools、R8 与 AAPT2：适用各自项目及 Android SDK 许可证。

- [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [Eclipse Public License 1.0](https://www.eclipse.org/legal/epl-v10.html)
- [Chromium license](https://source.chromium.org/chromium/chromium/src/+/main:LICENSE)

需要为特定分发版本核对组件时，请以对应源码、构建配置和最终 APK 为准。
