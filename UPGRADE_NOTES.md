# Upgrade notes — 2.3.0

- applicationId 仍为 `com.xinyv.median.compat`，versionCode 为 92、versionName 为 `2.3.0`。
- 发布证书 SHA-256 仍为 `80daf48c091d6174981c2a176360b42ac32463d23ddc9e5f3c95c0951e5e3da9`；可覆盖同一签名链且 versionCode 低于 92 的旧版，请勿先卸载。
- 覆盖更新保留书签、历史、标签、主页、站点设置、用户脚本、密码库、下载记录和本地文件；脚本与密码数据格式保持向后兼容。
- 密码自动填充与登录保存提示默认开启，可在“设置 → 隐私、脚本与密码 → 密码管理器”分别关闭。
- 自动填充首次读取已存在的加密密码库时会要求系统身份验证，并在本机建立不可逆主机索引；不会自动提交表单。
- 媒体中心只报告页面可见和网络可观察到的资源，不绕过 DRM、平台授权、短时令牌或服务端限制。

## 2.1.11

- applicationId 仍为 `com.xinyv.median.compat`，versionCode 为 75、versionName 为 `2.1.11`。
- 2.1.11 继续使用 1.4.1 以来的稳定发布证书，SHA-256 为 `80daf48c091d6174981c2a176360b42ac32463d23ddc9e5f3c95c0951e5e3da9`。
- 已安装版本只要属于这条稳定签名链且 versionCode 低于 75，即可直接覆盖安装；不要卸载，浏览数据会保留。
- 随附的 `MedianBrowser-signing-backup.zip` 就是该稳定证书的私钥备份，必须私密保存，后续更新继续使用。
- 本轮没有改变本地数据格式；书签、历史、标签、网站策略、用户脚本、密码库和下载记录均沿用原数据。

## 2.1.6

- applicationId 仍为 `com.xinyv.median.compat`，versionCode 为 69、versionName 为 `2.1.6`。
- 要直接覆盖 2.1.5，APK 必须继续使用证书 SHA-256 `80daf48c091d6174981c2a176360b42ac32463d23ddc9e5f3c95c0951e5e3da9` 对应的原发布私钥。
- 升级后会自动清理 2.1.5 误写入的“我的主页”示例冲突；其他主页设置与浏览数据保留。

## 2.1.5
- applicationId 仍为 `com.xinyv.median.compat`，versionCode 为 68、versionName 为 `2.1.5`；发布证书与 2.1.4 完全相同，请直接覆盖安装，不要卸载旧版。
- 个性化主页配置已按功能分组；原有标题、Logo 代码、图片、壁纸、自定义 HTML 和启动策略会继续保留。
- 自定义 CSS 是推荐模式，会自动停用完整 HTML 以恢复内置搜索与快捷网站；HTML 代码本身不会删除，之后仍可重新启用。
- 完整 HTML 中的内联 JavaScript 现在可运行，但无法联网、读取父主页或调用 Median 内部操作。

## 2.1.4 说明

- applicationId 仍为 `com.xinyv.median.compat`，versionCode 为 67、versionName 为 `2.1.4`；发布证书与 2.1.3 完全相同，请直接覆盖安装，不要卸载旧版。
- 新功能位于“个性化主页”：第二项“每次打开”控制冷启动/主页键/新建标签，最后一项“自定义主页”用于编辑本地 HTML。
- 旧版“恢复上次标签”会自动迁移为“保留上一次访问的内容”；原有标签、书签、历史、下载记录、壁纸和 Logo 均保留。
- 自定义 HTML 支持 HTML 与内联 CSS，但出于本地数据安全不执行 JavaScript；壁纸仍使用原来的独立设置。

## 2.1.3 说明

- applicationId 仍为 `com.xinyv.median.compat`，versionCode 为 66、versionName 为 `2.1.3`；发布证书与 2.1.2 完全相同，请直接覆盖安装，不要卸载旧版。
- 新建下载会尽早显示总大小和百分比；旧的活动任务在继续下载并取得响应头后也会自动补齐大小。
- 服务器若使用 chunked/未知长度传输，本地无法提前知道最终大小，此时界面会如实显示“总大小未知”及实时已下载字节。

## 2.1.2 说明

- applicationId 仍为 `com.xinyv.median.compat`，versionCode 为 65、versionName 为 `2.1.2`；发布证书与 2.1.1 完全相同，可直接覆盖安装，不要卸载旧版。
- 新设置位于“个性化主页 → Logo 字间距 / 渐变方向”；局部间隔可在 Logo 代码中写成 `[space=4]`。
- 2.1.1 的标题、Logo 代码、壁纸和其他浏览数据全部兼容；旧 Logo 默认改用正常字距，避免字母互相挤压。

## 2.1.1 说明

- applicationId 仍为 `com.xinyv.median.compat`，versionCode 为 64、versionName 为 `2.1.1`；可直接覆盖 2.1.0，主页配置和浏览数据不会被清除。
- 在“个性化主页 → 文字 Logo”选择内置样式，或进入“编辑 Logo 代码”使用安全的 color/gradient 标记。
- 2.1.0 的标题、壁纸、Logo 图片和其他个性化设置继续兼容；默认仍使用 Median 经典样式。

