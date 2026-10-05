package com.erobrine.tlmcw.workspace;

import com.erobrine.tlmcw.TouhouLittleMaidCustomWorkspace;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WorkspaceComponents {
    public static final DeferredRegister<DataComponentType<?>> REGISTER = DeferredRegister.create(
            Registries.DATA_COMPONENT_TYPE, TouhouLittleMaidCustomWorkspace.MODID);
    // Kept so original compasses saved by 1.0.0-1.0.2 still load; no longer controls any item behavior.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> CUBOID_MODE = REGISTER.register(
            "cuboid_mode", () -> DataComponentType.<Boolean>builder().persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WorkspacePlan>> PLAN = REGISTER.register(
            "workspace_plan", () -> DataComponentType.<WorkspacePlan>builder().persistent(WorkspacePlan.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(WorkspacePlan.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> EDIT_MODE = REGISTER.register(
            "edit_mode", () -> DataComponentType.<Integer>builder().persistent(Codec.intRange(0, CompassEditor.TOOL_COUNT - 1))
                    .networkSynchronized(ByteBufCodecs.VAR_INT).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ROUTE_ANCHOR = REGISTER.register(
            "route_anchor", () -> DataComponentType.<Integer>builder().networkSynchronized(ByteBufCodecs.VAR_INT).build());
    // Intentionally non-persistent: unfinished selections do not survive saving/rejoining.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> FIRST_CORNER = REGISTER.register(
            "first_corner", () -> DataComponentType.<BlockPos>builder().networkSynchronized(BlockPos.STREAM_CODEC).build());
    private WorkspaceComponents() {}
}
