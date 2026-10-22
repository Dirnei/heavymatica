package io.github.dirnei.mc.heavymatica.targeting.jade;

import io.github.dirnei.mc.heavymatica.Heavymatica;
import io.github.dirnei.mc.heavymatica.HeavymaticaClient;
import io.github.dirnei.mc.heavymatica.placement.PlacementSession;
import io.github.dirnei.mc.heavymatica.targeting.GhostTargeting.GhostTarget;
import io.github.dirnei.mc.heavymatica.targeting.TargetHud;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Shows the ghost target in Jade's tooltip as its planned block, with the targeting HUD's lines below the name. Only
 * Jade loads this class.
 */
@WailaPlugin
public final class HeavymaticaJadePlugin implements IWailaPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Heavymatica.MOD_ID, "ghost_target");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addRayTraceCallback((hit, accessor, original) -> replace(registration, hit, accessor));
        registration.registerBlockComponent(new GhostLines(), Block.class);
        TargetHud.showInJade();
    }

    /** Jade keeps no accessor when a callback returns null, so the one it passed in is returned unchanged. */
    private static Accessor<?> replace(IWailaClientRegistration registration, HitResult hit, Accessor<?> accessor) {
        GhostTarget target = target();
        if (target == null || !nearerThan(target, hit)) {
            return accessor;
        }
        Vec3 centre = Vec3.atCenterOf(target.pos());
        return registration.blockAccessor()
                .blockState(target.planned())
                .blockEntity((BlockEntity) null)
                .hit(new BlockHitResult(centre, Direction.UP, target.pos(), false))
                .serverConnected(false)
                .serverData(null)
                .build();
    }

    /** The target as far as the HUD range shows it. */
    private static @Nullable GhostTarget target() {
        return TargetHud.shownTarget(HeavymaticaClient.targeting());
    }

    /** True when Jade hit nothing, the target's position, or something farther away than the target. */
    private static boolean nearerThan(GhostTarget target, HitResult hit) {
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        if (hit instanceof BlockHitResult block && block.getBlockPos().equals(target.pos())) {
            return true;
        }
        Entity camera = Minecraft.getInstance().getCameraEntity();
        return camera == null || target.distance() <= hit.getLocation().distanceTo(camera.getEyePosition());
    }

    /** Adds the HUD's lines after the block name, for the accessor of the current target only. */
    private static final class GhostLines implements IBlockComponentProvider {

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            GhostTarget target = target();
            PlacementSession session = HeavymaticaClient.placements();
            if (target == null || session == null || !accessor.getPosition().equals(target.pos()) || accessor.getBlockState() != target.planned()) {
                return;
            }
            List<TargetHud.Line> lines = TargetHud.lines(target, TargetHud.selectedPlacement(session));
            for (TargetHud.Line line : lines.subList(1, lines.size())) {
                tooltip.add(line.text().copy().withColor(line.color() & 0xFFFFFF));
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
