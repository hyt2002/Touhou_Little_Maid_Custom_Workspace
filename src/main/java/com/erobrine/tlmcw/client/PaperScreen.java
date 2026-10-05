package com.erobrine.tlmcw.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Independently drawn paper cards and brass controls; no external textures or widgets. */
abstract class PaperScreen extends Screen {
    static final int INK = 0xFF40352A, MUTED = 0xFF786550, PAPER = 0xFFE5D6B4;
    int left, top, panelWidth, panelHeight;
    PaperScreen(Component title) { super(title); }
    static Component tr(String key, Object... values) { return Component.translatable("tlmcw." + key, values); }
    @Override protected void init() {
        panelWidth = Math.min(300, width - 16);
        panelHeight = Math.min(230, height - 16);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        frame(g, left, top, panelWidth, panelHeight, PAPER);
        g.fill(left + 6, top + 6, left + panelWidth - 6, top + 29, 0xFFC5AE7B);
        g.drawCenteredString(font, title, left + panelWidth / 2, top + 13, INK);
        renderContents(g, mouseX, mouseY, partialTick);
        // Screen.render in 1.21.1 also draws and blurs its background. We already
        // did that before the paper, so draw widgets directly to keep all text sharp.
        for (var renderable : renderables) renderable.render(g, mouseX, mouseY, partialTick);
    }
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}
    static void frame(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0xA0000000);
        g.fill(x, y, x + w, y + h, 0xFF524638);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFF5E5BF);
        g.fill(x + 3, y + 3, x + w - 2, y + h - 2, color);
        g.fill(x + 3, y + h - 3, x + w - 2, y + h - 2, 0xFFAD9469);
    }
    static boolean inside(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    PaperButton button(int x, int y, int w, String key, Runnable action) {
        return addRenderableWidget(new PaperButton(x, y, w, 20, tr(key), ignored -> action.run()));
    }
    PaperButton iconButton(int x, int y, String glyph, String tip, Runnable action) {
        PaperButton button = addRenderableWidget(new PaperButton(x, y, 22, 20, Component.literal(glyph), ignored -> action.run()));
        button.setTooltip(Tooltip.create(tr(tip))); return button;
    }
    static final class PaperButton extends Button {
        PaperButton(int x, int y, int w, int h, Component title, OnPress onPress) { super(x, y, w, h, title, onPress, DEFAULT_NARRATION); }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int color = !active ? 0xFFB7AD94 : isHoveredOrFocused() ? 0xFFF3E2AE : 0xFFCBB788;
            frame(g, getX(), getY(), getWidth(), getHeight(), color);
            var font = net.minecraft.client.Minecraft.getInstance().font;
            String text = font.plainSubstrByWidth(getMessage().getString(), getWidth() - 6);
            g.drawCenteredString(font, text, getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, active ? INK : MUTED);
        }
    }
}
