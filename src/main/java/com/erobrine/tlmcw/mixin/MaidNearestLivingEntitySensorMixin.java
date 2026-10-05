package com.erobrine.tlmcw.mixin;

import com.erobrine.tlmcw.workspace.WorkspaceLogic;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.sensor.MaidNearestLivingEntitySensor;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Comparator;

@Mixin(value = MaidNearestLivingEntitySensor.class, remap = false)
public abstract class MaidNearestLivingEntitySensorMixin {
    @Inject(method = "doTick(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)V",
            at = @At("HEAD"), cancellable = true)
    private void tlmcw$currentEntities(ServerLevel level, EntityMaid maid, CallbackInfo ci) {
        var state = WorkspaceLogic.active(maid);
        if (state == null) return;
        ci.cancel();
        var entities = level.getEntitiesOfClass(LivingEntity.class, state.activeArea().bounds(),
                e -> e != maid && e.isAlive() && state.activeArea().contains(e.blockPosition()));
        entities.sort(Comparator.comparingDouble(maid::distanceToSqr));
        maid.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, entities);
        maid.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, new NearestVisibleLivingEntities(maid, entities));
    }
}
