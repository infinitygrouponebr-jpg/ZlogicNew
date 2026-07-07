package infinitygroup.zlogic.zombie;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;

public final class ZombieFamilyHelper {
    private ZombieFamilyHelper() {
    }

    public static boolean isTargetZombie(LivingEntity entity) {
        return isTargetZombie(entity, true);
    }

    public static boolean isTargetZombie(LivingEntity entity, boolean affectOnlyZombies) {
        if (entity == null) {
            return false;
        }

        if (!affectOnlyZombies) {
            return entity instanceof Zombie;
        }

        if (!isZombieFamily(entity)) {
            return false;
        }

        EntityType<?> type = entity.getType();
        return type == EntityType.ZOMBIE
            || type == EntityType.ZOMBIE_VILLAGER
            || type == EntityType.HUSK
            || type == EntityType.DROWNED
            || entity instanceof ZombieVillager
            || entity instanceof Husk
            || entity instanceof Drowned
            || entity instanceof Zombie;
    }

    public static boolean isZombieFamily(Entity entity) {
        return entity instanceof Zombie
            || entity instanceof ZombieVillager
            || entity instanceof Husk
            || entity instanceof Drowned;
    }

    public static boolean isDaySpawned(Zombie zombie) {
        return zombie != null && DayZombieSpawnHandler.isDaySpawned(zombie);
    }
}
