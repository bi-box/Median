# Median Browser 2.1.15 修复报告

## 内部主页误判

- 旧逻辑在 `shouldOverrideUrlLoading()` 中同时检查信任集合、`currentPageUrl` 和 `WebView.getUrl()`。
- 部分 WebView provider 在回调时已把 `getUrl()` 更新为待处理的 `median://` 地址，因此合法主页点击会被误认为普通网页调用内部功能。
- 新逻辑仅采用由内置主页生成和令牌验证维护的稳定信任状态；自定义主页仍被隔离。
- `about:blank` 和已拦截内部命令的瞬态回调不会清除信任；真正进入网络网页时立即清除。
- 非主页调用继续返回已处理并阻止导航，但不显示 Toast。

## 卡顿来源与处理

- 移除广告过滤的 `MutationObserver` 和全文 `textContent/querySelectorAll` 扫描。静态 CSS 选择器和请求级网络规则不受影响。
- 移除 ADPF FrameMetrics 逐帧上报线程、强制最高刷新率和 UI 线程优先级修改。
- 禁用备用 WebView 和离屏预栅格，避免当前网页稳定后又启动第二渲染器造成掉帧。
- 订阅解析与本地索引预热从启动后 1.2 秒推迟到 4–8 秒空闲窗口；处理线程固定为后台优先级。
- 普通及隐私窗口均走直接 WebView 创建，不再依赖可能丢失的可选异步回调。

## 回归保护

- 新增可信主页、普通网页、自定义主页、瞬态空白页与网络离开主页的纯 Java 策略自测。
- 静态检查禁止权限误判 Toast、MutationObserver、`getComputedStyle`、procedural DOM 脚本、ADPF 性能控制器、强制刷新率和 UI 线程提权重新进入源码。
- 发行构建继续执行 API 36 Java 编译、R8、ZIP、500 KiB、v2/v3 签名和更新证书校验。
