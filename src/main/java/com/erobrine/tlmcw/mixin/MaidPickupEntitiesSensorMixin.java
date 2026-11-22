package com.erobrine.tlmcw.mixin;

import com.erobrine.tlmcw.workspace.WorkspaceLogic;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.sensor.MaidPickupEntitiesSensor;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.List;

@Mixin(value = MaidPickupEntitiesSensor.class, remap = false)
public abstract class MaidPickupEntitiesSensorMixin {
    @Inject(
            method =
                    "doTick(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void tlmcw$pickupBox(ServerLevel level, EntityMaid maid, CallbackInfo ci) {
        var state = WorkspaceLogic.active(maid);
        if (state == null) return;
        ci.cancel();
        if (!WorkspaceLogic.mayWork(maid)) {
            maid.getBrain().setMemory(InitEntities.VISIBLE_PICKUP_ENTITIES.get(), List.of());
            return;
        }
        var found = new java.util.LinkedHashSet<Entity>();
        for (var node : WorkspaceLogic.localAreas(maid, state)) {
            var box = node.bounds();
            found.addAll(
                    level.getEntitiesOfClass(
                            Entity.class,
                            box.bounds(),
                            e ->
                                    e.isAlive()
                                            && box.contains(e.blockPosition())
                                            && maid.canPickup(e, true)
                                            && maid.hasLineOfSight(e)));
        }
        var entities = new java.util.ArrayList<>(found);
        entities.sort(Comparator.comparingDouble(maid::distanceToSqr));
        maid.getBrain().setMemory(InitEntities.VISIBLE_PICKUP_ENTITIES.get(), entities);
    }
}