## 2.1.0 说明

- applicationId 仍为 `com.xinyv.median.compat`，versionCode 为 63、versionName 为 `2.1.0`；可直接覆盖 2.0.2，浏览数据不会被清除。
- 个性化主页入口位于主菜单“个性化主页”，也可从“浏览器设置”进入。
- 壁纸和 Logo 会压缩后保存在应用私有目录，覆盖更新会保留；卸载应用或在个性化主页中恢复默认会移除这些图片。
- 完整备份会保存个性化文字、颜色、布局和显示开关；壁纸与 Logo 图片文件仍需在新设备上重新选择。

## 2.0.2 说明

- applicationId 仍为 `com.xinyv.median.compat`，versionCode 为 62、versionName 为 `2.0.2`；可直接覆盖 2.0.1，浏览数据不会被清除。
- 主菜单与共享功能面板的点击波纹现在限制在当前菜单行内。

## 2.0.1 说明

- applicationId 仍为 `com.xinyv.median.compat`，versionCode 为 61、versionName 为 `2.0.1`；可直接覆盖 2.0.0，浏览数据不会被清除。
- 地址栏和首页输入判定已统一；纯数字与关键词现在走所选搜索引擎，合法网址直接打开。

## 2.0.0 说明

- applicationId 仍为 `com.xinyv.median.compat`，versionCode 为 60、versionName 为 `2.0.0`；发布证书 SHA-256 与 1.7.3 相同，可直接覆盖安装。
- 覆盖更新不会删除书签、历史、标签、网站设置、脚本、密码库、下载记录或已完成文件；请勿先卸载旧版，卸载仍会清除应用私有数据。
- 会话、下载记录和脚本存储格式保持向后兼容，不需要手工迁移。旧的暂停/失败下载可继续或重试。
- 2.0.0 会更积极地冻结后台标签，并限制保留的 WebView 状态数量；冷标签仍保留标题和 URL，内存紧张时恢复会重新加载页面而不是保留完整页面现场。
- 新下载继续使用 Median 内部单连接引擎，不调用 Android DownloadManager；经典通用媒体发现不承诺下载带 DRM、短时令牌或音视频分轨的平台媒体。

## 1.7.3 说明

- applicationId 仍为 `com.xinyv.median.compat`，继续使用同一发布证书；versionCode 为 53、versionName 为 `1.7.3`，可覆盖更新 1.7.2 及更早同签名版本。
- 通用媒体发现继续保持 1.5.1 基线，没有恢复任何 YouTube 专用嗅探或临时媒体地址逻辑。
- 下载状态格式保持兼容；现有暂停/失败任务可继续，但恢复时会从原始 URL 重新取得重定向和 Cookie。
- 已完成文件、浏览记录、书签、标签和设置不会因覆盖安装被清除。

## 1.7.2 说明

- applicationId 仍为 `com.xinyv.median.compat`，继续使用同一发布证书；versionCode 为 52、versionName 为 `1.7.2`，可覆盖更新 1.7.1，浏览数据和已完成下载记录不会被清除。
- 代码功能回到 1.5.1 的通用媒体发现基线，后续 YouTube 专用嗅探和临时媒体地址下载逻辑已全部移除。
- 旧试验版留下的失败记录仍会显示，可在新版下载页点击“清理”删除；新版不会再创建这些 YouTube 专用任务。
- 通用下载继续由 Median 内部单连接引擎完成，并加入实时 Cookie、重复任务抑制、明确 HTTP 错误及有限退避重试。

## 1.5.1 基线说明

- applicationId 仍为 `com.xinyv.median.compat`，并沿用 1.4.1–1.4.3 的稳定签名链；已安装这些版本时可直接覆盖更新，浏览数据不会清除。
- versionCode 升为 47，versionName 改为 `1.5.1`。
- 所有新任务改由 Median 内部单连接下载，不再调用 Android DownloadManager。
- 进入下载中心时，1.5.0 遗留的未完成系统任务会逐个停止并自动转成 Median 下载；已完成文件不会删除。
- 旧的分段任务若尚未完成，会清除分段临时状态并以单连接重新开始；已经下载完但尚未发布的任务会直接继续保存。
- 高权限用户脚本现在要求明确的 `@grant`；旧脚本若依赖隐式 GM 权限，需要补充权限声明。
- document-start 通过聚焦版 AndroidX-compatible facade 调用 System WebView boundary；不支持该能力的旧 WebView 仍会 fail closed，仅运行无原生权限脚本。建议用户更新 Android System WebView。
- 跨域用户脚本请求现在逐跳检查 `@connect`，并拒绝远程页面访问本机或私网目标；依赖宽松重定向的脚本可能需要调整。
- 登录态下载会把当前站点 Cookie 交给 Median 下载器，并在跨源重定向时剥离；不要把含敏感链接的调试日志发给第三方。
- 发布构建不再自动生成开发证书。必须显式提供四个 `MEDIAN_*` 签名环境变量。
