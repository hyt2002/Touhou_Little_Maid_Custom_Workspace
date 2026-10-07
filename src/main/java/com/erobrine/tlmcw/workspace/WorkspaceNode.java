package com.erobrine.tlmcw.workspace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record WorkspaceNode(
        UUID id,
        ResourceLocation dimension,
        AreaType type,
        String name,
        BlockPos min,
        BlockPos max) {
    public static final Codec<WorkspaceNode> CODEC =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            UUIDUtil.CODEC
                                                    .fieldOf("id")
                                                    .forGetter(WorkspaceNode::id),
                                            ResourceLocation.CODEC
                                                    .fieldOf("dimension")
                                                    .forGetter(WorkspaceNode::dimension),
                                            AreaType.CODEC
                                                    .fieldOf("type")
                                                    .forGetter(WorkspaceNode::type),
                                            Codec.string(0, WorkspacePlan.MAX_NAME_LENGTH)
                                                    .optionalFieldOf("name", "")
                                                    .forGetter(WorkspaceNode::name),
                                            BlockPos.CODEC
                                                    .fieldOf("min")
                                                    .forGetter(WorkspaceNode::min),
                                            BlockPos.CODEC
                                                    .fieldOf("max")
                                                    .forGetter(WorkspaceNode::max))
                                    .apply(i, WorkspaceNode::new));

    public WorkspaceNode {
        java.util.Objects.requireNonNull(id);
        java.util.Objects.requireNonNull(dimension);
        java.util.Objects.requireNonNull(type);
        min = min.immutable();
        max = max.immutable();
        name = WorkspacePlan.cleanName(name);
        if (min.getX() > max.getX()
                || min.getY() > max.getY()
                || min.getZ() > max.getZ()
                || type == AreaType.WAYPOINT && !min.equals(max))
            throw new IllegalArgumentException("Invalid node bounds");
    }

    public boolean isArea() {
        return type != AreaType.WAYPOINT;
    }

    public WorkArea bounds() {
        return new WorkArea(min, max);
    }

    public WorkspaceNode withName(String value) {
        return new WorkspaceNode(id, dimension, type, value, min, max);
    }

    public WorkspaceNode withType(AreaType value) {
        return new WorkspaceNode(id, dimension, value, name, min, max);
    }

    public static WorkspaceNode area(ResourceLocation dim, WorkArea area) {
        return new WorkspaceNode(UUID.randomUUID(), dim, AreaType.WORK, "", area.min(), area.max());
    }

    public static WorkspaceNode waypoint(ResourceLocation dim, BlockPos pos) {
        return new WorkspaceNode(UUID.randomUUID(), dim, AreaType.WAYPOINT, "", pos, pos);
    }
}
