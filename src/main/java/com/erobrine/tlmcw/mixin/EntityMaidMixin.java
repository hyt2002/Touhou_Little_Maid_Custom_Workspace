package com.erobrine.tlmcw.mixin;

import com.erobrine.tlmcw.workspace.WorkspaceController;
import com.erobrine.tlmcw.workspace.WorkspaceLogic;
import com.erobrine.tlmcw.workspace.WorkspaceState;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityMaid.class, remap = false)
public abstract class EntityMaidMixin {
    @Inject(method = "setHomeModeEnable", at = @At("HEAD"))
    private void tlmcw$homeModeChange(boolean enable, CallbackInfo ci) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (maid.isHomeModeEnable() != enable) WorkspaceController.onNavigationContextChange(maid);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
    private void tlmcw$beginDataLoad(CompoundTag tag, CallbackInfo ci) {
        WorkspaceController.beginDataLoad((EntityMaid) (Object) this);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void tlmcw$endDataLoad(CompoundTag tag, CallbackInfo ci) {
        WorkspaceController.endDataLoad((EntityMaid) (Object) this);
    }

    @Inject(
            method = "isWithinRestriction(Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void tlmcw$blockBounds(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        WorkspaceState state = WorkspaceLogic.active((EntityMaid) (Object) this);
        if (state != null)
            cir.setReturnValue(
                    !WorkspaceLogic.waitingForDimension((EntityMaid) (Object) this)
                            && state.navigationArea().contains(pos));
    }

    @Inject(method = "isWithinRestriction()Z", at = @At("HEAD"), cancellable = true)
    private void tlmcw$feetBounds(CallbackInfoReturnable<Boolean> cir) {
        EntityMaid maid = (EntityMaid) (Object) this;
        WorkspaceState state = WorkspaceLogic.active(maid);
        if (state != null)
            cir.setReturnValue(
                    WorkspaceLogic.waitingForDimension(maid)
                            || state.navigationArea().contains(maid.blockPosition()));
    }

    @Inject(method = "searchDimension", at = @At("HEAD"), cancellable = true)
    private void tlmcw$entitySearch(CallbackInfoReturnable<AABB> cir) {
        WorkspaceState state = WorkspaceLogic.active((EntityMaid) (Object) this);
        if (state != null) {
            EntityMaid maid = (EntityMaid) (Object) this;
            cir.setReturnValue(
                    WorkspaceLogic.waitingForDimension(maid)
                            ? new AABB(maid.position(), maid.position())
                            : state.activeArea().bounds());
        }
    }
}
