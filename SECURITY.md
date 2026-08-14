# Security Policy

## Supported versions

安全修复只面向当前最新稳定版本和正在测试的 Release Candidate。旧版本用户应升级。

## Reporting a vulnerability

公开仓库优先使用 GitHub Private Vulnerability Reporting / Security Advisory。若该功能不可用，请使用 Google Play 商店页中的开发者联系邮箱，并在标题中写明 `Median Browser Security`。请不要在修复发布前公开可直接利用的细节。

报告应尽量包含：受影响版本、Android 与 System WebView 版本、复现步骤、影响、最小 PoC，以及是否涉及用户脚本、下载、密码库、离线页面或权限。

## Threat model and boundaries

- Median 信任 Android 操作系统、Android Keystore 和已安装的 Android System WebView。
- 普通网页默认不可信；敏感网页权限要求 HTTPS、当前来源匹配和 Android 运行时授权。
- 用户脚本是用户主动安装的第三方代码，权限高于普通网页。Median 执行最小权限、匹配范围和 `@connect` 检查，但无法证明任意第三方脚本无恶意行为。
- document-start 原生桥接依赖 AndroidX WebKit 与设备 WebView 支持；不支持时高权限脚本会被禁用，而不是使用页面可窃取的兼容桥接。
- 浏览器允许用户主动访问 HTTP 网站，因此不能全局禁止明文流量。页面会受到 HTTP 本身的网络攻击风险。
- 本版本没有经过独立第三方渗透测试或形式化验证。正式大规模发布前应安排外部审计。

## Release security gates

- 静态检查、Android Lint、单元测试和 R8 release 构建全部通过。
- Release 密钥不进入仓库；使用 Play App Signing，并离线备份上传密钥。
- 对用户脚本桥接、重定向、私网访问、外部 Intent、离线 Provider、密码库和备份做人工回归。
- 对最终 AAB 做依赖和权限清单复核。
