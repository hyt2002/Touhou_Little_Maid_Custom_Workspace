package com.erobrine.tlmcw.workspace;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;

import java.util.function.Function;

/** Every persistence/network decoder migrates raw data before decoding the current schema. */
public final class WorkspaceDataVersions {
    public static final String FIELD = "data_version";
    public static final int PLAN_VERSION = 2, MAID_STATE_VERSION = 2;

    private enum Format {
        PLAN(PLAN_VERSION),
        MAID_STATE(MAID_STATE_VERSION);

        final int current;

        Format(int current) {
            this.current = current;
        }
    }

    public static <T> Codec<T> plan(Codec<T> body) {
        return versioned(body, Format.PLAN);
    }

    public static <T> Codec<T> maidState(Codec<T> body) {
        return versioned(body, Format.MAID_STATE);
    }

    private static <T> Codec<T> versioned(Codec<T> body, Format format) {
        return new Codec<>() {
            public <O> DataResult<Pair<T, O>> decode(DynamicOps<O> ops, O input) {
                try {
                    return migrate(new Dynamic<>(ops, input), format)
                            .flatMap(d -> body.decode(ops, d.getValue()));
                } catch (RuntimeException e) {
                    return DataResult.error(() -> "Invalid " + format + " data: " + e.getMessage());
                }
            }

            public <O> DataResult<O> encode(T value, DynamicOps<O> ops, O prefix) {
                return body.encode(value, ops, prefix)
                        .flatMap(
                                d ->
                                        ops.mergeToMap(
                                                d,
                                                ops.createString(FIELD),
                                                ops.createInt(format.current)));
            }
        };
    }

    private static <O> DataResult<Dynamic<O>> migrate(Dynamic<O> input, Format format) {
        return input.getOps()
                .getMap(input.getValue())
                .flatMap(
                        map -> {
                            O raw = map.get(FIELD);
                            if (raw == null
                                    && map.entries()
                                            .anyMatch(
                                                    e ->
                                                            e.getFirst()
                                                                    .equals(
                                                                            input.getOps()
                                                                                    .createString(
                                                                                            FIELD))))
                                return DataResult.error(() -> "Invalid null data_version");
                            DataResult<Integer> version =
                                    raw == null
                                            ? DataResult.success(0)
                                            : input.getOps()
                                                    .getNumberValue(raw)
                                                    .flatMap(
                                                            n -> {
                                                                int v = n.intValue();
                                                                return v >= 0
                                                                                && n.doubleValue()
                                                                                        == v
                                                                        ? DataResult.success(v)
                                                                        : DataResult.error(
                                                                                () ->
                                                                                        "Invalid"
                                                                                            + " data_version");
                                                            });
                            return version.flatMap(
                                    v -> {
                                        if (v > format.current)
                                            return DataResult.error(
                                                    () ->
                                                            "Unsupported future "
                                                                    + format
                                                                    + " version "
                                                                    + v);
                                        Dynamic<O> updated = input;
                                        for (int step = v; step < format.current; step++) {
                                            updated =
                                                    switch (step) {
                                                        case 0 -> updated; // Unversioned releases
                                                        // have the V1 shape.
                                                        case 1 ->
                                                                format == Format.PLAN
                                                                        ? WorkspaceLegacyMigration
                                                                                .plan(updated)
                                                                        : WorkspaceLegacyMigration
                                                                                .state(updated);
                                                        default ->
                                                                throw new IllegalStateException(
                                                                        "Missing migration step");
                                                    };
                                            updated =
                                                    updated.set(FIELD, updated.createInt(step + 1));
                                        }
                                        return DataResult.success(updated);
                                    });
                        });
    }

    /**
     * TLM requires a non-null decoded value. Keep unknown/broken data inert and round-trip its
     * original raw value.
     */
    public static <T> Codec<T> preserving(
            Codec<T> codec, Function<Dynamic<?>, T> fallback, Function<T, Dynamic<?>> raw) {
        return new Codec<>() {
            public <O> DataResult<Pair<T, O>> decode(DynamicOps<O> ops, O input) {
                DataResult<Pair<T, O>> result = codec.decode(ops, input);
                if (result.result().isPresent()) return result;
                com.mojang.logging.LogUtils.getLogger()
                        .warn(
                                "Preserving unreadable workspace data: {}",
                                result.error().orElseThrow().message());
                return DataResult.success(
                        Pair.of(fallback.apply(new Dynamic<>(ops, input)), ops.empty()));
            }

            public <O> DataResult<O> encode(T value, DynamicOps<O> ops, O prefix) {
                Dynamic<?> saved = raw.apply(value);
                return saved == null
                        ? codec.encode(value, ops, prefix)
                        : DataResult.success(saved.convert(ops).getValue());
            }
        };
    }

    private WorkspaceDataVersions() {}
}
