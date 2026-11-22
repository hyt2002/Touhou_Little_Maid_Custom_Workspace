package com.erobrine.tlmcw.gametest;

import static com.erobrine.tlmcw.gametest.V2Fixtures.*;

import com.erobrine.tlmcw.TouhouLittleMaidCustomWorkspace;
import com.erobrine.tlmcw.compat.CreateRedstoneCompat;
import com.erobrine.tlmcw.item.WorkspaceItems;
import com.erobrine.tlmcw.network.CompassEditPayload;
import com.erobrine.tlmcw.workspace.*;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder(TouhouLittleMaidCustomWorkspace.MODID)
@PrefixGameTestTemplate(false)
public final class WirelessRedstoneGameTests {
    private static WorkspacePlan plan(GameTestHelper helper, DepartureCondition condition) {
        var p =
                fixturePlan(
                        helper.getLevel().dimension().location(),
                        List.of(
                                WorkArea.between(
                                        helper.absolutePos(new BlockPos(3, 1, 3)),
                                        helper.absolutePos(new BlockPos(5, 3, 5))),
                                WorkArea.between(
                                        helper.absolutePos(new BlockPos(12, 1, 3)),
                                        helper.absolutePos(new BlockPos(14, 3, 5)))));
        return p.withSchedule(
                new WorkSchedule(
                        List.of(
                                new WorkScheduleEntry(n(0), condition),
                                new WorkScheduleEntry(n(1), new DelayCondition(1000))),
                        true));
    }

    private static EntityMaid maid(GameTestHelper helper, WorkspacePlan plan) {
        for (int x = 1; x <= 20; x++)
            for (int z = 1; z <= 12; z++) helper.setBlock(x, 1, z, Blocks.STONE);
        var maid = new EntityMaid(helper.getLevel());
        maid.setTame(true, true);
        maid.setOwnerUUID(UUID.randomUUID());
        maid.setSchedule(MaidSchedule.ALL);
        maid.getSchedulePos().setWorkPos(helper.absolutePos(new BlockPos(3, 2, 3)));
        maid.getSchedulePos().setDimension(helper.getLevel().dimension().location());
        maid.getSchedulePos().setConfigured(true);
        maid.setHomeModeEnable(true);
        maid.moveTo(helper.absoluteVec(new Vec3(3.5, 2, 3.5)));
        helper.getLevel().addFreshEntity(maid);
        // These synchronous checks run before the first real brain tick chooses its schedule.
        maid.getBrain().setDefaultActivity(Activity.WORK);
        maid.getBrain().useDefaultActivity();
        WorkspaceAccess.writeState(maid, WorkspaceState.initial(plan).withTravel(false), true);
        return maid;
    }

    private static WirelessRedstoneCondition signal(boolean powered) {
        return new WirelessRedstoneCondition(
                FrequencyMark.from(new ItemStack(Items.AMETHYST_SHARD)),
                FrequencyMark.from(new ItemStack(Items.NAUTILUS_SHELL)),
                powered);
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void frequencyMarksAndMixedSchedulesSurviveItemEntityAndNetworkRoundTrips(
            GameTestHelper helper) {
        var original = new ItemStack(Items.LEATHER_HELMET);
        original.set(DataComponents.DYED_COLOR, new DyedItemColor(0xCC3344, false));
        original.set(DataComponents.CUSTOM_NAME, Component.literal("Not part of the frequency"));
        var before = original.copy();
        var mark = FrequencyMark.from(original);
        helper.assertTrue(
                ItemStack.matches(original, before)
                        && mark.color().orElseThrow() == 0xCC3344
                        && mark.stack().getCount() == 1
                        && mark.stack().get(DataComponents.CUSTOM_NAME) == null,
                "Marking consumed an item, lost its color or copied unrelated item data");
        helper.assertTrue(
                FrequencyMark.from(new ItemStack(Items.DIAMOND, 32))
                        .equals(FrequencyMark.from(new ItemStack(Items.DIAMOND))),
                "Item counts changed a frequency");
        var condition = new WirelessRedstoneCondition(mark, FrequencyMark.EMPTY, false);
        var p = plan(helper, condition);
        var compass = new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get());
        WorkspaceAccess.writePlan(compass, p);
        var copied =
                ItemStack.parseOptional(
                        helper.getLevel().registryAccess(),
                        (CompoundTag) compass.save(helper.getLevel().registryAccess()));
        helper.assertTrue(
                p.equals(WorkspaceAccess.plan(copied)), "Compass round-trip lost its condition");
        var maid = maid(helper, p);
        var saved = WorkspaceAccess.state(maid);
        var loaded = new EntityMaid(helper.getLevel());
        loaded.load(maid.saveWithoutId(new CompoundTag()));
        helper.assertTrue(
                saved.equals(WorkspaceAccess.state(loaded)),
                "Maid round-trip lost its condition or identities");
        var payload = new CompassEditPayload(0, p, null, "", p.schedule());
        var buffer = Unpooled.buffer();
        try {
            CompassEditPayload.STREAM_CODEC.encode(buffer, payload);
            helper.assertTrue(
                    payload.equals(CompassEditPayload.STREAM_CODEC.decode(buffer)),
                    "Network codec did not preserve value equality for the editor's expected plan");
        } finally {
            buffer.release();
        }
        var missing =
                new FrequencyMark(
                        Optional.of(ResourceLocation.parse("missing_mod:frequency")),
                        Optional.empty());
        helper.assertTrue(
                !CreateRedstoneCompat.matches(new WirelessRedstoneCondition(missing, mark, false)),
                "Missing frequency item was treated as an unpowered frequency");
        helper.succeed();
    }

