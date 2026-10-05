package com.erobrine.tlmcw.workspace;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.schedule.Activity;

/** Preserves original task execution and pauses schedule actions until custom-area travel finishes. */
public final class WorkspaceBehaviorGate implements BehaviorControl<EntityMaid> {
    private final BehaviorControl<? super EntityMaid> original;
    private final Activity activity;
    public WorkspaceBehaviorGate(BehaviorControl<? super EntityMaid> original, Activity activity) { this.original = original; this.activity = activity; }
    private boolean allowed(EntityMaid maid) {
        // The Brain can lag behind the schedule briefly. Never run an old
        // activity's actions inside the newly selected destination area.
        return WorkspaceLogic.mayWork(maid) && (WorkspaceLogic.active(maid) == null
                || activity == Activity.CORE || activity == maid.getScheduleDetail());
    }
    @Override public Behavior.Status getStatus() { return original.getStatus(); }
    @Override public boolean tryStart(ServerLevel level, EntityMaid maid, long time) {
        return allowed(maid) && original.tryStart(level, maid, time);
    }
    @Override public void tickOrStop(ServerLevel level, EntityMaid maid, long time) {
        if (allowed(maid)) original.tickOrStop(level, maid, time);
        else original.doStop(level, maid, time);
    }
    @Override public void doStop(ServerLevel level, EntityMaid maid, long time) { original.doStop(level, maid, time); }
    @Override public String debugString() { return "WorkspaceGate[" + original.debugString() + "]"; }
}
