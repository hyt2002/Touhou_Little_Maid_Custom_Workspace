package com.erobrine.tlmcw.mixin;

import com.erobrine.tlmcw.workspace.WorkspaceBehaviorGate;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidPickupEntitiesTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidUpdateActivityFromSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.ArrayList;

@Mixin(value = MaidBrain.class, remap = false)
public abstract class MaidBrainMixin {
    @Redirect(method = {"registerWorkGoals", "registerIdleGoals", "registerRestGoals"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/Brain;addActivity(Lnet/minecraft/world/entity/schedule/Activity;Lcom/google/common/collect/ImmutableList;)V"))
    private static void tlmcw$gateWork(Brain<EntityMaid> brain, Activity activity,
            ImmutableList<Pair<Integer, BehaviorControl<? super EntityMaid>>> behaviors) {
        var gated = new ArrayList<Pair<Integer, BehaviorControl<? super EntityMaid>>>();
        for (var pair : behaviors) {
            BehaviorControl<? super EntityMaid> original = pair.getSecond();
            gated.add(Pair.of(pair.getFirst(), original instanceof MaidUpdateActivityFromSchedule
                    ? original : new WorkspaceBehaviorGate(original, activity)));
        }
        brain.addActivity(activity, ImmutableList.copyOf(gated));
    }
    @Redirect(method = "registerCoreGoals", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/Brain;addActivity(Lnet/minecraft/world/entity/schedule/Activity;Lcom/google/common/collect/ImmutableList;)V"))
    private static void tlmcw$gatePickup(Brain<EntityMaid> brain, Activity activity,
            ImmutableList<Pair<Integer, BehaviorControl<? super EntityMaid>>> behaviors) {
        var gated = new ArrayList<Pair<Integer, BehaviorControl<? super EntityMaid>>>();
        for (var pair : behaviors) {
            BehaviorControl<? super EntityMaid> original = pair.getSecond();
            gated.add(Pair.of(pair.getFirst(), original instanceof MaidPickupEntitiesTask
                    ? new WorkspaceBehaviorGate(original, activity) : original));
        }
        brain.addActivity(activity, ImmutableList.copyOf(gated));
    }
}
