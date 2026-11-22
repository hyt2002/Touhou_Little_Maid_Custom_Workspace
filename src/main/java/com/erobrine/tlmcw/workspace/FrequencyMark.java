package com.erobrine.tlmcw.workspace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

import java.util.Objects;
import java.util.Optional;

/** Create frequencies compare item identity and dyed color, not counts or arbitrary item NBT. */
public record FrequencyMark(Optional<ResourceLocation> item, Optional<Integer> color) {
    public static final FrequencyMark EMPTY = new FrequencyMark(Optional.empty(), Optional.empty());
    public static final Codec<FrequencyMark> CODEC =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            ResourceLocation.CODEC
                                                    .optionalFieldOf("item")
                                                    .forGetter(FrequencyMark::item),
                                            Codec.intRange(0, 0xFFFFFF)
                                                    .optionalFieldOf("color")
                                                    .forGetter(FrequencyMark::color))
                                    .apply(i, FrequencyMark::new));

    public FrequencyMark {
        Objects.requireNonNull(item);
        Objects.requireNonNull(color);
        if (color.isPresent() && (item.isEmpty() || color.get() < 0 || color.get() > 0xFFFFFF))
            throw new IllegalArgumentException("Invalid frequency color");
        if (item.filter(ResourceLocation.withDefaultNamespace("air")::equals).isPresent())
            throw new IllegalArgumentException("Use an empty mark for an empty frequency");
    }

    public static FrequencyMark from(ItemStack stack) {
        if (stack.isEmpty()) return EMPTY;
        var dyed = stack.get(DataComponents.DYED_COLOR);
        return new FrequencyMark(
                Optional.of(BuiltInRegistries.ITEM.getKey(stack.getItem())),
                dyed == null ? Optional.empty() : Optional.of(dyed.rgb()));
    }

    public boolean available() {
        return item.isEmpty() || BuiltInRegistries.ITEM.getOptional(item.get()).isPresent();
    }

    public ItemStack stack() {
        if (item.isEmpty()) return ItemStack.EMPTY;
        var found = BuiltInRegistries.ITEM.getOptional(item.get());
        if (found.isEmpty()) return ItemStack.EMPTY;
        var stack = new ItemStack(found.get());
        color.ifPresent(rgb -> stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb, true)));
        return stack;
    }
}
