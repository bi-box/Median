# Median Browser 2.1.7 快稳优化补丁报告

目标：在不改变产品定位、不牺牲核心安全边界的前提下，优先优化页面加载热路径、网页兼容恢复、主线程 I/O、内存压力下稳定性和发布一致性。

## 已落地改动

1. **发布检查修复**
   - `tools/static_checks.sh` 更新为检查 `versionCode 71` / `versionName 2.1.7`。
   - 自带静态检查已通过。

2. **网站级兼容模式**
   - `SiteSettingsStore` 新增 `compatibilityMode`。
   - 网站设置中新增“兼容模式”入口。
   - 开启后仅对当前 host 生效：允许第三方 Cookie、允许弹窗、关闭跟踪参数清理、暂停本站广告过滤、混合内容切到 `MIXED_CONTENT_COMPATIBILITY_MODE`。
   - HTTPS Only 对兼容模式站点不再强制升级 HTTP。

3. **加载失败自动提示兼容重试**
   - 主框架加载失败或 SSL 错误时，自动提示“启用兼容模式重试”。
   - 对 HTTPS 失败且全局 HTTPS Only 开启的站点，兼容重试会尝试 HTTP 回退。

4. **请求拦截热路径优化**
   - 每个 WebView 缓存当前 host 的广告过滤有效状态，避免每个资源请求重复匹配站点例外。
   - `Accept` header 改为按需读取：主框架或未知类型资源才读取，常见图片/脚本/CSS/字体/媒体后缀跳过 header 遍历。
   - 媒体嗅探改为仅在明显媒体 MIME/扩展名时进入，减少普通资源请求路径开销。

5. **Cookie 延迟 flush**
   - 用户脚本网络请求保存响应 Cookie 后，不再每次同步/即时 flush。
   - 改为 1 秒合并 flush，降低磁盘 I/O 抖动。

6. **用户脚本 GM 存储异步落盘**
   - `ScriptValueStore` 的 `GM_setValue` / `GM_deleteValue` 从同步 `commit()` 改为内存更新后单线程异步 `apply()`。
   - 降低频繁脚本存储导致的 UI 卡顿风险。
   - `BrowserServices.close()` 会关闭脚本存储后台线程。

7. **WebView 预热更保守**
   - 性能模式维持快速预热。
   - 标准/省电模式下，首屏未提交时延后预热，避免和当前页面加载抢 CPU/内存。
   - 发生内存压力或低内存后 5 分钟内不再预热 spare WebView。

8. **后台 renderer 崩溃恢复更细**
   - 后台/备用 WebView renderer 崩溃时，只清理对应 WebView，不再摧毁所有标签。
   - 前台 renderer 崩溃仍采用强恢复，保证当前页面可重新加载。

9. **后台任务拒绝策略更稳**
   - `DiscardOldestPolicy` 改为 `CallerRunsPolicy`，避免后台任务队列满时静默丢任务。

10. **内存压力下取消预热任务**
    - `onTrimMemory()` / `onLowMemory()` 记录内存压力时间并取消未执行的预热任务。

## 验证结果

运行：

```bash
./tools/static_checks.sh
```

结果：

```text
Java syntax sanity passed for 53 files
Android XML parse passed
NetworkSecuritySelfTest passed
DownloadRetryPolicySelfTest passed
OmniboxInputSelfTest passed
HomeOpenPolicySelfTest passed
CustomHomeCssSelfTest passed
CustomHomeHtmlSelfTest passed
HomePageConfigSelfTest passed
LogoMarkupSelfTest passed
Generated userscript JavaScript syntax passed
Static checks passed.
```

## 未完成的验证

由于当前环境不能访问 `services.gradle.org` 下载 Gradle wrapper 所需文件，也没有完整 Android SDK/真机构建环境，因此未能执行 `./gradlew assembleRelease` 或真机性能测试。源码已通过项目自带静态检查。

## 二次编译审查修正

完整 Java/AAPT2/D8 编译后又修正了以下边界问题：

- 兼容模式不再永久写入广告例外；关闭后恢复原有过滤状态。
- 兼容模式不再覆盖用户原有站点设置，只以一层临时兼容策略叠加。
- 兼容模式只放行用户触发的新窗口，不默认放行自动弹窗。
- SSL 子资源证书错误不再把子资源 URL 当成主页重试。
- HTTPS 失败不再自动降级 HTTP；HTTP 重试改为明确标注的不安全选项。
- 兼容提示增加单实例与 15 秒同站点节流，避免重复弹窗。
- Cookie flush 标志改为原子状态，修复脚本网络线程并发竞争。
- GM 存储直接使用 SharedPreferences.apply()：立即更新内存、异步落盘，避免额外线程造成备份看不到刚写入的数据。
- renderer 崩溃恢复恢复为整组 WebView 安全重建，避免共享 renderer 崩溃时只处理一个 WebView。
- 仅真实内存压力触发 5 分钟预热抑制；普通退到后台只取消当次预热，不长期惩罚返回后的性能。

## 完整构建验证

已使用 Android Platform 36 与 Build Tools 36.0.0 完成：

- AAPT2 资源编译与链接
- Java 17 源码编译
- D8 转换 DEX
- ZIP 对齐
- APK Signature Scheme v2/v3 签名验证

生成测试 APK 包名：`com.xinyv.median.compat.debug`，可与正式版共存。
