package com.erobrine.tlmcw.network;

import com.erobrine.tlmcw.TouhouLittleMaidCustomWorkspace;
import com.erobrine.tlmcw.workspace.*;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

public record CompassEditPayload(
        int slot, WorkspacePlan expected, UUID area, String name, WorkSchedule schedule)
        implements CustomPacketPayload {
    public static final Type<CompassEditPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            TouhouLittleMaidCustomWorkspace.MODID, "compass_edit"));
    public static final StreamCodec<ByteBuf, CompassEditPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    CompassEditPayload::slot,
                    ByteBufCodecs.fromCodec(WorkspacePlan.CODEC),
                    CompassEditPayload::expected,
                    ByteBufCodecs.optional(ByteBufCodecs.fromCodec(UUIDUtil.CODEC)),
                    p -> Optional.ofNullable(p.area()),
                    ByteBufCodecs.stringUtf8(WorkspacePlan.MAX_NAME_LENGTH),
                    CompassEditPayload::name,
                    ByteBufCodecs.fromCodec(WorkSchedule.CODEC),
                    CompassEditPayload::schedule,
                    (slot, plan, area, name, schedule) ->
                            new CompassEditPayload(slot, plan, area.orElse(null), name, schedule));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CompassEditPayload p, IPayloadContext c) {
        c.enqueueWork(
                () ->
                        CompassEdits.save(
                                c.player(), p.slot, p.expected, p.area, p.name, p.schedule));
    }
}
