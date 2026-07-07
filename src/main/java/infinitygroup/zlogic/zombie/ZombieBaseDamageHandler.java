package infinitygroup.zlogic.zombie;

import com.mojang.logging.LogUtils;
import infinitygroup.zlogic.Config;
import infinitygroup.zlogic.Zlogic;
import infinitygroup.zlogic.perf.PerformanceTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.slf4j.Logger;

public final class ZombieBaseDamageHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation BASE_DAMAGE_MODIFIER_ID = ResourceLocation.parse("zlogic:base_difficulty_damage");

    private ZombieBaseDamageHandler() {
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Zombie zombie) || zombie.level().isClientSide() || !(zombie.level() instanceof ServerLevel level)) {
            return;
        }

        if (!ZombieFamilyHelper.isZombieFamily(zombie)) {
            return;
        }

        PerformanceTracker.recordEntityProcessed();
        PerformanceTracker.recordZombieProcessed();

        if (zombie.tickCount % Math.max(1, Config.zombieBaseDamageCheckIntervalTicks) != 0) {
            return;
        }

        AttributeInstance attackDamage = zombie.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage == null) {
            return;
        }

        String difficultyName = String.valueOf(level.getDifficulty());
        if (!Config.enableZombieBaseDamageSystem || isPeaceful(difficultyName)) {
            if (removeModifier(attackDamage)) {
                debug("Removed zombie base damage modifier from {} at {} (enabled={}, difficulty={})", entity.getType().toShortString(), entity.blockPosition(), Config.enableZombieBaseDamageSystem, difficultyName);
            }
            return;
        }

        boolean daySpawned = ZombieFamilyHelper.isDaySpawned(zombie);
        boolean eligible = ZombieEligibilityHelper.isEligibleForBaseDamage(zombie);
        if (!eligible) {
            if (removeModifier(attackDamage)) {
                debug("Removed zombie base damage modifier from {} at {} (not eligible)", entity.getType().toShortString(), entity.blockPosition());
            }
            return;
        }

        double configuredValue = getConfiguredModifierAmount(difficultyName);
        AttributeModifier.Operation operation = getOperation();
        AttributeModifier expected = new AttributeModifier(BASE_DAMAGE_MODIFIER_ID, configuredValue, operation);
        AttributeModifier current = attackDamage.getModifier(BASE_DAMAGE_MODIFIER_ID);

        if (expected.equals(current)) {
            return;
        }

        if (current != null) {
            attackDamage.removeModifier(BASE_DAMAGE_MODIFIER_ID);
        }

        if (configuredValue <= 0.0D) {
            return;
        }

        attackDamage.addPermanentModifier(expected);

        if (Config.debugLogs || Config.zombieBaseDamageDebugLogs) {
            debug(
                "Applied zombie base damage modifier: zombie={} pos={} difficulty={} daySpawned={} amount={} operation={} multiplierMode={}",
                entity.getType().toShortString(),
                entity.blockPosition(),
                difficultyName,
                daySpawned,
                configuredValue,
                operation,
                Config.zombieBaseDamageUseMultiplier
            );
        }
    }

    public static ZombieBaseDamageInspection inspectZombie(ServerLevel level, LivingEntity living) {
        if (level == null || living == null) {
            return ZombieBaseDamageInspection.empty();
        }

        String difficultyName = String.valueOf(level.getDifficulty());
        boolean targetZombie = living instanceof Zombie zombie && ZombieFamilyHelper.isZombieFamily(zombie);
        boolean daySpawned = living instanceof Zombie zombie && ZombieFamilyHelper.isDaySpawned(zombie);
        boolean eligible = targetZombie && !isPeaceful(difficultyName) && Config.enableZombieBaseDamageSystem && ZombieEligibilityHelper.isEligibleForBaseDamage(living instanceof Zombie zombie ? zombie : null);
        AttributeInstance attackDamage = living.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeModifier current = attackDamage != null ? attackDamage.getModifier(BASE_DAMAGE_MODIFIER_ID) : null;
        double expectedAmount = getConfiguredModifierAmount(difficultyName);
        AttributeModifier.Operation operation = getOperation();
        String reason = determineReason(difficultyName, targetZombie, daySpawned, eligible);

        return new ZombieBaseDamageInspection(
            living.blockPosition(),
            living.getType().toShortString(),
            difficultyName,
            Config.enableZombieBaseDamageSystem,
            targetZombie,
            daySpawned,
            Config.zombieBaseDamageAffectsVanillaZombies,
            Config.zombieBaseDamageAffectsDaySpawnedZombies,
            Config.zombieBaseDamageUseMultiplier,
            attackDamage != null ? attackDamage.getValue() : 0.0D,
            current != null,
            current != null ? current.amount() : 0.0D,
            expectedAmount,
            operation,
            eligible,
            reason
        );
    }

    public static double getConfiguredModifierAmount(String difficultyName) {
        if (difficultyName == null || isPeaceful(difficultyName)) {
            return 0.0D;
        }

        if (Config.zombieBaseDamageUseMultiplier) {
            double multiplier = switch (difficultyName) {
                case "EASY" -> Config.zombieBaseDamageMultiplierEasy;
                case "NORMAL" -> Config.zombieBaseDamageMultiplierNormal;
                case "HARD" -> Config.zombieBaseDamageMultiplierHard;
                default -> 1.0D;
            };
            return Math.max(0.0D, multiplier - 1.0D);
        }

        return switch (difficultyName) {
            case "EASY" -> Config.zombieBaseDamageBonusEasy;
            case "NORMAL" -> Config.zombieBaseDamageBonusNormal;
            case "HARD" -> Config.zombieBaseDamageBonusHard;
            default -> 0.0D;
        };
    }

    public static AttributeModifier.Operation getOperation() {
        return Config.zombieBaseDamageUseMultiplier
            ? AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            : AttributeModifier.Operation.ADD_VALUE;
    }

    public static boolean isEligible(boolean daySpawned) {
        boolean vanillaEligible = Config.zombieBaseDamageAffectsVanillaZombies && !daySpawned;
        boolean daySpawnedEligible = Config.zombieBaseDamageAffectsDaySpawnedZombies && daySpawned;
        return vanillaEligible || daySpawnedEligible;
    }

    private static boolean removeModifier(AttributeInstance attackDamage) {
        if (attackDamage == null) {
            return false;
        }

        AttributeModifier current = attackDamage.getModifier(BASE_DAMAGE_MODIFIER_ID);
        if (current == null) {
            return false;
        }

        attackDamage.removeModifier(BASE_DAMAGE_MODIFIER_ID);
        return true;
    }

    private static String determineReason(String difficultyName, boolean targetZombie, boolean daySpawned, boolean eligible) {
        if (!targetZombie) {
            return "not eligible zombie";
        }

        if (!Config.enableZombieBaseDamageSystem) {
            return "feature disabled";
        }

        if (isPeaceful(difficultyName)) {
            return "peaceful";
        }

        if (!eligible) {
            return daySpawned ? "day spawned not enabled" : "vanilla zombie not enabled";
        }

        return "active";
    }

    private static void debug(String message, Object... args) {
        if (Config.debugLogs || Config.zombieBaseDamageDebugLogs) {
            LOGGER.info("[" + Zlogic.MODID + "] " + message, args);
        }
    }

    private static boolean isPeaceful(String difficultyName) {
        return "PEACEFUL".equals(difficultyName);
    }

    public record ZombieBaseDamageInspection(
        net.minecraft.core.BlockPos pos,
        String entityType,
        String difficulty,
        boolean enabled,
        boolean targetZombie,
        boolean daySpawned,
        boolean affectsVanillaZombies,
        boolean affectsDaySpawnedZombies,
        boolean useMultiplier,
        double attackDamageValue,
        boolean hasModifier,
        double modifierAmount,
        double expectedModifierAmount,
        AttributeModifier.Operation operation,
        boolean eligible,
        String reason
    ) {
        private static ZombieBaseDamageInspection empty() {
            return new ZombieBaseDamageInspection(
                net.minecraft.core.BlockPos.ZERO,
                "unknown",
                "PEACEFUL",
                false,
                false,
                false,
                false,
                false,
                false,
                0.0D,
                false,
                0.0D,
                0.0D,
                AttributeModifier.Operation.ADD_VALUE,
                false,
                "empty"
            );
        }
    }
}
