package com.erobrine.tlmcw.gametest;

import static com.erobrine.tlmcw.gametest.V2Fixtures.*;

import com.erobrine.tlmcw.TouhouLittleMaidCustomWorkspace;
import com.erobrine.tlmcw.compat.LittleMaidCompat;
import com.erobrine.tlmcw.item.WorkspaceItems;
import com.erobrine.tlmcw.workspace.*;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.item.ItemKappaCompass;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(TouhouLittleMaidCustomWorkspace.MODID)
@PrefixGameTestTemplate(false)
public final class ScheduleAreaGameTests {
    private static void floor(GameTestHelper helper) {
        for (int x = 1; x <= 22; x++)
            for (int z = 1; z <= 14; z++) helper.setBlock(x, 1, z, Blocks.STONE);
    }

    private static WorkArea area(GameTestHelper helper, int x) {
        return WorkArea.between(
                helper.absolutePos(new BlockPos(x, 1, 3)),
                helper.absolutePos(new BlockPos(x + 3, 3, 6)));
    }

    private static BlockPos point(GameTestHelper helper, int x, int z) {
        return helper.absolutePos(new BlockPos(x, 2, z));
    }

    private static EntityMaid maid(GameTestHelper helper, Player owner) {
        EntityMaid maid =
                new EntityMaid(helper.getLevel()) {
                    @Override
                    public net.minecraft.world.entity.LivingEntity getOwner() {
                        return owner == null ? super.getOwner() : owner;
                    }
                };
        maid.moveTo(helper.absoluteVec(new Vec3(3.5, 2, 3.5)));
        maid.setTame(true, true);
        maid.setOwnerUUID(owner == null ? UUID.randomUUID() : owner.getUUID());
        helper.getLevel().addFreshEntity(maid);
        maid.setSchedule(MaidSchedule.DAY);
        maid.getSchedulePos().setWorkPos(point(helper, 3, 3));
        maid.getSchedulePos().setIdlePos(point(helper, 9, 9));
        maid.getSchedulePos().setSleepPos(point(helper, 18, 11));
        maid.getSchedulePos().setDimension(helper.getLevel().dimension().location());
        maid.getSchedulePos().setConfigured(true);
        maid.setHomeModeEnable(true);
        return maid;
    }

    private static WorkspacePlan plan(
            GameTestHelper helper, FixtureGraph graph, List<AreaType> types) {
        return fixturePlan(
                helper.getLevel().dimension().location(),
                List.of(area(helper, 3), area(helper, 12), area(helper, 18)),
                graph,
                types);
    }

