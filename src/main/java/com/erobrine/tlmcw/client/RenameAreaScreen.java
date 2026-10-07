package com.erobrine.tlmcw.client;

import com.erobrine.tlmcw.network.CompassEditPayload;
import com.erobrine.tlmcw.workspace.WorkSchedule;
import com.erobrine.tlmcw.workspace.WorkspacePlan;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

import java.util.UUID;

final class RenameAreaScreen extends PaperScreen {
    private final int slot;
    private final UUID area;
    private final WorkspacePlan plan;
    private EditBox name;
    private String draft;

    RenameAreaScreen(int slot, WorkspacePlan plan, UUID area) {
        super(tr("rename_title"));
        this.slot = slot;
        this.plan = plan;
        this.area = area;
        draft = plan.name(area);
    }

    @Override
    protected void init() {
        super.init();
        name =
                addRenderableWidget(
                        new EditBox(
                                font, left + 22, top + 78, panelWidth - 44, 20, tr("area_name")));
        name.setMaxLength(WorkspacePlan.MAX_NAME_LENGTH);
        name.setValue(draft);
        name.setResponder(value -> draft = value);
        button(left + 22, top + panelHeight - 34, 72, "cancel", this::onClose);
        button(left + panelWidth - 94, top + panelHeight - 34, 72, "save", this::save);
        setInitialFocus(name);
    }

    private void save() {
        PacketDistributor.sendToServer(
                new CompassEditPayload(
                        slot,
                        plan,
                        area,
                        WorkspacePlan.cleanName(name.getValue()),
                        WorkSchedule.EMPTY));
        onClose();
    }

    @Override
    protected void renderContents(GuiGraphics g, int mx, int my, float partialTick) {
        g.drawString(
                font,
                font.plainSubstrByWidth(AreaLabels.full(plan, area).getString(), panelWidth - 44),
                left + 22,
                top + 43,
                INK,
                false);
        g.drawString(font, tr("area_name"), left + 22, top + 64, MUTED, false);
        var lines = font.split(tr("rename_note"), panelWidth - 44);
        for (int i = 0; i < lines.size(); i++)
            g.drawString(font, lines.get(i), left + 22, top + 112 + 11 * i, MUTED, false);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
