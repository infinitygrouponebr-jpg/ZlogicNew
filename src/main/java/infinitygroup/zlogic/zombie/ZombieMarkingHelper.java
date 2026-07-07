package infinitygroup.zlogic.zombie;

import infinitygroup.zlogic.Config;
import infinitygroup.zlogic.Zlogic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public final class ZombieMarkingHelper {
    public static final String ZLOGIC_SPAWNED_KEY = "zlogic_spawned";
    public static final String ZLOGIC_NIGHT_SPAWNED_KEY = "zlogic_night_spawned";
    private static final Logger LOGGER = LogUtils.getLogger();

    private ZombieMarkingHelper() {
    }

    public static void markZlogicSpawned(Mob mob) {
        if (mob == null) {
            return;
        }

        CompoundTag tag = mob.getPersistentData();
        if (tag.getBoolean(ZLOGIC_SPAWNED_KEY)) {
            return;
        }

        tag.putBoolean(ZLOGIC_SPAWNED_KEY, true);
        if (Config.zlogicDebugZombieMarkers || Config.debugLogs) {
            LOGGER.info("[{}] marked {} as zlogic spawned at {}", Zlogic.MODID, mob.getType().toShortString(), mob.blockPosition());
        }
    }

    public static void markDaySpawned(Mob mob) {
        if (mob == null) {
            return;
        }

        markZlogicSpawned(mob);

        CompoundTag tag = mob.getPersistentData();
        if (tag.getBoolean(DayZombieSpawnHandler.DAY_SPAWNED_KEY)) {
            return;
        }

        tag.putBoolean(DayZombieSpawnHandler.DAY_SPAWNED_KEY, true);
        if (Config.zlogicDebugZombieMarkers || Config.debugLogs) {
            LOGGER.info("[{}] marked {} as zlogic day spawned at {}", Zlogic.MODID, mob.getType().toShortString(), mob.blockPosition());
        }
    }

    public static void markNightSpawned(Mob mob) {
        if (mob == null) {
            return;
        }

        markZlogicSpawned(mob);

        CompoundTag tag = mob.getPersistentData();
        if (tag.getBoolean(ZLOGIC_NIGHT_SPAWNED_KEY)) {
            return;
        }

        tag.putBoolean(ZLOGIC_NIGHT_SPAWNED_KEY, true);
        if (Config.zlogicDebugZombieMarkers || Config.debugLogs) {
            LOGGER.info("[{}] marked {} as zlogic night spawned at {}", Zlogic.MODID, mob.getType().toShortString(), mob.blockPosition());
        }
    }

    public static boolean isZlogicSpawned(Entity entity) {
        if (!(entity instanceof Mob mob)) {
            return false;
        }

        CompoundTag tag = mob.getPersistentData();
        if (tag.getBoolean(ZLOGIC_SPAWNED_KEY)) {
            return true;
        }

        if (tag.getBoolean(ZLOGIC_NIGHT_SPAWNED_KEY)) {
            return true;
        }

        return Config.zlogicTreatLegacyDaySpawnedAsZlogic && tag.getBoolean(DayZombieSpawnHandler.DAY_SPAWNED_KEY);
    }

    public static boolean isDaySpawned(Entity entity) {
        return entity instanceof Mob mob && mob.getPersistentData().getBoolean(DayZombieSpawnHandler.DAY_SPAWNED_KEY);
    }

    public static boolean isNightSpawned(Entity entity) {
        return entity instanceof Mob mob && mob.getPersistentData().getBoolean(ZLOGIC_NIGHT_SPAWNED_KEY);
    }
}
