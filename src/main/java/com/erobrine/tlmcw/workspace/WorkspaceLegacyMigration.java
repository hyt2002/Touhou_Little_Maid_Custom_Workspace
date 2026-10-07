package com.erobrine.tlmcw.workspace;

import com.google.gson.*;
import com.mojang.serialization.*;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** The only place that understands V0/V1 indices and parallel arrays. */
final class WorkspaceLegacyMigration {
    private record Legacy(WorkspacePlan plan, Map<Integer, UUID> nodes, List<UUID> entries) {}

    private static <T> T read(Codec<T> codec, JsonElement input) {
        return codec.parse(JsonOps.INSTANCE, input).getOrThrow();
    }

    private static UUID id(UUID plan, String label) {
        return UUID.nameUUIDFromBytes(
                ("tlmcw:v2:" + plan + ":" + label).getBytes(StandardCharsets.UTF_8));
    }

    private static JsonElement canonical(JsonElement e) {
        if (e.isJsonObject()) {
            var out = new JsonObject();
            e.getAsJsonObject().keySet().stream()
                    .sorted()
                    .forEach(k -> out.add(k, canonical(e.getAsJsonObject().get(k))));
            return out;
        }
        if (e.isJsonArray()) {
            var out = new JsonArray();
            e.getAsJsonArray().forEach(v -> out.add(canonical(v)));
            return out;
        }
        return e.deepCopy();
    }

    private static JsonArray array(JsonObject o, String key) {
        return o.has(key) ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static int integer(JsonObject o, String key, int fallback) {
        return o.has(key) ? read(Codec.INT, o.get(key)) : fallback;
    }

    private static long number(JsonObject o, String key, long fallback) {
        return o.has(key) ? read(Codec.LONG, o.get(key)) : fallback;
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        if (!o.has(key)) return fallback;
        var v = o.get(key);
        if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
            int n = read(Codec.INT, v);
            if (n == 0 || n == 1) return n == 1;
        }
        return read(Codec.BOOL, v);
    }

    private static Legacy convertPlan(JsonObject old) {
        ResourceLocation dim = read(ResourceLocation.CODEC, old.get("dimension"));
        JsonArray areas = array(old, "areas"),
                types = array(old, "area_types"),
                names = array(old, "area_names");
        if (!old.has("areas")
                || areas.size() > 64
                || types.size() != 0 && types.size() != areas.size()
                || names.size() != 0 && names.size() != areas.size())
            throw new IllegalArgumentException("Invalid legacy area arrays");
        var nodes = new ArrayList<WorkspaceNode>();
        // Normalize optional defaults before hashing, so V0 and V1 snapshots of the same plan get
        // the same identities.
        var normalized = new JsonObject();
        normalized.addProperty("dimension", dim.toString());
        normalized.add("areas", areas.deepCopy());
        var normalizedTypes = new JsonArray();
        var normalizedNames = new JsonArray();
        for (int i = 0; i < areas.size(); i++) {
            normalizedTypes.add(types.isEmpty() ? "work" : types.get(i).getAsString());
            normalizedNames.add(
                    names.isEmpty() ? "" : WorkspacePlan.cleanName(names.get(i).getAsString()));
        }
        normalized.add("area_types", normalizedTypes);
        normalized.add("area_names", normalizedNames);
        JsonObject graph =
                old.has("route_graph") ? old.getAsJsonObject("route_graph") : new JsonObject();
        JsonArray points = array(graph, "points"), edges = array(graph, "edges");
        if (points.size() > RouteGraph.MAX_POINTS || edges.size() > RouteGraph.MAX_EDGES)
            throw new IllegalArgumentException("Legacy graph too large");
        var sortedEdges = new TreeSet<String>();
        for (var e : edges) {
            var edge = e.getAsJsonObject();
            int a = integer(edge, "a", Integer.MIN_VALUE),
                    b = integer(edge, "b", Integer.MIN_VALUE);
            if (a == b) throw new IllegalArgumentException("Legacy self edge");
            sortedEdges.add(Math.min(a, b) + "," + Math.max(a, b));
        }
        var normalizedGraph = new JsonObject();
        normalizedGraph.add("points", points.deepCopy());
        var normalizedEdges = new JsonArray();
        sortedEdges.forEach(normalizedEdges::add);
        normalizedGraph.add("edges", normalizedEdges);
        normalized.add("route_graph", normalizedGraph);
        JsonObject schedule =
                old.has("work_schedule") ? old.getAsJsonObject("work_schedule") : new JsonObject();
        JsonArray entries = array(schedule, "entries");
        if (entries.size() > WorkSchedule.MAX_ENTRIES)
            throw new IllegalArgumentException("Legacy schedule too large");
        var normalizedSchedule = new JsonObject();
        normalizedSchedule.add("entries", entries.deepCopy());
        normalizedSchedule.addProperty("cyclic", bool(schedule, "cyclic", true));
        normalized.add("work_schedule", normalizedSchedule);
        UUID planId =
                UUID.nameUUIDFromBytes(
                        ("tlmcw:legacy-plan:" + canonical(normalized))
                                .getBytes(StandardCharsets.UTF_8));
        var map = new HashMap<Integer, UUID>();
        for (int i = 0; i < areas.size(); i++) {
            UUID uuid = id(planId, "area:" + i);
            map.put(i, uuid);
            WorkArea bounds = read(WorkArea.CODEC, areas.get(i));
            AreaType type = read(AreaType.CODEC, normalizedTypes.get(i));
            if (type == AreaType.WAYPOINT)
                throw new IllegalArgumentException("Legacy area is not a waypoint");
            nodes.add(
                    new WorkspaceNode(
                            uuid,
                            dim,
                            type,
                            normalizedNames.get(i).getAsString(),
                            bounds.min(),
                            bounds.max()));
        }
        for (int i = 0; i < points.size(); i++) {
            UUID uuid = id(planId, "point:" + i);
            map.put(-i - 1, uuid);
            BlockPos pos = read(BlockPos.CODEC, points.get(i));
            nodes.add(new WorkspaceNode(uuid, dim, AreaType.WAYPOINT, "", pos, pos));
        }
        var migratedEdges = new ArrayList<RouteEdge>();
        for (String e : sortedEdges) {
            String[] pair = e.split(",");
            UUID a = map.get(Integer.parseInt(pair[0])), b = map.get(Integer.parseInt(pair[1]));
            if (a == null || b == null) throw new IllegalArgumentException("Dangling legacy edge");
            migratedEdges.add(new RouteEdge(a, b));
        }
        var migratedEntries = new ArrayList<WorkScheduleEntry>();
        var entryIds = new ArrayList<UUID>();
        for (int i = 0; i < entries.size(); i++) {
            var e = entries.get(i).getAsJsonObject();
            UUID dest = map.get(integer(e, "area", -1));
            if (dest == null) throw new IllegalArgumentException("Missing legacy schedule area");
            UUID uuid = id(planId, "entry:" + i);
            entryIds.add(uuid);
            migratedEntries.add(
                    new WorkScheduleEntry(
                            uuid, dest, read(DelayCondition.CODEC, e.get("condition"))));
        }
        return new Legacy(
                new WorkspacePlan(
                        planId,
                        nodes,
                        new RouteGraph(migratedEdges),
                        new WorkSchedule(migratedEntries, bool(schedule, "cyclic", true))),
                Map.copyOf(map),
                List.copyOf(entryIds));
    }

