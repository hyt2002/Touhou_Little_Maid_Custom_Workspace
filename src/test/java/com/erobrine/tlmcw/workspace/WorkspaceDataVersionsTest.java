package com.erobrine.tlmcw.workspace;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.mojang.serialization.*;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import java.util.*;

class WorkspaceDataVersionsTest {
    static final String PLAN =
            """
{"dimension":"minecraft:overworld","areas":[
  {"min":[0,64,0],"max":[3,67,3]}, {"min":[10,64,0],"max":[13,67,3]},
  {"min":[20,64,0],"max":[23,67,3]}, {"min":[30,64,0],"max":[33,67,3]}],
 "area_types":["work","work","idle","sleep"],"area_names":["菜田","果园","庭院","卧室"],
 "route_graph":{"points":[[6,65,5],[9,65,5]],"edges":[{"a":0,"b":-1},{"a":-1,"b":-2},{"a":-2,"b":1}]},
 "work_schedule":{"entries":[{"area":1,"condition":{"type":"delay","ticks":600}},
    {"area":1,"condition":{"type":"delay","ticks":120}},{"area":0,"condition":{"type":"delay","ticks":40}}],"cyclic":false}}
""";

    static JsonObject legacyPlan(int version) {
        var p = JsonParser.parseString(PLAN).getAsJsonObject();
        if (version >= 0) p.addProperty("data_version", version);
        return p;
    }

    static JsonObject legacyState(int version) {
        var s = new JsonObject();
        s.add("plan", legacyPlan(version));
        if (version >= 0) s.addProperty("data_version", version);
        s.addProperty("active_index", 3);
        s.addProperty("work_ticks", 837);
        s.addProperty("travelling", true);
        s.addProperty("schedule_type", "sleep");
        s.addProperty("work_index", 1);
        s.addProperty("schedule_index", 1);
        s.add("remaining_route", JsonParser.parseString("[]"));
        return s;
    }

    static WorkspacePlan loadPlan(JsonElement p) {
        return WorkspacePlan.CODEC.parse(JsonOps.INSTANCE, p).getOrThrow();
    }

    static WorkspaceState loadState(JsonElement s) {
        return WorkspaceState.CODEC.parse(JsonOps.INSTANCE, s).getOrThrow();
    }

    @Test
    void unversionedAndV1PlansGetIdenticalDeterministicIdentities() {
        var a = loadPlan(legacyPlan(-1));
        var b = loadPlan(legacyPlan(1));
        var c = loadPlan(legacyPlan(0));
        assertTrue(a.readable());
        assertEquals(a, b);
        assertEquals(a, c);
        assertEquals("菜田", a.name(a.areaId(0)));
        assertEquals(AreaType.SLEEP, a.type(a.areaId(3)));
        assertEquals(2, a.nodes(AreaType.WAYPOINT).size());
        assertEquals(3, a.graph().edges().size());
        assertEquals(a.areaId(1), a.schedule().entries().get(0).destination());
        assertEquals(a.areaId(1), a.schedule().entries().get(1).destination());
        assertNotEquals(a.schedule().entries().get(0).id(), a.schedule().entries().get(1).id());
    }

    @Test
    void normalizedDefaultsAndEdgeOrderDoNotChangeIdentity() {
        var p =
                JsonParser.parseString(
                                "{\"dimension\":\"minecraft:overworld\",\"areas\":[{\"min\":[0,64,0],\"max\":[3,67,3]}]}")
                        .getAsJsonObject();
        var a = loadPlan(p);
        p.add("area_types", JsonParser.parseString("[\"work\"]"));
        p.add("area_names", JsonParser.parseString("[\"\"]"));
        p.add("route_graph", JsonParser.parseString("{\"points\":[],\"edges\":[]}"));
        p.add("work_schedule", JsonParser.parseString("{\"entries\":[],\"cyclic\":true}"));
        assertEquals(a, loadPlan(p));
        var q = legacyPlan(1);
        q.getAsJsonObject("route_graph")
                .add(
                        "edges",
                        JsonParser.parseString(
                                "[{\"a\":1,\"b\":-2},{\"a\":-2,\"b\":-1},{\"a\":-1,\"b\":0}]"));
        assertEquals(loadPlan(legacyPlan(1)), loadPlan(q));
    }

