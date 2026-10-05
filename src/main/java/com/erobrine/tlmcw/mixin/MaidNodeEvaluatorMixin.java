package com.erobrine.tlmcw.mixin;

import com.erobrine.tlmcw.workspace.WorkspaceLogic;
import com.erobrine.tlmcw.workspace.WorkspaceState;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.navigation.MaidNodeEvaluator;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MaidNodeEvaluator.class, remap = false)
public abstract class MaidNodeEvaluatorMixin extends WalkNodeEvaluator {
    @Override
    public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob) {
        if (mob instanceof EntityMaid maid) {
            WorkspaceState state = WorkspaceLogic.active(maid);
            if (state != null && !state.travelling() && maid.isWithinRestriction()
                    && !state.activeArea().contains(new BlockPos(x, y, z))) return PathType.BLOCKED;
        }
        return super.getPathTypeOfMob(context, x, y, z, mob);
    }
    @Redirect(method = "getMaidBlockPathTypeRaw", at = @At(value = "INVOKE",
            target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;isWithinRestriction(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean tlmcw$walkingNode(EntityMaid maid, BlockPos pos) {
        WorkspaceState state = WorkspaceLogic.active(maid);
        // Bounds apply to the feet node above. Collision probes also inspect the head and floor.
        return state == null ? maid.isWithinRestriction(pos) : true;
    }
}
