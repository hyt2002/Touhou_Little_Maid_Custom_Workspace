package com.erobrine.tlmcw.workspace;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;

/** Conditions store configuration only; world-dependent evaluation belongs to the controller. */
public sealed interface DepartureCondition permits DelayCondition, WirelessRedstoneCondition {
    Codec<DepartureCondition> CODEC =
            Codec.either(DelayCondition.CODEC, WirelessRedstoneCondition.CODEC)
                    .xmap(
                            value -> value.map(delay -> delay, wireless -> wireless),
                            value ->
                                    value instanceof DelayCondition delay
                                            ? Either.left(delay)
                                            : Either.right((WirelessRedstoneCondition) value));
}
