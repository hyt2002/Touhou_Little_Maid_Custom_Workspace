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

class RouteGraphTest {
    private static WorkspacePlan plan() {
        return fixturePlan(
                ResourceLocation.parse("minecraft:overworld"),
                List.of(
                        WorkArea.between(new BlockPos(0, 64, 0), new BlockPos(2, 66, 2)),
                        WorkArea.between(new BlockPos(12, 64, 0), new BlockPos(14, 66, 2))));
    }

    private static WorkspacePlan connected() {
        FixtureGraph graph =
                new FixtureGraph(List.of(new BlockPos(4, 65, 1), new BlockPos(9, 65, 1)), List.of())
                        .connect(n(0), n(-1))
                        .connect(n(-1), n(-2))
                        .connect(n(-2), n(1));
        return attach(plan(), graph);
    }

    @Test
    void shortestRouteUsesDistanceRatherThanFewestNodesAndWorksInBothDirections() {
        var graph =
                new FixtureGraph(
                                List.of(
                                        new BlockPos(6, 65, 50),
                                        new BlockPos(4, 65, 1),
                                        new BlockPos(9, 65, 1)),
                                List.of())
                        .connect(n(0), n(-1))
                        .connect(n(-1), n(1))
                        .connect(n(0), n(-2))
                        .connect(n(-2), n(-3))
                        .connect(n(-3), n(1));
        var plan = attach(plan(), graph);
        assertEquals(List.of(n(0), n(-2), n(-3), n(1)), graph.shortestPath(plan, n(0), n(1)));
        assertEquals(List.of(n(1), n(-3), n(-2), n(0)), graph.shortestPath(plan, n(1), n(0)));
    }

    @Test
    void duplicateEdgesAndCyclesDoNotChangeTheRoute() {
        var plan = connected();
        var graph = plan.graph().connect(n(-1), n(0)).connect(n(-2), n(-1)).connect(n(-1), n(-1));
        assertEquals(plan.graph(), graph);
        assertEquals(List.of(n(0), n(-1), n(-2), n(1)), graph.shortestPath(plan, n(0), n(1)));
    }

    @Test
    void disconnectedGraphFallsBackToDirectNavigation() {
        var plan =
                attach(
                        plan(),
                        new FixtureGraph(List.of(new BlockPos(4, 65, 1)), List.of())
                                .connect(n(0), n(-1)));
        assertTrue(plan.graph().shortestPath(plan, n(0), n(1)).isEmpty());
        var next = WorkspaceState.initial(plan).withTravel(false).next();
        assertTrue(indices(next.remainingRoute()).isEmpty());
        assertEquals(plan.areas().get(1), next.navigationArea());
    }

    @Test
    void deletingAWorkAreaKeepsRemainingIdentities() {
        var plan =
                connected()
                        .addNode(
                                new WorkspaceNode(
                                        n(2),
                                        ResourceLocation.parse("minecraft:overworld"),
                                        AreaType.WORK,
                                        "",
                                        new BlockPos(20, 64, 0),
                                        new BlockPos(22, 66, 2)));
        plan = attach(plan, plan.graph().connect(n(1), n(2))).removeNode(n(0), true);
        assertEquals(2, plan.areas().size());
        assertEquals(
                java.util.Set.of(
                        new RouteEdge(n(-1), n(-2)),
                        new RouteEdge(n(-2), n(1)),
                        new RouteEdge(n(1), n(2))),
                java.util.Set.copyOf(plan.graph().edges()));
        assertEquals(2, points(plan).size());
    }

    @Test
    void deletingAWaypointDropsOnlyItsOwnEdges() {
        var plan = connected().removeNode(n(-1), false);
        assertEquals(List.of(new BlockPos(9, 65, 1)), points(plan));
        assertEquals(List.of(new RouteEdge(n(-2), n(1))), plan.graph().edges());
        assertEquals(2, plan.areas().size());
        assertTrue(plan.graph().shortestPath(plan, n(0), n(1)).isEmpty());
    }

