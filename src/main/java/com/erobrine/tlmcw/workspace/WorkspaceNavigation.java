package com.erobrine.tlmcw.workspace;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class WorkspaceNavigation {
    private static final int STALLED_ROUTE_TICKS = 40;
    private static final Map<EntityMaid, GroupRoute> GROUP_ROUTES = new WeakHashMap<>();

    private static final class GroupRoute {
        final UUID workArea;
        final WalkTarget intent;
        final ResourceLocation dimension;
        final UUID targetArea;
        BlockPos goal;
        final List<UUID> nodes;
        int index;
        BlockPos lastPosition;
        long lastProgress;

        GroupRoute(
                WorkspaceState state,
                EntityMaid maid,
                WalkTarget intent,
                BlockPos goal,
                WorkspaceNode target) {
            workArea = state.destinationId();
            this.intent = intent;
            dimension = maid.level().dimension().location();
            this.goal = goal;
            targetArea = target.id();
            nodes = RoutePlanner.toWorkArea(state.plan(), target, maid.position(), dimension);
            lastPosition = maid.blockPosition();
            lastProgress = maid.level().getGameTime();
        }
    }

    public static boolean hasGroupIntent(EntityMaid maid, WorkspaceState state) {
        return WorkspaceLogic.isGroupedWork(state)
                && !state.travelling()
                && maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                && (state.plan()
                                .inDimension(
                                        state.destinationId(), maid.level().dimension().location())
                        || !WorkspaceLogic.localAreas(maid, state).isEmpty());
    }

    public static boolean allowsIntent(EntityMaid maid) {
        WorkspaceState state = WorkspaceLogic.active(maid);
        return state != null
                && (hasGroupIntent(maid, state)
                        || state.travelling() && !WorkspaceLogic.waitingForDimension(maid));
    }

    /** Only the sink sees this intermediate target. The Brain keeps the caller's final target. */
    public static BlockPos intermediateTarget(EntityMaid maid, WalkTarget intent) {
        WorkspaceState state = WorkspaceLogic.active(maid);
        if (state == null || intent == null || WorkspaceLogic.waitingForDimension(maid)) {
            clear(maid);
            return null;
        }
        if (state.travelling()) {
            clear(maid);
            return state.remainingRoute().isEmpty()
                    ? null
                    : state.navigationArea().navigationCenter();
        }
        if (!hasGroupIntent(maid, state)) {
            clear(maid);
            return null;
        }

        BlockPos goal = intent.getTarget().currentBlockPosition().immutable();
        ResourceLocation dimension = maid.level().dimension().location();
        GroupRoute route = GROUP_ROUTES.get(maid);
        boolean newIntent =
                route == null
                        || route.intent != intent
                        || !route.workArea.equals(state.destinationId())
                        || !route.dimension.equals(dimension);
        if (newIntent || !route.goal.equals(goal)) {
            WorkspaceNode target =
                    RoutePlanner.workTargetArea(
                            WorkspaceLogic.localAreas(maid, state), maid.blockPosition(), goal);
            if (newIntent || !route.targetArea.equals(target.id())) {
                route = new GroupRoute(state, maid, intent, goal, target);
                GROUP_ROUTES.put(maid, route);
            } else {
                route.goal = goal;
            }
        }
        // Consume each member once. Retain the finished route until the intent changes so the
        // final walk can leave the nearest member's box without selecting it as an entry again.
        BlockPos feet = maid.blockPosition();
        if (!feet.equals(route.lastPosition)) {
            route.lastPosition = feet;
            route.lastProgress = maid.level().getGameTime();
        }
        while (route.index < route.nodes.size()) {
            var node = state.plan().node(route.nodes.get(route.index));
            if (!node.bounds().contains(feet)) return node.bounds().navigationCenter();
            route.index++;
        }
        return null;
    }

    public static void clear(EntityMaid maid) {
        GROUP_ROUTES.remove(maid);
    }

    public static boolean usesGroupRoute(EntityMaid maid, WalkTarget intent) {
        var route = GROUP_ROUTES.get(maid);
        return route != null && route.intent == intent && !route.nodes.isEmpty();
    }

    public static boolean isGroupRouteStalled(EntityMaid maid, WalkTarget intent) {
        var route = GROUP_ROUTES.get(maid);
        return route != null
                && route.intent == intent
                && !route.nodes.isEmpty()
                && maid.level().getGameTime() - route.lastProgress >= STALLED_ROUTE_TICKS;
    }

    private WorkspaceNavigation() {}
}
