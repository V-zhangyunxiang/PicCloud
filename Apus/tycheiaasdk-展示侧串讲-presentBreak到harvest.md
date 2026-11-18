# TycheIAASDK 主流程串讲（插屏 Break）

**范围：`TycheAds.presentBreak` → `harvest` → `reserve` → `showLease` → 结果分支**

## 1. 在整条业务链中的位置

```
boot
  → stageBreak → putStrategy        
  → presentBreak → gate → harvest → reserve → showLease →（Shown/Restore/Discard）  ← 正文
  → [无货则 Fallback] → 打点    
```

---

## 2. 主路径总图

![插屏展示主路径](tycheiaasdk-show-walkthrough-assets/01-main-flow.png)

文字版：

```
宿主 presentBreak(TycheFullscreenRequest)
  → presentFullscreen(..., INTERSTITIAL)
    → TycheApiGateway（补 adType，转 Lane 请求）
      → SdkRuntimeFacade → FullscreenLane → FullscreenFacade
        → FullscreenShowCoordinator.show
             ├─ Gatekeeper.evaluate
             │    Final → 失败返回（可打 adpv gap）
             │    Ready → 打 adpv gap → WinnerExecutor
             └─ VaultMesh.harvest   ★ 本篇终点
```

---

## 3. 术语速查

| Tyche             | 含义             |
| ----------------- | -------------- |
| Break             | 插屏             |
| present / stage   | 展示 / 预加载       |
| Gate / Gatekeeper | 展示前门禁          |
| bucket            | 一类缓存货架         |
| harvest           | 跨桶取候选并比价       |
| InventorySource   | 拍卖用轻量候选        |
| LiveAdCache       | 运行时广告缓存        |
| strategyId        | scene 绑定的策略 ID |

插屏：`MediationFormats.INTERSTITIAL = 1`。

---

## 4. 宿主入口：`TycheAds.presentBreak`

```kotlin
TycheAds.presentBreak(
    TycheFullscreenRequest(
        activity = this,
        sceneName = scene,
        adShowListener = listenerFor(...),
        // onUserEarnedRewardListener — 激励用，插屏通常 null
    )
)
```

| 字段 | 说明 |
|------|------|
| `activity` | 当前页，用于真正展示 |
| `sceneName` | 配置场景名，后续解析 `FullscreenScene` |
| `adShowListener` | shown / click / close / fail |
| `onUserEarnedRewardListener` | 激励；Break 可忽略 |

向下转发（外壳）：

```
presentBreak
  → presentFullscreen(request, INTERSTITIAL)   // 钉死插屏
    → TycheApiGateway.presentFullscreen
         ① toLaneFullscreenShowRequest(adType)
         ② runtime.showFullscreen → … → FullscreenShowCoordinator.show
         ③ 按 outcome 回调 onAdShowFailed / 返回 TycheCancelable
```

---

## 5. 核心调度：`FullscreenShowCoordinator.show`

Coordinator = **先 Gate，再 Winner**。

| 分支 | 含义 | 做什么 | 是否 harvest |
|------|------|--------|--------------|
| **Final** | 门禁已终结 | `publicFailurePending` 时打 `reportAdpvGapDetection`；直接返回失败 result | 否 |
| **Ready** | 门禁通过 | `onShowCalled`；**必打** adpv gap；`winnerExecutor.execute` | 是（在 execute 内） |

---

## 6. 门禁：`FullscreenShowGatekeeper`

Gate **先于** 任何缓存拍卖。

```
Gatekeeper.evaluate
  → PreSceneGateEvaluator   // 不依赖具体 scene 配置
       Final → 失败结束
       Ready → SceneGateEvaluator   // 解析 scene + 网络
```

### 6.1 PreScene 关卡顺序

![PreScene 门禁顺序](tycheiaasdk-show-walkthrough-assets/04-gate-sequence.png)

| 关卡               | 一句话                               |
| ---------------- | --------------------------------- |
| GlobalSwitch     | 总开关关了 → 一律不展示                     |
| Frequency        | 全局/类型/场景频控；含新用户首冷启、内容平衡           |
| ShowLimit        | 自然量/非自然量限额；通过后发 `ShowLimitTicket` |
| Impression       | 日曝光是否已满                           |
| CTR              | 点击过猛则抑制展示                         |
| Interval         | 距上次全屏关闭的冷却（按 adType）              |
| InterceptorChain | 扩展钩子；**当前 NO-OP，放行**              |

任一失败 → `PreScene Final`；全过 → `Ready(trace, showLimitTicket)`。

### 6.2 Scene 关卡

