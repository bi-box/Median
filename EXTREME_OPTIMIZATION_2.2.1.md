# Median Browser 2.2.1 极限优化实施报告

原则：功能和兼容性只进不退；通过减少重复工作、缩短热路径和收缩构建产物优化，不删除浏览器能力。

## 实施大纲与结果

1. **定位首次导航回归 — 已完成**
   - 对比可用的 2.1.17.3 与 2.2.0，确认旧包实际关闭了 R8 shrink/optimize/obfuscate，较慢时序掩盖了既有多导航竞争。
   - 找到“临时主页、会话/外部链接、排队输入”连续提交，以及离线页网络阻断状态继承两条故障链。

2. **重做冷启动首次导航状态机 — 已完成**
   - WebView 初始化不再导航；`completeStartup` 独占首个导航决定。
   - 用户输入优先于外部 Intent；没有直接输入时才恢复会话或主页。
   - HTTP(S) 统一走 `loadNetworkUrl`，在站点策略前后恢复联网不变量。

3. **补齐启动回归门禁 — 已完成**
   - 新增 `StartupNavigationPolicySelfTest`。
   - 静态检查断言初始化函数不能加载主页/URL、启动完成最多一次直接导航、网络入口必须两次清除离线阻断。

4. **UserScript 页面级共享运行时 — 已完成**
   - 兼容工厂在每个组合注入载荷中只出现一次，不再按已安装脚本数复制完整 GM API 源码。
   - 每个脚本仍由独立闭包和工厂实例隔离；桥接 token、脚本 ID、资源、菜单与回调不共享。
   - 修复纯本地授权的无效桥接调用，并补齐现代网络 Promise 的 timeout/abort 行为和常用元数据。

5. **媒体嗅探能力与热路径 — 已完成**
   - 网络层覆盖 HLS、DASH、Smooth Streaming、编码/双编码 URL、清单、完整媒体与有界分片。
   - 用户打开媒体中心时才执行增强 DOM/Resource Timing/JSON-LD/内联 URL 探测，不引入持续扫描。
   - 普通静态扩展名只有在路径、查询或 MIME 可能命中媒体规则时才进入完整分类，减少每个资源请求的字符串分配和小写转换。

6. **完整优化构建与覆盖安装验证 — 已完成**
   - 保留 R8 shrink/optimize/obfuscate、资源收缩、AAPT2 稀疏编码、原始 DEFLATE、确定性时间戳和 v2-only 签名。
   - 无生产第三方依赖、无新增位图、无常驻服务或 WebView 预热实例。
   - 包名、签名证书和权限集合与 2.2.0 完全一致；versionCode 从 85 升至 86。

## 验证结果

| 项目 | 结果 |
|---|---|
| Java 源码与 Android XML | 通过 |
| 启动导航策略测试 | 通过 |
| UserScript 生成 JavaScript 语法/共享运行时门禁 | 通过 |
| 媒体分类与探测 JavaScript 测试 | 通过 |
| 其余构建内纯 Java 自测 | 通过 |
| APK ZIP 完整性 | 通过 |
| APK Signature Scheme v2 | 通过 |
| 包名 / versionCode | `com.xinyv.median.compat` / `86` |
| 证书与 2.2.0 | 一致 |
| 权限集合与 2.2.0 | 无差异 |
| 最终大小 | 215,509 字节 |

真机仍建议重点覆盖：冷启动直接恢复网页、从离线 MHTML 回到网络页、外部链接唤起、安装多个 Greasy Fork 脚本、HLS/DASH/Smooth 页面媒体中心。
