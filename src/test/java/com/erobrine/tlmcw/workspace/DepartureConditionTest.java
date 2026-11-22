package com.erobrine.tlmcw.workspace;

import static com.erobrine.tlmcw.gametest.V2Fixtures.*;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class DepartureConditionTest {
    private static FrequencyMark mark(String item) {
        return new FrequencyMark(Optional.of(ResourceLocation.parse(item)), Optional.empty());
    }

    private static WorkspacePlan plan() {
        return fixturePlan(
                ResourceLocation.parse("minecraft:overworld"),
                List.of(
                        WorkArea.between(new BlockPos(0, 64, 0), new BlockPos(2, 66, 2)),
                        WorkArea.between(new BlockPos(8, 64, 0), new BlockPos(10, 66, 2))));
    }

    private static WirelessRedstoneCondition signal(boolean powered) {
        return new WirelessRedstoneCondition(
                mark("minecraft:diamond"), mark("minecraft:emerald"), powered);
    }

    @Test
    void readsExistingDelayWithoutChangingItsWireFormat() {
        var json = JsonParser.parseString("{\"type\":\"delay\",\"ticks\":600}");
        var delay = DepartureCondition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(new DelayCondition(600), delay);
        assertEquals(
                json, DepartureCondition.CODEC.encodeStart(JsonOps.INSTANCE, delay).getOrThrow());
    }

    @Test
    void mixedConditionsKeepIdentitiesProgressAndFrequencyDataThroughPlanAndStateCodecs() {
        var red =
                new FrequencyMark(
                        Optional.of(ResourceLocation.parse("minecraft:leather_helmet")),
                        Optional.of(0xEE3344));
        var signal = new WirelessRedstoneCondition(red, FrequencyMark.EMPTY, false);
        var entries =
                List.of(
                        new WorkScheduleEntry(n(0), signal),
                        new WorkScheduleEntry(n(1), new DelayCondition(123)));
        var configured = plan().withSchedule(new WorkSchedule(entries, true));
        var state = WorkspaceState.initial(configured).withTravel(false);
        var json = WorkspaceState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        assertEquals(state, WorkspaceState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(signal, entries.getFirst().duplicate().condition());
        assertNotEquals(entries.getFirst().id(), entries.getFirst().duplicate().id());
    }

    @Test
    void wirelessNeverFallsThroughToDelayAndOnlyCompletesAfterArrival() {
        var p =
                plan().withSchedule(
                                new WorkSchedule(
                                        List.of(
                                                new WorkScheduleEntry(n(0), signal(true)),
                                                new WorkScheduleEntry(n(1), new DelayCondition(3))),
                                        true));
        var travelling = WorkspaceState.initial(p);
        assertEquals(travelling, travelling.completeCondition());
        var waiting = travelling.withTravel(false);
        for (int i = 0; i < 10000; i++) assertSame(waiting, waiting.countWork(1));
        assertEquals(0, waiting.workTicks());
        var next = waiting.completeCondition();
        assertEquals(n(1), next.destinationId());
        assertEquals(p.schedule().entries().get(1).id(), next.entryId());
        assertTrue(next.travelling());
        var rest = waiting.suspend(AreaType.IDLE);
        assertEquals(rest, rest.completeCondition());
    }

    @Test
    void lastNonLoopingWirelessConditionFinishesOnceWithoutResettingConfiguration() {
        var p =
                plan().withSchedule(
                                new WorkSchedule(
                                        List.of(new WorkScheduleEntry(n(0), signal(false))),
                                        false));
        var state = WorkspaceState.initial(p).withTravel(false);
        var completed = state.completeCondition();
        assertTrue(completed.scheduleFinished());
        assertEquals(state.plan(), completed.plan());
        assertEquals(state.entryId(), completed.entryId());
        assertEquals(state.destinationId(), completed.destinationId());
        assertSame(completed, completed.completeCondition());
    }

    @Test
    void rejectsUnknownConditionsAndInvalidFrequencyColors() {
        assertTrue(
                DepartureCondition.CODEC
                        .parse(
                                JsonOps.INSTANCE,
                                JsonParser.parseString("{\"type\":\"unsupported\",\"ticks\":1}"))
                        .error()
                        .isPresent());
        assertThrows(
                IllegalArgumentException.class,
                () -> new FrequencyMark(Optional.empty(), Optional.of(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FrequencyMark(mark("minecraft:diamond").item(), Optional.of(-1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FrequencyMark(mark("minecraft:diamond").item(), Optional.of(0x1000000)));
        assertNotEquals(mark("minecraft:diamond"), mark("minecraft:emerald"));
        assertNotEquals(
                new WirelessRedstoneCondition(
                        mark("minecraft:diamond"), mark("minecraft:emerald"), true),
                new WirelessRedstoneCondition(
                        mark("minecraft:emerald"), mark("minecraft:diamond"), true));
    }
}
