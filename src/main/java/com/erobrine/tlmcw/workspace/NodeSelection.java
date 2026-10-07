package com.erobrine.tlmcw.workspace;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class NodeSelection {
    public static UUID pick(
            WorkspacePlan plan, Vec3 eye, Vec3 end, boolean points, ResourceLocation dim) {
        if (plan == null || !plan.readable()) return null;
        UUID selected = null;
        double distance = Double.POSITIVE_INFINITY;
        for (var node : plan.nodeList()) {
            if (!node.dimension().equals(dim) || !points && !node.isArea()) continue;
            var bounds = node.bounds().bounds();
            var hit = bounds.contains(eye) ? java.util.Optional.of(eye) : bounds.clip(eye, end);
            if (hit.isPresent() && hit.get().distanceToSqr(eye) < distance) {
                selected = node.id();
                distance = hit.get().distanceToSqr(eye);
            }
        }
        return selected;
    }

    private NodeSelection() {}
}
