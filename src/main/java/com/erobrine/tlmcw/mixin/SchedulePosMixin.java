package com.erobrine.tlmcw.mixin;

import com.erobrine.tlmcw.workspace.WorkspaceController;
import com.erobrine.tlmcw.workspace.WorkspaceLogic;
import com.erobrine.tlmcw.workspace.WorkspaceState;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.SchedulePos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void tlmcw$useWorkspaceReturn(EntityMaid maid, CallbackInfo ci) {
        // The controller owns custom returns, including during a group's task movement.
        if (WorkspaceLogic.active(maid) != null) ci.cancel();
    }
}
