package com.erobrine.tlmcw.gametest;

import static com.erobrine.tlmcw.gametest.V2Fixtures.*;

import com.erobrine.tlmcw.Config;
import com.erobrine.tlmcw.TouhouLittleMaidCustomWorkspace;
import com.erobrine.tlmcw.compat.LittleMaidCompat;
import com.erobrine.tlmcw.item.WorkspaceItems;
import com.erobrine.tlmcw.workspace.*;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskNormalFarm;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.tartaricacid.touhoulittlemaid.item.ItemKappaCompass;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(TouhouLittleMaidCustomWorkspace.MODID)
@PrefixGameTestTemplate(false)
public final class WorkspaceGameTests {
    private static void floor(GameTestHelper helper) {
        for (int x = 1; x <= 22; x++)
            for (int z = 1; z <= 14; z++) helper.setBlock(x, 1, z, Blocks.STONE);
    }

    private static WorkArea area(GameTestHelper helper, int x) {
        return WorkArea.between(
                helper.absolutePos(new BlockPos(x, 1, 3)),
                helper.absolutePos(new BlockPos(x + 3, 3, 6)));
    }

    private static EntityMaid maid(GameTestHelper helper) {
        return maid(helper, null);
    }

    private static EntityMaid maid(GameTestHelper helper, Player owner) {
        // A plain mock player has no negotiated mod connection. Supply its owner lookup locally.
        EntityMaid maid =
                new EntityMaid(helper.getLevel()) {
                    @Override
                    public net.minecraft.world.entity.LivingEntity getOwner() {
                        return owner == null ? super.getOwner() : owner;
                    }
                };
        maid.moveTo(helper.absoluteVec(new Vec3(3.5, 2, 3.5)));
        maid.setTame(true, true);
        maid.setOwnerUUID(UUID.randomUUID());
        helper.getLevel().addFreshEntity(maid);
        maid.setSchedule(MaidSchedule.ALL);
        maid.getSchedulePos().setWorkPos(helper.absolutePos(new BlockPos(3, 2, 3)));
        maid.getSchedulePos().setIdlePos(helper.absolutePos(new BlockPos(9, 2, 9)));
        maid.getSchedulePos().setSleepPos(helper.absolutePos(new BlockPos(18, 2, 11)));
        maid.getSchedulePos().setDimension(helper.getLevel().dimension().location());
        maid.getSchedulePos().setConfigured(true);
        maid.setHomeModeEnable(true);
        return maid;
    }

    private static WorkspacePlan plan(GameTestHelper helper) {
        return fixturePlan(
                helper.getLevel().dimension().location(),
                List.of(area(helper, 3), area(helper, 12)));
    }

