package com.erobrine.tlmcw.mixin;

import com.erobrine.tlmcw.workspace.WorkspaceNavigation;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import java.util.Optional;

@Mixin(MoveToTargetSink.class)
public abstract class MoveToTargetSinkMixin {
    @Unique private WalkTarget tlmcw$segmentIntent;
    @Unique private BlockPos tlmcw$segment;
    @Unique private WalkTarget tlmcw$segmentTarget;
    @Unique private boolean tlmcw$retainIntent;

    @Inject(
            method =
                    "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;)Z",
            at = @At("HEAD"))
    private void tlmcw$selectSegment(
            ServerLevel level, Mob mob, CallbackInfoReturnable<Boolean> ci) {
        WalkTarget intent = mob.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        tlmcw$segment =
                mob instanceof EntityMaid maid
                        ? WorkspaceNavigation.intermediateTarget(maid, intent)
                        : null;
        tlmcw$segmentIntent = tlmcw$segment == null ? null : intent;
        tlmcw$segmentTarget =
                tlmcw$segment == null
                        ? null
                        : new WalkTarget(tlmcw$segment, intent.getSpeedModifier(), 0);
        tlmcw$retainIntent = mob instanceof EntityMaid maid && tlmcw$preserveIntent(maid, intent);
    }

    @Inject(
            method =
                    "stop(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;J)V",
            at = @At("HEAD"))
    private void tlmcw$finishSegment(ServerLevel level, Mob mob, long time, CallbackInfo ci) {
        if (!(mob instanceof EntityMaid maid)) return;
        WalkTarget intent = mob.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        tlmcw$retainIntent = tlmcw$preserveIntent(maid, intent);
    }

    @Unique
    private boolean tlmcw$preserveIntent(EntityMaid maid, WalkTarget intent) {
        if (intent == null || WorkspaceNavigation.isGroupRouteStalled(maid, intent)) return false;
        return tlmcw$segment != null
                || WorkspaceNavigation.usesGroupRoute(maid, intent)
                        && intent.getTarget()
                                        .currentBlockPosition()
                                        .distManhattan(maid.blockPosition())
                                > intent.getCloseEnoughDist();
    }

    @Inject(
            method =
                    "canStillUse(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;J)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void tlmcw$advanceSegment(
            ServerLevel level, Mob mob, long time, CallbackInfoReturnable<Boolean> ci) {
        if (!(mob instanceof EntityMaid maid)) return;
        var intent = mob.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        BlockPos next = WorkspaceNavigation.intermediateTarget(maid, intent);
        // Vanilla's moving-target threshold can otherwise ignore adjacent waypoints.
        if (WorkspaceNavigation.isGroupRouteStalled(maid, intent)
                || !Objects.equals(tlmcw$segment, next)
                || tlmcw$segment != null && tlmcw$segmentIntent != intent) {
            ci.setReturnValue(false);
        }
    }

    @Redirect(
            method = {
                "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;)Z",
                "canStillUse(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;J)Z",
                "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;J)V",
                "stop(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;J)V"
            },
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/ai/Brain;getMemory(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;)Ljava/util/Optional;"))
    private Optional<?> tlmcw$readSegment(Brain<?> brain, MemoryModuleType<?> memory) {
        var value = brain.getMemory(memory);
        if (memory == MemoryModuleType.WALK_TARGET
                && tlmcw$segment != null
                && value.orElse(null) == tlmcw$segmentIntent) {
            return Optional.of(tlmcw$segmentTarget);
        }
        return value;
    }

    @Redirect(
            method = {
                "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;)Z",
                "stop(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;J)V"
            },
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/ai/Brain;eraseMemory(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;)V"))
    private void tlmcw$keepFinalTarget(Brain<?> brain, MemoryModuleType<?> memory) {
        if (memory == MemoryModuleType.WALK_TARGET && tlmcw$retainIntent) return;
        brain.eraseMemory(memory);
    }
}
