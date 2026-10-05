# TLM 1.5.3 工作区、休息区和河童的罗盘

本次依据作者发布的 `1.5.3-neoforge+mc1.21.1` 源码包检查，未修改 TLM 的行为。
NeoForge 开发版本固定为 `21.1.219`，Java 为 21。

## 开发依赖与源码

经用户选择，使用 Modrinth Maven，同时提供二进制和 `sources` classifier：

```groovy
repositories {
    maven {
        url = 'https://api.modrinth.com/maven'
        content { includeGroup 'maven.modrinth' }
    }
}

dependencies {
    implementation "maven.modrinth:touhou-little-maid:${tlm_version}"
}
```

`gradle.properties` 中 `tlm_version=1.5.3-neoforge+mc1.21.1`。
项目已有 `idea.module.downloadSources=true`，IDE 重新同步 Gradle 后可关联源码。
Gradle 的 `ArtifactResolutionQuery` 已实际解析到对应源码包，SHA-1 为
`cc8a36d873b096fc804f608f6d4d6238a4a916db`；`build` 已通过。

阅读用源码已解压在 `build/tlm-source-inspection/sources/`，清理 build 目录会删除该副本，
Gradle 缓存中的源码依然可以由 IDE 关联。本次没有启动游戏验证实际行为。

## 区域的数据模型

`entity/passive/SchedulePos.java` 保存三个 `BlockPos`：

| 日程活动 | 字段 | 全局半径配置 | 默认值 | 配置允许范围 |
|---|---|---|---|---|
| WORK：工作 | workPos | MaidWorkRange | 12 | 3–64 |
| IDLE：休息/娱乐 | idlePos | MaidIdleRange | 6 | 3–32 |
| REST：睡觉 | sleepPos | MaidSleepRange | 6 | 3–32 |

另外保存 `dimension` 和 `configured`。坐标按女仆分别保存，半径来自 TLM 全局配置，
原有 SchedulePos 不保存每只女仆的独立半径或区域顶点。

`save/load` 使用女仆 NBT 内的 `MaidSchedulePos`，包含 `Work`、`Idle`、`Sleep`、
`Dimension`、`Configured`。女仆实体的 `RESTRICT_CENTER`、`RESTRICT_RADIUS`
是当前生效中心/半径，通过同步实体数据维护。

`SchedulePos.restrictTo(maid)` 在 Home 模式开启时，依据
`maid.getScheduleDetail()` 选择中心和全局半径，再调用 `maid.restrictTo(...)`。
Home 关闭时此方法直接返回。

`EntityMaid.isWithinRestriction(pos)` 的判断为三维距离平方严格小于半径平方：

```text
(x - cx)^2 + (y - cy)^2 + (z - cz)^2 < radius^2
```

Y 坐标参与判断，恰好位于半径边界的方块不满足这个条件。

## Home 模式、日程和行为

Home 开启意味着不跟随主人，采用固定的日程中心；关闭时区域判定返回 true，
通常围绕女仆自身搜索，工作方块的公共搜索逻辑还限制目标靠近主人。

通过 GUI 开启 Home 的服务端逻辑在 `network/message/MaidConfigPackage.java`：

- 若 `configured=false`，将工作、休息、睡觉三个中心都初始化到女仆当前方块位置。
- 若 `configured=true`，要求女仆与记录区域同维度，且距离三个中心中最近一个不超过 32 格。
- 无法通过检查时提示用户并拒绝开启 Home。
- 成功后设置 Home 开关；当前限制会由周期逻辑更新。
- 关闭 Home 时将活动中心设为零点、半径设为非 Home 配置，跟随/搜索逻辑使用各自的条件。

`init/InitEntities.java` 定义的时间为游戏内时间：

| 日程 | 工作 | 休息/娱乐 | 睡觉 |
|---|---|---|---|
| DAY | 06:00–18:00 | 18:00–22:00 | 22:00–06:00 |
| NIGHT | 18:00–06:00 | 14:00–18:00 | 06:00–14:00 |
| ALL | 全天 | 无固定时段 | 无固定时段 |

`MaidUpdateActivityFromSchedule` 在日程活动变化时更新限制中心，并写入 WALK_TARGET、
LOOK_TARGET，让可移动的 Home 女仆前往当前中心。切换 DAY/NIGHT/ALL 时 GUI 网络逻辑
也会刷新 Brain、重新应用范围、设置前往中心的目标。

`entity/ai/brain/MaidBrain.java` 为活动注册不同行为：

- WORK：当前女仆任务的 Brain 行为、工作餐等。
- IDLE：寻找家庭餐设施、娱乐设施、随机走动等。
- REST：寻找女仆床并睡觉等。
- 核心行为（例如跟随、拾取、避险）有自己的额外条件。

`SchedulePos.tick()` 在服务端每 40 tick 执行一次：

