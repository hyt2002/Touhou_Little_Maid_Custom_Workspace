package com.erobrine.tlmcw.gametest;

import static com.erobrine.tlmcw.gametest.V2Fixtures.*;

import com.erobrine.tlmcw.workspace.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** A hidden development client exercises real inventory clicks without touching player saves. */
public final class ClientWirelessConditionTest {
    private static boolean starting, configured, finished;
    private static int phase, frames;
    private static Screen timetable;
    private static int originalScale;
    private static String originalLanguage;
    private static boolean originalPause;

    public static void start() {
        NeoForge.EVENT_BUS.addListener(ClientWirelessConditionTest::startWorld);
        NeoForge.EVENT_BUS.addListener(ClientWirelessConditionTest::tick);
        NeoForge.EVENT_BUS.addListener(ClientWirelessConditionTest::rendered);
    }

    private static Screen screen(String name, Class<?>[] types, Object... args) throws Exception {
        var constructor =
                Class.forName("com.erobrine.tlmcw.client." + name).getDeclaredConstructor(types);
        constructor.setAccessible(true);
        return (Screen) constructor.newInstance(args);
    }

    private static Object field(Object value, String name) throws Exception {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(value);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void finish(Throwable failure) {
        if (finished) return;
        finished = true;
        var mc = Minecraft.getInstance();
        if (failure != null) failure.printStackTrace();
        System.out.println(
                failure == null ? "TLMCW_WIRELESS_UI_PASSED" : "TLMCW_WIRELESS_UI_FAILED");
        mc.options.guiScale().set(originalScale);
        mc.options.languageCode = originalLanguage;
        mc.getLanguageManager().setSelected(originalLanguage);
        mc.options.pauseOnLostFocus = originalPause;
        mc.stop();
    }

    private static void startWorld(ScreenEvent.Render.Post event) {
        var mc = Minecraft.getInstance();
        if (starting || !(event.getScreen() instanceof TitleScreen) || mc.getOverlay() != null)
            return;
        starting = true;
        originalScale = mc.options.guiScale().get();
        originalLanguage = mc.options.languageCode;
        originalPause = mc.options.pauseOnLostFocus;
        GLFW.glfwHideWindow(mc.getWindow().getWindow());
        mc.options.pauseOnLostFocus = false;
        mc.options.guiScale().set(2);
        mc.options.languageCode = "zh_cn";
        mc.getLanguageManager().setSelected("zh_cn");
        mc.reloadResourcePacks()
                .thenRun(
                        () ->
                                mc.execute(
                                        () -> {
                                            try {
                                                mc.resizeDisplay();
                                                mc.createWorldOpenFlows()
                                                        .createFreshLevel(
                                                                "tlmcw-wireless-ui-"
                                                                        + System
                                                                                .currentTimeMillis(),
                                                                new LevelSettings(
                                                                        "Wireless condition"
                                                                                + " verification",
                                                                        GameType.CREATIVE,
                                                                        false,
                                                                        Difficulty.PEACEFUL,
                                                                        true,
                                                                        new GameRules(),
                                                                        WorldDataConfiguration
                                                                                .DEFAULT),
                                                                new WorldOptions(0, false, false),
                                                                registry ->
                                                                        registry.registryOrThrow(
                                                                                        Registries
                                                                                                .WORLD_PRESET)
                                                                                .getHolderOrThrow(
                                                                                        WorldPresets
                                                                                                .FLAT)
                                                                                .value()
                                                                                .createWorldDimensions(),
                                                                event.getScreen());
                                            } catch (Throwable failure) {
                                                finish(failure);
                                            }
                                        }));
    }

    private static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (!starting || configured || mc.player == null || mc.level == null || mc.screen != null)
            return;
        configured = true;
        var helmet = new ItemStack(Items.LEATHER_HELMET);
        helmet.set(DataComponents.DYED_COLOR, new DyedItemColor(0xCC3344, true));
        mc.getSingleplayerServer()
                .execute(
                        () -> {
                            var player =
                                    mc.getSingleplayerServer()
                                            .getPlayerList()
                                            .getPlayer(mc.player.getUUID());
                            player.getInventory().setItem(9, helmet);
                            player.getInventory().setItem(10, new ItemStack(Items.DIAMOND, 32));
                            player.inventoryMenu.broadcastChanges();
                            mc.execute(
                                    () -> {
                                        try {
                                            var plan =
                                                    fixturePlan(
                                                                    ResourceLocation.parse(
                                                                            "minecraft:overworld"),
                                                                    List.of(
                                                                            WorkArea.between(
                                                                                    new BlockPos(
                                                                                            0, 64,
                                                                                            0),
                                                                                    new BlockPos(
                                                                                            3, 66,
                                                                                            3)),
                                                                            WorkArea.between(
                                                                                    new BlockPos(
                                                                                            10, 64,
                                                                                            0),
                                                                                    new BlockPos(
                                                                                            13, 66,
                                                                                            3))))
                                                            .withName(n(0), "菜田")
                                                            .withName(n(1), "果园")
                                                            .withSchedule(
                                                                    new WorkSchedule(
                                                                            List.of(
                                                                                    new WorkScheduleEntry(
                                                                                            n(0),
                                                                                            new DelayCondition(
                                                                                                    100)),
                                                                                    new WorkScheduleEntry(
                                                                                            n(1),
                                                                                            new DelayCondition(
                                                                                                    200))),
                                                                            true));
                                            timetable =
                                                    screen(
                                                            "WorkScheduleScreen",
                                                            new Class<?>[] {
                                                                int.class, WorkspacePlan.class
                                                            },
                                                            0,
                                                            plan);
                                            mc.setScreen(timetable);
                                        } catch (Throwable failure) {
                                            finish(failure);
                                        }
                                    });
                        });
    }

