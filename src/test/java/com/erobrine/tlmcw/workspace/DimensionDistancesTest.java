package com.erobrine.tlmcw.workspace;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;

import java.util.List;

class DimensionDistancesTest {
    private final ResourceLocation overworld = ResourceLocation.parse("minecraft:overworld"),
            nether = ResourceLocation.parse("minecraft:the_nether"),
            end = ResourceLocation.parse("minecraft:the_end");

    @Test
    void defaultsAreOneAndSameDimensionIsAlwaysUnscaled() {
        var table = DimensionDistances.table(List.of());
        assertEquals(1, table.multiplier(overworld, nether));
        assertEquals(5, table.distance(Vec3.ZERO, overworld, new Vec3(3, 0, 4), nether));
        assertEquals(1, table.multiplier(overworld, overworld));
    }

    @Test
    void eachUndirectedPairHasItsOwnRateAndUnspecifiedPairsKeepOne() {
        var table =
                DimensionDistances.table(
                        List.of(
                                "minecraft:overworld|minecraft:the_nether=8",
                                "minecraft:the_end|minecraft:overworld=0.25"));
        assertEquals(8, table.multiplier(overworld, nether));
        assertEquals(8, table.multiplier(nether, overworld));
        assertEquals(0.25, table.multiplier(overworld, end));
        assertEquals(1, table.multiplier(nether, end));
        assertEquals(40, table.distance(Vec3.ZERO, overworld, new Vec3(3, 0, 4), nether));
        assertEquals(5, table.distance(Vec3.ZERO, overworld, new Vec3(3, 0, 4), overworld));
    }

    @Test
    void invalidOrConflictingEntriesAreRejected() {
        for (String s :
                List.of(
                        "x",
                        "minecraft:overworld|minecraft:the_nether=0",
                        "minecraft:overworld|minecraft:the_nether=-1",
                        "minecraft:overworld|minecraft:the_nether=NaN",
                        "minecraft:overworld|minecraft:the_nether=Infinity",
                        "minecraft:overworld|minecraft:overworld=8",
                        "Bad Name|minecraft:the_nether=1"))
            assertFalse(DimensionDistances.validEntry(s), s);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        DimensionDistances.table(
                                List.of(
                                        "minecraft:overworld|minecraft:the_nether=8",
                                        "minecraft:the_nether|minecraft:overworld=2")));
    }

    @Test
    void configuredRatesChangeDijkstraAndCandidateSelectionThroughOneSharedTable() {
        var a =
                WorkspaceNode.area(
                        overworld,
                        WorkArea.between(
                                new net.minecraft.core.BlockPos(0, 64, 0),
                                new net.minecraft.core.BlockPos(2, 66, 2)));
        var b =
                WorkspaceNode.area(
                        overworld,
                        WorkArea.between(
                                new net.minecraft.core.BlockPos(20, 64, 0),
                                new net.minecraft.core.BlockPos(22, 66, 2)));
        var point = WorkspaceNode.waypoint(nether, new net.minecraft.core.BlockPos(10, 65, 1));
        var graph =
                RouteGraph.EMPTY
                        .connect(a.id(), b.id())
                        .connect(a.id(), point.id())
                        .connect(point.id(), b.id());
        var plan =
                new WorkspacePlan(
                        java.util.UUID.randomUUID(),
                        List.of(a, b, point),
                        graph,
                        WorkSchedule.EMPTY);
        var cheap =
                DimensionDistances.table(List.of("minecraft:overworld|minecraft:the_nether=0.1"));
        var expensive =
                DimensionDistances.table(List.of("minecraft:overworld|minecraft:the_nether=8"));
        assertEquals(
                List.of(a.id(), point.id(), b.id()),
                graph.shortestPath(plan, a.id(), b.id(), null, cheap));
        assertEquals(
                List.of(a.id(), b.id()), graph.shortestPath(plan, a.id(), b.id(), null, expensive));
        assertEquals(
                List.of(point.id(), b.id()),
                RoutePlanner.to(plan, b.id(), a.bounds().bounds().getCenter(), overworld, cheap)
                        .remaining());
        assertEquals(
                List.of(b.id()),
                RoutePlanner.to(plan, b.id(), a.bounds().bounds().getCenter(), overworld, expensive)
                        .remaining());
        var idle = plan.withType(a.id(), AreaType.IDLE).withType(b.id(), AreaType.IDLE);
        assertEquals(
                b.id(),
                RoutePlanner.nearest(
                                idle,
                                AreaType.IDLE,
                                a.bounds().bounds().getCenter(),
                                a.id(),
                                overworld,
                                cheap)
                        .destination());
    }

    @Test
    void ratesChangeWhichRestAreaHasTheShortestTotalRoute() {
        var start = WorkspaceNode.waypoint(overworld, new net.minecraft.core.BlockPos(0, 65, 0));
        var local =
                WorkspaceNode.area(
                                overworld,
                                WorkArea.between(
                                        new net.minecraft.core.BlockPos(20, 65, 0),
                                        new net.minecraft.core.BlockPos(20, 65, 0)))
                        .withType(AreaType.IDLE);
        var foreign =
                WorkspaceNode.area(
                                nether,
                                WorkArea.between(
                                        new net.minecraft.core.BlockPos(10, 65, 0),
                                        new net.minecraft.core.BlockPos(10, 65, 0)))
                        .withType(AreaType.IDLE);
        var plan =
                new WorkspacePlan(
                        java.util.UUID.randomUUID(),
                        List.of(start, local, foreign),
                        RouteGraph.EMPTY
                                .connect(start.id(), local.id())
                                .connect(start.id(), foreign.id()),
                        WorkSchedule.EMPTY);
        var feet = start.bounds().bounds().getCenter();
        var cheap =
                DimensionDistances.table(List.of("minecraft:overworld|minecraft:the_nether=0.1"));
        var expensive =
                DimensionDistances.table(List.of("minecraft:overworld|minecraft:the_nether=8"));
        var cheapRoute = RoutePlanner.nearest(plan, AreaType.IDLE, feet, null, overworld, cheap);
        var expensiveRoute =
                RoutePlanner.nearest(plan, AreaType.IDLE, feet, null, overworld, expensive);
        assertEquals(foreign.id(), cheapRoute.destination());
        assertEquals(1, cheapRoute.distance());
        assertEquals(local.id(), expensiveRoute.destination());
        assertEquals(20, expensiveRoute.distance());
    }
}
