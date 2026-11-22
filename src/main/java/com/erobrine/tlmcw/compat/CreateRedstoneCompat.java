package com.erobrine.tlmcw.compat;

import com.erobrine.tlmcw.workspace.WirelessRedstoneCondition;

import net.neoforged.fml.ModList;

/** Keep optional Create classes behind a loading boundary, including on dedicated servers. */
public final class CreateRedstoneCompat {
    public static boolean available() {
        return ModList.get().isLoaded("create");
    }

    public static boolean matches(WirelessRedstoneCondition condition) {
        // Missing Create or a removed frequency item must never count as an unpowered signal.
        return available()
                && condition.first().available()
                && condition.second().available()
                && Loaded.powered(condition) == condition.powered();
    }

    private static final class Loaded {
        static boolean powered(WirelessRedstoneCondition condition) {
            var first =
                    com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency
                            .of(condition.first().stack());
            var second =
                    com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency
                            .of(condition.second().stack());
            // Same global, loaded-network query used by Create's train timetable. No range check.
            return com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.hasAnyLoadedPower(
                    net.createmod.catnip.data.Couple.create(first, second));
        }
    }

    private CreateRedstoneCompat() {}
}
