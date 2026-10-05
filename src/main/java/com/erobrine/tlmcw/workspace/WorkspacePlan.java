package com.erobrine.tlmcw.workspace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

public record WorkspacePlan(ResourceLocation dimension, List<WorkArea> areas, RouteGraph graph, List<AreaType> areaTypes,
                            List<String> areaNames, WorkSchedule schedule) {
    public static final int MAX_NAME_LENGTH = 64;
    public static final Codec<WorkspacePlan> CODEC = WorkspaceDataVersions.plan(RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(WorkspacePlan::dimension),
            WorkArea.CODEC.listOf(0, 64).fieldOf("areas").forGetter(WorkspacePlan::areas),
            RouteGraph.CODEC.optionalFieldOf("route_graph", RouteGraph.EMPTY).forGetter(WorkspacePlan::graph),
            AreaType.CODEC.listOf(0, 64).optionalFieldOf("area_types", List.of()).forGetter(WorkspacePlan::areaTypes),
            Codec.string(0, MAX_NAME_LENGTH).listOf(0, 64).optionalFieldOf("area_names", List.of()).forGetter(WorkspacePlan::areaNames),
            WorkSchedule.CODEC.optionalFieldOf("work_schedule", WorkSchedule.EMPTY).forGetter(WorkspacePlan::schedule)
    ).apply(instance, WorkspacePlan::new)));
    public WorkspacePlan {
        areas = List.copyOf(areas);
        if (areaTypes.isEmpty()) areaTypes = java.util.Collections.nCopies(areas.size(), AreaType.WORK);
        if (areaTypes.size() != areas.size()) throw new IllegalArgumentException("Area type count must match cuboid count");
        areaTypes = List.copyOf(areaTypes);
        if (areaNames.isEmpty()) areaNames = java.util.Collections.nCopies(areas.size(), "");
        if (areaNames.size() != areas.size()) throw new IllegalArgumentException("Area name count must match cuboid count");
        areaNames = areaNames.stream().map(WorkspacePlan::cleanName).toList();
        schedule = schedule.retainWorkAreas(areaTypes);
        final int count = areas.size();
        final RouteGraph original = graph;
        graph = new RouteGraph(graph.points(), graph.edges().stream()
                .filter(e -> original.hasNode(e.a(), count) && original.hasNode(e.b(), count)).toList());
    }
    public WorkspacePlan(ResourceLocation dimension, List<WorkArea> areas, RouteGraph graph, List<AreaType> types) { this(dimension, areas, graph, types, List.of(), WorkSchedule.EMPTY); }
    public WorkspacePlan(ResourceLocation dimension, List<WorkArea> areas, RouteGraph graph) { this(dimension, areas, graph, List.of()); }
    public WorkspacePlan(ResourceLocation dimension, List<WorkArea> areas) { this(dimension, areas, RouteGraph.EMPTY); }
    public static WorkspacePlan empty(ResourceLocation dimension) { return new WorkspacePlan(dimension, List.of()); }
    public boolean hasNodes() { return !areas.isEmpty() || !graph.points().isEmpty(); }
    public AreaType type(int area) { return areaTypes.get(area); }
    public String name(int area) { return areaNames.get(area); }
    public static String cleanName(String name) {
        String clean = name.replaceAll("[\\p{Cntrl}§]", "").strip();
        if (clean.length() <= MAX_NAME_LENGTH) return clean;
        int end = Character.isHighSurrogate(clean.charAt(MAX_NAME_LENGTH - 1)) ? MAX_NAME_LENGTH - 1 : MAX_NAME_LENGTH;
        return clean.substring(0, end);
    }
    public WorkspacePlan withName(int area, String name) {
        var updated = new java.util.ArrayList<>(areaNames);
        updated.set(area, cleanName(name));
        return new WorkspacePlan(dimension, areas, graph, areaTypes, updated, schedule);
    }
    public WorkspacePlan withSchedule(WorkSchedule value) {
        if (!value.validFor(this)) throw new IllegalArgumentException("Schedule destination must be a work area");
        return new WorkspacePlan(dimension, areas, graph, areaTypes, areaNames, value);
    }
    public List<Integer> nodes(AreaType type) {
        return java.util.stream.IntStream.range(0, areas.size()).filter(i -> areaTypes.get(i) == type).boxed().toList();
    }
    public WorkspacePlan withType(int area, AreaType type) {
        var updated = new java.util.ArrayList<>(areaTypes);
        updated.set(area, type);
        return new WorkspacePlan(dimension, areas, graph, updated, areaNames, schedule);
    }
    public WorkspacePlan clearWorkAreas() {
        // Constructor validation drops only edges whose cuboid endpoints disappeared.
        return new WorkspacePlan(dimension, List.of(), graph);
    }
    public WorkspacePlan clearRoutePoints() {
        return new WorkspacePlan(dimension, areas, new RouteGraph(List.of(), graph.edges().stream()
                .filter(edge -> edge.a() >= 0 && edge.b() >= 0).toList()), areaTypes, areaNames, schedule);
    }
    public WorkArea nodeArea(int node) {
        if (node >= 0) return areas.get(node);
        var point = graph.points().get(-node - 1);
        return WorkArea.between(point, point);
    }
    public WorkspacePlan addArea(WorkArea area) {
        var updated = new java.util.ArrayList<>(areas);
        var types = new java.util.ArrayList<>(areaTypes);
        updated.add(area);
        types.add(AreaType.WORK);
        var names = new java.util.ArrayList<>(areaNames);
        names.add("");
        return new WorkspacePlan(dimension, updated, graph, types, names, schedule);
    }
    public WorkspacePlan removeNode(int node, boolean removeArea) {
        var updated = new java.util.ArrayList<>(areas);
        var types = new java.util.ArrayList<>(areaTypes);
        var names = new java.util.ArrayList<>(areaNames);
        if (node >= 0 && removeArea) updated.remove(node);
        if (node >= 0 && removeArea) types.remove(node);
        if (node >= 0 && removeArea) names.remove(node);
        return new WorkspacePlan(dimension, updated, graph.removeNode(node, removeArea), types, names,
                node >= 0 && removeArea ? schedule.removeArea(node) : schedule);
    }
    public WorkspacePlan withGraph(RouteGraph value) { return new WorkspacePlan(dimension, areas, value, areaTypes, areaNames, schedule); }
}
