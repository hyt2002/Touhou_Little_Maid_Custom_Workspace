package com.erobrine.tlmcw.workspace;

import com.erobrine.tlmcw.Config;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class WorkspaceController {
    private static final Map<EntityMaid, Travel> TRAVEL = new WeakHashMap<>();
    private static final Set<EntityMaid> LOADING = Collections.newSetFromMap(new WeakHashMap<>());

    public static void beginDataLoad(EntityMaid maid) {
        if (maid.level() instanceof ServerLevel) {
            LOADING.add(maid);
            TRAVEL.remove(maid);
        }
    }

    public static void endDataLoad(EntityMaid maid) {
        if (maid.level() instanceof ServerLevel) LOADING.remove(maid);
    }

    public static void onNavigationContextChange(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level) || LOADING.contains(maid)) return;
        WorkspaceState state = WorkspaceLogic.state(maid);
        if (state == null) return;
        // Home can be toggled and the entity stored/moved before another tick. A successful
        // dimension transfer also invalidates the old entry. Persist this without resetting work.
        clearWork(maid, level);
        WorkspaceAccess.writeState(maid, state.suspend(state.scheduleType()), true);
        TRAVEL.remove(maid);
    }

    private static final class Travel {
        final UUID areaIndex;
        final AreaType type;
        final WorkArea area;
        final BlockPos target;
        int ticks;

        Travel(WorkspaceState state) {
            this.areaIndex = state.destinationId();
            this.type = state.scheduleType();
            this.area = state.navigationArea();
            this.target = area.navigationCenter();
        }
    }

    public static void onMaidTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        if (!(maid.level() instanceof ServerLevel level)) return;
        WorkspaceState saved = prepareSchedule(maid);
        if (saved == null) {
            TRAVEL.remove(maid);
            return;
        }
        WorkspaceState state = WorkspaceLogic.active(maid);
        if (state == null) {
            TRAVEL.remove(maid);
            if (!saved.travelling()) WorkspaceAccess.writeState(maid, saved.withTravel(true), true);
            return;
        }
        if (waitForDimension(maid, state, level)) return;
        if (state.waitingDimension()) {
            state = state.withWaitingDimension(false);
            WorkspaceAccess.writeState(maid, state, true);
            clearWork(maid, level);
        }
        if (!maid.isEffectiveAi()
                || !maid.canBrainMoving()
                || maid.guiOpening
                || maid.getBrain().isActive(Activity.PANIC)) return;
        // A direct fallback chosen without a local graph entry cannot be trusted after reload
        // or return from another dimension. Re-evaluate it once from the actual current position.
        // Preserve non-empty routes: their next node can be an intentional dimension crossing.
        if (state.travelling()
                && state.remainingRoute().isEmpty()
                && !TRAVEL.containsKey(maid)
                && !state.activeArea().contains(maid.blockPosition())) {
            state =
                    state.destination(
                            state.scheduleType(),
                            planScheduleRoute(maid, state, state.scheduleType(), level));
            WorkspaceAccess.writeState(maid, state, true);
            clearWork(maid, level);
            if (waitForDimension(maid, state, level)) return;
        }
        WorkArea area = state.activeArea();
        if (!state.travelling() && !area.contains(maid.blockPosition())) {
            state =
                    state.destination(
                            state.scheduleType(),
                            RoutePlanner.to(
                                    state.plan(),
                                    state.destinationId(),
                                    maid.position(),
                                    level.dimension().location()));
            WorkspaceAccess.writeState(maid, state, true);
            clearWork(maid, level);
        }
        boolean advanced = false;
        while (state.travelling()
                && !state.remainingRoute().isEmpty()
                && state.plan().inDimension(state.navigationId(), level.dimension().location())
                && state.navigationArea().contains(maid.blockPosition())) {
            state = state.advanceRoute();
            advanced = true;
        }
        if (advanced) {
            WorkspaceAccess.writeState(maid, state, true);
            clearWork(maid, level);
            TRAVEL.remove(maid);
            maid.getSchedulePos().restrictTo(maid);
        }
        if (waitForDimension(maid, state, level)) return;
        if (!state.remainingRoute().isEmpty()
                || !state.plan().inDimension(state.destinationId(), level.dimension().location())
                || !area.contains(maid.blockPosition())) {
            if (!state.travelling()) {
                state = state.withTravel(true);
                WorkspaceAccess.writeState(maid, state, true);
                clearWork(maid, level);
            }
            Travel travel = TRAVEL.get(maid);
            WorkArea navigationArea = state.navigationArea();
            if (travel == null
                    || !Objects.equals(travel.areaIndex, state.destinationId())
                    || travel.type != state.scheduleType()
                    || !travel.area.equals(navigationArea)) {
                travel = new Travel(state);
                TRAVEL.put(maid, travel);
                maid.getSchedulePos().restrictTo(maid);
            }
            int timeout = Config.TRAVEL_TIMEOUT_TICKS.get();
            boolean maySkip =
                    state.scheduleType() == AreaType.WORK
                            ? state.hasNextWorkDestination()
                            : state.plan().nodes(state.scheduleType()).size() > 1;
            if (timeout > 0 && ++travel.ticks >= timeout && maySkip) {
                if (state.scheduleType() == AreaType.WORK) {
                    var next = state.next();
                    rotate(
                            maid,
                            next.destination(
                                    AreaType.WORK,
                                    RoutePlanner.to(
                                            next.plan(),
                                            next.destinationId(),
                                            maid.position(),
                                            level.dimension().location())),
                            level);
                } else {
                    var route =
                            RoutePlanner.nearest(
                                    state.plan(),
                                    state.scheduleType(),
                                    maid.position(),
                                    state.destinationId(),
                                    level.dimension().location());
                    if (route != null)
                        rotate(maid, state.destination(state.scheduleType(), route), level);
                }
                return;
            }
            if (maid.tickCount % 20 == 0
                    || !maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                BehaviorUtils.setWalkAndLookTargetMemories(maid, travel.target, 0.7f, 0);
            }
            return;
        }
        if (state.travelling()) {
            clearWork(maid, level);
            state = state.withTravel(false);
            WorkspaceAccess.writeState(maid, state, true);
            maid.getSchedulePos().restrictTo(maid);
            Travel travel = TRAVEL.get(maid);
            if (travel != null) travel.ticks = 0;
            return;
        }
        if (state.scheduleType() != AreaType.WORK || !maid.getBrain().isActive(Activity.WORK))
            return;
        WorkspaceState counted = state.countWork(Config.WORK_TICKS.get());
        if (counted.travelling())
            rotate(
                    maid,
                    counted.destination(
                            AreaType.WORK,
                            RoutePlanner.to(
                                    counted.plan(),
                                    counted.destinationId(),
                                    maid.position(),
                                    level.dimension().location())),
                    level);
        else WorkspaceAccess.writeState(maid, counted, false);
    }

    private static boolean waitForDimension(
            EntityMaid maid, WorkspaceState state, ServerLevel level) {
        if (state.plan().inDimension(state.navigationId(), level.dimension().location()))
            return false;
        if (!state.waitingDimension()) {
            WorkspaceAccess.writeState(maid, state.withWaitingDimension(true), true);
            clearWork(maid, level);
        }
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        TRAVEL.remove(maid);
        return true;
    }

    public static void apply(EntityMaid maid, WorkspacePlan plan) {
        boolean wasActive = WorkspaceLogic.active(maid) != null;
        WorkspaceAccess.writeState(maid, WorkspaceState.initial(plan), true);
        TRAVEL.remove(maid);
        prepareSchedule(maid, true);
        if (maid.level() instanceof ServerLevel level) {
            if (wasActive || WorkspaceLogic.active(maid) != null) clearWork(maid, level);
            // Replacing a plan can remove the current type: restore its original center
            // immediately.
            maid.getSchedulePos().restrictTo(maid);
        }
    }

    public static WorkspaceState prepareSchedule(EntityMaid maid) {
        return prepareSchedule(maid, false);
    }

    private static WorkspaceState prepareSchedule(EntityMaid maid, boolean force) {
        WorkspaceState state = WorkspaceLogic.state(maid);
        if (state == null
                || !maid.isHomeModeEnable()
                || !(maid.level() instanceof ServerLevel level)
                // SchedulePos.load calls restrictTo before vanilla transfer sets the final
                // position.
                || LOADING.contains(maid)) return state;
        AreaType type = AreaType.from(maid.getScheduleDetail());
        if (type == null) return state;
        if (state.plan().nodes(type).isEmpty()) {
            if (force || !state.suspended() || state.scheduleType() != type) {
                state = state.suspend(type);
                WorkspaceAccess.writeState(maid, state, true);
                TRAVEL.remove(maid);
                if (!force) {
                    clearWork(maid, level);
                    // The new schedule marker makes the nested restrictTo call fall through to TLM.
                    maid.getSchedulePos().restrictTo(maid);
                }
            }
            return state;
        }
        if (!force
                && !state.suspended()
                && state.scheduleType() == type
                && state.plan().type(state.destinationId()) == type) return state;
        RoutePlanner.Route route = planScheduleRoute(maid, state, type, level);
        WorkspaceState next = state.destination(type, route);
        WorkspaceAccess.writeState(maid, next, true);
        TRAVEL.remove(maid);
        clearWork(maid, level);
        return next;
    }

    private static RoutePlanner.Route planScheduleRoute(
            EntityMaid maid, WorkspaceState state, AreaType type, ServerLevel level) {
        return type == AreaType.WORK
                ? RoutePlanner.to(
                        state.plan(),
                        state.workDestination(),
                        maid.position(),
                        level.dimension().location())
                : RoutePlanner.nearest(
                        state.plan(), type, maid.position(), null, level.dimension().location());
    }

    public static void clear(EntityMaid maid) {
        boolean wasActive = WorkspaceLogic.active(maid) != null;
        WorkspaceAccess.writeState(
                maid,
                WorkspaceState.initial(WorkspacePlan.empty(maid.level().dimension().location())),
                true);
        TRAVEL.remove(maid);
        if (wasActive && maid.level() instanceof ServerLevel level) {
            clearWork(maid, level);
            maid.getSchedulePos().restrictTo(maid);
        }
    }

    private static void rotate(EntityMaid maid, WorkspaceState next, ServerLevel level) {
        clearWork(maid, level);
        WorkspaceAccess.writeState(maid, next, true);
        TRAVEL.remove(maid);
        maid.getSchedulePos().restrictTo(maid);
    }

    private static void clearWork(EntityMaid maid, ServerLevel level) {
        for (var behavior : maid.getBrain().getRunningBehaviors()) {
            if (behavior instanceof WorkspaceBehaviorGate)
                behavior.doStop(level, maid, level.getGameTime());
        }
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    public static BlockPos navigationTarget(EntityMaid maid) {
        WorkspaceState state = WorkspaceLogic.active(maid);
        return state == null || WorkspaceLogic.waitingForDimension(maid)
                ? maid.blockPosition()
                : state.navigationArea().navigationCenter();
    }

    private WorkspaceController() {}
}
