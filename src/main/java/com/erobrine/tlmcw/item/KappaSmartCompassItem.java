package com.erobrine.tlmcw.item;

import com.erobrine.tlmcw.workspace.CompassEditor;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class KappaSmartCompassItem extends Item {
    public KappaSmartCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return CompassEditor.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand == InteractionHand.MAIN_HAND
                && CompassEditor.mode(stack) == CompassEditor.ROUTE_NODES) {
            CompassEditor.routeClick(stack, player);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (hand == InteractionHand.MAIN_HAND
                && CompassEditor.mode(stack) == CompassEditor.MARK_AREAS) {
            CompassEditor.markClick(stack, player);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(
            ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
        if (entity instanceof EntityMaid maid)
            return CompassEditor.interactMaid(stack, player, maid, hand);
        return super.interactLivingEntity(stack, player, entity, hand);
    }
}
