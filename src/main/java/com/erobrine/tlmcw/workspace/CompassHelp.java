package com.erobrine.tlmcw.workspace;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Keybind components resolve on the receiving client, including server-sent action-bar messages. */
public final class CompassHelp {
    public static MutableComponent text(String key, Object... args) {
        Object[] bindings = switch (key) {
            case "work_menu_description", "edit_hint" -> keys("key.use", "key.attack", "key.sneak");
            case "route_menu_description", "route_edit_hint" -> keys("key.use", "key.attack");
            case "apply_menu_description", "apply_hint" -> keys("key.use", "key.sneak");
            case "rename_menu_description", "schedule_menu_description", "mark_menu_description",
                    "rename_edit_hint", "schedule_edit_hint", "mark_edit_hint", "aim_maid",
                    "select_route_start", "route_start", "first_corner" -> keys("key.use");
            case "menu_hold", "menu_scroll", "select_apply_tool" -> keys("key.tlmcw.tool_menu");
            case "key_hint_work", "key_hint_route" -> keys("key.sneak", "key.tlmcw.mode");
            default -> args;
        };
        return Component.translatable("tlmcw." + key, bindings);
    }
    private static Object[] keys(String... actions) {
        return java.util.Arrays.stream(actions).map(Component::keybind).toArray();
    }
    private CompassHelp() {}
}
