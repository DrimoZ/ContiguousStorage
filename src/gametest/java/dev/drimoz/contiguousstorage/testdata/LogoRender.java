package dev.drimoz.contiguousstorage.testdata;

import com.buuz135.functionalstorage.FunctionalStorage;
import com.buuz135.functionalstorage.block.tile.ItemControllableDrawerTile;
import com.buuz135.functionalstorage.block.tile.StorageControllerTile;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dev-only, {@code ./gradlew runClient -Plogo}: renders the mod's logo from the real game. A 2x2 wall
 * - two Functional Storage drawers, a controller, Immaterial Drawers' energy drawer - with glowing
 * seams where the blocks touch, and past a gap a drawer that does not touch, marked with a red cross.
 * Orthographic isometric view, transparent background, written to {@code run/logo/logo.png}.
 */
public final class LogoRender {

    private static final BlockPos BASE = new BlockPos(300, -60, 300);
    private static final int SIZE = 1024;
    private static final int OUT = 512;
    /** [x, y, kind]. Kinds: 0 Functional Storage oak drawer, 1 controller, 2 energy drawer. x = 2 is the gap. */
    private static final int[][] BLOCKS = {{0, 0, 0}, {1, 0, 1}, {0, 1, 0}, {1, 1, 2}, {3, 0, 0}};
    private static final float HALF = 2.1f;

    private final Minecraft mc = Minecraft.getInstance();
    private int ticks;
    private boolean done;

