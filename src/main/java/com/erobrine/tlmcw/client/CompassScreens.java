package com.erobrine.tlmcw.client;

import com.erobrine.tlmcw.network.OpenCompassEditorPayload;
import com.erobrine.tlmcw.workspace.CompassEditor;
import com.erobrine.tlmcw.workspace.WorkspaceComponents;
import net.minecraft.client.Minecraft;

public final class CompassScreens {
    public static void open(OpenCompassEditorPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.player.getInventory().selected != payload.slot()
                || !CompassEditor.isSmartCompass(mc.player.getMainHandItem())) return;
        int mode = CompassEditor.mode(mc.player.getMainHandItem());
        if (mode != (payload.area() < 0 ? CompassEditor.SCHEDULE_OPTIONS : CompassEditor.RENAME_AREAS)) return;
        // This snapshot is authoritative even if normal inventory synchronization arrives a tick later.
        mc.player.getMainHandItem().set(WorkspaceComponents.PLAN.get(), payload.plan());
        mc.setScreen(payload.area() < 0 ? new WorkScheduleScreen(payload.slot(), payload.plan())
                : new RenameAreaScreen(payload.slot(), payload.plan(), payload.area()));
    }
    private CompassScreens() {}
}