1. 重新应用当前日程中心及全局半径。
2. 在范围内则结束；不可移动也结束。
3. 超出范围时设置回中心的行走/注视目标，速度参数 0.7、到达容差 3。
4. 距中心超过 `radius + 4`，并且当前 WALK_TARGET 不等于中心时，尝试传送。
   最多 10 次，候选位置为中心 X/Z ±3、Y +0～3，传送还要满足安全地面等条件。

`canBrainMoving()` 在坐下、骑乘、睡眠或被拴绳时返回 false。
这套回归机制是定期行为控制，不能视为瞬间强制位置约束。

## 目标搜索和寻路

- `MaidMoveToBlockTask` 使用当前半径作为水平搜索范围；Home 下优先从仍在范围内的
  最近工作点搜索，否则使用当前限制中心；非 Home 下使用女仆当前位置。
  候选方块还须通过 `isWithinRestriction`、任务条件、可达性及主人位置检查。
- `IMaidTask.searchDimension/searchRadius` 提供实体搜索 AABB 和范围，任务可以重写。
- 拾取、娱乐设施、女仆床等也读取中心/半径并检查目标是否处于范围内。
- `MaidNodeEvaluator` 在女仆当前处于范围内时，将范围外的寻路节点标记为 BLOCKED；
  当前已处于范围外时不采用这一阻挡条件，便于返回区域。

因此自定义区域需要一起考虑存储、目标搜索、边界判定、寻路和越界回归。
仅设置一次 `maid.restrictTo(...)` 的半径会被每 40 tick 的全局配置重新覆盖。

## 河童的罗盘：记录和写入

实现文件为 `item/ItemKappaCompass.java`，物品 ID 为 `touhou_little_maid:kappa_compass`。

罗盘的物品数据使用 1.21.1 Data Components：

- `KAPPA_COMPASS_ACTIVITY_POS`：Map<String, BlockPos>，键为 work/idle/rest。
- `KAPPA_COMPASS_DIMENSION`：维度字符串。

两者都配置了持久化和网络同步 Codec，定义在 `init/InitDataComponent.java`。

`useOn()` 右击方块时记录的是被点击方块本身的位置，按记录数量依次处理：

1. 第一次写 work。
2. 第二次写 idle，要求与 work 同维度，三维距离不超过 64 格。
3. 第三次写 rest，要求与已记录维度相同，与 idle 的三维距离不超过 64 格。
4. 三个点都记录后提示坐标已满，不能直接覆盖。
5. Shift 右击方块清除罗盘的坐标和维度。

第三次仅检查 sleep 与 idle 的距离，没有额外检查 sleep 与 work 的距离。

`getPoint(activity, compass)` 按“指定活动 → IDLE → WORK”回退：

- 仅记录 work 时，三个活动使用同一中心。
- 记录 work、idle 时，sleep 使用 idle 中心。
- 记录三个点时，各自使用自己的中心。

普通右击女仆时，`interactLivingEntity()` 在服务端检查维度，依次写入
`SchedulePos` 的 work/idle/sleep，设 `configured=true`，并调用 `restrictTo(maid)`。
罗盘数据不会被清除，物品不会被消耗，可以给多只女仆应用同一组坐标。
这一步不调用 `maid.setHomeModeEnable(true)`，Home 需要另外开启。

女仆的 `mobInteract()` 中，主手且主人交互的分支先发出 `InteractMaidEvent`，
再调用物品的 `interactLivingEntity()`。罗盘自身的方法没有检查主人或主副手。
进一步检查 NeoForge 21.1.219 的 Minecraft `Player.interactOn()` 源码可见：
实体交互没有消费操作时，会再次走物品的 `interactLivingEntity()` 回退分支。
因此不能把女仆上述分支的主人检查视为罗盘完整交互链路的权限保证；
在没有额外事件拦截时，非主人或副手也可能通过回退分支写入/清除坐标。
这是源码链路推断，未在游戏中实测。附属模组应在实际修改区域的服务端入口检查权限。

Shift 右击女仆调用 `SchedulePos.clear(maid)`：保留 work，令 idle、sleep 都等于 work，
设 `configured=false`，维度改为女仆当前维度，并重新应用范围。
它不会清除罗盘数据，也不会关闭 Home 或把所有坐标设置为零。

## 客户端显示

`CompassRenderEvent` 在持有河童罗盘且维度匹配时，显示工作红色、休息绿色、睡觉蓝色标记。
它根据罗盘上的数据展示，还未写入女仆的坐标也能预览。

`MaidAreaClickEvent` 处理普通原版指南针右击女仆：服务端通过 `SyncMaidAreaPackage`
发送女仆的 SchedulePos，客户端 `MaidAreaRenderEvent` 缓存 30 秒并显示区域。
这两种显示路径读取的数据来源不同。

虽然辅助方法名为 `renderCylinder`，`RenderHelper` 实际绘制同一高度上的圆环；
显示使用全局半径，核心 `isWithinRestriction` 使用前述三维球形距离判断。
