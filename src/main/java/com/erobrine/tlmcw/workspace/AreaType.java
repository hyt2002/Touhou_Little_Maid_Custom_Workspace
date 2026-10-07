package com.erobrine.tlmcw.workspace;

import com.mojang.serialization.Codec;

import net.minecraft.world.entity.schedule.Activity;

import java.util.Locale;

public enum AreaType {
    WORK,
    IDLE,
    SLEEP,
    WAYPOINT;
    public static final Codec<AreaType> CODEC =
            Codec.STRING.xmap(
                    value -> valueOf(value.toUpperCase(Locale.ROOT)),
                    value -> value.name().toLowerCase(Locale.ROOT));

    public AreaType next() {
        return values()[(ordinal() + 1) % 3];
    }

    public String translationKey() {
        return "tlmcw.area_type_" + name().toLowerCase(Locale.ROOT);
    }

    public static AreaType from(Activity activity) {
        if (activity == Activity.WORK) return WORK;
        if (activity == Activity.IDLE) return IDLE;
        return activity == Activity.REST ? SLEEP : null;
    }
}
