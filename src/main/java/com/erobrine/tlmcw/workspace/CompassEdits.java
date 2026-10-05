package com.erobrine.tlmcw.workspace;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Server-side editing guards also keep an open screen from overwriting a changed or replaced draft. */
public final class CompassEdits {
    public static boolean save(Player player, int slot, WorkspacePlan expected, int area, String name, WorkSchedule schedule) {
        ItemStack stack = player.getMainHandItem();
        if (player.getInventory().selected != slot || !CompassEditor.isSmartCompass(stack)
                || !expected.equals(stack.get(WorkspaceComponents.PLAN.get()))
                || !expected.dimension().equals(player.level().dimension().location())) {
            CompassEditor.message(player, "editor_stale"); return false;
        }
        if (area >= 0) {
            if (CompassEditor.mode(stack) != CompassEditor.RENAME_AREAS || area >= expected.areas().size()
                    || name.length() > WorkspacePlan.MAX_NAME_LENGTH) return false;
            stack.set(WorkspaceComponents.PLAN.get(), expected.withName(area, name));
        } else {
            if (area != -1 || CompassEditor.mode(stack) != CompassEditor.SCHEDULE_OPTIONS || !schedule.validFor(expected)) return false;
            stack.set(WorkspaceComponents.PLAN.get(), expected.withSchedule(schedule));
        }
        CompassEditor.message(player, "editor_saved");
        return true;
    }
    private CompassEdits() {}
}
