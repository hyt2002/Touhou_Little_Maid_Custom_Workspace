package com.erobrine.tlmcw.workspace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public record WorkspacePlan(
        UUID planId,
        List<WorkspaceNode> nodeList,
        RouteGraph graph,
        WorkSchedule schedule,
        Dynamic<?> unreadableData) {
    public static final int MAX_NAME_LENGTH = 64;
    static final Codec<WorkspacePlan> BODY_CODEC =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            UUIDUtil.CODEC
                                                    .fieldOf("plan_id")
                                                    .forGetter(WorkspacePlan::planId),
                                            WorkspaceNode.CODEC
                                                    .listOf(0, 64 + RouteGraph.MAX_POINTS)
                                                    .fieldOf("nodes")
                                                    .forGetter(WorkspacePlan::nodeList),
                                            RouteEdge.CODEC
                                                    .listOf(0, RouteGraph.MAX_EDGES)
                                                    .optionalFieldOf("edges", List.of())
                                                    .forGetter(p -> p.graph().edges()),
                                            WorkSchedule.CODEC
                                                    .optionalFieldOf(
                                                            "work_schedule", WorkSchedule.EMPTY)
                                                    .forGetter(WorkspacePlan::schedule))
                                    .apply(
                                            i,
                                            (id, nodes, edges, schedule) ->
                                                    new WorkspacePlan(
                                                            id,
                                                            nodes,
                                                            new RouteGraph(edges),
                                                            schedule)));
    public static final Codec<WorkspacePlan> CODEC =
            WorkspaceDataVersions.preserving(
                    WorkspaceDataVersions.plan(BODY_CODEC),
                    WorkspacePlan::unreadable,
                    WorkspacePlan::unreadableData);

    public WorkspacePlan {
        Objects.requireNonNull(planId);
        nodeList = List.copyOf(nodeList);
        var ids = new HashSet<UUID>();
        for (var node : nodeList)
            if (!ids.add(node.id())) throw new IllegalArgumentException("Duplicate node UUID");
        if (nodeList.stream().filter(WorkspaceNode::isArea).count() > 64
                || nodeList.stream().filter(n -> !n.isArea()).count() > RouteGraph.MAX_POINTS)
            throw new IllegalArgumentException("Too many nodes");
        if (graph.edges().stream().anyMatch(e -> !ids.contains(e.a()) || !ids.contains(e.b())))
            throw new IllegalArgumentException("Dangling edge");
        final var checkedNodes = nodeList;
        if (schedule.entries().stream()
                .anyMatch(
                        e ->
                                checkedNodes.stream()
                                        .noneMatch(
                                                n ->
                                                        n.id().equals(e.destination())
                                                                && n.type() == AreaType.WORK)))
            throw new IllegalArgumentException("Invalid schedule destination");
    }

    public WorkspacePlan(
            UUID id, List<WorkspaceNode> nodes, RouteGraph graph, WorkSchedule schedule) {
        this(id, nodes, graph, schedule, null);
    }

    public WorkspacePlan(ResourceLocation dim, List<WorkArea> areas) {
        this(
                UUID.randomUUID(),
                areas.stream().map(a -> WorkspaceNode.area(dim, a)).toList(),
                RouteGraph.EMPTY,
                WorkSchedule.EMPTY);
    }

    static WorkspacePlan unreadable(Dynamic<?> raw) {
        return new WorkspacePlan(
                new UUID(0, 0), List.of(), RouteGraph.EMPTY, WorkSchedule.EMPTY, raw);
    }

    public boolean readable() {
        return unreadableData == null;
    }

    public static WorkspacePlan empty(ResourceLocation dim) {
        return new WorkspacePlan(dim, List.of());
    }

    public WorkspaceNode node(UUID id) {
        return nodeList.stream().filter(n -> n.id().equals(id)).findFirst().orElse(null);
    }

    public boolean hasNodes() {
        return !nodeList.isEmpty();
    }

    public List<UUID> nodes(AreaType type) {
        return nodeList.stream().filter(n -> n.type() == type).map(WorkspaceNode::id).toList();
    }

    public List<UUID> nodes(AreaType type, ResourceLocation dim) {
        return nodeList.stream()
                .filter(n -> n.type() == type && n.dimension().equals(dim))
                .map(WorkspaceNode::id)
                .toList();
    }

    public List<UUID> areaIds() {
        return nodeList.stream().filter(WorkspaceNode::isArea).map(WorkspaceNode::id).toList();
    }

    public List<WorkArea> areas() {
        return nodeList.stream().filter(WorkspaceNode::isArea).map(WorkspaceNode::bounds).toList();
    }

    public UUID areaId(int displayIndex) {
        return areaIds().get(displayIndex);
    }

    public AreaType type(UUID id) {
        return Objects.requireNonNull(node(id)).type();
    }

    public String name(UUID id) {
        return Objects.requireNonNull(node(id)).name();
    }

    public WorkArea nodeArea(UUID id) {
        return Objects.requireNonNull(node(id)).bounds();
    }

    public boolean inDimension(UUID id, ResourceLocation dim) {
        var n = node(id);
        return n != null && n.dimension().equals(dim);
    }

    public static String cleanName(String name) {
        String clean = name.replaceAll("[\\p{Cntrl}§]", "").strip();
        if (clean.length() <= MAX_NAME_LENGTH) return clean;
        int end =
                Character.isHighSurrogate(clean.charAt(MAX_NAME_LENGTH - 1))
                        ? MAX_NAME_LENGTH - 1
                        : MAX_NAME_LENGTH;
        return clean.substring(0, end);
    }

    public WorkspacePlan withName(UUID id, String value) {
        return new WorkspacePlan(
                planId,
                nodeList.stream().map(n -> n.id().equals(id) ? n.withName(value) : n).toList(),
                graph,
                schedule);
    }

    public WorkspacePlan withType(UUID id, AreaType type) {
        if (type == AreaType.WAYPOINT || node(id) == null || !node(id).isArea())
            throw new IllegalArgumentException("Not an area type");
        return new WorkspacePlan(
                planId,
                nodeList.stream().map(n -> n.id().equals(id) ? n.withType(type) : n).toList(),
                graph,
                type == AreaType.WORK ? schedule : schedule.removeDestination(id));
    }

    public WorkspacePlan withSchedule(WorkSchedule value) {
        return new WorkspacePlan(planId, nodeList, graph, value);
    }

    public WorkspacePlan withGraph(RouteGraph value) {
        return new WorkspacePlan(planId, nodeList, value, schedule);
    }

    public WorkspacePlan addNode(WorkspaceNode node) {
        var nodes = new ArrayList<>(nodeList);
        nodes.add(node);
        return new WorkspacePlan(planId, nodes, graph, schedule);
    }

    public WorkspacePlan addArea(ResourceLocation dim, WorkArea area) {
        return addNode(WorkspaceNode.area(dim, area));
    }

    public WorkspacePlan clearWorkAreas() {
        return retain(nodeList.stream().filter(n -> !n.isArea()).toList());
    }

    public WorkspacePlan clearRoutePoints() {
        return retain(nodeList.stream().filter(WorkspaceNode::isArea).toList());
    }

    private WorkspacePlan retain(List<WorkspaceNode> nodes) {
        var ids =
                nodes.stream().map(WorkspaceNode::id).collect(java.util.stream.Collectors.toSet());
        return new WorkspacePlan(
                planId,
                nodes,
                new RouteGraph(
                        graph.edges().stream()
                                .filter(e -> ids.contains(e.a()) && ids.contains(e.b()))
                                .toList()),
                new WorkSchedule(
                        schedule.entries().stream()
                                .filter(e -> ids.contains(e.destination()))
                                .toList(),
                        schedule.cyclic()));
    }

    public WorkspacePlan removeNode(UUID id, boolean removeArea) {
        if (node(id) == null) return this;
        if (node(id).isArea() && !removeArea) return withGraph(graph.removeNode(id));
        return retain(nodeList.stream().filter(n -> !n.id().equals(id)).toList());
    }
}
