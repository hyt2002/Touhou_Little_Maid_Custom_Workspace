package com.erobrine.tlmcw.workspace;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RoutePlannerTest {
    private static WorkArea box(int x) { return WorkArea.between(new BlockPos(x, 64, 0), new BlockPos(x + 2, 66, 2)); }
    private static WorkspacePlan plan() {
        return new WorkspacePlan(ResourceLocation.parse("minecraft:overworld"), List.of(box(0), box(10), box(20), box(30)))
                .withType(1, AreaType.IDLE).withType(2, AreaType.SLEEP);
    }
    @Test void markingChangesOnlyTypeAndRetainsNodeIdsAndEdges() {
        var graph = new RouteGraph(List.of(new BlockPos(6, 65, 1)), List.of()).connect(0, -1).connect(-1, 1);
        var original = plan().withGraph(graph);
        var changed = original.withType(0, original.type(0).next());
        assertEquals(AreaType.IDLE, changed.type(0));
        assertEquals(original.graph(), changed.graph());
        assertEquals(original.areas(), changed.areas());
        assertEquals(AreaType.WORK, changed.type(0).next().next());
        var removed = changed.removeNode(1, true);
        assertEquals(List.of(AreaType.IDLE, AreaType.SLEEP, AreaType.WORK), removed.areaTypes());
    }
    @Test void workRotationSkipsOtherAreaTypesAndRestPreservesItsProgress() {
        var state = WorkspaceState.initial(plan()).withTravel(false).countWork(1);
        assertEquals(3, state.activeIndex());
        state = state.withTravel(false).countWork(100);
        var rest = state.destination(AreaType.SLEEP, new RoutePlanner.Route(2, List.of(), 10, false)).withTravel(false);
        assertEquals(3, rest.workIndex());
        assertEquals(1, rest.workTicks());
        assertEquals(rest, rest.countWork(1));
        var resumed = rest.destination(AreaType.WORK, new RoutePlanner.Route(3, List.of(), 10, false)).withTravel(false);
        assertEquals(0, resumed.countWork(2).activeIndex());
    }
    @Test void typedDestinationRouteAndSeparateWorkCursorSurviveSaving() {
        var graph = new RouteGraph(List.of(new BlockPos(24, 65, 1)), List.of()).connect(3, -1).connect(-1, 2);
        var state = new WorkspaceState(plan().withGraph(graph), 3, 837, false)
                .destination(AreaType.SLEEP, new RoutePlanner.Route(2, List.of(-1, 2), 10, true));
        var restored = WorkspaceState.CODEC.parse(JsonOps.INSTANCE,
                WorkspaceState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow()).getOrThrow();
        assertEquals(state, restored);
        assertEquals(AreaType.SLEEP, restored.scheduleType());
        assertEquals(3, restored.workIndex());
        assertEquals(837, restored.workTicks());
    }
    @Test void legacyStateDefaultsToWorkTypesAndItsPreviousWorkCursor() {
        var json = JsonParser.parseString("{\"plan\":{\"dimension\":\"minecraft:overworld\",\"areas\":[{\"min\":[0,64,0],\"max\":[2,66,2]},{\"min\":[10,64,0],\"max\":[12,66,2]}]},\"active_index\":1,\"work_ticks\":73,\"travelling\":false}");
        var restored = WorkspaceState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(List.of(AreaType.WORK, AreaType.WORK), restored.plan().areaTypes());
        assertEquals(1, restored.workIndex());
        assertEquals(73, restored.workTicks());
    }
    @Test void nearestIdleCandidateUsesTotalRouteDistanceRatherThanStraightLine() {
        var graph = new RouteGraph(List.of(new BlockPos(0, 65, 30), new BlockPos(10, 65, 30), new BlockPos(6, 65, 1)), List.of())
                .connect(0, -1).connect(-1, -2).connect(-2, 1).connect(0, -3).connect(-3, 2);
        var plan = plan().withType(2, AreaType.IDLE).withGraph(graph);
        var route = RoutePlanner.nearest(plan, AreaType.IDLE, new Vec3(1.5, 65, 1.5), -1);
        assertEquals(2, route.destination());
        assertEquals(List.of(-3, 2), route.remaining());
    }
    @Test void aDisconnectedCloserAreaDoesNotBeatAConnectedCandidate() {
        var graph = new RouteGraph(List.of(new BlockPos(6, 65, 1)), List.of()).connect(0, -1).connect(-1, 2);
        var route = RoutePlanner.nearest(plan().withType(2, AreaType.IDLE).withGraph(graph), AreaType.IDLE,
                new Vec3(1.5, 65, 1.5), -1);
        assertEquals(2, route.destination());
        assertTrue(route.connected());
    }
    @Test void offGraphEntryIgnoresCloserPointsInOtherComponents() {
        var graph = new RouteGraph(List.of(new BlockPos(4, 65, 8), new BlockPos(6, 65, 8), new BlockPos(12, 65, 8)), List.of())
                .connect(-2, -3).connect(-3, 2);
        var route = RoutePlanner.to(plan().withGraph(graph), 2, new Vec3(3.5, 65, 8.5));
        assertEquals(List.of(-2, -3, 2), route.remaining());
        assertTrue(route.connected());
    }
    @Test void offGraphEntryUsesNearestConnectedPointBeforePlanningTheRest() {
        var graph = new RouteGraph(List.of(new BlockPos(3, 65, 10), new BlockPos(25, 65, 10), new BlockPos(3, 65, 70)), List.of())
                .connect(-1, -3).connect(-3, 3).connect(-2, 3);
        var route = RoutePlanner.to(plan().withGraph(graph), 3, new Vec3(3.5, 65, 12.5));
        assertEquals(List.of(-1, -3, 3), route.remaining());
    }
    @Test void missingRoutesFallBackToTheNearestDestination() {
        var route = RoutePlanner.nearest(plan().withType(2, AreaType.IDLE), AreaType.IDLE, new Vec3(15, 65, 8), -1);
        assertEquals(1, route.destination());
        assertFalse(route.connected());
        assertTrue(route.remaining().isEmpty());
    }
    @Test void plansWithOnlyRestAreasAndBulkClearingRetainTheirTypes() {
        var plan = new WorkspacePlan(ResourceLocation.parse("minecraft:overworld"), List.of(box(0))).withType(0, AreaType.IDLE);
        assertEquals(-1, WorkspaceState.initial(plan).workIndex());
        assertEquals(plan.areaTypes(), plan.clearRoutePoints().areaTypes());
        assertTrue(plan.clearWorkAreas().areaTypes().isEmpty());
    }
}
