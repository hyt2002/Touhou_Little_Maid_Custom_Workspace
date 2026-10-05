package com.erobrine.tlmcw.workspace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Undirected endpoints: work areas use nonnegative indices, waypoints use -index-1. */
public record RouteEdge(int a, int b) {
    public static final Codec<RouteEdge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("a").forGetter(RouteEdge::a),
            Codec.INT.fieldOf("b").forGetter(RouteEdge::b)
    ).apply(instance, RouteEdge::new));

    public RouteEdge {
        if (a == b) throw new IllegalArgumentException("A route edge must connect different nodes");
        if (a > b) { int swap = a; a = b; b = swap; }
    }
    public boolean touches(int node) { return a == node || b == node; }
    public int other(int node) { return a == node ? b : a; }
}
