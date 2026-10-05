package com.erobrine.tlmcw.network;

import com.erobrine.tlmcw.TouhouLittleMaidCustomWorkspace;
import com.erobrine.tlmcw.workspace.WorkspacePlan;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A server-approved snapshot of the compass being edited. Client screen classes stay on the client. */
public record OpenCompassEditorPayload(int slot, int area, WorkspacePlan plan) implements CustomPacketPayload {
    public static final Type<OpenCompassEditorPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TouhouLittleMaidCustomWorkspace.MODID, "open_compass_editor"));
    public static final StreamCodec<ByteBuf, OpenCompassEditorPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenCompassEditorPayload::slot,
            ByteBufCodecs.VAR_INT, OpenCompassEditorPayload::area,
            ByteBufCodecs.fromCodec(WorkspacePlan.CODEC), OpenCompassEditorPayload::plan, OpenCompassEditorPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
