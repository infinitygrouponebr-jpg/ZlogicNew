package infinitygroup.zlogic.zombie;

import infinitygroup.zlogic.Config;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;

public final class ZombieEligibilityHelper {
    private ZombieEligibilityHelper() {
    }

    public static boolean isEligibleForZlogicSystems(Entity entity) {
        if (!ZombieFamilyHelper.isZombieFamily(entity)) {
            return false;
        }

        if (!Config.zlogicSystemsAffectOnlyZlogicZombies) {
            return true;
        }

        return ZombieMarkingHelper.isZlogicSpawned(entity);
    }

    public static boolean isEligibleForNightBuffs(Entity entity) {
        if (!ZombieFamilyHelper.isZombieFamily(entity)) {
            return false;
        }

        if (Config.zlogicSystemsAffectOnlyZlogicZombies) {
            return ZombieMarkingHelper.isZlogicSpawned(entity);
        }

        if (ZombieMarkingHelper.isZlogicSpawned(entity)) {
            return true;
        }

        return entity instanceof LivingEntity living && ZombieFamilyHelper.isTargetZombie(living, Config.affectOnlyZombies);
    }

    public static boolean isEligibleForAggression(Zombie zombie) {
        if (zombie == null) {
            return false;
        }

        if (Config.zlogicSystemsAffectOnlyZlogicZombies) {
            return ZombieMarkingHelper.isZlogicSpawned(zombie);
        }

        if (ZombieMarkingHelper.isZlogicSpawned(zombie)) {
            return true;
        }

        if (ZombieMarkingHelper.isDaySpawned(zombie)) {
            return Config.aggressionAffectsDaySpawnedZombies;
        }

        return Config.aggressionAffectsVanillaZombies;
    }

    public static boolean isEligibleForBaseDamage(Zombie zombie) {
        if (zombie == null) {
            return false;
        }

        if (Config.zlogicSystemsAffectOnlyZlogicZombies) {
            return ZombieMarkingHelper.isZlogicSpawned(zombie);
        }

        if (ZombieMarkingHelper.isZlogicSpawned(zombie)) {
            return true;
        }

        boolean vanillaEligible = Config.zombieBaseDamageAffectsVanillaZombies && !ZombieMarkingHelper.isDaySpawned(zombie);
        boolean daySpawnedEligible = Config.zombieBaseDamageAffectsDaySpawnedZombies && ZombieMarkingHelper.isDaySpawned(zombie);
        return vanillaEligible || daySpawnedEligible;
    }

    public static boolean isEligibleForThreatScaling(Entity entity) {
        if (!ZombieFamilyHelper.isZombieFamily(entity)) {
            return false;
        }

        if (Config.zlogicSystemsAffectOnlyZlogicZombies) {
            return ZombieMarkingHelper.isZlogicSpawned(entity);
        }

        if (ZombieMarkingHelper.isZlogicSpawned(entity)) {
            return true;
        }

        return true;
    }

    public static boolean isEligibleForMachineAttack(Entity entity) {
        if (!ZombieFamilyHelper.isZombieFamily(entity)) {
            return false;
        }

        if (Config.zlogicSystemsAffectOnlyZlogicZombies) {
            return ZombieMarkingHelper.isZlogicSpawned(entity);
        }

        if (ZombieMarkingHelper.isZlogicSpawned(entity)) {
            return true;
        }

        return true;
    }

    public static boolean isEligibleForBarrierBreak(Zombie zombie) {
        if (zombie == null || !ZombieFamilyHelper.isZombieFamily(zombie)) {
            return false;
        }

        if (Config.barrierBreakOnlyEligibleZombies || Config.zlogicSystemsAffectOnlyZlogicZombies) {
            return ZombieMarkingHelper.isZlogicSpawned(zombie);
        }

        return true;
    }

    public static boolean isEligibleForSunProtection(Zombie zombie) {
        if (zombie == null) {
            return false;
        }

        if (Config.zlogicSystemsAffectOnlyZlogicZombies) {
            return ZombieMarkingHelper.isZlogicSpawned(zombie);
        }

        if (ZombieMarkingHelper.isZlogicSpawned(zombie)) {
            return ZombieMarkingHelper.isDaySpawned(zombie) && Config.daySpawnedZombiesIgnoreSunBurn
                || ZombieMarkingHelper.isNightSpawned(zombie) && Config.nightSpawnedZombiesIgnoreSunBurn;
        }

        return ZombieMarkingHelper.isDaySpawned(zombie) && Config.daySpawnedZombiesIgnoreSunBurn;
    }
}
