package com.erobrine.tlmcw.mixin;

import com.erobrine.tlmcw.workspace.WorkspaceController;
import com.erobrine.tlmcw.workspace.WorkspaceLogic;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidUpdateActivityFromSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MaidUpdateActivityFromSchedule.class, remap = false)
public abstract class MaidUpdateActivityFromScheduleMixin {
    @Redirect(method = "start", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/behavior/BehaviorUtils;setWalkAndLookTargetMemories(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/core/BlockPos;FI)V"))
    private void tlmcw$exactWorkReturn(LivingEntity entity, BlockPos pos, float speed, int tolerance) {
        if (entity instanceof EntityMaid maid && WorkspaceLogic.active(maid) != null) {
            BehaviorUtils.setWalkAndLookTargetMemories(maid, WorkspaceController.navigationTarget(maid), speed, 0);
        } else BehaviorUtils.setWalkAndLookTargetMemories(entity, pos, speed, tolerance);
    }
}
