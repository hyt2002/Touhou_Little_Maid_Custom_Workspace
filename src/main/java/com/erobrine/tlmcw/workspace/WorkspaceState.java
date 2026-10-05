package com.erobrine.tlmcw.workspace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/** Persistent schedule destination and independent work rotation progress. */
public record WorkspaceState(WorkspacePlan plan, int activeIndex, long workTicks, boolean travelling,
                             List<Integer> remainingRoute, AreaType scheduleType, int workIndex,
                             int scheduleIndex, boolean scheduleFinished) {
    public static final Codec<WorkspaceState> CODEC = WorkspaceDataVersions.maidState(RecordCodecBuilder.create(instance -> instance.group(
            WorkspacePlan.CODEC.fieldOf("plan").forGetter(WorkspaceState::plan),
            Codec.INT.fieldOf("active_index").forGetter(WorkspaceState::activeIndex),
            Codec.LONG.optionalFieldOf("work_ticks", 0L).forGetter(WorkspaceState::workTicks),
            Codec.BOOL.optionalFieldOf("travelling", true).forGetter(WorkspaceState::travelling),
            Codec.INT.listOf(0, RouteGraph.MAX_POINTS + 64).optionalFieldOf("remaining_route", List.of()).forGetter(WorkspaceState::remainingRoute),
            AreaType.CODEC.optionalFieldOf("schedule_type", AreaType.WORK).forGetter(WorkspaceState::scheduleType),
            Codec.INT.optionalFieldOf("work_index", -1).forGetter(WorkspaceState::workIndex),
            Codec.INT.optionalFieldOf("schedule_index", 0).forGetter(WorkspaceState::scheduleIndex),
            Codec.BOOL.optionalFieldOf("schedule_finished", false).forGetter(WorkspaceState::scheduleFinished)
    ).apply(instance, WorkspaceState::new)));

    public WorkspaceState {
        activeIndex = plan.areas().isEmpty() ? 0 : Math.floorMod(activeIndex, plan.areas().size());
        workTicks = Math.max(0, workTicks);
        var work = plan.nodes(AreaType.WORK);
        if (!work.contains(workIndex)) workIndex = scheduleType == AreaType.WORK && work.contains(activeIndex)
                ? activeIndex : work.isEmpty() ? -1 : work.getFirst();
        if (plan.schedule().custom()) {
            scheduleIndex = Math.floorMod(scheduleIndex, plan.schedule().entries().size());
            workIndex = plan.schedule().entries().get(scheduleIndex).area();
            if (plan.schedule().cyclic()) scheduleFinished = false;
        } else { scheduleIndex = 0; scheduleFinished = false; }
        remainingRoute = List.copyOf(remainingRoute);
        boolean valid = travelling && !remainingRoute.isEmpty() && remainingRoute.getLast() == activeIndex;
        for (int i = 0; valid && i < remainingRoute.size(); i++) {
            valid = plan.graph().hasNode(remainingRoute.get(i), plan.areas().size());
            if (valid && i > 0) valid = remainingRoute.get(i - 1).intValue() != remainingRoute.get(i).intValue()
                    && plan.graph().edges().contains(new RouteEdge(remainingRoute.get(i - 1), remainingRoute.get(i)));
        }
        if (!valid) remainingRoute = List.of();
    }
    public WorkspaceState(WorkspacePlan plan, int activeIndex, long workTicks, boolean travelling, List<Integer> remainingRoute) {
        this(plan, activeIndex, workTicks, travelling, remainingRoute, AreaType.WORK, activeIndex);
    }
    public WorkspaceState(WorkspacePlan plan, int activeIndex, long workTicks, boolean travelling,
                          List<Integer> remainingRoute, AreaType scheduleType, int workIndex) {
        this(plan, activeIndex, workTicks, travelling, remainingRoute, scheduleType, workIndex,
                plan.schedule().indexOfArea(workIndex), false);
    }
    public WorkspaceState(WorkspacePlan plan, int activeIndex, long workTicks, boolean travelling) {
        this(plan, activeIndex, workTicks, travelling, List.of());
    }
    public static WorkspaceState initial(WorkspacePlan plan) {
        var work = plan.nodes(AreaType.WORK);
        int index = plan.schedule().custom() ? plan.schedule().entries().getFirst().area() : work.isEmpty() ? 0 : work.getFirst();
        return new WorkspaceState(plan, index, 0, true, List.of(), plan.areas().isEmpty() ? AreaType.WORK : plan.type(index), index);
    }
    public WorkArea activeArea() { return plan.areas().get(activeIndex); }
    public WorkArea navigationArea() { return remainingRoute.isEmpty() ? activeArea() : plan.nodeArea(remainingRoute.getFirst()); }
    public WorkspaceState withTravel(boolean value) { return new WorkspaceState(plan, activeIndex, workTicks, value, remainingRoute, scheduleType, workIndex, scheduleIndex, scheduleFinished); }
    public WorkspaceState withRoute(List<Integer> route) { return new WorkspaceState(plan, activeIndex, workTicks, travelling, route, scheduleType, workIndex, scheduleIndex, scheduleFinished); }
    public WorkspaceState suspend(AreaType type) {
        // Keep the custom destination/progress while the current schedule uses its original TLM area.
        return new WorkspaceState(plan, activeIndex, workTicks, true, remainingRoute, type, workIndex, scheduleIndex, scheduleFinished);
    }
    public WorkspaceState advanceRoute() { return withRoute(remainingRoute.subList(1, remainingRoute.size())); }
    public WorkspaceState destination(AreaType type, RoutePlanner.Route route) {
        return new WorkspaceState(plan, route.destination(), workTicks, true, route.remaining(), type,
                type == AreaType.WORK ? route.destination() : workIndex, scheduleIndex, scheduleFinished);
    }
    public boolean hasNextWorkDestination() {
        if (!plan.schedule().custom()) return plan.nodes(AreaType.WORK).size() > 1;
        return !scheduleFinished && (scheduleIndex + 1 < plan.schedule().entries().size()
                || plan.schedule().cyclic() && plan.schedule().entries().size() > 1);
    }
    public WorkspaceState next() {
        var work = plan.nodes(AreaType.WORK);
        if (work.isEmpty()) return this;
        int cursor = scheduleIndex;
        int next;
        if (plan.schedule().custom()) {
            if (!hasNextWorkDestination()) return this;
            cursor = (scheduleIndex + 1) % plan.schedule().entries().size();
            next = plan.schedule().entries().get(cursor).area();
        } else next = work.get((work.indexOf(workIndex) + 1) % work.size());
        var route = plan.graph().shortestPath(plan, activeIndex, next);
        return new WorkspaceState(plan, next, 0, true, route.isEmpty() ? List.of() : route.subList(1, route.size()), AreaType.WORK, next, cursor, false);
    }
    public WorkspaceState countWork(long intervalTicks) {
        if (travelling || scheduleType != AreaType.WORK || scheduleFinished) return this;
        if (plan.schedule().custom()) intervalTicks = plan.schedule().entries().get(scheduleIndex).condition().ticks();
        long elapsed = workTicks + 1;
        int count = plan.nodes(AreaType.WORK).size();
        boolean finished = false;
        if (elapsed >= intervalTicks) {
            if (hasNextWorkDestination()) return next();
            finished = plan.schedule().custom() && !plan.schedule().cyclic();
        }
        return new WorkspaceState(plan, activeIndex, count == 1 || plan.schedule().custom() && !hasNextWorkDestination() ? Math.min(elapsed, intervalTicks) : elapsed,
                false, List.of(), scheduleType, workIndex, scheduleIndex, finished);
    }
}
