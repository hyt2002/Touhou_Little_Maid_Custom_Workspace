package com.erobrine.tlmcw.workspace;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceDataVersionsTest {
    private static final String LEGACY_PLAN = """
            {"dimension":"minecraft:overworld","areas":[{"min":[0,64,0],"max":[3,67,3]}]}
            """;

    private static WorkspaceState progress() {
        var plan = new WorkspacePlan(ResourceLocation.parse("minecraft:overworld"), List.of(
                WorkArea.between(new BlockPos(0, 64, 0), new BlockPos(3, 67, 3)),
                WorkArea.between(new BlockPos(10, 64, 0), new BlockPos(13, 67, 3)),
                WorkArea.between(new BlockPos(20, 64, 0), new BlockPos(23, 67, 3)),
                WorkArea.between(new BlockPos(30, 64, 0), new BlockPos(33, 67, 3))))
                .withType(2, AreaType.IDLE).withType(3, AreaType.SLEEP)
                .withName(0, "菜田").withName(1, "果园").withName(2, "庭院").withName(3, "卧室")
                .withGraph(new RouteGraph(List.of(new BlockPos(6, 65, 5)), List.of()).connect(0, -1).connect(-1, 1))
                .withSchedule(new WorkSchedule(List.of(
                        new WorkScheduleEntry(1, new DelayCondition(600)),
                        new WorkScheduleEntry(0, new DelayCondition(120))), true));
        return new WorkspaceState(plan, 1, 73, true, List.of(-1, 1), AreaType.WORK, 1, 0, false);
    }

    @Test void jsonAndNbtContainIndependentPlanAndMaidVersions() {
        var state = progress();
        JsonObject json = WorkspaceState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow().getAsJsonObject();
        assertEquals(WorkspaceDataVersions.MAID_STATE_VERSION, json.get("data_version").getAsInt());
        assertEquals(WorkspaceDataVersions.PLAN_VERSION, json.getAsJsonObject("plan").get("data_version").getAsInt());
        assertEquals(state, WorkspaceState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        CompoundTag nbt = (CompoundTag) WorkspaceState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
        assertEquals(WorkspaceDataVersions.MAID_STATE_VERSION, nbt.getInt("data_version"));
        assertEquals(WorkspaceDataVersions.PLAN_VERSION, nbt.getCompound("plan").getInt("data_version"));
        assertEquals(state, WorkspaceState.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow());
    }

    @Test void unversionedFirstReleasePlanKeepsItsDefaultsAndGainsVersionOnSave() {
        var legacy = JsonParser.parseString(LEGACY_PLAN);
        WorkspacePlan loaded = WorkspacePlan.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertEquals(1, loaded.areas().size());
        assertEquals(List.of(AreaType.WORK), loaded.areaTypes());
        assertEquals(List.of(""), loaded.areaNames());
        assertEquals(RouteGraph.EMPTY, loaded.graph());
        assertEquals(WorkSchedule.EMPTY, loaded.schedule());
        var saved = WorkspacePlan.CODEC.encodeStart(JsonOps.INSTANCE, loaded).getOrThrow().getAsJsonObject();
        assertEquals(WorkspaceDataVersions.PLAN_VERSION, saved.get("data_version").getAsInt());
        assertFalse(legacy.getAsJsonObject().has("data_version"), "Migration mutated the caller's stored object");
    }

    @Test void unversionedFirstReleaseMaidKeepsActiveAreaAndGameTickProgress() {
        var legacy = JsonParser.parseString("""
                {"plan":%s,"active_index":0,"work_ticks":837,"travelling":false}
                """.formatted(LEGACY_PLAN));
        WorkspaceState loaded = WorkspaceState.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertEquals(837, loaded.workTicks());
        assertEquals(0, loaded.activeIndex());
        assertEquals(0, loaded.workIndex());
        assertFalse(loaded.travelling());
        assertEquals(List.of(), loaded.remainingRoute());
        assertEquals(AreaType.WORK, loaded.scheduleType());
        assertFalse(loaded.scheduleFinished());
    }

    @Test void recentUnversionedNbtPreservesNamesGraphTimetableAndInFlightProgress() {
        var state = progress();
        CompoundTag legacy = (CompoundTag) WorkspaceState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
        legacy.remove("data_version");
        legacy.getCompound("plan").remove("data_version");
        var loaded = WorkspaceState.CODEC.parse(NbtOps.INSTANCE, legacy).getOrThrow();
        assertEquals(state, loaded);
        CompoundTag saved = (CompoundTag) WorkspaceState.CODEC.encodeStart(NbtOps.INSTANCE, loaded).getOrThrow();
        assertEquals(WorkspaceDataVersions.MAID_STATE_VERSION, saved.getInt("data_version"));
        assertEquals(WorkspaceDataVersions.PLAN_VERSION, saved.getCompound("plan").getInt("data_version"));
    }

    @Test void currentMaidEnvelopeCanContainAnUnversionedPlan() {
        var state = progress();
        JsonObject json = WorkspaceState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow().getAsJsonObject();
        json.getAsJsonObject("plan").remove("data_version");
        assertEquals(state, WorkspaceState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test void explicitVersionZeroIsAlsoMigrated() {
        JsonObject json = WorkspaceState.CODEC.encodeStart(JsonOps.INSTANCE, progress()).getOrThrow().getAsJsonObject();
        json.addProperty("data_version", 0);
        json.getAsJsonObject("plan").addProperty("data_version", 0);
        assertEquals(progress(), WorkspaceState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test void futureVersionsAreRejectedAtBothLevelsWithoutPartialResults() {
        JsonObject json = WorkspaceState.CODEC.encodeStart(JsonOps.INSTANCE, progress()).getOrThrow().getAsJsonObject();
        json.addProperty("data_version", WorkspaceDataVersions.MAID_STATE_VERSION + 1);
        var futureMaid = WorkspaceState.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(futureMaid.isError());
        assertTrue(futureMaid.resultOrPartial(message -> {}).isEmpty());
        json.addProperty("data_version", WorkspaceDataVersions.MAID_STATE_VERSION);
        json.getAsJsonObject("plan").addProperty("data_version", WorkspaceDataVersions.PLAN_VERSION + 1);
        assertTrue(WorkspaceState.CODEC.parse(JsonOps.INSTANCE, json).isError());
        assertTrue(WorkspacePlan.CODEC.parse(JsonOps.INSTANCE, json.get("plan")).isError());
    }

    @Test void malformedVersionNeverFallsBackToLegacyZero() {
        for (String value : List.of("-1", "0.5", "true", "\"1\"", "null", "2147483648")) {
            JsonObject json = JsonParser.parseString(LEGACY_PLAN).getAsJsonObject();
            json.add("data_version", JsonParser.parseString(value));
            assertTrue(WorkspacePlan.CODEC.parse(JsonOps.INSTANCE, json).isError(), value);
        }
        assertTrue(WorkspacePlan.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("[]")).isError());
    }
}