    @Test
    void clearingWorkAreasKeepsWaypointIndicesConnectionsAndSavedData() {
        var original = attach(connected(), connected().graph().connect(n(0), n(1)));
        var cleared = original.clearWorkAreas();
        assertTrue(cleared.areas().isEmpty());
        assertEquals(points(original), points(cleared));
        assertEquals(List.of(new RouteEdge(n(-1), n(-2))), cleared.graph().edges());
        assertEquals(List.of(n(-1), n(-2)), cleared.graph().shortestPath(cleared, n(-1), n(-2)));
        assertEquals(
                cleared,
                WorkspacePlan.CODEC
                        .parse(
                                JsonOps.INSTANCE,
                                WorkspacePlan.CODEC
                                        .encodeStart(JsonOps.INSTANCE, cleared)
                                        .getOrThrow())
                        .getOrThrow());
        assertEquals(2, original.areas().size());
    }

    @Test
    void clearingRoutePointsKeepsWorkAreasAndTheirDirectConnections() {
        var original = attach(connected(), connected().graph().connect(n(0), n(1)));
        var cleared = original.clearRoutePoints();
        assertEquals(original.areas(), cleared.areas());
        assertTrue(points(cleared).isEmpty());
        assertEquals(List.of(new RouteEdge(n(0), n(1))), cleared.graph().edges());
        assertEquals(List.of(n(0), n(1)), cleared.graph().shortestPath(cleared, n(0), n(1)));
        assertEquals(2, points(original).size());
    }

    @Test
    void inFlightRouteSurvivesSavingAndNeverCountsAsWork() {
        var state = WorkspaceState.initial(connected()).withTravel(false).next();
        assertEquals(List.of(-1, -2, 1), indices(state.remainingRoute()));
        state = state.advanceRoute();
        assertEquals(List.of(-2, 1), indices(state.remainingRoute()));
        assertEquals(1, state.navigationArea().volume());
        assertEquals(
                state,
                WorkspaceState.CODEC
                        .parse(
                                JsonOps.INSTANCE,
                                WorkspaceState.CODEC
                                        .encodeStart(JsonOps.INSTANCE, state)
                                        .getOrThrow())
                        .getOrThrow());
        assertEquals(state, state.countWork(1));
        assertTrue(indices(state.withTravel(false).remainingRoute()).isEmpty());
    }

    @Test
    void oldSavedPlansAndStatesLoadWithoutAGraph() {
        var json =
                JsonParser.parseString(
                        "{\"plan\":{\"dimension\":\"minecraft:overworld\",\"areas\":[{\"min\":[0,64,0],\"max\":[2,66,2]}]},\"active_index\":0,\"work_ticks\":73,\"travelling\":false}");
        var state = WorkspaceState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(73, state.workTicks());
        assertEquals(RouteGraph.EMPTY, state.plan().graph());
        assertTrue(indices(state.remainingRoute()).isEmpty());
    }

    @Test
    void outlinePickingIncludesAirNodesAndChoosesTheClosestOutline() {
        var plan = attach(plan(), new FixtureGraph(List.of(new BlockPos(5, 65, 1)), List.of()));
        assertEquals(
                n(-1),
                NodeSelection.pick(
                        plan,
                        new Vec3(3.5, 65.5, 1.5),
                        new Vec3(16, 65.5, 1.5),
                        true,
                        ResourceLocation.parse("minecraft:overworld")));
        assertEquals(
                n(1),
                NodeSelection.pick(
                        plan,
                        new Vec3(3.5, 65.5, 1.5),
                        new Vec3(16, 65.5, 1.5),
                        false,
                        ResourceLocation.parse("minecraft:overworld")));
        assertEquals(
                n(0),
                NodeSelection.pick(
                        plan,
                        new Vec3(1, 65, 1),
                        new Vec3(1, 66, 1),
                        true,
                        ResourceLocation.parse("minecraft:overworld")));
    }
}
