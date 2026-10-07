package com.erobrine.tlmcw.client;

import com.erobrine.tlmcw.workspace.WorkspacePlan;

import net.minecraft.network.chat.Component;

import java.util.UUID;

public final class AreaLabels {
    public static Component name(WorkspacePlan plan, UUID area) {
        return plan.name(area).isBlank()
                ? Component.translatable("tlmcw.unnamed_area", plan.areaIds().indexOf(area) + 1)
                : Component.literal(plan.name(area));
    }

    public static Component full(WorkspacePlan plan, UUID area) {
        return name(plan, area)
                .copy()
                .append(" · ")
                .append(Component.translatable(plan.type(area).translationKey()));
    }

    private AreaLabels() {}
}
