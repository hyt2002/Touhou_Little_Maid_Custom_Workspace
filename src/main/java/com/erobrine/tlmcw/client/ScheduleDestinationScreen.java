package com.erobrine.tlmcw.client;

import com.erobrine.tlmcw.workspace.AreaType;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;

import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class ScheduleDestinationScreen extends PaperScreen {
    private final WorkScheduleScreen parent;
    private final int entry;
    private UUID selected;
    private int scroll;
    private String query = "";

    ScheduleDestinationScreen(WorkScheduleScreen parent, int entry, UUID selected) {
        super(tr("destination_editor"));
        this.parent = parent;
        this.entry = entry;
        this.selected = selected;
    }

    private List<UUID> choices() {
        return parent.plan.nodes(AreaType.WORK).stream()
                .filter(
                        area ->
                                AreaLabels.name(parent.plan, area)
                                        .getString()
                                        .toLowerCase(Locale.ROOT)
                                        .contains(query.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private int listTop() {
        return top + 82;
    }

    private int listBottom() {
        return top + panelHeight - 64;
    }

    private int maxScroll() {
        return Math.max(0, choices().size() * 23 - (listBottom() - listTop()));
    }

    @Override
    protected void init() {
        super.init();
        EditBox search =
                addRenderableWidget(
                        new EditBox(
                                font,
                                left + 22,
                                top + 53,
                                panelWidth - 44,
                                20,
                                tr("search_areas")));
        search.setMaxLength(64);
        search.setValue(query);
        search.setHint(tr("search_areas"));
        search.setResponder(
                value -> {
                    query = value;
                    scroll = 0;
                });
        iconButton(left + 22, top + panelHeight - 34, "×", "cancel", this::onClose);
        iconButton(
                left + panelWidth - 44,
                top + panelHeight - 34,
                "✔",
                "save_destination",
                () -> {
                    parent.changeDestination(entry, selected);
                    onClose();
                });
        setInitialFocus(search);
    }

    @Override
    protected void renderContents(GuiGraphics g, int mx, int my, float partialTick) {
        g.drawString(font, tr("destination_work_only"), left + 22, top + 37, MUTED, false);
        g.enableScissor(left + 20, listTop(), left + panelWidth - 20, listBottom());
        var choices = choices();
        for (int i = 0; i < choices.size(); i++) {
            int y = listTop() + i * 23 - scroll;
            if (y + 22 < listTop() || y >= listBottom()) continue;
            UUID area = choices.get(i);
            boolean hover = inside(mx, my, left + 22, y, panelWidth - 44, 21);
            frame(
                    g,
                    left + 22,
                    y,
                    panelWidth - 44,
                    21,
                    selected.equals(area) ? 0xFFBEC592 : hover ? 0xFFF4E6C7 : 0xFFD8C5A0);
            String name =
                    font.plainSubstrByWidth(
                            AreaLabels.name(parent.plan, area).getString(), panelWidth - 65);
            g.drawString(font, name, left + 30, y + 7, INK, false);
        }
        g.disableScissor();
        String name =
                font.plainSubstrByWidth(
                        AreaLabels.name(parent.plan, selected).getString(), panelWidth - 44);
        g.drawString(font, name, left + 22, top + panelHeight - 51, INK, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0
                && inside(
                        mx, my, left + 22, listTop(), panelWidth - 44, listBottom() - listTop())) {
            int i = (int) (my - listTop() + scroll) / 23;
            var choices = choices();
            if (i >= 0 && i < choices.size()) {
                selected = choices.get(i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (inside(mx, my, left + 22, listTop(), panelWidth - 44, listBottom() - listTop())) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (vertical * 23)));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            parent.changeDestination(entry, selected);
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
