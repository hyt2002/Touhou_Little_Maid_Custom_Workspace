package com.erobrine.tlmcw.workspace;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public final class CompassEdits {
    public static boolean save(
            Player player,
            int slot,
            WorkspacePlan expected,
            UUID area,
            String name,
            WorkSchedule schedule) {
        return save(player, slot, expected, area, name, schedule, false);
    }

    public static boolean save(
            Player player,
            int slot,
            WorkspacePlan expected,
            UUID area,
            String name,
            WorkSchedule schedule,
            boolean groupName) {
        ItemStack stack = player.getMainHandItem();
        if (player.getInventory().selected != slot
                || !CompassEditor.isSmartCompass(stack)
                || !expected.readable()
                || !expected.equals(WorkspaceAccess.plan(stack))) {
            CompassEditor.message(player, "editor_stale");
            return false;
        }
        WorkspacePlan updated;
        if (groupName) {
            if (area != null || CompassEditor.mode(stack) != CompassEditor.MERGE_WORK_AREAS)
                return false;
            String group = WorkspacePlan.cleanName(name);
            if (group.isEmpty()) stack.remove(WorkspaceComponents.WORK_GROUP_NAME.get());
            else stack.set(WorkspaceComponents.WORK_GROUP_NAME.get(), group);
            CompassEditor.message(
                    player, group.isEmpty() ? "group_name_cleared" : "group_name_set", group);
            return true;
        } else if (area != null) {
            if (CompassEditor.mode(stack) != CompassEditor.RENAME_AREAS
                    || expected.node(area) == null
                    || !expected.node(area).isArea()
                    || !expected.inDimension(area, player.level().dimension().location())
                    || name.length() > WorkspacePlan.MAX_NAME_LENGTH) return false;
            updated = expected.withName(area, name);
        } else {
            if (CompassEditor.mode(stack) != CompassEditor.SCHEDULE_OPTIONS
                    || !schedule.validFor(expected)) return false;
            updated = expected.withSchedule(schedule);
        }
        if (!WorkspaceAccess.writePlan(stack, updated)) return false;
        CompassEditor.message(player, "editor_saved");
        return true;
    }

    private CompassEdits() {}
}
