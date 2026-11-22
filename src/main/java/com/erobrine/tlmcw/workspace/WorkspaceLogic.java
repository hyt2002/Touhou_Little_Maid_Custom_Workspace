package com.erobrine.tlmcw.workspace;

import com.erobrine.tlmcw.compat.LittleMaidCompat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class WorkspaceLogic {
    @Nullable
    public static WorkspaceState state(EntityMaid maid) {
        if (LittleMaidCompat.WORKSPACES == null) return null;
        WorkspaceState state = WorkspaceAccess.state(maid);
        return state == null || state.plan().nodeList().stream().noneMatch(WorkspaceNode::isArea)
                ? null
                : state;
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
                && inActiveArea(maid, state, maid.blockPosition());
    }

    public static List<WorkspaceNode> localAreas(EntityMaid maid, WorkspaceState state) {
        var dimension = maid.level().dimension().location();
        return state.plan().areaMembers(state.destinationId()).stream()
                .filter(node -> node.dimension().equals(dimension))
                .toList();
    }

    public static boolean isGroupedWork(WorkspaceState state) {
        if (state == null || state.scheduleType() != AreaType.WORK) return false;
        WorkspaceNode area = state.plan().node(state.destinationId());
        return area != null && !area.workGroup().isEmpty();
    }

    public static WorkArea localAreaAtFeet(EntityMaid maid, WorkspaceState state) {
        if (isGroupedWork(state)) {
            for (var area : localAreas(maid, state))
                if (area.bounds().contains(maid.blockPosition())) return area.bounds();
        }
        return state.activeArea();
    }

    public static boolean inActiveArea(EntityMaid maid, WorkspaceState state, BlockPos pos) {
        var dimension = maid.level().dimension().location();
        if (!isGroupedWork(state))
            return state.plan().inDimension(state.destinationId(), dimension)
                    && state.activeArea().contains(pos);
        return localAreas(maid, state).stream().anyMatch(node -> node.bounds().contains(pos));
    }

    public static boolean waitingForDimension(EntityMaid maid) {
        WorkspaceState state = active(maid);
        if (state == null
                || state.plan()
                        .inDimension(state.navigationId(), maid.level().dimension().location()))
            return false;
        if (!isGroupedWork(state) || !state.remainingRoute().isEmpty()) return true;
        if (state.travelling()) return !inActiveArea(maid, state, maid.blockPosition());
        return localAreas(maid, state).isEmpty();
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
