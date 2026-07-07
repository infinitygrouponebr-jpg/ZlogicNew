package infinitygroup.zlogic.zombie;

import com.mojang.logging.LogUtils;
import infinitygroup.zlogic.Config;
import infinitygroup.zlogic.Zlogic;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import org.slf4j.Logger;

public final class ZlogicSpawnControlHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ZlogicSpawnControlHandler() {
    }

    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!Config.disableVanillaMonsterSpawns && !Config.disableAllNaturalHostileMobSpawns) {
            return;
        }

        Mob mob = event.getEntity();
        if (!(mob instanceof Monster)) {
            return;
        }

        if (Config.allowZlogicSpawnedMonsters && ZombieMarkingHelper.isZlogicSpawned(mob)) {
            return;
        }

        MobSpawnType spawnType = event.getSpawnType();
        if (Config.allowSpawnerMonsterSpawns && isSpawnerSpawn(spawnType)) {
            return;
        }

        if (Config.allowStructureMonsterSpawns && isStructureSpawn(spawnType)) {
            return;
        }

        if (Config.disableVanillaMonsterSpawnsOnlyInOverworld && !isOverworld(mob)) {
            return;
        }

        if (!shouldBlockMob(mob.getType(), spawnType)) {
            return;
        }

        event.setSpawnCancelled(true);

        if (Config.vanillaSpawnBlockDebugLogs || Config.debugLogs) {
            LOGGER.info(
                "[{}] blocked vanilla monster spawn: type={} spawnType={} dim={} pos={} reason={}",
                Zlogic.MODID,
                mob.getType().toShortString(),
                spawnType,
                mob.level().dimension().location(),
                new BlockPos((int) Math.floor(event.getX()), (int) Math.floor(event.getY()), (int) Math.floor(event.getZ())),
                describeReason(mob.getType(), spawnType)
            );
        }
    }

    private static boolean shouldBlockMob(EntityType<?> type, MobSpawnType spawnType) {
        if (type == null || spawnType == null) {
            return false;
        }

        if (Config.disableAllNaturalHostileMobSpawns) {
            return isNaturalishSpawn(spawnType);
        }

        if (!Config.disableVanillaMonsterSpawns) {
            return false;
        }

        if (!isNaturalishSpawn(spawnType)) {
            return false;
        }

        return type == EntityType.ZOMBIE
            || type == EntityType.ZOMBIE_VILLAGER
            || type == EntityType.HUSK
            || type == EntityType.DROWNED
            || type == EntityType.SKELETON
            || type == EntityType.STRAY
            || type == EntityType.WITHER_SKELETON
            || type == EntityType.CREEPER
            || type == EntityType.SPIDER
            || type == EntityType.CAVE_SPIDER
            || type == EntityType.ENDERMAN
            || type == EntityType.SLIME
            || type == EntityType.WITCH
            || type == EntityType.PILLAGER;
    }

    private static boolean isSpawnerSpawn(MobSpawnType spawnType) {
        String name = spawnType.name();
        return name.contains("SPAWNER");
    }

    private static boolean isStructureSpawn(MobSpawnType spawnType) {
        String name = spawnType.name();
        return name.contains("STRUCTURE") || name.contains("CHUNK");
    }

    private static boolean isNaturalishSpawn(MobSpawnType spawnType) {
        String name = spawnType.name();
        return name.contains("NATURAL") || name.contains("PATROL") || name.contains("CHUNK") || name.contains("STRUCTURE");
    }

    private static boolean isOverworld(Mob mob) {
        return mob != null && mob.level() != null && mob.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD);
    }

    private static String describeReason(EntityType<?> type, MobSpawnType spawnType) {
        if (Config.disableAllNaturalHostileMobSpawns) {
            return "global natural hostile spawn block";
        }

        return "type block: " + (type != null ? type.toShortString() : "unknown") + " spawnType=" + spawnType;
    }
}
