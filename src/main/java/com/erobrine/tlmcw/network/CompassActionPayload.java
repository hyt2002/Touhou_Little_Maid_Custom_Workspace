package com.erobrine.tlmcw.network;

import com.erobrine.tlmcw.TouhouLittleMaidCustomWorkspace;
import com.erobrine.tlmcw.workspace.CompassEditor;
import com.erobrine.tlmcw.workspace.WorkArea;
import com.erobrine.tlmcw.workspace.WorkspaceComponents;
import com.erobrine.tlmcw.workspace.WorkspacePlan;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

public record CompassActionPayload(int action, int index, BlockPos min, BlockPos max) implements CustomPacketPayload {
    public static final int CLEAR = 1, DELETE = 2, SET_MODE = 3, MARK = 4, OPEN_RENAME = 5, OPEN_SCHEDULE = 6;
    public static final Type<CompassActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            TouhouLittleMaidCustomWorkspace.MODID, "compass_action"));
    public static final StreamCodec<ByteBuf, CompassActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CompassActionPayload::action,
            ByteBufCodecs.VAR_INT, CompassActionPayload::index,
            BlockPos.STREAM_CODEC, CompassActionPayload::min,
            BlockPos.STREAM_CODEC, CompassActionPayload::max, CompassActionPayload::new);
    public static CompassActionPayload simple(int action) {
        return new CompassActionPayload(action, -1, BlockPos.ZERO, BlockPos.ZERO);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(CompassActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ItemStack stack = player.getMainHandItem();
            if (!CompassEditor.isSmartCompass(stack)) return;
            if (payload.action == SET_MODE) {
                CompassEditor.setMode(stack, player, payload.index);
            } else if (payload.action == CLEAR) {
                CompassEditor.clearNodes(stack, player);
            } else if (payload.action == OPEN_RENAME || payload.action == OPEN_SCHEDULE) {
                WorkspacePlan plan = stack.get(WorkspaceComponents.PLAN.get());
                if (plan == null || !plan.dimension().equals(player.level().dimension().location())) {
                    CompassEditor.message(player, "no_areas"); return;
                }
                boolean rename = payload.action == OPEN_RENAME;
                if (CompassEditor.mode(stack) != (rename ? CompassEditor.RENAME_AREAS : CompassEditor.SCHEDULE_OPTIONS)) return;
                if (rename) {
                    if (payload.index < 0 || payload.index >= plan.areas().size()) return;
                    WorkArea area = plan.areas().get(payload.index);
                    Integer selected = CompassEditor.selectedNode(plan, player, false);
                    if (!area.min().equals(payload.min) || !area.max().equals(payload.max)
                            || selected == null || selected != payload.index) return;
                }
                PacketDistributor.sendToPlayer(player, new OpenCompassEditorPayload(player.getInventory().selected, rename ? payload.index : -1, plan));
            } else if (payload.action == DELETE || payload.action == MARK) {
                WorkspacePlan plan = stack.get(WorkspaceComponents.PLAN.get());
                if (plan == null || !plan.dimension().equals(player.level().dimension().location())
                        || !plan.graph().hasNode(payload.index, plan.areas().size())) return;
                boolean routeMode = CompassEditor.mode(stack) == CompassEditor.ROUTE_NODES;
                boolean markMode = CompassEditor.mode(stack) == CompassEditor.MARK_AREAS;
                if (payload.action == MARK && (!markMode || payload.index < 0)) return;
                if (payload.action == DELETE && CompassEditor.mode(stack) != CompassEditor.WORK_AREAS && !routeMode) return;
                if (!routeMode && payload.index < 0) return;
                WorkArea area = plan.nodeArea(payload.index);
                if (!area.min().equals(payload.min) || !area.max().equals(payload.max)) return;
                Integer selected = CompassEditor.selectedNode(plan, player, routeMode);
                if (selected == null || selected != payload.index) return;
                if (payload.action == MARK) { CompassEditor.mark(stack, player, payload.index); return; }
                stack.set(WorkspaceComponents.PLAN.get(), plan.removeNode(payload.index, !routeMode));
                CompassEditor.cancelSelection(stack);
                CompassEditor.message(player, routeMode ? "route_deleted" : "area_deleted", payload.index + 1);
            }
        });
    }
}