1. `resolveFullscreen(sceneName)` → 得到 `FullscreenScene`（含 `strategyId`、是否 bidding 等）；失败 `SCENE_LOOKUP_FAILED`
2. `isNetworkAllowed()`；失败 `CONNECTIVITY_GATE_BLOCKED`
3. 成功 → `ShowGateDecision.Ready(FullscreenShowReady(...))`，供后续 harvest 使用

---

## 7. 进入 Winner 路径：`FullscreenWinnerExecutor.execute`

| 步骤 | 方法 | 文档位置 |
|------|------|----------|
| 1 选赢家 | `vaultMesh.harvest` | §8 |
| 2 判断 / 断言 | `hasWinner` / `requireNotNull` | §10–11 |
| 3 独占取出 | `reserveWinner` / consume | §12 |
| 4 展示 + 结果 | `showLease` → Shown/Restore/Discard | §13–14 |

无 winner 或 reserve 失败 → `FallbackNeeded`（§10 / §12；live-load 细节暂缓）。

---

## 8. `VaultMesh.harvest`

### 8.1 总览图

![harvest 内部四步](tycheiaasdk-show-walkthrough-assets/02-harvest-internal.png)

核心代码结构：

```kotlin
fun harvest(request, scene): BidHarvest {
    val plan = plan(scene)                          // ① 问谁
    val candidates = plan.sources.flatMap { bucket ->
        inventorySourceProvider(bucket, request, scene)  // ② 怎么取
    }                                               // ③ flatMap 汇总
    val winner = candidates.maxWithOrNull(::compareSources)  // ④ 选赢家
    return BidHarvest(plan, candidates, winner)
}
```

| 符号 | 输入 | 输出 | 角色 |
|------|------|------|------|
| `plan` | scene | bucket 列表 | **问谁** |
| `inventorySourceProvider` | 单个 bucket | `List<InventorySource>` | **怎么取** |
| `flatMap` | 各桶小列表 | 一张 `candidates` | **如何汇总** |
| `maxWithOrNull` | candidates | winner 或 null | **谁最高** |

> **harvest 只 snapshot，不 remove。** 锁货是后续 `reserve` / `consume`。

### 8.2 `plan(scene)`：扫哪些桶

竞价开启 + 普通插屏，典型名单：

`SharedCeil` → `CeilAutomation` → `SharedFloor` → `FloorAutomation` → `SceneBackup` → `NewUser` → `Strategy`

（通知开屏才会加 Notification* 桶；普通 Break 不加。）

### 8.3 `inventorySourceProvider`：单桶取现货

实际注入为：

```kotlin
cacheRuntime.inventoryFor(
    bucket,
    sceneName = request.sceneName,
    adType = request.adType,
    strategyId = scene.strategyId,
    fallbackStrategyId = scene.notificationFallbackStrategyId,
)
```

**以 Strategy 桶为例：**

```
snapshotStrategy(strategyId)
  → 按 adType 过滤
  → 只留 ready
  → List<InventorySource>
```

其它桶同理，只是 snapshot 的货架不同（Shared / Backup / NewUser…）。  
**一次 invoke 只处理一个 bucket**，可能返回空列表。

### 8.4 `flatMap`：多桶 → 一张表

```
SharedCeil → [A]
Strategy   → [P1, P3]
…
flatMap → candidates = [A, P1, P3, …]
→ maxWithOrNull(CacheRankJudge) → winner
```

比价摘要：两边有效 eCPM（>0）→ **价高者胜**；否则门槛 / order / adType / source priority。

### 8.5 与 JSON「scene→strategy→placements」如何对齐

![配置 / 缓存 / harvest 关系](tycheiaasdk-show-walkthrough-assets/03-config-cache-harvest.png)

| 阶段 | 做什么 |
|------|--------|
| 配置 JSON | `scene → strategyId → placements[P1,P2,P3]`，定义策略货架能装什么 |
| stageBreak / preload | load 成功后 `putStrategy` 等写入 `LiveAdCache` |
| harvest | **不扫 JSON**；按桶 `inventoryFor` 读现货，再跨桶比价 |

三句话：

1. **JSON placements = 装货说明书**
2. **inventorySourceProvider = 打开某一个缓存货架看现货**（Strategy 的钥匙是 `strategyId`）
3. **flatMap = 多货架合成一张表再比价**

---

## 9. harvest 之后总览

`FullscreenWinnerExecutor.execute` 在 `harvest` 之后的完整顺序：

![harvest 之后流程](tycheiaasdk-show-walkthrough-assets/05-after-harvest.png)

