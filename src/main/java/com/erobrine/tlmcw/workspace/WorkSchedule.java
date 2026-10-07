package com.erobrine.tlmcw.workspace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.*;

public record WorkSchedule(List<WorkScheduleEntry> entries, boolean cyclic) {
    public static final int MAX_ENTRIES = 128;
    public static final WorkSchedule EMPTY = new WorkSchedule(List.of(), true);
    public static final Codec<WorkSchedule> CODEC =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            WorkScheduleEntry.CODEC
                                                    .listOf(0, MAX_ENTRIES)
                                                    .fieldOf("entries")
                                                    .forGetter(WorkSchedule::entries),
                                            Codec.BOOL
                                                    .optionalFieldOf("cyclic", true)
                                                    .forGetter(WorkSchedule::cyclic))
                                    .apply(i, WorkSchedule::new));

    public WorkSchedule {
        entries = List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES
                || entries.stream().map(WorkScheduleEntry::id).distinct().count() != entries.size())
            throw new IllegalArgumentException("Invalid schedule identities");
    }

    public boolean custom() {
        return !entries.isEmpty();
    }

    public boolean validFor(WorkspacePlan plan) {
        return entries.stream()
                .allMatch(
                        e ->
                                plan.node(e.destination()) != null
                                        && plan.type(e.destination()) == AreaType.WORK);
    }

    public WorkSchedule removeDestination(UUID id) {
        return new WorkSchedule(
                entries.stream().filter(e -> !e.destination().equals(id)).toList(), cyclic);
    }

    public WorkScheduleEntry entry(UUID id) {
        return entries.stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
    }

    public int position(UUID id) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).id().equals(id)) return i;
        return -1;
    }
}
