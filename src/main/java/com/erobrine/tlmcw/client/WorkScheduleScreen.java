package com.erobrine.tlmcw.client;

import com.erobrine.tlmcw.Config;
import com.erobrine.tlmcw.network.CompassEditPayload;
import com.erobrine.tlmcw.workspace.*;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Destination cards with an independently edited departure condition, following the train timetable
 * interaction.
 */
final class WorkScheduleScreen extends PaperScreen {
    private static final int CARD_HEIGHT = 64, CARD_STEP = 72;
    final WorkspacePlan plan;
    final List<WorkScheduleEntry> entries = new ArrayList<>();
    private final int slot;
    private boolean cyclic, custom;
    private int scroll;
    private PaperButton add, loop;

    WorkScheduleScreen(int slot, WorkspacePlan plan) {
        super(tr("schedule_title"));
        this.slot = slot;
        this.plan = plan;
        custom = plan.schedule().custom();
        cyclic = plan.schedule().cyclic();
        if (custom) entries.addAll(plan.schedule().entries());
        else seedDefaults();
    }

    private void seedDefaults() {
        entries.clear();
        for (UUID area : plan.nodes(AreaType.WORK))
            entries.add(new WorkScheduleEntry(area, new DelayCondition(Config.WORK_TICKS.get())));
    }

    private int viewportTop() {
        return top + 49;
    }

    private int viewportBottom() {
        return top + panelHeight - 56;
    }

    private int maxScroll() {
        return Math.max(0, entries.size() * CARD_STEP - (viewportBottom() - viewportTop()));
    }

    @Override
    protected void init() {
        super.init();
        scroll = Math.min(scroll, maxScroll());
        loop =
                button(
                        left + 13,
                        top + panelHeight - 30,
                        73,
                        cyclic ? "loop_on" : "loop_off",
                        () -> {
                            cyclic = !cyclic;
                            custom = true;
                            loop.setMessage(tr(cyclic ? "loop_on" : "loop_off"));
                        });
        button(
                left + 92,
                top + panelHeight - 30,
                80,
                "schedule_default",
                () -> {
                    custom = false;
                    cyclic = true;
                    scroll = 0;
                    seedDefaults();
                    rebuildWidgets();
                });
        add =
                button(
                        left + 13,
                        top + panelHeight - 53,
                        panelWidth - 26,
                        "schedule_add",
                        () -> {
                            var work = plan.nodes(AreaType.WORK);
                            UUID node =
                                    work.stream()
                                            .filter(
                                                    area ->
                                                            entries.stream()
                                                                    .noneMatch(
                                                                            entry ->
                                                                                    entry.destination()
                                                                                            .equals(
                                                                                                    area)))
                                            .findFirst()
                                            .orElse(work.getFirst());
                            entries.add(
                                    new WorkScheduleEntry(
                                            node, new DelayCondition(Config.WORK_TICKS.get())));
                            custom = true;
                            scroll = maxScroll();
                            updateButtons();
                        });
        iconButton(left + panelWidth - 62, top + panelHeight - 30, "×", "cancel", this::onClose);
        iconButton(
                left + panelWidth - 36,
                top + panelHeight - 30,
                "✔",
                "save",
                () -> {
                    WorkSchedule schedule =
                            custom && !entries.isEmpty()
                                    ? new WorkSchedule(entries, cyclic)
                                    : WorkSchedule.EMPTY;
                    PacketDistributor.sendToServer(
                            new CompassEditPayload(slot, plan, null, "", schedule));
                    onClose();
                });
        updateButtons();
    }

    private void updateButtons() {
        add.active =
                !plan.nodes(AreaType.WORK).isEmpty() && entries.size() < WorkSchedule.MAX_ENTRIES;
        loop.active = !entries.isEmpty();
    }

    void changeDestination(int index, UUID area) {
        entries.set(index, entries.get(index).withDestination(area));
        custom = true;
    }

    void changeCondition(int index, DelayCondition condition) {
        entries.set(index, entries.get(index).withCondition(condition));
        custom = true;
    }

