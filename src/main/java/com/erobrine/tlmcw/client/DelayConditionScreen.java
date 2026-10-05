package com.erobrine.tlmcw.client;

import com.erobrine.tlmcw.workspace.DelayCondition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import org.lwjgl.glfw.GLFW;

/** The condition type selector and duration controls are separate from destination editing. */
final class DelayConditionScreen extends PaperScreen {
    private final WorkScheduleScreen parent;
    private final int entry;
    private String draft;
    private EditBox ticks;
    private PaperButton confirm;
    DelayConditionScreen(WorkScheduleScreen parent, int entry, DelayCondition condition) {
        super(tr("condition_editor")); this.parent = parent; this.entry = entry; draft = Integer.toString(condition.ticks());
    }
    @Override protected void init() {
        super.init();
        var type = button(left + 22, top + 76, panelWidth - 44, "condition_delay", () -> {});
        type.active = false;
        ticks = addRenderableWidget(new EditBox(font, left + 56, top + 116, panelWidth - 154, 20, tr("delay_duration")));
        ticks.setMaxLength(7); ticks.setFilter(value -> value.matches("[0-9]*")); ticks.setValue(draft);
        ticks.setResponder(value -> { draft = value; if (confirm != null) confirm.active = valid(); });
        iconButton(left + 28, top + 116, "−", "decrease_delay", () -> step(-1));
        iconButton(left + panelWidth - 92, top + 116, "+", "increase_delay", () -> step(1));
        iconButton(left + 22, top + panelHeight - 34, "×", "cancel", this::onClose);
        confirm = iconButton(left + panelWidth - 44, top + panelHeight - 34, "✔", "save_condition", this::save);
        confirm.active = valid(); setInitialFocus(ticks);
    }
    private int value() {
        try { return Integer.parseInt(draft); } catch (NumberFormatException ignored) { return -1; }
    }
    private boolean valid() { return value() >= 0 && value() <= DelayCondition.MAX_TICKS; }
    private void step(int direction) {
        long value = Math.max(0, value()) + (long) direction * (hasShiftDown() ? 15 : 1);
        ticks.setValue(Long.toString(Math.max(0, Math.min(DelayCondition.MAX_TICKS, value))));
    }
    private void save() { if (valid()) { parent.changeCondition(entry, new DelayCondition(value())); onClose(); } }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override protected void renderContents(GuiGraphics g, int mx, int my, float partialTick) {
        String area = font.plainSubstrByWidth(AreaLabels.name(parent.plan, parent.entries.get(entry).area()).getString(), panelWidth - 44);
        g.drawString(font, area, left + 22, top + 43, INK, false);
        g.drawString(font, tr("condition_type"), left + 22, top + 62, MUTED, false);
        g.drawString(font, tr("delay_duration"), left + 22, top + 103, MUTED, false);
        g.drawString(font, tr("ticks_unit"), left + panelWidth - 64, top + 122, INK, false);
        var lines = font.split(tr(valid() ? "condition_note" : "condition_invalid", DelayCondition.MAX_TICKS), panelWidth - 44);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), left + 22, top + 147 + 11 * i, valid() ? MUTED : 0xFFAD4030, false);
    }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (inside(mx, my, left + 22, top + 112, panelWidth - 44, 29) && vertical != 0) { step(vertical > 0 ? 1 : -1); return true; }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { save(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
}
