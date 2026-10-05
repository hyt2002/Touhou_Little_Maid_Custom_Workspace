package com.erobrine.tlmcw.gametest;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
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
        for (int x = 1; x <= 22; x++) for (int z = 1; z <= 14; z++) helper.setBlock(x, 1, z, Blocks.STONE);
    }
    private static WorkArea area(GameTestHelper helper, int x) {
        return WorkArea.between(helper.absolutePos(new BlockPos(x, 1, 3)),
                helper.absolutePos(new BlockPos(x + 3, 3, 6)));
    }
    private static EntityMaid maid(GameTestHelper helper) {
        return maid(helper, null);
    }
    private static EntityMaid maid(GameTestHelper helper, Player owner) {
        // A plain mock player has no negotiated mod connection. Supply its owner lookup locally.
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override public net.minecraft.world.entity.LivingEntity getOwner() {
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
        return new WorkspacePlan(helper.getLevel().dimension().location(), List.of(area(helper, 3), area(helper, 12)));
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
        maid.setAndSyncData(LittleMaidCompat.WORKSPACES,
                new WorkspaceState(plan, 0, Config.WORK_TICKS.get() - 30L, false));
        maid.getSchedulePos().restrictTo(maid);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(WorkspaceLogic.state(maid).activeIndex() == 0, "Rotated before the game tick budget");
            helper.assertTrue(helper.getBlockState(new BlockPos(3, 2, 3)).getValue(CropBlock.AGE) < 7,
                    "First area was not harvested before rotation");
            helper.assertTrue(helper.getBlockState(new BlockPos(12, 2, 3)).getValue(CropBlock.AGE) == 7,
                    "Inactive area was worked before rotation");
        });
        helper.succeedWhen(() -> {
            var state = WorkspaceLogic.state(maid);
            helper.assertTrue(state.activeIndex() == 1 && !state.travelling(), "Not yet working in second area");
            helper.assertTrue(area(helper, 12).contains(maid.blockPosition()), "Not inside second area");
            helper.assertTrue(!helper.getBlockState(new BlockPos(12, 2, 3)).is(Blocks.WHEAT)
                    || helper.getBlockState(new BlockPos(12, 2, 3)).getValue(CropBlock.AGE) < 7,
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
        maid.setAndSyncData(LittleMaidCompat.WORKSPACES, new WorkspaceState(plan(helper), 0, 73, false));
        helper.setDayTime(13000);
        maid.getSchedulePos().restrictTo(maid);
        helper.assertTrue(maid.getRestrictCenter().equals(idle), "Idle center was replaced by a work area");
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(WorkspaceLogic.state(maid).workTicks() == 73, "Idle time counted as work");
            helper.setDayTime(18000);
            maid.getSchedulePos().restrictTo(maid);
            helper.assertTrue(maid.getRestrictCenter().equals(sleep), "Sleep center was replaced by a work area");
        });
        helper.runAtTickTime(24, () -> {
            helper.assertTrue(WorkspaceLogic.state(maid).workTicks() == 73, "Sleep time counted as work");
            helper.setDayTime(0);
            maid.moveTo(helper.absoluteVec(new Vec3(3.5, 2, 3.5)));
            maid.getSchedulePos().restrictTo(maid);
        });
        helper.runAtTickTime(65, () -> {
            var state = WorkspaceLogic.state(maid);
            helper.assertTrue(state.workTicks() > 73 && state.workTicks() < 115, "Clock did not resume using game ticks");
            helper.assertTrue(state.activeIndex() == 0, "Changing day time triggered rotation");
            helper.assertTrue(maid.getSchedulePos().getIdlePos().equals(idle), "Idle point changed");
            helper.assertTrue(maid.getSchedulePos().getSleepPos().equals(sleep), "Sleep point changed");
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
            compass.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(corner), Direction.UP, corner, false)));
        }
        helper.assertTrue(compass.get(WorkspaceComponents.PLAN.get()).areas().size() == 1, "Two clicks did not create a box");
        maid.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(WorkspaceLogic.state(maid) == null, "Cuboid tool applied data without selecting the Apply tool");
        CompassEditor.setMode(compass, owner, CompassEditor.APPLY_TO_MAID);
        maid.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(WorkspaceLogic.state(maid).plan().areas().size() == 1, "Owner failed to apply");
        helper.assertTrue(maid.getSchedulePos().getIdlePos().equals(idle) && maid.getSchedulePos().getSleepPos().equals(sleep),
                "Applying work areas changed idle/sleep");
        Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        stranger.setUUID(UUID.randomUUID());
        stranger.setShiftKeyDown(true);
        compass.getItem().interactLivingEntity(compass, stranger, maid, InteractionHand.MAIN_HAND);
        helper.assertTrue(WorkspaceLogic.state(maid) != null, "Non-owner cleared the work areas");
        owner.setShiftKeyDown(true);
        boolean sitting = maid.isMaidInSittingPose();
        maid.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(WorkspaceLogic.state(maid) == null, "Owner could not restore the original work area");
        helper.assertTrue(maid.isMaidInSittingPose() == sitting, "Smart compass crouch interaction toggled sitting");
        helper.assertTrue(maid.getSchedulePos().getIdlePos().equals(idle) && maid.getSchedulePos().getSleepPos().equals(sleep),
                "Restoring changed idle/sleep");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void savedProgressAndCompassDraftSurviveReload(GameTestHelper helper) {
        floor(helper);
        EntityMaid maid = maid(helper);
        var graph = new RouteGraph(List.of(helper.absolutePos(new BlockPos(9, 2, 10))), List.of())
                .connect(0, -1).connect(-1, 1);
        WorkspaceState progress = new WorkspaceState(plan(helper).withGraph(graph), 1, 837, true, List.of(-1, 1));
        maid.setAndSyncData(LittleMaidCompat.WORKSPACES, progress);
        CompoundTag tag = maid.saveWithoutId(new CompoundTag());
        EntityMaid restored = new EntityMaid(helper.getLevel());
        restored.load(tag);
        helper.assertTrue(progress.equals(WorkspaceLogic.state(restored)), "Entity save lost area or game tick progress");
        restored.getSchedulePos().restrictTo(restored);
        helper.assertTrue(restored.getRestrictCenter().equals(progress.navigationArea().navigationCenter()), "Reload restarted the route at the destination");
        helper.assertTrue(restored.getSchedulePos().getIdlePos().equals(maid.getSchedulePos().getIdlePos())
                && restored.getSchedulePos().getSleepPos().equals(maid.getSchedulePos().getSleepPos()), "Save changed idle/sleep points");
        EntityMaid other = maid(helper);
        WorkspaceController.apply(other, new WorkspacePlan(progress.plan().dimension(), List.of(area(helper, 3))));
        helper.assertTrue(progress.equals(WorkspaceLogic.state(maid)), "Another maid's assignment changed this maid");

        ItemStack compass = new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get());
        compass.set(WorkspaceComponents.EDIT_MODE.get(), CompassEditor.ROUTE_NODES);
        compass.set(WorkspaceComponents.ROUTE_ANCHOR.get(), -1);
        compass.set(WorkspaceComponents.PLAN.get(), progress.plan());
        compass.set(WorkspaceComponents.FIRST_CORNER.get(), area(helper, 3).min());
        ItemStack restoredCompass = ItemStack.parseOptional(helper.getLevel().registryAccess(),
                (CompoundTag) compass.save(helper.getLevel().registryAccess()));
        helper.assertTrue(CompassEditor.isSmartCompass(restoredCompass), "Compass save lost the smart item type");
        helper.assertTrue(CompassEditor.mode(restoredCompass) == CompassEditor.ROUTE_NODES, "Compass save lost the selected tool");
        helper.assertTrue(progress.plan().equals(restoredCompass.get(WorkspaceComponents.PLAN.get())), "Compass save lost its completed draft");
        helper.assertTrue(!restoredCompass.has(WorkspaceComponents.FIRST_CORNER.get()), "Incomplete selection survived saving");
        helper.assertTrue(!restoredCompass.has(WorkspaceComponents.ROUTE_ANCHOR.get()), "Temporary selected route node survived saving");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void unversionedCompassAndMaidSaveUpgradeWithoutLosingProgress(GameTestHelper helper) {
        EntityMaid maid = maid(helper);
        var graph = new RouteGraph(List.of(helper.absolutePos(new BlockPos(9, 2, 10))), List.of())
                .connect(0, -1).connect(-1, 1);
        var plan = plan(helper).withGraph(graph).withName(0, "菜田").withName(1, "果园")
                .withSchedule(new WorkSchedule(List.of(new WorkScheduleEntry(1, new DelayCondition(600)),
                        new WorkScheduleEntry(0, new DelayCondition(120))), true));
        var progress = new WorkspaceState(plan, 1, 73, true, List.of(-1, 1), AreaType.WORK, 1, 0, false);
        maid.setAndSyncData(LittleMaidCompat.WORKSPACES, progress);
        String maidKey = LittleMaidCompat.WORKSPACES.getKey().toString();
        CompoundTag savedMaid = maid.saveWithoutId(new CompoundTag());
        CompoundTag stateTag = savedMaid.getCompound("MaidTaskDataMaps").getCompound(maidKey);
        helper.assertTrue(stateTag.getInt("data_version") == WorkspaceDataVersions.MAID_STATE_VERSION,
                "Real maid save omitted its schema version");
        helper.assertTrue(stateTag.getCompound("plan").getInt("data_version") == WorkspaceDataVersions.PLAN_VERSION,
                "Maid's nested plan omitted its schema version");
        stateTag.remove("data_version");
        stateTag.getCompound("plan").remove("data_version");
        EntityMaid restored = new EntityMaid(helper.getLevel());
        restored.load(savedMaid);
        helper.assertTrue(progress.equals(WorkspaceLogic.state(restored)), "Legacy maid migration changed route or condition progress");
        var resaved = restored.saveWithoutId(new CompoundTag()).getCompound("MaidTaskDataMaps").getCompound(maidKey);
        helper.assertTrue(resaved.getInt("data_version") == WorkspaceDataVersions.MAID_STATE_VERSION
                && resaved.getCompound("plan").getInt("data_version") == WorkspaceDataVersions.PLAN_VERSION,
                "Legacy maid did not gain versions on its next save");
        helper.assertTrue(progress.equals(LittleMaidCompat.WORKSPACES.readSyncData(
                LittleMaidCompat.WORKSPACES.writeSyncData(WorkspaceLogic.state(restored)))), "Versioned maid synchronization lost progress");

        ItemStack compass = new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get());
        compass.set(WorkspaceComponents.PLAN.get(), plan);
        compass.set(WorkspaceComponents.EDIT_MODE.get(), CompassEditor.APPLY_TO_MAID);
        CompoundTag savedCompass = (CompoundTag) compass.save(helper.getLevel().registryAccess());
        String planKey = WorkspaceComponents.PLAN.getId().toString();
        var planTag = savedCompass.getCompound("components").getCompound(planKey);
        helper.assertTrue(planTag.getInt("data_version") == WorkspaceDataVersions.PLAN_VERSION,
                "Real compass save omitted its schema version");
        planTag.remove("data_version");
        ItemStack loadedCompass = ItemStack.parseOptional(helper.getLevel().registryAccess(), savedCompass);
        helper.assertTrue(plan.equals(loadedCompass.get(WorkspaceComponents.PLAN.get()))
                && CompassEditor.mode(loadedCompass) == CompassEditor.APPLY_TO_MAID,
                "Legacy compass migration changed plan or tool");
        var resavedCompass = (CompoundTag) loadedCompass.save(helper.getLevel().registryAccess());
        helper.assertTrue(resavedCompass.getCompound("components").getCompound(planKey).getInt("data_version")
                == WorkspaceDataVersions.PLAN_VERSION, "Legacy compass did not gain a version on save");
        loadedCompass.set(WorkspaceComponents.PLAN.get(), WorkspacePlan.empty(plan.dimension()));
        helper.assertTrue(progress.equals(WorkspaceLogic.state(restored)), "Compass migration shared mutable data with the maid");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void originalCompassKeepsItsScheduleInteractionsEvenWithLegacyData(GameTestHelper helper) {
        floor(helper);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        EntityMaid maid = maid(helper, owner);
        maid.setOwnerUUID(owner.getUUID());
        maid.setHomeModeEnable(false);
        ItemStack original = new ItemStack(InitItems.KAPPA_COMPASS.get());
        original.set(WorkspaceComponents.CUBOID_MODE.get(), true);
        original.set(WorkspaceComponents.PLAN.get(), plan(helper));
        // Include old component data in an actual save/load round trip before interacting.
        original = ItemStack.parseOptional(helper.getLevel().registryAccess(),
                (CompoundTag) original.save(helper.getLevel().registryAccess()));
        helper.assertTrue(!CompassEditor.isSmartCompass(original), "Legacy mode activated addon behavior on original compass");
        owner.setItemInHand(InteractionHand.MAIN_HAND, original);
        List<BlockPos> points = List.of(helper.absolutePos(new BlockPos(3, 2, 3)),
                helper.absolutePos(new BlockPos(9, 2, 9)), helper.absolutePos(new BlockPos(18, 2, 11)));
        for (BlockPos point : points) {
            original.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(point), Direction.UP, point, false)));
        }
        helper.assertTrue(ItemKappaCompass.getRecordCount(original) == 3, "Original compass failed to record three schedule points");
        helper.assertTrue(ItemKappaCompass.getPoint(Activity.WORK, original).equals(points.get(0)), "Original work point was hidden or changed");
        helper.assertTrue(ItemKappaCompass.getPoint(Activity.IDLE, original).equals(points.get(1)), "Original idle point changed");
        helper.assertTrue(ItemKappaCompass.getPoint(Activity.REST, original).equals(points.get(2)), "Original sleep point changed");
        maid.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(maid.getSchedulePos().getWorkPos().equals(points.get(0))
                && maid.getSchedulePos().getIdlePos().equals(points.get(1))
                && maid.getSchedulePos().getSleepPos().equals(points.get(2)), "Original compass failed to apply its schedule points");
        helper.assertTrue(WorkspaceLogic.state(maid) == null, "Original compass applied a legacy cuboid draft");
        helper.assertTrue(original.get(WorkspaceComponents.PLAN.get()).equals(plan(helper)), "Original interaction edited legacy draft data");
        owner.setShiftKeyDown(true);
        maid.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(!maid.getSchedulePos().isConfigured(), "Original crouch interaction failed to clear maid schedule");
        BlockPos point = points.get(0);
        original.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(point), Direction.UP, point, false)));
        helper.assertTrue(ItemKappaCompass.getRecordCount(original) == 0, "Original crouch interaction failed to clear recorded points");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void smartCompassRecipeProducesTheDedicatedItem(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(
                TouhouLittleMaidCustomWorkspace.MODID, "kappa_smart_compass")).orElseThrow();
        helper.assertTrue(holder.value() instanceof CraftingRecipe, "Smart compass recipe did not load as a crafting recipe");
        CraftingRecipe recipe = (CraftingRecipe) holder.value();
        CraftingInput input = CraftingInput.of(2, 2, List.of(new ItemStack(Items.COPPER_INGOT), ItemStack.EMPTY,
                new ItemStack(InitItems.KAPPA_COMPASS.get()), new ItemStack(Items.REDSTONE)));
        helper.assertTrue(recipe.matches(input, helper.getLevel()), "Smart compass ingredients did not match");
        ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(CompassEditor.isSmartCompass(result) && result.getCount() == 1,
                "Crafting did not produce one functional smart compass");
        helper.assertTrue(result.getMaxStackSize() == 1, "Smart compass allows stacked independent drafts");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void routeEditorConnectsWorkAreasUsingFeetForBlockAndAirClicks(GameTestHelper helper) {
        floor(helper);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack compass = new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, compass);
        compass.set(WorkspaceComponents.PLAN.get(), plan(helper));
        CompassEditor.setMode(compass, owner, CompassEditor.ROUTE_NODES);
        owner.moveTo(helper.absoluteVec(new Vec3(3.5, 2, 4.5)));
        owner.setXRot(90);
        compass.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(compass.get(WorkspaceComponents.ROUTE_ANCHOR.get()) == 0, "Work area was not selected as start node");
        owner.moveTo(helper.absoluteVec(new Vec3(9.5, 2, 4.5)));
        owner.setXRot(-90);
        BlockPos unrelatedBlock = helper.absolutePos(new BlockPos(20, 1, 13));
        compass.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(unrelatedBlock), Direction.UP, unrelatedBlock, false)));
        var updated = compass.get(WorkspaceComponents.PLAN.get());
        helper.assertTrue(updated.graph().points().equals(List.of(owner.blockPosition())), "Click recorded the block instead of player feet");
        helper.assertTrue(updated.graph().edges().contains(new RouteEdge(0, -1)), "New point was not connected to the start area");
        owner.moveTo(helper.absoluteVec(new Vec3(13.5, 2, 4.5)));
        owner.setXRot(90);
        compass.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        updated = compass.get(WorkspaceComponents.PLAN.get());
        helper.assertTrue(updated.graph().shortestPath(updated, 0, 1).equals(List.of(0, -1, 1)), "Could not finish a route at an existing work area");
        helper.assertTrue(updated.graph().shortestPath(updated, 1, 0).equals(List.of(1, -1, 0)), "Graph edges are not undirected");
        owner.setShiftKeyDown(true);
        compass.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(compass.get(WorkspaceComponents.ROUTE_ANCHOR.get()) == 1, "Crouch-click cleared the selected node");
        helper.assertTrue(compass.get(WorkspaceComponents.PLAN.get()).equals(updated), "Clicking the selected node changed the graph");
        owner.moveTo(helper.absoluteVec(new Vec3(9.5, 2, 11.5)));
        owner.setXRot(-90);
        compass.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        updated = compass.get(WorkspaceComponents.PLAN.get());
        helper.assertTrue(updated.graph().points().size() == 2 && updated.graph().points().get(1).equals(owner.blockPosition()),
                "Crouch-air click did not create a point at player feet");
        helper.assertTrue(updated.graph().edges().contains(new RouteEdge(1, -2))
                && compass.get(WorkspaceComponents.ROUTE_ANCHOR.get()) == -2, "Crouch-air click did not connect the new point");
        owner.moveTo(helper.absoluteVec(new Vec3(3.5, 2, 4.5)));
        owner.setXRot(90);
        compass.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(unrelatedBlock), Direction.UP, unrelatedBlock, false)));
        updated = compass.get(WorkspaceComponents.PLAN.get());
        helper.assertTrue(updated.graph().edges().contains(new RouteEdge(-2, 0))
                && compass.get(WorkspaceComponents.ROUTE_ANCHOR.get()) == 0, "Crouch-block click did not connect the existing node");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void appliedPlanSurvivesCompassEditsAndRemoval(GameTestHelper helper) {
        floor(helper);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        EntityMaid maid = maid(helper, owner);
        maid.setOwnerUUID(owner.getUUID());
        maid.setHomeModeEnable(false);
        var graph = new RouteGraph(List.of(helper.absolutePos(new BlockPos(9, 2, 4))), List.of())
                .connect(0, -1).connect(-1, 1);
        WorkspacePlan draft = plan(helper).withGraph(graph);
        ItemStack compass = new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get());
        compass.set(WorkspaceComponents.PLAN.get(), draft);
        compass.set(WorkspaceComponents.EDIT_MODE.get(), CompassEditor.ROUTE_NODES);
        compass.set(WorkspaceComponents.ROUTE_ANCHOR.get(), 0);
        owner.setItemInHand(InteractionHand.MAIN_HAND, compass);
        CompassEditor.setMode(compass, owner, CompassEditor.APPLY_TO_MAID);
        maid.mobInteract(owner, InteractionHand.MAIN_HAND);
        WorkspaceState applied = WorkspaceLogic.state(maid);
        helper.assertTrue(applied != null && applied.plan().equals(draft), "Maid interaction did not apply the complete graph");

        CompassEditor.setMode(compass, owner, CompassEditor.ROUTE_NODES);
        compass.set(WorkspaceComponents.ROUTE_ANCHOR.get(), 0);

        owner.moveTo(helper.absoluteVec(new Vec3(9.5, 2, 11.5)));
        owner.setXRot(-90);
        compass.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(compass.get(WorkspaceComponents.PLAN.get()).graph().points().size() == 2, "Compass edit did not add a node");
        helper.assertTrue(applied.equals(WorkspaceLogic.state(maid)), "Editing the compass changed the maid's applied plan");
        compass.set(WorkspaceComponents.PLAN.get(), compass.get(WorkspaceComponents.PLAN.get()).removeNode(-1, false));
        helper.assertTrue(applied.equals(WorkspaceLogic.state(maid)), "Deleting a draft node changed the maid's graph");
        CompassEditor.clearNodes(compass, owner);
        helper.assertTrue(applied.equals(WorkspaceLogic.state(maid)), "Clearing route points changed the maid's applied plan");
        CompassEditor.setMode(compass, owner, CompassEditor.WORK_AREAS);
        CompassEditor.clearNodes(compass, owner);
        helper.assertTrue(applied.equals(WorkspaceLogic.state(maid)), "Clearing work areas changed the maid's applied plan");
        compass.shrink(1);
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(applied.equals(WorkspaceLogic.state(maid)), "Destroying the compass changed the maid's applied plan");
        EntityMaid restored = new EntityMaid(helper.getLevel());
        restored.load(maid.saveWithoutId(new CompoundTag()));
        helper.assertTrue(applied.equals(WorkspaceLogic.state(restored)), "Maid save required the original compass to restore its plan");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void clearingNodesUsesTheSelectedToolAndKeepsOtherNodesEditable(GameTestHelper helper) {
        floor(helper);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack compass = new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, compass);
        var graph = new RouteGraph(List.of(helper.absolutePos(new BlockPos(9, 2, 4)),
                helper.absolutePos(new BlockPos(9, 2, 11))), List.of())
                .connect(0, -1).connect(-1, -2).connect(-2, 1).connect(0, 1);
        WorkspacePlan original = plan(helper).withGraph(graph);
        compass.set(WorkspaceComponents.PLAN.get(), original);
        compass.set(WorkspaceComponents.FIRST_CORNER.get(), area(helper, 3).min());
        compass.set(WorkspaceComponents.ROUTE_ANCHOR.get(), 0);
        CompassEditor.clearNodes(compass, owner);
        WorkspacePlan cleared = compass.get(WorkspaceComponents.PLAN.get());
        helper.assertTrue(cleared.areas().isEmpty() && cleared.graph().points().equals(graph.points()),
                "Work tool clear removed route points or retained work areas");
        helper.assertTrue(cleared.graph().edges().equals(List.of(new RouteEdge(-1, -2))), "Work clear changed surviving point connections");
        helper.assertTrue(!compass.has(WorkspaceComponents.FIRST_CORNER.get()) && !compass.has(WorkspaceComponents.ROUTE_ANCHOR.get()),
                "Bulk clear retained a stale selection");
        CompassEditor.setMode(compass, owner, CompassEditor.ROUTE_NODES);
        owner.moveTo(helper.absoluteVec(new Vec3(9.5, 2, 4.5)));
        owner.setXRot(90);
        compass.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        helper.assertTrue(compass.get(WorkspaceComponents.ROUTE_ANCHOR.get()) == -1, "Retained point could not be selected without work areas");
        owner.moveTo(helper.absoluteVec(new Vec3(9.5, 2, 13.5)));
        owner.setXRot(-90);
        compass.use(helper.getLevel(), owner, InteractionHand.MAIN_HAND);
        cleared = compass.get(WorkspaceComponents.PLAN.get());
        helper.assertTrue(cleared.graph().points().size() == 3 && cleared.graph().edges().contains(new RouteEdge(-1, -3)),
                "Retained point graph could not be extended without work areas");
        CompassEditor.setMode(compass, owner, CompassEditor.WORK_AREAS);
        for (BlockPos corner : List.of(area(helper, 3).min(), area(helper, 3).max())) {
            compass.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(corner), Direction.UP, corner, false)));
        }
        helper.assertTrue(compass.get(WorkspaceComponents.PLAN.get()).areas().size() == 1
                && compass.get(WorkspaceComponents.PLAN.get()).graph().equals(cleared.graph()), "Adding a new work area lost the retained point graph");
        compass.set(WorkspaceComponents.PLAN.get(), original);
        CompassEditor.setMode(compass, owner, CompassEditor.ROUTE_NODES);
        CompassEditor.clearNodes(compass, owner);
        cleared = compass.get(WorkspaceComponents.PLAN.get());
        helper.assertTrue(cleared.areas().equals(original.areas()) && cleared.graph().points().isEmpty(),
                "Route tool clear removed work areas or retained route points");
        helper.assertTrue(cleared.graph().edges().equals(List.of(new RouteEdge(0, 1))), "Route clear lost direct work-area connections");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void enteringDestinationEarlyDoesNotSkipRequiredNodes(GameTestHelper helper) {
        floor(helper);
        EntityMaid maid = maid(helper);
        BlockPos waypoint = helper.absolutePos(new BlockPos(9, 2, 11));
        var graph = new RouteGraph(List.of(waypoint), List.of()).connect(0, -1).connect(-1, 1);
        var state = WorkspaceState.initial(plan(helper).withGraph(graph)).withTravel(false).next();
        maid.setAndSyncData(LittleMaidCompat.WORKSPACES, state);
        maid.moveTo(helper.absoluteVec(new Vec3(13.5, 2, 4.5)));
        maid.getSchedulePos().restrictTo(maid);
        helper.runAtTickTime(3, () -> {
            var current = WorkspaceLogic.state(maid);
            helper.assertTrue(current.travelling() && current.workTicks() == 0, "Destination bypassed a mandatory waypoint");
            helper.assertTrue(WorkspaceController.navigationTarget(maid).equals(waypoint), "Return behavior skipped the waypoint");
            maid.moveTo(Vec3.atBottomCenterOf(waypoint));
        });
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(WorkspaceLogic.state(maid).remainingRoute().equals(List.of(1)), "Single-block waypoint was not advanced");
            maid.moveTo(helper.absoluteVec(new Vec3(13.5, 2, 4.5)));
        });
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(!WorkspaceLogic.state(maid).travelling(), "Destination did not start work after visiting its waypoint");
            helper.succeed();
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 500)
    public static void disconnectedGraphStillWalksDirectlyToNextWorkArea(GameTestHelper helper) {
        floor(helper);
        EntityMaid maid = maid(helper);
        var graph = new RouteGraph(List.of(helper.absolutePos(new BlockPos(9, 2, 11))), List.of()).connect(0, -1);
        maid.setAndSyncData(LittleMaidCompat.WORKSPACES,
                new WorkspaceState(plan(helper).withGraph(graph), 0, Config.WORK_TICKS.get() - 2L, false));
        maid.getSchedulePos().restrictTo(maid);
        helper.onEachTick(() -> {
            var state = WorkspaceLogic.state(maid);
            if (state.activeIndex() != 1) return;
            helper.assertTrue(state.remainingRoute().isEmpty(), "Disconnected graph supplied a route");
            helper.assertTrue(WorkspaceController.navigationTarget(maid).equals(state.activeArea().navigationCenter()), "Fallback did not target the destination center");
            if (!state.travelling()) {
                helper.assertTrue(state.activeArea().contains(maid.blockPosition()), "Direct fallback started work outside the destination");
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
        var graph = new RouteGraph(List.of(point), List.of()).connect(0, -1).connect(-1, 1);
        maid.setAndSyncData(LittleMaidCompat.WORKSPACES, new WorkspaceState(plan(helper).withGraph(graph), 1, 0, true, List.of(-1, 1)));
        helper.setDayTime(13000);
        maid.getSchedulePos().restrictTo(maid);
        helper.assertTrue(maid.getRestrictCenter().equals(maid.getSchedulePos().getIdlePos()), "Graph replaced the idle center");
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(WorkspaceLogic.state(maid).remainingRoute().equals(List.of(-1, 1)), "Idle discarded the unfinished route");
            helper.setDayTime(18000);
            maid.getSchedulePos().restrictTo(maid);
            helper.assertTrue(maid.getRestrictCenter().equals(maid.getSchedulePos().getSleepPos()), "Graph replaced the sleep center");
        });
        helper.runAtTickTime(24, () -> {
            helper.assertTrue(WorkspaceLogic.state(maid).remainingRoute().equals(List.of(-1, 1)), "Sleep discarded the unfinished route");
            helper.assertTrue(WorkspaceLogic.state(maid).workTicks() == 0, "Rest counted as route work");
            helper.setDayTime(0);
            maid.moveTo(Vec3.atBottomCenterOf(point));
            maid.getSchedulePos().restrictTo(maid);
        });
        helper.runAtTickTime(35, () -> {
            helper.assertTrue(WorkspaceLogic.state(maid).remainingRoute().equals(List.of(1)), "Work did not resume the saved route");
            helper.assertTrue(WorkspaceLogic.state(maid).workTicks() == 0, "Waypoint consumed working time");
            helper.succeed();
        });
    }

    @GameTest(template = "test_route", timeoutTicks = 2400)
    public static void followsMandatoryNodesAroundALongWall(GameTestHelper helper) { longWallRoute(helper, false); }

    @GameTest(template = "test_route", timeoutTicks = 2400)
    public static void followsTheSameUndirectedRouteInReverse(GameTestHelper helper) { longWallRoute(helper, true); }

    private static void longWallRoute(GameTestHelper helper, boolean reverse) {
        for (int x = 1; x <= 70; x++) for (int z = 1; z <= 18; z++) helper.setBlock(x, 1, z, Blocks.STONE);
        for (int y = 2; y <= 4; y++) {
            for (int x = 3; x <= 64; x++) {
                helper.setBlock(x, y, 3, Blocks.STONE);
                helper.setBlock(x, y, 9, Blocks.STONE);
            }
            for (int z = 3; z <= 15; z++) helper.setBlock(64, y, z, Blocks.STONE);
        }
        WorkArea a = WorkArea.between(helper.absolutePos(new BlockPos(57, 2, 5)), helper.absolutePos(new BlockPos(61, 3, 7)));
        WorkArea b = WorkArea.between(helper.absolutePos(new BlockPos(57, 2, 11)), helper.absolutePos(new BlockPos(61, 3, 14)));
        var points = List.of(helper.absolutePos(new BlockPos(34, 2, 6)), helper.absolutePos(new BlockPos(2, 2, 6)),
                helper.absolutePos(new BlockPos(2, 2, 12)), helper.absolutePos(new BlockPos(34, 2, 12)));
        var graph = new RouteGraph(points, List.of()).connect(0, -1).connect(-1, -2).connect(-2, -3).connect(-3, -4).connect(-4, 1);
        var plan = new WorkspacePlan(helper.getLevel().dimension().location(), List.of(a, b), graph);
        EntityMaid maid = maid(helper);
        int start = reverse ? 1 : 0, end = reverse ? 0 : 1;
        maid.moveTo(helper.absoluteVec(new Vec3(59.5, 2, reverse ? 12.5 : 6.5)));
        maid.setAndSyncData(LittleMaidCompat.WORKSPACES, new WorkspaceState(plan, end, 0, true));
        var direct = maid.getNavigation().createPath(plan.areas().get(end).navigationCenter(), 0);
        helper.assertTrue(direct == null || !direct.canReach(), "Long-wall setup unexpectedly allowed a full direct path");
        maid.setAndSyncData(LittleMaidCompat.WORKSPACES,
                new WorkspaceState(plan, start, Config.WORK_TICKS.get() - 2L, false));
        maid.getSchedulePos().restrictTo(maid);
        boolean[] visited = new boolean[points.size()];
        helper.onEachTick(() -> {
            for (int i = 0; i < points.size(); i++) if (points.get(i).equals(maid.blockPosition())) visited[i] = true;
            var state = WorkspaceLogic.state(maid);
            if (state.activeIndex() != end) return;
            if (state.travelling()) {
                helper.assertTrue(state.workTicks() == 0, "Intermediate route nodes counted as working time");
                maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).ifPresent(target -> {
                    helper.assertTrue(target.getTarget().currentBlockPosition().equals(state.navigationArea().navigationCenter()), "Periodic return overwrote the current route node");
                    helper.assertTrue(target.getCloseEnoughDist() == 0, "Route node uses an arrival tolerance");
                });
            } else {
                for (boolean point : visited) helper.assertTrue(point, "Maid skipped a mandatory single-block waypoint");
                helper.assertTrue(state.activeArea().contains(maid.blockPosition()), "Started working outside the final area");
                helper.succeed();
            }
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 200)
    public static void targetsCenterButStartsWorkAtTheFirstBoundaryCrossing(GameTestHelper helper) {
        floor(helper);
        WorkArea area = WorkArea.between(helper.absolutePos(new BlockPos(12, 1, 3)),
                helper.absolutePos(new BlockPos(21, 3, 10)));
        BlockPos center = BlockPos.containing(area.bounds().getCenter());
        EntityMaid maid = maid(helper);
        maid.moveTo(helper.absoluteVec(new Vec3(11.1, 2, 6.5)));
        WorkspaceController.apply(maid, new WorkspacePlan(helper.getLevel().dimension().location(), List.of(area)));
        helper.runAtTickTime(1, () -> {
            helper.assertTrue(WorkspaceLogic.state(maid).travelling(), "Near the box was treated as inside it");
            helper.assertTrue(WorkspaceLogic.state(maid).workTicks() == 0, "Travel consumed work ticks");
        });
        helper.onEachTick(() -> {
            var state = WorkspaceLogic.state(maid);
            helper.assertTrue(maid.getRestrictCenter().equals(center), "Return center is not the box center");
            if (state.travelling()) {
                helper.assertTrue(state.workTicks() == 0, "Travel consumed work ticks");
                maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).ifPresent(target -> {
                    helper.assertTrue(target.getTarget().currentBlockPosition().equals(center), "Walk target drifted from the center");
                    helper.assertTrue(target.getCloseEnoughDist() == 0, "TLM restored a distance tolerance");
                });
            } else {
                helper.assertTrue(area.contains(maid.blockPosition()), "Started working outside the box");
                helper.assertTrue(center.distManhattan(maid.blockPosition()) > 2, "Waited for the center before working");
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
        WorkArea area = WorkArea.between(helper.absolutePos(new BlockPos(12, 1, 5)),
                helper.absolutePos(new BlockPos(20, 3, 10)));
        EntityMaid maid = maid(helper);
        maid.moveTo(helper.absoluteVec(new Vec3(8.5, 2, 7.5)));
        WorkspaceController.apply(maid, new WorkspacePlan(helper.getLevel().dimension().location(), List.of(area)));
        boolean[] detoured = {false};
        helper.onEachTick(() -> {
            if (maid.getX() < helper.absolutePos(new BlockPos(5, 1, 3)).getX()) detoured[0] = true;
            var state = WorkspaceLogic.state(maid);
            if (state.travelling()) {
                helper.assertTrue(state.workTicks() == 0, "Travel counted as work behind the wall");
                maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).ifPresent(target -> {
                    helper.assertTrue(target.getTarget().currentBlockPosition().equals(area.navigationCenter()),
                            "Periodic return replaced the center target");
                    helper.assertTrue(target.getCloseEnoughDist() == 0, "Periodic return restored a distance tolerance");
                });
            } else {
                helper.assertTrue(detoured[0], "Reached the box without walking out of the concave wall");
                helper.assertTrue(area.contains(maid.blockPosition()), "Started work before entering");
                helper.assertTrue(area.navigationCenter().distManhattan(maid.blockPosition()) > 2,
                        "Continued to the center after entering");
                helper.succeed();
            }
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void floorOnlySelectionNeverCountsAsArrived(GameTestHelper helper) {
        floor(helper);
        WorkArea floorOnly = WorkArea.between(helper.absolutePos(new BlockPos(3, 1, 3)),
                helper.absolutePos(new BlockPos(6, 1, 6)));
        EntityMaid maid = maid(helper);
        WorkspaceController.apply(maid, new WorkspacePlan(helper.getLevel().dimension().location(), List.of(floorOnly)));
        helper.runAtTickTime(20, () -> {
            var state = WorkspaceLogic.state(maid);
            helper.assertTrue(state.travelling(), "Floor-only selection accepted the standing layer above it");
            helper.assertTrue(state.workTicks() == 0, "Invalid selection consumed work ticks");
            helper.assertTrue(!floorOnly.contains(maid.blockPosition()), "Maid entered the solid floor");
            helper.assertTrue(!maid.isWithinRestriction(), "Restriction accepted feet above the box");
            helper.succeed();
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 500)
    public static void rotatesFromUpperToAdjacentLowerAreaUsingStairs(GameTestHelper helper) {
        floor(helper);
        for (int x = 3; x <= 6; x++) for (int z = 3; z <= 6; z++) helper.setBlock(x, 4, z, Blocks.STONE);
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
        WorkArea lower = WorkArea.between(helper.absolutePos(new BlockPos(3, 1, 3)),
                helper.absolutePos(new BlockPos(6, 4, 6)));
        WorkArea upper = WorkArea.between(helper.absolutePos(new BlockPos(3, 5, 3)),
                helper.absolutePos(new BlockPos(6, 7, 6)));
        EntityMaid maid = maid(helper);
        maid.moveTo(helper.absoluteVec(new Vec3(4.5, 5, 4.5)));
        WorkspacePlan plan = new WorkspacePlan(helper.getLevel().dimension().location(), List.of(upper, lower));
        maid.setAndSyncData(LittleMaidCompat.WORKSPACES,
                new WorkspaceState(plan, 0, Config.WORK_TICKS.get() - 2L, false));
        maid.getSchedulePos().restrictTo(maid);
        helper.runAtTickTime(6, () -> {
            var state = WorkspaceLogic.state(maid);
            helper.assertTrue(state.activeIndex() == 1 && state.travelling(), "Upper floor was counted as arrival in lower area");
            helper.assertTrue(state.workTicks() == 0, "Transfer consumed work ticks");
            helper.assertTrue(!lower.contains(maid.blockPosition()), "Maid unexpectedly reached lower area already");
        });
        boolean[] usedStairs = {false};
        helper.onEachTick(() -> {
            if (maid.getX() >= helper.absolutePos(new BlockPos(7, 1, 4)).getX()) usedStairs[0] = true;
            var state = WorkspaceLogic.state(maid);
            if (state.activeIndex() == 1 && !state.travelling()) {
                helper.assertTrue(usedStairs[0], "Transfer bypassed the staircase");
                helper.assertTrue(lower.contains(maid.blockPosition()), "Started work outside the lower box");
                helper.assertTrue(maid.blockPosition().getY() == helper.absolutePos(new BlockPos(0, 2, 0)).getY(),
                        "Started work before reaching the lower floor");
                helper.succeed();
            }
        });
    }
}
