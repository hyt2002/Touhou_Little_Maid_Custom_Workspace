package com.erobrine.tlmcw.workspace;

import com.erobrine.tlmcw.Config;
import com.erobrine.tlmcw.item.WorkspaceItems;
import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.UUID;

public final class CompassEditor {
    public static final int WORK_AREAS = 0,
            ROUTE_NODES = 1,
            MARK_AREAS = 2,
            APPLY_TO_MAID = 3,
            RENAME_AREAS = 4,
            SCHEDULE_OPTIONS = 5,
            READ_FROM_MAID = 6,
            TOOL_COUNT = 7;
    // Display order is independent of the persisted mode IDs used by existing compasses.
    public static final java.util.List<Integer> TOOL_ORDER =
            java.util.List.of(
                    READ_FROM_MAID,
                    WORK_AREAS,
                    ROUTE_NODES,
                    MARK_AREAS,
                    RENAME_AREAS,
                    SCHEDULE_OPTIONS,
                    APPLY_TO_MAID);

    public static boolean isSmartCompass(ItemStack stack) {
        return stack.is(WorkspaceItems.KAPPA_SMART_COMPASS.get());
    }

    public static int mode(ItemStack stack) {
        return stack.getOrDefault(WorkspaceComponents.EDIT_MODE.get(), WORK_AREAS);
    }

    public static void cancelSelection(ItemStack stack) {
        stack.remove(WorkspaceComponents.FIRST_CORNER.get());
    }

    public static void setMode(ItemStack stack, Player player, int mode) {
        if (mode < WORK_AREAS || mode >= TOOL_COUNT) return;
        stack.set(WorkspaceComponents.EDIT_MODE.get(), mode);
        cancelSelection(stack);
        message(
                player,
                switch (mode) {
                    case ROUTE_NODES -> "mode_route";
                    case MARK_AREAS -> "mode_mark";
                    case APPLY_TO_MAID -> "mode_apply";
                    case RENAME_AREAS -> "mode_rename";
                    case SCHEDULE_OPTIONS -> "mode_schedule";
                    case READ_FROM_MAID -> "mode_read";
                    default -> "mode_work";
                });
    }

    public static void clearNodes(ItemStack stack, Player player) {
        if (mode(stack) != WORK_AREAS && mode(stack) != ROUTE_NODES) {
            message(player, "clear_edit_tool");
            return;
        }
        if (WorkspaceAccess.blocked(stack)) {
            message(player, "data_unreadable");
            return;
        }
        boolean routeMode = mode(stack) == ROUTE_NODES;
        WorkspacePlan plan = WorkspaceAccess.plan(stack);
        if (plan != null)
            WorkspaceAccess.writePlan(
                    stack, routeMode ? plan.clearRoutePoints() : plan.clearWorkAreas());
        cancelSelection(stack);
        message(player, routeMode ? "route_points_cleared" : "work_areas_cleared");
    }

    public static void message(Player player, String key, Object... args) {
        player.displayClientMessage(CompassHelp.text(key, args), true);
    }

