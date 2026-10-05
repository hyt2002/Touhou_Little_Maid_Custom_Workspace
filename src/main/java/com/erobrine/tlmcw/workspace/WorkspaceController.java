package com.erobrine.tlmcw.workspace;

import com.erobrine.tlmcw.Config;
import com.erobrine.tlmcw.compat.LittleMaidCompat;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import java.util.Map;
import java.util.WeakHashMap;

public final class WorkspaceController {
    private static final Map<EntityMaid, Travel> TRAVEL = new WeakHashMap<>();
    private static final class Travel {
        final int areaIndex;
        final AreaType type;
        final WorkArea area;
        final BlockPos target;
        int ticks;
        Travel(WorkspaceState state) {
            this.areaIndex = state.activeIndex();
            this.type = state.scheduleType();
            this.area = state.navigationArea();
            this.target = area.navigationCenter();
        }
    }

    public static void onMaidTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        if (!(maid.level() instanceof ServerLevel level)) return;
        WorkspaceState saved = prepareSchedule(maid);
        if (saved == null) { TRAVEL.remove(maid); return; }
        WorkspaceState state = WorkspaceLogic.active(maid);
        if (state == null) {
            TRAVEL.remove(maid);
            if (!saved.travelling()) maid.setAndSyncData(LittleMaidCompat.WORKSPACES, saved.withTravel(true));
            return;
        }
        if (!maid.isEffectiveAi() || !maid.canBrainMoving() || maid.guiOpening || maid.getBrain().isActive(Activity.PANIC)) return;
        WorkArea area = state.activeArea();
        if (!state.travelling() && !area.contains(maid.blockPosition())) {
            state = state.destination(state.scheduleType(), RoutePlanner.to(state.plan(), state.activeIndex(), maid.position()));
            maid.setAndSyncData(LittleMaidCompat.WORKSPACES, state);
            clearWork(maid, level);
        }
        boolean advanced = false;
        while (state.travelling() && !state.remainingRoute().isEmpty() && state.navigationArea().contains(maid.blockPosition())) {
            state = state.advanceRoute();
            advanced = true;
        }
        if (advanced) {
            maid.setAndSyncData(LittleMaidCompat.WORKSPACES, state);
            clearWork(maid, level);
            TRAVEL.remove(maid);
            maid.getSchedulePos().restrictTo(maid);
        }
        if (!state.remainingRoute().isEmpty() || !area.contains(maid.blockPosition())) {
            if (!state.travelling()) {
                state = state.withTravel(true);
                maid.setAndSyncData(LittleMaidCompat.WORKSPACES, state);
                clearWork(maid, level);
            }
            Travel travel = TRAVEL.get(maid);
            WorkArea navigationArea = state.navigationArea();
            if (travel == null || travel.areaIndex != state.activeIndex() || travel.type != state.scheduleType() || !travel.area.equals(navigationArea)) {
                travel = new Travel(state);
                TRAVEL.put(maid, travel);
                maid.getSchedulePos().restrictTo(maid);
            }
            int timeout = Config.TRAVEL_TIMEOUT_TICKS.get();
            boolean maySkip = state.scheduleType() == AreaType.WORK ? state.hasNextWorkDestination() : state.plan().nodes(state.scheduleType()).size() > 1;
            if (timeout > 0 && ++travel.ticks >= timeout && maySkip) {
                if (state.scheduleType() == AreaType.WORK) {
                    var next = state.next();
                    rotate(maid, next.destination(AreaType.WORK, RoutePlanner.to(next.plan(), next.activeIndex(), maid.position())), level);
                } else {
                    var route = RoutePlanner.nearest(state.plan(), state.scheduleType(), maid.position(), state.activeIndex());
                    if (route != null) rotate(maid, state.destination(state.scheduleType(), route), level);
                }
                return;
            }
            if (maid.tickCount % 20 == 0 || !maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                BehaviorUtils.setWalkAndLookTargetMemories(maid, travel.target, 0.7f, 0);
            }
            return;
        }
        if (state.travelling()) {
            clearWork(maid, level);
            state = state.withTravel(false);
            maid.setAndSyncData(LittleMaidCompat.WORKSPACES, state);
            maid.getSchedulePos().restrictTo(maid);
            Travel travel = TRAVEL.get(maid);
            if (travel != null) travel.ticks = 0;
            return;
        }
        if (state.scheduleType() != AreaType.WORK || !maid.getBrain().isActive(Activity.WORK)) return;
        WorkspaceState counted = state.countWork(Config.WORK_TICKS.get());
        if (counted.travelling()) rotate(maid,
                counted.destination(AreaType.WORK, RoutePlanner.to(counted.plan(), counted.activeIndex(), maid.position())), level);
        else maid.setData(LittleMaidCompat.WORKSPACES, counted);
    }

    public static void apply(EntityMaid maid, WorkspacePlan plan) {
        boolean wasActive = WorkspaceLogic.active(maid) != null;
        maid.setAndSyncData(LittleMaidCompat.WORKSPACES, WorkspaceState.initial(plan));
        TRAVEL.remove(maid);
        prepareSchedule(maid, true);
        if (maid.level() instanceof ServerLevel level) {
            if (wasActive || WorkspaceLogic.active(maid) != null) clearWork(maid, level);
            // Replacing a plan can remove the current type: restore its original center immediately.
            maid.getSchedulePos().restrictTo(maid);
        }
    }
    public static WorkspaceState prepareSchedule(EntityMaid maid) { return prepareSchedule(maid, false); }
    private static WorkspaceState prepareSchedule(EntityMaid maid, boolean force) {
        WorkspaceState state = WorkspaceLogic.state(maid);
        if (state == null || !maid.isHomeModeEnable() || !(maid.level() instanceof ServerLevel level)
                || !state.plan().dimension().equals(level.dimension().location())) return state;
        AreaType type = AreaType.from(maid.getScheduleDetail());
        if (type == null) return state;
        if (state.plan().nodes(type).isEmpty()) {
            if (force || state.scheduleType() != type) {
                state = state.suspend(type);
                maid.setAndSyncData(LittleMaidCompat.WORKSPACES, state);
                TRAVEL.remove(maid);
                if (!force) {
                    clearWork(maid, level);
                    // The new schedule marker makes the nested restrictTo call fall through to TLM.
                    maid.getSchedulePos().restrictTo(maid);
                }
            }
            return state;
        }
        if (!force && state.scheduleType() == type && state.plan().type(state.activeIndex()) == type) return state;
        RoutePlanner.Route route = type == AreaType.WORK
                ? RoutePlanner.to(state.plan(), state.workIndex(), maid.position())
                : RoutePlanner.nearest(state.plan(), type, maid.position(), -1);
        WorkspaceState next = state.destination(type, route);
        maid.setAndSyncData(LittleMaidCompat.WORKSPACES, next);
        TRAVEL.remove(maid);
        clearWork(maid, level);
        return next;
    }
    public static void clear(EntityMaid maid) {
        boolean wasActive = WorkspaceLogic.active(maid) != null;
        maid.setAndSyncData(LittleMaidCompat.WORKSPACES,
                WorkspaceState.initial(WorkspacePlan.empty(maid.level().dimension().location())));
        TRAVEL.remove(maid);
        if (wasActive && maid.level() instanceof ServerLevel level) {
            clearWork(maid, level);
            maid.getSchedulePos().restrictTo(maid);
        }
    }
    private static void rotate(EntityMaid maid, WorkspaceState next, ServerLevel level) {
        clearWork(maid, level);
        maid.setAndSyncData(LittleMaidCompat.WORKSPACES, next);
        TRAVEL.remove(maid);
        maid.getSchedulePos().restrictTo(maid);
    }
    private static void clearWork(EntityMaid maid, ServerLevel level) {
        for (var behavior : maid.getBrain().getRunningBehaviors()) {
            if (behavior instanceof WorkspaceBehaviorGate) behavior.doStop(level, maid, level.getGameTime());
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
        return state == null ? maid.blockPosition() : state.navigationArea().navigationCenter();
    }
    private WorkspaceController() {}
}
