package com.erobrine.tlmcw.workspace;

import com.erobrine.tlmcw.compat.LittleMaidCompat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import net.minecraft.world.item.ItemStack;

/**
 * Shared read/write checkpoint. Component/task-data codecs migrate before any value reaches this
 * layer.
 */
public final class WorkspaceAccess {
    public static WorkspacePlan plan(ItemStack stack) {
        WorkspacePlan p = stack.get(WorkspaceComponents.PLAN.get());
        return p != null && p.readable() ? p : null;
    }

    public static boolean blocked(ItemStack stack) {
        var p = stack.get(WorkspaceComponents.PLAN.get());
        return p != null && !p.readable();
    }

    public static boolean writePlan(ItemStack stack, WorkspacePlan plan) {
        if (blocked(stack) || !plan.readable()) return false;
        stack.set(WorkspaceComponents.PLAN.get(), plan);
        return true;
    }

    public static WorkspaceState state(EntityMaid maid) {
        if (LittleMaidCompat.WORKSPACES == null) return null;
        var state = maid.getData(LittleMaidCompat.WORKSPACES);
        return state != null && state.readable() ? state : null;
    }

    public static boolean blocked(EntityMaid maid) {
        if (LittleMaidCompat.WORKSPACES == null) return false;
        var state = maid.getData(LittleMaidCompat.WORKSPACES);
        return state != null && !state.readable();
    }

    public static boolean writeState(EntityMaid maid, WorkspaceState state, boolean sync) {
        if (blocked(maid) || !state.readable()) return false;
        if (sync) maid.setAndSyncData(LittleMaidCompat.WORKSPACES, state);
        else maid.setData(LittleMaidCompat.WORKSPACES, state);
        return true;
    }

    private WorkspaceAccess() {}
}
