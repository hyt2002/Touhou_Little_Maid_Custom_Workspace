package com.erobrine.tlmcw.client;

import com.erobrine.tlmcw.Config;
import com.erobrine.tlmcw.compat.CreateRedstoneCompat;
import com.erobrine.tlmcw.workspace.*;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;

import org.lwjgl.glfw.GLFW;

/** Edits one departure condition. Changes remain local until both editors are confirmed. */
final class DepartureConditionScreen extends PaperScreen {
    private final WorkScheduleScreen parent;
    private final int entry;
    private boolean wireless;
    private WirelessRedstoneCondition signal;
    private String draft;
    private EditBox ticks;
    private PaperButton confirm;

    DepartureConditionScreen(WorkScheduleScreen parent, int entry, DepartureCondition condition) {
        super(tr("condition_editor"));
        this.parent = parent;
        this.entry = entry;
        wireless = condition instanceof WirelessRedstoneCondition;
        signal =
                wireless
                        ? (WirelessRedstoneCondition) condition
                        : WirelessRedstoneCondition.empty();
        draft =
                Integer.toString(
                        condition instanceof DelayCondition delay
                                ? delay.ticks()
                                : Config.WORK_TICKS.get());
    }

    @Override
    protected void init() {
        super.init();
        ticks = null;
        var type =
                button(
                        left + 22,
                        top + 70,
                        panelWidth - 44,
                        wireless ? "condition_wireless" : "condition_delay",
                        () -> {
                            wireless = !wireless;
                            rebuildWidgets();
                        });
        type.active = wireless || CreateRedstoneCompat.available();
        type.setTooltip(
                Tooltip.create(tr(type.active ? "condition_switch_type" : "create_required")));
        if (wireless) {
            button(
                    left + 22,
                    top + 99,
                    panelWidth - 44,
                    signal.powered() ? "wireless_powered" : "wireless_unpowered",
                    () -> {
                        signal =
                                new WirelessRedstoneCondition(
                                        signal.first(), signal.second(), !signal.powered());
                        rebuildWidgets();
                    });
            button(
                    left + 22,
                    top + 151,
                    panelWidth - 44,
                    "frequency_choose",
                    () -> openFrequencies(0));
        } else {
            ticks =
                    addRenderableWidget(
                            new EditBox(
                                    font,
                                    left + 56,
                                    top + 116,
                                    panelWidth - 154,
                                    20,
                                    tr("delay_duration")));
            ticks.setMaxLength(7);
            ticks.setFilter(value -> value.matches("[0-9]*"));
            ticks.setValue(draft);
            ticks.setResponder(
                    value -> {
                        draft = value;
                        if (confirm != null) confirm.active = valid();
                    });
            iconButton(left + 28, top + 116, "−", "decrease_delay", () -> step(-1));
            iconButton(left + panelWidth - 92, top + 116, "+", "increase_delay", () -> step(1));
        }
        iconButton(left + 22, top + panelHeight - 30, "×", "cancel", this::onClose);
        confirm =
                iconButton(
                        left + panelWidth - 44,
                        top + panelHeight - 30,
                        "✔",
                        "save_condition",
                        this::save);
        confirm.active = valid();
        if (ticks != null) setInitialFocus(ticks);
    }

    private void openFrequencies(int selected) {
        minecraft.setScreen(new FrequencyPickerScreen(this, signal, selected));
    }

    void frequencies(FrequencyMark first, FrequencyMark second) {
        signal = new WirelessRedstoneCondition(first, second, signal.powered());
    }

    private int value() {
        try {
            return Integer.parseInt(draft);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private boolean valid() {
        return wireless || value() >= 0 && value() <= DelayCondition.MAX_TICKS;
    }

    private void step(int direction) {
        long value = Math.max(0, value()) + (long) direction * (hasShiftDown() ? 15 : 1);
        ticks.setValue(Long.toString(Math.max(0, Math.min(DelayCondition.MAX_TICKS, value))));
    }

    private void save() {
        if (!valid()) return;
        parent.changeCondition(entry, wireless ? signal : new DelayCondition(value()));
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    protected void renderContents(GuiGraphics g, int mx, int my, float partialTick) {
        g.drawString(
                font,
                font.plainSubstrByWidth(
                        AreaLabels.name(parent.plan, parent.entries.get(entry).destination())
                                .getString(),
                        panelWidth - 44),
                left + 22,
                top + 43,
                INK,
                false);
        g.drawString(font, tr("condition_type"), left + 22, top + 58, MUTED, false);
        if (wireless) {
            for (int i = 0; i < 2; i++) {
                int x = left + 22 + i * (panelWidth - 44) / 2;
                var mark = i == 0 ? signal.first() : signal.second();
                frame(
                        g,
                        x,
                        top + 127,
                        20,
                        20,
                        inside(mx, my, x, top + 127, 20, 20) ? 0xFFF4EACF : 0xFFCEBC97);
                g.renderItem(mark.stack(), x + 2, top + 129);
                g.drawString(font, tr("frequency_number", i + 1), x + 26, top + 133, INK, false);
            }
            boolean available = CreateRedstoneCompat.available();
            var lines =
                    font.split(
                            tr(available ? "wireless_note" : "create_required"), panelWidth - 44);
            for (int i = 0; i < Math.min(2, lines.size()); i++)
                g.drawString(
                        font,
                        lines.get(i),
                        left + 22,
                        top + 176 + i * 10,
                        available ? MUTED : 0xFFAD4030,
                        false);
        } else {
            g.drawString(font, tr("delay_duration"), left + 22, top + 103, MUTED, false);
            g.drawString(font, tr("ticks_unit"), left + panelWidth - 64, top + 122, INK, false);
            var lines =
                    font.split(
                            tr(
                                    valid() ? "condition_note" : "condition_invalid",
                                    DelayCondition.MAX_TICKS),
                            panelWidth - 44);
            for (int i = 0; i < lines.size(); i++)
                g.drawString(
                        font,
                        lines.get(i),
                        left + 22,
                        top + 147 + i * 11,
                        valid() ? MUTED : 0xFFAD4030,
                        false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (wireless && button == 0)
            for (int i = 0; i < 2; i++)
                if (inside(mx, my, left + 22 + i * (panelWidth - 44) / 2, top + 127, 20, 20)) {
                    openFrequencies(i);
                    return true;
                }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!wireless
                && inside(mx, my, left + 22, top + 112, panelWidth - 44, 29)
                && vertical != 0) {
            step(vertical > 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
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
