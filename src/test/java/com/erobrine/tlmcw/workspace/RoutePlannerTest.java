package com.erobrine.tlmcw.workspace;

import static com.erobrine.tlmcw.gametest.V2Fixtures.*;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;

import java.util.List;

class RoutePlannerTest {
    private static WorkArea box(int x) {
        return WorkArea.between(new BlockPos(x, 64, 0), new BlockPos(x + 2, 66, 2));
    }

    private static WorkspacePlan plan() {
        return fixturePlan(
                        ResourceLocation.parse("minecraft:overworld"),
                        List.of(box(0), box(10), box(20), box(30)))
                .withType(n(1), AreaType.IDLE)
                .withType(n(2), AreaType.SLEEP);
    }

    @Test
    void markingChangesOnlyTypeAndRetainsNodeIdsAndEdges() {
        var graph =
                new FixtureGraph(List.of(new BlockPos(6, 65, 1)), List.of())
                        .connect(n(0), n(-1))
                        .connect(n(-1), n(1));
        var original = attach(plan(), graph);
        var changed = original.withType(n(0), original.type(n(0)).next());
        assertEquals(AreaType.IDLE, changed.type(n(0)));
        assertEquals(original.graph(), changed.graph());
        assertEquals(original.areas(), changed.areas());
        assertEquals(AreaType.WORK, changed.type(n(0)).next().next());
        var removed = changed.removeNode(n(1), true);
        assertEquals(List.of(AreaType.IDLE, AreaType.SLEEP, AreaType.WORK), types(removed));
    }

    @Test
    void workRotationSkipsOtherAreaTypesAndRestPreservesItsProgress() {
        var state = WorkspaceState.initial(plan()).withTravel(false).countWork(1);
        assertEquals(3, destination(state));
        state = state.withTravel(false).countWork(100);
        var rest =
                state.destination(AreaType.SLEEP, fixtureRoute(2, List.of(), 10, false))
                        .withTravel(false);
        assertEquals(3, workDestination(rest));
        assertEquals(1, rest.workTicks());
        assertEquals(rest, rest.countWork(1));
        var resumed =
                rest.destination(AreaType.WORK, fixtureRoute(3, List.of(), 10, false))
                        .withTravel(false);
        assertEquals(0, destination(resumed.countWork(2)));
    }

    @Test
    void typedDestinationRouteAndSeparateWorkCursorSurviveSaving() {
        var graph =
                new FixtureGraph(List.of(new BlockPos(24, 65, 1)), List.of())
                        .connect(n(3), n(-1))
                        .connect(n(-1), n(2));
        var state =
                fixtureState(attach(plan(), graph), 3, 837, false)
                        .destination(AreaType.SLEEP, fixtureRoute(2, List.of(-1, 2), 10, true));
        var restored =
                WorkspaceState.CODEC
                        .parse(
                                JsonOps.INSTANCE,
                                WorkspaceState.CODEC
                                        .encodeStart(JsonOps.INSTANCE, state)
                                        .getOrThrow())
                        .getOrThrow();
        assertEquals(state, restored);
        assertEquals(AreaType.SLEEP, restored.scheduleType());
        assertEquals(3, workDestination(restored));
        assertEquals(837, restored.workTicks());
    }

    @Test
    void legacyStateDefaultsToWorkTypesAndItsPreviousWorkCursor() {
        var json =
                JsonParser.parseString(
                        "{\"plan\":{\"dimension\":\"minecraft:overworld\",\"areas\":[{\"min\":[0,64,0],\"max\":[2,66,2]},{\"min\":[10,64,0],\"max\":[12,66,2]}]},\"active_index\":1,\"work_ticks\":73,\"travelling\":false}");
        var restored = WorkspaceState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(List.of(AreaType.WORK, AreaType.WORK), types(restored.plan()));
        assertEquals(restored.plan().areaId(1), restored.workDestination());
        assertEquals(73, restored.workTicks());
    }

    @Test
    void nearestIdleCandidateUsesTotalRouteDistanceRatherThanStraightLine() {
        var graph =
                new FixtureGraph(
                                List.of(
                                        new BlockPos(0, 65, 30),
                                        new BlockPos(10, 65, 30),
                                        new BlockPos(6, 65, 1)),
                                List.of())
                        .connect(n(0), n(-1))
                        .connect(n(-1), n(-2))
                        .connect(n(-2), n(1))
                        .connect(n(0), n(-3))
                        .connect(n(-3), n(2));
        var plan = attach(plan().withType(n(2), AreaType.IDLE), graph);
        var route =
                RoutePlanner.nearest(
                        plan,
                        AreaType.IDLE,
                        new Vec3(1.5, 65, 1.5),
                        null,
                        ResourceLocation.parse("minecraft:overworld"));
        assertEquals(n(2), route.destination());
        assertEquals(List.of(-3, 2), indices(route.remaining()));
    }

    @Test
    void aDisconnectedCloserAreaDoesNotBeatAConnectedCandidate() {
        var graph =
                new FixtureGraph(List.of(new BlockPos(6, 65, 1)), List.of())
                        .connect(n(0), n(-1))
                        .connect(n(-1), n(2));
        var route =
                RoutePlanner.nearest(
                        attach(plan().withType(n(2), AreaType.IDLE), graph),
                        AreaType.IDLE,
                        new Vec3(1.5, 65, 1.5),
                        null,
                        ResourceLocation.parse("minecraft:overworld"));
        assertEquals(n(2), route.destination());
        assertTrue(route.connected());
    }

