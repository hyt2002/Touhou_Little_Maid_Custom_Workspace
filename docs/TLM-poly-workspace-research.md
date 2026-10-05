# 女仆立方工作区与轮换机制：技术路线

调研日期：2026-10-05。本文保留实现前的源码评估；后续已完成第一版独立立方工作区与游戏刻轮换，实际操作及验证见 [FIRST-VERSION.md](FIRST-VERSION.md)。未添加 Create 依赖。

## 核心抽象

功能分成两个直接的改动：

1. 单个工作区从中心加半径，改为两个对角点定义的轴对齐长方体。
2. 每只女仆从一个工作区，改为保存多个工作区的列表。

```text
WorkArea：min、max、dimension
MaidWorkAreas：areas、activeIndex、rotationPolicy
运行状态：前往当前工作区 / 在当前工作区工作
```

两个角允许各边长度不同，即强力胶式长方体框选。每个框独立保存、显示、判断和搜索。
无需合并框、生成统一多边形边界、计算相交轮廓、做三角化或建立体素形状。
框之间允许重叠；轮换仍按工作区身份和列表顺序处理。

一次仅有一个生效工作区。所有普通作业目标、局部搜索和返回目标都以当前区为准。
其他工作区仅保存在待轮换列表中，不同时参与目标搜索。
满足轮换条件后，更换 activeIndex，重新执行前往当前区、到达、开始工作的行为链。
轮换更换的是工作地点，女仆选择的任务类型可以保持不变。

## 复用现有到达流程

源码确认：MaidUpdateActivityFromSchedule 在日程活动变化时调用 SchedulePos.restrictTo，
然后通过 BehaviorUtils.setWalkAndLookTargetMemories 写入新中心作为 WALK_TARGET。
MaidBrain 的核心 MoveToTargetSink 与已有 MaidPathNavigation 等导航负责实际移动。
SchedulePos.tick 周期检查当前限制，并处理回归与原有传送兜底。

这支持复用已有移动行为链，新增轮换控制器决定何时更新目标工作区。
轮换发生时可能仍是 WORK 活动，因此不会自然触发时间日程变化；需要主动执行同样的
区域更新和 WALK_TARGET 设置，不能仅改变列表索引后等待日程事件。

地面 MaidNodeEvaluator 的现有逻辑仅在女仆当前处于限制内时阻挡外部节点。
切换生效区到 B 后，仍在 A 的女仆通常已处于 B 外，这条逻辑允许她向 B 返回，
无需让 A、B 同时生效或建立连接走廊。重叠区和地形仍按现有导航处理。

需要区分复用行为链与复用一个函数：TLM 没有在本次检查中提供一个统一的
“入区完成后开始所有工作”的公开调用。Brain 的 WORK 活动可能在到达前已经启用。
附属应增加很小的前往/工作状态控制，避免旧工作目标或其他工作行为抢占转场目标。
到达判断应依据实际进入当前立方区及有效行走位置，不只依据 WALK_TARGET 是否消失。

## 轮换状态与条件

```text
工作时间开始
  → 激活当前工作区
  → 用已有导航前往该区
  → 到达后开始区内工作
  → 轮换条件满足
  → 在操作边界停止旧区工作并清理旧目标
  → 激活下一个区，再次前往并开始工作
```

顺序循环可作为初始调度方式，其他选择方式可以后续配置。只有一个区时保持在该区工作。
日程结束、关闭 Home 或女仆不可移动时暂停轮换与工作计时；这些条件不应连续触发切区。
转场失败时沿用现有导航/回归能力，并需有超时后重试或跳过的明确规则，避免高速轮转。

| 轮换条件 | 信号与语义 | 接入难度 |
|---|---|---|
| 工作时间 | 到达后累计工作阶段 tick，转场和休息不计入 | 低 |
| 工作额度 | 成功完成指定数量的作业，例如收获、种植或剪毛 | 中，需定义计数单位并接入相关任务 |
| 当前区暂时无事可做 | 多次有效搜索无目标，且无待完成操作 | 中，需区分材料不足、冷却、不可达和暂时无目标 |
| 任务特定完成条件 | 具体任务判断当前区完成一轮 | 按任务增加适配 |