```text
harvest
  → !hasWinner？ → FallbackNeeded
  → requireNotNull(winner)          // 非空断言
  → reserveWinner(...) → lease?
       null？ → FallbackNeeded
  → showLease(lease, ...) → Shown | Restore | Discard
  → 映射成 FullscreenShowResult
  → WinnerDecision.Final(result)     // 缓存路径终结（成功或失败都不再 fallback）
```

**四个动作的本质差：**

| 动作 | 对缓存的影响 |
|------|----------------|
| harvest | 只看不拿（snapshot） |
| reserve / consume | 拿走独占（从桶里移除） |
| Restore | 展示没成，把货还回桶 |
| Discard | 货坏了 / 不可复用，destroy，不还桶 |

---

## 10. `hasWinner`：有没有可选中的缓存赢家

```kotlin
val harvest = vaultMesh.harvest(request, ready.scene)
if (!harvest.hasWinner) {
    return FullscreenWinnerDecision.FallbackNeeded
}
```

- `hasWinner` 实现就是 `winner != null`（见 `BidHarvest`）。
- **没有赢家**（各桶都空或都没有 ready 候选）→ **不继续锁货**，直接 `FallbackNeeded`。
- Coordinator 收到后走 `fallbackCoordinator.resolve(...)`（live-load 兜底，本篇只点到入口）。

> 讲解口径：比价阶段「货架上空」≠ 展示失败终态，而是「改走实时加载」。

---

## 11. `requireNotNull(harvest.winner)`

```kotlin
val winner = requireNotNull(harvest.winner)
```

- 上一行刚判断过 `hasWinner`，此处 **逻辑上已非空**。
- `requireNotNull` 是 Kotlin 非空断言：把 `InventorySource?` 收成 `InventorySource`，方便后面打日志用 `winner.bucket / placementId / …`。
- **不是新业务分支**；若真为 null 会抛异常（正常路径不会发生）。

---

## 12. `reserveWinner`：从「看中」到「独占租约」

### 12.1 为什么还要 reserve

注释写得很清楚：

> Harvest only snapshots the current winner；reserve 必须 **再确认缓存里还在**，并 **原子 lease** 出来给人展示。

竞态例子：harvest 时看到 P1，到真正 show 前 P1 可能被别的逻辑消费/过期 → reserve 失败 → 同样 `FallbackNeeded`。

### 12.2 调用链

```text
reserveWinner(request, scene, harvest)          // WinnerExecutor 注入的 lambda
  → FullscreenInventoryLeaseRuntime.reserve
       winner = harvest.winner ?: return null
       entry = cacheRuntime.consumeWinner(winner, sceneName, strategyId, …)
            ?: return null
       return FullscreenInventoryLease(
            entry = entry,
            restore = { cacheRuntime.restore(entry, strategyId, …) }
       )
```

### 12.3 `consumeWinner` 做什么

按 `winner.bucket` 走对应 `LiveAdCache.consume*`，用 **placementId + sourceId + adType + orderNumber**（Strategy 还要 `strategyId`）精确匹配并取出 `LiveCacheEntry`：

| bucket | consume API |
|--------|-------------|
| SharedCeil / CeilAutomation / SharedFloor / FloorAutomation | `consumeShared` |
| SceneBackup | `consumeSceneBackup` |
| NewUser | `consumeNewUser` |
| Strategy | `consumeStrategy(strategyId, …)` |
| NotificationMain | `consumeNotificationMain` |
| NotificationFallbackStrategy | 按 fallbackStrategyId `consumeStrategy` |

- 成功：得到完整 `LiveCacheEntry`（含 mediation `handle`）
- 失败：`null` → Executor 返回 `FallbackNeeded`

### 12.4 `FullscreenInventoryLease` 是什么

```kotlin
data class FullscreenInventoryLease(
    val entry: LiveCacheEntry,   // 已从缓存拿走的货
    val restore: () -> Unit,     // 展示没成时把货放回原桶
)
```

可以把它想成：**超市购物车里已经拿走的那一件商品 + 退货单**。

---

## 13. `fullscreenPresenter.showLease`：真正尝试展示

```kotlin
FullscreenPresenter.showLease(
    lease, activity, sceneName, requestedAdType,
    rowId = showLimitTicket.rowId,   // 限额审计行，供后续回调记账
    adShowListener, onUserEarnedRewardListener,
)
```

内部两步：

### 13.1 前置检查 + 绑定上下文

1. `lease.entry.handle == null` → 直接 **Discard**（没有可展示对象）
2. `FullscreenHandleBinder.bindForShow(...)`

   把本次 show 的上下文绑到 handle 的 observer 上：`sceneName`、`adType`、`rowId`、`sourceBucket`、宿主 listener、激励 listener 等  
    → 后续 impression/click/close 才能打到正确场景

