package com.erobrine.tlmcw.client;

import com.erobrine.tlmcw.network.CompassActionPayload;
import com.erobrine.tlmcw.workspace.CompassEditor;
import com.erobrine.tlmcw.workspace.CompassHelp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Passive tool shelf: hold Alt, scroll, release to equip. Uses no Create classes or assets. */
public final class CompassToolMenu {
    private static boolean focused;
    private static int selection, slot = -1;
    private static float lift;

    public static boolean focused() {
        return focused;
    }

    public static void tick(boolean usable, ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (!usable) {
            focused = false;
            lift = 0;
            slot = -1;
            return;
        }
        int currentSlot = mc.player.getInventory().selected;
        if (slot != currentSlot) {
            focused = false;
            lift = 0;
            slot = currentSlot;
        }
        boolean down = WorkspaceClient.TOOL_MENU.isDown();
        if (down && !focused) {
            selection = CompassEditor.mode(stack);
            focused = true;
        }
        if (!down && focused) {
            focused = false;
            if (selection != CompassEditor.mode(stack))
                PacketDistributor.sendToServer(
                        new CompassActionPayload(
                                CompassActionPayload.SET_MODE,
                                selection,
                                BlockPos.ZERO,
                                BlockPos.ZERO));
        }
        if (!focused) selection = CompassEditor.mode(stack);
        lift += ((focused ? 10 : 0) - lift) * 0.25f;
    }

