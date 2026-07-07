package infinitygroup.zlogic.zombie;

import com.mojang.logging.LogUtils;
import infinitygroup.zlogic.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import org.slf4j.Logger;

public final class ZombieThreatLevelManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation DAMAGE_MODIFIER_ID = ResourceLocation.parse("zlogic:threat_scaling_damage");
    public static final ResourceLocation SPEED_MODIFIER_ID = ResourceLocation.parse("zlogic:threat_scaling_speed");
    public static final ResourceLocation FOLLOW_RANGE_MODIFIER_ID = ResourceLocation.parse("zlogic:threat_scaling_follow_range");
    public static final ResourceLocation ARMOR_MODIFIER_ID = ResourceLocation.parse("zlogic:threat_scaling_armor");
    public static final ResourceLocation HEALTH_MODIFIER_ID = ResourceLocation.parse("zlogic:threat_scaling_health");

    private static final ResourceLocation LEGACY_DAMAGE_MODIFIER_ID = ResourceLocation.parse("zlogic:survival_scaling_damage");
    private static final ResourceLocation LEGACY_SPEED_MODIFIER_ID = ResourceLocation.parse("zlogic:survival_scaling_speed");
    private static final ResourceLocation LEGACY_FOLLOW_RANGE_MODIFIER_ID = ResourceLocation.parse("zlogic:survival_scaling_follow_range");
    private static final ResourceLocation LEGACY_ARMOR_MODIFIER_ID = ResourceLocation.parse("zlogic:survival_scaling_armor");
    private static final ResourceLocation LEGACY_HEALTH_MODIFIER_ID = ResourceLocation.parse("zlogic:survival_scaling_health");

    private static final String THREAT_LEVEL_KEY = "zlogic_threat_level";
    private static final String THREAT_WORLD_DAY_KEY = "zlogic_threat_world_day";
    private static final String THREAT_WORLD_DAY_LEVEL_KEY = "zlogic_threat_world_day_level";
    private static final String THREAT_DIFFICULTY_BONUS_KEY = "zlogic_threat_difficulty_bonus";
    private static final String THREAT_DISTANCE_BONUS_KEY = "zlogic_threat_distance_bonus";
    private static final String THREAT_RANDOM_VARIANCE_KEY = "zlogic_threat_random_variance";
    private static final String THREAT_NEAREST_PLAYER_BONUS_KEY = "zlogic_threat_nearest_player_bonus";
    private static final String THREAT_FINAL_LEVEL_KEY = "zlogic_threat_final_level";
    private static final String THREAT_SOURCE_KEY = "zlogic_threat_source";
    private static final String THREAT_APPLIED_KEY = "zlogic_threat_applied";
    private static final String THREAT_LAST_APPLY_TICK_KEY = "zlogic_threat_last_apply_tick";
    private static final String THREAT_HEALTH_ADJUSTED_ONCE_KEY = "zlogic_threat_health_adjusted_once";

    private static final String LEGACY_LEVEL_KEY = "zlogic_scaling_level";
    private static final String LEGACY_WORLD_DAY_KEY = "zlogic_scaling_world_day";
    private static final String LEGACY_APPLIED_KEY = "zlogic_scaling_applied";
    private static final String LEGACY_SOURCE_KEY = "zlogic_scaling_source";
    private static final String LEGACY_LAST_APPLY_TICK_KEY = "zlogic_scaling_last_apply_tick";
    private static final String LEGACY_HEALTH_ADJUSTED_ONCE_KEY = "zlogic_scaling_health_adjusted_once";

    private ZombieThreatLevelManager() {
    }

    public static ThreatComputation compute(ServerLevel level, Zombie zombie) {
        return compute(level, zombie, false, false);
    }

    public static ThreatComputation compute(ServerLevel level, Zombie zombie, boolean refreshRandomVariance, boolean allowNearestPlayerSearch) {
        if (level == null || zombie == null || !ZombieFamilyHelper.isZombieFamily(zombie)) {
            return ThreatComputation.empty();
        }

        ZombieSurvivalScalingHelper.ThreatScalingMode mode = ZombieSurvivalScalingHelper.ThreatScalingMode.parse(Config.threatScalingMode);
        boolean enabled = Config.enableThreatLevelScaling && mode != ZombieSurvivalScalingHelper.ThreatScalingMode.DISABLED;
        int worldDay = ZombieSurvivalScalingHelper.getWorldDay(level);
        int worldDayLevel = enabled && Config.threatWorldDayEnabled
            ? ZombieSurvivalScalingHelper.calculateWorldDayLevel(worldDay)
            : 0;
        int difficultyBonus = enabled && mode == ZombieSurvivalScalingHelper.ThreatScalingMode.MULTI_FACTOR
            ? ZombieSurvivalScalingHelper.calculateDifficultyBonus(level)
            : 0;
        int distanceBonus = enabled && mode == ZombieSurvivalScalingHelper.ThreatScalingMode.MULTI_FACTOR
            ? ZombieSurvivalScalingHelper.calculateDistanceBonus(level, zombie)
            : 0;
        int storedRandomVariance = getStoredRandomVariance(zombie);
        int randomVariance = enabled && mode == ZombieSurvivalScalingHelper.ThreatScalingMode.MULTI_FACTOR
            ? (refreshRandomVariance ? ZombieSurvivalScalingHelper.calculateRandomVariance(zombie) : storedRandomVariance)
            : 0;
        int storedNearestPlayerBonus = getStoredNearestPlayerBonus(zombie);
        int nearestPlayerBonus = enabled && mode == ZombieSurvivalScalingHelper.ThreatScalingMode.MULTI_FACTOR
            ? (allowNearestPlayerSearch && Config.threatNearestPlayerEnabled
                ? ZombieSurvivalScalingHelper.calculateNearestPlayerBonus(level, zombie)
                : storedNearestPlayerBonus)
            : 0;

        if (mode == ZombieSurvivalScalingHelper.ThreatScalingMode.LEGACY_DAY) {
            difficultyBonus = 0;
            distanceBonus = 0;
            randomVariance = 0;
            nearestPlayerBonus = 0;
        }

        int finalLevel = enabled
            ? ZombieSurvivalScalingHelper.calculateThreatFinalLevel(worldDayLevel, difficultyBonus, distanceBonus, randomVariance, nearestPlayerBonus)
            : 0;
        String source = enabled
            ? buildSource(mode, worldDayLevel, difficultyBonus, distanceBonus, randomVariance, nearestPlayerBonus)
            : "DISABLED";

        return build(
            level,
            zombie,
            mode,
            source,
            enabled,
            worldDay,
            worldDayLevel,
            difficultyBonus,
            distanceBonus,
            randomVariance,
            nearestPlayerBonus,
            finalLevel,
            refreshRandomVariance,
            allowNearestPlayerSearch
        );
    }

    public static ApplyResult apply(ServerLevel level, Zombie zombie, boolean fromSpawnOrJoin) {
        return apply(level, zombie, fromSpawnOrJoin, fromSpawnOrJoin);
    }

    public static ApplyResult apply(ServerLevel level, Zombie zombie, boolean fromSpawnOrJoin, boolean refreshRandomVariance) {
        if (level == null || zombie == null || !ZombieFamilyHelper.isZombieFamily(zombie)) {
            return ApplyResult.empty();
        }

        ThreatComputation computation = compute(level, zombie, refreshRandomVariance, fromSpawnOrJoin);
        if (!computation.active()) {
            clearStoredState(zombie);
            removeAllModifiers(zombie);
            return new ApplyResult(computation, 0, false, false);
        }

        if (Config.threatScalingStoreOnMob) {
            storeComputation(zombie, computation);
        }

        int changes = 0;
        changes += syncScalingModifier(zombie.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_MODIFIER_ID, LEGACY_DAMAGE_MODIFIER_ID, computation.damageMultiplier());
        changes += syncScalingModifier(zombie.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER_ID, LEGACY_SPEED_MODIFIER_ID, computation.speedMultiplier());
        changes += syncScalingModifier(zombie.getAttribute(Attributes.FOLLOW_RANGE), FOLLOW_RANGE_MODIFIER_ID, LEGACY_FOLLOW_RANGE_MODIFIER_ID, computation.followRangeMultiplier());
        changes += syncScalingModifier(zombie.getAttribute(Attributes.ARMOR), ARMOR_MODIFIER_ID, LEGACY_ARMOR_MODIFIER_ID, computation.armorMultiplier());

        boolean healthAdjusted = false;
        AttributeInstance health = zombie.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            double beforeMax = health.getValue();
            int healthChange = syncScalingModifier(health, HEALTH_MODIFIER_ID, LEGACY_HEALTH_MODIFIER_ID, computation.healthMultiplier());
            changes += healthChange;
            if (fromSpawnOrJoin && Config.threatScalingApplyOnSpawn && healthChange > 0 && !hasHealthAdjustedOnce(zombie)) {
                double afterMax = health.getValue();
                adjustHealthProportionally(zombie, beforeMax, afterMax);
                setHealthAdjustedOnce(zombie, true);
                healthAdjusted = true;
            } else if (zombie.getHealth() > health.getValue()) {
                zombie.setHealth((float) health.getValue());
            }
        }

        setApplied(zombie, true);
        setLastApplyTick(zombie, zombie.level().getGameTime());
        if (Config.threatScalingStoreOnMob) {
            storeComputation(zombie, computation);
        }

        if (Config.threatScalingDebugLogs) {
            LOGGER.info(
                "[zlogic] threat scaling applied zombie={} pos={} level={} worldDay={} components=[day={},difficulty={},distance={},variance={},nearest={}] source={} changes={} active={}",
                zombie.getType().toShortString(),
                zombie.blockPosition(),
                computation.finalLevel(),
                computation.worldDay(),
                computation.worldDayLevel(),
                computation.difficultyBonus(),
                computation.distanceBonus(),
                computation.randomVariance(),
                computation.nearestPlayerBonus(),
                computation.source(),
                changes,
                computation.active()
            );
        }

        return new ApplyResult(computation, changes, healthAdjusted, true);
    }

    public static boolean shouldReapply(ServerLevel level, Zombie zombie) {
        if (level == null || zombie == null) {
            return false;
        }

        ThreatComputation computation = compute(level, zombie, false, false);
        if (!computation.active()) {
            return hasAnyThreatModifier(zombie);
        }

        if (!isApplied(zombie)) {
            return true;
        }

        if (getStoredFinalLevel(zombie) != computation.finalLevel()) {
            return true;
        }

        if (getStoredWorldDay(zombie) != computation.worldDay()) {
            return true;
        }

        if (!Config.survivalScalingReapplyMissingModifiers) {
            return false;
        }

        return !modifiersMatch(zombie, computation);
    }

    public static boolean modifiersMatch(Zombie zombie, ThreatComputation computation) {
        if (zombie == null || computation == null || !computation.active()) {
            return false;
        }

        return modifierMatches(zombie.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_MODIFIER_ID, LEGACY_DAMAGE_MODIFIER_ID, computation.damageMultiplier())
            && modifierMatches(zombie.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER_ID, LEGACY_SPEED_MODIFIER_ID, computation.speedMultiplier())
            && modifierMatches(zombie.getAttribute(Attributes.FOLLOW_RANGE), FOLLOW_RANGE_MODIFIER_ID, LEGACY_FOLLOW_RANGE_MODIFIER_ID, computation.followRangeMultiplier())
            && modifierMatches(zombie.getAttribute(Attributes.ARMOR), ARMOR_MODIFIER_ID, LEGACY_ARMOR_MODIFIER_ID, computation.armorMultiplier())
            && modifierMatches(zombie.getAttribute(Attributes.MAX_HEALTH), HEALTH_MODIFIER_ID, LEGACY_HEALTH_MODIFIER_ID, computation.healthMultiplier());
    }

    public static void clearStoredState(Zombie zombie) {
        if (zombie == null) {
            return;
        }

        var tag = zombie.getPersistentData();
        tag.remove(THREAT_LEVEL_KEY);
        tag.remove(THREAT_WORLD_DAY_KEY);
        tag.remove(THREAT_WORLD_DAY_LEVEL_KEY);
        tag.remove(THREAT_DIFFICULTY_BONUS_KEY);
        tag.remove(THREAT_DISTANCE_BONUS_KEY);
        tag.remove(THREAT_RANDOM_VARIANCE_KEY);
        tag.remove(THREAT_NEAREST_PLAYER_BONUS_KEY);
        tag.remove(THREAT_FINAL_LEVEL_KEY);
        tag.remove(THREAT_APPLIED_KEY);
        tag.remove(THREAT_SOURCE_KEY);
        tag.remove(THREAT_LAST_APPLY_TICK_KEY);
        tag.remove(THREAT_HEALTH_ADJUSTED_ONCE_KEY);
    }

    public static void clearAllScalingModifiers(Zombie zombie) {
        if (zombie == null) {
            return;
        }

        removeAllModifiers(zombie);
        clearStoredState(zombie);
    }

    public static void clearThreatStateAndModifiers(Zombie zombie) {
        if (zombie == null) {
            return;
        }

        removeThreatModifiers(zombie);
        clearStoredState(zombie);
    }

    public static int getStoredThreatLevel(Zombie zombie) {
        return readIntCompat(zombie, THREAT_LEVEL_KEY, LEGACY_LEVEL_KEY);
    }

    public static int getStoredWorldDay(Zombie zombie) {
        return readIntCompat(zombie, THREAT_WORLD_DAY_KEY, LEGACY_WORLD_DAY_KEY);
    }

    public static int getStoredWorldDayLevel(Zombie zombie) {
        return readIntCompat(zombie, THREAT_WORLD_DAY_LEVEL_KEY, null);
    }

    public static int getStoredDifficultyBonus(Zombie zombie) {
        return readIntCompat(zombie, THREAT_DIFFICULTY_BONUS_KEY, null);
    }

    public static int getStoredDistanceBonus(Zombie zombie) {
        return readIntCompat(zombie, THREAT_DISTANCE_BONUS_KEY, null);
    }

    public static int getStoredRandomVariance(Zombie zombie) {
        return readIntCompat(zombie, THREAT_RANDOM_VARIANCE_KEY, null);
    }

    public static int getStoredNearestPlayerBonus(Zombie zombie) {
        return readIntCompat(zombie, THREAT_NEAREST_PLAYER_BONUS_KEY, null);
    }

    public static int getStoredFinalLevel(Zombie zombie) {
        return readIntCompat(zombie, THREAT_FINAL_LEVEL_KEY, LEGACY_LEVEL_KEY);
    }

    public static boolean isApplied(Zombie zombie) {
        if (zombie == null) {
            return false;
        }

        return zombie.getPersistentData().getBoolean(THREAT_APPLIED_KEY) || zombie.getPersistentData().getBoolean(LEGACY_APPLIED_KEY);
    }

    public static String getStoredSource(Zombie zombie) {
        if (zombie == null) {
            return "none";
        }

        var tag = zombie.getPersistentData();
        if (tag.contains(THREAT_SOURCE_KEY)) {
            return tag.getString(THREAT_SOURCE_KEY);
        }

        if (tag.contains(LEGACY_SOURCE_KEY)) {
            return tag.getString(LEGACY_SOURCE_KEY);
        }

        return "none";
    }

    public static long getLastApplyTick(Zombie zombie) {
        if (zombie == null) {
            return 0L;
        }

        var tag = zombie.getPersistentData();
        if (tag.contains(THREAT_LAST_APPLY_TICK_KEY)) {
            return tag.getLong(THREAT_LAST_APPLY_TICK_KEY);
        }

        if (tag.contains(LEGACY_LAST_APPLY_TICK_KEY)) {
            return tag.getLong(LEGACY_LAST_APPLY_TICK_KEY);
        }

        return 0L;
    }

    public static boolean hasHealthAdjustedOnce(Zombie zombie) {
        if (zombie == null) {
            return false;
        }

        var tag = zombie.getPersistentData();
        return tag.getBoolean(THREAT_HEALTH_ADJUSTED_ONCE_KEY) || tag.getBoolean(LEGACY_HEALTH_ADJUSTED_ONCE_KEY);
    }

    public static ThreatInspection inspectZombie(ServerLevel level, Zombie zombie) {
        if (level == null || zombie == null) {
            return ThreatInspection.empty();
        }

        ThreatComputation computation = compute(level, zombie, false, false);
        AttributeInstance attackDamage = zombie.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance movementSpeed = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance followRange = zombie.getAttribute(Attributes.FOLLOW_RANGE);
        AttributeInstance armor = zombie.getAttribute(Attributes.ARMOR);
        AttributeInstance maxHealth = zombie.getAttribute(Attributes.MAX_HEALTH);

        return new ThreatInspection(
            zombie.blockPosition(),
            zombie.getType().toShortString(),
            Config.enableThreatLevelScaling,
            computation.mode(),
            computation.worldDay(),
            computation.worldDayLevel(),
            computation.difficultyBonus(),
            computation.distanceBonus(),
            computation.randomVariance(),
            computation.nearestPlayerBonus(),
            computation.finalLevel(),
            ZombieSurvivalScalingHelper.getEffectiveLevelCap(),
            computation.source(),
            computation.active(),
            computation.applyOnSpawn(),
            computation.recheckIntervalTicks(),
            computation.storeOnMob(),
            computation.reapplyMissingModifiers(),
            computation.disableHeavyEntityTickLogic(),
            getStoredThreatLevel(zombie),
            getStoredWorldDay(zombie),
            getStoredWorldDayLevel(zombie),
            getStoredDifficultyBonus(zombie),
            getStoredDistanceBonus(zombie),
            getStoredRandomVariance(zombie),
            getStoredNearestPlayerBonus(zombie),
            getStoredFinalLevel(zombie),
            isApplied(zombie),
            getStoredSource(zombie),
            getLastApplyTick(zombie),
            hasHealthAdjustedOnce(zombie),
            computation.damageMultiplier(),
            computation.speedMultiplier(),
            computation.followRangeMultiplier(),
            computation.armorMultiplier(),
            computation.healthMultiplier(),
            attackDamage != null ? attackDamage.getValue() : 0.0D,
            movementSpeed != null ? movementSpeed.getValue() : 0.0D,
            followRange != null ? followRange.getValue() : 0.0D,
            armor != null ? armor.getValue() : 0.0D,
            maxHealth != null ? maxHealth.getValue() : 0.0D,
            attackDamage != null && attackDamage.getModifier(DAMAGE_MODIFIER_ID) != null,
            movementSpeed != null && movementSpeed.getModifier(SPEED_MODIFIER_ID) != null,
            followRange != null && followRange.getModifier(FOLLOW_RANGE_MODIFIER_ID) != null,
            armor != null && armor.getModifier(ARMOR_MODIFIER_ID) != null,
            maxHealth != null && maxHealth.getModifier(HEALTH_MODIFIER_ID) != null,
            attackDamage != null && attackDamage.getModifier(LEGACY_DAMAGE_MODIFIER_ID) != null,
            movementSpeed != null && movementSpeed.getModifier(LEGACY_SPEED_MODIFIER_ID) != null,
            followRange != null && followRange.getModifier(LEGACY_FOLLOW_RANGE_MODIFIER_ID) != null,
            armor != null && armor.getModifier(LEGACY_ARMOR_MODIFIER_ID) != null,
            maxHealth != null && maxHealth.getModifier(LEGACY_HEALTH_MODIFIER_ID) != null,
            attackDamage != null && attackDamage.getModifier(DAMAGE_MODIFIER_ID) != null ? attackDamage.getModifier(DAMAGE_MODIFIER_ID).amount() : 0.0D,
            movementSpeed != null && movementSpeed.getModifier(SPEED_MODIFIER_ID) != null ? movementSpeed.getModifier(SPEED_MODIFIER_ID).amount() : 0.0D,
            followRange != null && followRange.getModifier(FOLLOW_RANGE_MODIFIER_ID) != null ? followRange.getModifier(FOLLOW_RANGE_MODIFIER_ID).amount() : 0.0D,
            armor != null && armor.getModifier(ARMOR_MODIFIER_ID) != null ? armor.getModifier(ARMOR_MODIFIER_ID).amount() : 0.0D,
            maxHealth != null && maxHealth.getModifier(HEALTH_MODIFIER_ID) != null ? maxHealth.getModifier(HEALTH_MODIFIER_ID).amount() : 0.0D,
            computation
        );
    }

    private static ThreatComputation build(
        ServerLevel level,
        Zombie zombie,
        ZombieSurvivalScalingHelper.ThreatScalingMode mode,
        String source,
        boolean active,
        int worldDay,
        int worldDayLevel,
        int difficultyBonus,
        int distanceBonus,
        int randomVariance,
        int nearestPlayerBonus,
        int finalLevel,
        boolean refreshRandomVariance,
        boolean allowNearestPlayerSearch
    ) {
        double damageMultiplier = ZombieSurvivalScalingHelper.calculateMultiplierFromLevel(finalLevel, Config.threatDamagePerLevel, Config.threatMaxDamageMultiplier);
        double speedMultiplier = ZombieSurvivalScalingHelper.calculateMultiplierFromLevel(finalLevel, Config.threatSpeedPerLevel, Config.threatMaxSpeedMultiplier);
        double followRangeMultiplier = ZombieSurvivalScalingHelper.calculateMultiplierFromLevel(finalLevel, Config.threatFollowRangePerLevel, Config.threatMaxFollowRangeMultiplier);
        double armorMultiplier = ZombieSurvivalScalingHelper.calculateMultiplierFromLevel(finalLevel, Config.threatArmorPerLevel, Config.threatMaxArmorMultiplier);
        double healthMultiplier = ZombieSurvivalScalingHelper.calculateMultiplierFromLevel(finalLevel, Config.threatHealthPerLevel, Config.threatMaxHealthMultiplier);

        return new ThreatComputation(
            mode,
            source,
            worldDay,
            worldDayLevel,
            difficultyBonus,
            distanceBonus,
            randomVariance,
            nearestPlayerBonus,
            finalLevel,
            ZombieSurvivalScalingHelper.getEffectiveLevelCap(),
            Config.threatScalingMinimumLevel,
            active,
            Config.threatScalingApplyOnSpawn,
            Config.threatScalingRecheckIntervalTicks,
            Config.threatScalingStoreOnMob,
            Config.survivalScalingReapplyMissingModifiers,
            Config.survivalScalingDisableEntityTickHeavyLogic,
            damageMultiplier,
            speedMultiplier,
            followRangeMultiplier,
            armorMultiplier,
            healthMultiplier,
            getStoredThreatLevel(zombie),
            getStoredWorldDay(zombie),
            getStoredWorldDayLevel(zombie),
            getStoredDifficultyBonus(zombie),
            getStoredDistanceBonus(zombie),
            getStoredRandomVariance(zombie),
            getStoredNearestPlayerBonus(zombie),
            getStoredFinalLevel(zombie),
            isApplied(zombie),
            getStoredSource(zombie),
            getLastApplyTick(zombie),
            hasHealthAdjustedOnce(zombie)
        );
    }

    private static String buildSource(
        ZombieSurvivalScalingHelper.ThreatScalingMode mode,
        int worldDayLevel,
        int difficultyBonus,
        int distanceBonus,
        int randomVariance,
        int nearestPlayerBonus
    ) {
        if (mode == ZombieSurvivalScalingHelper.ThreatScalingMode.LEGACY_DAY) {
            return "WORLD_DAY";
        }

        StringBuilder builder = new StringBuilder();
        if (worldDayLevel > 0 || Config.threatWorldDayEnabled) {
            builder.append("WORLD_DAY");
        }
        if (difficultyBonus > 0 || Config.threatDifficultyEnabled) {
            appendSource(builder, "DIFFICULTY");
        }
        if (distanceBonus > 0 || Config.threatDistanceEnabled) {
            appendSource(builder, "DISTANCE");
        }
        if (randomVariance > 0 || Config.threatRandomVarianceEnabled) {
            appendSource(builder, "RANDOM");
        }
        if (nearestPlayerBonus > 0 || Config.threatNearestPlayerEnabled) {
            appendSource(builder, Config.threatNearestPlayerEnabled ? "NEAREST_PLAYER_PREPARED" : "NEAREST_PLAYER");
        }
        return builder.length() == 0 ? "DISABLED" : builder.toString();
    }

    private static void appendSource(StringBuilder builder, String part) {
        if (builder.length() > 0) {
            builder.append('+');
        }
        builder.append(part);
    }

    private static int syncScalingModifier(AttributeInstance instance, ResourceLocation newId, ResourceLocation legacyId, double multiplier) {
        if (instance == null) {
            return 0;
        }

        int changes = 0;
        AttributeModifier currentNew = instance.getModifier(newId);
        AttributeModifier currentLegacy = instance.getModifier(legacyId);

        if (multiplier <= 1.0D) {
            if (currentNew != null) {
                instance.removeModifier(newId);
                changes++;
            }

            if (currentLegacy != null) {
                instance.removeModifier(legacyId);
                changes++;
            }

            return changes;
        }

        double amount = multiplier - 1.0D;
        AttributeModifier expected = new AttributeModifier(newId, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (!expected.equals(currentNew)) {
            if (currentNew != null) {
                instance.removeModifier(newId);
                changes++;
            }

            instance.addPermanentModifier(expected);
            changes++;
        }

        if (currentLegacy != null) {
            instance.removeModifier(legacyId);
            changes++;
        }

        return changes;
    }

    private static boolean modifierMatches(AttributeInstance instance, ResourceLocation newId, ResourceLocation legacyId, double multiplier) {
        if (instance == null) {
            return false;
        }

        AttributeModifier currentNew = instance.getModifier(newId);
        AttributeModifier currentLegacy = instance.getModifier(legacyId);
        if (multiplier <= 1.0D) {
            return currentNew == null && currentLegacy == null;
        }

        double amount = multiplier - 1.0D;
        AttributeModifier expected = new AttributeModifier(newId, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        return expected.equals(currentNew) && currentLegacy == null;
    }

    private static void removeAllModifiers(Zombie zombie) {
        if (zombie == null) {
            return;
        }

        removeModifier(zombie.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_MODIFIER_ID, LEGACY_DAMAGE_MODIFIER_ID);
        removeModifier(zombie.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER_ID, LEGACY_SPEED_MODIFIER_ID);
        removeModifier(zombie.getAttribute(Attributes.FOLLOW_RANGE), FOLLOW_RANGE_MODIFIER_ID, LEGACY_FOLLOW_RANGE_MODIFIER_ID);
        removeModifier(zombie.getAttribute(Attributes.ARMOR), ARMOR_MODIFIER_ID, LEGACY_ARMOR_MODIFIER_ID);
        removeModifier(zombie.getAttribute(Attributes.MAX_HEALTH), HEALTH_MODIFIER_ID, LEGACY_HEALTH_MODIFIER_ID);
        zombie.setHealth(Math.min(zombie.getHealth(), zombie.getMaxHealth()));
    }

    private static void removeThreatModifiers(Zombie zombie) {
        if (zombie == null) {
            return;
        }

        removeModifier(zombie.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_MODIFIER_ID, null);
        removeModifier(zombie.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER_ID, null);
        removeModifier(zombie.getAttribute(Attributes.FOLLOW_RANGE), FOLLOW_RANGE_MODIFIER_ID, null);
        removeModifier(zombie.getAttribute(Attributes.ARMOR), ARMOR_MODIFIER_ID, null);
        removeModifier(zombie.getAttribute(Attributes.MAX_HEALTH), HEALTH_MODIFIER_ID, null);
    }

    private static void removeModifier(AttributeInstance instance, ResourceLocation newId, ResourceLocation legacyId) {
        if (instance == null) {
            return;
        }

        if (instance.getModifier(newId) != null) {
            instance.removeModifier(newId);
        }

        if (legacyId != null && instance.getModifier(legacyId) != null) {
            instance.removeModifier(legacyId);
        }
    }

    private static void adjustHealthProportionally(Zombie zombie, double beforeMax, double afterMax) {
        if (zombie == null || beforeMax <= 0.0D || afterMax <= 0.0D) {
            return;
        }

        double current = zombie.getHealth();
        double ratio = afterMax / beforeMax;
        zombie.setHealth((float) Math.min(afterMax, current * ratio));
    }

    private static void storeComputation(Zombie zombie, ThreatComputation computation) {
        if (zombie == null || computation == null) {
            return;
        }

        var tag = zombie.getPersistentData();
        tag.putInt(THREAT_LEVEL_KEY, computation.finalLevel());
        tag.putInt(THREAT_WORLD_DAY_KEY, computation.worldDay());
        tag.putInt(THREAT_WORLD_DAY_LEVEL_KEY, computation.worldDayLevel());
        tag.putInt(THREAT_DIFFICULTY_BONUS_KEY, computation.difficultyBonus());
        tag.putInt(THREAT_DISTANCE_BONUS_KEY, computation.distanceBonus());
        tag.putInt(THREAT_RANDOM_VARIANCE_KEY, computation.randomVariance());
        tag.putInt(THREAT_NEAREST_PLAYER_BONUS_KEY, computation.nearestPlayerBonus());
        tag.putInt(THREAT_FINAL_LEVEL_KEY, computation.finalLevel());
        tag.putBoolean(THREAT_APPLIED_KEY, computation.active());
        tag.putString(THREAT_SOURCE_KEY, computation.source());
        tag.putLong(THREAT_LAST_APPLY_TICK_KEY, zombie.level().getGameTime());

        tag.putInt(LEGACY_LEVEL_KEY, computation.finalLevel());
        tag.putInt(LEGACY_WORLD_DAY_KEY, computation.worldDay());
        tag.putBoolean(LEGACY_APPLIED_KEY, computation.active());
        tag.putString(LEGACY_SOURCE_KEY, computation.source());
        tag.putLong(LEGACY_LAST_APPLY_TICK_KEY, zombie.level().getGameTime());
    }

    private static void setApplied(Zombie zombie, boolean applied) {
        if (zombie != null) {
            zombie.getPersistentData().putBoolean(THREAT_APPLIED_KEY, applied);
            zombie.getPersistentData().putBoolean(LEGACY_APPLIED_KEY, applied);
        }
    }

    private static void setLastApplyTick(Zombie zombie, long tick) {
        if (zombie != null) {
            zombie.getPersistentData().putLong(THREAT_LAST_APPLY_TICK_KEY, tick);
            zombie.getPersistentData().putLong(LEGACY_LAST_APPLY_TICK_KEY, tick);
        }
    }

    private static void setHealthAdjustedOnce(Zombie zombie, boolean adjusted) {
        if (zombie != null) {
            zombie.getPersistentData().putBoolean(THREAT_HEALTH_ADJUSTED_ONCE_KEY, adjusted);
            zombie.getPersistentData().putBoolean(LEGACY_HEALTH_ADJUSTED_ONCE_KEY, adjusted);
        }
    }

    private static boolean hasAnyThreatModifier(Zombie zombie) {
        return modifierPresent(zombie.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_MODIFIER_ID, LEGACY_DAMAGE_MODIFIER_ID)
            || modifierPresent(zombie.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER_ID, LEGACY_SPEED_MODIFIER_ID)
            || modifierPresent(zombie.getAttribute(Attributes.FOLLOW_RANGE), FOLLOW_RANGE_MODIFIER_ID, LEGACY_FOLLOW_RANGE_MODIFIER_ID)
            || modifierPresent(zombie.getAttribute(Attributes.ARMOR), ARMOR_MODIFIER_ID, LEGACY_ARMOR_MODIFIER_ID)
            || modifierPresent(zombie.getAttribute(Attributes.MAX_HEALTH), HEALTH_MODIFIER_ID, LEGACY_HEALTH_MODIFIER_ID);
    }

    private static boolean modifierPresent(AttributeInstance instance, ResourceLocation newId, ResourceLocation legacyId) {
        return instance != null && (instance.getModifier(newId) != null || instance.getModifier(legacyId) != null);
    }

    private static int readIntCompat(Zombie zombie, String primaryKey, String legacyKey) {
        if (zombie == null) {
            return 0;
        }

        var tag = zombie.getPersistentData();
        if (tag.contains(primaryKey)) {
            return tag.getInt(primaryKey);
        }

        if (legacyKey != null && tag.contains(legacyKey)) {
            return tag.getInt(legacyKey);
        }

        return 0;
    }

    public record ThreatComputation(
        ZombieSurvivalScalingHelper.ThreatScalingMode mode,
        String source,
        int worldDay,
        int worldDayLevel,
        int difficultyBonus,
        int distanceBonus,
        int randomVariance,
        int nearestPlayerBonus,
        int finalLevel,
        int levelCap,
        int minimumLevel,
        boolean active,
        boolean applyOnSpawn,
        int recheckIntervalTicks,
        boolean storeOnMob,
        boolean reapplyMissingModifiers,
        boolean disableHeavyEntityTickLogic,
        double damageMultiplier,
        double speedMultiplier,
        double followRangeMultiplier,
        double armorMultiplier,
        double healthMultiplier,
        int storedLevel,
        int storedWorldDay,
        int storedWorldDayLevel,
        int storedDifficultyBonus,
        int storedDistanceBonus,
        int storedRandomVariance,
        int storedNearestPlayerBonus,
        int storedFinalLevel,
        boolean applied,
        String storedSource,
        long lastApplyTick,
        boolean healthAdjustedOnce
    ) {
        private static ThreatComputation empty() {
            return new ThreatComputation(
                ZombieSurvivalScalingHelper.ThreatScalingMode.DISABLED,
                "none",
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                false,
                0,
                false,
                false,
                false,
                1.0D,
                1.0D,
                1.0D,
                1.0D,
                1.0D,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                "none",
                0L,
                false
            );
        }
    }

    public record ThreatInspection(
        BlockPos pos,
        String entityType,
        boolean enabled,
        ZombieSurvivalScalingHelper.ThreatScalingMode mode,
        int worldDay,
        int worldDayLevel,
        int difficultyBonus,
        int distanceBonus,
        int randomVariance,
        int nearestPlayerBonus,
        int finalLevel,
        int levelCap,
        String source,
        boolean active,
        boolean applyOnSpawn,
        int recheckIntervalTicks,
        boolean storeOnMob,
        boolean reapplyMissingModifiers,
        boolean disableHeavyEntityTickLogic,
        int storedLevel,
        int storedWorldDay,
        int storedWorldDayLevel,
        int storedDifficultyBonus,
        int storedDistanceBonus,
        int storedRandomVariance,
        int storedNearestPlayerBonus,
        int storedFinalLevel,
        boolean applied,
        String storedSource,
        long lastApplyTick,
        boolean healthAdjustedOnce,
        double damageMultiplier,
        double speedMultiplier,
        double followRangeMultiplier,
        double armorMultiplier,
        double healthMultiplier,
        double attackDamageValue,
        double movementSpeedValue,
        double followRangeValue,
        double armorValue,
        double maxHealthValue,
        boolean hasDamageModifier,
        boolean hasSpeedModifier,
        boolean hasFollowRangeModifier,
        boolean hasArmorModifier,
        boolean hasHealthModifier,
        boolean hasLegacyDamageModifier,
        boolean hasLegacySpeedModifier,
        boolean hasLegacyFollowRangeModifier,
        boolean hasLegacyArmorModifier,
        boolean hasLegacyHealthModifier,
        double damageModifierAmount,
        double speedModifierAmount,
        double followRangeModifierAmount,
        double armorModifierAmount,
        double healthModifierAmount,
        ThreatComputation computation
    ) {
        private static ThreatInspection empty() {
            return new ThreatInspection(
                BlockPos.ZERO,
                "unknown",
                false,
                ZombieSurvivalScalingHelper.ThreatScalingMode.DISABLED,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                "none",
                false,
                false,
                0,
                false,
                false,
                false,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                "none",
                0L,
                false,
                1.0D,
                1.0D,
                1.0D,
                1.0D,
                1.0D,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                ThreatComputation.empty()
            );
        }
    }

    public record ApplyResult(ThreatComputation computation, int changes, boolean healthAdjusted, boolean applied) {
        private static ApplyResult empty() {
            return new ApplyResult(ThreatComputation.empty(), 0, false, false);
        }
    }
}