    @Test
    void offGraphEntryIgnoresCloserPointsInOtherComponents() {
        var graph =
                new FixtureGraph(
                                List.of(
                                        new BlockPos(4, 65, 8),
                                        new BlockPos(6, 65, 8),
                                        new BlockPos(12, 65, 8)),
                                List.of())
                        .connect(n(-2), n(-3))
                        .connect(n(-3), n(2));
        var route =
                RoutePlanner.to(
                        attach(plan(), graph),
                        n(2),
                        new Vec3(3.5, 65, 8.5),
                        ResourceLocation.parse("minecraft:overworld"));
        assertEquals(List.of(-2, -3, 2), indices(route.remaining()));
        assertTrue(route.connected());
    }

    @Test
    void offGraphEntryUsesNearestConnectedNodeBeforePlanningTheRest() {
        var graph =
                new FixtureGraph(
                                List.of(
                                        new BlockPos(3, 65, 10),
                                        new BlockPos(25, 65, 10),
                                        new BlockPos(3, 65, 70)),
                                List.of())
                        .connect(n(-1), n(-3))
                        .connect(n(-3), n(3))
                        .connect(n(-2), n(3));
        var route =
                RoutePlanner.to(
                        attach(plan(), graph),
                        n(3),
                        new Vec3(3.5, 65, 12.5),
                        ResourceLocation.parse("minecraft:overworld"));
        assertEquals(List.of(-1, -3, 3), indices(route.remaining()));
    }

    @Test
    void offGraphEntryIncludesEveryAreaTypeInsteadOfOnlyWaypoints() {
        var dim = ResourceLocation.parse("minecraft:overworld");
        for (var type : List.of(AreaType.WORK, AreaType.IDLE, AreaType.SLEEP)) {
            var graph =
                    new FixtureGraph(List.of(new BlockPos(10, 65, 10)), List.of())
                            .connect(n(0), n(-1))
                            .connect(n(-1), n(1));
            var plan = fixturePlan(dim, List.of(box(0), box(30)), graph).withType(n(0), type);
            var route = RoutePlanner.to(plan, n(1), new Vec3(-2, 65, 1.5), dim);
            assertEquals(List.of(n(0), n(-1), n(1)), route.remaining(), type.toString());
            assertTrue(route.connected());
        }
    }

    @Test
    void offGraphEntryWorksWithAnAreaOnlyGraph() {
        var dim = ResourceLocation.parse("minecraft:overworld");
        var plan =
                fixturePlan(
                        dim,
                        List.of(box(0), box(30)),
                        RouteGraph.EMPTY.connect(n(0), n(1)),
                        List.of(AreaType.IDLE, AreaType.WORK));
        var route = RoutePlanner.to(plan, n(1), new Vec3(-2, 65, 1.5), dim);
        assertEquals(List.of(n(0), n(1)), route.remaining());
        assertTrue(route.connected());
    }

    @Test
    void nearestDestinationCanItselfBeTheOffGraphEntryEvenWithoutEdges() {
        var dim = ResourceLocation.parse("minecraft:overworld");
        var route =
                RoutePlanner.nearest(
                        plan().withType(n(2), AreaType.IDLE),
                        AreaType.IDLE,
                        new Vec3(15, 65, 8),
                        null,
                        dim);
        assertEquals(n(1), route.destination());
        assertEquals(List.of(n(1)), route.remaining());
        assertTrue(route.connected());
    }

    @Test
    void offGraphEntryIgnoresCloserForeignNodesAndDisconnectedAreas() {
        var dim = ResourceLocation.parse("minecraft:overworld");
        var plan =
                fixturePlan(dim, List.of(box(0), box(30), box(-3)))
                        .withGraph(RouteGraph.EMPTY.connect(n(0), n(1)));
        var foreign =
                WorkspaceNode.waypoint(
                        ResourceLocation.parse("minecraft:the_nether"), new BlockPos(-2, 65, 5));
        plan = plan.addNode(foreign).withGraph(plan.graph().connect(foreign.id(), n(0)));
        var route = RoutePlanner.to(plan, n(1), new Vec3(-1.5, 65, 5.5), dim);
        assertEquals(List.of(n(0), n(1)), route.remaining());
        assertTrue(route.connected());
    }

    @Test
    void missingRoutesFallBackToTheNearestDestination() {
        var route =
                RoutePlanner.nearest(
                        plan().withType(n(2), AreaType.IDLE),
                        AreaType.IDLE,
                        new Vec3(1.5, 65, 1.5),
                        null,
                        ResourceLocation.parse("minecraft:overworld"));
        assertEquals(n(1), route.destination());
        assertFalse(route.connected());
        assertTrue(indices(route.remaining()).isEmpty());
    }

    @Test
    void plansWithOnlyRestAreasAndBulkClearingRetainTheirTypes() {
        var plan =
                fixturePlan(ResourceLocation.parse("minecraft:overworld"), List.of(box(0)))
                        .withType(n(0), AreaType.IDLE);
        assertEquals(-1, workDestination(WorkspaceState.initial(plan)));
        assertEquals(types(plan), types(plan.clearRoutePoints()));
        assertTrue(types(plan.clearWorkAreas()).isEmpty());
    }
}
