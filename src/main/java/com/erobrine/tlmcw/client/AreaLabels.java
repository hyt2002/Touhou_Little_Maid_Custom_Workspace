package com.erobrine.tlmcw.client;

import com.erobrine.tlmcw.workspace.WorkspacePlan;
import net.minecraft.network.chat.Component;

public final class AreaLabels {
    public static Component name(WorkspacePlan plan, int area) {
        return plan.name(area).isBlank() ? Component.translatable("tlmcw.unnamed_area", area + 1) : Component.literal(plan.name(area));
    }
    public static Component full(WorkspacePlan plan, int area) {
        return name(plan, area).copy().append(" · ").append(Component.translatable(plan.type(area).translationKey()));
    }
    private AreaLabels() {}
}