    @GameTest(batch = "mixed_compasses", template = "test_empty", timeoutTicks = 100)
    public static void partialPlansAndOriginalCompassCoexistAcrossEverySchedule(
            GameTestHelper helper) {
        floor(helper);
        helper.setDayTime(0);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        EntityMaid maid = maid(helper, owner);
        ItemStack original = new ItemStack(InitItems.KAPPA_COMPASS.get());
        List<BlockPos> originalPoints =
                List.of(point(helper, 6, 9), point(helper, 10, 11), point(helper, 20, 11));
        List<Activity> activities = List.of(Activity.WORK, Activity.IDLE, Activity.REST);
        ItemKappaCompass.addDimension(helper.getLevel().dimension().location(), original);
        for (int i = 0; i < 3; i++)
            ItemKappaCompass.addPoint(activities.get(i), originalPoints.get(i), original);
        owner.setItemInHand(InteractionHand.MAIN_HAND, original);
        maid.mobInteract(owner, InteractionHand.MAIN_HAND);
        ItemStack smart = new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get());
        smart.set(WorkspaceComponents.EDIT_MODE.get(), CompassEditor.APPLY_TO_MAID);
        int[] times = {0, 13000, 18000};
        int[] ranges = {
            MaidConfig.MAID_WORK_RANGE.get(),
            MaidConfig.MAID_IDLE_RANGE.get(),
            MaidConfig.MAID_SLEEP_RANGE.get()
        };
        // Exercise all seven non-empty combinations, including removal of a formerly active type.
        for (int mask = 7; mask > 0; mask--) {
            var areas = new java.util.ArrayList<WorkArea>();
            var types = new java.util.ArrayList<AreaType>();
            for (int i = 0; i < 3; i++)
                if ((mask & (1 << i)) != 0) {
                    areas.add(area(helper, 3 + i * 7));
                    types.add(AreaType.values()[i]);
                }
            WorkspacePlan partial =
                    fixturePlan(
                            helper.getLevel().dimension().location(),
                            areas,
                            RouteGraph.EMPTY,
                            types);
            smart.set(WorkspaceComponents.PLAN.get(), partial);
            for (int i = 0; i < 3; i++) {
                helper.setDayTime(times[i]);
                maid.getSchedulePos().restrictTo(maid);
                owner.setItemInHand(InteractionHand.MAIN_HAND, smart);
                maid.mobInteract(owner, InteractionHand.MAIN_HAND);
                var active = WorkspaceLogic.active(maid);
                boolean custom = (mask & (1 << i)) != 0;
                helper.assertTrue(
                        (active != null) == custom,
                        "Wrong priority for partial mask " + mask + " / " + activities.get(i));
                if (custom) {
                    helper.assertTrue(
                            active.plan().type(active.destinationId()) == AreaType.values()[i],
                            "Wrong custom area type");
                    helper.assertTrue(
                            maid.getRestrictCenter()
                                    .equals(active.navigationArea().navigationCenter()),
                            "Custom center not applied");
                    helper.assertTrue(
                            !maid.isWithinRestriction(originalPoints.get(i)),
                            "Original circle overrode the custom cuboid");
                } else {
                    helper.assertTrue(
                            maid.getRestrictCenter().equals(originalPoints.get(i)),
                            "Removed type left a stale custom center");
                    helper.assertTrue(
                            maid.getRestrictRadius() == ranges[i],
                            "Fallback did not restore original radius");
                    helper.assertTrue(
                            maid.isWithinRestriction(originalPoints.get(i)),
                            "Fallback kept custom target filtering");
                    helper.assertTrue(
                            WorkspaceLogic.mayWork(maid),
                            "Fallback was still blocked by custom travel");
                }
                helper.assertTrue(
                        maid.getSchedulePos().getWorkPos().equals(originalPoints.get(0))
                                && maid.getSchedulePos().getIdlePos().equals(originalPoints.get(1))
                                && maid.getSchedulePos()
                                        .getSleepPos()
                                        .equals(originalPoints.get(2)),
                        "Smart compass overwrote original points");
                owner.setItemInHand(InteractionHand.MAIN_HAND, original);
                maid.mobInteract(owner, InteractionHand.MAIN_HAND);
                helper.assertTrue(
                        WorkspaceLogic.state(maid).plan().equals(partial),
                        "Original compass overwrote the smart plan");
                helper.assertTrue(
                        (WorkspaceLogic.active(maid) != null) == custom,
                        "Original reapply changed area priority");
            }
        }
        // The final work-only plan must also survive a save/load with original idle/sleep settings.
        EntityMaid restored = new EntityMaid(helper.getLevel());
        restored.load(maid.saveWithoutId(new CompoundTag()));
        restored.getSchedulePos().restrictTo(restored);
        helper.assertTrue(
                WorkspaceLogic.active(restored) == null
                        && restored.getRestrictCenter().equals(originalPoints.get(2)),
                "Reload lost original sleep fallback");
        helper.assertTrue(
                WorkspaceLogic.state(restored)
                        .plan()
                        .equals(smart.get(WorkspaceComponents.PLAN.get())),
                "Reload lost partial plan");
        owner.setItemInHand(InteractionHand.MAIN_HAND, smart);
        owner.setShiftKeyDown(true);
        maid.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(
                WorkspaceLogic.state(maid) == null
                        && maid.getSchedulePos().getWorkPos().equals(originalPoints.get(0)),
                "Clearing smart settings erased original points");
        helper.succeed();
    }

    @GameTest(batch = "mixed_native_bed", template = "test_empty", timeoutTicks = 600)
    public static void workOnlyPlanStillSleepsInOriginalCompassArea(GameTestHelper helper) {
        floor(helper);
        bed(helper, 14);
        helper.setDayTime(18000);
        EntityMaid maid = maid(helper, null);
        maid.getSchedulePos().setSleepPos(point(helper, 14, 4));
        maid.moveTo(helper.absoluteVec(new Vec3(14.5, 2, 6.5)));
        WorkspaceController.apply(
                maid,
                fixturePlan(helper.getLevel().dimension().location(), List.of(area(helper, 3))));
        helper.succeedWhen(
                () -> {
                    helper.assertTrue(
                            maid.isSleeping(), "Work-only cuboids blocked original sleep behavior");
                    helper.assertTrue(
                            maid.getSleepingPos().orElseThrow().equals(point(helper, 14, 4)),
                            "Fallback used the wrong bed");
                    helper.assertTrue(
                            WorkspaceLogic.active(maid) == null
                                    && WorkspaceLogic.state(maid).workTicks() == 0,
                            "Original sleep ran custom work logic");
                });
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void markCyclesTypesAndAppliedTypedSnapshotSurvivesCompassRemoval(
            GameTestHelper helper) {
        floor(helper);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        EntityMaid maid = maid(helper, owner);
        maid.setSchedule(MaidSchedule.ALL);
        ItemStack compass = new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, compass);
        var graph =
                new FixtureGraph(List.of(point(helper, 9, 4), point(helper, 16, 4)), List.of())
                        .connect(n(0), n(-1))
                        .connect(n(-1), n(1))
                        .connect(n(1), n(-2))
                        .connect(n(-2), n(2));
        compass.set(
                WorkspaceComponents.PLAN.get(),
                plan(helper, graph, List.of(AreaType.WORK, AreaType.WORK, AreaType.WORK)));
        CompassEditor.setMode(compass, owner, CompassEditor.MARK_AREAS);
        owner.moveTo(helper.absoluteVec(new Vec3(3.5, 2, 4.5)));
        owner.setXRot(90);
        for (AreaType expected : List.of(AreaType.IDLE, AreaType.SLEEP, AreaType.WORK)) {
            compass.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
            helper.assertTrue(
                    compass.get(WorkspaceComponents.PLAN.get()).type(n(0)) == expected,
                    "Mark click did not cycle region type");
        }
        owner.moveTo(helper.absoluteVec(new Vec3(12.5, 2, 4.5)));
        compass.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        owner.moveTo(helper.absoluteVec(new Vec3(18.5, 2, 4.5)));
        compass.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        compass.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        WorkspacePlan typed = compass.get(WorkspaceComponents.PLAN.get());
        helper.assertTrue(
                types(typed).equals(List.of(AreaType.WORK, AreaType.IDLE, AreaType.SLEEP)),
                "Marking changed another region");
        helper.assertTrue(
                java.util.Set.copyOf(typed.graph().edges())
                        .equals(java.util.Set.copyOf(graph.edges())),
                "Marking changed graph node IDs or connections");
        CompassEditor.clearNodes(compass, owner);
        helper.assertTrue(
                typed.equals(compass.get(WorkspaceComponents.PLAN.get())),
                "Clear key erased data in Mark mode");
        maid.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(
                WorkspaceLogic.state(maid) == null, "Mark mode unexpectedly applied the plan");
        CompassEditor.setMode(compass, owner, CompassEditor.APPLY_TO_MAID);
        maid.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(
                WorkspaceLogic.state(maid).plan().equals(typed),
                "Apply mode lost typed regions or graph");
        ItemStack restoredCompass =
                ItemStack.parseOptional(
                        helper.getLevel().registryAccess(),
                        (CompoundTag) compass.save(helper.getLevel().registryAccess()));
        helper.assertTrue(
                restoredCompass.get(WorkspaceComponents.PLAN.get()).equals(typed),
                "Compass NBT lost region types");
        CompassEditor.setMode(compass, owner, CompassEditor.WORK_AREAS);
        CompassEditor.clearNodes(compass, owner);
        helper.assertTrue(
                compass.get(WorkspaceComponents.PLAN.get()).areas().isEmpty(),
                "Cuboid clear left idle or sleep regions");
        helper.assertTrue(
                points(compass.get(WorkspaceComponents.PLAN.get())).equals(graph.points()),
                "Cuboid clear removed intermediate points");
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        EntityMaid restored = new EntityMaid(helper.getLevel());
        restored.load(maid.saveWithoutId(new CompoundTag()));
        helper.assertTrue(
                WorkspaceLogic.state(restored).plan().equals(typed),
                "Maid NBT depended on edited or deleted compass");
        helper.succeed();
    }

    // Day time is shared across the test world, so each time-changing test has its own batch.
    @GameTest(batch = "typed_schedule", template = "test_empty", timeoutTicks = 600)
    public static void allSchedulesVisitGraphNodesAndPreserveWorkProgress(GameTestHelper helper) {
        floor(helper);
        helper.setDayTime(0);
        EntityMaid maid = maid(helper, null);
        var graph =
                new FixtureGraph(List.of(point(helper, 9, 4), point(helper, 16, 4)), List.of())
                        .connect(n(0), n(-1))
                        .connect(n(-1), n(1))
                        .connect(n(1), n(-2))
                        .connect(n(-2), n(2));
        var plan =
                plan(helper, graph, List.of(AreaType.WORK, AreaType.IDLE, AreaType.SLEEP))
                        .withSchedule(
                                new WorkSchedule(
                                        List.of(
                                                new WorkScheduleEntry(
                                                        n(0), new DelayCondition(1000))),
                                        true));
        maid.setAndSyncData(LittleMaidCompat.WORKSPACES, fixtureState(plan, 0, 837, false));
        helper.setDayTime(13000);
        maid.getSchedulePos().restrictTo(maid);
        helper.assertTrue(
                indices(WorkspaceLogic.state(maid).remainingRoute()).equals(List.of(-1, 1)),
                "Idle transition ignored graph");
        int[] phase = {0};
        boolean[] visited = {false, false};
        helper.onEachTick(
                () -> {
                    WorkspaceState state = WorkspaceLogic.state(maid);
                    if (phase[0] < 2)
                        helper.assertTrue(
                                state.workTicks() == 837 && workDestination(state) == 0,
                                "Rest or sleep consumed/reset work progress");
                    if (phase[0] == 0) {
                        if (plan.nodeArea(n(-1)).contains(maid.blockPosition())) visited[0] = true;
                        if (state.scheduleType() == AreaType.IDLE && !state.travelling()) {
                            helper.assertTrue(
                                    visited[0] && area(helper, 12).contains(maid.blockPosition()),
                                    "Idle arrival skipped its waypoint");
                            helper.setDayTime(18000);
                            maid.getSchedulePos().restrictTo(maid);
                            helper.assertTrue(
                                    indices(WorkspaceLogic.state(maid).remainingRoute())
                                            .equals(List.of(-2, 2)),
                                    "Sleep transition ignored graph");
                            phase[0] = 1;
                        }
                    } else if (phase[0] == 1) {
                        if (plan.nodeArea(n(-2)).contains(maid.blockPosition())) visited[1] = true;
                        if (state.scheduleType() == AreaType.SLEEP && !state.travelling()) {
                            helper.assertTrue(
                                    visited[1] && area(helper, 18).contains(maid.blockPosition()),
                                    "Sleep arrival skipped its waypoint");
                            helper.setDayTime(0);
                            maid.getSchedulePos().restrictTo(maid);
                            helper.assertTrue(
                                    indices(WorkspaceLogic.state(maid).remainingRoute())
                                            .equals(List.of(-2, 1, -1, 0)),
                                    "Work return ignored reverse graph");
                            phase[0] = 2;
                        }
                    } else if (state.scheduleType() == AreaType.WORK
                            && !state.travelling()
                            && state.workTicks() > 837) {
                        helper.assertTrue(
                                workDestination(state) == 0
                                        && area(helper, 3).contains(maid.blockPosition()),
                                "Work did not resume in saved region");
                        helper.succeed();
                    }
                });
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void namedTimetableEditsAreValidatedAndMaidSnapshotSurvivesReload(
            GameTestHelper helper) {
        floor(helper);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        EntityMaid maid = maid(helper, owner);
        maid.setSchedule(MaidSchedule.ALL);
        ItemStack compass = new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, compass);
        var graph =
                new FixtureGraph(List.of(point(helper, 9, 4), point(helper, 16, 4)), List.of())
                        .connect(n(0), n(-1))
                        .connect(n(-1), n(1))
                        .connect(n(1), n(-2))
                        .connect(n(-2), n(2));
        var original = plan(helper, graph, List.of(AreaType.WORK, AreaType.IDLE, AreaType.WORK));
        compass.set(WorkspaceComponents.PLAN.get(), original);
        CompassEditor.setMode(compass, owner, CompassEditor.RENAME_AREAS);
        int slot = owner.getInventory().selected;
        helper.assertTrue(
                CompassEdits.save(owner, slot, original, n(0), "菜田", WorkSchedule.EMPTY),
                "Valid name edit was rejected");
        var named = compass.get(WorkspaceComponents.PLAN.get());
        helper.assertTrue(
                named.name(n(0)).equals("菜田")
                        && java.util.Set.copyOf(named.graph().edges())
                                .equals(java.util.Set.copyOf(graph.edges())),
                "Rename changed nodes or graph");
        helper.assertTrue(
                !CompassEdits.save(owner, slot, original, n(0), "旧界面", WorkSchedule.EMPTY),
                "Stale editor overwrote a newer draft");
        helper.assertTrue(
                !CompassEdits.save(owner, slot + 1, named, n(0), "错物品", WorkSchedule.EMPTY),
                "Editor saved to wrong inventory slot");
        CompassEditor.setMode(compass, owner, CompassEditor.SCHEDULE_OPTIONS);
        var invalid =
                new WorkSchedule(List.of(new WorkScheduleEntry(n(1), new DelayCondition(5))), true);
        helper.assertTrue(
                !CompassEdits.save(owner, slot, named, null, "", invalid),
                "Timetable accepted an Idle destination");
        var schedule =
                new WorkSchedule(
                        List.of(
                                new WorkScheduleEntry(n(2), new DelayCondition(10)),
                                new WorkScheduleEntry(n(0), new DelayCondition(5))),
                        false);
        helper.assertTrue(
                CompassEdits.save(owner, slot, named, null, "", schedule),
                "Valid timetable save failed");
        var edited = compass.get(WorkspaceComponents.PLAN.get());
        ItemStack restoredCompass =
                ItemStack.parseOptional(
                        helper.getLevel().registryAccess(),
                        (CompoundTag) compass.save(helper.getLevel().registryAccess()));
        helper.assertTrue(
                edited.equals(restoredCompass.get(WorkspaceComponents.PLAN.get())),
                "Compass NBT lost names or timetable");
        CompassEditor.setMode(compass, owner, CompassEditor.APPLY_TO_MAID);
        maid.mobInteract(owner, InteractionHand.MAIN_HAND);
        var applied = WorkspaceLogic.state(maid);
        helper.assertTrue(
                workDestination(applied) == 2
                        && indices(applied.remainingRoute()).equals(List.of(-1, 1, -2, 2)),
                "First timetable destination did not use graph navigation");
        CompassEditor.setMode(compass, owner, CompassEditor.WORK_AREAS);
        CompassEditor.clearNodes(compass, owner);
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        EntityMaid restored = new EntityMaid(helper.getLevel());
        restored.load(maid.saveWithoutId(new CompoundTag()));
        helper.assertTrue(
                applied.equals(WorkspaceLogic.state(restored)),
                "Maid save depended on edited or destroyed compass");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 500)
    public static void customTimetableVisitsRepeatedAreaAndFinishesAtChosenDestination(
            GameTestHelper helper) {
        floor(helper);
        EntityMaid maid = maid(helper, null);
        maid.setSchedule(MaidSchedule.ALL);
        var graph =
                new FixtureGraph(List.of(point(helper, 9, 4), point(helper, 16, 4)), List.of())
                        .connect(n(0), n(-1))
                        .connect(n(-1), n(1))
                        .connect(n(1), n(-2))
                        .connect(n(-2), n(2));
        var schedule =
                new WorkSchedule(
                        List.of(
                                new WorkScheduleEntry(n(0), new DelayCondition(20)),
                                new WorkScheduleEntry(n(0), new DelayCondition(10)),
                                new WorkScheduleEntry(n(2), new DelayCondition(25))),
                        false);
        var plan =
                plan(helper, graph, List.of(AreaType.WORK, AreaType.IDLE, AreaType.WORK))
                        .withSchedule(schedule);
        WorkspaceController.apply(maid, plan);
        boolean[] visited = {false, false, false};
        int[] completedTicks = {0};
        helper.onEachTick(
                () -> {
                    var state = WorkspaceLogic.state(maid);
                    if (entryPosition(state) == 1 && !state.travelling()) visited[0] = true;
                    if (entryPosition(state) == 2
                            && plan.nodeArea(n(-1)).contains(maid.blockPosition()))
                        visited[1] = true;
                    if (entryPosition(state) == 2
                            && plan.nodeArea(n(-2)).contains(maid.blockPosition()))
                        visited[2] = true;
                    if (state.scheduleFinished()) {
                        helper.assertTrue(
                                visited[0] && visited[1] && visited[2],
                                "Timetable skipped repeated entry or graph nodes");
                        helper.assertTrue(
                                destination(state) == 2
                                        && entryPosition(state) == 2
                                        && state.workTicks() == 25
                                        && !state.travelling()
                                        && area(helper, 18).contains(maid.blockPosition()),
                                "Timetable did not finish correctly at chosen destination");
                        if (++completedTicks[0] >= 8) helper.succeed();
                    }
                });
    }

    private static void bed(GameTestHelper helper, int headX) {
        var state =
                InitBlocks.MAID_BED
                        .get()
                        .defaultBlockState()
                        .setValue(BedBlock.FACING, Direction.EAST);
        helper.setBlock(headX - 1, 2, 4, state.setValue(BedBlock.PART, BedPart.FOOT));
        helper.setBlock(headX, 2, 4, state.setValue(BedBlock.PART, BedPart.HEAD));
    }

    @GameTest(batch = "typed_sleep_bed", template = "test_empty", timeoutTicks = 600)
    public static void sleepUsesShortestGraphRouteAndOnlyBedsInsideSelectedRegion(
            GameTestHelper helper) {
        floor(helper);
        bed(helper, 14);
        bed(helper, 17); // Closer to arrival than the selected bed, but outside its region.
        bed(helper, 20);
        helper.setDayTime(18000);
        EntityMaid maid = maid(helper, null);
        var graph =
                new FixtureGraph(
                                List.of(
                                        point(helper, 6, 13),
                                        point(helper, 13, 13),
                                        point(helper, 9, 4)),
                                List.of())
                        .connect(n(0), n(-1))
                        .connect(n(-1), n(-2))
                        .connect(n(-2), n(1))
                        .connect(n(0), n(-3))
                        .connect(n(-3), n(2));
        WorkspaceController.apply(
                maid, plan(helper, graph, List.of(AreaType.WORK, AreaType.SLEEP, AreaType.SLEEP)));
        helper.assertTrue(
                destination(WorkspaceLogic.state(maid)) == 2,
                "Sleep chose straight-line nearest area instead of shortest graph route");
        helper.assertTrue(
                indices(WorkspaceLogic.state(maid).remainingRoute()).equals(List.of(-3, 2)),
                "Sleep route is incorrect");
        boolean[] slept = {false};
        helper.onEachTick(
                () -> {
                    if (!slept[0] && maid.isSleeping()) {
                        helper.assertTrue(
                                maid.getSleepingPos().orElseThrow().equals(point(helper, 20, 4)),
                                "Maid used a bed outside selected sleep region");
                        helper.assertTrue(
                                !WorkspaceLogic.state(maid).travelling(),
                                "Maid slept before finishing navigation");
                        helper.setDayTime(0);
                        maid.getSchedulePos().restrictTo(maid);
                        helper.assertTrue(
                                indices(WorkspaceLogic.state(maid).remainingRoute())
                                        .equals(List.of(-3, 0)),
                                "Waking return ignored graph");
                        slept[0] = true;
                    } else if (slept[0]
                            && !maid.isSleeping()
                            && !WorkspaceLogic.state(maid).travelling()) {
                        helper.assertTrue(
                                area(helper, 3).contains(maid.blockPosition()),
                                "Maid did not return to work after waking");
                        helper.succeed();
                    }
                });
    }

    @GameTest(batch = "typed_tall_sleep", template = "test_empty", timeoutTicks = 250)
    public static void sleepOnlyPlanSearchesFullCuboidHeight(GameTestHelper helper) {
        floor(helper);
        bed(helper, 5);
        helper.setDayTime(18000);
        EntityMaid maid = maid(helper, null);
        WorkArea tall =
                WorkArea.between(
                        helper.absolutePos(new BlockPos(3, 1, 3)),
                        helper.absolutePos(new BlockPos(6, 21, 6)));
        WorkspacePlan plan =
                fixturePlan(
                        helper.getLevel().dimension().location(),
                        List.of(tall),
                        RouteGraph.EMPTY,
                        List.of(AreaType.SLEEP));
        WorkspaceController.apply(maid, plan);
        helper.assertTrue(
                workDestination(WorkspaceLogic.state(maid)) == -1,
                "Sleep-only plan created a work cursor");
        helper.succeedWhen(
                () -> {
                    helper.assertTrue(
                            maid.isSleeping(), "POI search missed the bottom of a tall cuboid");
                    helper.assertTrue(
                            maid.getSleepingPos().orElseThrow().equals(point(helper, 5, 4)),
                            "Wrong bed selected in tall cuboid");
                    helper.assertTrue(
                            WorkspaceLogic.state(maid).workTicks() == 0,
                            "Sleep-only plan accumulated work time");
                });
    }

    @GameTest(batch = "typed_off_graph", template = "test_empty", timeoutTicks = 400)
    public static void offGraphSleepEntersNearestWaypointInTargetComponent(GameTestHelper helper) {
        floor(helper);
        helper.setDayTime(18000);
        EntityMaid maid = maid(helper, null);
        maid.moveTo(helper.absoluteVec(new Vec3(9.5, 2, 8.5)));
        var graph =
                new FixtureGraph(
                                List.of(
                                        point(helper, 10, 8),
                                        point(helper, 12, 8),
                                        point(helper, 16, 8)),
                                List.of())
                        .connect(n(0), n(-1))
                        .connect(n(-2), n(-3))
                        .connect(n(-3), n(2));
        var plan = plan(helper, graph, List.of(AreaType.WORK, AreaType.IDLE, AreaType.SLEEP));
        WorkspaceController.apply(maid, plan);
        helper.assertTrue(
                indices(WorkspaceLogic.state(maid).remainingRoute()).equals(List.of(-2, -3, 2)),
                "Off-graph route used an unrelated nearby component");
        helper.assertTrue(
                WorkspaceController.navigationTarget(maid).equals(point(helper, 12, 8)),
                "Off-graph entry was not nearest connected waypoint");
        boolean[] visited = {false, false};
        helper.onEachTick(
                () -> {
                    if (plan.nodeArea(n(-2)).contains(maid.blockPosition())) visited[0] = true;
                    if (plan.nodeArea(n(-3)).contains(maid.blockPosition())) visited[1] = true;
                    if (!WorkspaceLogic.state(maid).travelling()) {
                        helper.assertTrue(
                                visited[0] && visited[1],
                                "Off-graph navigation skipped required points");
                        helper.assertTrue(
                                area(helper, 18).contains(maid.blockPosition()),
                                "Off-graph sleep did not reach its destination");
                        helper.succeed();
                    }
                });
    }

    @GameTest(batch = "typed_off_graph", template = "test_empty", timeoutTicks = 400)
    public static void offGraphSleepVisitsTheNearestAreaBeforeItsWaypoints(GameTestHelper helper) {
        floor(helper);
        helper.setDayTime(18000);
        EntityMaid maid = maid(helper, null);
        maid.moveTo(helper.absoluteVec(new Vec3(9.5, 2, 4.5)));
        var graph =
                new FixtureGraph(List.of(point(helper, 10, 10)), List.of())
                        .connect(n(0), n(-1))
                        .connect(n(-1), n(2));
        var plan = plan(helper, graph, List.of(AreaType.WORK, AreaType.IDLE, AreaType.SLEEP));
        WorkspaceController.apply(maid, plan);
        helper.assertTrue(
                WorkspaceLogic.state(maid).remainingRoute().equals(List.of(n(0), n(-1), n(2))),
                "Off-graph navigation skipped the closest connected area");
        helper.assertTrue(
                WorkspaceController.navigationTarget(maid)
                        .equals(area(helper, 3).navigationCenter()),
                "Navigation did not target the area entry first");
        boolean[] visited = {false, false};
        helper.onEachTick(
                () -> {
                    if (plan.nodeArea(n(0)).contains(maid.blockPosition())) visited[0] = true;
                    if (plan.nodeArea(n(-1)).contains(maid.blockPosition())) visited[1] = true;
                    helper.assertTrue(
                            WorkspaceLogic.state(maid).workTicks() == 0,
                            "Passing through a work area during sleep navigation counted as work");
                    if (!WorkspaceLogic.state(maid).travelling()) {
                        helper.assertTrue(
                                visited[0] && visited[1],
                                "Navigation did not visit both the area entry and its waypoint");
                        helper.assertTrue(
                                area(helper, 18).contains(maid.blockPosition()),
                                "Off-graph area-entry navigation did not reach the sleep"
                                        + " destination");
                        helper.succeed();
                    }
                });
    }

    private static WorkspacePlan reloadPlan(GameTestHelper helper) {
        var graph =
                new FixtureGraph(List.of(point(helper, 10, 10)), List.of())
                        .connect(n(0), n(-1))
                        .connect(n(-1), n(2));
        return plan(helper, graph, List.of(AreaType.WORK, AreaType.IDLE, AreaType.SLEEP));
    }

    private static WorkspacePlan homeResumePlan(GameTestHelper helper, WorkspaceNode foreign) {
        var graph =
                new FixtureGraph(List.of(point(helper, 10, 10), point(helper, 16, 10)), List.of())
                        .connect(n(0), n(-1))
                        .connect(n(-1), n(1))
                        .connect(n(1), n(-2))
                        .connect(n(-2), n(2));
        var plan =
                plan(helper, graph, List.of(AreaType.WORK, AreaType.IDLE, AreaType.SLEEP))
                        .addNode(foreign);
        return plan.withGraph(
                plan.graph()
                        .connect(foreign.id(), n(0))
                        .connect(foreign.id(), n(1))
                        .connect(foreign.id(), n(2)));
    }

    private static WorkspaceState pendingForeignRoute(
            WorkspacePlan plan, WorkspaceNode foreign, AreaType type) {
        var target = n(type.ordinal());
        return new WorkspaceState(
                plan,
                target,
                837,
                true,
                List.of(foreign.id(), target),
                type,
                n(0),
                null,
                false,
                false);
    }

    @GameTest(batch = "home_resume_schedules", template = "test_empty", timeoutTicks = 100)
    public static void homeReenableReplansAllScheduleTypesAfterRelocation(GameTestHelper helper) {
        floor(helper);
        EntityMaid maid = maid(helper, null);
        var foreign =
                WorkspaceNode.waypoint(
                        ResourceLocation.parse("minecraft:the_nether"), new BlockPos(10, 64, 10));
        var plan = homeResumePlan(helper, foreign);
        int[] times = {0, 13000, 18000};
        List<List<UUID>> expected =
                List.of(
                        List.of(n(0)),
                        List.of(n(0), n(-1), n(1)),
                        List.of(n(0), n(-1), n(1), n(-2), n(2)));
        for (int i = 0; i < times.length; i++) {
            helper.setDayTime(times[i]);
            var pending = pendingForeignRoute(plan, foreign, AreaType.values()[i]);
            WorkspaceAccess.writeState(maid, pending, true);
            // Repeating true must preserve a legitimate in-progress cross-dimension route.
            maid.setHomeModeEnable(true);
            helper.assertTrue(
                    WorkspaceAccess.state(maid).equals(pending),
                    "An unchanged Home setting reset a pending route");
            // No entity tick occurs between disabling, moving and re-enabling Home.
            maid.setHomeModeEnable(false);
            maid.moveTo(helper.absoluteVec(new Vec3(8.5, 2, 4.5)));
            maid.setHomeModeEnable(true);
            WorkspaceController.onMaidTick(
                    new com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent(maid));
            var resumed = WorkspaceLogic.state(maid);
            helper.assertTrue(
                    !resumed.waitingDimension()
                            && resumed.remainingRoute().equals(expected.get(i))
                            && resumed.scheduleType() == AreaType.values()[i],
                    "Re-enabling Home kept an old foreign route for " + AreaType.values()[i]);
            helper.assertTrue(
                    resumed.plan().equals(plan)
                            && resumed.workTicks() == 837
                            && resumed.workDestination().equals(n(0)),
                    "Home switch lost configuration or work progress");
            helper.assertTrue(
                    maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                    "Re-enabling Home did not initiate navigation");
        }
        helper.succeed();
    }

    @GameTest(batch = "smart_slab_home_resume", template = "test_empty", timeoutTicks = 450)
    public static void smartSlabRelocationResumesGraphNavigationAfterHomeEnable(
            GameTestHelper helper) {
        floor(helper);
        helper.setDayTime(18000);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        EntityMaid maid = maid(helper, owner);
        var foreign =
                WorkspaceNode.waypoint(
                        ResourceLocation.parse("minecraft:the_nether"), new BlockPos(10, 64, 10));
        var plan = homeResumePlan(helper, foreign);
        WorkspaceAccess.writeState(maid, pendingForeignRoute(plan, foreign, AreaType.SLEEP), true);
        owner.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(InitItems.SMART_SLAB_EMPTY.get()));
        maid.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(
                maid.isRemoved() && owner.getMainHandItem().is(InitItems.SMART_SLAB_HAS_MAID.get()),
                "TLM smart slab did not capture the maid");
        BlockPos floor = helper.absolutePos(new BlockPos(8, 1, 4));
        owner.getMainHandItem()
                .useOn(
                        new UseOnContext(
                                owner,
                                InteractionHand.MAIN_HAND,
                                new BlockHitResult(
                                        Vec3.atCenterOf(floor), Direction.UP, floor, false)));
        var restored = (EntityMaid) helper.getLevel().getEntity(maid.getUUID());
        helper.assertTrue(
                restored != null && restored != maid && !restored.isHomeModeEnable(),
                "Smart slab did not restore a maid with Home disabled");
        restored.setHomeModeEnable(true);
        WorkspaceController.onMaidTick(
                new com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent(restored));
        helper.assertTrue(
                WorkspaceLogic.state(restored)
                        .remainingRoute()
                        .equals(List.of(n(0), n(-1), n(1), n(-2), n(2))),
                "Smart slab relocation kept the old route after enabling Home");
        boolean[] visited = {false, false, false};
        helper.onEachTick(
                () -> {
                    if (plan.nodeArea(n(0)).contains(restored.blockPosition())) visited[0] = true;
                    if (plan.nodeArea(n(-1)).contains(restored.blockPosition())) visited[1] = true;
                    if (plan.nodeArea(n(-2)).contains(restored.blockPosition())) visited[2] = true;
                    helper.assertTrue(
                            WorkspaceLogic.state(restored).workTicks() == 837,
                            "Home resume reset or consumed the saved work clock");
                    if (!WorkspaceLogic.state(restored).travelling()) {
                        helper.assertTrue(
                                visited[0]
                                        && visited[1]
                                        && visited[2]
                                        && area(helper, 18).contains(restored.blockPosition()),
                                "Restored maid skipped graph nodes or did not reach the sleep"
                                    + " area");
                        helper.succeed();
                    }
                });
    }

    private static EntityMaid transfer(EntityMaid maid, ServerLevel level, Vec3 position) {
        return (EntityMaid)
                maid.changeDimension(
                        new DimensionTransition(
                                level, position, Vec3.ZERO, 0, 0, DimensionTransition.DO_NOTHING));
    }

    private static void assertReloadEntry(GameTestHelper helper, EntityMaid maid) {
        WorkspaceController.onMaidTick(
                new com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent(maid));
        var state = WorkspaceLogic.state(maid);
        helper.assertTrue(
                state.scheduleType() == AreaType.SLEEP
                        && state.remainingRoute().equals(List.of(n(0), n(-1), n(2)))
                        && !state.waitingDimension(),
                "Return after a schedule change skipped the closest graph entry");
        helper.assertTrue(
                maid.getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .orElseThrow()
                        .getTarget()
                        .currentBlockPosition()
                        .equals(area(helper, 3).navigationCenter()),
                "Restored navigation walked directly to the destination");
        helper.assertTrue(
                state.workTicks() == 837 && state.workDestination().equals(n(0)),
                "Reload or schedule change lost work progress");
    }

    @GameTest(batch = "schedule_transfer_placement", template = "test_empty", timeoutTicks = 100)
    public static void scheduleChangeDuringTransferPlansFromFinalPlacement(GameTestHelper helper) {
        floor(helper);
        helper.setDayTime(0);
        EntityMaid maid = maid(helper, null);
        WorkspaceAccess.writeState(maid, fixtureState(reloadPlan(helper), 0, 837, false), true);
        var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        // These numeric coordinates match a waypoint in the destination dimension, but the
        // transfer actually places the maid outside every node. Loading must not plan from them.
        var foreign = transfer(maid, nether, Vec3.atBottomCenterOf(point(helper, 10, 10)));
        helper.setDayTime(18000);
        var returned =
                transfer(foreign, helper.getLevel(), helper.absoluteVec(new Vec3(9.5, 2, 4.5)));
        assertReloadEntry(helper, returned);
        helper.succeed();
    }

    @GameTest(batch = "schedule_chunk_reload", template = "test_empty", timeoutTicks = 1200)
    public static void foreignChunkReloadAfterScheduleChangeReentersGraph(GameTestHelper helper) {
        floor(helper);
        helper.setDayTime(0);
        EntityMaid maid = maid(helper, null);
        var plan = reloadPlan(helper);
        WorkspaceAccess.writeState(maid, fixtureState(plan, 0, 837, false), true);
        maid.setNoAi(true);
        maid.setNoGravity(true);
        var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        int chunkX = 40, chunkZ = 40;
        nether.setChunkForced(chunkX, chunkZ, true);
        // GameTest advances ticks without real-time pacing. Finish the surrounding chunk
        // generation synchronously, then pump its task queue while waiting for entity visibility.
        for (int x = chunkX - 2; x <= chunkX + 2; x++)
            for (int z = chunkZ - 2; z <= chunkZ + 2; z++) nether.getChunk(x, z);
        var foreign = transfer(maid, nether, new Vec3(chunkX * 16 + 8.5, 128, chunkZ * 16 + 8.5));
        UUID id = foreign.getUUID();
        EntityMaid[] returned = {null};
        boolean[] visited = {false, false};
        helper.onEachTick(
                () -> {
                    if (returned[0] == null) return;
                    var current = returned[0];
                    if (plan.nodeArea(n(0)).contains(current.blockPosition())) visited[0] = true;
                    if (plan.nodeArea(n(-1)).contains(current.blockPosition())) visited[1] = true;
                    helper.assertTrue(
                            WorkspaceLogic.state(current).workTicks() == 837,
                            "Reloaded sleep navigation consumed work delay");
                });
        helper.startSequence()
                .thenWaitUntil(
                        () -> {
                            for (int i = 0; i < 64 && nether.getChunkSource().pollTask(); i++) {}
                            helper.assertTrue(
                                    nether.getEntity(id) == foreign,
                                    "Destination chunk is not tracking the maid yet");
                        })
                .thenExecute(() -> nether.setChunkForced(chunkX, chunkZ, false))
                .thenWaitUntil(
                        () ->
                                helper.assertTrue(
                                        foreign.isRemoved()
                                                && foreign.getRemovalReason()
                                                        == Entity.RemovalReason.UNLOADED_TO_CHUNK,
                                        "Foreign chunk has not actually unloaded its maid"))
                .thenExecute(
                        () -> {
                            helper.setDayTime(18000);
                            nether.setChunkForced(chunkX, chunkZ, true);
                        })
                .thenWaitUntil(
                        () -> {
                            for (int i = 0; i < 64 && nether.getChunkSource().pollTask(); i++) {}
                            helper.assertTrue(
                                    nether.getEntity(id) instanceof EntityMaid,
                                    "Maid has not reloaded from chunk storage");
                        })
                .thenExecute(
                        () -> {
                            var reloaded = (EntityMaid) nether.getEntity(id);
                            helper.assertTrue(
                                    reloaded != foreign, "Unload retained the old entity");
                            WorkspaceController.onMaidTick(
                                    new com.github.tartaricacid.touhoulittlemaid.api.event
                                            .MaidTickEvent(reloaded));
                            var waiting = WorkspaceLogic.state(reloaded);
                            helper.assertTrue(
                                    waiting.scheduleType() == AreaType.SLEEP
                                            && waiting.waitingDimension()
                                            && waiting.remainingRoute().isEmpty(),
                                    "Foreign schedule should wait without a local graph entry");
                            returned[0] =
                                    transfer(
                                            reloaded,
                                            helper.getLevel(),
                                            helper.absoluteVec(new Vec3(9.5, 2, 4.5)));
                            returned[0].setNoAi(false);
                            returned[0].setNoGravity(false);
                            nether.setChunkForced(chunkX, chunkZ, false);
                            assertReloadEntry(helper, returned[0]);
                        })
                .thenWaitUntil(
                        () ->
                                helper.assertTrue(
                                        !WorkspaceLogic.state(returned[0]).travelling(),
                                        "Returned maid has not completed the graph route"))
                .thenExecute(
                        () ->
                                helper.assertTrue(
                                        visited[0]
                                                && visited[1]
                                                && area(helper, 18)
                                                        .contains(returned[0].blockPosition()),
                                        "Restored route skipped an entry or waypoint"))
                .thenSucceed();
    }
}
