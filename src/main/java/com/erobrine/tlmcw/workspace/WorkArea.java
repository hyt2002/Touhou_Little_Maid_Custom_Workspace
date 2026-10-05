package com.erobrine.tlmcw.workspace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** Integer block bounds are inclusive; the rendered AABB includes the whole endpoint blocks. */
public record WorkArea(BlockPos min, BlockPos max) {
    public static final Codec<WorkArea> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("min").forGetter(WorkArea::min),
            BlockPos.CODEC.fieldOf("max").forGetter(WorkArea::max)
    ).apply(instance, WorkArea::new));

    public WorkArea {
        min = min.immutable();
        max = max.immutable();
        if (min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()) {
            throw new IllegalArgumentException("Reversed work area bounds");
        }
    }

    public static WorkArea between(BlockPos a, BlockPos b) {
        return new WorkArea(new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())));
    }
    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }
    public int sizeX() { return max.getX() - min.getX() + 1; }
    public int sizeY() { return max.getY() - min.getY() + 1; }
    public int sizeZ() { return max.getZ() - min.getZ() + 1; }
    public long volume() { return (long) sizeX() * sizeY() * sizeZ(); }
    public BlockPos center() {
        return new BlockPos(min.getX() + (sizeX() - 1) / 2,
                min.getY() + (sizeY() - 1) / 2, min.getZ() + (sizeZ() - 1) / 2);
    }
    /** Block containing the geometric center of the selected whole blocks. */
    public BlockPos navigationCenter() { return BlockPos.containing(bounds().getCenter()); }
    public int horizontalRadius() {
        BlockPos c = center();
        int x = Math.max(c.getX() - min.getX(), max.getX() - c.getX());
        int z = Math.max(c.getZ() - min.getZ(), max.getZ() - c.getZ());
        return (int) Math.ceil(Math.sqrt((double) x * x + (double) z * z)) + 2;
    }
    public int verticalRadius() {
        return Math.max(center().getY() - min.getY(), max.getY() - center().getY()) + 2;
    }
    public BlockPos candidate(long index) {
        int x = (int) (index % sizeX());
        int z = (int) (index / sizeX() % sizeZ());
        int y = (int) (index / ((long) sizeX() * sizeZ()));
        return min.offset(x, y, z);
    }
    public AABB bounds() {
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);
    }
}
