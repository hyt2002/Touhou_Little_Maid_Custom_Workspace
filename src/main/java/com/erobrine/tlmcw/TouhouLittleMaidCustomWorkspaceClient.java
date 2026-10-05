package com.erobrine.tlmcw;

import com.erobrine.tlmcw.client.WorkspaceClient;
import com.erobrine.tlmcw.client.CompassToolMenu;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = TouhouLittleMaidCustomWorkspace.MODID, dist = Dist.CLIENT)
public final class TouhouLittleMaidCustomWorkspaceClient {
    public TouhouLittleMaidCustomWorkspaceClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(WorkspaceClient::registerKeys);
        NeoForge.EVENT_BUS.addListener(WorkspaceClient::onTick);
        NeoForge.EVENT_BUS.addListener(WorkspaceClient::onInput);
        NeoForge.EVENT_BUS.addListener(WorkspaceClient::onRender);
        NeoForge.EVENT_BUS.addListener(WorkspaceClient::onTooltip);
        NeoForge.EVENT_BUS.addListener(CompassToolMenu::scroll);
        NeoForge.EVENT_BUS.addListener(CompassToolMenu::render);
        if (Boolean.getBoolean("tlmcw.uiSmokeTest")) {
            try { Class.forName("com.erobrine.tlmcw.gametest.ClientUiSmokeTest").getMethod("start").invoke(null); }
            catch (ReflectiveOperationException exception) { throw new IllegalStateException("Development UI test unavailable", exception); }
        }
        if (Boolean.getBoolean("tlmcw.worldRenderTest")) {
            try { Class.forName("com.erobrine.tlmcw.gametest.ClientWorldRenderTest").getMethod("start").invoke(null); }
            catch (ReflectiveOperationException exception) { throw new IllegalStateException("Development world render test unavailable", exception); }
        }
        if (Boolean.getBoolean("tlmcw.compassControlsTest")) {
            try { Class.forName("com.erobrine.tlmcw.gametest.ClientCompassControlsTest").getMethod("start").invoke(null); }
            catch (ReflectiveOperationException exception) { throw new IllegalStateException("Development controls test unavailable", exception); }
        }
    }
}
