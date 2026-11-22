package com.erobrine.tlmcw.client;

import com.erobrine.tlmcw.workspace.FrequencyMark;
import com.erobrine.tlmcw.workspace.WirelessRedstoneCondition;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import org.lwjgl.glfw.GLFW;

/** Phantom frequency marks: inventory clicks only copy item identity/color, never move items. */
final class FrequencyPickerScreen extends PaperScreen {
    private final DepartureConditionScreen parent;
    private FrequencyMark first, second;
    private int selected;
    private ItemStack hovered = ItemStack.EMPTY;

    FrequencyPickerScreen(
            DepartureConditionScreen parent, WirelessRedstoneCondition condition, int selected) {
        super(tr("frequency_picker_title"));
        this.parent = parent;
        first = condition.first();
        second = condition.second();
        this.selected = selected;
    }

    private int markX(int index) {
        return left + panelWidth / 2 - 43 + index * 66;
    }

    private int inventoryX() {
        return left + (panelWidth - 162) / 2;
    }

    private int slotY(int row) {
        return top + 104 + row * 18 + (row == 3 ? 6 : 0);
    }

    @Override
    protected void init() {
        super.init();
        iconButton(left + 22, top + panelHeight - 30, "×", "cancel", this::onClose);
        button(
                left + panelWidth / 2 - 43,
                top + panelHeight - 30,
                86,
                "frequency_clear",
                () -> set(FrequencyMark.EMPTY));
        iconButton(
                left + panelWidth - 44,
                top + panelHeight - 30,
                "✔",
                "save_frequencies",
                this::save);
    }

    private void set(FrequencyMark mark) {
        if (selected == 0) first = mark;
        else second = mark;
    }

    private void save() {
        parent.frequencies(first, second);
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void slot(
            GuiGraphics g, int x, int y, ItemStack stack, boolean active, int mx, int my) {
        boolean hover = inside(mx, my, x, y, 18, 18);
        frame(g, x, y, 18, 18, active ? 0xFFE8F1C6 : 0xFFCEBC97);
        g.renderItem(stack, x + 1, y + 1);
        g.renderItemDecorations(font, stack, x + 1, y + 1);
        if (hover) {
            g.fill(x + 1, y + 1, x + 17, y + 17, 0x40FFFFFF);
            hovered = stack;
        }
    }

    @Override
    protected void renderContents(GuiGraphics g, int mx, int my, float partialTick) {
        hovered = ItemStack.EMPTY;
        for (int i = 0; i < 2; i++) {
            g.drawString(font, Integer.toString(i + 1), markX(i) - 12, top + 51, INK, false);
            slot(g, markX(i), top + 46, (i == 0 ? first : second).stack(), selected == i, mx, my);
        }
        var note = font.split(tr("frequency_picker_note"), panelWidth - 30);
        for (int i = 0; i < Math.min(2, note.size()); i++)
            g.drawString(font, note.get(i), left + 15, top + 73 + 10 * i, MUTED, false);
        g.drawString(font, tr("player_inventory"), inventoryX(), top + 94, INK, false);
        if (minecraft.player == null) return;
        for (int row = 0; row < 4; row++)
            for (int col = 0; col < 9; col++) {
                int index = row == 3 ? col : 9 + row * 9 + col;
                slot(
                        g,
                        inventoryX() + col * 18,
                        slotY(row),
                        minecraft.player.getInventory().getItem(index),
                        false,
                        mx,
                        my);
            }
        slot(g, inventoryX() + 170, slotY(3), minecraft.player.getOffhandItem(), false, mx, my);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        if (!hovered.isEmpty()) g.renderTooltip(font, hovered, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i < 2; i++)
                if (inside(mx, my, markX(i), top + 46, 18, 18)) {
                    selected = i;
                    return true;
                }
            if (minecraft.player != null) {
                for (int row = 0; row < 4; row++)
                    for (int col = 0; col < 9; col++)
                        if (inside(mx, my, inventoryX() + col * 18, slotY(row), 18, 18)) {
                            int index = row == 3 ? col : 9 + row * 9 + col;
                            set(FrequencyMark.from(minecraft.player.getInventory().getItem(index)));
                            return true;
                        }
                if (inside(mx, my, inventoryX() + 170, slotY(3), 18, 18)) {
                    set(FrequencyMark.from(minecraft.player.getOffhandItem()));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
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