    @Override
    protected void renderContents(GuiGraphics g, int mx, int my, float partialTick) {
        g.drawString(
                font,
                tr(custom ? "schedule_custom_status" : "schedule_default_status"),
                left + 13,
                top + 35,
                MUTED,
                false);
        int x = left + 15, cardWidth = panelWidth - 45;
        Component hover = null;
        g.enableScissor(left + 10, viewportTop(), left + panelWidth - 9, viewportBottom());
        if (entries.isEmpty()) {
            var lines = font.split(tr("schedule_empty"), panelWidth - 40);
            for (int i = 0; i < lines.size(); i++)
                g.drawString(
                        font, lines.get(i), left + 20, viewportTop() + 15 + 11 * i, MUTED, false);
        }
        for (int i = 0; i < entries.size(); i++) {
            int y = viewportTop() + i * CARD_STEP - scroll;
            if (y + CARD_HEIGHT < viewportTop() || y > viewportBottom()) continue;
            WorkScheduleEntry entry = entries.get(i);
            frame(g, x, y, cardWidth, CARD_HEIGHT, 0xFFD0BA8E);
            g.fill(x + 3, y + 3, x + cardWidth - 3, y + 25, 0xFFF1E1BD);
            g.fill(x + 8, y + 5, x + 10, y + CARD_HEIGHT + 9, 0xFFAB8A50);
            g.drawString(font, Integer.toString(i + 1), x + 14, y + 9, MUTED, false);
            String target =
                    font.plainSubstrByWidth(
                            AreaLabels.name(plan, entry.destination()).getString(), cardWidth - 74);
            g.drawString(font, target, x + 34, y + 9, INK, false);
            g.drawString(font, "×", x + cardWidth - 14, y + 7, INK, false);
            g.drawString(font, "+", x + cardWidth - 14, y + 48, INK, false);
            boolean conditionHover =
                    inside(mx, my, x + 30, y + 31, cardWidth - 55, 22)
                            && my >= viewportTop()
                            && my < viewportBottom();
            frame(g, x + 30, y + 31, cardWidth - 55, 22, conditionHover ? 0xFFF4EACF : 0xFFE0CFAC);
            g.drawString(
                    font,
                    tr("delay_summary", entry.condition().ticks()),
                    x + 38,
                    y + 38,
                    INK,
                    false);
            g.drawString(font, "↓", x + 13, y + 38, 0xFF866633, false);
            if (i > 0) g.drawString(font, "▲", x + cardWidth + 5, y + 16, INK, false);
            if (i + 1 < entries.size())
                g.drawString(font, "▼", x + cardWidth + 5, y + 34, INK, false);
            if (inside(mx, my, x, y, cardWidth + 20, CARD_HEIGHT)
                    && my >= viewportTop()
                    && my < viewportBottom()) {
                if (mx >= x + cardWidth) hover = tr("schedule_reorder");
                else if (mx >= x + cardWidth - 20)
                    hover = tr(my < y + 25 ? "schedule_remove" : "schedule_duplicate");
                else
                    hover =
                            conditionHover
                                    ? tr("condition_edit_hint")
                                    : AreaLabels.full(plan, entry.destination());
            }
        }
        g.disableScissor();
        if (maxScroll() > 0) {
            int track = viewportBottom() - viewportTop();
            int thumb = Math.max(12, track * track / (entries.size() * CARD_STEP));
            int y = viewportTop() + (track - thumb) * scroll / maxScroll();
            g.fill(
                    left + panelWidth - 10,
                    viewportTop(),
                    left + panelWidth - 8,
                    viewportBottom(),
                    0xFFB8A378);
            g.fill(left + panelWidth - 11, y, left + panelWidth - 7, y + thumb, 0xFF765F3A);
        }
        if (hover != null) g.renderTooltip(font, hover, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (my >= viewportTop() && my < viewportBottom()) {
            int index = (int) (my - viewportTop() + scroll) / CARD_STEP;
            if (index >= 0 && index < entries.size()) {
                int x = left + 15,
                        w = panelWidth - 45,
                        y = viewportTop() + index * CARD_STEP - scroll;
                if (inside(mx, my, x, y, w + 20, CARD_HEIGHT) && button == 0) {
                    if (mx >= x + w) {
                        int other = my < y + 30 ? index - 1 : index + 1;
                        if (other >= 0 && other < entries.size()) {
                            Collections.swap(entries, index, other);
                            custom = true;
                        }
                    } else if (mx >= x + w - 20 && my < y + 25) {
                        entries.remove(index);
                        custom = true;
                        scroll = Math.min(scroll, maxScroll());
                    } else if (mx >= x + w - 20) {
                        if (entries.size() < WorkSchedule.MAX_ENTRIES) {
                            entries.add(index + 1, entries.get(index).duplicate());
                            custom = true;
                        }
                    } else if (my >= y + 27)
                        minecraft.setScreen(
                                new DelayConditionScreen(
                                        this, index, entries.get(index).condition()));
                    else
                        minecraft.setScreen(
                                new ScheduleDestinationScreen(
                                        this, index, entries.get(index).destination()));
                    updateButtons();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (inside(
                mx,
                my,
                left + 10,
                viewportTop(),
                panelWidth - 20,
                viewportBottom() - viewportTop())) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (vertical * 24)));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }
}
