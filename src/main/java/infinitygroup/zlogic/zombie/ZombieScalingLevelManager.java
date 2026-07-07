package infinitygroup.zlogic.zombie;

import infinitygroup.zlogic.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;

public final class ZombieScalingLevelManager {
    public static final ResourceLocation DAMAGE_MODIFIER_ID = ResourceLocation.parse("zlogic:survival_scaling_damage");
    public static final ResourceLocation SPEED_MODIFIER_ID = ResourceLocation.parse("zlogic:survival_scaling_speed");
    public static final ResourceLocation FOLLOW_RANGE_MODIFIER_ID = ResourceLocation.parse("zlogic:survival_scaling_follow_range");
    public static final ResourceLocation ARMOR_MODIFIER_ID = ResourceLocation.parse("zlogic:survival_scaling_armor");
    public static final ResourceLocation HEALTH_MODIFIER_ID = ResourceLocation.parse("zlogic:survival_scaling_health");

    private static final String LEVEL_KEY = "zlogic_scaling_level";
    private static final String WORLD_DAY_KEY = "zlogic_scaling_world_day";
    private static final String APPLIED_KEY = "zlogic_scaling_applied";
    private static final String SOURCE_KEY = "zlogic_scaling_source";
    private static final String LAST_APPLY_TICK_KEY = "zlogic_scaling_last_apply_tick";
    private static final String HEALTH_ADJUSTED_ONCE_KEY = "zlogic_scaling_health_adjusted_once";

    private ZombieScalingLevelManager() {
    }

    public static ScalingComputation compute(ServerLevel level, Zombie zombie) {
        if (level == null || zombie == null || !ZombieFamilyHelper.isZombieFamily(zombie)) {
            return ScalingComputation.empty();
        }

        ZombieSurvivalScalingHelper.SurvivalScalingMode mode = ZombieSurvivalScalingHelper.SurvivalScalingMode.parse(Config.survivalScalingMode);
        if (!Config.enableSurvivalDaysScaling || mode == ZombieSurvivalScalingHelper.SurvivalScalingMode.DISABLED) {
            return ScalingComputation.disabled(level, zombie, mode);
        }

        int worldDay = ZombieSurvivalScalingHelper.getWorldDay(level);
        int scalingDays = ZombieSurvivalScalingHelper.getScalingDays(worldDay);
        int levelValue = ZombieSurvivalScalingHelper.calculateScalingLevel(worldDay);
        String source = switch (mode) {
            case NEAREST_PLAYER -> "NEAREST_PLAYER_PREPARED";
            case HYBRID -> Config.survivalScalingUseNearestPlayer ? "HYBRID_PREPARED" : "WORLD_DAY";
            default -> "WORLD_DAY";
        };

        return build(level, zombie, mode, source, worldDay, scalingDays, levelValue, true);
    }

    public static ApplyResult apply(ServerLevel level, Zombie zombie, boolean fromSpawnOrJoin) {
        if (level == null || zombie == null || !ZombieFamilyHelper.isZombieFamily(zombie)) {
            return ApplyResult.empty();
        }

        ScalingComputation computation = compute(level, zombie);
        if (!computation.active()) {
            clearStoredState(zombie);
            removeAllModifiers(zombie);
            return new ApplyResult(computation, 0, false, false);
        }

        if (Config.survivalScalingStoreLevelOnMob) {
            storeComputation(zombie, computation);
        }

        int changes = 0;
        changes += syncScalingModifier(zombie.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_MODIFIER_ID, computation.damageMultiplier());
        changes += syncScalingModifier(zombie.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER_ID, computation.speedMultiplier());
        changes += syncScalingModifier(zombie.getAttribute(Attributes.FOLLOW_RANGE), FOLLOW_RANGE_MODIFIER_ID, computation.followRangeMultiplier());
        changes += syncScalingModifier(zombie.getAttribute(Attributes.ARMOR), ARMOR_MODIFIER_ID, computation.armorMultiplier());

        boolean healthChanged = false;
        AttributeInstance health = zombie.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            double beforeMax = health.getValue();
            int healthChange = syncScalingModifier(health, HEALTH_MODIFIER_ID, computation.healthMultiplier());
            healthChanged = healthChange > 0;
            changes += healthChange;

            if (fromSpawnOrJoin && Config.survivalScalingApplyOnSpawn && healthChanged && !hasHealthAdjustedOnce(zombie)) {
                double afterMax = health.getValue();
                adjustHealthProportionally(zombie, beforeMax, afterMax);
                setHealthAdjustedOnce(zombie, true);
            } else if (zombie.getHealth() > health.getValue()) {
                zombie.setHealth((float) health.getValue());
            }
        }

