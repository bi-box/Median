# Median Browser 2.2.2 极限优化实施报告

原则：不删除功能，不降低脚本、嗅探、安全或恢复能力；只消除竞争、重复工作、无界资源与无损包装开销。

## 实施大纲

| 项目 | 实现结果 |
|---|---|
| 冷启动状态机 | 等待数据、Resume、attach 和下一帧，执行前二次校验 |
| 首次导航 | 每 WebView 独立确认，超时只补发一次 |
| 页面设置 | 离线、主页、在线三套显式转换，性能档不再修改联网权限 |
| 启动 I/O | 四个核心存储分两条有界后台链并行；脚本载荷后台生成 |
| 私密模式 | WebView、过滤、Cookie、Resume、attach/帧全部就绪后才导航 |
| UserScript | 保留 2.2.1 页面级共享运行时、GM4 Promise、Cookie/资源/二进制兼容能力 |
| 媒体嗅探 | 保留 HLS、DASH、Smooth、DOM、Resource Timing、JSON-LD 与编码 URL 探测 |
| 下载资源 | 唤醒锁 30 分钟租约，活跃下载每 10 分钟续租 |
| DEX/APK | 完整 R8 + 稀疏资源 + 每条目最小 raw-DEFLATE + v2-only 签名 |

## 量化结果

| 版本 | APK 大小 | 与 2.2.2 差值 |
|---|---:|---:|
| 可用基线 2.1.17.3 | 262,623 B | -51,210 B（-19.50%） |
| 上一版 2.2.1 | 215,509 B | -4,096 B（-1.90%） |
| 本版 2.2.2 | **211,413 B** | — |

- 功能增加后的 R8 DEX：421,972 B。
- raw-DEFLATE 后 DEX：199,808 B。
- APK 共 6 个必要 ZIP 条目；`resources.arsc` 保持未压缩以避免资源读取性能倒退。
- 曾实测 `-repackageclasses`，最终 APK 没有减少 1 字节，因此撤回该选项。

## 发布验证

| 项目 | 结果 |
|---|---|
| Java/XML 与生成 JavaScript | 通过 |
| 启动、首航、脚本、媒体、主页、下载等自测 | 通过 |
| ZIP 完整性 / 4 字节对齐 | 通过 |
| 签名 | APK Signature Scheme v2，通过 |
| applicationId / versionCode | `com.xinyv.median.compat` / `87` |
| 权限集合相对 2.2.1 | 无变化 |
| 证书 SHA-256 | `80daf48c091d6174981c2a176360b42ac32463d23ddc9e5f3c95c0951e5e3da9` |
| APK SHA-256 | `62de77c350f76f9038944e2f6633d30ad397ae4897d7326059a36984ae6298f2` |

同包名、同证书且 versionCode 从 86 升至 87，符合覆盖安装条件。未在容器内做真机安装测试。
