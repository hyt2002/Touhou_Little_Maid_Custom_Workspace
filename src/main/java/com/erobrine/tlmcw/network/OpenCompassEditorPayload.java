package com.erobrine.tlmcw.network;

import com.erobrine.tlmcw.TouhouLittleMaidCustomWorkspace;
import com.erobrine.tlmcw.workspace.WorkspacePlan;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public record OpenCompassEditorPayload(int slot, UUID area, WorkspacePlan plan)
        implements CustomPacketPayload {
    public static final Type<OpenCompassEditorPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            TouhouLittleMaidCustomWorkspace.MODID, "open_compass_editor"));
    public static final StreamCodec<ByteBuf, OpenCompassEditorPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    OpenCompassEditorPayload::slot,
                    ByteBufCodecs.optional(ByteBufCodecs.fromCodec(UUIDUtil.CODEC)),
                    p -> Optional.ofNullable(p.area()),
                    ByteBufCodecs.fromCodec(WorkspacePlan.CODEC),
                    OpenCompassEditorPayload::plan,
                    (slot, area, plan) ->
                            new OpenCompassEditorPayload(slot, area.orElse(null), plan));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
