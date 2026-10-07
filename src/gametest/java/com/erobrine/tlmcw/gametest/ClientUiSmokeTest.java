package com.erobrine.tlmcw.gametest;

import static com.erobrine.tlmcw.gametest.V2Fixtures.*;

import com.erobrine.tlmcw.workspace.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Opt-in screenshots and real widget interactions. Excluded from the release JAR. */
public final class ClientUiSmokeTest {
    private static int phase = -1, frames;
    private static boolean loading;
    private static Screen parent;
    private static WorkspacePlan plan;
    private static int originalScale;
    private static String originalLanguage;

    public static void start() {
        NeoForge.EVENT_BUS.addListener(ClientUiSmokeTest::rendered);
    }

    private static Screen screen(String type, Class<?>[] types, Object... values) throws Exception {
        var constructor =
                Class.forName("com.erobrine.tlmcw.client." + type).getDeclaredConstructor(types);
        constructor.setAccessible(true);
        return (Screen) constructor.newInstance(values);
    }

    private static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void restore(Minecraft mc) {
        if (originalLanguage == null) return;
        mc.options.guiScale().set(originalScale);
        mc.options.languageCode = originalLanguage;
        mc.getLanguageManager().setSelected(originalLanguage);
    }

    private static void rendered(ScreenEvent.Render.Post event) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (phase == -1) {
                if (loading
                        || !(event.getScreen() instanceof TitleScreen)
                        || mc.getOverlay() != null) return;
                loading = true;
                originalScale = mc.options.guiScale().get();
                originalLanguage = mc.options.languageCode;
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.languageCode = "zh_cn";
                mc.getLanguageManager().setSelected("zh_cn");
                mc.reloadResourcePacks()
                        .thenRun(
                                () ->
                                        mc.execute(
                                                () -> {
                                                    try {
                                                        plan =
                                                                fixturePlan(
                                                                                ResourceLocation
                                                                                        .parse(
                                                                                                "minecraft:overworld"),
                                                                                List.of(
                                                                                        WorkArea
                                                                                                .between(
                                                                                                        new BlockPos(
                                                                                                                0,
                                                                                                                64,
                                                                                                                0),
                                                                                                        new BlockPos(
                                                                                                                3,
                                                                                                                66,
                                                                                                                3)),
                                                                                        WorkArea
                                                                                                .between(
                                                                                                        new BlockPos(
                                                                                                                10,
                                                                                                                64,
                                                                                                                0),
                                                                                                        new BlockPos(
                                                                                                                13,
                                                                                                                66,
                                                                                                                3)),
                                                                                        WorkArea
                                                                                                .between(
                                                                                                        new BlockPos(
                                                                                                                20,
                                                                                                                64,
                                                                                                                0),
                                                                                                        new BlockPos(
                                                                                                                23,
                                                                                                                66,
                                                                                                                3))))
                                                                        .withName(n(0), "一号种植园")
                                                                        .withName(n(1), "树木采集区")
                                                                        .withName(
                                                                                n(2),
                                                                                "长名称选区".repeat(10))
                                                                        .withSchedule(
                                                                                new WorkSchedule(
                                                                                        List.of(
                                                                                                new WorkScheduleEntry(
                                                                                                                n(
                                                                                                                        2),
                                                                                                                new DelayCondition(
                                                                                                                        2400)),
                                                                                                        new WorkScheduleEntry(
                                                                                                                n(
                                                                                                                        0),
                                                                                                                new DelayCondition(
                                                                                                                        600)),
                                                                                                new WorkScheduleEntry(
                                                                                                                n(
                                                                                                                        1),
                                                                                                                new DelayCondition(
                                                                                                                        100)),
                                                                                                        new WorkScheduleEntry(
                                                                                                                n(
                                                                                                                        0),
                                                                                                                new DelayCondition(
                                                                                                                        40))),
                                                                                        true));
                                                        mc.options.guiScale().set(2);
                                                        mc.resizeDisplay();
                                                        mc.setScreen(
                                                                screen(
                                                                        "RenameAreaScreen",
                                                                        new Class[] {
                                                                            int.class,
                                                                            WorkspacePlan.class,
                                                                            int.class
                                                                        },
                                                                        0,
                                                                        plan,
                                                                        2));
                                                        phase = 0;
                                                    } catch (Exception exception) {
                                                        throw new IllegalStateException(exception);
                                                    }
                                                }));
                return;
            }
            if (mc.getOverlay() != null || ++frames < 8) return;
            frames = 0;
            int left = (int) field(event.getScreen(), "left"),
                    top = (int) field(event.getScreen(), "top");
            int panelWidth = (int) field(event.getScreen(), "panelWidth"),
                    panelHeight = (int) field(event.getScreen(), "panelHeight");
            for (var child : event.getScreen().children())
                if (child instanceof AbstractWidget widget) {
                    if (widget.getX() < left
                            || widget.getY() < top
                            || widget.getRight() > left + panelWidth
                            || widget.getBottom() > top + panelHeight)
                        throw new AssertionError(
                                "Widget outside paper panel: " + widget.getMessage().getString());
                }
            event.getGuiGraphics().flush();
            Path path = Path.of("../build/ui-smoke/" + phase + ".png");
            Files.createDirectories(path.getParent());
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(path);
            }
            System.out.println(
                    "TLMCW_UI_SNAPSHOT "
                            + phase
                            + " "
                            + event.getScreen().getClass().getSimpleName());
            switch (phase++) {
                case 0 -> {
                    parent =
                            screen(
                                    "WorkScheduleScreen",
                                    new Class[] {int.class, WorkspacePlan.class},
                                    0,
                                    plan);
                    mc.setScreen(parent);
                }
                case 1 -> parent.mouseClicked(left + 65, top + 49 + 38, 0);
                case 2 -> {
                    EditBox duration =
                            event.getScreen().children().stream()
                                    .filter(EditBox.class::isInstance)
                                    .map(EditBox.class::cast)
                                    .findFirst()
                                    .orElseThrow();
                    duration.setValue("600");
                    event.getScreen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                    var entry = (WorkScheduleEntry) ((List<?>) field(parent, "entries")).getFirst();
                    if (entry.condition().ticks() != 600)
                        throw new AssertionError("Condition GUI failed to update the entry");
                    parent.mouseClicked(left + 65, top + 49 + 10, 0);
                }
                case 3 -> {
                    event.getScreen().mouseClicked(left + 35, top + 82 + 10, 0);
                    event.getScreen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                    var entry = (WorkScheduleEntry) ((List<?>) field(parent, "entries")).getFirst();
                    if (!entry.destination().equals(n(0)))
                        throw new AssertionError("Destination GUI failed to update the entry");
                    mc.options.guiScale().set(3);
                    mc.resizeDisplay();
                }
                case 4 -> {
                    parent.mouseScrolled(left + 60, top + 90, 0, -7);
                }
                case 5 -> {
                    System.out.println("TLMCW_UI_SMOKE_PASSED");
                    restore(mc);
                    mc.stop();
                }
                default -> throw new AssertionError("Unexpected UI test phase");
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.out.println("TLMCW_UI_SMOKE_FAILED");
            restore(mc);
            mc.stop();
        }
    }
}
