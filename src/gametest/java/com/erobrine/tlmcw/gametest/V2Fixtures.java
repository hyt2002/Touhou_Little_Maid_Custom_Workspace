package com.erobrine.tlmcw.gametest;

import com.erobrine.tlmcw.workspace.*;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Deterministic fixture identities; production always creates fresh UUIDs for new nodes. */
public final class V2Fixtures {
    public static UUID n(int slot) {
        return UUID.nameUUIDFromBytes(("test-node:" + slot).getBytes(StandardCharsets.UTF_8));
    }

    public static WorkspacePlan fixturePlan(ResourceLocation dim, List<WorkArea> areas) {
        return fixturePlan(dim, areas, RouteGraph.EMPTY, List.of(), List.of(), WorkSchedule.EMPTY);
    }

    public static WorkspacePlan fixturePlan(
            ResourceLocation dim, List<WorkArea> areas, RouteGraph graph) {
        return fixturePlan(dim, areas, graph, List.of(), List.of(), WorkSchedule.EMPTY);
    }

    public static WorkspacePlan fixturePlan(
            ResourceLocation dim, List<WorkArea> areas, RouteGraph graph, List<AreaType> types) {
        return fixturePlan(dim, areas, graph, types, List.of(), WorkSchedule.EMPTY);
    }

    public static WorkspacePlan fixturePlan(
            ResourceLocation dim,
            List<WorkArea> areas,
            RouteGraph graph,
            List<AreaType> types,
            List<String> names,
            WorkSchedule schedule) {
        var nodes = new ArrayList<WorkspaceNode>();
        for (int i = 0; i < areas.size(); i++) {
            var a = areas.get(i);
            nodes.add(
                    new WorkspaceNode(
                            n(i),
                            dim,
                            types.isEmpty() ? AreaType.WORK : types.get(i),
                            names.isEmpty() ? "" : names.get(i),
                            a.min(),
                            a.max()));
        }
        return new WorkspacePlan(n(999), nodes, graph, schedule);
    }

    public static WorkspacePlan fixturePlan(
            ResourceLocation dim, List<WorkArea> areas, FixtureGraph graph, List<AreaType> types) {
        return attach(fixturePlan(dim, areas, RouteGraph.EMPTY, types), graph);
    }

    public static WorkspacePlan fixturePlan(
            ResourceLocation dim, List<WorkArea> areas, FixtureGraph graph) {
        return attach(fixturePlan(dim, areas), graph);
    }

    public static WorkspacePlan fixturePlan(
            ResourceLocation dim,
            List<WorkArea> areas,
            FixtureGraph graph,
            List<AreaType> types,
            List<String> names,
            WorkSchedule schedule) {
        return attach(fixturePlan(dim, areas, RouteGraph.EMPTY, types, names, schedule), graph);
    }

    public static WorkspacePlan attach(WorkspacePlan plan, RouteGraph graph) {
        return plan.withGraph(graph);
    }

    public static WorkspacePlan attach(WorkspacePlan plan, FixtureGraph graph) {
        var dim = plan.nodeList().getFirst().dimension();
        var nodes =
                new ArrayList<>(plan.nodeList().stream().filter(WorkspaceNode::isArea).toList());
        for (int i = 0; i < graph.points().size(); i++) {
            var p = graph.points().get(i);
            nodes.add(new WorkspaceNode(n(-i - 1), dim, AreaType.WAYPOINT, "", p, p));
        }
        return new WorkspacePlan(
                plan.planId(), nodes, new RouteGraph(graph.edges()), plan.schedule());
    }

    public record FixtureGraph(List<BlockPos> points, List<RouteEdge> edges) {
        public FixtureGraph connect(int a, int b) {
            return connect(n(a), n(b));
        }

        public FixtureGraph connect(UUID a, UUID b) {
            if (a.equals(b)) return this;
            var e = new ArrayList<>(edges);
            e.add(new RouteEdge(a, b));
            return new FixtureGraph(points, e.stream().distinct().toList());
        }

        public List<UUID> shortestPath(WorkspacePlan p, UUID a, UUID b) {
            return p.graph().shortestPath(p, a, b);
        }
    }

    public static List<BlockPos> points(WorkspacePlan p) {
        return p.nodeList().stream().filter(n -> !n.isArea()).map(WorkspaceNode::min).toList();
    }

    public static List<AreaType> types(WorkspacePlan p) {
        return p.nodeList().stream()
                .filter(WorkspaceNode::isArea)
                .map(WorkspaceNode::type)
                .toList();
    }

    public static List<String> names(WorkspacePlan p) {
        return p.nodeList().stream()
                .filter(WorkspaceNode::isArea)
                .map(WorkspaceNode::name)
                .toList();
    }

    public static int index(UUID id) {
        if (id == null) return -1;
        for (int i = -256; i < 64; i++) if (n(i).equals(id)) return i;
        throw new AssertionError("Unexpected fixture identity " + id);
    }

    public static int destination(WorkspaceState s) {
        return index(s.destinationId());
    }

    public static int workDestination(WorkspaceState s) {
        return index(s.workDestination());
    }

    public static int entryPosition(WorkspaceState s) {
        return s.plan().schedule().position(s.entryId());
    }

