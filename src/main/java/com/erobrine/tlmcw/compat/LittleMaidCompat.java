package com.erobrine.tlmcw.compat;

import com.erobrine.tlmcw.TouhouLittleMaidCustomWorkspace;
import com.erobrine.tlmcw.workspace.WorkspaceState;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import net.minecraft.resources.ResourceLocation;

@LittleMaidExtension
public final class LittleMaidCompat implements ILittleMaid {
    public static TaskDataKey<WorkspaceState> WORKSPACES;
    @Override
    public void registerTaskData(TaskDataRegister register) {
        WORKSPACES = register.register(ResourceLocation.fromNamespaceAndPath(
                TouhouLittleMaidCustomWorkspace.MODID, "workspaces"), WorkspaceState.CODEC);
    }
}
