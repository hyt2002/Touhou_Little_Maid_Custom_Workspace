package com.erobrine.tlmcw.workspace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;

public record WirelessRedstoneCondition(FrequencyMark first, FrequencyMark second, boolean powered)
        implements DepartureCondition {
    public static final Codec<WirelessRedstoneCondition> CODEC =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            Codec.STRING
                                                    .validate(
                                                            type ->
                                                                    type.equals(
                                                                                    "create_redstone_link")
                                                                            ? DataResult.success(
                                                                                    type)
                                                                            : DataResult.error(
                                                                                    () ->
                                                                                            "Unsupported"
                                                                                                + " departure"
                                                                                                + " condition:"
                                                                                                + " "
                                                                                                    + type))
                                                    .fieldOf("type")
                                                    .forGetter(c -> "create_redstone_link"),
                                            FrequencyMark.CODEC
                                                    .fieldOf("first")
                                                    .forGetter(WirelessRedstoneCondition::first),
                                            FrequencyMark.CODEC
                                                    .fieldOf("second")
                                                    .forGetter(WirelessRedstoneCondition::second),
                                            Codec.BOOL
                                                    .fieldOf("powered")
                                                    .forGetter(WirelessRedstoneCondition::powered))
                                    .apply(
                                            i,
                                            (type, first, second, powered) ->
                                                    new WirelessRedstoneCondition(
                                                            first, second, powered)));

    public WirelessRedstoneCondition {
        Objects.requireNonNull(first);
        Objects.requireNonNull(second);
    }

    public static WirelessRedstoneCondition empty() {
        return new WirelessRedstoneCondition(FrequencyMark.EMPTY, FrequencyMark.EMPTY, true);
    }
}
