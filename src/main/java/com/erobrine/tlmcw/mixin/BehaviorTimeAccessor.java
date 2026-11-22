package com.erobrine.tlmcw.mixin;

import net.minecraft.world.entity.ai.behavior.Behavior;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Behavior.class)
public interface BehaviorTimeAccessor {
    @Accessor("endTimestamp")
    long tlmcw$getEndTimestamp();

    @Accessor("endTimestamp")
    void tlmcw$setEndTimestamp(long time);
}
