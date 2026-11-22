package com.erobrine.tlmcw.mixin;

import com.erobrine.tlmcw.workspace.WorkspaceNavigation;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidAwaitTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import net.minecraft.server.level.ServerLevel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MaidAwaitTask.class, remap = false)
public abstract class MaidAwaitTaskMixin {
    @Inject(
            method =
                    "start(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;J)V",
            at = @At("HEAD"),
            cancellable = true)
    private void tlmcw$allowTravel(ServerLevel level, EntityMaid maid, long time, CallbackInfo ci) {
        if (WorkspaceNavigation.allowsIntent(maid)) ci.cancel();
    }
}
