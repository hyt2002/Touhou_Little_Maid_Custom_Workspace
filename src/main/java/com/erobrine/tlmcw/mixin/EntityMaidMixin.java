package com.erobrine.tlmcw.mixin;

import com.erobrine.tlmcw.workspace.WorkspaceLogic;
import com.erobrine.tlmcw.workspace.WorkspaceState;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityMaid.class, remap = false)
public abstract class EntityMaidMixin {
    @Inject(method = "isWithinRestriction(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void tlmcw$blockBounds(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        WorkspaceState state = WorkspaceLogic.active((EntityMaid) (Object) this);
        if (state != null) cir.setReturnValue(state.navigationArea().contains(pos));
    }
    @Inject(method = "isWithinRestriction()Z", at = @At("HEAD"), cancellable = true)
    private void tlmcw$feetBounds(CallbackInfoReturnable<Boolean> cir) {
        EntityMaid maid = (EntityMaid) (Object) this;
        WorkspaceState state = WorkspaceLogic.active(maid);
        if (state != null) cir.setReturnValue(state.navigationArea().contains(maid.blockPosition()));
    }
    @Inject(method = "searchDimension", at = @At("HEAD"), cancellable = true)
    private void tlmcw$entitySearch(CallbackInfoReturnable<AABB> cir) {
        WorkspaceState state = WorkspaceLogic.active((EntityMaid) (Object) this);
        if (state != null) cir.setReturnValue(state.activeArea().bounds());
    }
}
