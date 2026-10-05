package com.erobrine.tlmcw.mixin;

import com.erobrine.tlmcw.workspace.WorkspaceLogic;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidJoyTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitPoi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Comparator;

@Mixin(value = MaidJoyTask.class, remap = false)
public abstract class MaidJoyTaskMixin {
    @Shadow private boolean isOccupied(ServerLevel level, BlockPos pos) { throw new AssertionError(); }
    @Inject(method = "findJoy", at = @At("HEAD"), cancellable = true)
    private void tlmcw$selectedJoy(ServerLevel level, EntityMaid maid, CallbackInfoReturnable<BlockPos> cir) {
        var state = WorkspaceLogic.active(maid);
        if (state == null) return;
        var area = state.activeArea();
        cir.setReturnValue(level.getPoiManager().getInSquare(type -> type.value().equals(InitPoi.JOY_BLOCK.get()),
                        area.center(), area.horizontalRadius(), PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos).filter(pos -> area.contains(pos) && !isOccupied(level, pos))
                .min(Comparator.comparingDouble(pos -> pos.distSqr(maid.blockPosition()))).orElse(null));
    }
}
