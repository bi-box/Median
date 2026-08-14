# Median Browser 2.2.4 冷启动首航修复报告

## 现象与根因

用户截图显示内置主页仍停留在屏幕上，地址栏未切换为目标站点，顶部进度只停在首段。这说明主页脚本和 UI 正常，但首个 HTTP(S) 主框架没有真正开始。

定位到三个叠加问题：

1. 主页通过 `median://search` / `median://open` 发送可信内部命令。旧实现尚在 `shouldOverrideUrlLoading()` 回调内就同步调用新的 `loadUrl()`；部分 System WebView 在回调返回后取消内部协议时，会把这次嵌套网络导航一起取消。
2. WebView 即使只把任务放入队列也可能先报告约 10% 进度。旧守卫把任意进度回调当成“网络已开始”，因此提前关闭了原本 1.2 秒的恢复路径。
3. 首个 WebView 在 `webView` 字段绑定之前完成通用配置，被暂时判断为后台标签并设置为 BOUND 渲染优先级；第一次页面渲染前没有立即纠正。

## 修复

- `median://search` 和 `median://open` 完成安全校验并返回拦截结果后，再通过当前 WebView 的 `post()` 队列提交输入；执行时再次检查活动 WebView 与主页信任状态。
- `InitialNavigationGuard` 只接受 HTTP(S) `onPageStarted()`，不再由 `onProgressChanged()` 确认。
- 首航没有开始回调时，450 毫秒后停止失效任务并只补发一次；已开始、跳转或已补发的任务均不会重复。
- 普通窗口与隐私窗口均移除进度误确认；隐私窗口同样使用 450 毫秒单次恢复。
- 首个普通 WebView 绑定为活动标签后立即重新应用前台渲染策略。
- `applyPerformanceMode()` 不再重复查询站点图片设置；图片权限仍由 `applySiteSettings()` 唯一负责。

## 同步纯代码优化

- UserScript 公共运行时用一套 Promise 包装器复用 GM4 值、资源、Cookie、通知与剪贴板异步返回。
- 下载与 Cookie 兼容回调共用零延迟调度器。
- 同源 XHR 的 load/error/timeout/abort 共用终止分发器，`onloadend` 行为保持不变。
- `GM_openInTab` 删除结果恒等的目标分支。
- 页面访问策略的未使用返回值改为 `void`，渲染策略不再在每次站点导航重复执行。

以上均为 Java/JavaScript 代码和 R8 输出优化，没有删除功能、资源或兼容 API。

## 体积

| 指标 | 2.2.3 | 2.2.4 |
|---|---:|---:|
| APK | 207,317 B | **207,317 B** |
| R8 `classes.dex` | 420,524 B | **419,784 B** |
| raw-DEFLATE DEX | 199,090 B | **199,110 B** |
| versionCode | 88 | **89** |

功能修复增加了首航状态处理，但公共运行时代码合并使原始 DEX 反而减少 740 B；APK 继续保持在 207,317 B 的签名页。构建仍固定使用原有 raw-DEFLATE/Stored 条目方式。

## 验证项目

| 检查 | 结果 |
|---|---|
| Java 语法 / Android XML | 通过 |
| 主页生成 JavaScript | 通过 |
| UserScript 生成 JavaScript | 通过 |
| 首航守卫、启动就绪与内部导航自测 | 通过 |
| 媒体、脚本、安全、主页与下载自测 | 通过 |
| ZIP 完整性 / 4 与 16 KiB 对齐 | 通过 |
| APK v2 签名 / 证书 | 通过 / 与 2.2.3 相同 |
| 包名 / 版本 | `com.xinyv.median.compat` / `2.2.4 (89)` |
| APK SHA-256 | `64d4561e4f8baf817ad112e45eb037cbcdbcb7fac96bdddec710e065f6edb80b` |

当前构建环境没有连接 Android 真机或模拟器，不能冒充已完成设备侧冷启动实测。安装后应重点验证：彻底结束进程 → 冷启动 → 主页第一次搜索/快捷入口无需刷新即可进入网页。
