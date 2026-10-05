package com.erobrine.tlmcw.gametest;

import com.erobrine.tlmcw.client.WorkspaceClient;
import com.erobrine.tlmcw.item.WorkspaceItems;
import com.erobrine.tlmcw.workspace.*;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Sends clicks through Minecraft's mapped key pipeline and real client/server item interactions. */
public final class ClientCompassControlsTest {
    private record Binding(KeyMapping mapping, InputConstants.Key key, KeyModifier modifier) {
        Binding(KeyMapping mapping) { this(mapping, mapping.getKey(), mapping.getKeyModifier()); }
        void restore() { mapping.setKeyModifierAndCode(modifier, key); mapping.setDown(false); }
    }
    private record Step(int mode, boolean attack) {}
    private static final List<Step> STEPS = CompassEditor.TOOL_ORDER.stream()
            .flatMap(mode -> java.util.stream.Stream.of(new Step(mode, true), new Step(mode, false))).toList();
    private static List<Binding> originals;
    private static WorkspacePlan base;
    private static EntityMaid maid;
    private static CompletableFuture<Void> ready;
    private static int step, phase, ticks, totalTicks;
    private static boolean screenshot;

    public static void start() {
        NeoForge.EVENT_BUS.addListener(ClientCompassControlsTest::tick);
        NeoForge.EVENT_BUS.addListener(ClientCompassControlsTest::snapshot);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void rebind(KeyMapping mapping, InputConstants.Key key) { mapping.setKeyModifierAndCode(KeyModifier.NONE, key); }
    private static void initialize(Minecraft mc) {
        originals = List.of(new Binding(mc.options.keyUse), new Binding(mc.options.keyAttack), new Binding(mc.options.keyShift),
                new Binding(WorkspaceClient.TOOL_MENU), new Binding(WorkspaceClient.CLEAR_DRAFT));
        rebind(mc.options.keyUse, InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_LEFT));
        rebind(mc.options.keyAttack, InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_RIGHT));
        rebind(mc.options.keyShift, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_R));
        rebind(WorkspaceClient.TOOL_MENU, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K));
        rebind(WorkspaceClient.CLEAR_DRAFT, InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_L));
        KeyMapping.resetMapping();
        base = new WorkspacePlan(ResourceLocation.parse("minecraft:overworld"), List.of(
                WorkArea.between(new BlockPos(-2, -60, -2), new BlockPos(2, -58, 2)),
                WorkArea.between(new BlockPos(5, -60, 0), new BlockPos(8, -58, 3))),
                new RouteGraph(List.of(new BlockPos(1, -60, 3)), List.of()).connect(0, -1));
        String use = mc.options.keyUse.getTranslatedKeyMessage().getString(), attack = mc.options.keyAttack.getTranslatedKeyMessage().getString();
        check(CompassHelp.text("edit_hint").getString().contains(use) && CompassHelp.text("edit_hint").getString().contains(attack), "Help did not follow swapped mouse bindings");
        check(CompassHelp.text("key_hint_work").getString().contains("R") && CompassHelp.text("key_hint_work").getString().contains("L"), "Help did not follow sneak/clear remaps");
        check(CompassHelp.text("select_apply_tool").getString().contains("K"), "Server help kept a hardcoded Alt key");
        check(CompassEditor.TOOL_ORDER.getLast() == CompassEditor.APPLY_TO_MAID && CompassEditor.APPLY_TO_MAID == 3, "Menu reorder changed persisted tool IDs");
    }
    private static void configure(Minecraft mc, int mode) {
        ready = new CompletableFuture<>();
        mc.getSingleplayerServer().execute(() -> {
            try {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                player.teleportTo(0.5, -60, 0.5);
                if (maid == null) {
                    maid = new EntityMaid(player.serverLevel());
                    maid.setTame(true, true); maid.setOwnerUUID(player.getUUID()); maid.setNoAi(true);
                    maid.moveTo(0.5, -60, 2.7); maid.setSchedule(MaidSchedule.ALL);
                    player.serverLevel().addFreshEntity(maid); maid.setHomeModeEnable(true);
                }
                WorkspaceController.clear(maid);
                ItemStack stack = new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get());
                stack.set(WorkspaceComponents.PLAN.get(), base); stack.set(WorkspaceComponents.EDIT_MODE.get(), mode);
                player.getInventory().setItem(0, stack); player.getInventory().selected = 0;
                ready.complete(null);
            } catch (Throwable failure) { ready.completeExceptionally(failure); }
        });
    }
    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !CompassEditor.isSmartCompass(mc.player.getMainHandItem())) return;
        try {
            if (originals == null) initialize(mc);
            if (++totalTicks > 900) throw new AssertionError("Mapped controls test timed out at step " + step + " / phase " + phase);
            int mode = step < STEPS.size() ? STEPS.get(step).mode() : CompassEditor.APPLY_TO_MAID;
            mc.player.setYRot(0); mc.player.yRotO = 0;
            float pitch = mode == CompassEditor.APPLY_TO_MAID ? 18 : 90;
            if (mode == CompassEditor.APPLY_TO_MAID && maid != null && mc.level.getEntity(maid.getId()) instanceof EntityMaid target) {
                var offset = target.position().add(0, target.getBbHeight() / 2, 0).subtract(mc.player.getEyePosition());
                float yaw = (float) Math.toDegrees(Math.atan2(-offset.x, offset.z));
                mc.player.setYRot(yaw); mc.player.yRotO = yaw;
                pitch = (float) -Math.toDegrees(Math.atan2(offset.y, Math.sqrt(offset.x * offset.x + offset.z * offset.z)));
            }
            mc.player.setXRot(pitch); mc.player.xRotO = pitch;
            if (screenshot) return;
            if (step == STEPS.size()) {
                KeyMapping.set(WorkspaceClient.TOOL_MENU.getKey(), true);
                if (++ticks >= 10) screenshot = true;
                return;
            }
            if (phase == 0) { configure(mc, mode); phase = 1; ticks = 0; return; }
            if (phase == 1) {
                if (!ready.isDone()) return;
                ready.join();
                if (CompassEditor.mode(mc.player.getMainHandItem()) != mode || ++ticks < 10) return;
                KeyMapping.click(STEPS.get(step).attack() ? mc.options.keyAttack.getKey() : mc.options.keyUse.getKey());
                phase = 2; ticks = 0; return;
            }
            if (++ticks < 10) return;
            verify(mc, STEPS.get(step));
            System.out.println("TLMCW_CONTROLS_STEP " + step + " mode=" + mode + " action=" + (STEPS.get(step).attack() ? "attack" : "use"));
            mc.setScreen(null); step++; phase = 0; ticks = 0;
        } catch (Throwable failure) { fail(failure); }
    }
    private static void verify(Minecraft mc, Step action) throws Exception {
        ItemStack stack = mc.player.getMainHandItem(); WorkspacePlan plan = stack.get(WorkspaceComponents.PLAN.get());
        switch (action.mode()) {
            case CompassEditor.WORK_AREAS -> {
                check(plan.areas().size() == (action.attack() ? 1 : 2), "Cuboid action used the wrong mouse button");
                if (!action.attack()) {
                    check(stack.has(WorkspaceComponents.FIRST_CORNER.get()), "Mapped use did not select a corner");
                    var field = mc.gui.getClass().getDeclaredField("overlayMessageString"); field.setAccessible(true);
                    Component message = (Component) field.get(mc.gui);
                    check(message != null && message.getString().equals(CompassHelp.text("first_corner").getString()), "Server action-bar help did not resolve the real use key");
                }
            }
            case CompassEditor.ROUTE_NODES -> {
                check(plan.areas().size() == 2 && plan.graph().points().size() == 1, "Route action removed a cuboid");
                check(action.attack() ? plan.graph().edges().isEmpty() : Integer.valueOf(0).equals(stack.get(WorkspaceComponents.ROUTE_ANCHOR.get())), "Mapped route action failed");
            }
            case CompassEditor.MARK_AREAS -> check(plan.type(0) == (action.attack() ? AreaType.WORK : AreaType.IDLE), "Marking accepted attack or failed on use");
            case CompassEditor.RENAME_AREAS, CompassEditor.SCHEDULE_OPTIONS -> {
                String expected = action.mode() == CompassEditor.RENAME_AREAS ? "RenameAreaScreen" : "WorkScheduleScreen";
                check(action.attack() ? mc.screen == null : mc.screen != null && mc.screen.getClass().getSimpleName().equals(expected), "Editor accepted attack or failed on use");
            }
            case CompassEditor.APPLY_TO_MAID -> {
                var clientMaid = (EntityMaid) mc.level.getEntity(maid.getId());
                check(clientMaid != null, "Maid did not synchronize to the client");
                var state = WorkspaceLogic.state(clientMaid);
                check(action.attack() ? state == null : state != null && state.plan().equals(base),
                        "Apply accepted attack or failed on use; hit=" + mc.hitResult + "; state=" + state);
            }
            default -> throw new AssertionError("Unknown test mode");
        }
    }
    private static void snapshot(RenderGuiEvent.Post event) {
        if (!screenshot) return;
        try {
            Minecraft mc = Minecraft.getInstance(); event.getGuiGraphics().flush();
            Path path = Path.of("../build/compass-controls/swapped-keys.png"); Files.createDirectories(path.getParent());
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(path); }
            System.out.println("TLMCW_COMPASS_CONTROLS_PASSED"); restore(); ClientWorldRenderTest.finish();
        } catch (Throwable failure) { fail(failure); }
    }
    private static void restore() { if (originals != null) { originals.forEach(Binding::restore); KeyMapping.resetMapping(); } }
    private static void fail(Throwable failure) {
        failure.printStackTrace(); System.out.println("TLMCW_COMPASS_CONTROLS_FAILED");
        try {
            Path path = Path.of("../build/compass-controls/failed.png"); Files.createDirectories(path.getParent());
            try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) { image.writeToFile(path); }
        } catch (Exception ignored) {}
        restore(); ClientWorldRenderTest.finish();
    }
    private ClientCompassControlsTest() {}
}
