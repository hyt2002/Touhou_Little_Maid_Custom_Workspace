package com.erobrine.tlmcw.workspace;

import com.erobrine.tlmcw.compat.LittleMaidCompat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;

public final class WorkspaceLogic {
    @Nullable
    public static WorkspaceState state(EntityMaid maid) {
        if (LittleMaidCompat.WORKSPACES == null) return null;
        WorkspaceState state = WorkspaceAccess.state(maid);
        return state == null || state.plan().areas().isEmpty() ? null : state;
    }

    @Nullable
    public static WorkspaceState active(EntityMaid maid) {
        WorkspaceState state = state(maid);
        AreaType type = AreaType.from(maid.getScheduleDetail());
        return state != null
                        && maid.isHomeModeEnable()
                        && state.scheduleType() == type
                        && !state.suspended()
                        && state.destinationId() != null
                        && state.plan().type(state.destinationId()) == type
                ? state
                : null;
    }

    public static boolean mayWork(EntityMaid maid) {
        WorkspaceState saved = state(maid);
        AreaType type = AreaType.from(maid.getScheduleDetail());
        if (saved == null
                || !maid.isHomeModeEnable()
                || type == null
                || saved.plan().nodes(type).isEmpty()) return true;
        WorkspaceState state = active(maid);
        return state != null
                && !state.travelling()
                && !waitingForDimension(maid)
                && state.plan()
                        .inDimension(state.destinationId(), maid.level().dimension().location())
                && state.activeArea().contains(maid.blockPosition());
    }

    public static boolean waitingForDimension(EntityMaid maid) {
        WorkspaceState state = active(maid);
        return state != null
                && !state.plan()
                        .inDimension(state.navigationId(), maid.level().dimension().location());
    }

    public static boolean searchChunksLoaded(ServerLevel level, WorkArea area) {
        int radius = area.horizontalRadius();
        var center = area.center();
        for (int x = (center.getX() - radius) >> 4; x <= (center.getX() + radius) >> 4; x++) {
            for (int z = (center.getZ() - radius) >> 4; z <= (center.getZ() + radius) >> 4; z++) {
                if (!level.hasChunk(x, z)) return false;
            }
        }
        return true;
    }

    private WorkspaceLogic() {}
}