    @GameTest(template = "test_empty", timeoutTicks = 500)
    public static void rotatesAndWorksInNextArea(GameTestHelper helper) {
        floor(helper);
        helper.setBlock(3, 1, 3, Blocks.FARMLAND);
        helper.setBlock(3, 2, 3, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        helper.setBlock(12, 1, 3, Blocks.FARMLAND);
        helper.setBlock(12, 2, 3, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        EntityMaid maid = maid(helper);
        maid.setTask(new TaskNormalFarm());
        maid.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
        maid.getAvailableInv(true).insertItem(0, new ItemStack(Items.WHEAT_SEEDS, 32), false);
        WorkspacePlan plan = plan(helper);
        maid.setAndSyncData(
                LittleMaidCompat.WORKSPACES,
                fixtureState(plan, 0, Config.WORK_TICKS.get() - 30L, false));
        maid.getSchedulePos().restrictTo(maid);
        helper.runAtTickTime(
                20,
                () -> {
                    helper.assertTrue(
                            destination(WorkspaceLogic.state(maid)) == 0,
                            "Rotated before the game tick budget");
                    helper.assertTrue(
                            helper.getBlockState(new BlockPos(3, 2, 3)).getValue(CropBlock.AGE) < 7,
                            "First area was not harvested before rotation");
                    helper.assertTrue(
                            helper.getBlockState(new BlockPos(12, 2, 3)).getValue(CropBlock.AGE)
                                    == 7,
                            "Inactive area was worked before rotation");
                });
        helper.succeedWhen(
                () -> {
                    var state = WorkspaceLogic.state(maid);
                    helper.assertTrue(
                            destination(state) == 1 && !state.travelling(),
                            "Not yet working in second area");
                    helper.assertTrue(
                            area(helper, 12).contains(maid.blockPosition()),
                            "Not inside second area");
                    helper.assertTrue(
                            !helper.getBlockState(new BlockPos(12, 2, 3)).is(Blocks.WHEAT)
                                    || helper.getBlockState(new BlockPos(12, 2, 3))
                                                    .getValue(CropBlock.AGE)
                                            < 7,
                            "Second area not harvested");
                });
    }

    // Tests that change the world's time need separate batches to avoid changing
    // another schedule test's day while it is checking its paused counter.
    @GameTest(batch = "schedule_clock", template = "test_empty", timeoutTicks = 100)
    public static void idleSleepAndTimeSetDoNotAdvanceWorkClock(GameTestHelper helper) {
        floor(helper);
        EntityMaid maid = maid(helper);
        maid.setSchedule(MaidSchedule.DAY);
        BlockPos idle = maid.getSchedulePos().getIdlePos();
        BlockPos sleep = maid.getSchedulePos().getSleepPos();
        maid.setAndSyncData(LittleMaidCompat.WORKSPACES, fixtureState(plan(helper), 0, 73, false));
        helper.setDayTime(13000);
        maid.getSchedulePos().restrictTo(maid);
        helper.assertTrue(
                maid.getRestrictCenter().equals(idle), "Idle center was replaced by a work area");
        helper.runAtTickTime(
                12,
                () -> {
                    helper.assertTrue(
                            WorkspaceLogic.state(maid).workTicks() == 73,
                            "Idle time counted as work");
                    helper.setDayTime(18000);
                    maid.getSchedulePos().restrictTo(maid);
                    helper.assertTrue(
                            maid.getRestrictCenter().equals(sleep),
                            "Sleep center was replaced by a work area");
                });
        helper.runAtTickTime(
                24,
                () -> {
                    helper.assertTrue(
                            WorkspaceLogic.state(maid).workTicks() == 73,
                            "Sleep time counted as work");
                    helper.setDayTime(0);
                    maid.moveTo(helper.absoluteVec(new Vec3(3.5, 2, 3.5)));
                    maid.getSchedulePos().restrictTo(maid);
                });
        helper.runAtTickTime(
                65,
                () -> {
                    var state = WorkspaceLogic.state(maid);
                    helper.assertTrue(
                            state.workTicks() > 73 && state.workTicks() < 115,
                            "Clock did not resume using game ticks");
                    helper.assertTrue(
                            destination(state) == 0, "Changing day time triggered rotation");
                    helper.assertTrue(
                            maid.getSchedulePos().getIdlePos().equals(idle), "Idle point changed");
                    helper.assertTrue(
                            maid.getSchedulePos().getSleepPos().equals(sleep),
                            "Sleep point changed");
                    helper.succeed();
                });
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void compassSelectionOwnershipAndRestore(GameTestHelper helper) {
        floor(helper);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        EntityMaid maid = maid(helper, owner);
        maid.setOwnerUUID(owner.getUUID());
        maid.setHomeModeEnable(false);
        BlockPos idle = maid.getSchedulePos().getIdlePos();
        BlockPos sleep = maid.getSchedulePos().getSleepPos();
        ItemStack compass = new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, compass);
        for (BlockPos corner : List.of(area(helper, 3).min(), area(helper, 3).max())) {
            compass.useOn(
                    new UseOnContext(
                            owner,
                            InteractionHand.MAIN_HAND,
                            new BlockHitResult(
                                    Vec3.atCenterOf(corner), Direction.UP, corner, false)));
        }
        helper.assertTrue(
                compass.get(WorkspaceComponents.PLAN.get()).areas().size() == 1,
                "Two clicks did not create a box");
        maid.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(
                WorkspaceLogic.state(maid) == null,
                "Cuboid tool applied data without selecting the Apply tool");
        CompassEditor.setMode(compass, owner, CompassEditor.APPLY_TO_MAID);
        maid.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(
                WorkspaceLogic.state(maid).plan().areas().size() == 1, "Owner failed to apply");
        helper.assertTrue(
                maid.getSchedulePos().getIdlePos().equals(idle)
                        && maid.getSchedulePos().getSleepPos().equals(sleep),
                "Applying work areas changed idle/sleep");
        Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        stranger.setUUID(UUID.randomUUID());
        stranger.setShiftKeyDown(true);
        compass.getItem().interactLivingEntity(compass, stranger, maid, InteractionHand.MAIN_HAND);
        helper.assertTrue(WorkspaceLogic.state(maid) != null, "Non-owner cleared the work areas");
        owner.setShiftKeyDown(true);
        boolean sitting = maid.isMaidInSittingPose();
        maid.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(
                WorkspaceLogic.state(maid) == null,
                "Owner could not restore the original work area");
        helper.assertTrue(
                maid.isMaidInSittingPose() == sitting,
                "Smart compass crouch interaction toggled sitting");
        helper.assertTrue(
                maid.getSchedulePos().getIdlePos().equals(idle)
                        && maid.getSchedulePos().getSleepPos().equals(sleep),
                "Restoring changed idle/sleep");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void savedProgressAndCompassDraftSurviveReload(GameTestHelper helper) {
        floor(helper);
        EntityMaid maid = maid(helper);
        var graph =
                new FixtureGraph(List.of(helper.absolutePos(new BlockPos(9, 2, 10))), List.of())
                        .connect(n(0), n(-1))
                        .connect(n(-1), n(1));
        WorkspaceState progress =
                fixtureState(attach(plan(helper), graph), 1, 837, true, List.of(-1, 1));
        maid.setAndSyncData(LittleMaidCompat.WORKSPACES, progress);
        CompoundTag tag = maid.saveWithoutId(new CompoundTag());
        EntityMaid restored = new EntityMaid(helper.getLevel());
        restored.load(tag);
        helper.assertTrue(
                progress.equals(WorkspaceLogic.state(restored)),
                "Entity save lost area or game tick progress");
        restored.getSchedulePos().restrictTo(restored);
        helper.assertTrue(
                restored.getRestrictCenter().equals(progress.navigationArea().navigationCenter()),
                "Reload restarted the route at the destination");
        helper.assertTrue(
                restored.getSchedulePos().getIdlePos().equals(maid.getSchedulePos().getIdlePos())
                        && restored.getSchedulePos()
                                .getSleepPos()
                                .equals(maid.getSchedulePos().getSleepPos()),
                "Save changed idle/sleep points");
        EntityMaid other = maid(helper);
        WorkspaceController.apply(
                other, fixturePlan(dim(progress.plan()), List.of(area(helper, 3))));
        helper.assertTrue(
                progress.equals(WorkspaceLogic.state(maid)),
                "Another maid's assignment changed this maid");

        ItemStack compass = new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get());
        compass.set(WorkspaceComponents.EDIT_MODE.get(), CompassEditor.ROUTE_NODES);
        compass.set(WorkspaceComponents.ROUTE_ANCHOR.get(), n(-1));
        compass.set(WorkspaceComponents.PLAN.get(), progress.plan());
        compass.set(
                WorkspaceComponents.FIRST_CORNER.get(),
                new SelectionCorner(
                        helper.getLevel().dimension().location(), area(helper, 3).min()));
        ItemStack restoredCompass =
                ItemStack.parseOptional(
                        helper.getLevel().registryAccess(),
                        (CompoundTag) compass.save(helper.getLevel().registryAccess()));
        helper.assertTrue(
                CompassEditor.isSmartCompass(restoredCompass),
                "Compass save lost the smart item type");
        helper.assertTrue(
                CompassEditor.mode(restoredCompass) == CompassEditor.ROUTE_NODES,
                "Compass save lost the selected tool");
        helper.assertTrue(
                progress.plan().equals(restoredCompass.get(WorkspaceComponents.PLAN.get())),
                "Compass save lost its completed draft");
        helper.assertTrue(
                !restoredCompass.has(WorkspaceComponents.FIRST_CORNER.get()),
                "Incomplete selection survived saving");
        helper.assertTrue(
                restoredCompass.get(WorkspaceComponents.ROUTE_ANCHOR.get()).equals(n(-1)),
                "Persistent route anchor was lost on save");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void unversionedCompassAndMaidSaveUpgradeWithoutLosingProgress(
            GameTestHelper helper) {
        EntityMaid maid = maid(helper);
        var graph =
                new FixtureGraph(List.of(helper.absolutePos(new BlockPos(9, 2, 10))), List.of())
                        .connect(n(0), n(-1))
                        .connect(n(-1), n(1));
        var plan =
                attach(plan(helper), graph)
                        .withName(n(0), "菜田")
                        .withName(n(1), "果园")
                        .withSchedule(
                                new WorkSchedule(
                                        List.of(
                                                new WorkScheduleEntry(
                                                        n(1), new DelayCondition(600)),
                                                new WorkScheduleEntry(
                                                        n(0), new DelayCondition(120))),
                                        true));
        var progress = fixtureState(plan, 1, 73, true, List.of(-1, 1), AreaType.WORK, 1, 0, false);
        String maidKey = LittleMaidCompat.WORKSPACES.getKey().toString();
        for (boolean versioned : List.of(false, true)) {
            CompoundTag oldState = legacyStateTag(progress, versioned);
            var expected =
                    WorkspaceState.CODEC
                            .parse(net.minecraft.nbt.NbtOps.INSTANCE, oldState)
                            .getOrThrow();
            helper.assertTrue(
                    expected.readable()
                            && expected.workTicks() == 73
                            && expected.remainingRoute().size() == 2,
                    "Legacy fixture did not preserve runtime");
            CompoundTag savedMaid = maid.saveWithoutId(new CompoundTag());
            savedMaid.getCompound("MaidTaskDataMaps").put(maidKey, oldState);
            EntityMaid restored = new EntityMaid(helper.getLevel());
            restored.load(savedMaid);
            helper.assertTrue(
                    expected.equals(WorkspaceAccess.state(restored)),
                    "Real maid load did not migrate at its codec boundary");
            var resaved =
                    restored.saveWithoutId(new CompoundTag())
                            .getCompound("MaidTaskDataMaps")
                            .getCompound(maidKey);
            helper.assertTrue(
                    resaved.getInt("data_version") == 2
                            && resaved.getCompound("plan").getInt("data_version") == 2,
                    "Maid did not immediately become V2");
            helper.assertTrue(
                    expected.equals(
                            LittleMaidCompat.WORKSPACES.readSyncData(
                                    LittleMaidCompat.WORKSPACES.writeSyncData(expected))),
                    "Sync lost UUID runtime references");
            ItemStack compass = new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get());
            compass.set(WorkspaceComponents.EDIT_MODE.get(), CompassEditor.APPLY_TO_MAID);
            CompoundTag savedCompass =
                    (CompoundTag) compass.save(helper.getLevel().registryAccess());
            String planKey = WorkspaceComponents.PLAN.getId().toString();
            savedCompass.getCompound("components").put(planKey, legacyPlanTag(plan, versioned));
            ItemStack loaded =
                    ItemStack.parseOptional(helper.getLevel().registryAccess(), savedCompass);
            helper.assertTrue(
                    expected.plan().equals(WorkspaceAccess.plan(loaded)),
                    "Old maid/compass snapshots got different identities");
            var encoded = (CompoundTag) loaded.save(helper.getLevel().registryAccess());
            helper.assertTrue(
                    encoded.getCompound("components").getCompound(planKey).getInt("data_version")
                            == 2,
                    "Compass was not migrated on its first read");
            WorkspaceAccess.writePlan(
                    loaded, WorkspacePlan.empty(helper.getLevel().dimension().location()));
            helper.assertTrue(
                    expected.equals(WorkspaceAccess.state(restored)),
                    "Editing migrated compass changed maid snapshot");
        }
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void originalCompassKeepsItsScheduleInteractionsEvenWithLegacyData(
            GameTestHelper helper) {
        floor(helper);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        EntityMaid maid = maid(helper, owner);
        maid.setOwnerUUID(owner.getUUID());
        maid.setHomeModeEnable(false);
        ItemStack original = new ItemStack(InitItems.KAPPA_COMPASS.get());
        original.set(WorkspaceComponents.CUBOID_MODE.get(), true);
        original.set(WorkspaceComponents.PLAN.get(), plan(helper));
        // Include old component data in an actual save/load round trip before interacting.
        original =
                ItemStack.parseOptional(
                        helper.getLevel().registryAccess(),
                        (CompoundTag) original.save(helper.getLevel().registryAccess()));
        helper.assertTrue(
                !CompassEditor.isSmartCompass(original),
                "Legacy mode activated addon behavior on original compass");
        owner.setItemInHand(InteractionHand.MAIN_HAND, original);
        List<BlockPos> points =
                List.of(
                        helper.absolutePos(new BlockPos(3, 2, 3)),
                        helper.absolutePos(new BlockPos(9, 2, 9)),
                        helper.absolutePos(new BlockPos(18, 2, 11)));
        for (BlockPos point : points) {
            original.useOn(
                    new UseOnContext(
                            owner,
                            InteractionHand.MAIN_HAND,
                            new BlockHitResult(
                                    Vec3.atCenterOf(point), Direction.UP, point, false)));
        }
        helper.assertTrue(
                ItemKappaCompass.getRecordCount(original) == 3,
                "Original compass failed to record three schedule points");
        helper.assertTrue(
                ItemKappaCompass.getPoint(Activity.WORK, original).equals(points.get(0)),
                "Original work point was hidden or changed");
        helper.assertTrue(
                ItemKappaCompass.getPoint(Activity.IDLE, original).equals(points.get(1)),
                "Original idle point changed");
        helper.assertTrue(
                ItemKappaCompass.getPoint(Activity.REST, original).equals(points.get(2)),
                "Original sleep point changed");
        maid.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(
                maid.getSchedulePos().getWorkPos().equals(points.get(0))
                        && maid.getSchedulePos().getIdlePos().equals(points.get(1))
                        && maid.getSchedulePos().getSleepPos().equals(points.get(2)),
                "Original compass failed to apply its schedule points");
        helper.assertTrue(
                WorkspaceLogic.state(maid) == null,
                "Original compass applied a legacy cuboid draft");
        helper.assertTrue(
                original.get(WorkspaceComponents.PLAN.get()).equals(plan(helper)),
                "Original interaction edited legacy draft data");
        owner.setShiftKeyDown(true);
        maid.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(
                !maid.getSchedulePos().isConfigured(),
                "Original crouch interaction failed to clear maid schedule");
        BlockPos point = points.get(0);
        original.useOn(
                new UseOnContext(
                        owner,
                        InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(point), Direction.UP, point, false)));
        helper.assertTrue(
                ItemKappaCompass.getRecordCount(original) == 0,
                "Original crouch interaction failed to clear recorded points");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void smartCompassRecipeProducesTheDedicatedItem(GameTestHelper helper) {
        var holder =
                helper.getLevel()
                        .getRecipeManager()
                        .byKey(
                                ResourceLocation.fromNamespaceAndPath(
                                        TouhouLittleMaidCustomWorkspace.MODID,
                                        "kappa_smart_compass"))
                        .orElseThrow();
        helper.assertTrue(
                holder.value() instanceof CraftingRecipe,
                "Smart compass recipe did not load as a crafting recipe");
        CraftingRecipe recipe = (CraftingRecipe) holder.value();
        CraftingInput input =
                CraftingInput.of(
                        2,
                        2,
                        List.of(
                                new ItemStack(Items.COPPER_INGOT),
                                ItemStack.EMPTY,
                                new ItemStack(InitItems.KAPPA_COMPASS.get()),
                                new ItemStack(Items.REDSTONE)));
        helper.assertTrue(
                recipe.matches(input, helper.getLevel()),
                "Smart compass ingredients did not match");
        ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(
                CompassEditor.isSmartCompass(result) && result.getCount() == 1,
                "Crafting did not produce one functional smart compass");
        helper.assertTrue(
                result.getMaxStackSize() == 1, "Smart compass allows stacked independent drafts");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void routeEditorConnectsWorkAreasUsingFeetForBlockAndAirClicks(
            GameTestHelper helper) {
        floor(helper);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack compass = new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, compass);
        compass.set(WorkspaceComponents.PLAN.get(), plan(helper));
        CompassEditor.setMode(compass, owner, CompassEditor.ROUTE_NODES);
        owner.moveTo(helper.absoluteVec(new Vec3(3.5, 2, 4.5)));
        owner.setXRot(90);
        compass.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(
                compass.get(WorkspaceComponents.ROUTE_ANCHOR.get()).equals(n(0)),
                "Work area was not selected as start node");
        owner.moveTo(helper.absoluteVec(new Vec3(9.5, 2, 4.5)));
        owner.setXRot(-90);
        BlockPos unrelatedBlock = helper.absolutePos(new BlockPos(20, 1, 13));
        compass.useOn(
                new UseOnContext(
                        owner,
                        InteractionHand.MAIN_HAND,
                        new BlockHitResult(
                                Vec3.atCenterOf(unrelatedBlock),
                                Direction.UP,
                                unrelatedBlock,
                                false)));
        var updated = compass.get(WorkspaceComponents.PLAN.get());
        helper.assertTrue(
                points(updated).equals(List.of(owner.blockPosition())),
                "Click recorded the block instead of player feet");
        helper.assertTrue(
                updated.graph()
                        .edges()
                        .contains(new RouteEdge(n(0), updated.nodes(AreaType.WAYPOINT).get(0))),
                "New point was not connected to the start area");
        owner.moveTo(helper.absoluteVec(new Vec3(13.5, 2, 4.5)));
        owner.setXRot(90);
        compass.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        updated = compass.get(WorkspaceComponents.PLAN.get());
        helper.assertTrue(
                updated.graph()
                        .shortestPath(updated, n(0), n(1))
                        .equals(List.of(n(0), updated.nodes(AreaType.WAYPOINT).get(0), n(1))),
                "Could not finish a route at an existing work area");
        helper.assertTrue(
                updated.graph()
                        .shortestPath(updated, n(1), n(0))
                        .equals(List.of(n(1), updated.nodes(AreaType.WAYPOINT).get(0), n(0))),
                "Graph edges are not undirected");
        owner.setShiftKeyDown(true);
        compass.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(
                compass.get(WorkspaceComponents.ROUTE_ANCHOR.get()).equals(n(1)),
                "Crouch-click cleared the selected node");
        helper.assertTrue(
                compass.get(WorkspaceComponents.PLAN.get()).equals(updated),
                "Clicking the selected node changed the graph");
        owner.moveTo(helper.absoluteVec(new Vec3(9.5, 2, 11.5)));
        owner.setXRot(-90);
        compass.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        updated = compass.get(WorkspaceComponents.PLAN.get());
        helper.assertTrue(
                points(updated).size() == 2 && points(updated).get(1).equals(owner.blockPosition()),
                "Crouch-air click did not create a point at player feet");
        helper.assertTrue(
                updated.graph()
                                .edges()
                                .contains(
                                        new RouteEdge(
                                                n(1), updated.nodes(AreaType.WAYPOINT).get(1)))
                        && compass.get(WorkspaceComponents.ROUTE_ANCHOR.get())
                                .equals(updated.nodes(AreaType.WAYPOINT).get(1)),
                "Crouch-air click did not connect the new point");
        owner.moveTo(helper.absoluteVec(new Vec3(3.5, 2, 4.5)));
        owner.setXRot(90);
        compass.useOn(
                new UseOnContext(
                        owner,
                        InteractionHand.MAIN_HAND,
                        new BlockHitResult(
                                Vec3.atCenterOf(unrelatedBlock),
                                Direction.UP,
                                unrelatedBlock,
                                false)));
        updated = compass.get(WorkspaceComponents.PLAN.get());
        helper.assertTrue(
                updated.graph()
                                .edges()
                                .contains(
                                        new RouteEdge(
                                                updated.nodes(AreaType.WAYPOINT).get(1), n(0)))
                        && compass.get(WorkspaceComponents.ROUTE_ANCHOR.get()).equals(n(0)),
                "Crouch-block click did not connect the existing node");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void appliedPlanSurvivesCompassEditsAndRemoval(GameTestHelper helper) {
        floor(helper);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        EntityMaid maid = maid(helper, owner);
        maid.setOwnerUUID(owner.getUUID());
        maid.setHomeModeEnable(false);
        var graph =
                new FixtureGraph(List.of(helper.absolutePos(new BlockPos(9, 2, 4))), List.of())
                        .connect(n(0), n(-1))
                        .connect(n(-1), n(1));
        WorkspacePlan draft = attach(plan(helper), graph);
        ItemStack compass = new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get());
        compass.set(WorkspaceComponents.PLAN.get(), draft);
        compass.set(WorkspaceComponents.EDIT_MODE.get(), CompassEditor.ROUTE_NODES);
        compass.set(WorkspaceComponents.ROUTE_ANCHOR.get(), n(0));
        owner.setItemInHand(InteractionHand.MAIN_HAND, compass);
        CompassEditor.setMode(compass, owner, CompassEditor.APPLY_TO_MAID);
        maid.mobInteract(owner, InteractionHand.MAIN_HAND);
        WorkspaceState applied = WorkspaceLogic.state(maid);
        helper.assertTrue(
                applied != null && applied.plan().equals(draft),
                "Maid interaction did not apply the complete graph");

        CompassEditor.setMode(compass, owner, CompassEditor.ROUTE_NODES);
        compass.set(WorkspaceComponents.ROUTE_ANCHOR.get(), n(0));

        owner.moveTo(helper.absoluteVec(new Vec3(9.5, 2, 11.5)));
        owner.setXRot(-90);
        compass.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(
                points(compass.get(WorkspaceComponents.PLAN.get())).size() == 2,
                "Compass edit did not add a node");
        helper.assertTrue(
                applied.equals(WorkspaceLogic.state(maid)),
                "Editing the compass changed the maid's applied plan");
        compass.set(
                WorkspaceComponents.PLAN.get(),
                compass.get(WorkspaceComponents.PLAN.get()).removeNode(n(-1), false));
        helper.assertTrue(
                applied.equals(WorkspaceLogic.state(maid)),
                "Deleting a draft node changed the maid's graph");
        CompassEditor.clearNodes(compass, owner);
        helper.assertTrue(
                applied.equals(WorkspaceLogic.state(maid)),
                "Clearing route points changed the maid's applied plan");
        CompassEditor.setMode(compass, owner, CompassEditor.WORK_AREAS);
        CompassEditor.clearNodes(compass, owner);
        helper.assertTrue(
                applied.equals(WorkspaceLogic.state(maid)),
                "Clearing work areas changed the maid's applied plan");
        compass.shrink(1);
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(
                applied.equals(WorkspaceLogic.state(maid)),
                "Destroying the compass changed the maid's applied plan");
        EntityMaid restored = new EntityMaid(helper.getLevel());
        restored.load(maid.saveWithoutId(new CompoundTag()));
        helper.assertTrue(
                applied.equals(WorkspaceLogic.state(restored)),
                "Maid save required the original compass to restore its plan");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void readMaidCopiesCompletePlanWithoutChangingRuntime(GameTestHelper helper) {
        floor(helper);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        EntityMaid maid = maid(helper, owner);
        maid.setOwnerUUID(owner.getUUID());
        maid.setHomeModeEnable(false);
        var graph =
                new FixtureGraph(List.of(helper.absolutePos(new BlockPos(9, 2, 4))), List.of())
                        .connect(n(0), n(-1))
                        .connect(n(-1), n(1))
                        .connect(n(1), n(2))
                        .connect(n(2), n(3));
        WorkspacePlan assigned =
                attach(
                                fixturePlan(
                                                helper.getLevel().dimension().location(),
                                                List.of(
                                                        area(helper, 3),
                                                        area(helper, 12),
                                                        area(helper, 17),
                                                        area(helper, 19)))
                                        .withType(n(2), AreaType.IDLE)
                                        .withType(n(3), AreaType.SLEEP)
                                        .withName(n(0), "农田")
                                        .withName(n(1), "牧场")
                                        .withName(n(2), "客厅")
                                        .withName(n(3), "卧室"),
                                graph)
                        .withSchedule(
                                new WorkSchedule(
                                        List.of(
                                                new WorkScheduleEntry(
                                                        n(0), new DelayCondition(240)),
                                                new WorkScheduleEntry(
                                                        n(1), new DelayCondition(600))),
                                        false));
        WorkspaceState progress =
                fixtureState(assigned, 1, 73, true, List.of(-1, 1), AreaType.WORK, 1, 1, false);
        maid.setAndSyncData(LittleMaidCompat.WORKSPACES, progress);
        Vec3 position = maid.position();
        var nativeWork = maid.getSchedulePos().getWorkPos();
        var nativeIdle = maid.getSchedulePos().getIdlePos();
        var nativeSleep = maid.getSchedulePos().getSleepPos();
        ItemStack compass = new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get());
        compass.set(WorkspaceComponents.PLAN.get(), plan(helper));
        owner.setItemInHand(InteractionHand.MAIN_HAND, compass);
        CompassEditor.setMode(compass, owner, CompassEditor.READ_FROM_MAID);
        compass.set(
                WorkspaceComponents.FIRST_CORNER.get(),
                new SelectionCorner(
                        helper.getLevel().dimension().location(), area(helper, 3).min()));
        compass.set(WorkspaceComponents.ROUTE_ANCHOR.get(), n(0));
        owner.setShiftKeyDown(true);
        maid.mobInteract(owner, InteractionHand.MAIN_HAND);
        owner.setShiftKeyDown(false);
        helper.assertTrue(
                assigned.equals(compass.get(WorkspaceComponents.PLAN.get())),
                "Read lost areas, types, names, routes or custom schedule");
        helper.assertTrue(
                progress.equals(WorkspaceLogic.state(maid))
                        && maid.position().equals(position)
                        && !maid.isMaidInSittingPose(),
                "Read changed the maid's runtime or invoked TLM's sneak interaction");
        helper.assertTrue(
                nativeWork.equals(maid.getSchedulePos().getWorkPos())
                        && nativeIdle.equals(maid.getSchedulePos().getIdlePos())
                        && nativeSleep.equals(maid.getSchedulePos().getSleepPos()),
                "Read changed original compass positions");
        helper.assertTrue(
                !compass.has(WorkspaceComponents.FIRST_CORNER.get())
                        && n(0).equals(compass.get(WorkspaceComponents.ROUTE_ANCHOR.get())),
                "Reading silently canceled the persistent route anchor");
        ItemStack restored =
                ItemStack.parseOptional(
                        helper.getLevel().registryAccess(),
                        (CompoundTag) compass.save(helper.getLevel().registryAccess()));
        helper.assertTrue(
                CompassEditor.mode(restored) == CompassEditor.READ_FROM_MAID
                        && assigned.equals(restored.get(WorkspaceComponents.PLAN.get())),
                "Read mode or copied configuration did not survive saving");
        WorkspacePlan edited = assigned.withName(n(0), "已编辑").clearRoutePoints();
        compass.set(WorkspaceComponents.PLAN.get(), edited);
        helper.assertTrue(
                progress.equals(WorkspaceLogic.state(maid)),
                "Editing the copied draft changed the maid");
        CompassEditor.setMode(compass, owner, CompassEditor.APPLY_TO_MAID);
        maid.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(
                edited.equals(WorkspaceLogic.state(maid).plan()),
                "Edited readback could not be reapplied");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void rejectedReadPreservesDraftAndMaid(GameTestHelper helper) {
        floor(helper);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        Player other = helper.makeMockPlayer(GameType.SURVIVAL);
        EntityMaid maid = maid(helper, owner);
        maid.setOwnerUUID(owner.getUUID());
        maid.setHomeModeEnable(false);
        WorkspaceState assigned = WorkspaceState.initial(plan(helper).withName(n(0), "女仆配置"));
        maid.setAndSyncData(LittleMaidCompat.WORKSPACES, assigned);
        ItemStack compass = new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get());
        WorkspacePlan draft = plan(helper).withName(n(0), "原草稿");
        compass.set(WorkspaceComponents.PLAN.get(), draft);
        compass.set(WorkspaceComponents.EDIT_MODE.get(), CompassEditor.READ_FROM_MAID);
        compass.set(WorkspaceComponents.ROUTE_ANCHOR.get(), n(0));
        other.setItemInHand(InteractionHand.MAIN_HAND, compass);
        compass.interactLivingEntity(other, maid, InteractionHand.MAIN_HAND);
        helper.assertTrue(
                draft.equals(compass.get(WorkspaceComponents.PLAN.get()))
                        && assigned.equals(WorkspaceLogic.state(maid)),
                "A non-owner could read or modify the configuration");
        owner.setItemInHand(InteractionHand.OFF_HAND, compass);
        compass.interactLivingEntity(owner, maid, InteractionHand.OFF_HAND);
        helper.assertTrue(
                draft.equals(compass.get(WorkspaceComponents.PLAN.get())),
                "Offhand read replaced the draft");
        owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        owner.setItemInHand(InteractionHand.MAIN_HAND, compass);
        WorkspaceController.clear(maid);
        maid.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(
                WorkspaceLogic.state(maid) == null
                        && draft.equals(compass.get(WorkspaceComponents.PLAN.get()))
                        && n(0).equals(compass.get(WorkspaceComponents.ROUTE_ANCHOR.get())),
                "Missing configuration erased the draft or its selection");
        compass.useOn(
                new UseOnContext(
                        owner,
                        InteractionHand.MAIN_HAND,
                        new BlockHitResult(
                                Vec3.atCenterOf(area(helper, 3).min()),
                                Direction.UP,
                                area(helper, 3).min(),
                                false)));
        helper.assertTrue(
                draft.equals(compass.get(WorkspaceComponents.PLAN.get()))
                        && !compass.has(WorkspaceComponents.FIRST_CORNER.get()),
                "Read tool created a cuboid when used on a block");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void readMaidDuringNativeScheduleCopiesAssignedPlan(GameTestHelper helper) {
        floor(helper);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        EntityMaid maid = maid(helper, owner);
        maid.setOwnerUUID(owner.getUUID());
        maid.setSchedule(MaidSchedule.DAY);
        maid.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        WorkspaceState assigned = WorkspaceState.initial(plan(helper)).suspend(AreaType.IDLE);
        maid.setAndSyncData(LittleMaidCompat.WORKSPACES, assigned);
        helper.assertTrue(
                WorkspaceLogic.active(maid) == null,
                "Fixture unexpectedly has a custom active idle area");
        ItemStack compass = new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get());
        compass.set(WorkspaceComponents.EDIT_MODE.get(), CompassEditor.READ_FROM_MAID);
        owner.setItemInHand(InteractionHand.MAIN_HAND, compass);
        maid.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(
                assigned.plan().equals(compass.get(WorkspaceComponents.PLAN.get()))
                        && assigned.equals(WorkspaceLogic.state(maid)),
                "Native schedule fallback prevented reading the full saved plan");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void clearingNodesUsesTheSelectedToolAndKeepsOtherNodesEditable(
            GameTestHelper helper) {
        floor(helper);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack compass = new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, compass);
        var graph =
                new FixtureGraph(
                                List.of(
                                        helper.absolutePos(new BlockPos(9, 2, 4)),
                                        helper.absolutePos(new BlockPos(9, 2, 11))),
                                List.of())
                        .connect(n(0), n(-1))
                        .connect(n(-1), n(-2))
                        .connect(n(-2), n(1))
                        .connect(n(0), n(1));
        WorkspacePlan original = attach(plan(helper), graph);
        compass.set(WorkspaceComponents.PLAN.get(), original);
        compass.set(
                WorkspaceComponents.FIRST_CORNER.get(),
                new SelectionCorner(
                        helper.getLevel().dimension().location(), area(helper, 3).min()));
        compass.set(WorkspaceComponents.ROUTE_ANCHOR.get(), n(0));
        CompassEditor.clearNodes(compass, owner);
        WorkspacePlan cleared = compass.get(WorkspaceComponents.PLAN.get());
        helper.assertTrue(
                cleared.areas().isEmpty() && points(cleared).equals(graph.points()),
                "Work tool clear removed route points or retained work areas");
        helper.assertTrue(
                cleared.graph().edges().equals(List.of(new RouteEdge(n(-1), n(-2)))),
                "Work clear changed surviving point connections");
        helper.assertTrue(
                !compass.has(WorkspaceComponents.FIRST_CORNER.get())
                        && n(0).equals(compass.get(WorkspaceComponents.ROUTE_ANCHOR.get())),
                "Bulk clear silently canceled the persistent route anchor");
        CompassEditor.cancelRoute(compass, owner);
        CompassEditor.setMode(compass, owner, CompassEditor.ROUTE_NODES);
        owner.moveTo(helper.absoluteVec(new Vec3(9.5, 2, 4.5)));
        owner.setXRot(90);
        compass.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(
                compass.get(WorkspaceComponents.ROUTE_ANCHOR.get()).equals(n(-1)),
                "Retained point could not be selected without work areas");
        owner.moveTo(helper.absoluteVec(new Vec3(9.5, 2, 13.5)));
        owner.setXRot(-90);
        compass.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        cleared = compass.get(WorkspaceComponents.PLAN.get());
        helper.assertTrue(
                points(cleared).size() == 3
                        && cleared.graph()
                                .edges()
                                .contains(
                                        new RouteEdge(
                                                n(-1), cleared.nodes(AreaType.WAYPOINT).get(2))),
                "Retained point graph could not be extended without work areas");
        CompassEditor.setMode(compass, owner, CompassEditor.WORK_AREAS);
        for (BlockPos corner : List.of(area(helper, 3).min(), area(helper, 3).max())) {
            compass.useOn(
                    new UseOnContext(
                            owner,
                            InteractionHand.MAIN_HAND,
                            new BlockHitResult(
                                    Vec3.atCenterOf(corner), Direction.UP, corner, false)));
        }
        helper.assertTrue(
                compass.get(WorkspaceComponents.PLAN.get()).areas().size() == 1
                        && compass.get(WorkspaceComponents.PLAN.get())
                                .graph()
                                .equals(cleared.graph()),
                "Adding a new work area lost the retained point graph");
        compass.set(WorkspaceComponents.PLAN.get(), original);
        CompassEditor.setMode(compass, owner, CompassEditor.ROUTE_NODES);
        CompassEditor.clearNodes(compass, owner);
        cleared = compass.get(WorkspaceComponents.PLAN.get());
        helper.assertTrue(
                cleared.areas().equals(original.areas()) && points(cleared).isEmpty(),
                "Route tool clear removed work areas or retained route points");
        helper.assertTrue(
                cleared.graph().edges().equals(List.of(new RouteEdge(n(0), n(1)))),
                "Route clear lost direct work-area connections");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void enteringDestinationEarlyDoesNotSkipRequiredNodes(GameTestHelper helper) {
        floor(helper);
        EntityMaid maid = maid(helper);
        BlockPos waypoint = helper.absolutePos(new BlockPos(9, 2, 11));
        var graph =
                new FixtureGraph(List.of(waypoint), List.of())
                        .connect(n(0), n(-1))
                        .connect(n(-1), n(1));
        var state = WorkspaceState.initial(attach(plan(helper), graph)).withTravel(false).next();
        maid.setAndSyncData(LittleMaidCompat.WORKSPACES, state);
        maid.moveTo(helper.absoluteVec(new Vec3(13.5, 2, 4.5)));
        maid.getSchedulePos().restrictTo(maid);
        helper.runAtTickTime(
                3,
                () -> {
                    var current = WorkspaceLogic.state(maid);
                    helper.assertTrue(
                            current.travelling() && current.workTicks() == 0,
                            "Destination bypassed a mandatory waypoint");
                    helper.assertTrue(
                            WorkspaceController.navigationTarget(maid).equals(waypoint),
                            "Return behavior skipped the waypoint");
                    maid.moveTo(Vec3.atBottomCenterOf(waypoint));
                });
        helper.runAtTickTime(
                6,
                () -> {
                    helper.assertTrue(
                            indices(WorkspaceLogic.state(maid).remainingRoute()).equals(List.of(1)),
                            "Single-block waypoint was not advanced");
                    maid.moveTo(helper.absoluteVec(new Vec3(13.5, 2, 4.5)));
                });
        helper.runAtTickTime(
                10,
                () -> {
                    helper.assertTrue(
                            !WorkspaceLogic.state(maid).travelling(),
                            "Destination did not start work after visiting its waypoint");
                    helper.succeed();
                });
    }

    @GameTest(template = "test_empty", timeoutTicks = 500)
    public static void disconnectedGraphStillWalksDirectlyToNextWorkArea(GameTestHelper helper) {
        floor(helper);
        EntityMaid maid = maid(helper);
        var graph =
                new FixtureGraph(List.of(helper.absolutePos(new BlockPos(9, 2, 11))), List.of())
                        .connect(n(0), n(-1));
        maid.setAndSyncData(
                LittleMaidCompat.WORKSPACES,
                fixtureState(attach(plan(helper), graph), 0, Config.WORK_TICKS.get() - 2L, false));
        maid.getSchedulePos().restrictTo(maid);
        helper.onEachTick(
                () -> {
                    var state = WorkspaceLogic.state(maid);
                    if (destination(state) != 1) return;
                    helper.assertTrue(
                            indices(state.remainingRoute()).isEmpty(),
                            "Disconnected graph supplied a route");
                    helper.assertTrue(
                            WorkspaceController.navigationTarget(maid)
                                    .equals(state.activeArea().navigationCenter()),
                            "Fallback did not target the destination center");
                    if (!state.travelling()) {
                        helper.assertTrue(
                                state.activeArea().contains(maid.blockPosition()),
                                "Direct fallback started work outside the destination");
                        helper.succeed();
                    }
                });
    }

    @GameTest(batch = "schedule_route", template = "test_empty", timeoutTicks = 100)
    public static void idleAndSleepPreserveAnInFlightRoute(GameTestHelper helper) {
        floor(helper);
        EntityMaid maid = maid(helper);
        maid.setSchedule(MaidSchedule.DAY);
        BlockPos point = helper.absolutePos(new BlockPos(9, 2, 11));
        var graph =
                new FixtureGraph(List.of(point), List.of())
                        .connect(n(0), n(-1))
                        .connect(n(-1), n(1));
        maid.setAndSyncData(
                LittleMaidCompat.WORKSPACES,
                fixtureState(attach(plan(helper), graph), 1, 0, true, List.of(-1, 1)));
        helper.setDayTime(13000);
        maid.getSchedulePos().restrictTo(maid);
        helper.assertTrue(
                maid.getRestrictCenter().equals(maid.getSchedulePos().getIdlePos()),
                "Graph replaced the idle center");
        helper.runAtTickTime(
                12,
                () -> {
                    helper.assertTrue(
                            indices(WorkspaceLogic.state(maid).remainingRoute())
                                    .equals(List.of(-1, 1)),
                            "Idle discarded the unfinished route");
                    helper.setDayTime(18000);
                    maid.getSchedulePos().restrictTo(maid);
                    helper.assertTrue(
                            maid.getRestrictCenter().equals(maid.getSchedulePos().getSleepPos()),
                            "Graph replaced the sleep center");
                });
        helper.runAtTickTime(
                24,
                () -> {
                    helper.assertTrue(
                            indices(WorkspaceLogic.state(maid).remainingRoute())
                                    .equals(List.of(-1, 1)),
                            "Sleep discarded the unfinished route");
                    helper.assertTrue(
                            WorkspaceLogic.state(maid).workTicks() == 0,
                            "Rest counted as route work");
                    helper.setDayTime(0);
                    maid.moveTo(Vec3.atBottomCenterOf(point));
                    maid.getSchedulePos().restrictTo(maid);
                });
        helper.runAtTickTime(
                35,
                () -> {
                    helper.assertTrue(
                            indices(WorkspaceLogic.state(maid).remainingRoute()).equals(List.of(1)),
                            "Work did not resume the saved route");
                    helper.assertTrue(
                            WorkspaceLogic.state(maid).workTicks() == 0,
                            "Waypoint consumed working time");
                    helper.succeed();
                });
    }

    @GameTest(template = "test_route", timeoutTicks = 2400)
    public static void followsMandatoryNodesAroundALongWall(GameTestHelper helper) {
        longWallRoute(helper, false);
    }

    @GameTest(template = "test_route", timeoutTicks = 2400)
    public static void followsTheSameUndirectedRouteInReverse(GameTestHelper helper) {
        longWallRoute(helper, true);
    }

    private static void longWallRoute(GameTestHelper helper, boolean reverse) {
        for (int x = 1; x <= 70; x++)
            for (int z = 1; z <= 18; z++) helper.setBlock(x, 1, z, Blocks.STONE);
        for (int y = 2; y <= 4; y++) {
            for (int x = 3; x <= 64; x++) {
                helper.setBlock(x, y, 3, Blocks.STONE);
                helper.setBlock(x, y, 9, Blocks.STONE);
            }
            for (int z = 3; z <= 15; z++) helper.setBlock(64, y, z, Blocks.STONE);
        }
        WorkArea a =
                WorkArea.between(
                        helper.absolutePos(new BlockPos(57, 2, 5)),
                        helper.absolutePos(new BlockPos(61, 3, 7)));
        WorkArea b =
                WorkArea.between(
                        helper.absolutePos(new BlockPos(57, 2, 11)),
                        helper.absolutePos(new BlockPos(61, 3, 14)));
        var points =
                List.of(
                        helper.absolutePos(new BlockPos(34, 2, 6)),
                        helper.absolutePos(new BlockPos(2, 2, 6)),
                        helper.absolutePos(new BlockPos(2, 2, 12)),
                        helper.absolutePos(new BlockPos(34, 2, 12)));
        var graph =
                new FixtureGraph(points, List.of())
                        .connect(n(0), n(-1))
                        .connect(n(-1), n(-2))
                        .connect(n(-2), n(-3))
                        .connect(n(-3), n(-4))
                        .connect(n(-4), n(1));
        var plan = fixturePlan(helper.getLevel().dimension().location(), List.of(a, b), graph);
        EntityMaid maid = maid(helper);
        int start = reverse ? 1 : 0, end = reverse ? 0 : 1;
        maid.moveTo(helper.absoluteVec(new Vec3(59.5, 2, reverse ? 12.5 : 6.5)));
        maid.setAndSyncData(LittleMaidCompat.WORKSPACES, fixtureState(plan, end, 0, true));
        var direct = maid.getNavigation().createPath(plan.areas().get(end).navigationCenter(), 0);
        helper.assertTrue(
                direct == null || !direct.canReach(),
                "Long-wall setup unexpectedly allowed a full direct path");
        maid.setAndSyncData(
                LittleMaidCompat.WORKSPACES,
                fixtureState(plan, start, Config.WORK_TICKS.get() - 2L, false));
        maid.getSchedulePos().restrictTo(maid);
        boolean[] visited = new boolean[points.size()];
        helper.onEachTick(
                () -> {
                    for (int i = 0; i < points.size(); i++)
                        if (points.get(i).equals(maid.blockPosition())) visited[i] = true;
                    var state = WorkspaceLogic.state(maid);
                    if (destination(state) != end) return;
                    if (state.travelling()) {
                        helper.assertTrue(
                                state.workTicks() == 0,
                                "Intermediate route nodes counted as working time");
                        maid.getBrain()
                                .getMemory(MemoryModuleType.WALK_TARGET)
                                .ifPresent(
                                        target -> {
                                            helper.assertTrue(
                                                    target.getTarget()
                                                            .currentBlockPosition()
                                                            .equals(
                                                                    state.navigationArea()
                                                                            .navigationCenter()),
                                                    "Periodic return overwrote the current route"
                                                            + " node");
                                            helper.assertTrue(
                                                    target.getCloseEnoughDist() == 0,
                                                    "Route node uses an arrival tolerance");
                                        });
                    } else {
                        for (boolean point : visited)
                            helper.assertTrue(
                                    point, "Maid skipped a mandatory single-block waypoint");
                        helper.assertTrue(
                                state.activeArea().contains(maid.blockPosition()),
                                "Started working outside the final area");
                        helper.succeed();
                    }
                });
    }

    @GameTest(template = "test_empty", timeoutTicks = 200)
    public static void targetsCenterButStartsWorkAtTheFirstBoundaryCrossing(GameTestHelper helper) {
        floor(helper);
        WorkArea area =
                WorkArea.between(
                        helper.absolutePos(new BlockPos(12, 1, 3)),
                        helper.absolutePos(new BlockPos(21, 3, 10)));
        BlockPos center = BlockPos.containing(area.bounds().getCenter());
        EntityMaid maid = maid(helper);
        maid.moveTo(helper.absoluteVec(new Vec3(11.1, 2, 6.5)));
        WorkspaceController.apply(
                maid, fixturePlan(helper.getLevel().dimension().location(), List.of(area)));
        helper.runAtTickTime(
                1,
                () -> {
                    helper.assertTrue(
                            WorkspaceLogic.state(maid).travelling(),
                            "Near the box was treated as inside it");
                    helper.assertTrue(
                            WorkspaceLogic.state(maid).workTicks() == 0,
                            "Travel consumed work ticks");
                });
        helper.onEachTick(
                () -> {
                    var state = WorkspaceLogic.state(maid);
                    helper.assertTrue(
                            maid.getRestrictCenter().equals(center),
                            "Return center is not the box center");
                    if (state.travelling()) {
                        helper.assertTrue(state.workTicks() == 0, "Travel consumed work ticks");
                        maid.getBrain()
                                .getMemory(MemoryModuleType.WALK_TARGET)
                                .ifPresent(
                                        target -> {
                                            helper.assertTrue(
                                                    target.getTarget()
                                                            .currentBlockPosition()
                                                            .equals(center),
                                                    "Walk target drifted from the center");
                                            helper.assertTrue(
                                                    target.getCloseEnoughDist() == 0,
                                                    "TLM restored a distance tolerance");
                                        });
                    } else {
                        helper.assertTrue(
                                area.contains(maid.blockPosition()),
                                "Started working outside the box");
                        helper.assertTrue(
                                center.distManhattan(maid.blockPosition()) > 2,
                                "Waited for the center before working");
                        helper.succeed();
                    }
                });
    }

    @GameTest(template = "test_empty", timeoutTicks = 500)
    public static void walksAroundAConcaveWallBeforeEnteringTheWorkArea(GameTestHelper helper) {
        floor(helper);
        for (int y = 2; y <= 4; y++) {
            for (int z = 3; z <= 12; z++) helper.setBlock(10, y, z, Blocks.STONE);
            for (int x = 5; x <= 10; x++) {
                helper.setBlock(x, y, 3, Blocks.STONE);
                helper.setBlock(x, y, 12, Blocks.STONE);
            }
        }
        WorkArea area =
                WorkArea.between(
                        helper.absolutePos(new BlockPos(12, 1, 5)),
                        helper.absolutePos(new BlockPos(20, 3, 10)));
        EntityMaid maid = maid(helper);
        maid.moveTo(helper.absoluteVec(new Vec3(8.5, 2, 7.5)));
        WorkspaceController.apply(
                maid, fixturePlan(helper.getLevel().dimension().location(), List.of(area)));
        boolean[] detoured = {false};
        helper.onEachTick(
                () -> {
                    if (maid.getX() < helper.absolutePos(new BlockPos(5, 1, 3)).getX())
                        detoured[0] = true;
                    var state = WorkspaceLogic.state(maid);
                    if (state.travelling()) {
                        helper.assertTrue(
                                state.workTicks() == 0, "Travel counted as work behind the wall");
                        maid.getBrain()
                                .getMemory(MemoryModuleType.WALK_TARGET)
                                .ifPresent(
                                        target -> {
                                            helper.assertTrue(
                                                    target.getTarget()
                                                            .currentBlockPosition()
                                                            .equals(area.navigationCenter()),
                                                    "Periodic return replaced the center target");
                                            helper.assertTrue(
                                                    target.getCloseEnoughDist() == 0,
                                                    "Periodic return restored a distance"
                                                            + " tolerance");
                                        });
                    } else {
                        helper.assertTrue(
                                detoured[0],
                                "Reached the box without walking out of the concave wall");
                        helper.assertTrue(
                                area.contains(maid.blockPosition()),
                                "Started work before entering");
                        helper.assertTrue(
                                area.navigationCenter().distManhattan(maid.blockPosition()) > 2,
                                "Continued to the center after entering");
                        helper.succeed();
                    }
                });
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void floorOnlySelectionNeverCountsAsArrived(GameTestHelper helper) {
        floor(helper);
        WorkArea floorOnly =
                WorkArea.between(
                        helper.absolutePos(new BlockPos(3, 1, 3)),
                        helper.absolutePos(new BlockPos(6, 1, 6)));
        EntityMaid maid = maid(helper);
        WorkspaceController.apply(
                maid, fixturePlan(helper.getLevel().dimension().location(), List.of(floorOnly)));
        helper.runAtTickTime(
                20,
                () -> {
                    var state = WorkspaceLogic.state(maid);
                    helper.assertTrue(
                            state.travelling(),
                            "Floor-only selection accepted the standing layer above it");
                    helper.assertTrue(
                            state.workTicks() == 0, "Invalid selection consumed work ticks");
                    helper.assertTrue(
                            !floorOnly.contains(maid.blockPosition()),
                            "Maid entered the solid floor");
                    helper.assertTrue(
                            !maid.isWithinRestriction(), "Restriction accepted feet above the box");
                    helper.succeed();
                });
    }

    @GameTest(template = "test_empty", timeoutTicks = 500)
    public static void rotatesFromUpperToAdjacentLowerAreaUsingStairs(GameTestHelper helper) {
        floor(helper);
        for (int x = 3; x <= 6; x++)
            for (int z = 3; z <= 6; z++) helper.setBlock(x, 4, z, Blocks.STONE);
        // Enclose the upper floor so its only exit is the descending staircase at (7, 4).
        for (int y = 5; y <= 7; y++) {
            for (int x = 2; x <= 7; x++) {
                helper.setBlock(x, y, 2, Blocks.STONE);
                helper.setBlock(x, y, 7, Blocks.STONE);
            }
            for (int z = 3; z <= 6; z++) {
                helper.setBlock(2, y, z, Blocks.STONE);
                if (z != 4) helper.setBlock(7, y, z, Blocks.STONE);
            }
        }
        helper.setBlock(7, 3, 4, Blocks.STONE);
        helper.setBlock(8, 2, 4, Blocks.STONE);
        WorkArea lower =
                WorkArea.between(
                        helper.absolutePos(new BlockPos(3, 1, 3)),
                        helper.absolutePos(new BlockPos(6, 4, 6)));
        WorkArea upper =
                WorkArea.between(
                        helper.absolutePos(new BlockPos(3, 5, 3)),
                        helper.absolutePos(new BlockPos(6, 7, 6)));
        EntityMaid maid = maid(helper);
        maid.moveTo(helper.absoluteVec(new Vec3(4.5, 5, 4.5)));
        WorkspacePlan plan =
                fixturePlan(helper.getLevel().dimension().location(), List.of(upper, lower));
        maid.setAndSyncData(
                LittleMaidCompat.WORKSPACES,
                fixtureState(plan, 0, Config.WORK_TICKS.get() - 2L, false));
        maid.getSchedulePos().restrictTo(maid);
        helper.runAtTickTime(
                6,
                () -> {
                    var state = WorkspaceLogic.state(maid);
                    helper.assertTrue(
                            destination(state) == 1 && state.travelling(),
                            "Upper floor was counted as arrival in lower area");
                    helper.assertTrue(state.workTicks() == 0, "Transfer consumed work ticks");
                    helper.assertTrue(
                            !lower.contains(maid.blockPosition()),
                            "Maid unexpectedly reached lower area already");
                });
        boolean[] usedStairs = {false};
        helper.onEachTick(
                () -> {
                    if (maid.getX() >= helper.absolutePos(new BlockPos(7, 1, 4)).getX())
                        usedStairs[0] = true;
                    var state = WorkspaceLogic.state(maid);
                    if (destination(state) == 1 && !state.travelling()) {
                        helper.assertTrue(usedStairs[0], "Transfer bypassed the staircase");
                        helper.assertTrue(
                                lower.contains(maid.blockPosition()),
                                "Started work outside the lower box");
                        helper.assertTrue(
                                maid.blockPosition().getY()
                                        == helper.absolutePos(new BlockPos(0, 2, 0)).getY(),
                                "Started work before reaching the lower floor");
                        helper.succeed();
                    }
                });
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void pendingLinkSurvivesSlotDimensionModeAndSaveUntilAttack(
            GameTestHelper helper) {
        floor(helper);
        var player =
                new net.minecraft.server.level.ServerPlayer(
                        helper.getLevel().getServer(),
                        helper.getLevel(),
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "editor-test"),
                        net.minecraft.server.level.ClientInformation.createDefault()) {
                    @Override
                    public void displayClientMessage(
                            net.minecraft.network.chat.Component message, boolean actionBar) {}
                };
        ItemStack compass = new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get());
        WorkspaceAccess.writePlan(compass, plan(helper));
        player.getInventory().setItem(0, compass);
        player.getInventory().selected = 0;
        CompassEditor.setMode(compass, player, CompassEditor.ROUTE_NODES);
        player.setPos(helper.absoluteVec(new Vec3(3.5, 2, 4.5)));
        player.setXRot(90);
        CompassEditor.routeClick(compass, player);
        helper.assertTrue(
                n(0).equals(compass.get(WorkspaceComponents.ROUTE_ANCHOR.get())),
                "Failed to start the link");
        player.getInventory().selected = 1;
        CompassEditor.onPlayerTick(
                new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
        helper.assertTrue(
                n(0).equals(compass.get(WorkspaceComponents.ROUTE_ANCHOR.get())),
                "Hotbar switch canceled the link");
        ItemStack restored =
                ItemStack.parseOptional(
                        helper.getLevel().registryAccess(),
                        (CompoundTag) compass.save(helper.getLevel().registryAccess()));
        helper.assertTrue(
                n(0).equals(restored.get(WorkspaceComponents.ROUTE_ANCHOR.get())),
                "Reload canceled the link");
        var nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        Player foreign =
                new Player(
                        nether,
                        BlockPos.ZERO,
                        0,
                        new com.mojang.authlib.GameProfile(
                                UUID.randomUUID(), "cross-dimension-editor")) {
                    @Override
                    public boolean isCreative() {
                        return true;
                    }

                    @Override
                    public boolean isSpectator() {
                        return false;
                    }
                };
        foreign.setItemInHand(InteractionHand.MAIN_HAND, restored);
        foreign.moveTo(10.5, 64, 10.5);
        foreign.setXRot(-90);
        CompassEditor.routeClick(restored, foreign);
        var updated = WorkspaceAccess.plan(restored);
        UUID point = updated.nodes(AreaType.WAYPOINT).getFirst();
        helper.assertTrue(
                updated.node(point).dimension().equals(nether.dimension().location())
                        && updated.node(point).min().equals(foreign.blockPosition()),
                "Foreign point lost dimension or feet");
        helper.assertTrue(
                updated.graph().edges().contains(new RouteEdge(n(0), point)),
                "Cross-dimension edge was rejected");
        player.getInventory().setItem(0, restored);
        player.getInventory().selected = 0;
        CompassEditor.setMode(restored, player, CompassEditor.WORK_AREAS);
        helper.assertTrue(
                point.equals(restored.get(WorkspaceComponents.ROUTE_ANCHOR.get())),
                "Mode switch canceled the link");
        com.erobrine.tlmcw.network.CompassActionPayload.execute(
                player,
                new com.erobrine.tlmcw.network.CompassActionPayload(
                        com.erobrine.tlmcw.network.CompassActionPayload.DELETE,
                        n(0),
                        updated.node(n(0)).min(),
                        updated.node(n(0)).max()));
        helper.assertTrue(
                !restored.has(WorkspaceComponents.ROUTE_ANCHOR.get())
                        && updated.equals(WorkspaceAccess.plan(restored)),
                "Attack did not cancel without deleting data");
        com.erobrine.tlmcw.network.CompassActionPayload.execute(
                player,
                new com.erobrine.tlmcw.network.CompassActionPayload(
                        com.erobrine.tlmcw.network.CompassActionPayload.DELETE,
                        n(0),
                        updated.node(n(0)).min(),
                        updated.node(n(0)).max()));
        helper.assertTrue(
                WorkspaceAccess.plan(restored).node(n(0)) == null
                        && WorkspaceAccess.plan(restored).node(point) != null,
                "Attack without an anchor could not delete a region");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void unknownDataSurvivesRealItemAndMaidLoadAndBlocksEdits(GameTestHelper helper) {
        var p = plan(helper);
        CompoundTag raw =
                (CompoundTag)
                        WorkspacePlan.CODEC
                                .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, p)
                                .getOrThrow();
        raw.putInt("data_version", 99);
        raw.putString("future_field", "keep");
        ItemStack compass = new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get());
        compass.set(WorkspaceComponents.EDIT_MODE.get(), CompassEditor.ROUTE_NODES);
        CompoundTag item = (CompoundTag) compass.save(helper.getLevel().registryAccess());
        item.getCompound("components").put(WorkspaceComponents.PLAN.getId().toString(), raw);
        var restored = ItemStack.parseOptional(helper.getLevel().registryAccess(), item);
        helper.assertTrue(
                WorkspaceAccess.blocked(restored) && WorkspaceAccess.plan(restored) == null,
                "Future item data entered business logic");
        helper.assertTrue(
                !WorkspaceAccess.writePlan(restored, p), "Editing overwrote future item data");
        var saved = (CompoundTag) restored.save(helper.getLevel().registryAccess());
        helper.assertTrue(
                raw.equals(
                        saved.getCompound("components")
                                .get(WorkspaceComponents.PLAN.getId().toString())),
                "Future item data was lost");
        EntityMaid maid = maid(helper);
        var state = WorkspaceState.initial(p);
        CompoundTag stateRaw =
                (CompoundTag)
                        WorkspaceState.CODEC
                                .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, state)
                                .getOrThrow();
        stateRaw.putInt("data_version", 99);
        CompoundTag entity = maid.saveWithoutId(new CompoundTag());
        entity.getCompound("MaidTaskDataMaps")
                .put(LittleMaidCompat.WORKSPACES.getKey().toString(), stateRaw);
        var loaded = new EntityMaid(helper.getLevel());
        loaded.load(entity);
        helper.assertTrue(
                WorkspaceAccess.blocked(loaded) && WorkspaceAccess.state(loaded) == null,
                "Future maid data was decoded as an active configuration");
        helper.assertTrue(
                !WorkspaceAccess.writeState(loaded, state, true),
                "Editing overwrote future maid data");
        helper.assertTrue(
                stateRaw.equals(
                        loaded.saveWithoutId(new CompoundTag())
                                .getCompound("MaidTaskDataMaps")
                                .get(LittleMaidCompat.WORKSPACES.getKey().toString())),
                "TLM round-trip lost future data");
        helper.succeed();
    }

    @GameTest(batch = "cross_dimension_navigation", template = "test_empty", timeoutTicks = 100)
    public static void maidWaitsAtForeignNextNodeAndResumesAfterExternalTransfer(
            GameTestHelper helper) {
        helper.setDayTime(0);
        floor(helper);
        var maid = maid(helper);
        maid.setCustomName(net.minecraft.network.chat.Component.literal("Dimension test maid"));
        maid.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 4));
        maid.setHealth(12);
        maid.getBrain().setActiveActivityIfPossible(Activity.WORK);
        var foreign =
                WorkspaceNode.waypoint(
                        ResourceLocation.parse("minecraft:the_nether"), new BlockPos(10, 64, 10));
        var p = plan(helper).addNode(foreign);
        p = p.withGraph(p.graph().connect(n(0), foreign.id()).connect(foreign.id(), n(1)));
        var route = p.graph().shortestPath(p, n(0), n(1));
        helper.assertTrue(
                route.equals(List.of(n(0), foreign.id(), n(1))),
                "Planner discarded cross-dimension graph edges");
        WorkspaceAccess.writeState(
                maid,
                new WorkspaceState(
                        p,
                        n(1),
                        73,
                        true,
                        List.of(foreign.id(), n(1)),
                        AreaType.WORK,
                        n(1),
                        null,
                        false,
                        false),
                true);
        // Identical numeric coordinates in the wrong dimension must never satisfy the waypoint.
        maid.moveTo(Vec3.atBottomCenterOf(foreign.min()));
        Vec3 position = maid.position();
        int timeout = Config.TRAVEL_TIMEOUT_TICKS.get();
        Config.TRAVEL_TIMEOUT_TICKS.set(1);
        try {
            for (int i = 0; i < 20; i++)
                WorkspaceController.onMaidTick(
                        new com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent(maid));
            var waiting = WorkspaceAccess.state(maid);
            helper.assertTrue(
                    waiting.waitingDimension()
                            && waiting.remainingRoute().equals(List.of(foreign.id(), n(1)))
                            && waiting.workTicks() == 73,
                    "Wait skipped a node, consumed delay or used travel timeout");
            helper.assertTrue(
                    maid.position().equals(position)
                            && !maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                            && !WorkspaceLogic.mayWork(maid),
                    "Waiting still moved or performed work");
            var restored = new EntityMaid(helper.getLevel());
            restored.load(maid.saveWithoutId(new CompoundTag()));
            helper.assertTrue(
                    waiting.equals(WorkspaceAccess.state(restored)),
                    "Saving lost the waiting phase or remaining route");
            var nether =
                    helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            nether.getChunk(foreign.min());
            var transferred =
                    maid.changeDimension(
                            new net.minecraft.world.level.portal.DimensionTransition(
                                    nether,
                                    Vec3.atBottomCenterOf(foreign.min()),
                                    Vec3.ZERO,
                                    0,
                                    0,
                                    net.minecraft.world.level.portal.DimensionTransition
                                            .DO_NOTHING));
            helper.assertTrue(
                    transferred instanceof EntityMaid, "Vanilla transfer returned no maid");
            var netherMaid = (EntityMaid) transferred;
            helper.assertTrue(
                    netherMaid != maid && maid.isRemoved() && netherMaid.level() == nether,
                    "Cross-dimension transfer did not recreate and register the maid");
            // A loaded chunk may not be tracked yet, so UUID lookup can still be hidden.
            helper.assertTrue(
                    netherMaid.isAddedToLevel() && !netherMaid.isRemoved(),
                    "Transferred maid was not registered in the destination level");
            helper.assertTrue(
                    netherMaid.getUUID().equals(maid.getUUID())
                            && netherMaid.getOwnerUUID().equals(maid.getOwnerUUID())
                            && netherMaid.isTame()
                            && netherMaid.getCustomName().equals(maid.getCustomName())
                            && netherMaid.getHealth() == 12
                            && netherMaid.getMainHandItem().is(Items.DIAMOND)
                            && netherMaid.getMainHandItem().getCount() == 4
                            && waiting.suspend(waiting.scheduleType())
                                    .equals(WorkspaceAccess.state(netherMaid)),
                    "Vanilla NBT copy lost identity, owner, inventory or workspace progress");
            helper.assertTrue(
                    !netherMaid.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING),
                    "TLM random-teleport behavior still ran");
            WorkspaceController.onMaidTick(
                    new com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent(
                            netherMaid));
            helper.assertTrue(
                    WorkspaceAccess.state(netherMaid).remainingRoute().equals(List.of(n(1)))
                            && WorkspaceAccess.state(netherMaid).waitingDimension(),
                    "Did not advance the foreign waypoint or wait for return to overworld");
            var returned =
                    netherMaid.changeDimension(
                            new net.minecraft.world.level.portal.DimensionTransition(
                                    helper.getLevel(),
                                    Vec3.atBottomCenterOf(area(helper, 12).min().above()),
                                    Vec3.ZERO,
                                    0,
                                    0,
                                    net.minecraft.world.level.portal.DimensionTransition
                                            .DO_NOTHING));
            helper.assertTrue(
                    returned instanceof EntityMaid && netherMaid.isRemoved(),
                    "Return transfer failed or kept the previous entity alive");
            var returnedMaid = (EntityMaid) returned;
            helper.assertTrue(
                    helper.getLevel().getEntity(maid.getUUID()) == returnedMaid,
                    "Return transfer left a missing or duplicate entity");
            WorkspaceController.onMaidTick(
                    new com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent(
                            returnedMaid));
            var arrived = WorkspaceAccess.state(returnedMaid);
            helper.assertTrue(
                    !arrived.waitingDimension()
                            && !arrived.travelling()
                            && arrived.remainingRoute().isEmpty()
                            && arrived.workTicks() == 73,
                    "External transfer did not resume and finish the existing route");
        } finally {
            Config.TRAVEL_TIMEOUT_TICKS.set(timeout);
        }
        helper.succeed();
    }

