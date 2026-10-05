package com.erobrine.tlmcw.item;

import com.erobrine.tlmcw.TouhouLittleMaidCustomWorkspace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.Item;

public final class WorkspaceItems {
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(TouhouLittleMaidCustomWorkspace.MODID);
    public static final DeferredItem<KappaSmartCompassItem> KAPPA_SMART_COMPASS = REGISTER.registerItem(
            "kappa_smart_compass", KappaSmartCompassItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "main"))) {
            event.accept(KAPPA_SMART_COMPASS.get());
        }
    }

    private WorkspaceItems() {}
}
