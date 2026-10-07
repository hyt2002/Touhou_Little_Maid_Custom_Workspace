package com.erobrine.tlmcw.workspace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record SelectionCorner(ResourceLocation dimension, BlockPos pos) {
    public static final Codec<SelectionCorner> CODEC =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            ResourceLocation.CODEC
                                                    .fieldOf("dimension")
                                                    .forGetter(SelectionCorner::dimension),
                                            BlockPos.CODEC
                                                    .fieldOf("pos")
                                                    .forGetter(SelectionCorner::pos))
                                    .apply(i, SelectionCorner::new));
}
