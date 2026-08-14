# Positive Optimization Patch 2.1.6-p1

## 结论

本修正版保留优化版中确定正向的优化：

- 广告规则选择器去重改为 HashSet，避免 O(n²)；
- 浏览数据写盘改为锁内快照、锁外序列化；
- 写盘失败后延迟重试；
- 过滤订阅更新防止重复任务堆积。

同时修正可能被视为负优化或边界退化的地方。

## 修正 1：URL 比较加回规范化兜底

优化版把部分 `normalizeUrl(item.url).equals(normalized)` 改为 `item.url.equals(normalized)`。理论上当前写入路径都会保存规范化 URL，快路径是成立的；但为了兼容旧数据、异常导入数据、未来格式变化，本版改为：

```java
sameNormalizedUrl(stored, normalized)
```

逻辑：

1. 先直接比较，保留快路径；
2. 不相等时再对存量 URL 做 `normalizeUrl` 兜底。

这避免了历史/书签去重在旧数据下退化。

## 修正 2：订阅更新从“拒绝重复请求”改为“有条件合并”

优化版防止重复点击导致网络任务堆积，这是正优化；但如果自动更新正在跑，用户又手动点了完整更新，直接返回“正在更新”可能让手动意图丢失。

本版保留单任务队列，同时增加：

```java
manualUpdatePending
automaticUpdateInFlight
collectUpdateTargets(runAutomatic)
```

行为：

- 手动更新撞上自动更新：排队一次完整更新；
- 手动更新撞上手动更新：不重复排队；
- 自动更新撞上任何更新：仍然合并，避免后台堆积。

## 修正 3：版本号可原地安装

- `versionCode`: 69 → 70
- `versionName`: 2.1.6 → 2.1.7

避免构建出的 APK 因版本号未提升而无法覆盖安装。

## 验证

已通过：

```bash
./tools/static_checks.sh
```

当前环境没有 Android SDK / Build Tools，因此没有在沙盒内生成可安装 APK。包内提供了 `build-update.sh`，在本机 Android 构建环境中可直接生成签名更新 APK。
