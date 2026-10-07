package com.erobrine.tlmcw.workspace;

import static com.erobrine.tlmcw.gametest.V2Fixtures.*;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

class WorkScheduleTest {
    private static WorkspacePlan plan() {
        return fixturePlan(
                        ResourceLocation.parse("minecraft:overworld"),
                        List.of(
                                WorkArea.between(new BlockPos(0, 64, 0), new BlockPos(2, 66, 2)),
                                WorkArea.between(new BlockPos(10, 64, 0), new BlockPos(12, 66, 2)),
                                WorkArea.between(new BlockPos(20, 64, 0), new BlockPos(22, 66, 2)),
                                WorkArea.between(new BlockPos(30, 64, 0), new BlockPos(32, 66, 2))))
                .withType(n(3), AreaType.SLEEP);
    }

    private static WorkScheduleEntry entry(int area, int ticks) {
        return new WorkScheduleEntry(n(area), new DelayCondition(ticks));
    }

    @Test
    void namesArePlainBoundedTextAndDoNotAlterGraphOrTypes() {
        var original =
                attach(
                        plan(),
                        new FixtureGraph(List.of(new BlockPos(6, 65, 1)), List.of())
                                .connect(n(0), n(-1))
                                .connect(n(-1), n(2)));
        var named = original.withName(n(0), "  菜田\n§\tA  ");
        assertEquals("菜田A", named.name(n(0)));
        assertEquals(original.graph(), named.graph());
        assertEquals(types(original), types(named));
        assertEquals(64, WorkspacePlan.cleanName("田".repeat(65)).length());
        assertEquals(63, WorkspacePlan.cleanName("田".repeat(63) + "🌿").length());
        assertEquals("", named.withName(n(0), "  ").name(n(0)));
    }

    @Test
    void legacyPlansUseUnnamedAreasAndAutomaticRotation() {
        var json =
                JsonParser.parseString(
                        "{\"dimension\":\"minecraft:overworld\",\"areas\":[{\"min\":[0,64,0],\"max\":[2,66,2]}]}");
        var legacy = WorkspacePlan.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(List.of(""), names(legacy));
        assertEquals(WorkSchedule.EMPTY, legacy.schedule());
    }

    @Test
    void startsAtFirstScheduledDestinationAndUsesEachDelayRatherThanGlobalConfig() {
        var plan = plan().withSchedule(new WorkSchedule(List.of(entry(2, 3), entry(0, 7)), true));
        var state = WorkspaceState.initial(plan).withTravel(false);
        assertEquals(2, destination(state));
        state = state.countWork(2400).countWork(2400);
        assertEquals(2, destination(state));
        assertEquals(2, state.workTicks());
        state = state.countWork(2400);
        assertEquals(0, destination(state));
        assertEquals(1, entryPosition(state));
        assertTrue(state.travelling());
        state = state.withTravel(false);
        for (int i = 0; i < 6; i++) state = state.countWork(1);
        assertEquals(0, destination(state));
        state = state.countWork(1);
        assertEquals(2, destination(state));
        assertEquals(0, entryPosition(state));
    }

    @Test
    void repeatedDestinationEntriesHaveIndependentCursorsAndDelays() {
        var state =
                WorkspaceState.initial(
                                plan().withSchedule(
                                                new WorkSchedule(
                                                        List.of(
                                                                entry(0, 1),
                                                                entry(0, 2),
                                                                entry(2, 3)),
                                                        true)))
                        .withTravel(false)
                        .countWork(2400);
        assertEquals(0, destination(state));
        assertEquals(1, entryPosition(state));
        assertEquals(0, state.workTicks());
        assertTrue(state.travelling());
        assertEquals(state, state.countWork(2400));
        state = state.withTravel(false).countWork(2400);
        assertEquals(1, entryPosition(state));
        state = state.countWork(2400);
        assertEquals(2, destination(state));
        assertEquals(2, entryPosition(state));
    }

    @Test
    void sleepPreservesScheduleCursorAndResumesRemainingDelay() {
        var state =
                WorkspaceState.initial(
                                plan().withSchedule(
                                                new WorkSchedule(
                                                        List.of(entry(2, 4), entry(0, 7)), true)))
                        .withTravel(false)
                        .countWork(2400)
                        .countWork(2400);
        var asleep =
                state.destination(AreaType.SLEEP, fixtureRoute(3, List.of(), 10, false))
                        .withTravel(false);
        assertEquals(asleep, asleep.countWork(1));
        assertEquals(2, asleep.workTicks());
        var resumed =
                asleep.destination(
                                AreaType.WORK,
                                fixtureRoute(workDestination(asleep), List.of(), 10, false))
                        .withTravel(false);
        assertEquals(2, destination(resumed.countWork(1)));
        assertEquals(0, destination(resumed.countWork(1).countWork(1)));
    }

