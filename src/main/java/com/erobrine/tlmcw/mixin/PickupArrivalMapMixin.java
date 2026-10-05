package com.erobrine.tlmcw.mixin;

import com.erobrine.tlmcw.workspace.WorkspaceLogic;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidPickupEntitiesTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidPathFindingBFS;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MaidPickupEntitiesTask.class, remap = false)
public abstract class PickupArrivalMapMixin {
    @Inject(method = "start(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;J)V",
            at = @At("HEAD"), cancellable = true)
    private void tlmcw$loadedMap(ServerLevel level, EntityMaid maid, long time, CallbackInfo ci) {
        var state = WorkspaceLogic.active(maid);
        if (state != null && !WorkspaceLogic.searchChunksLoaded(level, state.activeArea())) ci.cancel();
    }
    @Redirect(method = "start", at = @At(value = "NEW",
            target = "com/github/tartaricacid/touhoulittlemaid/entity/passive/MaidPathFindingBFS"))
    private MaidPathFindingBFS tlmcw$pickupMap(NodeEvaluator evaluator, ServerLevel level, EntityMaid maid) {
        var state = WorkspaceLogic.active(maid);
        if (state == null) return new MaidPathFindingBFS(evaluator, level, maid);
        var area = state.activeArea();
        return new MaidPathFindingBFS(evaluator, level, maid, area.center(), area.horizontalRadius(), area.verticalRadius());
    }
}
