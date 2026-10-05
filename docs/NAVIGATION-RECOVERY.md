# 中心转场及现有导航的停滞恢复

核对日期：2026-10-06。版本：Minecraft 1.21.1、NeoForge 21.1.219、TLM 1.5.3、附属 1.1.0。原版寻路预算继续沿用；现可通过 [路径图](ROUTE-GRAPH.md) 拆分转场。

## 本次修改

转场目标从“靠近女仆的可站立入区点”改为“选中整块方块范围的几何中心所在方块”。中心处的地形处理交给现有地面导航。

无图路线时保持工作区中心目标；有连通图路线时改为当前必经节点，逐段到达。完成全部必经节点后，实际进入最终工作区才开始工作，无需继续到中心。X、Y、Z 严格使用框选边界，不扩展站立层或增加距离容差。

TLM `SchedulePos.tick` 每 40 刻和日程切换的返回目标统一为当前图节点或工作区中心、容差 0。图路线期间将日程返回传送调用改为继续导航当前节点；无图路线、休息和睡觉仍使用原有处理。

当前十六个 GameTest 和十七个 JUnit 测试通过，覆盖严格边界、短 U 形墙、上下相邻楼层转场，以及直接路径无法完整到达时，经四个节点绕过长墙并反向返回。测试不代表精确复现玩家截图中的地形。

## 卡住后会不会扩大搜索？

不会。现有流程有停滞检测和再次计算路径，但没有在重试时逐步扩大搜索范围或提高搜索节点预算。

### 位移检测

Minecraft `PathNavigation.doStuckDetection` 在当前导航运行超过 100 游戏刻后，对比当前位置与上次记录位置。位移阈值按速度计算：速度小于 1 时取其平方，再乘以 25。

位移小于阈值时标记 `isStuck` 并停止路径。它没有在这里调整 `FOLLOW_RANGE` 或 `maxVisitedNodesMultiplier`。

这是当前路径执行期间的检测，不是跨所有重试累计的全局静止计时器。开始移动路径时会重置位移检测窗口；如果一直获得很短的部分路径、很快结束并重启，女仆即使长期停留在附近，也可能没有连续执行到上述检测窗口。

### 当前路径节点超时

同一下一节点持续未完成时，导航累计游戏刻数。超时上限根据到下一节点的距离及速度计算；超过预计值的 3 倍会停止路径。这一处理也没有扩大搜索参数。

### 停止后的重试

女仆共用原版 `MoveToTargetSink`。因卡住而停止且尚未到达时，它设置随机 0～39 次检查的冷却，清除旧路径和行走目标。附属仍处于转场时会重新提交当前节点或工作区中心目标，之后重新尝试路径计算。

附属每 20 刻或缺少目标时提交当前目标，不表示每 20 刻强制重新计算整条路径：导航会复用尚未结束、目标一致的已有路径。

`MoveToTargetSink` 如果根本没有算出路径（返回 null），还会尝试朝目标方向选择随机中间点。这个兜底同样使用原来的导航搜索参数；如果已经算出了不能完整到达目标的部分路径，则会先沿该部分路径移动，而非立刻执行随机点兜底。

### 搜索参数

默认 TLM 女仆 `FOLLOW_RANGE` 为 64。地面导航以该属性确定搜索距离；路径计算器构造时的默认访问节点预算为该属性乘 16，即 1024。访问节点倍率默认是 1。

64 是距离参数，不是体积或节点数。原版 `PathFinder` 同时限制节点离起点的直线距离，以及沿节点累计的路线长度，候选路线累计长度必须小于 64。目标隔墙很近，但入口绕行过长时仍可能无法找到完整路径；节点预算也可能在探索入口前耗尽。

在此次核对的 TLM 及附属源码中，没有停滞后修改这两个参数的逻辑。重新计算路径时，搜索区域会随女仆当前坐标改变，但这属于搜索区域平移，不是半径或预算自适应增加。

工作区搜索的 `candidateBudget` 与局部 `MaidPathFindingBFS` 是寻找工作目标的机制，和实际转场的 A* 搜索预算不同。

## 核对的源码

- Minecraft：`PathNavigation` 的 `doStuckDetection`、`createPath`，`MoveToTargetSink` 的 `stop`、`tryComputePath`。
- TLM：`EntityMaid.createAttributes`、`MaidPathNavigation`、`MaidWrappedPathFinder`、`NavigationMixin`、`SchedulePos` 和 `MaidUpdateActivityFromSchedule`。
- 附属：`WorkspaceController`、`WorkArea`、`SchedulePosMixin` 和 `MaidUpdateActivityFromScheduleMixin`。

Minecraft 的阅读副本位于 `build/minecraft-source-inspection/`，从项目当前 NeoForge 开发源码包中提取；TLM 的阅读副本位于 `build/tlm-source-inspection/sources/`。
