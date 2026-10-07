package com.erobrine.tlmcw.workspace;

import com.erobrine.tlmcw.Config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.event.config.ModConfigEvent;

import java.util.*;

/**
 * Unordered dimension pairs because the path graph is undirected. Reloads replace one immutable
 * table.
 */
public final class DimensionDistances {
    private record Pair(ResourceLocation a, ResourceLocation b) {
        Pair {
            if (a.compareTo(b) > 0) {
                var t = a;
                a = b;
                b = t;
            }
        }
    }

    public static final class Table {
        private final Map<Pair, Double> values;

        private Table(Map<Pair, Double> values) {
            this.values = Map.copyOf(values);
        }

        public double multiplier(ResourceLocation a, ResourceLocation b) {
            return a.equals(b) ? 1 : values.getOrDefault(new Pair(a, b), 1.0);
        }

        public double distance(Vec3 a, ResourceLocation ad, Vec3 b, ResourceLocation bd) {
            return a.distanceTo(b) * multiplier(ad, bd);
        }
    }

    private record Entry(Pair pair, double multiplier) {}

    private static volatile Table current = new Table(Map.of());

    private static Entry parse(String text) {
        String[] parts = text.strip().split("=", -1);
        if (parts.length != 2)
            throw new IllegalArgumentException("Expected dimensionA|dimensionB=multiplier");
        String[] dims = parts[0].split("\\|", -1);
        if (dims.length != 2) throw new IllegalArgumentException("Expected two dimensions");
        var a = ResourceLocation.parse(dims[0].strip());
        var b = ResourceLocation.parse(dims[1].strip());
        double value = Double.parseDouble(parts[1].strip());
        if (a.equals(b) || !Double.isFinite(value) || value <= 0)
            throw new IllegalArgumentException(
                    "Multiplier must be finite and positive, between distinct dimensions");
        return new Entry(new Pair(a, b), value);
    }

    public static boolean validEntry(Object value) {
        if (!(value instanceof String s)) return false;
        try {
            parse(s);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static Table table(List<? extends String> entries) {
        var values = new HashMap<Pair, Double>();
        for (String text : entries) {
            var e = parse(text);
            if (values.putIfAbsent(e.pair(), e.multiplier()) != null)
                throw new IllegalArgumentException("Duplicate dimension pair");
        }
        return new Table(values);
    }

    private static void reload() {
        try {
            current = table(Config.DIMENSION_DISTANCE_MULTIPLIERS.get());
        } catch (IllegalArgumentException e) {
            com.mojang.logging.LogUtils.getLogger()
                    .warn(
                            "Invalid dimension distance table; keeping previous values: {}",
                            e.getMessage());
        }
    }

    public static void onLoading(ModConfigEvent.Loading e) {
        if (e.getConfig().getSpec() == Config.SPEC) reload();
    }

    public static void onReloading(ModConfigEvent.Reloading e) {
        if (e.getConfig().getSpec() == Config.SPEC) reload();
    }

    public static void onUnloading(ModConfigEvent.Unloading e) {
        if (e.getConfig().getSpec() == Config.SPEC) current = new Table(Map.of());
    }

    public static Table current() {
        return current;
    }

    private DimensionDistances() {}
}
