package com.erobrine.tlmcw.workspace;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WorkScheduleTest {
    private static WorkspacePlan plan() {
        return new WorkspacePlan(ResourceLocation.parse("minecraft:overworld"), List.of(
                WorkArea.between(new BlockPos(0, 64, 0), new BlockPos(2, 66, 2)),
                WorkArea.between(new BlockPos(10, 64, 0), new BlockPos(12, 66, 2)),
                WorkArea.between(new BlockPos(20, 64, 0), new BlockPos(22, 66, 2)),
                WorkArea.between(new BlockPos(30, 64, 0), new BlockPos(32, 66, 2))))
                .withType(3, AreaType.SLEEP);
    }
    private static WorkScheduleEntry entry(int area, int ticks) { return new WorkScheduleEntry(area, new DelayCondition(ticks)); }
    @Test void namesArePlainBoundedTextAndDoNotAlterGraphOrTypes() {
        var original = plan().withGraph(new RouteGraph(List.of(new BlockPos(6, 65, 1)), List.of()).connect(0, -1).connect(-1, 2));
        var named = original.withName(0, "  菜田\n§\tA  ");
        assertEquals("菜田A", named.name(0));
        assertEquals(original.graph(), named.graph()); assertEquals(original.areaTypes(), named.areaTypes());
        assertEquals(64, WorkspacePlan.cleanName("田".repeat(65)).length());
        assertEquals(63, WorkspacePlan.cleanName("田".repeat(63) + "🌿").length());
        assertEquals("", named.withName(0, "  ").name(0));
    }
    @Test void legacyPlansUseUnnamedAreasAndAutomaticRotation() {
        var json = JsonParser.parseString("{\"dimension\":\"minecraft:overworld\",\"areas\":[{\"min\":[0,64,0],\"max\":[2,66,2]}]}");
        var legacy = WorkspacePlan.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(List.of(""), legacy.areaNames()); assertEquals(WorkSchedule.EMPTY, legacy.schedule());
    }
    @Test void startsAtFirstScheduledDestinationAndUsesEachDelayRatherThanGlobalConfig() {
        var plan = plan().withSchedule(new WorkSchedule(List.of(entry(2, 3), entry(0, 7)), true));
        var state = WorkspaceState.initial(plan).withTravel(false);
        assertEquals(2, state.activeIndex());
        state = state.countWork(2400).countWork(2400);
        assertEquals(2, state.activeIndex()); assertEquals(2, state.workTicks());
        state = state.countWork(2400);
        assertEquals(0, state.activeIndex()); assertEquals(1, state.scheduleIndex()); assertTrue(state.travelling());
        state = state.withTravel(false);
        for (int i = 0; i < 6; i++) state = state.countWork(1);
        assertEquals(0, state.activeIndex());
        state = state.countWork(1);
        assertEquals(2, state.activeIndex()); assertEquals(0, state.scheduleIndex());
    }
    @Test void repeatedDestinationEntriesHaveIndependentCursorsAndDelays() {
        var state = WorkspaceState.initial(plan().withSchedule(new WorkSchedule(List.of(entry(0, 1), entry(0, 2), entry(2, 3)), true)))
                .withTravel(false).countWork(2400);
        assertEquals(0, state.activeIndex()); assertEquals(1, state.scheduleIndex()); assertEquals(0, state.workTicks());
        assertTrue(state.travelling()); assertEquals(state, state.countWork(2400));
        state = state.withTravel(false).countWork(2400);
        assertEquals(1, state.scheduleIndex());
        state = state.countWork(2400);
        assertEquals(2, state.activeIndex()); assertEquals(2, state.scheduleIndex());
    }
    @Test void sleepPreservesScheduleCursorAndResumesRemainingDelay() {
        var state = WorkspaceState.initial(plan().withSchedule(new WorkSchedule(List.of(entry(2, 4), entry(0, 7)), true)))
                .withTravel(false).countWork(2400).countWork(2400);
        var asleep = state.destination(AreaType.SLEEP, new RoutePlanner.Route(3, List.of(), 10, false)).withTravel(false);
        assertEquals(asleep, asleep.countWork(1)); assertEquals(2, asleep.workTicks());
        var resumed = asleep.destination(AreaType.WORK, new RoutePlanner.Route(asleep.workIndex(), List.of(), 10, false)).withTravel(false);
        assertEquals(2, resumed.countWork(1).activeIndex());
        assertEquals(0, resumed.countWork(1).countWork(1).activeIndex());
    }
    @Test void nonCyclicScheduleFinishesAtLastAreaAndDoesNotRestart() {
        var state = WorkspaceState.initial(plan().withSchedule(new WorkSchedule(List.of(entry(2, 1), entry(0, 2)), false)))
                .withTravel(false).countWork(2400).withTravel(false).countWork(2400).countWork(2400);
        assertTrue(state.scheduleFinished()); assertEquals(0, state.activeIndex()); assertFalse(state.travelling());
        assertEquals(2, state.workTicks()); assertEquals(state, state.countWork(2400));
        assertFalse(state.hasNextWorkDestination());
    }
    @Test void singleCyclicEntryStaysInPlaceAndCapsItsProgress() {
        var state = WorkspaceState.initial(plan().withSchedule(new WorkSchedule(List.of(entry(2, 2)), true))).withTravel(false);
        for (int i = 0; i < 10; i++) state = state.countWork(2400);
        assertEquals(2, state.activeIndex()); assertEquals(2, state.workTicks()); assertFalse(state.travelling()); assertFalse(state.scheduleFinished());
    }
    @Test void zeroDelayStillWaitsForAnEligibleWorkTickAfterArrival() {
        var state = WorkspaceState.initial(plan().withSchedule(new WorkSchedule(List.of(entry(0, 0), entry(2, 1)), false)));
        assertEquals(state, state.countWork(2400));
        state = state.withTravel(false).countWork(2400);
        assertEquals(2, state.activeIndex());
        state = state.withTravel(false).countWork(2400);
        assertTrue(state.scheduleFinished());
    }
    @Test void deletingAreaRemapsNamesAndEveryScheduleReference() {
        var plan = plan().withName(0, "A").withName(1, "B").withName(2, "C")
                .withSchedule(new WorkSchedule(List.of(entry(2, 2), entry(1, 1), entry(0, 3), entry(2, 4)), false));
        var removed = plan.removeNode(1, true);
        assertEquals(List.of("A", "C", ""), removed.areaNames());
        assertEquals(List.of(entry(1, 2), entry(0, 3), entry(1, 4)), removed.schedule().entries());
        assertFalse(removed.schedule().cyclic());
    }
    @Test void retaggingAndClearingKeepOnlyValidScheduleDestinations() {
        var plan = plan().withName(2, "C").withSchedule(new WorkSchedule(List.of(entry(2, 2), entry(0, 3)), true));
        var retagged = plan.withType(2, AreaType.IDLE);
        assertEquals(List.of(entry(0, 3)), retagged.schedule().entries()); assertEquals("C", retagged.name(2));
        assertEquals(plan.schedule(), plan.clearRoutePoints().schedule()); assertEquals(plan.areaNames(), plan.clearRoutePoints().areaNames());
        assertFalse(plan.clearWorkAreas().schedule().custom()); assertTrue(plan.clearWorkAreas().areaNames().isEmpty());
    }
    @Test void invalidOrNonWorkDestinationsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> plan().withSchedule(new WorkSchedule(List.of(entry(3, 1)), true)));
        assertThrows(IllegalArgumentException.class, () -> plan().withSchedule(new WorkSchedule(List.of(entry(7, 1)), true)));
        assertThrows(IllegalArgumentException.class, () -> new DelayCondition(-1));
        assertThrows(IllegalArgumentException.class, () -> new DelayCondition(DelayCondition.MAX_TICKS + 1));
        assertThrows(IllegalArgumentException.class, () -> new WorkSchedule(Collections.nCopies(129, entry(0, 1)), true));
        assertTrue(DelayCondition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\":\"unsupported\",\"ticks\":1}")).error().isPresent());
    }
    @Test void namedPlanAndRepeatedEntryProgressRoundTripThroughCodec() {
        var plan = plan().withName(0, "种植一号").withName(2, "树场")
                .withSchedule(new WorkSchedule(List.of(entry(0, 3), entry(0, 7), entry(2, 2)), false));
        var state = WorkspaceState.initial(plan).withTravel(false).countWork(1).countWork(1).countWork(1)
                .withTravel(false).countWork(1).destination(AreaType.SLEEP, new RoutePlanner.Route(3, List.of(), 10, false));
        var restored = WorkspaceState.CODEC.parse(JsonOps.INSTANCE, WorkspaceState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow()).getOrThrow();
        assertEquals(state, restored); assertEquals(1, restored.scheduleIndex()); assertEquals(1, restored.workTicks());
        var finished = new WorkspaceState(plan, 2, 2, false, List.of(), AreaType.WORK, 2, 2, true);
        assertEquals(finished, WorkspaceState.CODEC.parse(JsonOps.INSTANCE, WorkspaceState.CODEC.encodeStart(JsonOps.INSTANCE, finished).getOrThrow()).getOrThrow());
    }
}