    public static InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        if (context.getHand() != InteractionHand.MAIN_HAND) return InteractionResult.CONSUME;
        if (mode(stack) == ROUTE_NODES) return routeClick(stack, player);
        if (mode(stack) == MARK_AREAS) return markClick(stack, player);
        if (mode(stack) == APPLY_TO_MAID || mode(stack) == READ_FROM_MAID) {
            message(player, "aim_maid");
            return InteractionResult.CONSUME;
        }
        if (mode(stack) == RENAME_AREAS || mode(stack) == SCHEDULE_OPTIONS)
            return InteractionResult.CONSUME;
        if (player.isShiftKeyDown()) {
            cancelSelection(stack);
            message(player, "selection_cancelled");
            return InteractionResult.CONSUME;
        }
        WorkspacePlan plan = WorkspaceAccess.plan(stack);
        if (WorkspaceAccess.blocked(stack)) {
            message(player, "data_unreadable");
            return InteractionResult.CONSUME;
        }
        if (plan == null) {
            plan = WorkspacePlan.empty(player.level().dimension().location());
            WorkspaceAccess.writePlan(stack, plan);
            stack.remove(WorkspaceComponents.FIRST_CORNER.get());
        }
        SelectionCorner corner = stack.get(WorkspaceComponents.FIRST_CORNER.get());
        if (corner != null && !corner.dimension().equals(player.level().dimension().location())) {
            cancelSelection(stack);
            corner = null;
        }
        BlockPos first = corner == null ? null : corner.pos();
        if (first == null) {
            if (plan.areas().size() >= Config.MAX_AREAS.get()) {
                message(player, "too_many", Config.MAX_AREAS.get());
                return InteractionResult.CONSUME;
            }
            stack.set(
                    WorkspaceComponents.FIRST_CORNER.get(),
                    new SelectionCorner(
                            player.level().dimension().location(),
                            context.getClickedPos().immutable()));
            message(player, "first_corner");
        } else {
            WorkArea area = WorkArea.between(first, context.getClickedPos());
            if (!valid(area, player)) {
                message(player, "too_large");
                return InteractionResult.CONSUME;
            }
            if (plan.areas().size() >= Config.MAX_AREAS.get()) {
                message(player, "too_many", Config.MAX_AREAS.get());
                return InteractionResult.CONSUME;
            }
            plan = plan.addArea(player.level().dimension().location(), area);
            WorkspaceAccess.writePlan(stack, plan);
            stack.remove(WorkspaceComponents.FIRST_CORNER.get());
            message(
                    player,
                    "area_added",
                    plan.areas().size(),
                    area.sizeX(),
                    area.sizeY(),
                    area.sizeZ());
        }
        return InteractionResult.CONSUME;
    }

    public static UUID selectedNode(WorkspacePlan plan, Player player, boolean points) {
        Vec3 eye = player.getEyePosition();
        Vec3 end =
                eye.add(
                        player.getLookAngle()
                                .scale(
                                        player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE)
                                                + 1));
        return NodeSelection.pick(plan, eye, end, points, player.level().dimension().location());
    }

    public static InteractionResult routeClick(ItemStack stack, Player player) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (WorkspaceAccess.blocked(stack)) {
            message(player, "data_unreadable");
            return InteractionResult.CONSUME;
        }
        WorkspacePlan plan = WorkspaceAccess.plan(stack);
        if (plan == null || !plan.hasNodes()) {
            message(player, "select_route_start");
            return InteractionResult.CONSUME;
        }
        UUID anchor = stack.get(WorkspaceComponents.ROUTE_ANCHOR.get());
        UUID selected = selectedNode(plan, player, true);
        if (anchor == null) {
            if (selected == null) message(player, "select_route_start");
            else {
                stack.set(WorkspaceComponents.ROUTE_ANCHOR.get(), selected);
                message(player, "route_start");
            }
            return InteractionResult.CONSUME;
        }
        if (plan.node(anchor) == null) {
            message(player, "route_anchor_missing");
            return InteractionResult.CONSUME;
        }
        RouteGraph graph = plan.graph();
        if (selected == null) {
            BlockPos feet = player.blockPosition().immutable();
            if (!valid(WorkArea.between(feet, feet), player)) {
                message(player, "too_large");
                return InteractionResult.CONSUME;
            }
            selected =
                    plan.nodeList().stream()
                            .filter(
                                    n ->
                                            !n.isArea()
                                                    && n.dimension()
                                                            .equals(
                                                                    player.level()
                                                                            .dimension()
                                                                            .location())
                                                    && n.min().equals(feet))
                            .map(WorkspaceNode::id)
                            .findFirst()
                            .orElse(null);
            if (selected == null) {
                if (plan.nodes(AreaType.WAYPOINT).size() >= RouteGraph.MAX_POINTS) {
                    message(player, "graph_full");
                    return InteractionResult.CONSUME;
                }
                WorkspaceNode point =
                        WorkspaceNode.waypoint(player.level().dimension().location(), feet);
                plan = plan.addNode(point);
                selected = point.id();
            }
        }
        if (!anchor.equals(selected)
                && !graph.edges().contains(new RouteEdge(anchor, selected))
                && graph.edges().size() >= RouteGraph.MAX_EDGES) {
            message(player, "graph_full");
            return InteractionResult.CONSUME;
        }
        graph = graph.connect(anchor, selected);
        WorkspaceAccess.writePlan(stack, plan.withGraph(graph));
        stack.set(WorkspaceComponents.ROUTE_ANCHOR.get(), selected);
        message(
                player,
                "route_connected",
                plan.nodes(AreaType.WAYPOINT).size(),
                graph.edges().size());
        return InteractionResult.CONSUME;
    }

    private static boolean validShape(WorkArea area) {
        return area.sizeX() <= Config.MAX_EDGE.get()
                && area.sizeY() <= Config.MAX_EDGE.get()
                && area.sizeZ() <= Config.MAX_EDGE.get()
                && area.volume() <= Config.MAX_VOLUME.get();
    }

    public static void cancelRoute(ItemStack stack, Player player) {
        if (stack.remove(WorkspaceComponents.ROUTE_ANCHOR.get()) != null)
            message(player, "route_cancelled");
    }

    public static boolean valid(WorkArea area, Player player) {
        return area.sizeX() <= Config.MAX_EDGE.get()
                && area.sizeY() <= Config.MAX_EDGE.get()
                && area.sizeZ() <= Config.MAX_EDGE.get()
                && area.volume() <= Config.MAX_VOLUME.get()
                && area.min().getY() >= player.level().getMinBuildHeight()
                && area.max().getY() < player.level().getMaxBuildHeight()
                && player.level().getWorldBorder().isWithinBounds(area.min())
                && player.level().getWorldBorder().isWithinBounds(area.max());
    }

    public static InteractionResult markClick(ItemStack stack, Player player) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        WorkspacePlan plan = WorkspaceAccess.plan(stack);
        if (plan == null) {
            message(player, "aim_area");
            return InteractionResult.CONSUME;
        }
        UUID selected = selectedNode(plan, player, false);
        if (selected == null) message(player, "aim_area");
        else mark(stack, player, selected);
        return InteractionResult.CONSUME;
    }

    public static void mark(ItemStack stack, Player player, UUID node) {
        WorkspacePlan plan = WorkspaceAccess.plan(stack);
        if (plan == null || plan.node(node) == null || !plan.node(node).isArea()) return;
        AreaType type = plan.type(node).next();
        WorkspaceAccess.writePlan(stack, plan.withType(node, type));
        message(
                player,
                "area_marked",
                plan.areaIds().indexOf(node) + 1,
                Component.translatable(type.translationKey()));
    }

    public static InteractionResult interactMaid(
            ItemStack stack, Player player, EntityMaid maid, InteractionHand hand) {
        return mode(stack) == READ_FROM_MAID
                ? read(stack, player, maid, hand)
                : apply(stack, player, maid, hand);
    }

    private static InteractionResult read(
            ItemStack stack, Player player, EntityMaid maid, InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (hand != InteractionHand.MAIN_HAND || !maid.isOwnedBy(player)) {
            message(player, "read_not_owner");
            return InteractionResult.CONSUME;
        }
        if (WorkspaceAccess.blocked(stack) || WorkspaceAccess.blocked(maid)) {
            message(player, "data_unreadable");
            return InteractionResult.CONSUME;
        }
        // Read the assigned plan even when Home is disabled or this schedule uses TLM's native
        // area.
        WorkspaceState state = WorkspaceLogic.state(maid);
        if (state == null) {
            message(player, "maid_no_config");
            return InteractionResult.CONSUME;
        }
        WorkspacePlan plan = state.plan();
        // Plans and their nested data are immutable; future draft edits replace this snapshot.
        WorkspaceAccess.writePlan(stack, plan);
        cancelSelection(stack);
        message(
                player,
                "maid_read",
                plan.nodes(AreaType.WORK).size(),
                plan.nodes(AreaType.IDLE).size(),
                plan.nodes(AreaType.SLEEP).size());
        return InteractionResult.CONSUME;
    }

    public static InteractionResult apply(
            ItemStack stack, Player player, EntityMaid maid, InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (mode(stack) != APPLY_TO_MAID) {
            message(player, "select_apply_tool");
            return InteractionResult.CONSUME;
        }
        if (hand != InteractionHand.MAIN_HAND || !maid.isOwnedBy(player)) {
            message(player, "not_owner");
            return InteractionResult.CONSUME;
        }
        if (WorkspaceAccess.blocked(stack) || WorkspaceAccess.blocked(maid)) {
            message(player, "data_unreadable");
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown()) {
            WorkspaceController.clear(maid);
            message(player, "maid_cleared");
            return InteractionResult.CONSUME;
        }
        WorkspacePlan plan = WorkspaceAccess.plan(stack);
        if (plan == null || plan.areas().isEmpty()) {
            message(player, "no_areas");
            return InteractionResult.CONSUME;
        }
        if (plan.areas().size() > Config.MAX_AREAS.get()
                || plan.nodeList().stream()
                        .anyMatch(
                                n ->
                                        !validShape(n.bounds())
                                                || n.dimension()
                                                                .equals(
                                                                        player.level()
                                                                                .dimension()
                                                                                .location())
                                                        && !valid(n.bounds(), player))) {
            message(player, "too_large");
            return InteractionResult.CONSUME;
        }
        WorkspaceController.apply(maid, plan);
        message(
                player,
                "maid_applied",
                plan.nodes(AreaType.WORK).size(),
                plan.nodes(AreaType.IDLE).size(),
                plan.nodes(AreaType.SLEEP).size());
        return InteractionResult.CONSUME;
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isSmartCompass(event.getItemStack())) event.setUseBlock(TriState.FALSE);
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isSmartCompass(event.getEntity().getMainHandItem())) event.setCanceled(true);
    }

    public static void onAttackEntity(AttackEntityEvent event) {
        if (isSmartCompass(event.getEntity().getMainHandItem())) event.setCanceled(true);
    }

    public static void onInteractMaid(InteractMaidEvent event) {
        if (!isSmartCompass(event.getStack())) return;
        // Run before TLM's crouch-to-sit/dismount handlers, which exempt only its original compass.
        interactMaid(
                event.getStack(), event.getPlayer(), event.getMaid(), InteractionHand.MAIN_HAND);
        event.setCanceled(true);
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) return;
        for (ItemStack stack : player.getInventory().items) {
            if (isSmartCompass(stack)) {
                var corner = stack.get(WorkspaceComponents.FIRST_CORNER.get());
                if (stack != player.getMainHandItem()
                        || corner != null
                                && !corner.dimension()
                                        .equals(player.level().dimension().location()))
                    cancelSelection(stack);
            }
        }
    }

    private CompassEditor() {}
}
