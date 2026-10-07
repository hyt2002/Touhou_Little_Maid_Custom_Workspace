package com.erobrine.tlmcw.workspace;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class RoutePlanner {
    public record Route(
            UUID destination, List<UUID> remaining, double distance, boolean connected) {
        public Route {
            remaining = List.copyOf(remaining);
        }
    }

    public static Route to(WorkspacePlan plan, UUID target, Vec3 feet, ResourceLocation dim) {
        return to(plan, target, feet, dim, DimensionDistances.current());
    }

    public static Route to(
            WorkspacePlan plan,
            UUID target,
            Vec3 feet,
            ResourceLocation dim,
            DimensionDistances.Table table) {
        if (plan.node(target) == null) return null;
        Route best = null;
        boolean inNode = false;
        BlockPos block = BlockPos.containing(feet);
        for (var n : plan.nodeList()) {
            UUID id = n.id();
            if (!n.dimension().equals(dim) || !n.bounds().contains(block)) continue;
            inNode = true;
            var path = plan.graph().shortestPath(plan, id, target, feet, table);
            if (path.isEmpty()) continue;
            var remaining = path.subList(1, path.size());
            var route =
                    new Route(target, remaining, distance(plan, remaining, feet, dim, table), true);
            if (best == null || route.distance() < best.distance()) best = route;
        }
        if (best != null) return best;
        if (!inNode) {
            var component = plan.graph().component(plan, target);
            UUID entry = null;
            double nearest = Double.POSITIVE_INFINITY;
            for (var node : plan.nodeList())
                if (node.dimension().equals(dim) && component.contains(node.id())) {
                    double distance = feet.distanceToSqr(node.bounds().bounds().getCenter());
                    if (distance < nearest) {
                        nearest = distance;
                        entry = node.id();
                    }
                }
            if (entry != null) {
                var path = plan.graph().shortestPath(plan, entry, target, null, table);
                return new Route(target, path, distance(plan, path, feet, dim, table), true);
            }
        }
        return new Route(
                target,
                List.of(),
                table.distance(
                        feet,
                        dim,
                        plan.nodeArea(target).bounds().getCenter(),
                        plan.node(target).dimension()),
                false);
    }

    public static Route nearest(
            WorkspacePlan plan, AreaType type, Vec3 feet, UUID excluded, ResourceLocation dim) {
        return nearest(plan, type, feet, excluded, dim, DimensionDistances.current());
    }

    public static Route nearest(
            WorkspacePlan plan,
            AreaType type,
            Vec3 feet,
            UUID excluded,
            ResourceLocation dim,
            DimensionDistances.Table table) {
        Route connected = null, fallback = null;
        for (UUID id : plan.nodes(type)) {
            if (id.equals(excluded)) continue;
            Route route = to(plan, id, feet, dim, table);
            if (route.connected()) {
                if (connected == null || route.distance() < connected.distance()) connected = route;
            } else if (fallback == null || route.distance() < fallback.distance()) fallback = route;
        }
        return connected != null ? connected : fallback;
    }

    private static double distance(
            WorkspacePlan plan,
            List<UUID> path,
            Vec3 feet,
            ResourceLocation dim,
            DimensionDistances.Table table) {
        double distance = 0;
        Vec3 previous = feet;
        for (UUID id : path) {
            Vec3 center = plan.nodeArea(id).bounds().getCenter();
            distance += table.distance(previous, dim, center, plan.node(id).dimension());
            previous = center;
            dim = plan.node(id).dimension();
        }
        return distance;
    }

    private RoutePlanner() {}
}
