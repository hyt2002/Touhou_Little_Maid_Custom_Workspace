package com.erobrine.tlmcw.workspace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import java.util.*;

public record RouteGraph(List<BlockPos> points, List<RouteEdge> edges) {
    public static final int MAX_POINTS = 256, MAX_EDGES = 2048;
    public static final RouteGraph EMPTY = new RouteGraph(List.of(), List.of());
    public static final Codec<RouteGraph> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.listOf(0, MAX_POINTS).fieldOf("points").forGetter(RouteGraph::points),
            RouteEdge.CODEC.listOf(0, MAX_EDGES).fieldOf("edges").forGetter(RouteGraph::edges)
    ).apply(instance, RouteGraph::new));

    public RouteGraph {
        points = points.stream().map(BlockPos::immutable).toList();
        edges = edges.stream().distinct().toList();
        if (points.size() > MAX_POINTS || edges.size() > MAX_EDGES) throw new IllegalArgumentException("Route graph too large");
    }
    public boolean hasNode(int node, int areaCount) {
        return node >= 0 ? node < areaCount : node >= -points.size();
    }
    public RouteGraph connect(int a, int b) {
        if (a == b) return this;
        var edge = new RouteEdge(a, b);
        if (edges.contains(edge)) return this;
        var updated = new ArrayList<>(edges);
        updated.add(edge);
        return new RouteGraph(points, updated);
    }
    public RouteGraph addPoint(BlockPos pos) {
        var updated = new ArrayList<>(points);
        updated.add(pos.immutable());
        return new RouteGraph(updated, edges);
    }
    public RouteGraph removeNode(int node, boolean removeArea) {
        var updatedPoints = new ArrayList<>(points);
        if (node < 0) updatedPoints.remove(-node - 1);
        var updatedEdges = new ArrayList<RouteEdge>();
        for (RouteEdge edge : edges) {
            if (edge.touches(node)) continue;
            int a = remap(edge.a(), node, removeArea), b = remap(edge.b(), node, removeArea);
            updatedEdges.add(new RouteEdge(a, b));
        }
        return new RouteGraph(updatedPoints, updatedEdges);
    }
    private static int remap(int value, int removed, boolean removeArea) {
        if (removed < 0 && value < removed) return value + 1;
        if (removeArea && removed >= 0 && value > removed) return value - 1;
        return value;
    }

    /** The maximal connected subgraph containing the destination, including isolated destinations. */
    public Set<Integer> component(WorkspacePlan plan, int target) {
        if (!hasNode(target, plan.areas().size())) return Set.of();
        var result = new HashSet<Integer>();
        var queue = new ArrayDeque<Integer>();
        result.add(target);
        queue.add(target);
        while (!queue.isEmpty()) {
            int node = queue.remove();
            for (RouteEdge edge : edges) {
                if (!edge.touches(node)) continue;
                int other = edge.other(node);
                if (hasNode(other, plan.areas().size()) && result.add(other)) queue.add(other);
            }
        }
        return Set.copyOf(result);
    }

    /** Dijkstra uses Euclidean distances between region centers; the graph does not validate world terrain. */
    public List<Integer> shortestPath(WorkspacePlan plan, int start, int end) {
        return shortestPath(plan, start, end, null);
    }
    public List<Integer> shortestPath(WorkspacePlan plan, int start, int end, net.minecraft.world.phys.Vec3 startPosition) {
        if (!hasNode(start, plan.areas().size()) || !hasNode(end, plan.areas().size())) return List.of();
        record Entry(int node, double distance) {}
        var queue = new PriorityQueue<Entry>(Comparator.comparingDouble(Entry::distance).thenComparingInt(Entry::node));
        var distances = new HashMap<Integer, Double>();
        var previous = new HashMap<Integer, Integer>();
        distances.put(start, 0.0);
        queue.add(new Entry(start, 0));
        while (!queue.isEmpty()) {
            Entry current = queue.remove();
            if (current.distance() > distances.getOrDefault(current.node(), Double.POSITIVE_INFINITY)) continue;
            if (current.node() == end) {
                var path = new ArrayList<Integer>();
                for (int node = end; ; node = previous.get(node)) {
                    path.add(node);
                    if (node == start) break;
                }
                Collections.reverse(path);
                return List.copyOf(path);
            }
            for (RouteEdge edge : edges) {
                if (!edge.touches(current.node())) continue;
                int neighbor = edge.other(current.node());
                if (!hasNode(neighbor, plan.areas().size())) continue;
                var position = current.node() == start && startPosition != null ? startPosition : plan.nodeArea(current.node()).bounds().getCenter();
                double distance = current.distance() + position
                        .distanceTo(plan.nodeArea(neighbor).bounds().getCenter());
                if (distance < distances.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) {
                    distances.put(neighbor, distance);
                    previous.put(neighbor, current.node());
                    queue.add(new Entry(neighbor, distance));
                }
            }
        }
        return List.of();
    }
}