### 13.2 按 handle 类型展示（`showBoundHandle`）

| handle 类型 | 行为 | 结果映射 |
|-------------|------|----------|
| `LoadedFullscreenAdHandle` | `handle.show(activity, sceneName, adType)` | Shown / MissingActivity→**Restore** / Failed→**Discard** |
| `LoadedNativeAdHandle` | `NativeMixFullscreenHost.show(...)`（原生混全屏） | true→Shown；false→**Discard** |
| 其它 / null | — | **Discard** |

插屏主路径通常是 `LoadedFullscreenAdHandle`（各 mediation 的 interstitial handle）。

返回类型：`FullscreenLeasePresentationOutcome` = **Shown | Restore | Discard**。

---

## 14. 对 showLease 结果的判断（Executor 内）

```kotlin
when (showLease(...)) {
    Shown   → result = ShowResult(ShowWinner)
    Restore → lease.restore(); result = Fail(SHOW_PIPELINE_FAILED, …)
    Discard → handle.destroy(); result = Fail(SHOW_PIPELINE_FAILED, …)
}
return WinnerDecision.Final(result)
```

| Outcome | 缓存怎么处理 | 对外 outcome | 是否再 fallback |
|---------|--------------|--------------|-----------------|
| **Shown** | 已消耗，不还 | `FullscreenOutcome.ShowWinner` | 否（Final） |
| **Restore** | `lease.restore()` 放回原桶，下次还可被 harvest | `Fail` + `SHOW_PIPELINE_FAILED` | 否（Final） |
| **Discard** | `handle.destroy()`，不还桶 | `Fail` + `SHOW_PIPELINE_FAILED` | 否（Final） |

要点：

- **只有「没选到 / 没锁到」才 FallbackNeeded**；一旦 `showLease` 跑过，无论成败都是 **WinnerDecision.Final**。
- Restore 的典型触发：`MissingActivity`（Activity 不可用等）——货还在，只是这次展示条件不对。
- Discard 的典型触发：mediation `show` Failed、handle 缺失、原生混全屏失败——货不宜再卖。
Coordinator：`Final(result)` 直接返回宿主；`FallbackNeeded` → `fallbackCoordinator.resolve`（live-load，细节暂缓）。

---

## 15. 附录：装货侧 `stageBreak` 主流程

展示讲的是「从桶里取货」；装货讲的是「货怎么进 Strategy 桶」。串讲时本节约 10 分钟带过即可。

![stageBreak 主路径](tycheiaasdk-show-walkthrough-assets/06-stage-flow.png)

### 15.1 调用链

```text
TycheAds.stageBreak(sceneName)
  → TycheApiGateway.stageFullscreen(scene, INTERSTITIAL)
  → SdkRuntimeFacade.stageFullscreen
  → FullscreenLane.loadFullscreen
  → FullscreenFacade.stage
  → FullscreenStageCoordinator.stage          // 装货前门禁
  → FullscreenLoadOrchestrator.stageStrategyLoad
  → FullscreenStageLoadCoordinator.stageStrategyLoad
       → scope.launch { StrategyLoadUseCase.load(...) }   // 异步真正加载
  → 成功写入 LiveAdCache.putStrategy(strategyId, entry)
```

### 15.2 StageCoordinator 门禁

普通插屏 stage 前会过：全局开关 → 网络 → 日曝光是否允许 load → 解析 scene → adType 匹配 → 频控 → placements 非空。  

任一不过 → 返回 `false`，不发起加载。

### 15.3 StrategyLoadUseCase：按 placements 装货

从配置取出该 scene 的 `strategy` 与 `placements` 后，对每个 placement 大致：

```text
for placement in placements:
  缓存里已有同 placement？ → skip（cache hit）
  否则 → mediation load
  成功 → LiveAdCache.putStrategy(strategyId, entry)   // 写入 Strategy 桶
```

- API 返回 `true` 多表示 **「已接受发起」**；实际 load 在后台协程完成。
- 通知 / 新用户场景可能短接到其它 preload。

### 15.4 与 present 的关系

| | stageBreak | presentBreak |
|--|------------|--------------|
| 角色 | 装货 | 取货展示 |
| 读配置 placements | **是**（决定 load 谁） | 否（读缓存现货） |
| 写/读缓存 | `putStrategy` 写入 | `harvest` / `consume` 读取 |
| 典型顺序 | 先 stage（或靠自动化 preload） | 再 present |

> 没有 stage（或其它 preload）把货放进桶，present 的 harvest 往往会 `!hasWinner` 而走 Fallback。