    @GameTest(batch = "dimension_replan", template = "test_empty", timeoutTicks = 100)
    public static void dimensionChangeReplansUnconsumedSourceNodes(GameTestHelper helper) {
        floor(helper);
        var maid = maid(helper);
        var nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        var source =
                WorkspaceNode.waypoint(
                        helper.getLevel().dimension().location(),
                        helper.absolutePos(new BlockPos(10, 2, 4)));
        var entry = WorkspaceNode.waypoint(nether.dimension().location(), new BlockPos(5, 128, 4));
        var target =
                WorkspaceNode.area(
                        nether.dimension().location(),
                        WorkArea.between(new BlockPos(16, 128, 3), new BlockPos(19, 130, 6)));
        var scheduleEntry = new WorkScheduleEntry(target.id(), new DelayCondition(1000));
        var assigned =
                new WorkspacePlan(
                        UUID.randomUUID(),
                        List.of(source, entry, target),
                        RouteGraph.EMPTY
                                .connect(source.id(), entry.id())
                                .connect(entry.id(), target.id()),
                        new WorkSchedule(List.of(scheduleEntry), true));
        var before =
                new WorkspaceState(
                        assigned,
                        target.id(),
                        73,
                        true,
                        List.of(source.id(), entry.id(), target.id()),
                        AreaType.WORK,
                        target.id(),
                        scheduleEntry.id(),
                        false,
                        false);
        WorkspaceAccess.writeState(maid, before, true);
        // A portal can overlap the entity before its feet enter the source waypoint's block.
        maid.moveTo(helper.absoluteVec(new Vec3(9.7, 2, 4.5)));
        helper.assertTrue(
                !source.bounds().contains(maid.blockPosition()),
                "Fixture already consumed the source waypoint");
        Vec3 landing = new Vec3(8.5, 128, 4.5);
        nether.getChunk(BlockPos.containing(landing));
        var transferred =
                maid.changeDimension(
                        new net.minecraft.world.level.portal.DimensionTransition(
                                nether,
                                landing,
                                Vec3.ZERO,
                                0,
                                0,
                                net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING));
        helper.assertTrue(transferred instanceof EntityMaid, "Vanilla transfer returned no maid");
        var arrived = (EntityMaid) transferred;
        try {
            helper.assertTrue(
                    before.suspend(AreaType.WORK).equals(WorkspaceAccess.state(arrived)),
                    "Dimension change did not persist pending replanning with work progress");
            WorkspaceController.onMaidTick(
                    new com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent(arrived));
            var replanned = WorkspaceAccess.state(arrived);
            helper.assertTrue(
                    !replanned.suspended()
                            && !replanned.waitingDimension()
                            && replanned.remainingRoute().equals(List.of(entry.id(), target.id())),
                    "New dimension kept waiting for the unconsumed source-dimension node");
            helper.assertTrue(
                    replanned.plan().equals(assigned)
                            && replanned.workTicks() == 73
                            && replanned.workDestination().equals(target.id())
                            && replanned.entryId().equals(scheduleEntry.id()),
                    "Replanning reset the plan, work destination, schedule entry or elapsed ticks");
            helper.assertTrue(
                    arrived.getBrain()
                            .getMemory(MemoryModuleType.WALK_TARGET)
                            .map(
                                    walk ->
                                            walk.getTarget()
                                                    .currentBlockPosition()
                                                    .equals(entry.bounds().navigationCenter()))
                            .orElse(false),
                    "Navigation did not target the nearest entry in the actual new dimension");
        } finally {
            arrived.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "dimension_compatibility_config", template = "test_empty", timeoutTicks = 100)
    public static void dimensionCompatibilitySwitchControlsCooldownAndTransfer(
            GameTestHelper helper) {
        floor(helper);
        var maid = maid(helper);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var saved = fixtureState(plan(helper), 0, 73, false);
        WorkspaceAccess.writeState(maid, saved, true);
        boolean configured = Config.MAID_DIMENSION_COMPATIBILITY.get();
        EntityMaid transferred = null;
        try {
            Config.MAID_DIMENSION_COMPATIBILITY.set(true);
            helper.assertTrue(
                    maid.getDimensionChangingDelay() == player.getDimensionChangingDelay()
                            && maid.getDimensionChangingDelay() == 10,
                    "Enabled compatibility did not match the vanilla player's portal cooldown");
            maid.setPortalCooldown();
            maid.baseTick();
            helper.assertTrue(
                    maid.getPortalCooldown() == 9,
                    "Portal cooldown did not decrease by one game tick");
            maid.setAsInsidePortal(
                    (net.minecraft.world.level.block.Portal) Blocks.NETHER_PORTAL,
                    maid.blockPosition());
            helper.assertTrue(
                    maid.getPortalCooldown() == 10,
                    "Remaining in a portal did not refresh the player-equivalent cooldown");
            for (int i = 0; i < 10; i++) maid.baseTick();
            helper.assertTrue(
                    !maid.isOnPortalCooldown(),
                    "Player-equivalent cooldown did not expire after 10 game ticks outside a"
                        + " portal");

            var nether =
                    helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            Vec3 landing = new Vec3(8.5, 128, 4.5);
            boolean[] callback = {false};
            var transition =
                    new net.minecraft.world.level.portal.DimensionTransition(
                            nether, landing, Vec3.ZERO, 0, 0, entity -> callback[0] = true);
            Config.MAID_DIMENSION_COMPATIBILITY.set(false);
            maid.setPortalCooldown();
            helper.assertTrue(
                    maid.getDimensionChangingDelay() == 300
                            && maid.getPortalCooldown() == 300
                            && player.getDimensionChangingDelay() == 10,
                    "Disabled compatibility did not restore the original entity cooldown");
            var rejected = maid.changeDimension(transition);
            helper.assertTrue(
                    rejected == null
                            && !maid.isRemoved()
                            && maid.level() == helper.getLevel()
                            && !callback[0],
                    "Disabled compatibility still bypassed TLM's dimension-transfer handling");
            helper.assertTrue(
                    saved.equals(WorkspaceAccess.state(maid)),
                    "Disabled dimension compatibility unexpectedly invalidated the old route");

            Config.MAID_DIMENSION_COMPATIBILITY.set(true);
            maid.setPortalCooldown();
            nether.getChunk(BlockPos.containing(landing));
            var result = maid.changeDimension(transition);
            helper.assertTrue(
                    result instanceof EntityMaid && maid.isRemoved() && callback[0],
                    "Re-enabling compatibility did not restore vanilla transfer and callback");
            transferred = (EntityMaid) result;
            helper.assertTrue(
                    transferred.level() == nether
                            && transferred.position().equals(landing)
                            && transferred.getPortalCooldown() == 10
                            && saved.suspend(saved.scheduleType())
                                    .equals(WorkspaceAccess.state(transferred)),
                    "Transfer lost the player cooldown or failed to preserve progress for"
                        + " replanning");
        } finally {
            Config.MAID_DIMENSION_COMPATIBILITY.set(configured);
            if (transferred != null) transferred.discard();
            if (!maid.isRemoved()) maid.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void vanillaSameDimensionTransferUsesTheRequestedPositionAndCallback(
            GameTestHelper helper) {
        floor(helper);
        var maid = maid(helper);
        var saved = fixtureState(plan(helper), 0, 73, false);
        WorkspaceAccess.writeState(maid, saved, true);
        Vec3 destination = helper.absoluteVec(new Vec3(8.5, 2, 10.5));
        Vec3 speed = new Vec3(0.1, 0.2, 0.3);
        boolean[] callback = {false};
        var result =
                maid.changeDimension(
                        new net.minecraft.world.level.portal.DimensionTransition(
                                helper.getLevel(),
                                destination,
                                speed,
                                47,
                                0,
                                entity -> callback[0] = entity == maid));
        helper.assertTrue(
                result == maid
                        && !maid.isRemoved()
                        && maid.position().equals(destination)
                        && maid.getDeltaMovement().equals(speed)
                        && maid.getYRot() == 47
                        && callback[0],
                "Same-dimension vanilla transition ignored position, speed, rotation or callback");
        helper.assertTrue(
                !maid.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING),
                "Same-dimension transition still applied TLM's random-teleport glow");
        helper.assertTrue(
                saved.equals(WorkspaceAccess.state(maid)),
                "A same-dimension transition invalidated schedule progress");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void vanillaDimensionTransferRespectsNeoForgeCancellation(GameTestHelper helper) {
        floor(helper);
        var maid = maid(helper);
        var saved = fixtureState(plan(helper), 0, 73, false);
        WorkspaceAccess.writeState(maid, saved, true);
        Vec3 original = maid.position();
        boolean[] fired = {false};
        java.util.function.Consumer<
                        net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent>
                listener =
                        event -> {
                            if (event.getEntity() == maid) {
                                fired[0] = true;
                                event.setCanceled(true);
                            }
                        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
        try {
            var nether =
                    helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            var result =
                    maid.changeDimension(
                            new net.minecraft.world.level.portal.DimensionTransition(
                                    nether,
                                    original,
                                    Vec3.ZERO,
                                    0,
                                    0,
                                    net.minecraft.world.level.portal.DimensionTransition
                                            .DO_NOTHING));
            helper.assertTrue(
                    fired[0]
                            && result == null
                            && !maid.isRemoved()
                            && maid.level() == helper.getLevel()
                            && maid.position().equals(original),
                    "Canceled vanilla travel moved the maid or failed to fire the NeoForge event");
            helper.assertTrue(
                    saved.equals(WorkspaceAccess.state(maid)),
                    "Canceled dimension travel invalidated schedule progress");
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }
}
