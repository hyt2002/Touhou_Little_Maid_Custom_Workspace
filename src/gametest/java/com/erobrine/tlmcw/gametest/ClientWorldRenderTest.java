package com.erobrine.tlmcw.gametest;

import com.erobrine.tlmcw.item.WorkspaceItems;
import com.erobrine.tlmcw.workspace.*;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Real world/camera screenshots in a new, isolated development save. Excluded from release JARs. */
public final class ClientWorldRenderTest {
    private static boolean starting, configured;
    private static int phase, frames;
    private static WorkspacePlan plan;
    private static final int[][] labels = new int[3][2];
    private static int originalScale;
    private static String originalLanguage;
    private static GraphicsStatus originalGraphics;
    private static CloudStatus originalClouds;
    private static boolean originalPause;

    public static void start() {
        NeoForge.EVENT_BUS.addListener(ClientWorldRenderTest::startWorld);
        NeoForge.EVENT_BUS.addListener(ClientWorldRenderTest::tick);
        NeoForge.EVENT_BUS.addListener(ClientWorldRenderTest::project);
        NeoForge.EVENT_BUS.addListener(ClientWorldRenderTest::snapshot);
    }

    private static void startWorld(ScreenEvent.Render.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (starting || !(event.getScreen() instanceof TitleScreen) || mc.getOverlay() != null) return;
        starting = true;
        GLFW.glfwHideWindow(mc.getWindow().getWindow());
        originalScale = mc.options.guiScale().get(); originalLanguage = mc.options.languageCode;
        originalGraphics = mc.options.graphicsMode().get(); originalClouds = mc.options.cloudStatus().get();
        originalPause = mc.options.pauseOnLostFocus;
        mc.options.pauseOnLostFocus = false;
        mc.options.guiScale().set(2); mc.options.graphicsMode().set(GraphicsStatus.FANCY); mc.options.cloudStatus().set(CloudStatus.OFF);
        mc.options.languageCode = "zh_cn"; mc.getLanguageManager().setSelected("zh_cn");
        mc.reloadResourcePacks().thenRun(() -> mc.execute(() -> {
            try {
                mc.resizeDisplay();
                mc.createWorldOpenFlows().createFreshLevel("tlmcw-render-" + System.currentTimeMillis(),
                        new LevelSettings("World render verification", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(0, false, false),
                        registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                        event.getScreen());
            } catch (Throwable failure) { fail(failure); }
        }));
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!starting || mc.player == null || mc.level == null || mc.screen != null) return;
        if (configured) {
            mc.player.setYRot(0); mc.player.yRotO = 0;
            mc.player.setXRot(18); mc.player.xRotO = 18;
            return;
        }
        try {
            configured = true;
            var model = mc.getItemRenderer().getModel(new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get()), mc.level, mc.player, 0);
            var sprite = model.getParticleIcon().contents();
            if (!sprite.name().equals(ResourceLocation.parse("touhou_little_maid_custom_workspace:item/kappa_smart_compass"))
                    || sprite.width() != 16 || sprite.height() != 16 || sprite.getUniqueFrames().count() != 5)
                throw new AssertionError("Smart compass model did not load the custom five-frame texture");
            System.out.println("TLMCW_SMART_COMPASS_TEXTURE_PASSED");
            plan = new WorkspacePlan(ResourceLocation.parse("minecraft:overworld"), List.of(
                    WorkArea.between(new BlockPos(-9, -60, 0), new BlockPos(-6, -58, 3)),
                    WorkArea.between(new BlockPos(-2, -60, 0), new BlockPos(1, -58, 3)),
                    WorkArea.between(new BlockPos(5, -60, 0), new BlockPos(8, -58, 3))))
                    .withType(1, AreaType.IDLE).withType(2, AreaType.SLEEP)
                    .withName(0, "一号种植园").withName(1, "庭院休息区").withName(2, "女仆卧室");
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer();
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                player.teleportTo(0, -55, -16);
                player.setYRot(0); player.setXRot(18);
                player.getAbilities().flying = true; player.onUpdateAbilities();
                var stack = new ItemStack(WorkspaceItems.KAPPA_SMART_COMPASS.get());
                stack.set(WorkspaceComponents.PLAN.get(), plan);
                player.getInventory().setItem(0, stack); player.getInventory().selected = 0;
                var level = server.getLevel(Level.OVERWORLD);
                level.setDayTime(6000); level.setWeatherParameters(6000, 0, false, false);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            });
        } catch (Throwable failure) { fail(failure); }
    }

    private static void project(RenderLevelStageEvent event) {
        if (!configured || event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        var camera = event.getCamera().getPosition();
        Matrix4f matrix = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        for (int i = 0; i < plan.areas().size(); i++) {
            var area = plan.areas().get(i); var center = area.bounds().getCenter();
            Vector4f position = matrix.transform(new Vector4f((float) (center.x - camera.x),
                    (float) (area.max().getY() + 1.6 - camera.y), (float) (center.z - camera.z), 1));
            labels[i][0] = Math.round((position.x / position.w + 1) * mc.getWindow().getWidth() / 2);
            labels[i][1] = Math.round((1 - position.y / position.w) * mc.getWindow().getHeight() / 2);
        }
    }

    private static void snapshot(RenderGuiEvent.Post event) {
        if (Boolean.getBoolean("tlmcw.compassControlsTest")) return;
        Minecraft mc = Minecraft.getInstance();
        if (!configured || mc.player == null || mc.screen != null || !CompassEditor.isSmartCompass(mc.player.getMainHandItem()) || ++frames < 80) return;
        // Wait for the server time packet and the client's weather fade, rather than just rendered frames.
        if (phase == 2 && (mc.level.getDayTime() % 24000 != 18000 || mc.level.getRainLevel(1) < 0.5f)) return;
        frames = 0;
        try {
            event.getGuiGraphics().flush();
            Path path = Path.of("../build/world-render/" + phase + ".png"); Files.createDirectories(path.getParent());
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(path);
                for (int i = 0; i < labels.length; i++) {
                    int white = 0;
                    for (int x = Math.max(0, labels[i][0] - 75); x < Math.min(image.getWidth(), labels[i][0] + 75); x++)
                        for (int y = Math.max(0, labels[i][1] - 28); y < Math.min(image.getHeight(), labels[i][1] + 28); y++) {
                            int pixel = image.getPixelRGBA(x, y);
                            if ((pixel & 255) > 230 && (pixel >> 8 & 255) > 230 && (pixel >> 16 & 255) > 230) white++;
                        }
                    if (white < 8) throw new AssertionError("No visible label for area " + i + " at " + labels[i][0] + "," + labels[i][1]);
                }
            }
            System.out.println("TLMCW_WORLD_RENDER_SNAPSHOT " + phase);
            switch (phase++) {
                case 0 -> { mc.options.guiScale().set(3); mc.resizeDisplay(); }
                case 1 -> mc.getSingleplayerServer().execute(() -> {
                    var level = mc.getSingleplayerServer().getLevel(Level.OVERWORLD);
                    level.setDayTime(18000); level.setWeatherParameters(0, 6000, true, false);
                });
                case 2 -> { System.out.println("TLMCW_WORLD_RENDER_PASSED"); restore(mc); mc.stop(); }
                default -> throw new AssertionError("Unexpected render test phase");
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private static void restore(Minecraft mc) {
        mc.options.guiScale().set(originalScale); mc.options.languageCode = originalLanguage;
        mc.options.graphicsMode().set(originalGraphics); mc.options.cloudStatus().set(originalClouds);
        mc.options.pauseOnLostFocus = originalPause; mc.getLanguageManager().setSelected(originalLanguage);
    }
    public static void finish() { Minecraft mc = Minecraft.getInstance(); restore(mc); mc.stop(); }
    private static void fail(Throwable failure) {
        failure.printStackTrace(); System.out.println("TLMCW_WORLD_RENDER_FAILED");
        Minecraft mc = Minecraft.getInstance(); restore(mc); mc.stop();
    }
    private ClientWorldRenderTest() {}
}
