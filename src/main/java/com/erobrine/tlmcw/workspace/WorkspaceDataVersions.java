package com.erobrine.tlmcw.workspace;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;

/** Storage schema versions, independent of the mod release or network protocol version. */
public final class WorkspaceDataVersions {
    public static final String FIELD = "data_version";
    public static final int PLAN_VERSION = 1;
    public static final int MAID_STATE_VERSION = 1;

    public static <T> Codec<T> plan(Codec<T> body) { return versioned(body, Format.PLAN); }
    public static <T> Codec<T> maidState(Codec<T> body) { return versioned(body, Format.MAID_STATE); }

    private enum Format {
        PLAN(PLAN_VERSION), MAID_STATE(MAID_STATE_VERSION);
        final int current;
        Format(int current) { this.current = current; }
    }

    private static <T> Codec<T> versioned(Codec<T> body, Format format) {
        return new Codec<>() {
            @Override public <O> DataResult<Pair<T, O>> decode(DynamicOps<O> ops, O input) {
                return migrate(new Dynamic<>(ops, input), format)
                        .flatMap(updated -> body.decode(ops, updated.getValue()));
            }
            @Override public <O> DataResult<O> encode(T value, DynamicOps<O> ops, O prefix) {
                return body.encode(value, ops, prefix).flatMap(encoded ->
                        ops.mergeToMap(encoded, ops.createString(FIELD), ops.createInt(format.current)));
            }
        };
    }

    private static <O> DataResult<Dynamic<O>> migrate(Dynamic<O> input, Format format) {
        // Reading the map first distinguishes a missing version from malformed non-object data.
        return input.getOps().getMap(input.getValue()).flatMap(map -> {
            O rawVersion = map.get(FIELD);
            // JsonOps treats explicit JSON null like an absent value in MapLike.get().
            if (rawVersion == null && map.entries().anyMatch(entry -> entry.getFirst().equals(input.getOps().createString(FIELD))))
                return DataResult.error(() -> "Invalid null " + FIELD + " for " + format);
            DataResult<Integer> version = rawVersion == null ? DataResult.success(0)
                    : input.getOps().getNumberValue(rawVersion).flatMap(number -> {
                        int value = number.intValue();
                        if (value < 0 || number.doubleValue() != value)
                            return DataResult.error(() -> "Invalid " + FIELD + " for " + format + ": " + number);
                        return DataResult.success(value);
                    });
            return version.flatMap(value -> {
                if (value > format.current) return DataResult.error(() ->
                        "Unsupported " + format + " data version " + value + "; supported up to " + format.current);
                Dynamic<O> updated = input;
                for (int oldVersion = value; oldVersion < format.current; oldVersion++) {
                    // Apply each migration to raw data before the current shape is decoded.
                    updated = switch (format) {
                        case PLAN -> migratePlanStep(updated, oldVersion);
                        case MAID_STATE -> migrateMaidStateStep(updated, oldVersion);
                    };
                    updated = updated.set(FIELD, updated.createInt(oldVersion + 1));
                }
                return DataResult.success(updated);
            });
        });
    }

    private static <O> Dynamic<O> migratePlanStep(Dynamic<O> input, int oldVersion) {
        return switch (oldVersion) {
            // Releases up to 1.3.2 had no version field. Their existing optional defaults
            // still supply work types, empty names/graph and the default schedule.
            case 0 -> input;
            default -> throw new IllegalStateException("Missing plan migration from " + oldVersion);
        };
    }

    private static <O> Dynamic<O> migrateMaidStateStep(Dynamic<O> input, int oldVersion) {
        return switch (oldVersion) {
            // The nested plan runs its own version/migration codec independently.
            case 0 -> input;
            default -> throw new IllegalStateException("Missing maid state migration from " + oldVersion);
        };
    }

    private WorkspaceDataVersions() {}
}
