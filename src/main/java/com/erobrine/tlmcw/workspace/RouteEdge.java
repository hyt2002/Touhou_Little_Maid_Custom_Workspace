package com.erobrine.tlmcw.workspace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

import java.util.UUID;

public record RouteEdge(UUID a, UUID b) {
    public static final Codec<RouteEdge> CODEC =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            UUIDUtil.CODEC.fieldOf("a").forGetter(RouteEdge::a),
                                            UUIDUtil.CODEC.fieldOf("b").forGetter(RouteEdge::b))
                                    .apply(i, RouteEdge::new));

    public RouteEdge {
        if (a.equals(b)) throw new IllegalArgumentException("Self edge");
        if (a.compareTo(b) > 0) {
            UUID swap = a;
            a = b;
            b = swap;
        }
    }

    public boolean touches(UUID id) {
        return a.equals(id) || b.equals(id);
    }

    public UUID other(UUID id) {
        if (!touches(id)) throw new IllegalArgumentException("Not an endpoint");
        return a.equals(id) ? b : a;
    }
}