    @SubscribeEvent
    public void onTick(ClientTickEvent.Post event) {
        if (done || mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
            return;
        }
        ticks++;
        if (ticks == 100) {
            mc.getSingleplayerServer().submit(this::build).join();
        } else if (ticks == 140) {
            mc.getSingleplayerServer().submit(this::fill).join();
        } else if (ticks == 240) {
            done = true;
            try {
                render();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            mc.level.disconnect();
            mc.disconnect();
            mc.stop();
        }
    }

    private void build() {
        ServerLevel level = mc.getSingleplayerServer().overworld();
        Block[] kinds = {
                FunctionalStorage.DRAWER_TYPES.get(FunctionalStorage.DrawerType.X_1).get(0).getBlock(),
                FunctionalStorage.DRAWER_CONTROLLER.getBlock(),
                BuiltInRegistries.BLOCK.get(ResourceLocation.parse("immaterialdrawers:energy_drawer"))};
        BlockPos.betweenClosed(BASE, BASE.offset(5, 3, 0)).forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3));
        // South of the wall, looking north: each block takes the state it would for a player placing it,
        // front towards the player - Functional Storage's drawers have no model for a hand-made state.
        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        player.teleportTo(level, BASE.getX() + 2.5, BASE.getY() + 1, BASE.getZ() + 4.5, 180, 20);
        for (int[] block : BLOCKS) {
            BlockPos pos = BASE.offset(block[0], block[1], 0);
            var hit = new BlockHitResult(Vec3.atCenterOf(pos).subtract(0, 0.5, 0), Direction.UP, pos.below(), false);
            BlockState state = kinds[block[2]].getStateForPlacement(new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)));
            level.setBlock(pos, state != null ? state : kinds[block[2]].defaultBlockState(), 3);
        }
    }

    // Once the blocks have ticked: Functional Storage builds a drawer's storage on its first tick.
    private void fill() {
        ServerLevel level = mc.getSingleplayerServer().overworld();
        ItemStack[] contents = {new ItemStack(Items.DIAMOND, 64), ItemStack.EMPTY, new ItemStack(Items.GOLD_INGOT, 64),
                ItemStack.EMPTY, new ItemStack(Items.EMERALD, 64)};
        for (int i = 0; i < BLOCKS.length; i++) {
            BlockPos pos = BASE.offset(BLOCKS[i][0], BLOCKS[i][1], 0);
            if (!contents[i].isEmpty() && level.getBlockEntity(pos) instanceof ItemControllableDrawerTile<?> tile
                    && !(tile instanceof StorageControllerTile<?>)) {
                tile.getStorage().insertItem(0, contents[i].copy(), false);
            }
            var storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
            if (storage != null) {
                storage.receiveEnergy(Integer.MAX_VALUE, false);
            }
        }
    }

    private void render() throws Exception {
        TextureTarget target = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
        target.setClearColor(0, 0, 0, 0);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);

        Matrix4f oldProjection = RenderSystem.getProjectionMatrix();
        VertexSorting oldSorting = RenderSystem.getVertexSorting();
        Matrix4f projection = new Matrix4f().setOrtho(-HALF, HALF, -HALF, HALF, -100, 100);
        RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        Lighting.setupFor3DItems();
        mc.gameRenderer.lightTexture().turnOnLightLayer();
        RenderSystem.enableDepthTest();

        // Isometric: the +z (front) and +x faces and the top, centred on the blocks' bounding box.
        PoseStack pose = new PoseStack();
        pose.mulPose(Axis.XP.rotationDegrees(30));
        pose.mulPose(Axis.YP.rotationDegrees(-45));
        pose.translate(-2f, -1f, -0.5f);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (int[] block : BLOCKS) {
            BlockPos pos = BASE.offset(block[0], block[1], 0);
            BlockState state = mc.level.getBlockState(pos);
            BlockEntity be = mc.level.getBlockEntity(pos);
            pose.pushPose();
            pose.translate(block[0], block[1], 0);
            mc.getBlockRenderer().renderSingleBlock(state, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    be != null ? be.getModelData() : ModelData.EMPTY, null);
            if (be != null) {
                @SuppressWarnings("unchecked")
                BlockEntityRenderer<BlockEntity> renderer = (BlockEntityRenderer<BlockEntity>) mc.getBlockEntityRenderDispatcher().getRenderer(be);
                if (renderer != null) {
                    renderer.render(be, 0, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                }
            }
            buffers.endBatch();
            pose.popPose();
        }
        Matrix4f toScreen = new Matrix4f(projection).mul(pose.last().pose());

        NativeImage image = new NativeImage(SIZE, SIZE, false);
        RenderSystem.bindTexture(target.getColorTextureId());
        image.downloadTexture(0, false);
        image.flipY();

        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(oldProjection, oldSorting);
        mc.gameRenderer.lightTexture().turnOffLightLayer();
        mc.getMainRenderTarget().bindWrite(true);
        target.destroyBuffers();

        BufferedImage frame = ImageIO.read(new ByteArrayInputStream(image.asByteArray()));
        image.close();
        Graphics2D g = frame.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Seams on the front face (z = 1) where the wall's blocks touch.
        seam(g, screen(toScreen, 1, 0.14f, 1), screen(toScreen, 1, 0.86f, 1));
        seam(g, screen(toScreen, 1, 1.14f, 1), screen(toScreen, 1, 1.86f, 1));
        seam(g, screen(toScreen, 0.14f, 1, 1), screen(toScreen, 0.86f, 1, 1));
        seam(g, screen(toScreen, 1.14f, 1, 1), screen(toScreen, 1.86f, 1, 1));
        // The gap: the seams' glow in red, as a cross - linked is teal, not linked is red.
        float[] c = screen(toScreen, 2.4f, 0.5f, 0.5f);
        int k = 38;
        Color red = new Color(255, 82, 82);
        Color core = new Color(255, 200, 200);
        seam(g, new float[]{c[0] - k, c[1] - k}, new float[]{c[0] + k, c[1] + k}, red, core);
        seam(g, new float[]{c[0] - k, c[1] + k}, new float[]{c[0] + k, c[1] - k}, red, core);
        g.dispose();

        BufferedImage out = new BufferedImage(OUT, OUT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D scaled = out.createGraphics();
        scaled.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        scaled.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        scaled.drawImage(frame, 0, 0, OUT, OUT, null);
        scaled.dispose();
        Path dir = mc.gameDirectory.toPath().resolve("logo");
        Files.createDirectories(dir);
        ImageIO.write(out, "png", dir.resolve("logo.png").toFile());
    }

    private static void seam(Graphics2D g, float[] a, float[] b) {
        seam(g, a, b, new Color(95, 227, 210), new Color(190, 255, 246));
    }

    /** A glowing line: three soft strokes of {@code glow} under a {@code core} line. */
    private static void seam(Graphics2D g, float[] a, float[] b, Color glow, Color core) {
        for (int[] layer : new int[][]{{34, 40}, {24, 70}, {16, 120}}) {
            g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), layer[1]));
            g.setStroke(new BasicStroke(layer[0], BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(Math.round(a[0]), Math.round(a[1]), Math.round(b[0]), Math.round(b[1]));
        }
        g.setColor(core);
        g.setStroke(new BasicStroke(8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(Math.round(a[0]), Math.round(a[1]), Math.round(b[0]), Math.round(b[1]));
    }

    /** A block-space point (relative to the first block) to image pixels. */
    private static float[] screen(Matrix4f toScreen, float x, float y, float z) {
        Vector4f v = toScreen.transform(new Vector4f(x, y, z, 1));
        return new float[]{(v.x / v.w + 1) / 2 * SIZE, (1 - v.y / v.w) / 2 * SIZE};
    }
}
