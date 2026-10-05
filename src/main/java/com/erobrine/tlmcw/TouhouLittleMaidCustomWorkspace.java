package com.erobrine.tlmcw;

import com.erobrine.tlmcw.network.CompassActionPayload;
import com.erobrine.tlmcw.network.CompassEditPayload;
import com.erobrine.tlmcw.network.OpenCompassEditorPayload;
import com.erobrine.tlmcw.item.WorkspaceItems;
import com.erobrine.tlmcw.workspace.CompassEditor;
import com.erobrine.tlmcw.workspace.WorkspaceComponents;
import com.erobrine.tlmcw.workspace.WorkspaceController;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(TouhouLittleMaidCustomWorkspace.MODID)
public final class TouhouLittleMaidCustomWorkspace {
    public static final String MODID = "touhou_little_maid_custom_workspace";

    public TouhouLittleMaidCustomWorkspace(IEventBus modBus, ModContainer container) {
        WorkspaceComponents.REGISTER.register(modBus);
        WorkspaceItems.REGISTER.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modBus.addListener(this::registerPayloads);
        modBus.addListener(WorkspaceItems::addToCreativeTab);
        NeoForge.EVENT_BUS.addListener(WorkspaceController::onMaidTick);
        NeoForge.EVENT_BUS.addListener(CompassEditor::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(CompassEditor::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(CompassEditor::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, CompassEditor::onInteractMaid);
        NeoForge.EVENT_BUS.addListener(CompassEditor::onPlayerTick);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("5").playToServer(CompassActionPayload.TYPE,
                CompassActionPayload.STREAM_CODEC, CompassActionPayload::handle)
                .playToServer(CompassEditPayload.TYPE, CompassEditPayload.STREAM_CODEC, CompassEditPayload::handle)
                .playToClient(OpenCompassEditorPayload.TYPE, OpenCompassEditorPayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.erobrine.tlmcw.client.CompassScreens.open(payload)));
    }
}
