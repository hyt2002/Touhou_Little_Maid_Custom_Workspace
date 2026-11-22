package com.erobrine.tlmcw.mixin;

import com.erobrine.tlmcw.Config;
import com.erobrine.tlmcw.workspace.WorkArea;
import com.erobrine.tlmcw.workspace.WorkspaceLogic;
import com.erobrine.tlmcw.workspace.WorkspaceNode;
import com.erobrine.tlmcw.workspace.WorkspaceState;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidPathFindingBFS;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(value = MaidMoveToBlockTask.class, remap = false)
public abstract class MaidMoveToBlockTaskMixin extends MaidCheckRateTask {
    @Shadow @Final private float movementSpeed;
    @Shadow private BlockPos currentWorkPos;

    @Shadow
    protected abstract boolean shouldMoveTo(ServerLevel level, EntityMaid maid, BlockPos pos);

    @Shadow
    protected abstract MaidPathFindingBFS getOrCreateArrivalMap(ServerLevel level, EntityMaid maid);

    @Shadow
    protected abstract boolean checkPathReach(
            EntityMaid maid, MaidPathFindingBFS map, BlockPos pos);

    @Shadow
    protected abstract void clearCurrentArrivalMap(MaidPathFindingBFS map);

    @Unique private List<WorkspaceNode> tlmcw$areas = List.of();
    @Unique private java.util.UUID tlmcw$index;
    @Unique private long tlmcw$cursor;

    protected MaidMoveToBlockTaskMixin() {
        super(Map.of());
    }

    @Inject(method = "getOrCreateArrivalMap", at = @At("HEAD"), cancellable = true)
    private void tlmcw$localMap(
            ServerLevel level, EntityMaid maid, CallbackInfoReturnable<MaidPathFindingBFS> cir) {
        WorkspaceState state = WorkspaceLogic.active(maid);
        if (state != null) {
            WorkArea area = WorkspaceLogic.localAreaAtFeet(maid, state);
            cir.setReturnValue(
                    new MaidPathFindingBFS(
                            maid.getNavigation().getNodeEvaluator(),
                            level,
                            maid,
                            area.center(),
                            area.horizontalRadius(),
                            area.verticalRadius()));
        }
    }

    @Inject(method = "searchForDestination", at = @At("HEAD"), cancellable = true)
    private void tlmcw$searchBox(ServerLevel level, EntityMaid maid, CallbackInfo ci) {
        WorkspaceState state = WorkspaceLogic.active(maid);
        if (state == null) return;
        ci.cancel();
        if (!WorkspaceLogic.mayWork(maid)) return;
        var areas = WorkspaceLogic.localAreas(maid, state);
        if (areas.isEmpty()) return;
        if (!areas.equals(tlmcw$areas)
                || !java.util.Objects.equals(tlmcw$index, state.destinationId())) {
            tlmcw$areas = areas;
            tlmcw$index = state.destinationId();
            tlmcw$cursor = 0;
            currentWorkPos = null;
        }
        var boxes = areas.stream().map(WorkspaceNode::bounds).toList();
        long volume = boxes.stream().mapToLong(WorkArea::volume).sum();
        MaidPathFindingBFS map = null;
        try {
            int budget = (int) Math.min(volume, Config.SEARCH_BUDGET.get());
            boolean localChunksLoaded = true;
            for (int checked = 0; checked < budget; checked++) {
                long offset = tlmcw$cursor;
                tlmcw$cursor = (tlmcw$cursor + 1) % volume;
                WorkArea area = boxes.getFirst();
                for (WorkArea box : boxes) {
                    area = box;
                    if (offset < box.volume()) break;
                    offset -= box.volume();
                }
                BlockPos pos = area.candidate(offset);
                if (!level.hasChunkAt(pos) || !shouldMoveTo(level, maid, pos)) continue;
                if (area.contains(maid.blockPosition())) {
                    // This BFS starts at the maid. Remote boxes use segmented navigation.
                    if (map == null && localChunksLoaded) {
                        localChunksLoaded = WorkspaceLogic.searchChunksLoaded(level, area);
                        if (localChunksLoaded) map = getOrCreateArrivalMap(level, maid);
                    }
                    if (map == null || !checkPathReach(maid, map, pos)) continue;
                }
                BehaviorUtils.setWalkAndLookTargetMemories(maid, pos, movementSpeed, 0);
                maid.getBrain().setMemory(InitEntities.TARGET_POS.get(), new BlockPosTracker(pos));
                currentWorkPos = pos;
                setNextCheckTickCount(5);
                return;
            }
            currentWorkPos = null;
            setNextCheckTickCount(5);
        } finally {
            if (map != null) clearCurrentArrivalMap(map);
        }
    }
}
