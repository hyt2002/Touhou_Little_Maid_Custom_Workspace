package com.erobrine.tlmcw.workspace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

public record WorkSchedule(List<WorkScheduleEntry> entries, boolean cyclic) {
    public static final int MAX_ENTRIES = 128;
    public static final WorkSchedule EMPTY = new WorkSchedule(List.of(), true);
    public static final Codec<WorkSchedule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            WorkScheduleEntry.CODEC.listOf(0, MAX_ENTRIES).fieldOf("entries").forGetter(WorkSchedule::entries),
            Codec.BOOL.optionalFieldOf("cyclic", true).forGetter(WorkSchedule::cyclic)
    ).apply(instance, WorkSchedule::new));
    public WorkSchedule {
        entries = List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many schedule entries");
    }
    public boolean custom() { return !entries.isEmpty(); }
    public boolean validFor(WorkspacePlan plan) {
        return entries.stream().allMatch(entry -> entry.area() < plan.areas().size() && plan.type(entry.area()) == AreaType.WORK);
    }
    public WorkSchedule retainWorkAreas(List<AreaType> types) {
        return new WorkSchedule(entries.stream().filter(entry -> entry.area() < types.size() && types.get(entry.area()) == AreaType.WORK).toList(), cyclic);
    }
    public WorkSchedule removeArea(int node) {
        return new WorkSchedule(entries.stream().filter(entry -> entry.area() != node)
                .map(entry -> new WorkScheduleEntry(entry.area() > node ? entry.area() - 1 : entry.area(), entry.condition())).toList(), cyclic);
    }
    public int indexOfArea(int area) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).area() == area) return i;
        return 0;
    }
}
