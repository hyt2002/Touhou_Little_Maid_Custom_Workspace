package com.erobrine.tlmcw.workspace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

import java.util.UUID;

public record WorkScheduleEntry(UUID id, UUID destination, DelayCondition condition) {
    public static final Codec<WorkScheduleEntry> CODEC =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            UUIDUtil.CODEC
                                                    .fieldOf("id")
                                                    .forGetter(WorkScheduleEntry::id),
                                            UUIDUtil.CODEC
                                                    .fieldOf("destination")
                                                    .forGetter(WorkScheduleEntry::destination),
                                            DelayCondition.CODEC
                                                    .fieldOf("condition")
                                                    .forGetter(WorkScheduleEntry::condition))
                                    .apply(i, WorkScheduleEntry::new));

    public WorkScheduleEntry {
        java.util.Objects.requireNonNull(id);
        java.util.Objects.requireNonNull(destination);
        java.util.Objects.requireNonNull(condition);
    }

    public WorkScheduleEntry(UUID destination, DelayCondition condition) {
        this(UUID.randomUUID(), destination, condition);
    }

    public WorkScheduleEntry withDestination(UUID value) {
        return new WorkScheduleEntry(id, value, condition);
    }

    public WorkScheduleEntry withCondition(DelayCondition value) {
        return new WorkScheduleEntry(id, destination, value);
    }

    public WorkScheduleEntry duplicate() {
        return new WorkScheduleEntry(destination, condition);
    }
}
