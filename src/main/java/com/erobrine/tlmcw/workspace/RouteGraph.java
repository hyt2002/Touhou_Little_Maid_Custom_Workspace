package com.erobrine.tlmcw.workspace;

import net.minecraft.world.phys.Vec3;

import java.util.*;

public record RouteGraph(List<RouteEdge> edges) {
    public static final int MAX_POINTS = 256, MAX_EDGES = 2048;
    public static final RouteGraph EMPTY = new RouteGraph(List.of());

    public RouteGraph {
        edges =
                edges.stream()
                        .distinct()
                        .sorted(Comparator.comparing(RouteEdge::a).thenComparing(RouteEdge::b))
                        .toList();
        if (edges.size() > MAX_EDGES) throw new IllegalArgumentException("Too many edges");
    }

    public RouteGraph connect(UUID a, UUID b) {
        if (a.equals(b)) return this;
        var updated = new ArrayList<>(edges);
        updated.add(new RouteEdge(a, b));
        return new RouteGraph(updated);
    }

    public RouteGraph removeNode(UUID id) {
        return new RouteGraph(edges.stream().filter(e -> !e.touches(id)).toList());
    }

    /**
     * Edges use the shared dimension distance table. Navigation waits for external dimension
     * transfers.
     */
    public Set<UUID> component(WorkspacePlan plan, UUID target) {
        if (plan.node(target) == null) return Set.of();

        var result = new HashSet<UUID>();
        var queue = new ArrayDeque<UUID>();
        result.add(target);
        queue.add(target);
        while (!queue.isEmpty()) {
            UUID id = queue.remove();
            for (var edge : edges)
                if (edge.touches(id)) {
                    UUID other = edge.other(id);
                    if (plan.node(other) != null && result.add(other)) queue.add(other);
                }
        }
        return Set.copyOf(result);
    }

    public List<UUID> shortestPath(WorkspacePlan plan, UUID start, UUID end) {
        return shortestPath(plan, start, end, null);
    }

    public List<UUID> shortestPath(WorkspacePlan plan, UUID start, UUID end, Vec3 startPosition) {
        return shortestPath(plan, start, end, startPosition, DimensionDistances.current());
    }

    public List<UUID> shortestPath(
            WorkspacePlan plan,
            UUID start,
            UUID end,
            Vec3 startPosition,
            DimensionDistances.Table table) {
        if (plan.node(start) == null || plan.node(end) == null) return List.of();

        record Entry(UUID node, double distance) {}
        var queue =
                new PriorityQueue<Entry>(
                        Comparator.comparingDouble(Entry::distance).thenComparing(Entry::node));
        var distances = new HashMap<UUID, Double>();
        var previous = new HashMap<UUID, UUID>();
        distances.put(start, 0.0);
        queue.add(new Entry(start, 0));
        while (!queue.isEmpty()) {
            Entry current = queue.remove();
            if (current.distance()
                    > distances.getOrDefault(current.node(), Double.POSITIVE_INFINITY)) continue;
            if (current.node().equals(end)) {
                var path = new ArrayList<UUID>();
                for (UUID id = end; ; id = previous.get(id)) {
                    path.add(id);
                    if (id.equals(start)) break;
                }
                Collections.reverse(path);
                return List.copyOf(path);
            }
            for (var edge : edges)
                if (edge.touches(current.node())) {
                    UUID other = edge.other(current.node());
                    if (plan.node(other) == null) continue;
                    Vec3 pos =
                            current.node().equals(start) && startPosition != null
                                    ? startPosition
                                    : plan.nodeArea(current.node()).bounds().getCenter();
                    double distance =
                            current.distance()
                                    + table.distance(
                                            pos,
                                            plan.node(current.node()).dimension(),
                                            plan.nodeArea(other).bounds().getCenter(),
                                            plan.node(other).dimension());
                    if (distance < distances.getOrDefault(other, Double.POSITIVE_INFINITY)) {
                        distances.put(other, distance);
                        previous.put(other, current.node());
                        queue.add(new Entry(other, distance));
                    }
                }
        }
        return List.of();
    }
}