    @GameTest(batch = "wireless_absence", template = "test_empty", timeoutTicks = 100)
    public static void missingCreateNeverSatisfiesEitherWirelessCondition(GameTestHelper helper) {
        if (CreateRedstoneCompat.available()) {
            helper.succeed();
            return;
        }
        for (boolean powered : new boolean[] {true, false}) {
            var p = plan(helper, signal(powered));
            var maid = maid(helper, p);
            var before = WorkspaceAccess.state(maid);
            for (int i = 0; i < 40; i++) WorkspaceController.onMaidTick(new MaidTickEvent(maid));
            helper.assertTrue(
                    before.equals(WorkspaceAccess.state(maid)),
                    "Missing Create advanced a wireless condition or counted a default delay");
            maid.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "wireless_network", template = "test_empty", timeoutTicks = 100)
    public static void globalSignalsIgnoreDistanceAndDimensionAndRespectPowerState(
            GameTestHelper helper) {
        if (!CreateRedstoneCompat.available()) {
            helper.succeed();
            return;
        }
        Loaded.globalSignals(helper);
    }

    @GameTest(batch = "wireless_block", template = "test_empty", timeoutTicks = 100)
    public static void actualCreateRedstoneLinkAdvancesTheMaidSchedule(GameTestHelper helper) {
        if (!CreateRedstoneCompat.available()) {
            helper.succeed();
            return;
        }
        Loaded.actualBlock(helper);
    }

    /**
     * This class, including its Create interfaces and signatures, is never loaded without Create.
     */
    private static final class Loaded {
        private static final class Emitter
                implements com.simibubi.create.content.redstone.link.IRedstoneLinkable {
            final net.createmod.catnip.data.Couple<
                            com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler
                                    .Frequency>
                    key;
            int power;

            Emitter(WirelessRedstoneCondition signal) {
                key =
                        net.createmod.catnip.data.Couple.create(
                                com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler
                                        .Frequency.of(signal.first().stack()),
                                com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler
                                        .Frequency.of(signal.second().stack()));
            }

            @Override
            public int getTransmittedStrength() {
                return power;
            }

            @Override
            public void setReceivedStrength(int power) {}

            @Override
            public boolean isListening() {
                return false;
            }

            @Override
            public boolean isAlive() {
                return true;
            }

            @Override
            public BlockPos getLocation() {
                return new BlockPos(100000, 128, 100000);
            }

            @Override
            public net.createmod.catnip.data.Couple<
                            com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler
                                    .Frequency>
                    getNetworkKey() {
                return key;
            }
        }

        static void globalSignals(GameTestHelper helper) {
            var on = signal(true);
            var off = signal(false);
            var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
            var network = com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER;
            var first = new Emitter(on);
            var second = new Emitter(on);
            network.addToNetwork(nether, first);
            network.addToNetwork(helper.getLevel(), second);
            try {
                var maid = maid(helper, plan(helper, on));
                WorkspaceController.onMaidTick(new MaidTickEvent(maid));
                helper.assertTrue(
                        n(0).equals(WorkspaceAccess.state(maid).destinationId()),
                        "Unpowered transmitter satisfied a powered condition");
                first.power = 15;
                network.updateNetworkOf(nether, first);
                helper.assertTrue(
                        CreateRedstoneCompat.matches(on) && !CreateRedstoneCompat.matches(off),
                        "Did not receive a distant transmitter in another dimension");
                helper.assertTrue(
                        !CreateRedstoneCompat.matches(
                                new WirelessRedstoneCondition(on.second(), on.first(), true)),
                        "The two frequency slots lost their order");
                maid.setHomeModeEnable(false);
                WorkspaceController.onMaidTick(new MaidTickEvent(maid));
                helper.assertTrue(
                        n(0).equals(WorkspaceAccess.state(maid).destinationId()),
                        "Signal advanced the schedule with Home disabled");
                maid.setHomeModeEnable(true);
                WorkspaceController.onMaidTick(new MaidTickEvent(maid));
                // The arrival tick ends travel; conditions are checked on the next work tick.
                WorkspaceController.onMaidTick(new MaidTickEvent(maid));
                helper.assertTrue(
                        n(1).equals(WorkspaceAccess.state(maid).destinationId()),
                        "Powered signal did not advance after the arrival and work ticks");
                maid.discard();

                var waitingOff = maid(helper, plan(helper, off));
                first.power = 0;
                second.power = 15;
                network.updateNetworkOf(nether, first);
                network.updateNetworkOf(helper.getLevel(), second);
                WorkspaceController.onMaidTick(new MaidTickEvent(waitingOff));
                helper.assertTrue(
                        n(0).equals(WorkspaceAccess.state(waitingOff).destinationId()),
                        "Unpowered condition ignored another powered transmitter");
                network.removeFromNetwork(helper.getLevel(), second);
                helper.assertTrue(
                        CreateRedstoneCompat.matches(off),
                        "Removing the last powered transmitter left a stale powered result");
                WorkspaceController.onMaidTick(new MaidTickEvent(waitingOff));
                helper.assertTrue(
                        n(1).equals(WorkspaceAccess.state(waitingOff).destinationId()),
                        "Unpowered signal did not advance the schedule");
                waitingOff.discard();
            } finally {
                network.removeFromNetwork(nether, first);
                network.removeFromNetwork(helper.getLevel(), second);
            }
            helper.succeed();
        }

        static void actualBlock(GameTestHelper helper) {
            var condition = signal(true);
            var maid = maid(helper, plan(helper, condition));
            var linkPos = new BlockPos(7, 2, 3);
            var linkBlock =
                    BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:redstone_link"));
            helper.setBlock(
                    linkPos,
                    linkBlock
                            .defaultBlockState()
                            .setValue(
                                    net.minecraft.world.level.block.state.properties
                                            .BlockStateProperties.FACING,
                                    net.minecraft.core.Direction.UP));
            helper.runAtTickTime(
                    3,
                    () -> {
                        var be =
                                (com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity)
                                        helper.getLevel()
                                                .getBlockEntity(helper.absolutePos(linkPos));
                        helper.assertTrue(be != null, "Create link block entity was not placed");
                        var behaviour =
                                be.getBehaviour(
                                        com.simibubi.create.content.redstone.link.LinkBehaviour
                                                .TYPE);
                        helper.assertTrue(
                                behaviour != null, "Create link behaviour was not initialized");
                        behaviour.setFrequency(true, condition.first().stack());
                        behaviour.setFrequency(false, condition.second().stack());
                        helper.setBlock(7, 2, 4, Blocks.REDSTONE_BLOCK);
                    });
            helper.runAtTickTime(
                    10,
                    () -> {
                        helper.assertTrue(
                                CreateRedstoneCompat.matches(condition),
                                "Real powered Create link did not appear in the global network");
                        helper.assertTrue(
                                n(1).equals(WorkspaceAccess.state(maid).destinationId()),
                                "Real Create redstone link did not advance the maid's timetable");
                        helper.succeed();
                    });
        }
    }
}
