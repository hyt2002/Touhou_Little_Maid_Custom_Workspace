package com.erobrine.tlmcw.mixin;

import com.erobrine.tlmcw.workspace.WorkspaceController;
import com.erobrine.tlmcw.workspace.WorkspaceLogic;
import com.erobrine.tlmcw.workspace.WorkspaceState;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.SchedulePos;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SchedulePos.class, remap = false)
public abstract class SchedulePosMixin {
    @Inject(method = "restrictTo", at = @At("HEAD"), cancellable = true)
    private void tlmcw$currentArea(EntityMaid maid, CallbackInfo ci) {
        WorkspaceController.prepareSchedule(maid);
        WorkspaceState state = WorkspaceLogic.active(maid);
        if (state != null) {
            maid.restrictTo(
                    WorkspaceController.navigationTarget(maid),
                    state.navigationArea().horizontalRadius());
            ci.cancel();
        }
    }

    @Redirect(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/SchedulePos;teleport(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)V"))
    private void tlmcw$routeReturn(SchedulePos schedule, EntityMaid maid) {
        WorkspaceState state = WorkspaceLogic.active(maid);
        if (WorkspaceLogic.waitingForDimension(maid)) return;
        if (state != null && !state.remainingRoute().isEmpty()) {
            BehaviorUtils.setWalkAndLookTargetMemories(
                    maid, WorkspaceController.navigationTarget(maid), 0.7f, 0);
        } else {
            teleport(maid);
        }
    }

    @Shadow
    private void teleport(EntityMaid maid) {
        throw new AssertionError();
    }

    @Redirect(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/ai/behavior/BehaviorUtils;setWalkAndLookTargetMemories(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/core/BlockPos;FI)V"))
    private void tlmcw$exactReturn(LivingEntity entity, BlockPos pos, float speed, int tolerance) {
        if (entity instanceof EntityMaid waiting && WorkspaceLogic.waitingForDimension(waiting))
            return;
        if (entity instanceof EntityMaid maid && WorkspaceLogic.active(maid) != null) {
            BehaviorUtils.setWalkAndLookTargetMemories(
                    maid, WorkspaceController.navigationTarget(maid), speed, 0);
        } else BehaviorUtils.setWalkAndLookTargetMemories(entity, pos, speed, tolerance);
    }
}
