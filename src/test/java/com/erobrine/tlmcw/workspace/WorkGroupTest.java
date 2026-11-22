package com.erobrine.tlmcw.workspace;

import static com.erobrine.tlmcw.gametest.V2Fixtures.*;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;

import java.util.List;

class WorkGroupTest {
    private static final ResourceLocation OVERWORLD =
            ResourceLocation.withDefaultNamespace("overworld");

    private static WorkspacePlan plan() {
        return fixturePlan(
                OVERWORLD,
                List.of(
                        WorkArea.between(new BlockPos(0, 64, 0), new BlockPos(2, 66, 2)),
                        WorkArea.between(new BlockPos(20, 64, 0), new BlockPos(22, 66, 2)),
                        WorkArea.between(new BlockPos(45, 64, 0), new BlockPos(47, 66, 2))));
    }

    @Test
    void groupingKeepsAreaIdentityAndCountsOnceInTheDefaultSchedule() {
        var grouped =
                plan().withName(n(0), "Kitchen")
                        .withWorkGroup(n(0), "  Restaurant ")
                        .withWorkGroup(n(1), "Restaurant");
        assertEquals(List.of(n(0), n(2)), grouped.defaultWorkDestinations());
        assertEquals(
                List.of(n(0), n(1)),
                grouped.areaMembers(n(1)).stream().map(WorkspaceNode::id).toList());
        assertEquals("Kitchen", grouped.name(n(0)));
        assertEquals(
                n(2),
                WorkspaceState.initial(grouped).withTravel(false).countWork(1).destinationId());
        var encoded = WorkspacePlan.CODEC.encodeStart(JsonOps.INSTANCE, grouped).getOrThrow();
        assertEquals(grouped, WorkspacePlan.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        var v2 =
                WorkspacePlan.CODEC
                        .encodeStart(JsonOps.INSTANCE, plan())
                        .getOrThrow()
                        .getAsJsonObject();
        v2.addProperty("data_version", 2);
        var migrated = WorkspacePlan.CODEC.parse(JsonOps.INSTANCE, v2).getOrThrow();
        assertEquals(List.of(n(0), n(1), n(2)), migrated.defaultWorkDestinations());
        assertEquals("", migrated.node(n(1)).workGroup());
        assertEquals("", grouped.withType(n(1), AreaType.IDLE).node(n(1)).workGroup());
    }

    @Test
    void picksClosestEntryInTheTargetsComponentAndDelegatesWithinTheSameBox() {
        var grouped =
                attach(
                        plan().withWorkGroup(n(0), "Restaurant").withWorkGroup(n(1), "Restaurant"),
                        new FixtureGraph(
                                        List.of(new BlockPos(12, 64, 2), new BlockPos(5, 64, 1)),
                                        List.of())
                                .connect(n(0), n(-1))
                                .connect(n(-1), n(1)));
        var members = grouped.areaMembers(n(0));
        WorkspaceNode target =
                RoutePlanner.workTargetArea(
                        members, new BlockPos(5, 64, 1), new BlockPos(24, 64, 1));
        assertEquals(n(1), target.id());
        assertEquals(
                List.of(n(0), n(-1), n(1)),
                RoutePlanner.toWorkArea(grouped, target, new Vec3(5.5, 64, 1.5), OVERWORLD));
        assertEquals(
                List.of(n(-1), n(1)),
                RoutePlanner.toWorkArea(grouped, target, new Vec3(11.5, 64, 1.5), OVERWORLD));
        assertTrue(
                RoutePlanner.toWorkArea(grouped, target, new Vec3(20.5, 64, 1.5), OVERWORLD)
                        .isEmpty());
    }
}
