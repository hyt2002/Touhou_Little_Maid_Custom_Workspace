package com.erobrine.tlmcw;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue WORK_TICKS =
            BUILDER.comment(
                            "Default work game ticks per area and for newly added delay conditions;"
                                    + " custom schedules keep their own values.",
                            "Travel, rest, sleep and immobility do not count.")
                    .defineInRange("workTicksPerArea", 2400, 1, 1728000);
    public static final ModConfigSpec.IntValue TRAVEL_TIMEOUT_TICKS =
            BUILDER.comment(
                            "Skip an unreachable area after this many active game ticks; zero"
                                    + " disables skipping.")
                    .defineInRange("travelTimeoutTicks", 0, 0, 72000);
    public static final ModConfigSpec.IntValue MAX_AREAS =
            BUILDER.comment("Total cuboids across work, idle and sleep types in one plan.")
                    .defineInRange("maxAreasPerMaid", 16, 1, 64);
    public static final ModConfigSpec.IntValue MAX_EDGE =
            BUILDER.comment("Maximum number of blocks along an axis of one area.")
                    .defineInRange("maxAreaEdge", 64, 1, 128);
    public static final ModConfigSpec.IntValue MAX_VOLUME =
            BUILDER.comment("Maximum block volume of one cuboid.")
                    .defineInRange("maxAreaVolume", 131072, 1, 2097152);
    public static final ModConfigSpec.IntValue SEARCH_BUDGET =
            BUILDER.comment(
                            "Maximum candidate blocks checked by one task search. Search resumes on"
                                    + " its next check.")
                    .defineInRange("candidateBudget", 2048, 64, 65536);
    public static final ModConfigSpec.BooleanValue MAID_DIMENSION_COMPATIBILITY =
            BUILDER.comment(
                            "Enable vanilla maid dimension transfers, player-equivalent portal"
                                + " cooldown (10 game ticks), and route replanning after transfer.",
                            "Disable to restore TLM's dimension-transfer handling and the original"
                                + " entity portal cooldown. Saved areas and graphs are preserved.")
                    .define("maidDimensionCompatibility", true);
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>>
            DIMENSION_DISTANCE_MULTIPLIERS =
                    BUILDER.comment(
                                    "Distance multiplier per unordered dimension pair. Unconfigured"
                                        + " pairs use 1; same-dimension distances are unchanged.",
                                    "Format: dimensionA|dimensionB=positiveMultiplier. Example:"
                                            + " minecraft:overworld|minecraft:the_nether=8",
                                    "Configure each pair only once; both directions use the same"
                                            + " multiplier.")
                            .defineListAllowEmpty(
                                    "dimensionDistanceMultipliers",
                                    java.util.List.<String>of(),
                                    () -> "minecraft:overworld|minecraft:the_nether=1",
                                    com.erobrine.tlmcw.workspace.DimensionDistances::validEntry);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