    public static void scroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null
                || mc.screen != null
                || !CompassEditor.isSmartCompass(mc.player.getMainHandItem())
                || !WorkspaceClient.TOOL_MENU.isDown()) return;
        if (!focused) {
            selection = CompassEditor.mode(mc.player.getMainHandItem());
            slot = mc.player.getInventory().selected;
            focused = true;
        }
        if (event.getScrollDeltaY() != 0)
            selection =
                    CompassEditor.TOOL_ORDER.get(
                            Math.floorMod(
                                    CompassEditor.TOOL_ORDER.indexOf(selection)
                                            + (event.getScrollDeltaY() < 0 ? 1 : -1),
                                    CompassEditor.TOOL_COUNT));
        event.setCanceled(true);
    }

    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null
                || mc.screen != null
                || mc.options.hideGui
                || !CompassEditor.isSmartCompass(mc.player.getMainHandItem())) return;
        GuiGraphics graphics = event.getGuiGraphics();
        int width =
                Math.min(
                        Math.max(220, CompassEditor.TOOL_COUNT * 50 + 30),
                        graphics.guiWidth() - 16);
        int x = (graphics.guiWidth() - width) / 2;
        // GUI coordinates keep the same spacing at every GUI scale. Leave room
        // below the shelf for its expanded description, above the hotbar.
        int y = graphics.guiHeight() - 105 - Math.round(lift);
        int selected = focused ? selection : CompassEditor.mode(mc.player.getMainHandItem());
        panel(graphics, x, y, width, 30, focused ? 0xE830343B : 0x8030343B);
        graphics.drawCenteredString(
                mc.font,
                CompassHelp.text(focused ? "menu_scroll" : "menu_hold"),
                graphics.guiWidth() / 2,
                y - 10,
                0xCCDDF0);
        for (int position = 0; position < CompassEditor.TOOL_ORDER.size(); position++) {
            int mode = CompassEditor.TOOL_ORDER.get(position);
            int centerX =
                    x + 20 + (int) ((width - 40) * (position + 0.5) / CompassEditor.TOOL_COUNT);
            // The selected slot has two 15-pixel rows: a padded 12-pixel
            // icon above, and the normal-size name below.
            int iconX = centerX - 6;
            int iconY = y + 12 - (mode == selected ? 10 : 0);
            int color = mode == selected ? 0xFFE7F3FF : focused ? 0xFF8797A5 : 0x668797A5;
            if (mode == selected) {
                graphics.fill(iconX - 1, iconY - 1, iconX + 13, iconY + 13, 0x804E687E);
                graphics.drawCenteredString(mc.font, modeName(mode), centerX, y + 18, 0xCCDDF0);
            }
            icon(graphics, iconX, iconY, mode, color);
        }
        if (lift > 2.5f) {
            int alpha = Math.min(255, (int) (lift / 10 * 230));
            panel(graphics, x, y + 33, width, 52, (alpha << 24) | 0x30343B);
            String key =
                    switch (selected) {
                        case CompassEditor.ROUTE_NODES -> "route_menu_description";
                        case CompassEditor.MARK_AREAS -> "mark_menu_description";
                        case CompassEditor.APPLY_TO_MAID -> "apply_menu_description";
                        case CompassEditor.RENAME_AREAS -> "rename_menu_description";
                        case CompassEditor.SCHEDULE_OPTIONS -> "schedule_menu_description";
                        case CompassEditor.READ_FROM_MAID -> "read_menu_description";
                        default -> "work_menu_description";
                    };
            var lines = mc.font.split(CompassHelp.text(key), width - 16);
            for (int i = 0; i < Math.min(lines.size(), 4); i++) {
                graphics.drawString(
                        mc.font,
                        lines.get(i),
                        x + 8,
                        y + 38 + i * 10,
                        (alpha << 24) | 0xCCDDF0,
                        false);
            }
        }
    }

    public static Component modeName(int mode) {
        return Component.translatable(
                switch (mode) {
                    case CompassEditor.ROUTE_NODES -> "tlmcw.mode_route";
                    case CompassEditor.MARK_AREAS -> "tlmcw.mode_mark";
                    case CompassEditor.APPLY_TO_MAID -> "tlmcw.mode_apply";
                    case CompassEditor.RENAME_AREAS -> "tlmcw.mode_rename";
                    case CompassEditor.SCHEDULE_OPTIONS -> "tlmcw.mode_schedule";
                    case CompassEditor.READ_FROM_MAID -> "tlmcw.mode_read";
                    default -> "tlmcw.mode_work";
                });
    }

    private static void panel(GuiGraphics g, int x, int y, int width, int height, int color) {
        g.fill(x, y, x + width, y + height, color);
        g.fill(x, y, x + width, y + 1, 0x88798595);
        g.fill(x, y + height - 1, x + width, y + height, 0xAA101419);
        g.fill(x, y, x + 1, y + height, 0x88798595);
        g.fill(x + width - 1, y, x + width, y + height, 0xAA101419);
    }

    private static void icon(GuiGraphics g, int x, int y, int mode, int color) {
        if (mode == CompassEditor.WORK_AREAS) {
            outline(g, x, y + 4, 8, 8, color);
            outline(g, x + 4, y, 8, 8, color);
            for (int i = 0; i <= 4; i++) {
                g.fill(x + i, y + 4 - i, x + i + 1, y + 5 - i, color);
                g.fill(x + 7 + i, y + 11 - i, x + 8 + i, y + 12 - i, color);
            }
        } else if (mode == CompassEditor.ROUTE_NODES) {
            g.fill(x + 2, y + 2, x + 10, y + 3, color);
            g.fill(x + 9, y + 2, x + 10, y + 10, color);
            g.fill(x + 2, y + 9, x + 10, y + 10, color);
            for (int[] p : new int[][] {{0, 0}, {8, 0}, {8, 8}, {0, 8}})
                outline(g, x + p[0], y + p[1], 4, 4, color);
        } else if (mode == CompassEditor.MARK_AREAS) {
            outline(g, x, y + 4, 8, 8, color);
            for (int i = 0; i < 7; i++) g.fill(x + 4 + i, y + 7 - i, x + 6 + i, y + 9 - i, color);
        } else if (mode == CompassEditor.RENAME_AREAS) {
            outline(g, x, y + 1, 12, 9, color);
            g.fill(x + 2, y + 3, x + 8, y + 4, color);
            g.fill(x + 2, y + 6, x + 6, y + 7, color);
            g.fill(x + 9, y, x + 10, y + 12, color);
        } else if (mode == CompassEditor.SCHEDULE_OPTIONS) {
            outline(g, x, y, 12, 12, color);
            for (int i = 0; i < 3; i++) g.fill(x + 3, y + 3 + i * 3, x + 9, y + 4 + i * 3, color);
        } else if (mode == CompassEditor.READ_FROM_MAID) {
            outline(g, x + 8, y, 4, 4, color);
            g.fill(x + 9, y + 5, x + 11, y + 12, color);
            g.fill(x, y + 6, x + 8, y + 7, color);
            for (int i = 0; i < 4; i++) {
                g.fill(x + i, y + 6 - i, x + i + 1, y + 7 - i, color);
                g.fill(x + i, y + 6 + i, x + i + 1, y + 7 + i, color);
            }
        } else {
            outline(g, x, y, 4, 4, color);
            g.fill(x + 1, y + 5, x + 3, y + 12, color);
            g.fill(x + 4, y + 6, x + 12, y + 7, color);
            for (int i = 0; i < 4; i++) {
                g.fill(x + 8 + i, y + 3 + i, x + 9 + i, y + 4 + i, color);
                g.fill(x + 8 + i, y + 9 - i, x + 9 + i, y + 10 - i, color);
            }
        }
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private CompassToolMenu() {}
}