    @Test
    void migrationMapsEveryRuntimeReferenceAndKeepsRestWorkProgress() {
        var s = loadState(legacyState(1));
        assertTrue(s.readable());
        assertEquals(s.plan().areaId(3), s.destinationId());
        assertEquals(s.plan().areaId(1), s.workDestination());
        assertEquals(s.plan().schedule().entries().get(1).id(), s.entryId());
        assertEquals(837, s.workTicks());
        assertEquals(AreaType.SLEEP, s.scheduleType());
        assertTrue(s.travelling());
        assertEquals(loadPlan(legacyPlan(1)), s.plan());
        assertEquals(s, loadState(legacyState(-1)));
    }

    @Test
    void inFlightLegacyRouteConvertsUsingTheSameNestedPlanMap() {
        var raw = legacyState(1);
        raw.addProperty("active_index", 1);
        raw.addProperty("schedule_type", "work");
        raw.add("remaining_route", JsonParser.parseString("[-1,-2,1]"));
        var s = loadState(raw);
        assertTrue(s.readable());
        assertEquals(
                List.of(
                        s.plan().nodes(AreaType.WAYPOINT).get(0),
                        s.plan().nodes(AreaType.WAYPOINT).get(1),
                        s.plan().areaId(1)),
                s.remainingRoute());
        assertEquals(s, s.countWork(1));
    }

    @Test
    void jsonAndNbtRoundTripAreV2WithRealUuidIntArrays() {
        var s = loadState(legacyState(1));
        var json =
                WorkspaceState.CODEC
                        .encodeStart(JsonOps.INSTANCE, s)
                        .getOrThrow()
                        .getAsJsonObject();
        assertEquals(2, json.get("data_version").getAsInt());
        assertEquals(2, json.getAsJsonObject("plan").get("data_version").getAsInt());
        assertFalse(json.has("active_index"));
        assertTrue(json.has("runtime"));
        assertEquals(s, loadState(json));
        CompoundTag tag =
                (CompoundTag) WorkspaceState.CODEC.encodeStart(NbtOps.INSTANCE, s).getOrThrow();
        assertEquals(4, tag.getCompound("plan").getIntArray("plan_id").length);
        assertEquals(
                4,
                tag.getCompound("plan")
                        .getList("nodes", Tag.TAG_COMPOUND)
                        .getCompound(0)
                        .getIntArray("id")
                        .length);
        assertEquals(4, tag.getCompound("runtime").getIntArray("destination").length);
        assertEquals(s, WorkspaceState.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());
    }

    @Test
    void migrationDoesNotMutateRawInputAndIsIdempotent() {
        var raw = legacyState(1);
        var copy = raw.deepCopy();
        var loaded = loadState(raw);
        assertEquals(copy, raw);
        var current = WorkspaceState.CODEC.encodeStart(JsonOps.INSTANCE, loaded).getOrThrow();
        assertEquals(loaded, loadState(current));
        assertEquals(loaded, loadState(raw));
    }

    @Test
    void futureAndMalformedPlansPreserveTheOriginalInsteadOfBecomingEmptyPlans() {
        for (String version : List.of("3", "-1", "0.5", "true", "\"1\"", "null", "2147483648")) {
            var raw = legacyPlan(1);
            raw.add("data_version", JsonParser.parseString(version));
            var p = loadPlan(raw);
            assertFalse(p.readable(), version);
            assertEquals(raw, WorkspacePlan.CODEC.encodeStart(JsonOps.INSTANCE, p).getOrThrow());
        }
        var raw = legacyPlan(1);
        raw.getAsJsonObject("route_graph")
                .add("edges", JsonParser.parseString("[{\"a\":0,\"b\":-99}]"));
        var p = loadPlan(raw);
        assertFalse(p.readable());
        assertEquals(raw, WorkspacePlan.CODEC.encodeStart(JsonOps.INSTANCE, p).getOrThrow());
    }

