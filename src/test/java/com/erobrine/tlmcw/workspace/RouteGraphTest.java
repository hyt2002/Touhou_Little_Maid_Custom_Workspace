package com.erobrine.tlmcw.workspace;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RouteGraphTest {
    private static WorkspacePlan plan() {
        return new WorkspacePlan(ResourceLocation.parse("minecraft:overworld"), List.of(
                WorkArea.between(new BlockPos(0, 64, 0), new BlockPos(2, 66, 2)),
                WorkArea.between(new BlockPos(12, 64, 0), new BlockPos(14, 66, 2))));
    }
    private static WorkspacePlan connected() {
        RouteGraph graph = new RouteGraph(List.of(new BlockPos(4, 65, 1), new BlockPos(9, 65, 1)), List.of())
                .connect(0, -1).connect(-1, -2).connect(-2, 1);
        return plan().withGraph(graph);
    }
    @Test void shortestRouteUsesDistanceRatherThanFewestNodesAndWorksInBothDirections() {
        var graph = new RouteGraph(List.of(new BlockPos(6, 65, 50), new BlockPos(4, 65, 1), new BlockPos(9, 65, 1)), List.of())
                .connect(0, -1).connect(-1, 1).connect(0, -2).connect(-2, -3).connect(-3, 1);
        var plan = plan().withGraph(graph);
        assertEquals(List.of(0, -2, -3, 1), graph.shortestPath(plan, 0, 1));
        assertEquals(List.of(1, -3, -2, 0), graph.shortestPath(plan, 1, 0));
    }
    @Test void duplicateEdgesAndCyclesDoNotChangeTheRoute() {
        var plan = connected();
        var graph = plan.graph().connect(-1, 0).connect(-2, -1).connect(-1, -1);
        assertEquals(plan.graph(), graph);
        assertEquals(List.of(0, -1, -2, 1), graph.shortestPath(plan, 0, 1));
    }
    @Test void disconnectedGraphFallsBackToDirectNavigation() {
        var plan = plan().withGraph(new RouteGraph(List.of(new BlockPos(4, 65, 1)), List.of()).connect(0, -1));
        assertTrue(plan.graph().shortestPath(plan, 0, 1).isEmpty());
        var next = WorkspaceState.initial(plan).withTravel(false).next();
        assertTrue(next.remainingRoute().isEmpty());
        assertEquals(plan.areas().get(1), next.navigationArea());
    }
    @Test void deletingAWorkAreaRemapsOnlyTheRemainingWorkNodeIndices() {
        var plan = connected().addArea(WorkArea.between(new BlockPos(20, 64, 0), new BlockPos(22, 66, 2)));
        plan = plan.withGraph(plan.graph().connect(1, 2)).removeNode(0, true);
        assertEquals(2, plan.areas().size());
        assertEquals(List.of(new RouteEdge(-1, -2), new RouteEdge(-2, 0), new RouteEdge(0, 1)), plan.graph().edges());
        assertEquals(2, plan.graph().points().size());
    }
    @Test void deletingAWaypointDropsItsEdgesAndRemapsTheOtherPoints() {
        var plan = connected().removeNode(-1, false);
        assertEquals(List.of(new BlockPos(9, 65, 1)), plan.graph().points());
        assertEquals(List.of(new RouteEdge(-1, 1)), plan.graph().edges());
        assertEquals(2, plan.areas().size());
        assertTrue(plan.graph().shortestPath(plan, 0, 1).isEmpty());
    }
    @Test void clearingWorkAreasKeepsWaypointIndicesConnectionsAndSavedData() {
        var original = connected().withGraph(connected().graph().connect(0, 1));
        var cleared = original.clearWorkAreas();
        assertTrue(cleared.areas().isEmpty());
        assertEquals(original.graph().points(), cleared.graph().points());
        assertEquals(List.of(new RouteEdge(-1, -2)), cleared.graph().edges());
        assertEquals(List.of(-1, -2), cleared.graph().shortestPath(cleared, -1, -2));
        assertEquals(cleared, WorkspacePlan.CODEC.parse(JsonOps.INSTANCE,
                WorkspacePlan.CODEC.encodeStart(JsonOps.INSTANCE, cleared).getOrThrow()).getOrThrow());
        assertEquals(2, original.areas().size());
    }
    @Test void clearingRoutePointsKeepsWorkAreasAndTheirDirectConnections() {
        var original = connected().withGraph(connected().graph().connect(0, 1));
        var cleared = original.clearRoutePoints();
        assertEquals(original.areas(), cleared.areas());
        assertTrue(cleared.graph().points().isEmpty());
        assertEquals(List.of(new RouteEdge(0, 1)), cleared.graph().edges());
        assertEquals(List.of(0, 1), cleared.graph().shortestPath(cleared, 0, 1));
        assertEquals(2, original.graph().points().size());
    }
    @Test void inFlightRouteSurvivesSavingAndNeverCountsAsWork() {
        var state = WorkspaceState.initial(connected()).withTravel(false).next();
        assertEquals(List.of(-1, -2, 1), state.remainingRoute());
        state = state.advanceRoute();
        assertEquals(List.of(-2, 1), state.remainingRoute());
        assertEquals(1, state.navigationArea().volume());
        assertEquals(state, WorkspaceState.CODEC.parse(JsonOps.INSTANCE,
                WorkspaceState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow()).getOrThrow());
        assertEquals(state, state.countWork(1));
        assertTrue(state.withTravel(false).remainingRoute().isEmpty());
    }
    @Test void oldSavedPlansAndStatesLoadWithoutAGraph() {
        var json = JsonParser.parseString("{\"plan\":{\"dimension\":\"minecraft:overworld\",\"areas\":[{\"min\":[0,64,0],\"max\":[2,66,2]}]},\"active_index\":0,\"work_ticks\":73,\"travelling\":false}");
        var state = WorkspaceState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(73, state.workTicks());
        assertEquals(RouteGraph.EMPTY, state.plan().graph());
        assertTrue(state.remainingRoute().isEmpty());
    }
    @Test void outlinePickingIncludesAirNodesAndChoosesTheClosestOutline() {
        var plan = plan().withGraph(new RouteGraph(List.of(new BlockPos(5, 65, 1)), List.of()));
        assertEquals(-1, NodeSelection.pick(plan, new Vec3(3.5, 65.5, 1.5), new Vec3(16, 65.5, 1.5), true));
        assertEquals(1, NodeSelection.pick(plan, new Vec3(3.5, 65.5, 1.5), new Vec3(16, 65.5, 1.5), false));
        assertEquals(0, NodeSelection.pick(plan, new Vec3(1, 65, 1), new Vec3(1, 66, 1), true));
    }
}
