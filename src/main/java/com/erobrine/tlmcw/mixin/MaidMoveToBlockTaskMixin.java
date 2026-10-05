package com.erobrine.tlmcw.mixin;

import com.erobrine.tlmcw.Config;
import com.erobrine.tlmcw.workspace.WorkArea;
import com.erobrine.tlmcw.workspace.WorkspaceLogic;
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
import java.util.Map;

@Mixin(value = MaidMoveToBlockTask.class, remap = false)
public abstract class MaidMoveToBlockTaskMixin extends MaidCheckRateTask {
    @Shadow @Final private float movementSpeed;
    @Shadow private BlockPos currentWorkPos;
    @Shadow protected abstract boolean shouldMoveTo(ServerLevel level, EntityMaid maid, BlockPos pos);
    @Shadow protected abstract MaidPathFindingBFS getOrCreateArrivalMap(ServerLevel level, EntityMaid maid);
    @Shadow protected abstract boolean checkPathReach(EntityMaid maid, MaidPathFindingBFS map, BlockPos pos);
    @Shadow protected abstract void clearCurrentArrivalMap(MaidPathFindingBFS map);
    @Unique private WorkArea tlmcw$area;
    @Unique private int tlmcw$index = -1;
    @Unique private long tlmcw$cursor;

    protected MaidMoveToBlockTaskMixin() { super(Map.of()); }

    @Inject(method = "getOrCreateArrivalMap", at = @At("HEAD"), cancellable = true)
    private void tlmcw$localMap(ServerLevel level, EntityMaid maid, CallbackInfoReturnable<MaidPathFindingBFS> cir) {
        WorkspaceState state = WorkspaceLogic.active(maid);
        if (state != null) {
            WorkArea area = state.activeArea();
            cir.setReturnValue(new MaidPathFindingBFS(maid.getNavigation().getNodeEvaluator(), level, maid,
                    area.center(), area.horizontalRadius(), area.verticalRadius()));
        }
    }

    @Inject(method = "searchForDestination", at = @At("HEAD"), cancellable = true)
    private void tlmcw$searchBox(ServerLevel level, EntityMaid maid, CallbackInfo ci) {
        WorkspaceState state = WorkspaceLogic.active(maid);
        if (state == null) return;
        ci.cancel();
        if (!WorkspaceLogic.mayWork(maid)) return;
        WorkArea area = state.activeArea();
        if (!area.equals(tlmcw$area) || tlmcw$index != state.activeIndex()) {
            tlmcw$area = area;
            tlmcw$index = state.activeIndex();
            tlmcw$cursor = 0;
            currentWorkPos = null;
        }
        // PathNavigationRegion caches whole chunks; do not instantiate it for unloaded chunks.
        if (!WorkspaceLogic.searchChunksLoaded(level, area)) return;
        MaidPathFindingBFS map = getOrCreateArrivalMap(level, maid);
        try {
            int budget = (int) Math.min(area.volume(), Config.SEARCH_BUDGET.get());
            for (int checked = 0; checked < budget; checked++) {
                BlockPos pos = area.candidate(tlmcw$cursor);
                tlmcw$cursor = (tlmcw$cursor + 1) % area.volume();
                if (!level.hasChunkAt(pos) || !shouldMoveTo(level, maid, pos) || !checkPathReach(maid, map, pos)) continue;
                BehaviorUtils.setWalkAndLookTargetMemories(maid, pos, movementSpeed, 0);
                maid.getBrain().setMemory(InitEntities.TARGET_POS.get(), new BlockPosTracker(pos));
                currentWorkPos = pos;
                setNextCheckTickCount(5);
                return;
            }
            currentWorkPos = null;
            setNextCheckTickCount(5);
        } finally {
            clearCurrentArrivalMap(map);
        }
    }
}
