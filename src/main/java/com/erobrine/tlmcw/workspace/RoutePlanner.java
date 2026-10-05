package com.erobrine.tlmcw.workspace;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Distances use game coordinates; routing never scans world terrain or reads a real-time clock. */
public final class RoutePlanner {
    public record Route(int destination, List<Integer> remaining, double distance, boolean connected) {
        public Route { remaining = List.copyOf(remaining); }
    }
    public static Route to(WorkspacePlan plan, int target, Vec3 feet) {
        Route best = null;
        boolean inNode = false;
        BlockPos block = BlockPos.containing(feet);
        for (int node = -plan.graph().points().size(); node < plan.areas().size(); node++) {
            if (!plan.nodeArea(node).contains(block)) continue;
            inNode = true;
            var path = plan.graph().shortestPath(plan, node, target, feet);
            if (path.isEmpty()) continue;
            var remaining = path.subList(1, path.size());
            Route route = new Route(target, remaining, distance(plan, remaining, feet), true);
            if (best == null || route.distance() < best.distance()) best = route;
        }
        if (best != null) return best;
        if (!inNode) {
            var component = plan.graph().component(plan, target);
            Integer entry = null;
            double nearest = Double.POSITIVE_INFINITY;
            // Only intermediate points connected to this destination can be entries.
            for (int node = -1; node >= -plan.graph().points().size(); node--) {
                if (!component.contains(node)) continue;
                double distance = feet.distanceToSqr(plan.nodeArea(node).bounds().getCenter());
                if (distance < nearest) { nearest = distance; entry = node; }
            }
            if (entry != null) {
                var path = plan.graph().shortestPath(plan, entry, target);
                return new Route(target, path, distance(plan, path, feet), true);
            }
        }
        return new Route(target, List.of(), feet.distanceTo(plan.nodeArea(target).bounds().getCenter()), false);
    }
    public static Route nearest(WorkspacePlan plan, AreaType type, Vec3 feet, int excluded) {
        Route connected = null, fallback = null;
        for (int node : plan.nodes(type)) {
            if (node == excluded) continue;
            Route route = to(plan, node, feet);
            if (route.connected()) {
                if (connected == null || route.distance() < connected.distance()) connected = route;
            } else if (fallback == null || route.distance() < fallback.distance()) fallback = route;
        }
        return connected != null ? connected : fallback;
    }
    private static double distance(WorkspacePlan plan, List<Integer> path, Vec3 feet) {
        double distance = 0;
        Vec3 previous = feet;
        for (int node : path) {
            Vec3 center = plan.nodeArea(node).bounds().getCenter();
            distance += previous.distanceTo(center);
            previous = center;
        }
        return distance;
    }
    private RoutePlanner() {}
}
