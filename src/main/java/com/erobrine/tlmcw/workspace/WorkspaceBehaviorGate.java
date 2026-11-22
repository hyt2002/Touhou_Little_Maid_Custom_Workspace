package com.erobrine.tlmcw.workspace;

import com.erobrine.tlmcw.mixin.BehaviorTimeAccessor;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;

/**
 * Preserves original task execution and pauses schedule actions until custom-area travel finishes.
 */
public final class WorkspaceBehaviorGate implements BehaviorControl<EntityMaid> {
    private final BehaviorControl<? super EntityMaid> original;
    private final Activity activity;
    private WalkTarget ownTarget;

    public WorkspaceBehaviorGate(BehaviorControl<? super EntityMaid> original, Activity activity) {
        this.original = original;
        this.activity = activity;
    }

    private boolean allowed(EntityMaid maid) {
        // The Brain can lag behind the schedule briefly. Never run an old
        // activity's actions inside the newly selected destination area.
        return WorkspaceLogic.mayWork(maid)
                && (WorkspaceLogic.active(maid) == null
                        || activity == Activity.CORE
                        || activity == maid.getScheduleDetail());
    }

    @Override
    public Behavior.Status getStatus() {
        return original.getStatus();
    }

    @Override
    public boolean tryStart(ServerLevel level, EntityMaid maid, long time) {
        if (!allowed(maid)) return false;
        if (!tracksTarget(maid)) return original.tryStart(level, maid, time);
        WalkTarget before = requestedTarget(maid);
        boolean started = original.tryStart(level, maid, time);
        ownTarget = started && requestedTarget(maid) != before ? requestedTarget(maid) : null;
        return started;
    }

    @Override
    public void tickOrStop(ServerLevel level, EntityMaid maid, long time) {
        if (!allowed(maid)
                && !(activity == Activity.WORK
                        && maid.getScheduleDetail() == Activity.WORK
                        && WorkspaceNavigation.hasGroupIntent(maid, WorkspaceLogic.active(maid)))) {
            doStop(level, maid, time);
            return;
        }
        if (!tracksTarget(maid)) {
            original.tickOrStop(level, maid, time);
            return;
        }
        WalkTarget before = requestedTarget(maid);
        if (before != null
                && before == ownTarget
                && original instanceof BehaviorTimeAccessor clock
                && maid.getNavigation().isInProgress()
                && WorkspaceNavigation.usesGroupRoute(maid, before)) {
            // Time spent making the additional trip should not consume this behavior's timeout.
            // Vanilla still evaluates canStillUse, so the task can finish or cancel normally.
            clock.tlmcw$setEndTimestamp(clock.tlmcw$getEndTimestamp() + 1);
        }
        original.tickOrStop(level, maid, time);
        if (original.getStatus() == Behavior.Status.STOPPED) ownTarget = null;
        else if (requestedTarget(maid) != before) ownTarget = requestedTarget(maid);
    }

    @Override
    public void doStop(ServerLevel level, EntityMaid maid, long time) {
        ownTarget = null;
        original.doStop(level, maid, time);
    }

    private boolean tracksTarget(EntityMaid maid) {
        return activity == Activity.WORK
                && WorkspaceLogic.isGroupedWork(WorkspaceLogic.active(maid));
    }

    private static WalkTarget requestedTarget(EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
    }

    @Override
    public String debugString() {
        return "WorkspaceGate[" + original.debugString() + "]";
    }
}
