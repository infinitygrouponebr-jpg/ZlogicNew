package infinitygroup.zlogic.zombie;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;

public final class DayZombieSpawnHandler {
    public static final String DAY_SPAWNED_KEY = "zlogic_day_spawned";

    private DayZombieSpawnHandler() {
    }

    public static void markDaySpawned(Zombie zombie) {
        ZombieMarkingHelper.markDaySpawned(zombie);
    }

    public static void markZlogicSpawned(Mob mob) {
        ZombieMarkingHelper.markZlogicSpawned(mob);
    }

    public static boolean isDaySpawned(Zombie zombie) {
        return ZombieMarkingHelper.isDaySpawned(zombie);
    }

    public static boolean isZlogicSpawned(Zombie zombie) {
        return ZombieMarkingHelper.isZlogicSpawned(zombie);
    }
}