    @Test
    void unknownOrBrokenNestedPlanPreservesEntireMaidIncludingRuntime() {
        for (boolean nested : List.of(false, true)) {
            var raw = legacyState(1);
            if (nested) raw.getAsJsonObject("plan").addProperty("data_version", 9);
            else raw.addProperty("data_version", 9);
            var s = loadState(raw);
            assertFalse(s.readable());
            assertEquals(raw, WorkspaceState.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow());
            Tag tag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, raw);
            var nbt = WorkspaceState.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
            assertFalse(nbt.readable());
            assertEquals(tag, WorkspaceState.CODEC.encodeStart(NbtOps.INSTANCE, nbt).getOrThrow());
        }
    }

    @Test
    void duplicateIdentitiesAndMissingScheduleTargetsArePreservedAsInvalid() {
        var raw =
                WorkspacePlan.CODEC
                        .encodeStart(JsonOps.INSTANCE, loadPlan(legacyPlan(1)))
                        .getOrThrow()
                        .getAsJsonObject();
        raw.getAsJsonArray("nodes").add(raw.getAsJsonArray("nodes").get(0).deepCopy());
        assertFalse(loadPlan(raw).readable());
        raw =
                WorkspacePlan.CODEC
                        .encodeStart(JsonOps.INSTANCE, loadPlan(legacyPlan(1)))
                        .getOrThrow()
                        .getAsJsonObject();
        raw.getAsJsonArray("nodes").remove(1);
        assertFalse(loadPlan(raw).readable());
    }

    @Test
    void deletingAndReorderingNodesKeepsOtherIdentitiesAndReferences() {
        var p = loadPlan(legacyPlan(1));
        var removed = p.removeNode(p.areaId(2), true);
        assertEquals(p.node(p.areaId(3)), removed.node(p.areaId(3)));
        assertEquals(p.graph(), removed.graph());
        assertEquals(p.schedule(), removed.schedule());
        var nodes = new ArrayList<>(removed.nodeList());
        Collections.reverse(nodes);
        var reordered =
                new WorkspacePlan(removed.planId(), nodes, removed.graph(), removed.schedule());
        assertEquals(removed.graph(), reordered.graph());
        assertEquals(removed.schedule(), reordered.schedule());
        assertEquals(removed.node(p.areaId(0)), reordered.node(p.areaId(0)));
    }

    @Test
    void crossDimensionEdgesPersistAndPlanTheNextForeignNodeWithoutTreatingCoordinatesAsArrival() {
        var p = loadPlan(legacyPlan(1));
        var foreign =
                WorkspaceNode.waypoint(
                        ResourceLocation.parse("minecraft:the_nether"), p.node(p.areaId(0)).min());
        p =
                p.addNode(foreign)
                        .withGraph(
                                p.graph()
                                        .connect(p.areaId(0), foreign.id())
                                        .connect(foreign.id(), p.areaId(1)));
        assertEquals(
                p, loadPlan(WorkspacePlan.CODEC.encodeStart(JsonOps.INSTANCE, p).getOrThrow()));
        assertTrue(p.graph().component(p, p.areaId(0)).contains(foreign.id()));
        assertEquals(
                List.of(p.areaId(0), foreign.id()),
                p.graph().shortestPath(p, p.areaId(0), foreign.id()));
        assertEquals(
                foreign.id(),
                RoutePlanner.to(
                                p,
                                foreign.id(),
                                net.minecraft.world.phys.Vec3.atCenterOf(foreign.min()),
                                ResourceLocation.parse("minecraft:overworld"))
                        .destination());
        assertNotEquals(
                foreign.id(),
                NodeSelection.pick(
                        p,
                        net.minecraft.world.phys.Vec3.atCenterOf(foreign.min()),
                        net.minecraft.world.phys.Vec3.atCenterOf(foreign.min()).add(0, 1, 0),
                        true,
                        ResourceLocation.parse("minecraft:overworld")));
    }
}