    static <O> Dynamic<O> plan(Dynamic<O> raw) {
        var legacy = convertPlan(raw.convert(JsonOps.INSTANCE).getValue().getAsJsonObject());
        return new Dynamic<>(
                        JsonOps.INSTANCE,
                        WorkspacePlan.BODY_CODEC
                                .encodeStart(JsonOps.INSTANCE, legacy.plan())
                                .getOrThrow())
                .convert(raw.getOps());
    }

    static <O> Dynamic<O> state(Dynamic<O> raw) {
        JsonObject old = raw.convert(JsonOps.INSTANCE).getValue().getAsJsonObject();
        JsonObject savedPlan = old.getAsJsonObject("plan");
        // A V1 state contains a V0/V1 plan. Migrate the complete snapshot once and reuse the
        // identity mapping.
        if (integer(savedPlan, "data_version", 0) > 1)
            throw new IllegalArgumentException("Unexpected newer plan inside legacy runtime");
        Legacy migrated = convertPlan(savedPlan);
        WorkspacePlan p = migrated.plan();
        AreaType type =
                old.has("schedule_type")
                        ? read(AreaType.CODEC, old.get("schedule_type"))
                        : AreaType.WORK;
        int count = p.areaIds().size(), active = integer(old, "active_index", 0);
        UUID dest = count == 0 ? null : migrated.nodes().get(Math.floorMod(active, count));
        var work = p.nodes(AreaType.WORK);
        UUID workDest = migrated.nodes().get(integer(old, "work_index", -1));
        if (!work.contains(workDest))
            workDest =
                    type == AreaType.WORK && work.contains(dest)
                            ? dest
                            : work.isEmpty() ? null : work.getFirst();
        UUID entry = null;
        boolean finished = bool(old, "schedule_finished", false);
        if (p.schedule().custom()) {
            entry =
                    migrated.entries()
                            .get(
                                    Math.floorMod(
                                            integer(old, "schedule_index", 0),
                                            migrated.entries().size()));
            workDest = p.schedule().entry(entry).destination();
            if (p.schedule().cyclic()) finished = false;
        } else finished = false;
        var route = new ArrayList<UUID>();
        for (var e : array(old, "remaining_route")) {
            UUID uuid = migrated.nodes().get(read(Codec.INT, e));
            if (uuid == null) {
                route.clear();
                break;
            }
            route.add(uuid);
        }
        boolean suspended = dest == null || p.type(dest) != type;
        var state =
                new WorkspaceState(
                        p,
                        dest,
                        Math.max(0, number(old, "work_ticks", 0)),
                        bool(old, "travelling", true),
                        route,
                        type,
                        workDest,
                        entry,
                        finished,
                        suspended);
        return new Dynamic<>(
                        JsonOps.INSTANCE,
                        WorkspaceState.BODY_CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow())
                .convert(raw.getOps());
    }

    private WorkspaceLegacyMigration() {}
}