    public static List<Integer> indices(List<UUID> list) {
        return list.stream().map(V2Fixtures::index).toList();
    }

    public static WorkspaceState fixtureState(
            WorkspacePlan p, int dest, long ticks, boolean travel) {
        return fixtureState(p, dest, ticks, travel, List.of());
    }

    public static WorkspaceState fixtureState(
            WorkspacePlan p, int dest, long ticks, boolean travel, List<Integer> route) {
        return fixtureState(p, dest, ticks, travel, route, AreaType.WORK, dest, 0, false);
    }

    public static WorkspaceState fixtureState(
            WorkspacePlan p,
            int dest,
            long ticks,
            boolean travel,
            List<Integer> route,
            AreaType type,
            int work) {
        return fixtureState(p, dest, ticks, travel, route, type, work, 0, false);
    }

    public static WorkspaceState fixtureState(
            WorkspacePlan p,
            int dest,
            long ticks,
            boolean travel,
            List<Integer> route,
            AreaType type,
            int work,
            int entry,
            boolean finished) {
        UUID e = p.schedule().custom() ? p.schedule().entries().get(entry).id() : null;
        return new WorkspaceState(
                p,
                n(dest),
                ticks,
                travel,
                route.stream().map(V2Fixtures::n).toList(),
                type,
                work < 0 ? null : n(work),
                e,
                finished,
                false);
    }

    public static RoutePlanner.Route fixtureRoute(
            int dest, List<Integer> route, double distance, boolean connected) {
        return new RoutePlanner.Route(
                n(dest), route.stream().map(V2Fixtures::n).toList(), distance, connected);
    }

    public static ResourceLocation dim(WorkspacePlan p) {
        return p.nodeList().getFirst().dimension();
    }

    public static net.minecraft.nbt.CompoundTag legacyPlanTag(WorkspacePlan p, boolean version) {
        var tag = new net.minecraft.nbt.CompoundTag();
        if (version) tag.putInt("data_version", 1);
        tag.putString("dimension", dim(p).toString());
        var areas = new net.minecraft.nbt.ListTag();
        var types = new net.minecraft.nbt.ListTag();
        var names = new net.minecraft.nbt.ListTag();
        for (var node : p.nodeList())
            if (node.isArea()) {
                var a = new net.minecraft.nbt.CompoundTag();
                a.putIntArray(
                        "min", new int[] {node.min().getX(), node.min().getY(), node.min().getZ()});
                a.putIntArray(
                        "max", new int[] {node.max().getX(), node.max().getY(), node.max().getZ()});
                areas.add(a);
                types.add(
                        net.minecraft.nbt.StringTag.valueOf(
                                node.type().name().toLowerCase(java.util.Locale.ROOT)));
                names.add(net.minecraft.nbt.StringTag.valueOf(node.name()));
            }
        tag.put("areas", areas);
        tag.put("area_types", types);
        tag.put("area_names", names);
        var graph = new net.minecraft.nbt.CompoundTag();
        var points = new net.minecraft.nbt.ListTag();
        for (var node : p.nodeList())
            if (!node.isArea())
                points.add(
                        new net.minecraft.nbt.IntArrayTag(
                                new int[] {
                                    node.min().getX(), node.min().getY(), node.min().getZ()
                                }));
        var edges = new net.minecraft.nbt.ListTag();
        for (var e : p.graph().edges()) {
            var edge = new net.minecraft.nbt.CompoundTag();
            edge.putInt("a", index(e.a()));
            edge.putInt("b", index(e.b()));
            edges.add(edge);
        }
        graph.put("points", points);
        graph.put("edges", edges);
        tag.put("route_graph", graph);
        var schedule = new net.minecraft.nbt.CompoundTag();
        var entries = new net.minecraft.nbt.ListTag();
        for (var e : p.schedule().entries()) {
            var entry = new net.minecraft.nbt.CompoundTag();
            entry.putInt("area", index(e.destination()));
            entry.put(
                    "condition",
                    DelayCondition.CODEC
                            .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, e.condition())
                            .getOrThrow());
            entries.add(entry);
        }
        schedule.put("entries", entries);
        schedule.putBoolean("cyclic", p.schedule().cyclic());
        tag.put("work_schedule", schedule);
        return tag;
    }

    public static net.minecraft.nbt.CompoundTag legacyStateTag(WorkspaceState s, boolean version) {
        var tag = new net.minecraft.nbt.CompoundTag();
        if (version) tag.putInt("data_version", 1);
        tag.put("plan", legacyPlanTag(s.plan(), version));
        tag.putInt("active_index", index(s.destinationId()));
        tag.putLong("work_ticks", s.workTicks());
        tag.putBoolean("travelling", s.travelling());
        var route = new net.minecraft.nbt.ListTag();
        s.remainingRoute().forEach(n -> route.add(net.minecraft.nbt.IntTag.valueOf(index(n))));
        tag.put("remaining_route", route);
        tag.putString("schedule_type", s.scheduleType().name().toLowerCase(java.util.Locale.ROOT));
        tag.putInt("work_index", index(s.workDestination()));
        tag.putInt("schedule_index", Math.max(0, entryPosition(s)));
        tag.putBoolean("schedule_finished", s.scheduleFinished());
        return tag;
    }

    private V2Fixtures() {}
}
