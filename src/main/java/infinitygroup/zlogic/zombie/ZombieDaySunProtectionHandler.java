package infinitygroup.zlogic.zombie;

import infinitygroup.zlogic.Config;
import infinitygroup.zlogic.perf.PerformanceTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class ZombieDaySunProtectionHandler {
    private ZombieDaySunProtectionHandler() {
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!Config.daySpawnedZombiesIgnoreSunBurn) {
            return;
        }

        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || !(entity instanceof Zombie zombie)) {
            return;
        }

        if (!ZombieEligibilityHelper.isEligibleForSunProtection(zombie) || !zombie.isOnFire()) {
            return;
        }

        PerformanceTracker.recordEntityProcessed();
        PerformanceTracker.recordZombieProcessed();

        if (!zombie.level().isDay()) {
            return;
        }

        if (!zombie.level().canSeeSky(zombie.blockPosition()) || zombie.level().isRainingAt(zombie.blockPosition().above()) || zombie.isInLava()) {
            return;
        }

        zombie.clearFire();
    }
}
