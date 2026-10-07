package com.erobrine.tlmcw.client;

import com.erobrine.tlmcw.network.CompassActionPayload;
import com.erobrine.tlmcw.workspace.AreaType;
import com.erobrine.tlmcw.workspace.CompassEditor;
import com.erobrine.tlmcw.workspace.CompassHelp;
import com.erobrine.tlmcw.workspace.NodeSelection;
import com.erobrine.tlmcw.workspace.WorkArea;
import com.erobrine.tlmcw.workspace.WorkspaceAccess;
import com.erobrine.tlmcw.workspace.WorkspaceComponents;
import com.erobrine.tlmcw.workspace.WorkspaceLogic;
import com.erobrine.tlmcw.workspace.WorkspacePlan;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.UUID;

public final class WorkspaceClient {
    public static final KeyMapping CLEAR_DRAFT =
            new KeyMapping(
                    "key.tlmcw.mode",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_V,
                    "key.categories.tlmcw");
    public static final KeyMapping TOOL_MENU =
            new KeyMapping(
                    "key.tlmcw.tool_menu",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_LEFT_ALT,
                    "key.categories.tlmcw");

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(CLEAR_DRAFT);
        event.register(TOOL_MENU);
    }

    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean usable =
                mc.player != null
                        && mc.screen == null
                        && CompassEditor.isSmartCompass(mc.player.getMainHandItem());
        CompassToolMenu.tick(usable, usable ? mc.player.getMainHandItem() : ItemStack.EMPTY);
        while (CLEAR_DRAFT.consumeClick()) {
            if (mc.player == null
                    || mc.screen != null
                    || !mc.player.isShiftKeyDown()
                    || !CompassEditor.isSmartCompass(mc.player.getMainHandItem())) continue;
            PacketDistributor.sendToServer(CompassActionPayload.simple(CompassActionPayload.CLEAR));
        }
    }

    public static void onInput(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null
                || mc.screen != null
                || !CompassEditor.isSmartCompass(mc.player.getMainHandItem())) return;
        if (CompassToolMenu.focused() || TOOL_MENU.isDown()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        ItemStack stack = mc.player.getMainHandItem();
        int mode = CompassEditor.mode(stack);
        // These flags describe the mapped game action, not a physical left/right mouse button.
        if (event.isAttack()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            if (stack.has(WorkspaceComponents.ROUTE_ANCHOR.get())) {
                PacketDistributor.sendToServer(
                        CompassActionPayload.simple(CompassActionPayload.CANCEL_ROUTE));
                return;
            }
            if (mode != CompassEditor.WORK_AREAS && mode != CompassEditor.ROUTE_NODES) return;
            if (stack.has(WorkspaceComponents.FIRST_CORNER.get())) return;
            WorkspacePlan plan = WorkspaceAccess.plan(stack);
            UUID index = selected(plan, mode == CompassEditor.ROUTE_NODES);
            if (index != null) {
                WorkArea area = plan.nodeArea(index);
                PacketDistributor.sendToServer(
                        new CompassActionPayload(
                                CompassActionPayload.DELETE, index, area.min(), area.max()));
            }
            return;
        }
        if (!event.isUseItem()) return;
        if (mode == CompassEditor.RENAME_AREAS || mode == CompassEditor.SCHEDULE_OPTIONS) {
            event.setCanceled(true);
            event.setSwingHand(false);
            if (mode == CompassEditor.SCHEDULE_OPTIONS)
                PacketDistributor.sendToServer(
                        CompassActionPayload.simple(CompassActionPayload.OPEN_SCHEDULE));
            else {
                WorkspacePlan plan = WorkspaceAccess.plan(stack);
                UUID index = selected(plan, false);
                if (index == null)
                    mc.player.displayClientMessage(Component.translatable("tlmcw.aim_area"), true);
                else {
                    WorkArea area = plan.nodeArea(index);
                    PacketDistributor.sendToServer(
                            new CompassActionPayload(
                                    CompassActionPayload.OPEN_RENAME,
                                    index,
                                    area.min(),
                                    area.max()));
                }
            }
            return;
        }
        // Other use actions run through the item API and its server-side validation.
    }

    private static UUID selected(WorkspacePlan plan, boolean points) {
        Minecraft mc = Minecraft.getInstance();
        if (plan == null || mc.player == null) return null;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 end =
                eye.add(
                        mc.player
                                .getLookAngle()
                                .scale(
                                        mc.player.getAttributeValue(
                                                        Attributes.BLOCK_INTERACTION_RANGE)
                                                + 1));
        return NodeSelection.pick(plan, eye, end, points, mc.player.level().dimension().location());
    }

    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !CompassEditor.isSmartCompass(mc.player.getMainHandItem())) return;
        ItemStack stack = mc.player.getMainHandItem();
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        try {
            if (mc.hitResult instanceof EntityHitResult hit
                    && hit.getEntity() instanceof EntityMaid maid) {
                var state = WorkspaceLogic.state(maid);
                if (state != null) {
                    for (UUID i : state.plan().areaIds()) {
                        if (!state.plan().inDimension(i, mc.player.level().dimension().location()))
                            continue;
                        boolean active = Objects.equals(i, state.destinationId());
                        drawArea(pose, state.plan(), i, false, active);
                    }
                    drawGraph(
                            pose,
                            state.plan(),
                            null,
                            state.remainingRoute().isEmpty()
                                    ? null
                                    : state.remainingRoute().getFirst());
                }
            } else {
                WorkspacePlan plan = WorkspaceAccess.plan(stack);
                if (plan != null) {
                    UUID selected =
                            stack.has(WorkspaceComponents.FIRST_CORNER.get())
                                    ? null
                                    : selected(
                                            plan,
                                            CompassEditor.mode(stack) == CompassEditor.ROUTE_NODES);
                    for (UUID i : plan.areaIds()) {
                        if (!plan.inDimension(i, mc.player.level().dimension().location()))
                            continue;
                        boolean highlighted = i.equals(selected);
                        drawArea(pose, plan, i, highlighted, false);
                    }
                    drawGraph(
                            pose,
                            plan,
                            selected,
                            stack.get(WorkspaceComponents.ROUTE_ANCHOR.get()));
                    var first = stack.get(WorkspaceComponents.FIRST_CORNER.get());
                    if (first != null
                            && first.dimension().equals(mc.player.level().dimension().location())
                            && mc.hitResult instanceof BlockHitResult hit) {
                        draw(
                                pose,
                                WorkArea.between(first.pos(), hit.getBlockPos()),
                                0.25f,
                                0.75f,
                                1);
                    }
                    if (CompassEditor.mode(stack) == CompassEditor.ROUTE_NODES
                            && stack.has(WorkspaceComponents.ROUTE_ANCHOR.get())) {
                        draw(
                                pose,
                                WorkArea.between(
                                        mc.player.blockPosition(), mc.player.blockPosition()),
                                0.3f,
                                0.8f,
                                1);
                        UUID anchor = stack.get(WorkspaceComponents.ROUTE_ANCHOR.get());
                        if (plan.inDimension(anchor, mc.player.level().dimension().location())) {
                            Vec3 target =
                                    selected == null
                                            ? Vec3.atCenterOf(mc.player.blockPosition())
                                            : plan.nodeArea(selected).bounds().getCenter();
                            drawLine(
                                    pose,
                                    plan.nodeArea(anchor).bounds().getCenter(),
                                    target,
                                    0.5f,
                                    1,
                                    1);
                        }
                    }
                }
            }
        } finally {
            pose.popPose();
        }
    }

    private static void drawArea(
            PoseStack pose, WorkspacePlan plan, UUID index, boolean highlighted, boolean active) {
        drawTag(pose, plan, index);
        switch (plan.type(index)) {
            case WORK -> draw(pose, plan.nodeArea(index), 1, 0, 0);
            case IDLE -> draw(pose, plan.nodeArea(index), 0, 1, 0);
            case SLEEP -> draw(pose, plan.nodeArea(index), 0, 0, 1);
        }
        if (highlighted || active) {
            // A separate outline keeps the area's type color visible while it is selected or
            // active.
            var buffer =
                    Minecraft.getInstance()
                            .renderBuffers()
                            .bufferSource()
                            .getBuffer(RenderType.lines());
            LevelRenderer.renderLineBox(
                    pose,
                    buffer,
                    plan.nodeArea(index).bounds().inflate(0.035),
                    1,
                    1,
                    highlighted ? 0.15f : 1,
                    1);
        }
    }

    private static void drawTag(PoseStack pose, WorkspacePlan plan, UUID index) {
        Minecraft mc = Minecraft.getInstance();
        var area = plan.nodeArea(index);
        Vec3 center = area.bounds().getCenter();
        Vec3 position = new Vec3(center.x, area.max().getY() + 1.35, center.z);
        if (mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(position) > 96 * 96) return;
        String name = AreaLabels.name(plan, index).getString();
        if (mc.font.width(name) > 180) name = mc.font.plainSubstrByWidth(name, 172) + "…";
        Component type = Component.translatable(plan.type(index).translationKey());
        int color =
                switch (plan.type(index)) {
                    case WORK -> 0xFFFF1111;
                    case IDLE -> 0xFF11FF11;
                    case SLEEP -> 0xFF1111FF;
                    case WAYPOINT -> 0xFF55CCFF;
                };
        pose.pushPose();
        pose.translate(position.x, position.y, position.z);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        // Match vanilla nameplates: reversing X as well reverses the glyph winding and culls the
        // text.
        pose.scale(0.05f, -0.05f, 0.05f);
        var buffer = mc.renderBuffers().bufferSource();
        // Draw the shared backing first. Font's see-through background is sorted over the glyphs
        // when it is submitted with the text, darkening both the name and the type color.
        float halfWidth = Math.max(mc.font.width(name), mc.font.width(type)) / 2.0f + 2;
        var backing = buffer.getBuffer(RenderType.textBackgroundSeeThrough());
        var matrix = pose.last().pose();
        backing.addVertex(matrix, -halfWidth, -21, -0.01f)
                .setColor(0x70000000)
                .setLight(LightTexture.FULL_BRIGHT);
        backing.addVertex(matrix, -halfWidth, 0, -0.01f)
                .setColor(0x70000000)
                .setLight(LightTexture.FULL_BRIGHT);
        backing.addVertex(matrix, halfWidth, 0, -0.01f)
                .setColor(0x70000000)
                .setLight(LightTexture.FULL_BRIGHT);
        backing.addVertex(matrix, halfWidth, -21, -0.01f)
                .setColor(0x70000000)
                .setLight(LightTexture.FULL_BRIGHT);
        for (int line = 0; line < 2; line++) {
            Component text = line == 0 ? Component.literal(name) : type;
            float x = -mc.font.width(text) / 2.0f;
            // Both lines sit above the top face, including the second line and its descenders.
            mc.font.drawInBatch(
                    text,
                    x,
                    line * 10 - 20,
                    line == 0 ? 0xFFFFFFFF : color,
                    false,
                    pose.last().pose(),
                    buffer,
                    Font.DisplayMode.SEE_THROUGH,
                    0,
                    LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
    }

    private static void drawGraph(PoseStack pose, WorkspacePlan plan, UUID selected, UUID anchor) {
        var dim = Minecraft.getInstance().player.level().dimension().location();
        for (UUID node : plan.nodes(AreaType.WAYPOINT, dim)) {
            boolean highlighted = node.equals(selected);
            boolean active = node.equals(anchor);
            draw(
                    pose,
                    plan.nodeArea(node),
                    highlighted ? 1 : 0.2f,
                    highlighted || active ? 1 : 0.7f,
                    highlighted ? 0.2f : 1);
        }
        if (anchor != null && plan.inDimension(anchor, dim) && plan.node(anchor).isArea())
            draw(pose, plan.nodeArea(anchor), 0.3f, 1, 1);
        for (var edge : plan.graph().edges()) {
            if (!plan.inDimension(edge.a(), dim) || !plan.inDimension(edge.b(), dim)) continue;
            Vec3 a = plan.nodeArea(edge.a()).bounds().getCenter(),
                    b = plan.nodeArea(edge.b()).bounds().getCenter();
            drawLine(pose, a, b, 0.2f, 0.8f, 1);
        }
    }

    private static void drawLine(
            PoseStack pose, Vec3 a, Vec3 b, float red, float green, float blue) {
        if (a.distanceToSqr(b) < 1.0e-8) return;
        var buffer =
                Minecraft.getInstance()
                        .renderBuffers()
                        .bufferSource()
                        .getBuffer(RenderType.lines());
        var matrix = pose.last();
        Vec3 normal = b.subtract(a).normalize();
        buffer.addVertex(matrix.pose(), (float) a.x, (float) a.y, (float) a.z)
                .setColor(red, green, blue, 1)
                .setNormal(matrix, (float) normal.x, (float) normal.y, (float) normal.z);
        buffer.addVertex(matrix.pose(), (float) b.x, (float) b.y, (float) b.z)
                .setColor(red, green, blue, 1)
                .setNormal(matrix, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void draw(PoseStack pose, WorkArea area, float red, float green, float blue) {
        var buffer =
                Minecraft.getInstance()
                        .renderBuffers()
                        .bufferSource()
                        .getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(
                pose, buffer, area.bounds().inflate(0.002), red, green, blue, 1);
    }

    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!CompassEditor.isSmartCompass(stack)) return;
        event.getToolTip()
                .add(
                        CompassToolMenu.modeName(CompassEditor.mode(stack))
                                .copy()
                                .withStyle(ChatFormatting.GOLD));
        event.getToolTip().add(CompassHelp.text("menu_hold").withStyle(ChatFormatting.GRAY));
        if (CompassEditor.mode(stack) == CompassEditor.WORK_AREAS
                || CompassEditor.mode(stack) == CompassEditor.ROUTE_NODES)
            event.getToolTip()
                    .add(
                            CompassHelp.text(
                                            CompassEditor.mode(stack) == CompassEditor.ROUTE_NODES
                                                    ? "key_hint_route"
                                                    : "key_hint_work")
                                    .withStyle(ChatFormatting.GRAY));
        var plan = WorkspaceAccess.plan(stack);
        event.getToolTip()
                .add(
                        Component.translatable(
                                "tlmcw.typed_area_count",
                                plan == null ? 0 : plan.nodes(AreaType.WORK).size(),
                                plan == null ? 0 : plan.nodes(AreaType.IDLE).size(),
                                plan == null ? 0 : plan.nodes(AreaType.SLEEP).size()));
        event.getToolTip()
                .add(
                        Component.translatable(
                                "tlmcw.route_count",
                                plan == null ? 0 : plan.nodes(AreaType.WAYPOINT).size(),
                                plan == null ? 0 : plan.graph().edges().size()));
        event.getToolTip()
                .add(
                        CompassHelp.text(
                                        switch (CompassEditor.mode(stack)) {
                                            case CompassEditor.ROUTE_NODES -> "route_edit_hint";
                                            case CompassEditor.MARK_AREAS -> "mark_edit_hint";
                                            case CompassEditor.APPLY_TO_MAID -> "apply_hint";
                                            case CompassEditor.RENAME_AREAS -> "rename_edit_hint";
                                            case CompassEditor.SCHEDULE_OPTIONS ->
                                                    "schedule_edit_hint";
                                            case CompassEditor.READ_FROM_MAID -> "read_hint";
                                            default -> "edit_hint";
                                        })
                                .withStyle(ChatFormatting.GRAY));
        if (CompassEditor.mode(stack) != CompassEditor.APPLY_TO_MAID)
            event.getToolTip().add(CompassHelp.text("apply_hint").withStyle(ChatFormatting.GRAY));
    }

    private WorkspaceClient() {}
}