        setApplied(zombie, true);
        setLastApplyTick(zombie, zombie.level().getGameTime());
        if (Config.survivalScalingStoreLevelOnMob) {
            storeComputation(zombie, computation);
        }

        return new ApplyResult(computation, changes, healthChanged, true);
    }

    public static boolean shouldReapply(ServerLevel level, Zombie zombie) {
        if (level == null || zombie == null) {
            return false;
        }

        ScalingComputation computation = compute(level, zombie);
        if (!computation.active()) {
            return hasAnyModifier(zombie);
        }

        if (!isApplied(zombie)) {
            return true;
        }

        if (getStoredLevel(zombie) != computation.scalingLevel()) {
            return true;
        }

        if (!Config.survivalScalingReapplyMissingModifiers) {
            return false;
        }

        return !modifiersMatch(zombie, computation);
    }

    public static boolean modifiersMatch(Zombie zombie, ScalingComputation computation) {
        if (zombie == null || computation == null || !computation.active()) {
            return false;
        }

        return modifierMatches(zombie.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_MODIFIER_ID, computation.damageMultiplier())
            && modifierMatches(zombie.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER_ID, computation.speedMultiplier())
            && modifierMatches(zombie.getAttribute(Attributes.FOLLOW_RANGE), FOLLOW_RANGE_MODIFIER_ID, computation.followRangeMultiplier())
            && modifierMatches(zombie.getAttribute(Attributes.ARMOR), ARMOR_MODIFIER_ID, computation.armorMultiplier())
            && modifierMatches(zombie.getAttribute(Attributes.MAX_HEALTH), HEALTH_MODIFIER_ID, computation.healthMultiplier());
    }

    public static void clearStoredState(Zombie zombie) {
        if (zombie == null) {
            return;
        }

        var tag = zombie.getPersistentData();
        tag.remove(LEVEL_KEY);
        tag.remove(WORLD_DAY_KEY);
        tag.remove(APPLIED_KEY);
        tag.remove(SOURCE_KEY);
        tag.remove(LAST_APPLY_TICK_KEY);
        tag.remove(HEALTH_ADJUSTED_ONCE_KEY);
    }

    public static int getStoredLevel(Zombie zombie) {
        if (zombie == null) {
            return 0;
        }

        return zombie.getPersistentData().contains(LEVEL_KEY) ? zombie.getPersistentData().getInt(LEVEL_KEY) : 0;
    }

    public static int getStoredWorldDay(Zombie zombie) {
        if (zombie == null) {
            return 0;
        }

        return zombie.getPersistentData().contains(WORLD_DAY_KEY) ? zombie.getPersistentData().getInt(WORLD_DAY_KEY) : 0;
    }

    public static boolean isApplied(Zombie zombie) {
        return zombie != null && zombie.getPersistentData().getBoolean(APPLIED_KEY);
    }

    public static String getStoredSource(Zombie zombie) {
        if (zombie == null || !zombie.getPersistentData().contains(SOURCE_KEY)) {
            return "none";
        }

        return zombie.getPersistentData().getString(SOURCE_KEY);
    }

    public static long getLastApplyTick(Zombie zombie) {
        if (zombie == null || !zombie.getPersistentData().contains(LAST_APPLY_TICK_KEY)) {
            return 0L;
        }

        return zombie.getPersistentData().getLong(LAST_APPLY_TICK_KEY);
    }

    public static boolean hasHealthAdjustedOnce(Zombie zombie) {
        return zombie != null && zombie.getPersistentData().getBoolean(HEALTH_ADJUSTED_ONCE_KEY);
    }

    private static ScalingComputation build(
        ServerLevel level,
        Zombie zombie,
        ZombieSurvivalScalingHelper.SurvivalScalingMode mode,
        String source,
        int worldDay,
        int scalingDays,
        int scalingLevel,
        boolean active
    ) {
        double damageMultiplier = ZombieSurvivalScalingHelper.calculateMultiplierFromLevel(scalingLevel, Config.survivalScalingDamagePerDay, Config.survivalScalingMaxDamageMultiplier);
        double speedMultiplier = ZombieSurvivalScalingHelper.calculateMultiplierFromLevel(scalingLevel, Config.survivalScalingSpeedPerDay, Config.survivalScalingMaxSpeedMultiplier);
        double followRangeMultiplier = ZombieSurvivalScalingHelper.calculateMultiplierFromLevel(scalingLevel, Config.survivalScalingFollowRangePerDay, Config.survivalScalingMaxFollowRangeMultiplier);
        double armorMultiplier = ZombieSurvivalScalingHelper.calculateMultiplierFromLevel(scalingLevel, Config.survivalScalingArmorPerDay, Config.survivalScalingMaxArmorMultiplier);
        double healthMultiplier = ZombieSurvivalScalingHelper.calculateMultiplierFromLevel(scalingLevel, Config.survivalScalingHealthPerDay, Config.survivalScalingMaxHealthMultiplier);

        return new ScalingComputation(
            mode,
            source,
            worldDay,
            scalingDays,
            scalingLevel,
            ZombieSurvivalScalingHelper.getEffectiveLevelCap(),
            Config.survivalScalingLevelsPerDay,
            Config.survivalScalingStartDay,
            damageMultiplier,
            speedMultiplier,
            followRangeMultiplier,
            armorMultiplier,
            healthMultiplier,
            active && scalingLevel >= 0,
            Config.survivalScalingApplyOnSpawn,
            Config.survivalScalingRecheckIntervalTicks,
            Config.survivalScalingStoreLevelOnMob,
            Config.survivalScalingReapplyMissingModifiers,
            Config.survivalScalingDisableEntityTickHeavyLogic,
            getStoredLevel(zombie),
            getStoredWorldDay(zombie),
            isApplied(zombie),
            getStoredSource(zombie),
            getLastApplyTick(zombie),
            hasHealthAdjustedOnce(zombie)
        );
    }

    private static void storeComputation(Zombie zombie, ScalingComputation computation) {
        if (zombie == null || computation == null) {
            return;
        }

        var tag = zombie.getPersistentData();
        tag.putInt(LEVEL_KEY, computation.scalingLevel());
        tag.putInt(WORLD_DAY_KEY, computation.worldDay());
        tag.putBoolean(APPLIED_KEY, computation.active());
        tag.putString(SOURCE_KEY, computation.source());
        tag.putLong(LAST_APPLY_TICK_KEY, zombie.level().getGameTime());
    }

    private static void setApplied(Zombie zombie, boolean applied) {
        if (zombie != null) {
            zombie.getPersistentData().putBoolean(APPLIED_KEY, applied);
        }
    }

    private static void setLastApplyTick(Zombie zombie, long tick) {
        if (zombie != null) {
            zombie.getPersistentData().putLong(LAST_APPLY_TICK_KEY, tick);
        }
    }

    private static void setHealthAdjustedOnce(Zombie zombie, boolean adjusted) {
        if (zombie != null) {
            zombie.getPersistentData().putBoolean(HEALTH_ADJUSTED_ONCE_KEY, adjusted);
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

    private static int syncScalingModifier(AttributeInstance instance, ResourceLocation id, double multiplier) {
        if (instance == null) {
            return 0;
        }

        AttributeModifier current = instance.getModifier(id);
        if (multiplier <= 1.0D) {
            if (current != null) {
                instance.removeModifier(id);
                return 1;
            }

            return 0;
        }

        double amount = multiplier - 1.0D;
        AttributeModifier expected = new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (expected.equals(current)) {
            return 0;
        }

        if (current != null) {
            instance.removeModifier(id);
        }

        instance.addPermanentModifier(expected);
        return 1;
    }

    private static boolean modifierMatches(AttributeInstance instance, ResourceLocation id, double multiplier) {
        if (instance == null) {
            return false;
        }

        AttributeModifier current = instance.getModifier(id);
        if (multiplier <= 1.0D) {
            return current == null;
        }

        double amount = multiplier - 1.0D;
        AttributeModifier expected = new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        return expected.equals(current);
    }

    private static boolean hasAnyModifier(Zombie zombie) {
        return modifierPresent(zombie.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_MODIFIER_ID)
            || modifierPresent(zombie.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER_ID)
            || modifierPresent(zombie.getAttribute(Attributes.FOLLOW_RANGE), FOLLOW_RANGE_MODIFIER_ID)
            || modifierPresent(zombie.getAttribute(Attributes.ARMOR), ARMOR_MODIFIER_ID)
            || modifierPresent(zombie.getAttribute(Attributes.MAX_HEALTH), HEALTH_MODIFIER_ID);
    }

    private static void removeAllModifiers(Zombie zombie) {
        if (zombie == null) {
            return;
        }

        syncRemove(zombie.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_MODIFIER_ID);
        syncRemove(zombie.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER_ID);
        syncRemove(zombie.getAttribute(Attributes.FOLLOW_RANGE), FOLLOW_RANGE_MODIFIER_ID);
        syncRemove(zombie.getAttribute(Attributes.ARMOR), ARMOR_MODIFIER_ID);
        syncRemove(zombie.getAttribute(Attributes.MAX_HEALTH), HEALTH_MODIFIER_ID);
        zombie.setHealth(Math.min(zombie.getHealth(), zombie.getMaxHealth()));
    }

    private static boolean modifierPresent(AttributeInstance instance, ResourceLocation id) {
        return instance != null && instance.getModifier(id) != null;
    }

    private static int syncRemove(AttributeInstance instance, ResourceLocation id) {
        if (instance != null && instance.getModifier(id) != null) {
            instance.removeModifier(id);
            return 1;
        }

        return 0;
    }

    public record ScalingComputation(
        ZombieSurvivalScalingHelper.SurvivalScalingMode mode,
        String source,
        int worldDay,
        int scalingDays,
        int scalingLevel,
        int levelCap,
        double levelsPerDay,
        int startDay,
        double damageMultiplier,
        double speedMultiplier,
        double followRangeMultiplier,
        double armorMultiplier,
        double healthMultiplier,
        boolean active,
        boolean applyOnSpawn,
        int recheckIntervalTicks,
        boolean storeLevelOnMob,
        boolean reapplyMissingModifiers,
        boolean disableHeavyEntityTickLogic,
        int storedLevel,
        int storedWorldDay,
        boolean applied,
        String storedSource,
        long lastApplyTick,
        boolean healthAdjustedOnce
    ) {
        private static ScalingComputation empty() {
            return new ScalingComputation(
                ZombieSurvivalScalingHelper.SurvivalScalingMode.DISABLED,
                "none",
                0,
                0,
                0,
                0,
                0.0D,
                0,
                1.0D,
                1.0D,
                1.0D,
                1.0D,
                1.0D,
                false,
                false,
                0,
                false,
                false,
                false,
                0,
                0,
                false,
                "none",
                0L,
                false
            );
        }

        private static ScalingComputation disabled(ServerLevel level, Zombie zombie, ZombieSurvivalScalingHelper.SurvivalScalingMode mode) {
            int worldDay = level != null ? ZombieSurvivalScalingHelper.getWorldDay(level) : 0;
            return new ScalingComputation(
                mode,
                "disabled",
                worldDay,
                ZombieSurvivalScalingHelper.getScalingDays(worldDay),
                0,
                ZombieSurvivalScalingHelper.getEffectiveLevelCap(),
                Config.survivalScalingLevelsPerDay,
                Config.survivalScalingStartDay,
                1.0D,
                1.0D,
                1.0D,
                1.0D,
                1.0D,
                false,
                Config.survivalScalingApplyOnSpawn,
                Config.survivalScalingRecheckIntervalTicks,
                Config.survivalScalingStoreLevelOnMob,
                Config.survivalScalingReapplyMissingModifiers,
                Config.survivalScalingDisableEntityTickHeavyLogic,
                zombie != null ? getStoredLevel(zombie) : 0,
                zombie != null ? getStoredWorldDay(zombie) : 0,
                zombie != null && isApplied(zombie),
                zombie != null ? getStoredSource(zombie) : "none",
                zombie != null ? getLastApplyTick(zombie) : 0L,
                zombie != null && hasHealthAdjustedOnce(zombie)
            );
        }
    }

    public record ApplyResult(ScalingComputation computation, int changes, boolean healthAdjusted, boolean applied) {
        private static ApplyResult empty() {
            return new ApplyResult(ScalingComputation.empty(), 0, false, false);
        }
    }
}
