package com.erobrine.tlmcw.network;

import com.erobrine.tlmcw.TouhouLittleMaidCustomWorkspace;
import com.erobrine.tlmcw.workspace.*;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

public record CompassActionPayload(int action, int mode, UUID node, BlockPos min, BlockPos max)
        implements CustomPacketPayload {
    public static final int CLEAR = 1,
            DELETE = 2,
            SET_MODE = 3,
            MARK = 4,
            OPEN_RENAME = 5,
            OPEN_SCHEDULE = 6,
            CANCEL_ROUTE = 7;
    public static final Type<CompassActionPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            TouhouLittleMaidCustomWorkspace.MODID, "compass_action"));
    public static final StreamCodec<ByteBuf, CompassActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    CompassActionPayload::action,
                    ByteBufCodecs.VAR_INT,
                    CompassActionPayload::mode,
                    ByteBufCodecs.optional(ByteBufCodecs.fromCodec(UUIDUtil.CODEC)),
                    p -> Optional.ofNullable(p.node()),
                    BlockPos.STREAM_CODEC,
                    CompassActionPayload::min,
                    BlockPos.STREAM_CODEC,
                    CompassActionPayload::max,
                    (action, mode, node, min, max) ->
                            new CompassActionPayload(action, mode, node.orElse(null), min, max));

    public CompassActionPayload(int action, UUID node, BlockPos min, BlockPos max) {
        this(action, 0, node, min, max);
    }

    public CompassActionPayload(int action, int mode, BlockPos min, BlockPos max) {
        this(action, mode, null, min, max);
    }

    public static CompassActionPayload simple(int action) {
        return new CompassActionPayload(action, 0, null, BlockPos.ZERO, BlockPos.ZERO);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CompassActionPayload p, IPayloadContext context) {
        context.enqueueWork(
                () -> {
                    if (context.player() instanceof ServerPlayer player) execute(player, p);
                });
    }

    public static void execute(ServerPlayer player, CompassActionPayload p) {
        ItemStack stack = player.getMainHandItem();
        if (!CompassEditor.isSmartCompass(stack)) return;
        if (p.action == CANCEL_ROUTE) {
            CompassEditor.cancelRoute(stack, player);
            return;
        }
        if (p.action == SET_MODE) {
            CompassEditor.setMode(stack, player, p.mode);
            return;
        }
        if (p.action == CLEAR) {
            CompassEditor.clearNodes(stack, player);
            return;
        }
        if (p.action == DELETE && stack.has(WorkspaceComponents.ROUTE_ANCHOR.get())) {
            CompassEditor.cancelRoute(stack, player);
            return;
        }
        WorkspacePlan plan = WorkspaceAccess.plan(stack);
        if (plan == null) {
            CompassEditor.message(
                    player, WorkspaceAccess.blocked(stack) ? "data_unreadable" : "no_areas");
            return;
        }
        boolean rename = p.action == OPEN_RENAME;
        if (rename || p.action == OPEN_SCHEDULE) {
            if (CompassEditor.mode(stack)
                    != (rename ? CompassEditor.RENAME_AREAS : CompassEditor.SCHEDULE_OPTIONS))
                return;
            if (rename && !matches(plan, p, player, false)) return;
            PacketDistributor.sendToPlayer(
                    player,
                    new OpenCompassEditorPayload(
                            player.getInventory().selected, rename ? p.node : null, plan));
            return;
        }
        if (p.action != DELETE && p.action != MARK) return;
        boolean route = CompassEditor.mode(stack) == CompassEditor.ROUTE_NODES;
        if (p.action == MARK && CompassEditor.mode(stack) != CompassEditor.MARK_AREAS) return;
        if (p.action == DELETE && CompassEditor.mode(stack) != CompassEditor.WORK_AREAS && !route)
            return;
        if (!matches(plan, p, player, route)) return;
        if (p.action == MARK) {
            CompassEditor.mark(stack, player, p.node);
            return;
        }
        WorkspaceAccess.writePlan(stack, plan.removeNode(p.node, !route));
        CompassEditor.cancelSelection(stack);
        CompassEditor.message(
                player,
                route ? "route_deleted" : "area_deleted",
                plan.areaIds().indexOf(p.node) + 1);
    }

    private static boolean matches(
            WorkspacePlan plan, CompassActionPayload p, ServerPlayer player, boolean points) {
        var n = plan.node(p.node);
        if (n == null
                || !points && !n.isArea()
                || !n.dimension().equals(player.level().dimension().location())) return false;
        return n.min().equals(p.min)
                && n.max().equals(p.max)
                && Objects.equals(CompassEditor.selectedNode(plan, player, points), p.node);
    }
}
