# Median Browser 2.1.14 修复报告

## 已确认根因

- 2.1.13 的兜底会对网页节点逐个执行样式计算，并用 MutationObserver 持续跟踪整棵 DOM；大型搜索页和动态网站因此持续占用 WebView 渲染线程。
- 同一兜底给 `input`、`textarea` 和 `select` 强制写入背景色，直接形成 Google 和 Median 主页搜索框中的黑色矩形。
- 主窗口还会等待可选的 WebView 异步预启动回调，部分厂商内核丢失回调时只能等 3 秒兜底，看起来像网页暂时打不开。

## 修复后的深色链路

- `PageDarkening` 及其生成脚本、自测和所有调用点已经删除。
- Activity 在 `super.onCreate()` 前选定真实明暗主题，使 WebView 从创建时就得到正确的 `isLightTheme` / `prefers-color-scheme` 信号。
- Android 13+ 调用框架 `WebSettings.setAlgorithmicDarkeningAllowed()`；Android 8–12 使用 WebView provider 的 support-library converter 和 `WebSettingsBoundaryInterface` 调用原生算法。
- 切换时仅更新原生 WebSettings、WebView 背景和 Median 自身界面，不运行网页 JavaScript，也不强制 reload。
- 所有普通网站走同一策略；仅 Median 本地主页由自身轻量 HTML 直接生成匹配的明暗配色。

## 启动与体积

- 主窗口不再依赖可选的异步 provider 回调，消除 3 秒导航门槛；用户状态仍在有界后台线程中加载。
- 未新增生产依赖、SDK、位图或常驻服务，保留 R8 与 500 KiB 预算检查。
- 静态回归会拒绝重新加入 `PageDarkening`、DOM 深色注入或主窗口异步启动等待。
