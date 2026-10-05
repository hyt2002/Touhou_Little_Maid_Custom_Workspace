# 智能罗盘与女仆的数据版本

从附属 1.3.3 开始，完成的罗盘计划和女仆的自定义区域状态都保存整数 `data_version`。数据版本只在对应存储结构变化时递增，不随每次 mod 发布或网络协议变化递增。

| 保存位置 | 当前版本 | 内容 |
| --- | ---: | --- |
| 罗盘组件 `touhou_little_maid_custom_workspace:workspace_plan` | 1 | 维度、立方区域、类型、名称、路径图、工作调度 |
| 女仆 `MaidTaskDataMaps["touhou_little_maid_custom_workspace:workspaces"]` | 1 | 内嵌计划、活动区域、工作及调度游标、游戏刻进度、转场、剩余路线、日程类型、完成状态 |
| 女仆状态内的 `plan` | 1 | 与罗盘计划使用同一结构，但为女仆独立快照 |

罗盘的工具编号仍独立存于 `edit_mode`，保持原有稳定编号。未完成框选与临时路径起点不持久化。原版河童罗盘行为和 TLM 原生日程坐标不纳入附属的版本迁移。

简化存储示例：

```json
{
  "data_version": 1,
  "plan": {
    "data_version": 1,
    "dimension": "minecraft:overworld",
    "areas": []
  },
  "active_index": 0
}
```

## 兼容既有存档

1.3.2 及之前没有版本字段的结构视为版本 0；显式写入 0 也按旧版处理。0→1 不改字段布局，保留已有数据，仍由原有可选字段默认值补齐早期缺失的类型、名称、图和调度。嵌套计划独立处理自身版本，因此女仆外层与内层版本不必相同。

加载时先按版本迁移原始 JSON/NBT，再交给当前结构的 Codec。重新保存或同步时总是写入当前版本。JSON 与 NBT 走同一流程，不修改传入的原始存档对象。高于当前支持版本的数据，以及负数、非整数、字符串或 null 版本，会返回解析错误，不猜测未知布局，也不会静默按旧版读取；降级不保证兼容。

## 后续开发的迁移入口

代码位于 `workspace/WorkspaceDataVersions.java`。`PLAN_VERSION` 与 `MAID_STATE_VERSION` 分别控制计划和女仆外层状态；`migratePlanStep`、`migrateMaidStateStep` 分别执行一次 N→N+1 迁移，公共流程会按顺序逐版执行。

以后更改计划字段时增加 `PLAN_VERSION`，并在计划迁移入口增加旧版本分支；更改女仆进度或状态字段时增加 `MAID_STATE_VERSION` 和状态迁移分支。迁移应在当前 Codec 解析前完成字段重命名、结构转换或默认值补充。单纯改贴图、菜单排序或默认配置不增加数据版本。新增迁移时补充真实旧 JSON/NBT 样本测试，检查区域及节点引用、调度与游戏刻进度仍有效。

`WorkspacePlan.CODEC` 同时供罗盘存储、编辑网络包及女仆内嵌计划使用；`WorkspaceState.CODEC` 注册到 TLM 的 TaskData 保存和同步系统。因此迁移入口覆盖两处持久化与同步路径，不需要针对每次应用、编辑或女仆加载另加 Mixin。此版网络协议编号升至 5，客户端与服务端应共同更新。
