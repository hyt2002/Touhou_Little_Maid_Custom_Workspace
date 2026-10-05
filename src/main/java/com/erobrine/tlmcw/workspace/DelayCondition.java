package com.erobrine.tlmcw.workspace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** The first supported departure condition; duration always uses active working game ticks. */
public record DelayCondition(int ticks) {
    public static final int MAX_TICKS = 1_728_000;
    public static final Codec<DelayCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.validate(type -> type.equals("delay") ? DataResult.success(type)
                    : DataResult.error(() -> "Unsupported departure condition: " + type)).fieldOf("type").forGetter(condition -> "delay"),
            Codec.intRange(0, MAX_TICKS).fieldOf("ticks").forGetter(DelayCondition::ticks)
    ).apply(instance, (type, ticks) -> new DelayCondition(ticks)));
    public DelayCondition {
        if (ticks < 0 || ticks > MAX_TICKS) throw new IllegalArgumentException("Invalid working delay");
    }
}
