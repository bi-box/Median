# Median Browser Privacy Policy / 隐私政策

Last updated / 最后更新：2026-08-10

Median Browser is a local-first Android browser. The application developer does not operate a Median account, synchronization server, advertising network, analytics service, or telemetry backend in this release.

Median Browser 是一款本地优先的 Android 浏览器。本版本不提供 Median 账户或同步服务器，也不集成广告、分析或遥测后台。

## Data stored on the device / 设备本地数据

The app may store bookmarks, browsing history, open-tab sessions, site preferences, cookies, website storage, downloads and download records, offline pages, filter lists, user scripts, saved passwords, and settings on the user's device. Saved passwords are protected with Android Keystore and AES-GCM. User-created full backups are encrypted with the password chosen by the user.

应用可在设备上保存书签、浏览历史、标签会话、网站设置、Cookie、网站存储、下载文件及记录、离线页面、过滤规则、用户脚本、密码和偏好。密码库由 Android Keystore 与 AES-GCM 保护；完整备份使用用户设置的密码加密。

## Network requests / 网络请求

When the user visits a page, searches, translates content, updates a filter subscription, installs a user script, or downloads a file, the relevant website or service receives a normal network request and may receive the IP address, user agent, request URL, cookies, form data, or other information necessary for that action. Those third parties process data under their own policies. Median does not proxy browser traffic through a developer-operated server.

当用户访问网页、搜索、翻译、更新过滤订阅、安装用户脚本或下载文件时，对应网站或服务会收到正常网络请求，并可能接收 IP 地址、用户代理、请求网址、Cookie、表单内容或完成操作所需的其他信息。第三方按其自身政策处理数据。Median 不通过开发者运营的服务器中转浏览流量。

## Permissions / 权限

- Camera and microphone: provided only after a secure webpage requests them and the user grants Android permission.
- Location: provided only after a secure webpage requests it and the user grants permission.
- Notifications and foreground service: used to display and control active downloads.
- Network state and wake lock: used only to enforce download constraints and keep an active file transfer complete. Normal browsing does not hold a Wi-Fi performance lock.

摄像头、麦克风和位置仅在 HTTPS 页面主动请求、页面来源匹配且用户授权后提供。通知与前台服务用于显示和控制下载；网络状态与唤醒锁只用于执行下载约束并保证正在传输的文件完整结束，普通浏览不会持有 Wi-Fi 性能锁。

## User scripts / 用户脚本

User scripts are third-party code. A script may modify pages and, when explicitly granted, request cross-origin resources, use the clipboard, start downloads, or open tabs. Median limits declared permissions and connection targets, but users should install scripts only from trusted sources.

用户脚本属于第三方代码。脚本在明确获得权限后可修改网页、跨域请求、使用剪贴板、发起下载或打开标签页。Median 会限制声明权限和连接目标，但用户仍应只安装可信脚本。

## Sharing, retention, and deletion / 分享、保留与删除

The developer does not sell personal data. The app itself does not send local browser records to the developer. Local data remains until the user clears it, removes individual items, deletes exported files, or uninstalls the app. Data sent directly to user-selected websites and services is governed by those services.

开发者不出售个人数据，应用本身不会把本地浏览记录发送给开发者。本地数据会保留至用户清除、删除对应项目或导出文件，或卸载应用。直接发送给用户选择的网站和服务的数据由对应服务管理。

## Children / 儿童

Median Browser is a general-purpose browser and is not specifically directed to children. Website content and third-party services are outside the developer's control. Guardians should use Android and website parental controls where appropriate.

## Contact / 联系方式

For privacy requests, use the developer contact address shown on the Google Play listing. For a GitHub build, use the repository's Security or Issues section, as appropriate. The public policy URL and contact channel must be verified before each release.
