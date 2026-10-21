package io.github.dirnei.mc.heavymatica.thumbnail;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.dirnei.mc.heavymatica.formats.SchematicModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders a schematic into a 256 x 256 PNG with a fixed isometric camera (D12), offscreen: block models are
 * tessellated like moving blocks, drawn into a texture of our own and read back from the GPU. Must be called on the
 * render thread. Only the visible surface is drawn, up to {@link #BLOCK_BUDGET} blocks.
 */
public final class ThumbnailRenderer {

    public static final int SIZE = 256;
    public static final int BLOCK_BUDGET = 200_000;

    private ThumbnailRenderer() {
    }

    /** Renders and returns the PNG bytes once the GPU readback completes. */
    public static CompletableFuture<byte[]> renderPng(SchematicModel model) {
        CompletableFuture<byte[]> result = new CompletableFuture<>();
        try {
            render(model, image -> {
                try (image) {
                    Path file = Files.createTempFile("heavymatica-thumbnail", ".png");
                    try {
                        image.writeToFile(file);
                        result.complete(Files.readAllBytes(file));
                    } finally {
                        Files.deleteIfExists(file);
                    }
                } catch (IOException | RuntimeException e) {
                    result.completeExceptionally(e);
                }
            });
        } catch (RuntimeException e) {
            result.completeExceptionally(e);
        }
        return result;
    }

    private static void render(SchematicModel model, java.util.function.Consumer<NativeImage> callback) {
        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        GpuDevice device = RenderSystem.getDevice();
        GpuTexture color = device.createTexture(() -> "Heavymatica thumbnail", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC
                | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8, SIZE, SIZE, 1, 1);
        GpuTextureView colorView = device.createTextureView(color);
        TextureFormat depthFormat = minecraft.getMainRenderTarget().getDepthTexture().getFormat();
        GpuTexture depth = device.createTexture(() -> "Heavymatica thumbnail depth", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST
                | GpuTexture.USAGE_TEXTURE_BINDING, depthFormat, SIZE, SIZE, 1, 1);
        GpuTextureView depthView = device.createTextureView(depth);
        ProjectionMatrixBuffer projectionBuffer = new ProjectionMatrixBuffer("Heavymatica thumbnail");

        try (ByteBufferBuilder bytes = new ByteBufferBuilder(1 << 20)) {
            device.createCommandEncoder().clearColorAndDepthTextures(color, 0, depth, 1.0);
            Projection projection = new Projection();
            projection.setupOrtho(-4000.0F, 4000.0F, SIZE, SIZE, true);
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.ORTHOGRAPHIC);
            RenderSystem.outputColorTextureOverride = colorView;
            RenderSystem.outputDepthTextureOverride = depthView;
            try {
                MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(bytes);
                drawBlocks(model, bufferSource, minecraft);
                bufferSource.endBatch();
            } finally {
                RenderSystem.outputColorTextureOverride = null;
                RenderSystem.outputDepthTextureOverride = null;
                RenderSystem.restoreProjectionMatrix();
            }
        }

        GpuBuffer readback = device.createBuffer(() -> "Heavymatica thumbnail readback", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                (long) SIZE * SIZE * color.getFormat().pixelSize());
        CommandEncoder encoder = device.createCommandEncoder();
        encoder.copyTextureToBuffer(color, readback, 0L, () -> {
            try (GpuBuffer.MappedView view = encoder.mapBuffer(readback, true, false)) {
                NativeImage image = new NativeImage(SIZE, SIZE, false);
                for (int y = 0; y < SIZE; y++) {
                    for (int x = 0; x < SIZE; x++) {
                        image.setPixelABGR(x, SIZE - y - 1, view.data().getInt((x + y * SIZE) * color.getFormat().pixelSize()));
                    }
                }
                callback.accept(image);
            } finally {
                readback.close();
                colorView.close();
                color.close();
                depthView.close();
                depth.close();
                projectionBuffer.close();
            }
        }, 0);
    }

    private static void drawBlocks(SchematicModel model, MultiBufferSource.BufferSource bufferSource, Minecraft minecraft) {
        SchematicView view = new SchematicView(model);
        BlockStateModelSet models = minecraft.getModelManager().getBlockStateModelSet();
        ModelBlockRenderer renderer = new ModelBlockRenderer(true, true, minecraft.getBlockColors());

        // Isometric camera: fit the bounding box's diagonal into the image, centred.
        float diagonal = (float) Math.sqrt((double) model.width() * model.width() + (double) model.height() * model.height() + (double) model.length() * model.length());
        float scale = SIZE * 0.92F / Math.max(diagonal, 1.0F);
        PoseStack pose = new PoseStack();
        pose.translate(SIZE / 2.0F, SIZE / 2.0F, 0.0F);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(30.0F));
        pose.mulPose(Axis.YP.rotationDegrees(225.0F));
        pose.translate(-model.width() / 2.0F, -model.height() / 2.0F, -model.length() / 2.0F);

        BlockQuadOutput output = (x, y, z, quad, instance) -> {
            pose.pushPose();
            pose.translate(x, y, z);
            ChunkSectionLayer layer = quad.materialInfo().layer();
            bufferSource.getBuffer(switch (layer) {
                case SOLID -> RenderTypes.solidMovingBlock();
                case CUTOUT -> RenderTypes.cutoutMovingBlock();
                case TRANSLUCENT -> RenderTypes.translucentMovingBlock();
            }).putBakedQuad(pose.last(), quad, instance);
            pose.popPose();
        };

        int drawn = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < model.height() && drawn < BLOCK_BUDGET; y++) {
            for (int z = 0; z < model.length() && drawn < BLOCK_BUDGET; z++) {
                for (int x = 0; x < model.width() && drawn < BLOCK_BUDGET; x++) {
                    pos.set(x, y, z);
                    BlockState state = view.getBlockState(pos);
                    if (state.isAir() || state.getRenderShape() != RenderShape.MODEL || hidden(view, pos)) {
                        continue;
                    }
                    BlockPos fixed = pos.immutable();
                    renderer.tesselateBlock(output, x, y, z, view, fixed, state, models.get(state), state.getSeed(fixed));
                    drawn++;
                }
            }
        }
    }

    /** A block surrounded by full opaque blocks on every side cannot be seen. */
    private static boolean hidden(SchematicView view, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            if (!view.contains(neighbour) || !view.getBlockState(neighbour).isSolidRender()) {
                return false;
            }
        }
        return true;
    }
}
