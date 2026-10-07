package com.erobrine.tlmcw.workspace;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

import java.util.*;

/**
 * UUID references only. Work progress survives idle/sleep schedules independently of the active
 * destination.
 */
public record WorkspaceState(
        WorkspacePlan plan,
        UUID destinationId,
        long workTicks,
        boolean travelling,
        List<UUID> remainingRoute,
        AreaType scheduleType,
        UUID workDestination,
        UUID entryId,
        boolean scheduleFinished,
        boolean suspended,
        boolean waitingDimension,
        Dynamic<?> unreadableData) {
    private record Work(
            Optional<UUID> destination, Optional<UUID> entry, long elapsed, boolean finished) {
        static final Codec<Work> CODEC =
                RecordCodecBuilder.create(
                        i ->
                                i.group(
                                                UUIDUtil.CODEC
                                                        .optionalFieldOf("destination")
                                                        .forGetter(Work::destination),
                                                UUIDUtil.CODEC
                                                        .optionalFieldOf("entry")
                                                        .forGetter(Work::entry),
                                                Codec.LONG
                                                        .optionalFieldOf("elapsed_ticks", 0L)
                                                        .forGetter(Work::elapsed),
                                                Codec.BOOL
                                                        .optionalFieldOf("finished", false)
                                                        .forGetter(Work::finished))
                                        .apply(i, Work::new));
    }

    private record Runtime(
            AreaType type, String phase, Optional<UUID> destination, List<UUID> route, Work work) {
        static final Codec<Runtime> CODEC =
                RecordCodecBuilder.create(
                        i ->
                                i.group(
                                                AreaType.CODEC
                                                        .fieldOf("schedule_type")
                                                        .forGetter(Runtime::type),
                                                Codec.STRING
                                                        .fieldOf("phase")
                                                        .forGetter(Runtime::phase),
                                                UUIDUtil.CODEC
                                                        .optionalFieldOf("destination")
                                                        .forGetter(Runtime::destination),
                                                UUIDUtil.CODEC
                                                        .listOf(0, 320)
                                                        .optionalFieldOf(
                                                                "remaining_route", List.of())
                                                        .forGetter(Runtime::route),
                                                Work.CODEC.fieldOf("work").forGetter(Runtime::work))
                                        .apply(i, Runtime::new));
    }

    static final Codec<WorkspaceState> BODY_CODEC =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            WorkspacePlan.CODEC
                                                    .fieldOf("plan")
                                                    .forGetter(WorkspaceState::plan),
                                            Runtime.CODEC
                                                    .fieldOf("runtime")
                                                    .forGetter(WorkspaceState::runtime))
                                    .apply(i, WorkspaceState::fromRuntime));
    public static final Codec<WorkspaceState> CODEC =
            WorkspaceDataVersions.preserving(
                    WorkspaceDataVersions.maidState(BODY_CODEC),
                    WorkspaceState::unreadable,
                    WorkspaceState::unreadableData);

    private Runtime runtime() {
        return new Runtime(
                scheduleType,
                suspended
                        ? "suspended"
                        : waitingDimension ? "waiting_dimension" : travelling ? "travel" : "active",
                Optional.ofNullable(destinationId),
                remainingRoute,
                new Work(
                        Optional.ofNullable(workDestination),
                        Optional.ofNullable(entryId),
                        workTicks,
                        scheduleFinished));
    }

    private static WorkspaceState fromRuntime(WorkspacePlan plan, Runtime r) {
        if (!plan.readable()
                || !Set.of("suspended", "waiting_dimension", "travel", "active")
                        .contains(r.phase()))
            throw new IllegalArgumentException("Unreadable runtime");
        return new WorkspaceState(
                plan,
                r.destination().orElse(null),
                r.work().elapsed(),
                !r.phase().equals("active"),
                r.route(),
                r.type(),
                r.work().destination().orElse(null),
                r.work().entry().orElse(null),
                r.work().finished(),
                r.phase().equals("suspended"),
                r.phase().equals("waiting_dimension"),
                null);
    }

    public WorkspaceState {
        remainingRoute = List.copyOf(remainingRoute);
        if (workTicks < 0 || scheduleType == AreaType.WAYPOINT)
            throw new IllegalArgumentException("Invalid runtime");
        if (destinationId != null
                && (plan.node(destinationId) == null || !plan.node(destinationId).isArea()))
            throw new IllegalArgumentException("Missing destination");
        if (workDestination != null
                && (plan.node(workDestination) == null
                        || plan.type(workDestination) != AreaType.WORK))
            throw new IllegalArgumentException("Missing work destination");
        if (plan.schedule().custom() && entryId == null
                || !plan.nodes(AreaType.WORK).isEmpty() && workDestination == null)
            throw new IllegalArgumentException("Missing work runtime reference");
        if (!suspended && !plan.areaIds().isEmpty() && destinationId == null)
            throw new IllegalArgumentException("Missing active destination");
        if (entryId != null
                && (plan.schedule().entry(entryId) == null
                        || !plan.schedule().entry(entryId).destination().equals(workDestination)))
            throw new IllegalArgumentException("Missing schedule entry");
        if (!suspended && destinationId != null && plan.type(destinationId) != scheduleType)
            throw new IllegalArgumentException("Schedule type mismatch");
        boolean valid =
                travelling
                        && !remainingRoute.isEmpty()
                        && Objects.equals(remainingRoute.getLast(), destinationId);
        for (int i = 0; valid && i < remainingRoute.size(); i++) {
            UUID id = remainingRoute.get(i);
            valid = plan.node(id) != null;
            if (valid && i > 0)
                valid =
                        !remainingRoute.get(i - 1).equals(id)
                                && plan.graph()
                                        .edges()
                                        .contains(new RouteEdge(remainingRoute.get(i - 1), id));
        }
        if (!valid && !remainingRoute.isEmpty()) {
            remainingRoute = List.of();
            travelling = true;
            suspended = true;
        }
    }

    public WorkspaceState(
            WorkspacePlan p,
            UUID dest,
            long ticks,
            boolean travel,
            List<UUID> route,
            AreaType type,
            UUID work,
            UUID entry,
            boolean finished,
            boolean suspended) {
        this(p, dest, ticks, travel, route, type, work, entry, finished, suspended, false, null);
    }

    static WorkspaceState unreadable(Dynamic<?> raw) {
        return new WorkspaceState(
                WorkspacePlan.unreadable(raw),
                null,
                0,
                true,
                List.of(),
                AreaType.WORK,
                null,
                null,
                false,
                true,
                false,
                raw);
    }

    public boolean readable() {
        return unreadableData == null && plan.readable();
    }

    public static WorkspaceState initial(WorkspacePlan p) {
        var work = p.nodes(AreaType.WORK);
        UUID entry = p.schedule().custom() ? p.schedule().entries().getFirst().id() : null;
        UUID dest =
                entry != null
                        ? p.schedule().entry(entry).destination()
                        : work.isEmpty()
                                ? p.areaIds().stream().findFirst().orElse(null)
                                : work.getFirst();
        return new WorkspaceState(
                p,
                dest,
                0,
                true,
                List.of(),
                dest == null ? AreaType.WORK : p.type(dest),
                work.isEmpty() ? null : dest,
                entry,
                false,
                false);
    }

    public WorkArea activeArea() {
        return plan.nodeArea(destinationId);
    }

    public WorkArea navigationArea() {
        return plan.nodeArea(remainingRoute.isEmpty() ? destinationId : remainingRoute.getFirst());
    }

    public WorkspaceState withTravel(boolean value) {
        return new WorkspaceState(
                plan,
                destinationId,
                workTicks,
                value,
                value ? remainingRoute : List.of(),
                scheduleType,
                workDestination,
                entryId,
                scheduleFinished,
                suspended,
                value && waitingDimension,
                null);
    }

    public WorkspaceState withRoute(List<UUID> route) {
        return new WorkspaceState(
                plan,
                destinationId,
                workTicks,
                travelling,
                route,
                scheduleType,
                workDestination,
                entryId,
                scheduleFinished,
                suspended,
                waitingDimension,
                null);
    }

    public WorkspaceState suspend(AreaType type) {
        return new WorkspaceState(
                plan,
                destinationId,
                workTicks,
                true,
                remainingRoute,
                type,
                workDestination,
                entryId,
                scheduleFinished,
                true);
    }

    public UUID navigationId() {
        return remainingRoute.isEmpty() ? destinationId : remainingRoute.getFirst();
    }

    public WorkspaceState withWaitingDimension(boolean value) {
        return new WorkspaceState(
                plan,
                destinationId,
                workTicks,
                true,
                remainingRoute,
                scheduleType,
                workDestination,
                entryId,
                scheduleFinished,
                false,
                value,
                null);
    }

    public WorkspaceState advanceRoute() {
        return withRoute(remainingRoute.subList(1, remainingRoute.size()));
    }

    public WorkspaceState destination(AreaType type, RoutePlanner.Route route) {
        if (route == null) return suspend(type);
        return new WorkspaceState(
                plan,
                route.destination(),
                workTicks,
                true,
                route.remaining(),
                type,
                type == AreaType.WORK ? route.destination() : workDestination,
                entryId,
                scheduleFinished,
                false);
    }

    public boolean hasNextWorkDestination() {
        if (!plan.schedule().custom()) return plan.nodes(AreaType.WORK).size() > 1;
        int position = plan.schedule().position(entryId);
        return !scheduleFinished
                && (position + 1 < plan.schedule().entries().size()
                        || plan.schedule().cyclic() && plan.schedule().entries().size() > 1);
    }

    public WorkspaceState next() {
        var work = plan.nodes(AreaType.WORK);
        if (work.isEmpty()) return this;
        UUID next, entry = null;
        if (plan.schedule().custom()) {
            if (!hasNextWorkDestination()) return this;
            var e =
                    plan.schedule()
                            .entries()
                            .get(
                                    (plan.schedule().position(entryId) + 1)
                                            % plan.schedule().entries().size());
            next = e.destination();
            entry = e.id();
        } else next = work.get((work.indexOf(workDestination) + 1) % work.size());
        var route = plan.graph().shortestPath(plan, destinationId, next);
        return new WorkspaceState(
                plan,
                next,
                0,
                true,
                route.isEmpty() ? List.of() : route.subList(1, route.size()),
                AreaType.WORK,
                next,
                entry,
                false,
                false);
    }

    public WorkspaceState countWork(long interval) {
        if (travelling || suspended || scheduleType != AreaType.WORK || scheduleFinished)
            return this;
        if (plan.schedule().custom()) interval = plan.schedule().entry(entryId).condition().ticks();
        long elapsed = workTicks == Long.MAX_VALUE ? Long.MAX_VALUE : workTicks + 1;
        boolean finished = false;
        if (elapsed >= interval) {
            if (hasNextWorkDestination()) return next();
            finished = plan.schedule().custom() && !plan.schedule().cyclic();
        }
        return new WorkspaceState(
                plan,
                destinationId,
                hasNextWorkDestination() ? elapsed : Math.min(elapsed, interval),
                false,
                List.of(),
                scheduleType,
                workDestination,
                entryId,
                finished,
                false);
    }
}