条件输出统一的“请求轮换”，由同一个控制器完成切区。
本次查看的 IMaidTask/API 事件没有通用的“工作额度成功一次”或“区域全部完成”信号。
农作物、剪毛等作业完成于不同 Behavior 中，额度和完成条件需逐任务接入。
例如农田还会重新生长，“完成”通常只能表示本轮暂时没有可执行工作。
不能把一次 TARGET_POS 清除或一次搜索失败直接当作完成。

建议先以时间条件验证 A 区工作、切换、到达 B 区、B 区工作的完整循环，
保留条件接口，再逐步接入额度与完成判断。

## 罗盘交互

调查依据是游戏中 Create 6.0.10 对应的官方标签 mc1.21.1-6.0.10。
[SuperGlueSelectionHandler](https://github.com/Creators-of-Create/Create/blob/mc1.21.1-6.0.10/src/main/java/com/simibubi/create/content/contraptions/glue/SuperGlueSelectionHandler.java)
提供明确的交互状态机：

- 第一次右击方块记录第一角，移动视线实时预览。
- 第二次右击确认一个框，重复操作继续增加工作区。
- Shift 右击取消未完成的选择。
- 瞄准已有框后左击删除。

新模式沿用这些操作，将确认的框加入罗盘草稿列表，右击自己的女仆时应用列表。
每只女仆保存草稿的独立快照；之后修改罗盘不自动修改已经配置的女仆。
原模式与新模式分别处理操作，避免旧罗盘坐标记录和清除逻辑同时执行。

框包含两个角对应的完整方块；渲染 AABB 的最大坐标为最大 BlockPos 加 1。
同高度的两点定义一层高的框，预览应明确显示高度，避免误解。

工作区采用几何坐标判定，不采用 Create 的可粘连方块群、胶水消耗或胶水实体。
可以使用 NeoForge 输入与渲染事件自行实现，无需 Create 前置。

## 保存与同步

优先使用已核对的 TLM 扩展 API：

- ILittleMaid.registerTaskData 注册自定义数据。
- TaskDataRegister.register 注册保存/同步 Codec。
- EntityMaid.setAndSyncData 保存每只女仆的数据并触发同步。

TLM 已负责这类数据的女仆 NBT 读写及客户端更新。
罗盘模式和草稿列表使用 1.21.1 Data Components；第一角和当前瞄准点属于临时编辑状态。
应用区域、增删草稿由服务端确认，并检查女仆主人、维度、交互距离和数据大小。

参考：[NeoForge 1.21.1 Data Components](https://docs.neoforged.net/docs/1.21.1/items/datacomponents/)、
[Custom Payload](https://docs.neoforged.net/docs/1.21.1/networking/payload/)。

## TLM 行为的最小适配

| 入口 | 必需适配 |
|---|---|
| 单区范围判定 | 以 min/max 做各轴坐标判断 |
| 生效区选择 | 仅使用 areas[activeIndex]，其他区等待轮换 |
| 方块目标搜索 | 在当前立方区扫描候选，保留原任务条件及可达性判定 |
| 实体目标搜索 | 在当前区查询并过滤，保留具体任务的射程等条件 |
| 单中心兼容入口 | 提供当前区的有效入区/返回目标和局部搜索上下文 |
| Home 与周期返回 | 当前区变更后跟随新区，防止每 40 tick 恢复旧工作中心 |
| 轮换控制 | 处理条件、切区、前往、到达和计数重置 |
| 旧区状态收尾 | 清理工作目标和旧路径，防止旧区动作在新区继续执行 |
| 客户端显示 | 按列表绘制独立方框，预览草稿和查看女仆分别取相应数据 |

工作区列表本身简单，实际接入仍涉及多个入口，因为 TLM 现有任务会分别读取中心、半径和搜索窗口。
本次未在 ILittleMaid 中发现能统一替换全部旧任务区域的策略接口，因此仍建议 API 加有条件的 Mixin。

第一版可仅在有效自定义工作区、Home 开启且日程为 WORK 时应用新逻辑；休息与睡觉设置保持原有行为。

## 局部搜索与寻路适配

现有 MaidMoveToBlockTask.searchForDestination 固定按中心、水平范围和任务垂直窗口扫描。
当前区的形状变为立方体时，需要同步调整这个局部搜索窗口。

现有 MaidPathFindingBFS 默认高度为上下 7 格，并在水平面按圆形截断；
准备区域及访问缓存也以单一中心和半径确定。

采用单一生效区后，只需让局部可达性窗口覆盖当前区；无需为远处所有区建立一张大地图。
可以用覆盖该区的保守范围并精确过滤目标，或适配 BFS 的准备边界与缓存，继续复用节点和搜索算法。
两区之间的转场由已有导航承担，到达后才运行新区局部工作搜索。
不应把工作目标搜索用的局部 BFS 与跨区移动导航混为一体。

切区时需停止或使旧工作行为失效，并清理任务相关 TARGET_POS、WALK_TARGET、PATH 等状态；
不要清除与工作无关的全部 Brain 数据。部分 Behavior 还有私有目标缓存，需在实现时检查。
EntityMaid.refreshBrain 会停止旧 Behavior 并重建行为，但 copyWithoutBehaviors 会保留记忆，
不能假设调用它就自动清空旧区状态，也不必默认每次切区都重建整个 Brain。

轮换计时可接在服务端 MaidTickEvent 或额外 Brain Behavior 中，数据仍使用 TLM TaskData。
MaidTickEvent 可以用于监听，不应为暂停工作而取消整个女仆 tick。
MaidTaskEnableEvent 用于任务选择可用性和 GUI，不是逐 tick 的工作执行开关。

## 难度与验证顺序

| 内容 | 初步难度 |
|---|---|
| 立方框选、预览、编辑列表 | 低到中 |
| 每只女仆独立保存与同步 | 低到中 |
| 单个当前立方区的目标搜索 | 中 |
| 当前区切换与原有到达流程协调 | 中 |
| 时间轮换 | 低到中 |
| 额度、无工作和任务特定完成条件 | 中，需逐任务适配 |
| 特殊任务与第三方任务全面兼容 | 视任务数量而定，需要逐项检查 |

建议先让一个代表性方块任务按时间轮换，在两个不同工作区依次正常作业，复用现有导航，
再验证高度变化、重叠框、Home 开关、日程切换和多人保存同步。
原型验证的重点是旧区任务正常收尾、前往新区、到达后开始新区工作，以及工作阶段的正确计时。

以上为实现前的源码级评估。后续第一版已验证加载、基础收割转场、游戏刻计时及保存；客户端显示、其他作业及复杂地形的验证范围见 [FIRST-VERSION.md](FIRST-VERSION.md)。

## 源码依据

TLM 使用作者发布的
[1.5.3-neoforge+mc1.21.1 源码包](https://cdn.modrinth.com/data/R0bDWFAW/versions/tXG1TkGx/touhou-little-maid-1.5.3-neoforge%2Bmc1.21.1-sources.jar)。
本地阅读副本位于 build/tlm-source-inspection/sources/。

主要检查：EntityMaid、SchedulePos、MaidMoveToBlockTask、MaidPathFindingBFS、
MaidNodeEvaluator、MaidUnderWaterNodeEvaluator、IMaidTask、ILittleMaid、TaskDataRegister、
MaidTaskDataMaps、MaidUpdateActivityFromSchedule、MaidFarmPlantTask、MaidShearTask、
MaidTickEvent、MaidTaskEnableEvent 与 ItemKappaCompass。原有具体行为见 docs/TLM-area-logic.md。
