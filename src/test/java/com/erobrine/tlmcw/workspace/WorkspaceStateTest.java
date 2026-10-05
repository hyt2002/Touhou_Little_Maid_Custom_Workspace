package com.erobrine.tlmcw.workspace;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceStateTest {
    private static WorkspacePlan plan(int count) {
        var areas = new ArrayList<WorkArea>();
        for (int i = 0; i < count; i++) {
            areas.add(WorkArea.between(new BlockPos(i * 10, 64, 0), new BlockPos(i * 10 + 3, 67, 3)));
        }
        return new WorkspacePlan(ResourceLocation.parse("minecraft:overworld"), areas);
    }
    @Test void endpointOrderAndEveryFaceAreInclusive() {
        WorkArea area = WorkArea.between(new BlockPos(2, 5, -1), new BlockPos(-3, 2, -5));
        assertTrue(area.contains(new BlockPos(-3, 2, -5)));
        assertTrue(area.contains(new BlockPos(2, 5, -1)));
        assertFalse(area.contains(new BlockPos(3, 3, -3)));
        assertFalse(area.contains(new BlockPos(0, 6, -3)));
        assertFalse(area.contains(new BlockPos(0, 3, -6)));
        assertEquals(120, area.volume());
        assertEquals(3.0, area.bounds().maxX);
        assertEquals(6.0, area.bounds().maxY);
    }
    @Test void scanVisitsEachBlockOnceIncludingNegativeCoordinates() {
        var area = WorkArea.between(new BlockPos(-2, -4, -3), new BlockPos(1, -2, 0));
        var visited = new HashSet<BlockPos>();
        for (long i = 0; i < area.volume(); i++) {
            assertTrue(area.contains(area.candidate(i)));
            assertTrue(visited.add(area.candidate(i)));
        }
        assertEquals(area.volume(), visited.size());
        assertEquals(area.max(), area.candidate(area.volume() - 1));
    }
    @Test void floorOnlyBoxDoesNotAcceptFeetAboveIt() {
        var area = WorkArea.between(new BlockPos(0, 64, 0), new BlockPos(3, 64, 3));
        assertTrue(area.contains(new BlockPos(2, 64, 2)));
        assertFalse(area.contains(new BlockPos(2, 65, 2)));
        assertFalse(area.contains(new BlockPos(4, 64, 2)));
        assertFalse(area.contains(new BlockPos(2, 63, 2)));
    }
    @Test void verticallyAdjacentAreasDoNotOverlap() {
        var lower = WorkArea.between(new BlockPos(0, 64, 0), new BlockPos(3, 66, 3));
        var upper = WorkArea.between(new BlockPos(0, 67, 0), new BlockPos(3, 69, 3));
        assertTrue(lower.contains(new BlockPos(2, 66, 2)));
        assertFalse(upper.contains(new BlockPos(2, 66, 2)));
        assertFalse(lower.contains(new BlockPos(2, 67, 2)));
        assertTrue(upper.contains(new BlockPos(2, 67, 2)));
        assertEquals(lower.bounds().maxY, upper.bounds().minY);
    }
    @Test void travelNeverConsumesWorkTicks() {
        var state = WorkspaceState.initial(plan(2));
        for (int i = 0; i < 10000; i++) state = state.countWork(20);
        assertEquals(0, state.workTicks());
        assertEquals(0, state.activeIndex());
        assertTrue(state.travelling());
    }
    @Test void rotatesAtTheExactGameTickBudgetAndStartsTravelling() {
        var state = WorkspaceState.initial(plan(2)).withTravel(false);
        for (int i = 0; i < 19; i++) state = state.countWork(20);
        assertEquals(19, state.workTicks());
        assertEquals(0, state.activeIndex());
        state = state.countWork(20);
        assertEquals(1, state.activeIndex());
        assertEquals(0, state.workTicks());
        assertTrue(state.travelling());
        state = state.withTravel(false);
        for (int i = 0; i < 20; i++) state = state.countWork(20);
        assertEquals(0, state.activeIndex());
    }
    @Test void onlyTheActiveAreaAcceptsWorkTargets() {
        var state = WorkspaceState.initial(plan(2)).withTravel(false);
        assertTrue(state.activeArea().contains(new BlockPos(1, 64, 1)));
        assertFalse(state.activeArea().contains(new BlockPos(11, 64, 1)));
        state = state.next();
        assertFalse(state.activeArea().contains(new BlockPos(1, 64, 1)));
        assertTrue(state.activeArea().contains(new BlockPos(11, 64, 1)));
    }
    @Test void progressAndActiveIndexSurviveSerialization() {
        var state = new WorkspaceState(plan(3), 2, 1731, false);
        JsonElement json = WorkspaceState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        assertEquals(state, WorkspaceState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }
    @Test void singleAreaNeverTriggersATransferAndPlanIsAnIndependentSnapshot() {
        var mutable = new ArrayList<>(plan(1).areas());
        var plan = new WorkspacePlan(ResourceLocation.parse("minecraft:overworld"), mutable);
        mutable.clear();
        var state = WorkspaceState.initial(plan).withTravel(false);
        for (int i = 0; i < 100; i++) state = state.countWork(20);
        assertEquals(1, plan.areas().size());
        assertEquals(0, state.activeIndex());
        assertFalse(state.travelling());
        assertEquals(20, state.workTicks());
        assertThrows(UnsupportedOperationException.class, () -> plan.areas().clear());
    }
}
