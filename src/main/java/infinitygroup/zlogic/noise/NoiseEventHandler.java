package infinitygroup.zlogic.noise;

import infinitygroup.zlogic.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

public final class NoiseEventHandler {
    private NoiseEventHandler() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!Config.enableNoiseSystem) {
            return;
        }

        for (ServerLevel level : event.getServer().getAllLevels()) {
            NoiseManager.tick(level);
        }
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!Config.enableNoiseSystem || event.isCanceled()) {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        UUID sourceId = event.getPlayer() != null ? event.getPlayer().getUUID() : null;
        NoiseManager.emitNoise(level, event.getPos(), 16.0D, 100, NoiseSourceType.BLOCK_BREAK, sourceId);
    }

    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!Config.enableNoiseSystem) {
            return;
        }

        Level level = event.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        UUID sourceId = null;
        if (event.getExplosion().getIndirectSourceEntity() != null) {
            sourceId = event.getExplosion().getIndirectSourceEntity().getUUID();
        } else if (event.getExplosion().getDirectSourceEntity() != null) {
            sourceId = event.getExplosion().getDirectSourceEntity().getUUID();
        }

        BlockPos pos = BlockPos.containing(event.getExplosion().center());
        NoiseManager.emitNoise(serverLevel, pos, 48.0D, 200, NoiseSourceType.EXPLOSION, sourceId);
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!Config.enableNoiseSystem) {
            return;
        }

        if (event.getLevel() instanceof ServerLevel level) {
            NoiseManager.clearLevel(level);
        }
    }
}