    private static void pickBoth(Screen picker, int left, int top, int width) {
        picker.mouseClicked(left + (width - 162) / 2 + 9, top + 113, 0);
        picker.mouseClicked(left + width / 2 + 32, top + 55, 0);
        picker.mouseClicked(left + (width - 162) / 2 + 27, top + 113, 0);
    }

    private static void checkInventory(Minecraft mc) {
        if (mc.player.getInventory().getItem(9).getCount() != 1
                || mc.player.getInventory().getItem(10).getCount() != 32)
            throw new AssertionError("Frequency picker consumed or moved inventory items");
    }

    private static void rendered(ScreenEvent.Render.Post event) {
        var mc = Minecraft.getInstance();
        if (finished
                || !configured
                || mc.getOverlay() != null
                || mc.screen == null
                || ++frames < 12) return;
        if (mc.player.getInventory().getItem(10).getCount() != 32) return;
        frames = 0;
        try {
            var current = event.getScreen();
            int left = (int) field(current, "left"), top = (int) field(current, "top");
            int width = (int) field(current, "panelWidth"),
                    height = (int) field(current, "panelHeight");
            for (var child : current.children())
                if (child instanceof AbstractWidget widget
                        && (widget.getX() < left
                                || widget.getY() < top
                                || widget.getRight() > left + width
                                || widget.getBottom() > top + height))
                    throw new AssertionError(
                            "Widget exceeds paper panel: " + widget.getMessage().getString());
            event.getGuiGraphics().flush();
            var path = Path.of("../build/wireless-ui/" + phase + ".png");
            Files.createDirectories(path.getParent());
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(path);
            }
            System.out.println(
                    "TLMCW_WIRELESS_UI_SNAPSHOT "
                            + phase
                            + " "
                            + current.getClass().getSimpleName());
            switch (phase++) {
                case 0 -> current.mouseClicked(left + 80, top + 87, 0);
                case 1 -> current.mouseClicked(left + 50, top + 80, 0);
                case 2 -> current.mouseClicked(left + 60, top + 161, 0);
                case 3 -> pickBoth(current, left, top, width);
                case 4 -> {
                    checkInventory(mc);
                    current.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    var unsaved = (WirelessRedstoneCondition) field(mc.screen, "signal");
                    if (!unsaved.first().equals(FrequencyMark.EMPTY)
                            || !unsaved.second().equals(FrequencyMark.EMPTY))
                        throw new AssertionError(
                                "Canceling the picker changed the condition draft");
                    int editorLeft = (int) field(mc.screen, "left"),
                            editorTop = (int) field(mc.screen, "top");
                    mc.screen.mouseClicked(editorLeft + 60, editorTop + 161, 0);
                    pickBoth(mc.screen, left, top, width);
                    mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                }
                case 5 -> {
                    current.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                    var entry =
                            (WorkScheduleEntry) ((List<?>) field(timetable, "entries")).getFirst();
                    if (!(entry.condition() instanceof WirelessRedstoneCondition c)
                            || c.first().color().orElse(-1) != 0xCC3344
                            || !c.second().equals(FrequencyMark.from(new ItemStack(Items.DIAMOND))))
                        throw new AssertionError(
                                "Inventory marks were not saved into the schedule entry");
                    checkInventory(mc);
                }
                case 6 -> {
                    current.mouseClicked(left + 80, top + 87, 0);
                    int editorLeft = (int) field(mc.screen, "left"),
                            editorTop = (int) field(mc.screen, "top");
                    mc.screen.mouseClicked(editorLeft + 50, editorTop + 109, 0);
                }
                case 7 -> {
                    current.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                    var entry =
                            (WorkScheduleEntry) ((List<?>) field(timetable, "entries")).getFirst();
                    if (((WirelessRedstoneCondition) entry.condition()).powered())
                        throw new AssertionError("Unpowered selection was not saved");
                    mc.options.guiScale().set(3);
                    mc.resizeDisplay();
                    mc.options.languageCode = "en_us";
                    mc.getLanguageManager().setSelected("en_us");
                    mc.reloadResourcePacks();
                }
                case 8 -> current.mouseClicked(left + 80, top + 87, 0);
                case 9 -> current.mouseClicked(left + 60, top + 161, 0);
                case 10 -> {
                    checkInventory(mc);
                    finish(null);
                }
                default -> throw new AssertionError("Unexpected verification phase");
            }
        } catch (Throwable failure) {
            finish(failure);
        }
    }
}
