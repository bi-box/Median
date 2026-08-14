# Median Browser 2.1.12 修复报告

## 对应需求

1. 网页深色：WebView 原生深色与即时浅色页兜底并用；切换不再强制等待重载。
2. 灰色空白页：预热 WebView 不再导航到 `about:blank`；网页弹窗关闭时同步销毁对应标签。
3. 脚本限制：删除脚本/依赖/资源的数量与体积配额、GM 存储配额、桥接请求与响应配额，以及按耗时自动禁用脚本的逻辑。
4. 自定义搜索：JSON 轻量存储多个引擎，支持添加、重命名、改地址、设默认、删除和旧数据迁移。
5. 网站图标：缓存 WebView 已下载的 favicon，主页从本地内部资源流读取；没有图标时显示首字母。
6. 性能调度：将“网络直通”改为“性能模式下保持广告拦截”，将实时诊断替换为释放后台网页内存。
7. 软件图标：使用 Android VectorDrawable 绘制不对称“几何中线”，采用石墨底与雾银线条，覆盖传统、圆形、自适应和单色图标。
8. 体积控制：未增加第三方库、网络 SDK 或位图资源；新增功能仅使用两个小型 Java 存储类与矢量路径。

## 保留的安全边界

- 用户脚本仍只从 HTTPS 安装。
- 原生脚本能力仍按 `@grant` 与 `@connect` 授权。
- 远程网页仍不能通过脚本访问本机或私网地址。
- WebView 证书校验、跨域跳转和凭据请求头保护不变。

## 验证

- Java 结构与 Android XML 解析通过。
- NetworkSecurity、DownloadRetryPolicy、OmniboxInput、HomeOpenPolicy、CustomHomeCss、CustomHomeHtml、HomePageConfig、LogoMarkup 自测通过。
- 生成的用户脚本 JavaScript 通过 Node 语法检查。
- 静态安全检查通过，无新增生产运行时依赖。
- APK 构建与原发布证书签名验证结果见随包附带的构建环境记录。
