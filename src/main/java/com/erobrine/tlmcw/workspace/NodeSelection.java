package com.erobrine.tlmcw.workspace;

import net.minecraft.world.phys.Vec3;

public final class NodeSelection {
    /** Pick the nearest node outline along the interaction ray, including a ray starting inside a node. */
    public static Integer pick(WorkspacePlan plan, Vec3 eye, Vec3 end, boolean includePoints) {
        if (plan == null) return null;
        Integer selected = null;
        double distance = Double.POSITIVE_INFINITY;
        int count = plan.areas().size() + (includePoints ? plan.graph().points().size() : 0);
        for (int i = 0; i < count; i++) {
            int node = i < plan.areas().size() ? i : -(i - plan.areas().size()) - 1;
            var bounds = plan.nodeArea(node).bounds();
            var hit = bounds.contains(eye) ? java.util.Optional.of(eye) : bounds.clip(eye, end);
            if (hit.isPresent() && hit.get().distanceToSqr(eye) < distance) {
                selected = node;
                distance = hit.get().distanceToSqr(eye);
            }
        }
        return selected;
    }
    private NodeSelection() {}
}
