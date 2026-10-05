package com.erobrine.tlmcw.network;

import com.erobrine.tlmcw.TouhouLittleMaidCustomWorkspace;
import com.erobrine.tlmcw.workspace.CompassEdits;
import com.erobrine.tlmcw.workspace.WorkSchedule;
import com.erobrine.tlmcw.workspace.WorkspacePlan;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CompassEditPayload(int slot, WorkspacePlan expected, int area, String name, WorkSchedule schedule) implements CustomPacketPayload {
    public static final Type<CompassEditPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TouhouLittleMaidCustomWorkspace.MODID, "compass_edit"));
    public static final StreamCodec<ByteBuf, CompassEditPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CompassEditPayload::slot,
            ByteBufCodecs.fromCodec(WorkspacePlan.CODEC), CompassEditPayload::expected,
            ByteBufCodecs.VAR_INT, CompassEditPayload::area,
            ByteBufCodecs.stringUtf8(WorkspacePlan.MAX_NAME_LENGTH), CompassEditPayload::name,
            ByteBufCodecs.fromCodec(WorkSchedule.CODEC), CompassEditPayload::schedule, CompassEditPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(CompassEditPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> CompassEdits.save(context.player(), payload.slot(), payload.expected(), payload.area(), payload.name(), payload.schedule()));
    }
}