    @Test
    void nonCyclicScheduleFinishesAtLastAreaAndDoesNotRestart() {
        var state =
                WorkspaceState.initial(
                                plan().withSchedule(
                                                new WorkSchedule(
                                                        List.of(entry(2, 1), entry(0, 2)), false)))
                        .withTravel(false)
                        .countWork(2400)
                        .withTravel(false)
                        .countWork(2400)
                        .countWork(2400);
        assertTrue(state.scheduleFinished());
        assertEquals(0, destination(state));
        assertFalse(state.travelling());
        assertEquals(2, state.workTicks());
        assertEquals(state, state.countWork(2400));
        assertFalse(state.hasNextWorkDestination());
    }

    @Test
    void singleCyclicEntryStaysInPlaceAndCapsItsProgress() {
        var state =
                WorkspaceState.initial(
                                plan().withSchedule(new WorkSchedule(List.of(entry(2, 2)), true)))
                        .withTravel(false);
        for (int i = 0; i < 10; i++) state = state.countWork(2400);
        assertEquals(2, destination(state));
        assertEquals(2, state.workTicks());
        assertFalse(state.travelling());
        assertFalse(state.scheduleFinished());
    }

    @Test
    void zeroDelayStillWaitsForAnEligibleWorkTickAfterArrival() {
        var state =
                WorkspaceState.initial(
                        plan().withSchedule(
                                        new WorkSchedule(
                                                List.of(entry(0, 0), entry(2, 1)), false)));
        assertEquals(state, state.countWork(2400));
        state = state.withTravel(false).countWork(2400);
        assertEquals(2, destination(state));
        state = state.withTravel(false).countWork(2400);
        assertTrue(state.scheduleFinished());
    }

    @Test
    void deletingAreaKeepsOtherNamesIdentitiesAndScheduleReferences() {
        var plan =
                plan().withName(n(0), "A")
                        .withName(n(1), "B")
                        .withName(n(2), "C")
                        .withSchedule(
                                new WorkSchedule(
                                        List.of(entry(2, 2), entry(1, 1), entry(0, 3), entry(2, 4)),
                                        false));
        var removed = plan.removeNode(n(1), true);
        assertEquals(List.of("A", "C", ""), names(removed));
        assertEquals(
                List.of(
                        plan.schedule().entries().get(0),
                        plan.schedule().entries().get(2),
                        plan.schedule().entries().get(3)),
                removed.schedule().entries());
        assertFalse(removed.schedule().cyclic());
    }

    @Test
    void retaggingAndClearingKeepOnlyValidScheduleDestinations() {
        var plan =
                plan().withName(n(2), "C")
                        .withSchedule(new WorkSchedule(List.of(entry(2, 2), entry(0, 3)), true));
        var retagged = plan.withType(n(2), AreaType.IDLE);
        assertEquals(List.of(plan.schedule().entries().get(1)), retagged.schedule().entries());
        assertEquals("C", retagged.name(n(2)));
        assertEquals(plan.schedule(), plan.clearRoutePoints().schedule());
        assertEquals(names(plan), names(plan.clearRoutePoints()));
        assertFalse(plan.clearWorkAreas().schedule().custom());
        assertTrue(names(plan.clearWorkAreas()).isEmpty());
    }

    @Test
    void invalidOrNonWorkDestinationsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> plan().withSchedule(new WorkSchedule(List.of(entry(3, 1)), true)));
        assertThrows(
                IllegalArgumentException.class,
                () -> plan().withSchedule(new WorkSchedule(List.of(entry(7, 1)), true)));
        assertThrows(IllegalArgumentException.class, () -> new DelayCondition(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DelayCondition(DelayCondition.MAX_TICKS + 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkSchedule(Collections.nCopies(129, entry(0, 1)), true));
        assertTrue(
                DelayCondition.CODEC
                        .parse(
                                JsonOps.INSTANCE,
                                JsonParser.parseString("{\"type\":\"unsupported\",\"ticks\":1}"))
                        .error()
                        .isPresent());
    }

    @Test
    void namedPlanAndRepeatedEntryProgressRoundTripThroughCodec() {
        var plan =
                plan().withName(n(0), "种植一号")
                        .withName(n(2), "树场")
                        .withSchedule(
                                new WorkSchedule(
                                        List.of(entry(0, 3), entry(0, 7), entry(2, 2)), false));
        var state =
                WorkspaceState.initial(plan)
                        .withTravel(false)
                        .countWork(1)
                        .countWork(1)
                        .countWork(1)
                        .withTravel(false)
                        .countWork(1)
                        .destination(AreaType.SLEEP, fixtureRoute(3, List.of(), 10, false));
        var restored =
                WorkspaceState.CODEC
                        .parse(
                                JsonOps.INSTANCE,
                                WorkspaceState.CODEC
                                        .encodeStart(JsonOps.INSTANCE, state)
                                        .getOrThrow())
                        .getOrThrow();
        assertEquals(state, restored);
        assertEquals(1, entryPosition(restored));
        assertEquals(1, restored.workTicks());
        var finished = fixtureState(plan, 2, 2, false, List.of(), AreaType.WORK, 2, 2, true);
        assertEquals(
                finished,
                WorkspaceState.CODEC
                        .parse(
                                JsonOps.INSTANCE,
                                WorkspaceState.CODEC
                                        .encodeStart(JsonOps.INSTANCE, finished)
                                        .getOrThrow())
                        .getOrThrow());
    }
}
