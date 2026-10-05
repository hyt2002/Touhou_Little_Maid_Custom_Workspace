package com.erobrine.tlmcw.workspace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Travel to this work-area node, then wait for its departure condition before the next entry. */
public record WorkScheduleEntry(int area, DelayCondition condition) {
    public static final Codec<WorkScheduleEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 63).fieldOf("area").forGetter(WorkScheduleEntry::area),
            DelayCondition.CODEC.fieldOf("condition").forGetter(WorkScheduleEntry::condition)
    ).apply(instance, WorkScheduleEntry::new));
    public WorkScheduleEntry {
        if (area < 0 || area >= 64 || condition == null) throw new IllegalArgumentException("Invalid work schedule entry");
    }
}
