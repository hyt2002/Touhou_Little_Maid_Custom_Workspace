package com.erobrine.tlmcw.mixin;

import com.erobrine.tlmcw.Config;
import com.erobrine.tlmcw.workspace.WorkspaceController;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityMaid.class, remap = false)
public abstract class MaidDimensionChangeMixin extends TamableAnimal {
    protected MaidDimensionChangeMixin(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    @Override
    public int getDimensionChangingDelay() {
        // Vanilla Player.getDimensionChangingDelay uses 10 game ticks.
        return Config.MAID_DIMENSION_COMPATIBILITY.get() ? 10 : super.getDimensionChangingDelay();
    }

    /** Delegate to vanilla, including entity recreation, NBT copying and NeoForge travel events. */
    @Inject(
            method =
                    "changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;",
            at = @At("HEAD"),
            cancellable = true)
    private void tlmcw$vanillaDimensionChange(
            DimensionTransition transition, CallbackInfoReturnable<Entity> cir) {
        if (!Config.MAID_DIMENSION_COMPATIBILITY.get()) return;
        var previousDimension = level().dimension();
        Entity transferred = super.changeDimension(transition);
        if (transferred instanceof EntityMaid maid
                && !previousDimension.equals(maid.level().dimension()))
            WorkspaceController.onNavigationContextChange(maid);
        cir.setReturnValue(transferred);
    }
}
