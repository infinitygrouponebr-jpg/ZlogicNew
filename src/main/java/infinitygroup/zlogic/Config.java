package infinitygroup.zlogic;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

@EventBusSubscriber(modid = Zlogic.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue ENABLE_NIGHT_ZOMBIE_BUFFS = BUILDER
        .comment("Enable or disable the night zombie buff system.")
        .define("enableNightZombieBuffs", true);

    private static final ModConfigSpec.DoubleValue NIGHT_SPEED_MULTIPLIER = BUILDER
        .comment("Extra movement speed applied at night as a multiplier over the zombie's base speed.")
        .defineInRange("nightSpeedMultiplier", 0.20D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue NIGHT_ATTACK_DAMAGE_BONUS = BUILDER
        .comment("Flat attack damage bonus applied at night.")
        .defineInRange("nightAttackDamageBonus", 2.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.DoubleValue NIGHT_FOLLOW_RANGE_BONUS = BUILDER
        .comment("Flat follow range bonus applied at night.")
        .defineInRange("nightFollowRangeBonus", 16.0D, 0.0D, 256.0D);

    private static final ModConfigSpec.DoubleValue NIGHT_ARMOR_BONUS = BUILDER
        .comment("Flat armor bonus applied at night.")
        .defineInRange("nightArmorBonus", 2.0D, 0.0D, 100.0D);

    private static final ModConfigSpec.BooleanValue AFFECT_ONLY_ZOMBIES = BUILDER
        .comment("When true, only the vanilla zombie family is affected. When false, any entity extending Zombie can be affected.")
        .define("affectOnlyZombies", true);

    private static final ModConfigSpec.BooleanValue DEBUG_LOGS = BUILDER
        .comment("Enable debug logging for the night buff system.")
        .define("debugLogs", false);

    private static final ModConfigSpec.BooleanValue ENABLE_NOISE_SYSTEM = BUILDER
        .comment("Enable or disable the base noise system.")
        .define("enableNoiseSystem", true);

    private static final ModConfigSpec.IntValue NOISE_CHECK_INTERVAL_TICKS = BUILDER
        .comment("How often zombies re-evaluate nearby noises.")
        .defineInRange("noiseCheckIntervalTicks", 40, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue NOISE_MEMORY_TICKS = BUILDER
        .comment("How long a noise event remains active before expiring.")
        .defineInRange("noiseMemoryTicks", 200, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue NOISE_MAX_EVENTS_PER_LEVEL = BUILDER
        .comment("Maximum number of active noise events stored per dimension.")
        .defineInRange("noiseMaxEventsPerLevel", 64, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue ZOMBIE_HEAR_NOISE_RADIUS_MULTIPLIER = BUILDER
        .comment("Multiplier applied to a noise radius when zombies evaluate whether they can hear it.")
        .defineInRange("zombieHearNoiseRadiusMultiplier", 1.0D, 0.0D, 64.0D);

    private static final ModConfigSpec.BooleanValue ZOMBIE_MOVE_TO_NOISE = BUILDER
        .comment("When true, zombies can walk toward a noise position if no player is prioritised.")
        .define("zombieMoveToNoise", true);

    private static final ModConfigSpec.BooleanValue ZOMBIE_TARGET_PLAYER_NEAR_NOISE = BUILDER
        .comment("When true, zombies try to target a valid player near a noise source.")
        .define("zombieTargetPlayerNearNoise", true);

    private static final ModConfigSpec.DoubleValue ZOMBIE_NOISE_TARGET_PLAYER_RADIUS = BUILDER
        .comment("Radius around a noise source used to look for a valid player target.")
        .defineInRange("zombieNoiseTargetPlayerRadius", 12.0D, 0.0D, 256.0D);

    private static final ModConfigSpec.DoubleValue ZOMBIE_NOISE_ALERT_RADIUS = BUILDER
        .comment("Radius used to alert other zombies after one zombie reacts to a noise.")
        .defineInRange("zombieNoiseAlertRadius", 24.0D, 0.0D, 256.0D);

    private static final ModConfigSpec.IntValue ZOMBIE_NOISE_ALERT_MAX_ZOMBIES = BUILDER
        .comment("Maximum number of nearby zombies alerted by one noise reaction.")
        .defineInRange("zombieNoiseAlertMaxZombies", 12, 0, 64);

    private static final ModConfigSpec.BooleanValue ZOMBIE_NOISE_REQUIRES_LINE_OF_SIGHT = BUILDER
        .comment("When true, alerted zombies must have line of sight to the noise or reacting zombie.")
        .define("zombieNoiseRequiresLineOfSight", false);

    private static final ModConfigSpec.DoubleValue ZOMBIE_NOISE_RETARGET_CHANCE = BUILDER
        .comment("Chance for a zombie with a valid target to switch to a player near a noise.")
        .defineInRange("zombieNoiseRetargetChance", 0.35D, 0.0D, 1.0D);

    private static final ModConfigSpec.BooleanValue ZOMBIE_NOISE_DEBUG_LOGS = BUILDER
        .comment("Enable debug logging for the noise system.")
        .define("zombieNoiseDebugLogs", false);

    private static final ModConfigSpec.BooleanValue ENABLE_ZOMBIE_BASE_DAMAGE_SYSTEM = BUILDER
        .comment("Enable or disable the zombie base damage system.")
        .define("enableZombieBaseDamageSystem", true);

    private static final ModConfigSpec.BooleanValue ZOMBIE_BASE_DAMAGE_AFFECTS_VANILLA_ZOMBIES = BUILDER
        .comment("When true, vanilla zombies are affected by the base damage system.")
        .define("zombieBaseDamageAffectsVanillaZombies", true);

    private static final ModConfigSpec.BooleanValue ZOMBIE_BASE_DAMAGE_AFFECTS_DAY_SPAWNED_ZOMBIES = BUILDER
        .comment("When true, zombies marked as day spawned by Zlogic are affected by the base damage system.")
        .define("zombieBaseDamageAffectsDaySpawnedZombies", true);

    private static final ModConfigSpec.IntValue ZOMBIE_BASE_DAMAGE_CHECK_INTERVAL_TICKS = BUILDER
        .comment("How often zombie base damage modifiers are re-evaluated.")
        .defineInRange("zombieBaseDamageCheckIntervalTicks", 100, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue ZOMBIE_BASE_DAMAGE_BONUS_EASY = BUILDER
        .comment("Flat extra attack damage applied to zombies on Easy when multiplier mode is disabled.")
        .defineInRange("zombieBaseDamageBonusEasy", 2.0D, 0.0D, 256.0D);

    private static final ModConfigSpec.DoubleValue ZOMBIE_BASE_DAMAGE_BONUS_NORMAL = BUILDER
        .comment("Flat extra attack damage applied to zombies on Normal when multiplier mode is disabled.")
        .defineInRange("zombieBaseDamageBonusNormal", 4.0D, 0.0D, 256.0D);

    private static final ModConfigSpec.DoubleValue ZOMBIE_BASE_DAMAGE_BONUS_HARD = BUILDER
        .comment("Flat extra attack damage applied to zombies on Hard when multiplier mode is disabled.")
        .defineInRange("zombieBaseDamageBonusHard", 6.0D, 0.0D, 256.0D);

    private static final ModConfigSpec.BooleanValue ZOMBIE_BASE_DAMAGE_USE_MULTIPLIER = BUILDER
        .comment("When true, zombie base damage uses multiplicative scaling instead of flat bonuses.")
        .define("zombieBaseDamageUseMultiplier", false);

    private static final ModConfigSpec.DoubleValue ZOMBIE_BASE_DAMAGE_MULTIPLIER_EASY = BUILDER
        .comment("Attack damage multiplier applied to zombies on Easy when multiplier mode is enabled.")
        .defineInRange("zombieBaseDamageMultiplierEasy", 1.25D, 0.0D, 64.0D);

    private static final ModConfigSpec.DoubleValue ZOMBIE_BASE_DAMAGE_MULTIPLIER_NORMAL = BUILDER
        .comment("Attack damage multiplier applied to zombies on Normal when multiplier mode is enabled.")
        .defineInRange("zombieBaseDamageMultiplierNormal", 1.50D, 0.0D, 64.0D);

    private static final ModConfigSpec.DoubleValue ZOMBIE_BASE_DAMAGE_MULTIPLIER_HARD = BUILDER
        .comment("Attack damage multiplier applied to zombies on Hard when multiplier mode is enabled.")
        .defineInRange("zombieBaseDamageMultiplierHard", 2.00D, 0.0D, 64.0D);

    private static final ModConfigSpec.BooleanValue ZOMBIE_BASE_DAMAGE_DEBUG_LOGS = BUILDER
        .comment("Enable debug logging for the zombie base damage system.")
        .define("zombieBaseDamageDebugLogs", false);

    private static final ModConfigSpec.BooleanValue ENABLE_SURVIVAL_DAYS_SCALING = BUILDER
        .comment("Prepare the survival-day scaling system without enabling it by default.")
        .define("enableSurvivalDaysScaling", false);

    private static final ModConfigSpec.ConfigValue<String> SURVIVAL_SCALING_MODE = BUILDER
        .comment("Survival scaling mode. Supported values: WORLD_DAY, NEAREST_PLAYER, HYBRID, DISABLED.")
        .define("survivalScalingMode", "WORLD_DAY");

    private static final ModConfigSpec.BooleanValue SURVIVAL_SCALING_APPLY_ON_SPAWN = BUILDER
        .comment("When true, apply survival scaling when a zombie enters the world.")
        .define("survivalScalingApplyOnSpawn", true);

    private static final ModConfigSpec.IntValue SURVIVAL_SCALING_START_DAY = BUILDER
        .comment("World day at which future survival scaling begins.")
        .defineInRange("survivalScalingStartDay", 10, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.BooleanValue SURVIVAL_SCALING_USE_NEAREST_PLAYER = BUILDER
        .comment("When true, the future survival scaling system will use the nearest player as reference.")
        .define("survivalScalingUseNearestPlayer", true);

    private static final ModConfigSpec.DoubleValue SURVIVAL_SCALING_RADIUS = BUILDER
        .comment("Radius used by the future survival-day scaling system to find a reference player.")
        .defineInRange("survivalScalingRadius", 96.0D, 0.0D, 256.0D);

    private static final ModConfigSpec.IntValue SURVIVAL_SCALING_CHECK_INTERVAL_TICKS = BUILDER
        .comment("How often the future survival-day scaling system re-evaluates a zombie.")
        .defineInRange("survivalScalingCheckIntervalTicks", 200, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue SURVIVAL_SCALING_RECHECK_INTERVAL_TICKS = BUILDER
        .comment("How often the light survival scaling fallback checks a zombie.")
        .defineInRange("survivalScalingRecheckIntervalTicks", 1200, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue SURVIVAL_SCALING_MAX_LEVEL = BUILDER
        .comment("Maximum raw scaling level before cap logic.")
        .defineInRange("survivalScalingMaxLevel", 100, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue SURVIVAL_SCALING_LEVEL_CAP = BUILDER
        .comment("Final cap applied to the computed survival scaling level.")
        .defineInRange("survivalScalingLevelCap", 100, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue SURVIVAL_SCALING_LEVELS_PER_DAY = BUILDER
        .comment("How many scaling levels are gained per world day after the start day.")
        .defineInRange("survivalScalingLevelsPerDay", 1.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.BooleanValue SURVIVAL_SCALING_STORE_LEVEL_ON_MOB = BUILDER
        .comment("When true, store the computed scaling level on the mob.")
        .define("survivalScalingStoreLevelOnMob", true);

    private static final ModConfigSpec.BooleanValue SURVIVAL_SCALING_REAPPLY_MISSING_MODIFIERS = BUILDER
        .comment("When true, reapply missing scaling modifiers during rare fallback checks.")
        .define("survivalScalingReapplyMissingModifiers", true);

    private static final ModConfigSpec.BooleanValue SURVIVAL_SCALING_DISABLE_ENTITY_TICK_HEAVY_LOGIC = BUILDER
        .comment("When true, keep EntityTickEvent scaling logic to a lightweight fallback only.")
        .define("survivalScalingDisableEntityTickHeavyLogic", true);

    private static final ModConfigSpec.DoubleValue SURVIVAL_SCALING_DAMAGE_PER_DAY = BUILDER
        .comment("Future survival scaling: attack damage increase per day after the start day.")
        .defineInRange("survivalScalingDamagePerDay", 0.02D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue SURVIVAL_SCALING_SPEED_PER_DAY = BUILDER
        .comment("Future survival scaling: movement speed increase per day after the start day.")
        .defineInRange("survivalScalingSpeedPerDay", 0.005D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue SURVIVAL_SCALING_FOLLOW_RANGE_PER_DAY = BUILDER
        .comment("Future survival scaling: follow range increase per day after the start day.")
        .defineInRange("survivalScalingFollowRangePerDay", 0.01D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue SURVIVAL_SCALING_ARMOR_PER_DAY = BUILDER
        .comment("Future survival scaling: armor increase per day after the start day.")
        .defineInRange("survivalScalingArmorPerDay", 0.01D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue SURVIVAL_SCALING_HEALTH_PER_DAY = BUILDER
        .comment("Future survival scaling: health increase per day after the start day.")
        .defineInRange("survivalScalingHealthPerDay", 0.01D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue SURVIVAL_SCALING_MAX_DAMAGE_MULTIPLIER = BUILDER
        .comment("Future survival scaling: maximum attack damage multiplier.")
        .defineInRange("survivalScalingMaxDamageMultiplier", 3.0D, 1.0D, 64.0D);

    private static final ModConfigSpec.DoubleValue SURVIVAL_SCALING_MAX_SPEED_MULTIPLIER = BUILDER
        .comment("Future survival scaling: maximum movement speed multiplier.")
        .defineInRange("survivalScalingMaxSpeedMultiplier", 1.8D, 1.0D, 64.0D);

    private static final ModConfigSpec.DoubleValue SURVIVAL_SCALING_MAX_FOLLOW_RANGE_MULTIPLIER = BUILDER
        .comment("Future survival scaling: maximum follow range multiplier.")
        .defineInRange("survivalScalingMaxFollowRangeMultiplier", 2.5D, 1.0D, 64.0D);

    private static final ModConfigSpec.DoubleValue SURVIVAL_SCALING_MAX_ARMOR_MULTIPLIER = BUILDER
        .comment("Future survival scaling: maximum armor multiplier.")
        .defineInRange("survivalScalingMaxArmorMultiplier", 2.0D, 1.0D, 64.0D);

    private static final ModConfigSpec.DoubleValue SURVIVAL_SCALING_MAX_HEALTH_MULTIPLIER = BUILDER
        .comment("Future survival scaling: maximum health multiplier.")
        .defineInRange("survivalScalingMaxHealthMultiplier", 2.5D, 1.0D, 64.0D);

    private static final ModConfigSpec.BooleanValue SURVIVAL_SCALING_DEBUG_LOGS = BUILDER
        .comment("Enable debug logging for the future survival-day scaling helpers.")
        .define("survivalScalingDebugLogs", false);

    private static final ModConfigSpec.BooleanValue ENABLE_THREAT_LEVEL_SCALING = BUILDER
        .comment("Enable or disable the threat-level scaling system.")
        .define("enableThreatLevelScaling", true);

    private static final ModConfigSpec.ConfigValue<String> THREAT_SCALING_MODE = BUILDER
        .comment("Threat scaling mode. Supported values: MULTI_FACTOR, LEGACY_DAY, DISABLED.")
        .define("threatScalingMode", "MULTI_FACTOR");

    private static final ModConfigSpec.BooleanValue THREAT_SCALING_APPLY_ON_SPAWN = BUILDER
        .comment("When true, apply threat scaling when a zombie enters the world.")
        .define("threatScalingApplyOnSpawn", true);

    private static final ModConfigSpec.IntValue THREAT_SCALING_RECHECK_INTERVAL_TICKS = BUILDER
        .comment("How often the lightweight threat scaling fallback rechecks a zombie.")
        .defineInRange("threatScalingRecheckIntervalTicks", 1200, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.BooleanValue THREAT_SCALING_STORE_ON_MOB = BUILDER
        .comment("When true, store threat scaling data on the mob.")
        .define("threatScalingStoreOnMob", true);

    private static final ModConfigSpec.BooleanValue THREAT_SCALING_DEBUG_LOGS = BUILDER
        .comment("Enable debug logging for the threat-level scaling system.")
        .define("threatScalingDebugLogs", false);

    private static final ModConfigSpec.IntValue THREAT_SCALING_FINAL_LEVEL_CAP = BUILDER
        .comment("Final cap applied to the computed threat level.")
        .defineInRange("threatScalingFinalLevelCap", 100, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue THREAT_SCALING_MINIMUM_LEVEL = BUILDER
        .comment("Minimum threat level allowed after all contributions are combined.")
        .defineInRange("threatScalingMinimumLevel", 0, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.BooleanValue THREAT_WORLD_DAY_ENABLED = BUILDER
        .comment("When true, the threat level includes a world-day contribution.")
        .define("threatWorldDayEnabled", true);

    private static final ModConfigSpec.IntValue THREAT_WORLD_DAY_START_DAY = BUILDER
        .comment("World day at which the threat world-day contribution begins.")
        .defineInRange("threatWorldDayStartDay", 10, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue THREAT_WORLD_DAY_LEVELS_PER_DAY = BUILDER
        .comment("How many threat levels are gained per world day after the start day.")
        .defineInRange("threatWorldDayLevelsPerDay", 1.0D, 0.0D, 1000.0D);

    private static final ModConfigSpec.IntValue THREAT_WORLD_DAY_MAX_LEVEL = BUILDER
        .comment("Maximum threat level contribution from world-day progression.")
        .defineInRange("threatWorldDayMaxLevel", 70, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.BooleanValue THREAT_DIFFICULTY_ENABLED = BUILDER
        .comment("When true, the threat level includes a difficulty contribution.")
        .define("threatDifficultyEnabled", true);

    private static final ModConfigSpec.IntValue THREAT_DIFFICULTY_EASY_BONUS = BUILDER
        .comment("Threat bonus added on Easy.")
        .defineInRange("threatDifficultyEasyBonus", 0, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue THREAT_DIFFICULTY_NORMAL_BONUS = BUILDER
        .comment("Threat bonus added on Normal.")
        .defineInRange("threatDifficultyNormalBonus", 5, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue THREAT_DIFFICULTY_HARD_BONUS = BUILDER
        .comment("Threat bonus added on Hard.")
        .defineInRange("threatDifficultyHardBonus", 10, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.BooleanValue THREAT_DISTANCE_ENABLED = BUILDER
        .comment("When true, the threat level includes a distance-from-spawn contribution.")
        .define("threatDistanceEnabled", true);

    private static final ModConfigSpec.IntValue THREAT_DISTANCE_START_BLOCKS = BUILDER
        .comment("Distance from spawn before the threat distance bonus starts.")
        .defineInRange("threatDistanceStartBlocks", 1000, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue THREAT_DISTANCE_LEVELS_PER_1000_BLOCKS = BUILDER
        .comment("Threat levels gained per 1000 blocks beyond the start distance.")
        .defineInRange("threatDistanceLevelsPer1000Blocks", 5, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue THREAT_DISTANCE_MAX_BONUS = BUILDER
        .comment("Maximum threat bonus from distance to spawn.")
        .defineInRange("threatDistanceMaxBonus", 30, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.BooleanValue THREAT_RANDOM_VARIANCE_ENABLED = BUILDER
        .comment("When true, threat level gains a small random variance at spawn or rescale.")
        .define("threatRandomVarianceEnabled", true);

    private static final ModConfigSpec.IntValue THREAT_RANDOM_VARIANCE_MIN = BUILDER
        .comment("Minimum random threat variance.")
        .defineInRange("threatRandomVarianceMin", 0, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue THREAT_RANDOM_VARIANCE_MAX = BUILDER
        .comment("Maximum random threat variance.")
        .defineInRange("threatRandomVarianceMax", 3, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.BooleanValue THREAT_NEAREST_PLAYER_ENABLED = BUILDER
        .comment("When true, threat scaling prepares a future nearest-player contribution on spawn/rescale.")
        .define("threatNearestPlayerEnabled", false);

    private static final ModConfigSpec.DoubleValue THREAT_NEAREST_PLAYER_RADIUS = BUILDER
        .comment("Radius used to find the nearest player for future threat scaling extensions.")
        .defineInRange("threatNearestPlayerRadius", 96.0D, 0.0D, 256.0D);

    private static final ModConfigSpec.BooleanValue THREAT_NEAREST_PLAYER_SURVIVAL_DAYS_ENABLED = BUILDER
        .comment("Reserved future toggle for nearest-player survival-day contribution.")
        .define("threatNearestPlayerSurvivalDaysEnabled", false);

    private static final ModConfigSpec.IntValue THREAT_NEAREST_PLAYER_MAX_BONUS = BUILDER
        .comment("Maximum threat bonus reserved for the nearest-player contribution.")
        .defineInRange("threatNearestPlayerMaxBonus", 50, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue THREAT_DAMAGE_PER_LEVEL = BUILDER
        .comment("Attack damage increase per threat level.")
        .defineInRange("threatDamagePerLevel", 0.015D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue THREAT_SPEED_PER_LEVEL = BUILDER
        .comment("Movement speed increase per threat level.")
        .defineInRange("threatSpeedPerLevel", 0.0025D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue THREAT_FOLLOW_RANGE_PER_LEVEL = BUILDER
        .comment("Follow range increase per threat level.")
        .defineInRange("threatFollowRangePerLevel", 0.008D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue THREAT_ARMOR_PER_LEVEL = BUILDER
        .comment("Armor increase per threat level.")
        .defineInRange("threatArmorPerLevel", 0.006D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue THREAT_HEALTH_PER_LEVEL = BUILDER
        .comment("Health increase per threat level.")
        .defineInRange("threatHealthPerLevel", 0.010D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue THREAT_MAX_DAMAGE_MULTIPLIER = BUILDER
        .comment("Maximum attack damage multiplier for threat scaling.")
        .defineInRange("threatMaxDamageMultiplier", 3.0D, 1.0D, 64.0D);

    private static final ModConfigSpec.DoubleValue THREAT_MAX_SPEED_MULTIPLIER = BUILDER
        .comment("Maximum movement speed multiplier for threat scaling.")
        .defineInRange("threatMaxSpeedMultiplier", 1.6D, 1.0D, 64.0D);

    private static final ModConfigSpec.DoubleValue THREAT_MAX_FOLLOW_RANGE_MULTIPLIER = BUILDER
        .comment("Maximum follow range multiplier for threat scaling.")
        .defineInRange("threatMaxFollowRangeMultiplier", 2.5D, 1.0D, 64.0D);

    private static final ModConfigSpec.DoubleValue THREAT_MAX_ARMOR_MULTIPLIER = BUILDER
        .comment("Maximum armor multiplier for threat scaling.")
        .defineInRange("threatMaxArmorMultiplier", 2.0D, 1.0D, 64.0D);

    private static final ModConfigSpec.DoubleValue THREAT_MAX_HEALTH_MULTIPLIER = BUILDER
        .comment("Maximum health multiplier for threat scaling.")
        .defineInRange("threatMaxHealthMultiplier", 2.5D, 1.0D, 64.0D);

    private static final ModConfigSpec.BooleanValue ENABLE_MICROTECH_COMPAT = BUILDER
        .comment("Enable or disable the optional MicroTech compatibility layer.")
        .define("enableMicroTechCompat", true);

    private static final ModConfigSpec.BooleanValue MICROTECH_MACHINE_NOISE_ENABLED = BUILDER
        .comment("Enable or disable noise emissions from MicroTech machines.")
        .define("microTechMachineNoiseEnabled", true);

    private static final ModConfigSpec.IntValue MICROTECH_MACHINE_NOISE_INTERVAL_TICKS = BUILDER
        .comment("How often MicroTech machine positions are re-scanned for noise emission.")
        .defineInRange("microTechMachineNoiseIntervalTicks", 100, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue MICROTECH_MACHINE_SCAN_INTERVAL_TICKS = BUILDER
        .comment("How often MicroTech machines are scanned for activity when emitting noise.")
        .defineInRange("microTechMachineScanIntervalTicks", 40, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue MICROTECH_MACHINE_MAX_BLOCKS_PER_SCAN = BUILDER
        .comment("Maximum number of block entities inspected per MicroTech scan tick.")
        .defineInRange("microTechMachineMaxBlocksPerScan", 512, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue MICROTECH_MACHINE_NOISE_RADIUS = BUILDER
        .comment("Radius used when MicroTech machines emit noise.")
        .defineInRange("microTechMachineNoiseRadius", 24.0D, 0.0D, 256.0D);

    private static final ModConfigSpec.IntValue MICROTECH_MACHINE_NOISE_DURATION_TICKS = BUILDER
        .comment("Duration of noise emitted by a MicroTech machine.")
        .defineInRange("microTechMachineNoiseDurationTicks", 120, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.BooleanValue MICROTECH_ONLY_RUNNING_MACHINES_MAKE_NOISE = BUILDER
        .comment("When true, only MicroTech machines detected as active emit noise.")
        .define("microTechOnlyRunningMachinesMakeNoise", true);

    private static final ModConfigSpec.BooleanValue MICROTECH_DEBUG_LOGS = BUILDER
        .comment("Enable debug logging for the MicroTech compatibility layer.")
        .define("microTechDebugLogs", false);

    private static final ModConfigSpec.BooleanValue ENABLE_ZOMBIE_MACHINE_ATTACKS = BUILDER
        .comment("Enable or disable zombie attacks against MicroTech machines.")
        .define("enableZombieMachineAttacks", true);

    private static final ModConfigSpec.BooleanValue ZOMBIE_MACHINE_ATTACK_USE_DIRECT_DETECTION = BUILDER
        .comment("When true, zombies use direct machine detection instead of requiring recent MACHINE noise.")
        .define("zombieMachineAttackUseDirectDetection", true);

    private static final ModConfigSpec.BooleanValue ZOMBIE_MACHINE_ATTACK_ONLY_MICROTECH = BUILDER
        .comment("When true, only MicroTech machines are eligible for zombie machine attacks.")
        .define("zombieMachineAttackOnlyMicroTech", true);

    private static final ModConfigSpec.BooleanValue ZOMBIE_MACHINE_ATTACK_REQUIRE_NOISE = BUILDER
        .comment("When true, zombies only attack machines that recently emitted MACHINE noise.")
        .define("zombieMachineAttackRequireNoise", true);

    private static final ModConfigSpec.DoubleValue ZOMBIE_MACHINE_ATTACK_RANGE = BUILDER
        .comment("Range used by zombies to look for machines they can attack.")
        .defineInRange("zombieMachineAttackRange", 2.2D, 0.1D, 16.0D);

    private static final ModConfigSpec.IntValue ZOMBIE_MACHINE_ATTACK_COOLDOWN_TICKS = BUILDER
        .comment("How often a zombie can attack a machine.")
        .defineInRange("zombieMachineAttackCooldownTicks", 40, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue ZOMBIE_MACHINE_DAMAGE_PER_HIT = BUILDER
        .comment("Damage applied to a machine per zombie hit.")
        .defineInRange("zombieMachineDamagePerHit", 1, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue MACHINE_DURABILITY_DEFAULT = BUILDER
        .comment("Fallback durability for machine blocks not matched by specific entries.")
        .defineInRange("machineDurabilityDefault", 8, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue MACHINE_DURABILITY_ENERGY_CONVERTER_T1 = BUILDER
        .comment("Durability for MicroTech energy converters.")
        .defineInRange("machineDurabilityEnergyConverterT1", 10, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue MACHINE_DURABILITY_EVO_TABLE = BUILDER
        .comment("Durability for MicroTech evo tables.")
        .defineInRange("machineDurabilityEvoTable", 8, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue MACHINE_DURABILITY_BATTERY_T1 = BUILDER
        .comment("Durability for MicroTech battery T1 blocks.")
        .defineInRange("machineDurabilityBatteryT1", 12, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue MACHINE_DURABILITY_BATTERY_T2 = BUILDER
        .comment("Durability for MicroTech battery T2 blocks.")
        .defineInRange("machineDurabilityBatteryT2", 16, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.BooleanValue ZOMBIE_MACHINE_BREAK_BLOCKS = BUILDER
        .comment("When true, zombies can break machine blocks when they reach durability zero.")
        .define("zombieMachineBreakBlocks", true);

    private static final ModConfigSpec.BooleanValue ZOMBIE_MACHINE_DROP_BLOCK = BUILDER
        .comment("When true, broken machine blocks drop their normal loot.")
        .define("zombieMachineDropBlock", true);

    private static final ModConfigSpec.BooleanValue ZOMBIE_MACHINE_ATTACK_ONLY_ACTIVE_MACHINES = BUILDER
        .comment("When true, only active machines can be attacked.")
        .define("zombieMachineAttackOnlyActiveMachines", true);

    private static final ModConfigSpec.BooleanValue ZOMBIE_MACHINE_ATTACK_DEBUG_LOGS = BUILDER
        .comment("Enable debug logging for zombie machine attacks.")
        .define("zombieMachineAttackDebugLogs", false);

    private static final ModConfigSpec.BooleanValue ENABLE_DIRECT_MACHINE_ATTRACTION = BUILDER
        .comment("Enable or disable direct zombie attraction to nearby active MicroTech machines.")
        .define("enableDirectMachineAttraction", true);

    private static final ModConfigSpec.DoubleValue DIRECT_MACHINE_ATTRACTION_RADIUS = BUILDER
        .comment("Radius zombies scan for active machines when direct machine attraction is enabled.")
        .defineInRange("directMachineAttractionRadius", 24.0D, 0.0D, 256.0D);

    private static final ModConfigSpec.IntValue DIRECT_MACHINE_ATTRACTION_CHECK_INTERVAL_TICKS = BUILDER
        .comment("How often each zombie rescans for a nearby machine target.")
        .defineInRange("directMachineAttractionCheckIntervalTicks", 60, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue DIRECT_MACHINE_ATTRACTION_MAX_SCAN_BLOCKS_PER_ZOMBIE = BUILDER
        .comment("Maximum number of block positions checked per zombie scan.")
        .defineInRange("directMachineAttractionMaxScanBlocksPerZombie", 128, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.BooleanValue DIRECT_MACHINE_ATTRACTION_ONLY_ACTIVE_MACHINES = BUILDER
        .comment("When true, only active machines can be chosen by direct attraction.")
        .define("directMachineAttractionOnlyActiveMachines", true);

    private static final ModConfigSpec.BooleanValue DIRECT_MACHINE_ATTRACTION_ONLY_MICROTECH = BUILDER
        .comment("When true, only MicroTech machines are eligible for direct attraction.")
        .define("directMachineAttractionOnlyMicroTech", true);

    private static final ModConfigSpec.DoubleValue DIRECT_MACHINE_ATTRACTION_NAVIGATION_SPEED = BUILDER
        .comment("Navigation speed used when zombies move toward an attracted machine.")
        .defineInRange("directMachineAttractionNavigationSpeed", 1.0D, 0.0D, 10.0D);

    private static final ModConfigSpec.DoubleValue DIRECT_MACHINE_IGNORE_PLAYER_TARGET_WITHIN = BUILDER
        .comment("If a zombie already has a player target within this distance, it will not switch to a machine.")
        .defineInRange("directMachineIgnoreMachineIfPlayerTargetWithin", 8.0D, 0.0D, 256.0D);

    private static final ModConfigSpec.IntValue DIRECT_MACHINE_ATTRACTION_REPATH_INTERVAL_TICKS = BUILDER
        .comment("How often a zombie may repath toward the same machine target.")
        .defineInRange("directMachineAttractionRepathIntervalTicks", 60, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue ZOMBIE_NOISE_REPATH_INTERVAL_TICKS = BUILDER
        .comment("How often a zombie may repath toward the same noise target.")
        .defineInRange("zombieNoiseRepathIntervalTicks", 60, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue DEBUG_ZOMBIE_COMMAND_FALLBACK_RADIUS = BUILDER
        .comment("Fallback radius used by /zlogic debug zombie when ray tracing does not hit a zombie exactly.")
        .defineInRange("debugZombieCommandFallbackRadius", 4.0D, 0.0D, 32.0D);

    private static final ModConfigSpec.BooleanValue DAY_SPAWNED_ZOMBIES_IGNORE_SUN_BURN = BUILDER
        .comment("When true, zombies marked as day spawned by Zlogic will have their sun burning cleared.")
        .define("daySpawnedZombiesIgnoreSunBurn", true);

    private static final ModConfigSpec.BooleanValue ENABLE_DAY_ZOMBIE_SPAWNS = BUILDER
        .comment("Enable or disable controlled daytime zombie spawning.")
        .define("enableDayZombieSpawns", true);

    private static final ModConfigSpec.BooleanValue ENABLE_NIGHT_ZOMBIE_SPAWNS = BUILDER
        .comment("Enable or disable controlled nighttime zombie spawning.")
        .define("enableNightZombieSpawns", true);

    private static final ModConfigSpec.IntValue NIGHT_SPAWN_INTERVAL_TICKS = BUILDER
        .comment("How often Zlogic attempts a nighttime zombie spawn wave.")
        .defineInRange("nightSpawnIntervalTicks", 400, 20, Integer.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue NIGHT_SPAWN_CHANCE = BUILDER
        .comment("Chance for a nighttime spawn wave to happen when the interval is reached.")
        .defineInRange("nightSpawnChance", 0.85D, 0.0D, 1.0D);

    private static final ModConfigSpec.IntValue NIGHT_SPAWN_MIN_GROUP_SIZE = BUILDER
        .comment("Minimum number of zombies in a nighttime spawn group.")
        .defineInRange("nightSpawnMinGroupSize", 2, 1, 32);

    private static final ModConfigSpec.IntValue NIGHT_SPAWN_MAX_GROUP_SIZE = BUILDER
        .comment("Maximum number of zombies in a nighttime spawn group.")
        .defineInRange("nightSpawnMaxGroupSize", 5, 1, 32);

    private static final ModConfigSpec.IntValue NIGHT_SPAWN_RADIUS_MIN = BUILDER
        .comment("Minimum radius around the player used for nighttime spawn attempts.")
        .defineInRange("nightSpawnRadiusMin", 24, 1, 256);

    private static final ModConfigSpec.IntValue NIGHT_SPAWN_RADIUS_MAX = BUILDER
        .comment("Maximum radius around the player used for nighttime spawn attempts.")
        .defineInRange("nightSpawnRadiusMax", 56, 1, 512);

    private static final ModConfigSpec.IntValue NIGHT_SPAWN_MAX_NEARBY_ZOMBIES = BUILDER
        .comment("Maximum number of nearby zombies allowed before nighttime spawn attempts stop.")
        .defineInRange("nightSpawnMaxNearbyZombies", 18, 0, 1000);

    private static final ModConfigSpec.IntValue NIGHT_SPAWN_NEARBY_CHECK_RADIUS = BUILDER
        .comment("Radius used to count nearby zombies before and during nighttime spawn attempts.")
        .defineInRange("nightSpawnNearbyCheckRadius", 72, 1, 256);

    private static final ModConfigSpec.BooleanValue NIGHT_SPAWN_ONLY_AT_NIGHT = BUILDER
        .comment("When true, nighttime zombies only spawn during night unless thunder is allowed.")
        .define("nightSpawnOnlyAtNight", true);

    private static final ModConfigSpec.BooleanValue NIGHT_SPAWN_ALLOW_DURING_THUNDER = BUILDER
        .comment("When true, nighttime zombies may spawn during thunder even if it is daytime.")
        .define("nightSpawnAllowDuringThunder", true);

    private static final ModConfigSpec.BooleanValue NIGHT_SPAWN_RESPECT_DO_MOB_SPAWNING = BUILDER
        .comment("When true, nighttime zombie spawning respects the doMobSpawning gamerule.")
        .define("nightSpawnRespectDoMobSpawning", true);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> NIGHT_SPAWN_ALLOWED_DIMENSIONS = BUILDER
        .comment("Allowed dimensions for nighttime zombie spawns.")
        .defineListAllowEmpty("nightSpawnAllowedDimensions", List.of("minecraft:overworld"), Config::validateResourceLocation);

    private static final ModConfigSpec.BooleanValue NIGHT_SPAWN_REQUIRE_DARKNESS = BUILDER
        .comment("When true, nighttime zombies only spawn in dark areas.")
        .define("nightSpawnRequireDarkness", false);

    private static final ModConfigSpec.IntValue NIGHT_SPAWN_MAX_LIGHT_LEVEL = BUILDER
        .comment("Maximum light level allowed when nightSpawnRequireDarkness is enabled.")
        .defineInRange("nightSpawnMaxLightLevel", 7, 0, 15);

    private static final ModConfigSpec.BooleanValue NIGHT_SPAWNED_ZOMBIES_IGNORE_SUN_BURN = BUILDER
        .comment("When true, nighttime spawned zombies will have their sun burning cleared.")
        .define("nightSpawnedZombiesIgnoreSunBurn", false);

    private static final ModConfigSpec.BooleanValue NIGHT_SPAWN_DEBUG_LOGS = BUILDER
        .comment("Enable debug logging for nighttime zombie spawning.")
        .define("nightSpawnDebugLogs", false);

    private static final ModConfigSpec.IntValue DAY_SPAWN_INTERVAL_TICKS = BUILDER
        .comment("How often Zlogic attempts a daytime zombie spawn wave.")
        .defineInRange("daySpawnIntervalTicks", 1200, 20, Integer.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue DAY_SPAWN_CHANCE = BUILDER
        .comment("Chance for a spawn wave to happen when the interval is reached.")
        .defineInRange("daySpawnChance", 0.35D, 0.0D, 1.0D);

    private static final ModConfigSpec.IntValue DAY_SPAWN_MIN_GROUP_SIZE = BUILDER
        .comment("Minimum number of zombies in a daytime spawn group.")
        .defineInRange("daySpawnMinGroupSize", 1, 1, 32);

    private static final ModConfigSpec.IntValue DAY_SPAWN_MAX_GROUP_SIZE = BUILDER
        .comment("Maximum number of zombies in a daytime spawn group.")
        .defineInRange("daySpawnMaxGroupSize", 3, 1, 32);

    private static final ModConfigSpec.IntValue DAY_SPAWN_RADIUS_MIN = BUILDER
        .comment("Minimum radius around the player used for daytime spawn attempts.")
        .defineInRange("daySpawnRadiusMin", 24, 1, 256);

    private static final ModConfigSpec.IntValue DAY_SPAWN_RADIUS_MAX = BUILDER
        .comment("Maximum radius around the player used for daytime spawn attempts.")
        .defineInRange("daySpawnRadiusMax", 48, 1, 512);

    private static final ModConfigSpec.IntValue DAY_SPAWN_MAX_NEARBY_ZOMBIES = BUILDER
        .comment("Maximum number of nearby zombies allowed before daytime spawn attempts stop.")
        .defineInRange("daySpawnMaxNearbyZombies", 8, 0, 1000);

    private static final ModConfigSpec.IntValue DAY_SPAWN_NEARBY_CHECK_RADIUS = BUILDER
        .comment("Radius used to count nearby zombies before and during daytime spawn attempts.")
        .defineInRange("daySpawnNearbyCheckRadius", 64, 1, 256);

    private static final ModConfigSpec.BooleanValue DAY_SPAWN_REQUIRE_SKY_ACCESS = BUILDER
        .comment("When true, daytime zombies only spawn in positions with direct sky access.")
        .define("daySpawnRequireSkyAccess", false);

    private static final ModConfigSpec.BooleanValue DAY_SPAWN_ALLOW_IN_DARK_AREAS_ONLY = BUILDER
        .comment("When true, daytime zombies only spawn in areas at or below the configured light level.")
        .define("daySpawnAllowInDarkAreasOnly", false);

    private static final ModConfigSpec.BooleanValue DAY_SPAWN_RESPECT_DO_MOB_SPAWNING = BUILDER
        .comment("When true, daytime zombie spawning respects the doMobSpawning gamerule.")
        .define("daySpawnRespectDoMobSpawning", true);

    private static final ModConfigSpec.IntValue DAY_SPAWN_MAX_LIGHT_LEVEL = BUILDER
        .comment("Maximum light level allowed when daySpawnAllowInDarkAreasOnly is enabled.")
        .defineInRange("daySpawnMaxLightLevel", 15, 0, 15);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> DAY_SPAWN_ALLOWED_DIMENSIONS = BUILDER
        .comment("Allowed dimensions for daytime zombie spawns.")
        .defineListAllowEmpty("daySpawnAllowedDimensions", List.of("minecraft:overworld"), Config::validateResourceLocation);

    private static final ModConfigSpec.BooleanValue ENABLE_ZOMBIE_AGGRESSION_SYSTEM = BUILDER
        .comment("Enable or disable the zombie aggression system.")
        .define("enableZombieAggressionSystem", true);

    private static final ModConfigSpec.BooleanValue AGGRESSION_AFFECTS_VANILLA_ZOMBIES = BUILDER
        .comment("When true, vanilla zombies and the other vanilla zombie family mobs are affected by the aggression system.")
        .define("aggressionAffectsVanillaZombies", true);

    private static final ModConfigSpec.BooleanValue AGGRESSION_AFFECTS_DAY_SPAWNED_ZOMBIES = BUILDER
        .comment("When true, zombies marked as day spawned by Zlogic are affected by the aggression system.")
        .define("aggressionAffectsDaySpawnedZombies", true);

    private static final ModConfigSpec.IntValue AGGRESSION_CHECK_INTERVAL_TICKS = BUILDER
        .comment("How often each zombie checks for players and nearby reinforcements.")
        .defineInRange("aggressionCheckIntervalTicks", 40, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue ZOMBIE_EXTRA_TARGET_RANGE = BUILDER
        .comment("Extra target range added on top of the zombie's follow range when searching for players.")
        .defineInRange("zombieExtraTargetRange", 16.0D, 0.0D, 256.0D);

    private static final ModConfigSpec.BooleanValue ZOMBIE_ALERT_NEARBY_ZOMBIES = BUILDER
        .comment("When true, zombies alert nearby zombies after acquiring a player target.")
        .define("zombieAlertNearbyZombies", true);

    private static final ModConfigSpec.DoubleValue ZOMBIE_ALERT_RADIUS = BUILDER
        .comment("Radius used to find nearby zombies to alert.")
        .defineInRange("zombieAlertRadius", 24.0D, 0.0D, 256.0D);

    private static final ModConfigSpec.IntValue ZOMBIE_ALERT_MAX_TARGETS = BUILDER
        .comment("Maximum number of nearby zombies alerted by a single target acquisition.")
        .defineInRange("zombieAlertMaxTargets", 8, 0, 64);

    private static final ModConfigSpec.BooleanValue ZOMBIE_ALERT_REQUIRES_LINE_OF_SIGHT = BUILDER
        .comment("When true, only nearby zombies with line of sight to the target or alerting zombie are alerted.")
        .define("zombieAlertRequiresLineOfSight", false);

    private static final ModConfigSpec.IntValue ZOMBIE_FORGET_TARGET_AFTER_TICKS = BUILDER
        .comment("How long a zombie keeps its current player target before re-evaluating it.")
        .defineInRange("zombieForgetTargetAfterTicks", 200, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue ZOMBIE_RETARGET_CHANCE = BUILDER
        .comment("Chance for a zombie to switch to another valid nearby player when re-evaluating targets.")
        .defineInRange("zombieRetargetChance", 0.25D, 0.0D, 1.0D);

    private static final ModConfigSpec.BooleanValue ZOMBIE_PREFER_NEAREST_PLAYER = BUILDER
        .comment("When true, zombies prefer the nearest valid player; otherwise they can pick randomly.")
        .define("zombiePreferNearestPlayer", true);

    private static final ModConfigSpec.BooleanValue ZLOGIC_SYSTEMS_AFFECT_ONLY_ZLOGIC_ZOMBIES = BUILDER
        .comment("When true, Zlogic systems only affect zombies marked as spawned by Zlogic.")
        .define("zlogicSystemsAffectOnlyZlogicZombies", false);

    private static final ModConfigSpec.BooleanValue ZLOGIC_TREAT_LEGACY_DAY_SPAWNED_AS_ZLOGIC = BUILDER
        .comment("When true, legacy day-spawned zombies are treated as Zlogic zombies.")
        .define("zlogicTreatLegacyDaySpawnedAsZlogic", true);

    private static final ModConfigSpec.BooleanValue ZLOGIC_DEBUG_ZOMBIE_MARKERS = BUILDER
        .comment("Enable debug logging for Zlogic zombie marker writes.")
        .define("zlogicDebugZombieMarkers", false);

    private static final ModConfigSpec.BooleanValue ENABLE_ZOMBIE_HORDE_CLIMB = BUILDER
        .comment("Enable or disable the Horde Climb system.")
        .define("enableZombieHordeClimb", true);

    private static final ModConfigSpec.BooleanValue HORDE_CLIMB_ONLY_ELIGIBLE_ZOMBIES = BUILDER
        .comment("When true, Horde Climb only affects zombies eligible for Zlogic systems.")
        .define("hordeClimbOnlyEligibleZombies", true);

    private static final ModConfigSpec.IntValue HORDE_CLIMB_MIN_ZOMBIES = BUILDER
        .comment("Minimum nearby zombies required to trigger Horde Climb.")
        .defineInRange("hordeClimbMinZombies", 3, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue HORDE_CLIMB_RADIUS = BUILDER
        .comment("Radius used to count nearby zombies for Horde Climb.")
        .defineInRange("hordeClimbRadius", 4.0D, 0.1D, 64.0D);

    private static final ModConfigSpec.DoubleValue HORDE_CLIMB_BASE_HEIGHT = BUILDER
        .comment("Base climb height when Horde Climb activates at the minimum zombie count.")
        .defineInRange("hordeClimbBaseHeight", 3.0D, 0.0D, 64.0D);

    private static final ModConfigSpec.DoubleValue HORDE_CLIMB_HEIGHT_PER_ZOMBIE = BUILDER
        .comment("Extra climb height added per zombie above the minimum threshold.")
        .defineInRange("hordeClimbHeightPerZombie", 1.0D, 0.0D, 64.0D);

    private static final ModConfigSpec.DoubleValue HORDE_CLIMB_MAX_HEIGHT = BUILDER
        .comment("Maximum climb height allowed for Horde Climb.")
        .defineInRange("hordeClimbMaxHeight", 12.0D, 0.0D, 64.0D);

    private static final ModConfigSpec.IntValue HORDE_CLIMB_COOLDOWN_TICKS = BUILDER
        .comment("Cooldown in ticks for a zombie after a Horde Climb impulse.")
        .defineInRange("hordeClimbCooldownTicks", 80, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue HORDE_CLIMB_OBSTACLE_CHECK_DISTANCE = BUILDER
        .comment("Distance in front of the zombie used to look for an obstacle.")
        .defineInRange("hordeClimbObstacleCheckDistance", 1.2D, 0.1D, 8.0D);

    private static final ModConfigSpec.IntValue HORDE_CLIMB_MIN_OBSTACLE_HEIGHT = BUILDER
        .comment("Minimum obstacle height required to trigger Horde Climb.")
        .defineInRange("hordeClimbMinObstacleHeight", 2, 1, 16);

    private static final ModConfigSpec.IntValue HORDE_CLIMB_MAX_OBSTACLE_SCAN_HEIGHT = BUILDER
        .comment("Maximum vertical blocks to scan when evaluating a climb obstacle.")
        .defineInRange("hordeClimbMaxObstacleScanHeight", 16, 1, 64);

    private static final ModConfigSpec.DoubleValue HORDE_CLIMB_VERTICAL_BOOST_BASE = BUILDER
        .comment("Base vertical boost applied when Horde Climb activates.")
        .defineInRange("hordeClimbVerticalBoostBase", 0.42D, 0.0D, 4.0D);

    private static final ModConfigSpec.DoubleValue HORDE_CLIMB_VERTICAL_BOOST_PER_HEIGHT = BUILDER
        .comment("Vertical boost added per block of climb height.")
        .defineInRange("hordeClimbVerticalBoostPerHeight", 0.08D, 0.0D, 4.0D);

    private static final ModConfigSpec.DoubleValue HORDE_CLIMB_FORWARD_BOOST = BUILDER
        .comment("Forward boost applied when Horde Climb triggers.")
        .defineInRange("hordeClimbForwardBoost", 0.45D, 0.0D, 4.0D);

    private static final ModConfigSpec.BooleanValue HORDE_CLIMB_REQUIRE_TARGET = BUILDER
        .comment("When true, Horde Climb only triggers if the zombie has a valid target.")
        .define("hordeClimbRequireTarget", true);

    private static final ModConfigSpec.BooleanValue HORDE_CLIMB_WORKS_AGAINST_PLAYERS = BUILDER
        .comment("When true, Horde Climb can trigger when the zombie targets a player.")
        .define("hordeClimbWorksAgainstPlayers", true);

    private static final ModConfigSpec.BooleanValue HORDE_CLIMB_WORKS_AGAINST_MACHINES = BUILDER
        .comment("When true, Horde Climb can trigger while the zombie targets a machine.")
        .define("hordeClimbWorksAgainstMachines", true);

    private static final ModConfigSpec.BooleanValue HORDE_CLIMB_PARTICLES = BUILDER
        .comment("Enable Horde Climb particles.")
        .define("hordeClimbParticles", true);

    private static final ModConfigSpec.BooleanValue HORDE_CLIMB_SOUNDS = BUILDER
        .comment("Enable Horde Climb sounds.")
        .define("hordeClimbSounds", true);

    private static final ModConfigSpec.BooleanValue HORDE_CLIMB_GROUP_VISUAL = BUILDER
        .comment("When true, nearby zombies contribute visual help during Horde Climb.")
        .define("hordeClimbGroupVisual", true);

    private static final ModConfigSpec.DoubleValue HORDE_CLIMB_ATTEMPT_CHANCE = BUILDER
        .comment("Chance for Horde Climb to attempt when all conditions are met.")
        .defineInRange("hordeClimbAttemptChance", 0.55D, 0.0D, 1.0D);

    private static final ModConfigSpec.IntValue HORDE_CLIMB_CHECK_INTERVAL_TICKS = BUILDER
        .comment("How often each zombie checks Horde Climb conditions.")
        .defineInRange("hordeClimbCheckIntervalTicks", 20, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.BooleanValue HORDE_CLIMB_DEBUG_LOGS = BUILDER
        .comment("Enable debug logging for Horde Climb.")
        .define("hordeClimbDebugLogs", false);

    private static final ModConfigSpec.BooleanValue ENABLE_HORDE_CLIMB_TARGET_ELEVATION_MODE = BUILDER
        .comment("When true, Horde Climb can trigger when the target is above the zombie.")
        .define("enableHordeClimbTargetElevationMode", true);

    private static final ModConfigSpec.DoubleValue HORDE_CLIMB_TARGET_MIN_Y_DIFFERENCE = BUILDER
        .comment("Minimum Y difference between zombie and target required for Target Elevation Mode.")
        .defineInRange("hordeClimbTargetMinYDifference", 2.0D, 0.0D, 64.0D);

    private static final ModConfigSpec.IntValue HORDE_CLIMB_TARGET_MIN_BLOCK_Y_DIFFERENCE = BUILDER
        .comment("Minimum block Y difference between zombie and target required for Target Elevation Mode.")
        .defineInRange("hordeClimbTargetMinBlockYDifference", 2, 0, 64);

    private static final ModConfigSpec.DoubleValue HORDE_CLIMB_TARGET_HORIZONTAL_RANGE = BUILDER
        .comment("Maximum horizontal distance to the target for Target Elevation Mode.")
        .defineInRange("hordeClimbTargetHorizontalRange", 6.0D, 0.0D, 64.0D);

    private static final ModConfigSpec.BooleanValue HORDE_CLIMB_TARGET_MODE_REQUIRE_OBSTACLE_BETWEEN = BUILDER
        .comment("When true, Target Elevation Mode requires an obstacle between zombie and target.")
        .define("hordeClimbTargetModeRequireObstacleBetween", true);

    private static final ModConfigSpec.BooleanValue HORDE_CLIMB_IGNORE_SMALL_TERRAIN_STEPS = BUILDER
        .comment("When true, small terrain steps do not count as Target Elevation Mode triggers.")
        .define("hordeClimbIgnoreSmallTerrainSteps", true);

    private static final ModConfigSpec.IntValue HORDE_CLIMB_TARGET_MODE_MIN_OBSTACLE_HEIGHT = BUILDER
        .comment("Minimum obstacle height required for Target Elevation Mode.")
        .defineInRange("hordeClimbTargetModeMinObstacleHeight", 2, 1, 64);

    private static final ModConfigSpec.DoubleValue HORDE_CLIMB_TARGET_HORIZONTAL_MIN_RANGE = BUILDER
        .comment("Minimum horizontal distance to the target for Target Elevation Mode.")
        .defineInRange("hordeClimbTargetHorizontalMinRange", 1.5D, 0.0D, 64.0D);

    private static final ModConfigSpec.BooleanValue HORDE_CLIMB_USE_TARGET_DIRECTION_FOR_BOOST = BUILDER
        .comment("When true, Horde Climb uses target direction for the boost instead of look direction.")
        .define("hordeClimbUseTargetDirectionForBoost", true);

    private static final ModConfigSpec.BooleanValue HORDE_CLIMB_LEADER_ONLY = BUILDER
        .comment("When true, only the zombie closest to the target attempts Horde Climb first.")
        .define("hordeClimbLeaderOnly", true);

    private static final ModConfigSpec.DoubleValue HORDE_CLIMB_LEADER_CHECK_RADIUS = BUILDER
        .comment("Radius used to find the Horde Climb leader zombie.")
        .defineInRange("hordeClimbLeaderCheckRadius", 5.0D, 0.0D, 64.0D);

    private static final ModConfigSpec.BooleanValue HORDE_CLIMB_ALLOW_PILLAR_CLIMB = BUILDER
        .comment("When true, Horde Climb can trigger on narrow pillars and small columns.")
        .define("hordeClimbAllowPillarClimb", true);

    private static final ModConfigSpec.IntValue HORDE_CLIMB_PILLAR_MIN_HEIGHT = BUILDER
        .comment("Minimum pillar height required to consider a narrow climbable column.")
        .defineInRange("hordeClimbPillarMinHeight", 1, 1, 64);

    private static final ModConfigSpec.BooleanValue HORDE_CLIMB_VERBOSE_DEBUG = BUILDER
        .comment("Enable extra Horde Climb debug reasons.")
        .define("hordeClimbVerboseDebug", false);

    private static final ModConfigSpec.BooleanValue ENABLE_HORDE_SIEGE_CLIMB = BUILDER
        .comment("When true, strong Horde Climb uses the stricter siege activation rules.")
        .define("enableHordeSiegeClimb", true);

    private static final ModConfigSpec.IntValue HORDE_SIEGE_CLIMB_MIN_ZOMBIES = BUILDER
        .comment("Minimum clustered zombies required to trigger Siege Climb.")
        .defineInRange("hordeSiegeClimbMinZombies", 5, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue HORDE_SIEGE_CLIMB_GROUP_RADIUS = BUILDER
        .comment("Radius used to count zombies participating in the same siege cluster.")
        .defineInRange("hordeSiegeClimbGroupRadius", 4.0D, 0.1D, 64.0D);

    private static final ModConfigSpec.DoubleValue HORDE_SIEGE_CLIMB_MAX_TARGET_DISTANCE = BUILDER
        .comment("Maximum target distance allowed for Siege Climb.")
        .defineInRange("hordeSiegeClimbMaxTargetDistance", 8.0D, 0.0D, 64.0D);

    private static final ModConfigSpec.DoubleValue HORDE_SIEGE_CLIMB_MAX_OBSTACLE_DISTANCE = BUILDER
        .comment("Maximum distance from the zombie to the obstacle used for Siege Climb.")
        .defineInRange("hordeSiegeClimbMaxObstacleDistance", 2.0D, 0.1D, 16.0D);

    private static final ModConfigSpec.IntValue HORDE_SIEGE_CLIMB_MIN_OBSTACLE_HEIGHT = BUILDER
        .comment("Minimum wall height required for Siege Climb.")
        .defineInRange("hordeSiegeClimbMinObstacleHeight", 2, 1, 16);

    private static final ModConfigSpec.IntValue HORDE_SIEGE_CLIMB_MAX_OBSTACLE_HEIGHT = BUILDER
        .comment("Maximum wall height that Siege Climb will attempt directly.")
        .defineInRange("hordeSiegeClimbMaxObstacleHeight", 4, 1, 16);

    private static final ModConfigSpec.BooleanValue HORDE_SIEGE_CLIMB_REQUIRE_OBSTACLE_IN_FRONT = BUILDER
        .comment("When true, Siege Climb only triggers if a real obstacle is directly in front of the zombie.")
        .define("hordeSiegeClimbRequireObstacleInFront", true);

    private static final ModConfigSpec.BooleanValue HORDE_SIEGE_CLIMB_REQUIRE_TARGET_ABOVE_OR_BEHIND_OBSTACLE = BUILDER
        .comment("When true, Siege Climb requires the target to be above or behind the obstacle.")
        .define("hordeSiegeClimbRequireTargetAboveOrBehindObstacle", true);

    private static final ModConfigSpec.IntValue HORDE_SIEGE_CLIMB_COOLDOWN_TICKS = BUILDER
        .comment("Individual cooldown applied after a Siege Climb activation.")
        .defineInRange("hordeSiegeClimbCooldownTicks", 80, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue HORDE_SIEGE_CLIMB_AREA_COOLDOWN_TICKS = BUILDER
        .comment("Cooldown shared by Siege Climb activations in the same area.")
        .defineInRange("hordeSiegeClimbAreaCooldownTicks", 80, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue HORDE_SIEGE_CLIMB_AREA_COOLDOWN_RADIUS = BUILDER
        .comment("Area radius used to share Siege Climb cooldowns.")
        .defineInRange("hordeSiegeClimbAreaCooldownRadius", 6.0D, 0.1D, 64.0D);

    private static final ModConfigSpec.DoubleValue HORDE_SIEGE_CLIMB_VERTICAL_BOOST_BASE = BUILDER
        .comment("Base vertical boost used by Siege Climb.")
        .defineInRange("hordeSiegeClimbVerticalBoostBase", 0.42D, 0.0D, 4.0D);

    private static final ModConfigSpec.DoubleValue HORDE_SIEGE_CLIMB_VERTICAL_BOOST_PER_HEIGHT = BUILDER
        .comment("Extra vertical boost applied per obstacle block during Siege Climb.")
        .defineInRange("hordeSiegeClimbVerticalBoostPerHeight", 0.12D, 0.0D, 4.0D);

    private static final ModConfigSpec.DoubleValue HORDE_SIEGE_CLIMB_FORWARD_BOOST = BUILDER
        .comment("Forward boost applied by Siege Climb.")
        .defineInRange("hordeSiegeClimbForwardBoost", 0.18D, 0.0D, 4.0D);

    private static final ModConfigSpec.BooleanValue HORDE_SIEGE_CLIMB_DEBUG_LOGS = BUILDER
        .comment("Enable debug logging for Siege Climb decisions.")
        .define("hordeSiegeClimbDebugLogs", false);

    private static final ModConfigSpec.BooleanValue HORDE_CLIMB_SUPPRESS_ON_NATURAL_SLOPES = BUILDER
        .comment("When true, strong Horde Climb is suppressed on gradual natural slopes.")
        .define("hordeClimbSuppressOnNaturalSlopes", true);

    private static final ModConfigSpec.IntValue HORDE_CLIMB_NATURAL_SLOPE_MAX_STEP_HEIGHT = BUILDER
        .comment("Maximum rise per sampled step still considered a natural slope.")
        .defineInRange("hordeClimbNaturalSlopeMaxStepHeight", 1, 0, 8);

    private static final ModConfigSpec.IntValue HORDE_CLIMB_NATURAL_SLOPE_SCAN_DISTANCE = BUILDER
        .comment("How many forward blocks are sampled when checking for natural slopes.")
        .defineInRange("hordeClimbNaturalSlopeScanDistance", 4, 1, 16);

    private static final ModConfigSpec.BooleanValue HORDE_CLIMB_REQUIRE_WALL_LIKE_OBSTACLE = BUILDER
        .comment("When true, strong Horde Climb requires a wall-like obstacle instead of a terrain bump.")
        .define("hordeClimbRequireWallLikeObstacle", true);

    private static final ModConfigSpec.BooleanValue ENABLE_ZOMBIE_BARRIER_BREAK = BUILDER
        .comment("Enable or disable the Zombie Barrier Break system.")
        .define("enableZombieBarrierBreak", true);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_ONLY_ELIGIBLE_ZOMBIES = BUILDER
        .comment("When true, only zombies eligible for Zlogic systems can break barriers.")
        .define("barrierBreakOnlyEligibleZombies", true);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_REQUIRE_FRONT_SIDE = BUILDER
        .comment("When true, zombies must be on the correct attack side of the barrier.")
        .define("barrierBreakRequireFrontSide", true);

    private static final ModConfigSpec.DoubleValue BARRIER_BREAK_FRONT_WIDTH = BUILDER
        .comment("Lateral tolerance used when counting zombies in front of a barrier.")
        .defineInRange("barrierBreakFrontWidth", 1.6D, 0.1D, 16.0D);

    private static final ModConfigSpec.DoubleValue BARRIER_BREAK_FRONT_DEPTH = BUILDER
        .comment("Depth used when counting zombies in front of a barrier.")
        .defineInRange("barrierBreakFrontDepth", 2.0D, 0.1D, 16.0D);

    private static final ModConfigSpec.DoubleValue BARRIER_BREAK_MAX_ZOMBIE_DISTANCE_TO_BARRIER = BUILDER
        .comment("Maximum distance a zombie may be from the barrier and still count.")
        .defineInRange("barrierBreakMaxZombieDistanceToBarrier", 2.4D, 0.1D, 16.0D);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_REQUIRE_TARGET_BEHIND_BARRIER = BUILDER
        .comment("When true, the target must be on the other side of the barrier.")
        .define("barrierBreakRequireTargetBehindBarrier", true);

    private static final ModConfigSpec.DoubleValue BARRIER_BREAK_MIN_PATH_ALIGNMENT = BUILDER
        .comment("Minimum alignment between zombie->barrier and zombie->target vectors.")
        .defineInRange("barrierBreakMinPathAlignment", 0.35D, -1.0D, 1.0D);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_REJECT_ZOMBIES_BEHIND_BARRIER = BUILDER
        .comment("When true, zombies behind the barrier are rejected.")
        .define("barrierBreakRejectZombiesBehindBarrier", true);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_SIDE_DEBUG = BUILDER
        .comment("Enable detailed debug for barrier side validation.")
        .define("barrierBreakSideDebug", false);

    private static final ModConfigSpec.IntValue BARRIER_BREAK_MIN_ZOMBIES = BUILDER
        .comment("Minimum nearby zombies required to allow barrier breaking.")
        .defineInRange("barrierBreakMinZombies", 2, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue BARRIER_BREAK_ZOMBIE_RADIUS = BUILDER
        .comment("Radius used to count zombies near the barrier.")
        .defineInRange("barrierBreakZombieRadius", 2.0D, 0.1D, 16.0D);

    private static final ModConfigSpec.IntValue BARRIER_BREAK_CHECK_INTERVAL_TICKS = BUILDER
        .comment("How often each zombie checks for barrier break opportunities.")
        .defineInRange("barrierBreakCheckIntervalTicks", 20, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue BARRIER_BREAK_ZOMBIE_COOLDOWN_TICKS = BUILDER
        .comment("Cooldown in ticks after a zombie attempts a barrier break.")
        .defineInRange("barrierBreakZombieCooldownTicks", 40, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue BARRIER_BREAK_BLOCK_COOLDOWN_TICKS = BUILDER
        .comment("Cooldown in ticks after a barrier takes damage.")
        .defineInRange("barrierBreakBlockCooldownTicks", 20, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_REQUIRE_TARGET = BUILDER
        .comment("When true, zombies must have a valid target to break barriers.")
        .define("barrierBreakRequireTarget", true);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_WORKS_AGAINST_PLAYERS = BUILDER
        .comment("When true, the system works against player targets.")
        .define("barrierBreakWorksAgainstPlayers", true);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_WORKS_AGAINST_MACHINES = BUILDER
        .comment("When true, the system works against machine targets.")
        .define("barrierBreakWorksAgainstMachines", true);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_WOODEN_DOORS = BUILDER
        .comment("When true, wooden doors can be broken.")
        .define("barrierBreakWoodenDoors", true);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_WOODEN_TRAPDOORS = BUILDER
        .comment("When true, wooden trapdoors can be broken.")
        .define("barrierBreakWoodenTrapdoors", false);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_WOODEN_FENCE_GATES = BUILDER
        .comment("When true, wooden fence gates can be broken.")
        .define("barrierBreakWoodenFenceGates", false);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_GLASS_BLOCKS = BUILDER
        .comment("When true, glass blocks can be broken.")
        .define("barrierBreakGlassBlocks", true);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_GLASS_PANES = BUILDER
        .comment("When true, glass panes can be broken.")
        .define("barrierBreakGlassPanes", true);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_STAINED_GLASS = BUILDER
        .comment("When true, stained glass blocks can be broken.")
        .define("barrierBreakStainedGlass", true);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_STAINED_GLASS_PANES = BUILDER
        .comment("When true, stained glass panes can be broken.")
        .define("barrierBreakStainedGlassPanes", true);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_TINTED_GLASS = BUILDER
        .comment("When true, tinted glass can be broken.")
        .define("barrierBreakTintedGlass", false);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_RESPECT_BLOCK_DENYLIST = BUILDER
        .comment("When true, the barrier break denylist is always respected.")
        .define("barrierBreakRespectBlockDenylist", true);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> BARRIER_BREAK_BLOCK_DENYLIST = BUILDER
        .comment("Blocks that must never be broken by the barrier break system.")
        .defineListAllowEmpty("barrierBreakBlockDenylist", List.of("minecraft:tinted_glass"), Config::validateResourceLocation);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> BARRIER_BREAK_EXTRA_BLOCK_ALLOWLIST = BUILDER
        .comment("Extra blocks that the user may allow to be broken.")
        .defineListAllowEmpty("barrierBreakExtraBlockAllowlist", List.of(), Config::validateResourceLocation);

    private static final ModConfigSpec.IntValue BARRIER_BREAK_DAMAGE_PER_HIT = BUILDER
        .comment("Damage dealt to a barrier when a valid horde hit occurs.")
        .defineInRange("barrierBreakDamagePerHit", 1, 1, 64);

    private static final ModConfigSpec.IntValue BARRIER_BREAK_WOODEN_DOOR_DURABILITY = BUILDER
        .comment("Durability for wooden doors.")
        .defineInRange("barrierBreakWoodenDoorDurability", 6, 1, 256);

    private static final ModConfigSpec.IntValue BARRIER_BREAK_GLASS_DURABILITY = BUILDER
        .comment("Durability for glass blocks.")
        .defineInRange("barrierBreakGlassDurability", 2, 1, 256);

    private static final ModConfigSpec.IntValue BARRIER_BREAK_GLASS_PANE_DURABILITY = BUILDER
        .comment("Durability for glass panes.")
        .defineInRange("barrierBreakGlassPaneDurability", 1, 1, 256);

    private static final ModConfigSpec.IntValue BARRIER_BREAK_EXTRA_BLOCK_DURABILITY = BUILDER
        .comment("Default durability for allowlisted extra blocks.")
        .defineInRange("barrierBreakExtraBlockDurability", 4, 1, 256);

    private static final ModConfigSpec.IntValue BARRIER_BREAK_DAMAGE_MEMORY_TICKS = BUILDER
        .comment("How long accumulated barrier damage is remembered if the barrier is not hit again.")
        .defineInRange("barrierBreakDamageMemoryTicks", 200, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_ACTUALLY_BREAK_BLOCKS = BUILDER
        .comment("When true, the barrier block is removed when durability reaches zero.")
        .define("barrierBreakActuallyBreakBlocks", true);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_DROP_BLOCKS = BUILDER
        .comment("When true, broken blocks drop as normal.")
        .define("barrierBreakDropBlocks", false);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_PARTICLES = BUILDER
        .comment("Enable barrier break particles.")
        .define("barrierBreakParticles", true);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_SOUNDS = BUILDER
        .comment("Enable barrier break sounds.")
        .define("barrierBreakSounds", true);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_DEBUG_LOGS = BUILDER
        .comment("Enable debug logging for barrier break.")
        .define("barrierBreakDebugLogs", false);

    private static final ModConfigSpec.BooleanValue BARRIER_BREAK_VERBOSE_DEBUG = BUILDER
        .comment("Enable verbose debug output for barrier break.")
        .define("barrierBreakVerboseDebug", false);

    private static final ModConfigSpec.BooleanValue DISABLE_VANILLA_MONSTER_SPAWNS = BUILDER
        .comment("When true, vanilla monster spawning can be blocked by type-specific flags.")
        .define("disableVanillaMonsterSpawns", false);

    private static final ModConfigSpec.BooleanValue DISABLE_VANILLA_MONSTER_SPAWNS_ONLY_IN_OVERWORLD = BUILDER
        .comment("When true, vanilla monster spawning is only blocked in the Overworld.")
        .define("disableVanillaMonsterSpawnsOnlyInOverworld", true);

    private static final ModConfigSpec.BooleanValue DISABLE_ALL_NATURAL_HOSTILE_MOB_SPAWNS = BUILDER
        .comment("When true, all natural hostile mob spawns are blocked unless explicitly allowed.")
        .define("disableAllNaturalHostileMobSpawns", false);

    private static final ModConfigSpec.BooleanValue DISABLE_VANILLA_ZOMBIE_SPAWNS = BUILDER
        .comment("When true, natural vanilla zombie family spawns are blocked.")
        .define("disableVanillaZombieSpawns", true);

    private static final ModConfigSpec.BooleanValue DISABLE_VANILLA_SKELETON_SPAWNS = BUILDER
        .comment("When true, natural vanilla skeleton-family spawns are blocked.")
        .define("disableVanillaSkeletonSpawns", true);

    private static final ModConfigSpec.BooleanValue DISABLE_VANILLA_CREEPER_SPAWNS = BUILDER
        .comment("When true, natural vanilla creeper spawns are blocked.")
        .define("disableVanillaCreeperSpawns", true);

    private static final ModConfigSpec.BooleanValue DISABLE_VANILLA_SPIDER_SPAWNS = BUILDER
        .comment("When true, natural vanilla spider and cave spider spawns are blocked.")
        .define("disableVanillaSpiderSpawns", true);

    private static final ModConfigSpec.BooleanValue DISABLE_VANILLA_ENDERMAN_SPAWNS = BUILDER
        .comment("When true, natural vanilla enderman spawns are blocked.")
        .define("disableVanillaEndermanSpawns", false);

    private static final ModConfigSpec.BooleanValue DISABLE_VANILLA_SLIME_SPAWNS = BUILDER
        .comment("When true, natural vanilla slime spawns are blocked.")
        .define("disableVanillaSlimeSpawns", false);

    private static final ModConfigSpec.BooleanValue DISABLE_VANILLA_WITCH_SPAWNS = BUILDER
        .comment("When true, natural vanilla witch spawns are blocked.")
        .define("disableVanillaWitchSpawns", true);

    private static final ModConfigSpec.BooleanValue DISABLE_VANILLA_PILLAGER_SPAWNS = BUILDER
        .comment("When true, natural vanilla pillager spawns are blocked.")
        .define("disableVanillaPillagerSpawns", false);

    private static final ModConfigSpec.BooleanValue ALLOW_SPAWNER_MONSTER_SPAWNS = BUILDER
        .comment("When true, monster spawns from spawners are allowed.")
        .define("allowSpawnerMonsterSpawns", true);

    private static final ModConfigSpec.BooleanValue ALLOW_STRUCTURE_MONSTER_SPAWNS = BUILDER
        .comment("When true, monster spawns from structures are allowed.")
        .define("allowStructureMonsterSpawns", true);

    private static final ModConfigSpec.BooleanValue ALLOW_ZLOGIC_SPAWNED_MONSTERS = BUILDER
        .comment("When true, monsters spawned by Zlogic are always allowed.")
        .define("allowZlogicSpawnedMonsters", true);

    private static final ModConfigSpec.BooleanValue VANILLA_SPAWN_BLOCK_DEBUG_LOGS = BUILDER
        .comment("Enable debug logging for vanilla monster spawn blocking.")
        .define("vanillaSpawnBlockDebugLogs", false);

    private static final ModConfigSpec.BooleanValue ENABLE_PERFORMANCE_DEBUG_LOGS = BUILDER
        .comment("Enable lightweight performance summary logs.")
        .define("enablePerformanceDebugLogs", false);

    private static final ModConfigSpec.IntValue PERFORMANCE_DEBUG_INTERVAL_TICKS = BUILDER
        .comment("How often performance summary logs are emitted.")
        .defineInRange("performanceDebugIntervalTicks", 200, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean enableNightZombieBuffs;
    public static double nightSpeedMultiplier;
    public static double nightAttackDamageBonus;
    public static double nightFollowRangeBonus;
    public static double nightArmorBonus;
    public static boolean affectOnlyZombies;
    public static boolean debugLogs;
    public static boolean enableNoiseSystem;
    public static int noiseCheckIntervalTicks;
    public static int noiseMemoryTicks;
    public static int noiseMaxEventsPerLevel;
    public static double zombieHearNoiseRadiusMultiplier;
    public static boolean zombieMoveToNoise;
    public static boolean zombieTargetPlayerNearNoise;
    public static double zombieNoiseTargetPlayerRadius;
    public static double zombieNoiseAlertRadius;
    public static int zombieNoiseAlertMaxZombies;
    public static boolean zombieNoiseRequiresLineOfSight;
    public static double zombieNoiseRetargetChance;
    public static boolean zombieNoiseDebugLogs;
    public static boolean enableZombieBaseDamageSystem;
    public static boolean zombieBaseDamageAffectsVanillaZombies;
    public static boolean zombieBaseDamageAffectsDaySpawnedZombies;
    public static int zombieBaseDamageCheckIntervalTicks;
    public static double zombieBaseDamageBonusEasy;
    public static double zombieBaseDamageBonusNormal;
    public static double zombieBaseDamageBonusHard;
    public static boolean zombieBaseDamageUseMultiplier;
    public static double zombieBaseDamageMultiplierEasy;
    public static double zombieBaseDamageMultiplierNormal;
    public static double zombieBaseDamageMultiplierHard;
    public static boolean zombieBaseDamageDebugLogs;
    public static boolean enableSurvivalDaysScaling;
    public static String survivalScalingMode;
    public static boolean survivalScalingApplyOnSpawn;
    public static int survivalScalingStartDay;
    public static boolean survivalScalingUseNearestPlayer;
    public static double survivalScalingRadius;
    public static int survivalScalingCheckIntervalTicks;
    public static int survivalScalingRecheckIntervalTicks;
    public static int survivalScalingMaxLevel;
    public static int survivalScalingLevelCap;
    public static double survivalScalingLevelsPerDay;
    public static boolean survivalScalingStoreLevelOnMob;
    public static boolean survivalScalingReapplyMissingModifiers;
    public static boolean survivalScalingDisableEntityTickHeavyLogic;
    public static double survivalScalingDamagePerDay;
    public static double survivalScalingSpeedPerDay;
    public static double survivalScalingFollowRangePerDay;
    public static double survivalScalingArmorPerDay;
    public static double survivalScalingHealthPerDay;
    public static double survivalScalingMaxDamageMultiplier;
    public static double survivalScalingMaxSpeedMultiplier;
    public static double survivalScalingMaxFollowRangeMultiplier;
    public static double survivalScalingMaxArmorMultiplier;
    public static double survivalScalingMaxHealthMultiplier;
    public static boolean survivalScalingDebugLogs;
    public static boolean enableThreatLevelScaling;
    public static String threatScalingMode;
    public static boolean threatScalingApplyOnSpawn;
    public static int threatScalingRecheckIntervalTicks;
    public static boolean threatScalingStoreOnMob;
    public static boolean threatScalingDebugLogs;
    public static int threatScalingFinalLevelCap;
    public static int threatScalingMinimumLevel;
    public static boolean threatWorldDayEnabled;
    public static int threatWorldDayStartDay;
    public static double threatWorldDayLevelsPerDay;
    public static int threatWorldDayMaxLevel;
    public static boolean threatDifficultyEnabled;
    public static int threatDifficultyEasyBonus;
    public static int threatDifficultyNormalBonus;
    public static int threatDifficultyHardBonus;
    public static boolean threatDistanceEnabled;
    public static int threatDistanceStartBlocks;
    public static int threatDistanceLevelsPer1000Blocks;
    public static int threatDistanceMaxBonus;
    public static boolean threatRandomVarianceEnabled;
    public static int threatRandomVarianceMin;
    public static int threatRandomVarianceMax;
    public static boolean threatNearestPlayerEnabled;
    public static double threatNearestPlayerRadius;
    public static boolean threatNearestPlayerSurvivalDaysEnabled;
    public static int threatNearestPlayerMaxBonus;
    public static double threatDamagePerLevel;
    public static double threatSpeedPerLevel;
    public static double threatFollowRangePerLevel;
    public static double threatArmorPerLevel;
    public static double threatHealthPerLevel;
    public static double threatMaxDamageMultiplier;
    public static double threatMaxSpeedMultiplier;
    public static double threatMaxFollowRangeMultiplier;
    public static double threatMaxArmorMultiplier;
    public static double threatMaxHealthMultiplier;
    public static boolean enableMicroTechCompat;
    public static boolean microTechMachineNoiseEnabled;
    public static int microTechMachineNoiseIntervalTicks;
    public static int microTechMachineScanIntervalTicks;
    public static int microTechMachineMaxBlocksPerScan;
    public static double microTechMachineNoiseRadius;
    public static int microTechMachineNoiseDurationTicks;
    public static boolean microTechOnlyRunningMachinesMakeNoise;
    public static boolean microTechDebugLogs;
    public static boolean enableZombieMachineAttacks;
    public static boolean zombieMachineAttackUseDirectDetection;
    public static boolean zombieMachineAttackOnlyMicroTech;
    public static boolean zombieMachineAttackRequireNoise;
    public static double zombieMachineAttackRange;
    public static int zombieMachineAttackCooldownTicks;
    public static int zombieMachineDamagePerHit;
    public static int machineDurabilityDefault;
    public static int machineDurabilityEnergyConverterT1;
    public static int machineDurabilityEvoTable;
    public static int machineDurabilityBatteryT1;
    public static int machineDurabilityBatteryT2;
    public static boolean zombieMachineBreakBlocks;
    public static boolean zombieMachineDropBlock;
    public static boolean zombieMachineAttackOnlyActiveMachines;
    public static boolean zombieMachineAttackDebugLogs;
    public static boolean enableDirectMachineAttraction;
    public static double directMachineAttractionRadius;
    public static int directMachineAttractionCheckIntervalTicks;
    public static int directMachineAttractionMaxScanBlocksPerZombie;
    public static boolean directMachineAttractionOnlyActiveMachines;
    public static boolean directMachineAttractionOnlyMicroTech;
    public static double directMachineAttractionNavigationSpeed;
    public static double directMachineIgnoreMachineIfPlayerTargetWithin;
    public static int directMachineAttractionRepathIntervalTicks;
    public static int zombieNoiseRepathIntervalTicks;
    public static double debugZombieCommandFallbackRadius;
    public static boolean daySpawnedZombiesIgnoreSunBurn;
    public static boolean enableDayZombieSpawns;
    public static boolean enableNightZombieSpawns;
    public static int nightSpawnIntervalTicks;
    public static double nightSpawnChance;
    public static int nightSpawnMinGroupSize;
    public static int nightSpawnMaxGroupSize;
    public static int nightSpawnRadiusMin;
    public static int nightSpawnRadiusMax;
    public static int nightSpawnMaxNearbyZombies;
    public static int nightSpawnNearbyCheckRadius;
    public static boolean nightSpawnOnlyAtNight;
    public static boolean nightSpawnAllowDuringThunder;
    public static boolean nightSpawnRespectDoMobSpawning;
    public static List<? extends String> nightSpawnAllowedDimensions;
    public static boolean nightSpawnRequireDarkness;
    public static int nightSpawnMaxLightLevel;
    public static boolean nightSpawnedZombiesIgnoreSunBurn;
    public static boolean nightSpawnDebugLogs;
    public static int daySpawnIntervalTicks;
    public static double daySpawnChance;
    public static int daySpawnMinGroupSize;
    public static int daySpawnMaxGroupSize;
    public static int daySpawnRadiusMin;
    public static int daySpawnRadiusMax;
    public static int daySpawnMaxNearbyZombies;
    public static int daySpawnNearbyCheckRadius;
    public static boolean daySpawnRequireSkyAccess;
    public static boolean daySpawnAllowInDarkAreasOnly;
    public static boolean daySpawnRespectDoMobSpawning;
    public static int daySpawnMaxLightLevel;
    public static List<? extends String> daySpawnAllowedDimensions;
    public static boolean enableZombieAggressionSystem;
    public static boolean aggressionAffectsVanillaZombies;
    public static boolean aggressionAffectsDaySpawnedZombies;
    public static int aggressionCheckIntervalTicks;
    public static double zombieExtraTargetRange;
    public static boolean zombieAlertNearbyZombies;
    public static double zombieAlertRadius;
    public static int zombieAlertMaxTargets;
    public static boolean zombieAlertRequiresLineOfSight;
    public static int zombieForgetTargetAfterTicks;
    public static double zombieRetargetChance;
    public static boolean zombiePreferNearestPlayer;
    public static boolean zlogicSystemsAffectOnlyZlogicZombies;
    public static boolean zlogicTreatLegacyDaySpawnedAsZlogic;
    public static boolean zlogicDebugZombieMarkers;
    public static boolean enableZombieHordeClimb;
    public static boolean hordeClimbOnlyEligibleZombies;
    public static int hordeClimbMinZombies;
    public static double hordeClimbRadius;
    public static double hordeClimbBaseHeight;
    public static double hordeClimbHeightPerZombie;
    public static double hordeClimbMaxHeight;
    public static int hordeClimbCooldownTicks;
    public static double hordeClimbObstacleCheckDistance;
    public static int hordeClimbMinObstacleHeight;
    public static int hordeClimbMaxObstacleScanHeight;
    public static double hordeClimbVerticalBoostBase;
    public static double hordeClimbVerticalBoostPerHeight;
    public static double hordeClimbForwardBoost;
    public static boolean hordeClimbRequireTarget;
    public static boolean hordeClimbWorksAgainstPlayers;
    public static boolean hordeClimbWorksAgainstMachines;
    public static boolean hordeClimbParticles;
    public static boolean hordeClimbSounds;
    public static boolean hordeClimbGroupVisual;
    public static double hordeClimbAttemptChance;
    public static int hordeClimbCheckIntervalTicks;
    public static boolean hordeClimbDebugLogs;
    public static boolean enableHordeClimbTargetElevationMode;
    public static double hordeClimbTargetMinYDifference;
    public static int hordeClimbTargetMinBlockYDifference;
    public static double hordeClimbTargetHorizontalRange;
    public static boolean hordeClimbTargetModeRequireObstacleBetween;
    public static boolean hordeClimbIgnoreSmallTerrainSteps;
    public static int hordeClimbTargetModeMinObstacleHeight;
    public static double hordeClimbTargetHorizontalMinRange;
    public static boolean hordeClimbUseTargetDirectionForBoost;
    public static boolean hordeClimbLeaderOnly;
    public static double hordeClimbLeaderCheckRadius;
    public static boolean hordeClimbAllowPillarClimb;
    public static int hordeClimbPillarMinHeight;
    public static boolean hordeClimbVerboseDebug;
    public static boolean enableHordeSiegeClimb;
    public static int hordeSiegeClimbMinZombies;
    public static double hordeSiegeClimbGroupRadius;
    public static double hordeSiegeClimbMaxTargetDistance;
    public static double hordeSiegeClimbMaxObstacleDistance;
    public static int hordeSiegeClimbMinObstacleHeight;
    public static int hordeSiegeClimbMaxObstacleHeight;
    public static boolean hordeSiegeClimbRequireObstacleInFront;
    public static boolean hordeSiegeClimbRequireTargetAboveOrBehindObstacle;
    public static int hordeSiegeClimbCooldownTicks;
    public static int hordeSiegeClimbAreaCooldownTicks;
    public static double hordeSiegeClimbAreaCooldownRadius;
    public static double hordeSiegeClimbVerticalBoostBase;
    public static double hordeSiegeClimbVerticalBoostPerHeight;
    public static double hordeSiegeClimbForwardBoost;
    public static boolean hordeSiegeClimbDebugLogs;
    public static boolean hordeClimbSuppressOnNaturalSlopes;
    public static int hordeClimbNaturalSlopeMaxStepHeight;
    public static int hordeClimbNaturalSlopeScanDistance;
    public static boolean hordeClimbRequireWallLikeObstacle;
    public static boolean enableZombieBarrierBreak;
    public static boolean barrierBreakOnlyEligibleZombies;
    public static boolean barrierBreakRequireFrontSide;
    public static double barrierBreakFrontWidth;
    public static double barrierBreakFrontDepth;
    public static double barrierBreakMaxZombieDistanceToBarrier;
    public static boolean barrierBreakRequireTargetBehindBarrier;
    public static double barrierBreakMinPathAlignment;
    public static boolean barrierBreakRejectZombiesBehindBarrier;
    public static boolean barrierBreakSideDebug;
    public static int barrierBreakMinZombies;
    public static double barrierBreakZombieRadius;
    public static int barrierBreakCheckIntervalTicks;
    public static int barrierBreakZombieCooldownTicks;
    public static int barrierBreakBlockCooldownTicks;
    public static boolean barrierBreakRequireTarget;
    public static boolean barrierBreakWorksAgainstPlayers;
    public static boolean barrierBreakWorksAgainstMachines;
    public static boolean barrierBreakWoodenDoors;
    public static boolean barrierBreakWoodenTrapdoors;
    public static boolean barrierBreakWoodenFenceGates;
    public static boolean barrierBreakGlassBlocks;
    public static boolean barrierBreakGlassPanes;
    public static boolean barrierBreakStainedGlass;
    public static boolean barrierBreakStainedGlassPanes;
    public static boolean barrierBreakTintedGlass;
    public static boolean barrierBreakRespectBlockDenylist;
    public static List<? extends String> barrierBreakBlockDenylist;
    public static List<? extends String> barrierBreakExtraBlockAllowlist;
    public static int barrierBreakDamagePerHit;
    public static int barrierBreakWoodenDoorDurability;
    public static int barrierBreakGlassDurability;
    public static int barrierBreakGlassPaneDurability;
    public static int barrierBreakExtraBlockDurability;
    public static int barrierBreakDamageMemoryTicks;
    public static boolean barrierBreakActuallyBreakBlocks;
    public static boolean barrierBreakDropBlocks;
    public static boolean barrierBreakParticles;
    public static boolean barrierBreakSounds;
    public static boolean barrierBreakDebugLogs;
    public static boolean barrierBreakVerboseDebug;
    public static boolean disableVanillaMonsterSpawns;
    public static boolean disableVanillaMonsterSpawnsOnlyInOverworld;
    public static boolean disableAllNaturalHostileMobSpawns;
    public static boolean disableVanillaZombieSpawns;
    public static boolean disableVanillaSkeletonSpawns;
    public static boolean disableVanillaCreeperSpawns;
    public static boolean disableVanillaSpiderSpawns;
    public static boolean disableVanillaEndermanSpawns;
    public static boolean disableVanillaSlimeSpawns;
    public static boolean disableVanillaWitchSpawns;
    public static boolean disableVanillaPillagerSpawns;
    public static boolean allowSpawnerMonsterSpawns;
    public static boolean allowStructureMonsterSpawns;
    public static boolean allowZlogicSpawnedMonsters;
    public static boolean vanillaSpawnBlockDebugLogs;
    public static boolean enablePerformanceDebugLogs;
    public static int performanceDebugIntervalTicks;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        enableNightZombieBuffs = ENABLE_NIGHT_ZOMBIE_BUFFS.get();
        nightSpeedMultiplier = NIGHT_SPEED_MULTIPLIER.get();
        nightAttackDamageBonus = NIGHT_ATTACK_DAMAGE_BONUS.get();
        nightFollowRangeBonus = NIGHT_FOLLOW_RANGE_BONUS.get();
        nightArmorBonus = NIGHT_ARMOR_BONUS.get();
        affectOnlyZombies = AFFECT_ONLY_ZOMBIES.get();
        debugLogs = DEBUG_LOGS.get();
        enableNoiseSystem = ENABLE_NOISE_SYSTEM.get();
        noiseCheckIntervalTicks = NOISE_CHECK_INTERVAL_TICKS.get();
        noiseMemoryTicks = NOISE_MEMORY_TICKS.get();
        noiseMaxEventsPerLevel = NOISE_MAX_EVENTS_PER_LEVEL.get();
        zombieHearNoiseRadiusMultiplier = ZOMBIE_HEAR_NOISE_RADIUS_MULTIPLIER.get();
        zombieMoveToNoise = ZOMBIE_MOVE_TO_NOISE.get();
        zombieTargetPlayerNearNoise = ZOMBIE_TARGET_PLAYER_NEAR_NOISE.get();
        zombieNoiseTargetPlayerRadius = ZOMBIE_NOISE_TARGET_PLAYER_RADIUS.get();
        zombieNoiseAlertRadius = ZOMBIE_NOISE_ALERT_RADIUS.get();
        zombieNoiseAlertMaxZombies = ZOMBIE_NOISE_ALERT_MAX_ZOMBIES.get();
        zombieNoiseRequiresLineOfSight = ZOMBIE_NOISE_REQUIRES_LINE_OF_SIGHT.get();
        zombieNoiseRetargetChance = ZOMBIE_NOISE_RETARGET_CHANCE.get();
        zombieNoiseDebugLogs = ZOMBIE_NOISE_DEBUG_LOGS.get();
        enableZombieBaseDamageSystem = ENABLE_ZOMBIE_BASE_DAMAGE_SYSTEM.get();
        zombieBaseDamageAffectsVanillaZombies = ZOMBIE_BASE_DAMAGE_AFFECTS_VANILLA_ZOMBIES.get();
        zombieBaseDamageAffectsDaySpawnedZombies = ZOMBIE_BASE_DAMAGE_AFFECTS_DAY_SPAWNED_ZOMBIES.get();
        zombieBaseDamageCheckIntervalTicks = ZOMBIE_BASE_DAMAGE_CHECK_INTERVAL_TICKS.get();
        zombieBaseDamageBonusEasy = ZOMBIE_BASE_DAMAGE_BONUS_EASY.get();
        zombieBaseDamageBonusNormal = ZOMBIE_BASE_DAMAGE_BONUS_NORMAL.get();
        zombieBaseDamageBonusHard = ZOMBIE_BASE_DAMAGE_BONUS_HARD.get();
        zombieBaseDamageUseMultiplier = ZOMBIE_BASE_DAMAGE_USE_MULTIPLIER.get();
        zombieBaseDamageMultiplierEasy = ZOMBIE_BASE_DAMAGE_MULTIPLIER_EASY.get();
        zombieBaseDamageMultiplierNormal = ZOMBIE_BASE_DAMAGE_MULTIPLIER_NORMAL.get();
        zombieBaseDamageMultiplierHard = ZOMBIE_BASE_DAMAGE_MULTIPLIER_HARD.get();
        zombieBaseDamageDebugLogs = ZOMBIE_BASE_DAMAGE_DEBUG_LOGS.get();
        enableSurvivalDaysScaling = ENABLE_SURVIVAL_DAYS_SCALING.get();
        survivalScalingMode = SURVIVAL_SCALING_MODE.get();
        survivalScalingApplyOnSpawn = SURVIVAL_SCALING_APPLY_ON_SPAWN.get();
        survivalScalingStartDay = SURVIVAL_SCALING_START_DAY.get();
        survivalScalingUseNearestPlayer = SURVIVAL_SCALING_USE_NEAREST_PLAYER.get();
        survivalScalingRadius = SURVIVAL_SCALING_RADIUS.get();
        survivalScalingCheckIntervalTicks = SURVIVAL_SCALING_CHECK_INTERVAL_TICKS.get();
        survivalScalingRecheckIntervalTicks = SURVIVAL_SCALING_RECHECK_INTERVAL_TICKS.get();
        survivalScalingMaxLevel = SURVIVAL_SCALING_MAX_LEVEL.get();
        survivalScalingLevelCap = SURVIVAL_SCALING_LEVEL_CAP.get();
        survivalScalingLevelsPerDay = SURVIVAL_SCALING_LEVELS_PER_DAY.get();
        survivalScalingStoreLevelOnMob = SURVIVAL_SCALING_STORE_LEVEL_ON_MOB.get();
        survivalScalingReapplyMissingModifiers = SURVIVAL_SCALING_REAPPLY_MISSING_MODIFIERS.get();
        survivalScalingDisableEntityTickHeavyLogic = SURVIVAL_SCALING_DISABLE_ENTITY_TICK_HEAVY_LOGIC.get();
        survivalScalingDamagePerDay = SURVIVAL_SCALING_DAMAGE_PER_DAY.get();
        survivalScalingSpeedPerDay = SURVIVAL_SCALING_SPEED_PER_DAY.get();
        survivalScalingFollowRangePerDay = SURVIVAL_SCALING_FOLLOW_RANGE_PER_DAY.get();
        survivalScalingArmorPerDay = SURVIVAL_SCALING_ARMOR_PER_DAY.get();
        survivalScalingHealthPerDay = SURVIVAL_SCALING_HEALTH_PER_DAY.get();
        survivalScalingMaxDamageMultiplier = SURVIVAL_SCALING_MAX_DAMAGE_MULTIPLIER.get();
        survivalScalingMaxSpeedMultiplier = SURVIVAL_SCALING_MAX_SPEED_MULTIPLIER.get();
        survivalScalingMaxFollowRangeMultiplier = SURVIVAL_SCALING_MAX_FOLLOW_RANGE_MULTIPLIER.get();
        survivalScalingMaxArmorMultiplier = SURVIVAL_SCALING_MAX_ARMOR_MULTIPLIER.get();
        survivalScalingMaxHealthMultiplier = SURVIVAL_SCALING_MAX_HEALTH_MULTIPLIER.get();
        survivalScalingDebugLogs = SURVIVAL_SCALING_DEBUG_LOGS.get();
        enableThreatLevelScaling = ENABLE_THREAT_LEVEL_SCALING.get();
        threatScalingMode = THREAT_SCALING_MODE.get();
        threatScalingApplyOnSpawn = THREAT_SCALING_APPLY_ON_SPAWN.get();
        threatScalingRecheckIntervalTicks = THREAT_SCALING_RECHECK_INTERVAL_TICKS.get();
        threatScalingStoreOnMob = THREAT_SCALING_STORE_ON_MOB.get();
        threatScalingDebugLogs = THREAT_SCALING_DEBUG_LOGS.get();
        threatScalingFinalLevelCap = THREAT_SCALING_FINAL_LEVEL_CAP.get();
        threatScalingMinimumLevel = THREAT_SCALING_MINIMUM_LEVEL.get();
        threatWorldDayEnabled = THREAT_WORLD_DAY_ENABLED.get();
        threatWorldDayStartDay = THREAT_WORLD_DAY_START_DAY.get();
        threatWorldDayLevelsPerDay = THREAT_WORLD_DAY_LEVELS_PER_DAY.get();
        threatWorldDayMaxLevel = THREAT_WORLD_DAY_MAX_LEVEL.get();
        threatDifficultyEnabled = THREAT_DIFFICULTY_ENABLED.get();
        threatDifficultyEasyBonus = THREAT_DIFFICULTY_EASY_BONUS.get();
        threatDifficultyNormalBonus = THREAT_DIFFICULTY_NORMAL_BONUS.get();
        threatDifficultyHardBonus = THREAT_DIFFICULTY_HARD_BONUS.get();
        threatDistanceEnabled = THREAT_DISTANCE_ENABLED.get();
        threatDistanceStartBlocks = THREAT_DISTANCE_START_BLOCKS.get();
        threatDistanceLevelsPer1000Blocks = THREAT_DISTANCE_LEVELS_PER_1000_BLOCKS.get();
        threatDistanceMaxBonus = THREAT_DISTANCE_MAX_BONUS.get();
        threatRandomVarianceEnabled = THREAT_RANDOM_VARIANCE_ENABLED.get();
        threatRandomVarianceMin = THREAT_RANDOM_VARIANCE_MIN.get();
        threatRandomVarianceMax = THREAT_RANDOM_VARIANCE_MAX.get();
        threatNearestPlayerEnabled = THREAT_NEAREST_PLAYER_ENABLED.get();
        threatNearestPlayerRadius = THREAT_NEAREST_PLAYER_RADIUS.get();
        threatNearestPlayerSurvivalDaysEnabled = THREAT_NEAREST_PLAYER_SURVIVAL_DAYS_ENABLED.get();
        threatNearestPlayerMaxBonus = THREAT_NEAREST_PLAYER_MAX_BONUS.get();
        threatDamagePerLevel = THREAT_DAMAGE_PER_LEVEL.get();
        threatSpeedPerLevel = THREAT_SPEED_PER_LEVEL.get();
        threatFollowRangePerLevel = THREAT_FOLLOW_RANGE_PER_LEVEL.get();
        threatArmorPerLevel = THREAT_ARMOR_PER_LEVEL.get();
        threatHealthPerLevel = THREAT_HEALTH_PER_LEVEL.get();
        threatMaxDamageMultiplier = THREAT_MAX_DAMAGE_MULTIPLIER.get();
        threatMaxSpeedMultiplier = THREAT_MAX_SPEED_MULTIPLIER.get();
        threatMaxFollowRangeMultiplier = THREAT_MAX_FOLLOW_RANGE_MULTIPLIER.get();
        threatMaxArmorMultiplier = THREAT_MAX_ARMOR_MULTIPLIER.get();
        threatMaxHealthMultiplier = THREAT_MAX_HEALTH_MULTIPLIER.get();
        enableMicroTechCompat = ENABLE_MICROTECH_COMPAT.get();
        microTechMachineNoiseEnabled = MICROTECH_MACHINE_NOISE_ENABLED.get();
        microTechMachineNoiseIntervalTicks = MICROTECH_MACHINE_NOISE_INTERVAL_TICKS.get();
        microTechMachineScanIntervalTicks = MICROTECH_MACHINE_SCAN_INTERVAL_TICKS.get();
        microTechMachineMaxBlocksPerScan = MICROTECH_MACHINE_MAX_BLOCKS_PER_SCAN.get();
        microTechMachineNoiseRadius = MICROTECH_MACHINE_NOISE_RADIUS.get();
        microTechMachineNoiseDurationTicks = MICROTECH_MACHINE_NOISE_DURATION_TICKS.get();
        microTechOnlyRunningMachinesMakeNoise = MICROTECH_ONLY_RUNNING_MACHINES_MAKE_NOISE.get();
        microTechDebugLogs = MICROTECH_DEBUG_LOGS.get();
        enableZombieMachineAttacks = ENABLE_ZOMBIE_MACHINE_ATTACKS.get();
        zombieMachineAttackUseDirectDetection = ZOMBIE_MACHINE_ATTACK_USE_DIRECT_DETECTION.get();
        zombieMachineAttackOnlyMicroTech = ZOMBIE_MACHINE_ATTACK_ONLY_MICROTECH.get();
        zombieMachineAttackRequireNoise = ZOMBIE_MACHINE_ATTACK_REQUIRE_NOISE.get();
        zombieMachineAttackRange = ZOMBIE_MACHINE_ATTACK_RANGE.get();
        zombieMachineAttackCooldownTicks = ZOMBIE_MACHINE_ATTACK_COOLDOWN_TICKS.get();
        zombieMachineDamagePerHit = ZOMBIE_MACHINE_DAMAGE_PER_HIT.get();
        machineDurabilityDefault = MACHINE_DURABILITY_DEFAULT.get();
        machineDurabilityEnergyConverterT1 = MACHINE_DURABILITY_ENERGY_CONVERTER_T1.get();
        machineDurabilityEvoTable = MACHINE_DURABILITY_EVO_TABLE.get();
        machineDurabilityBatteryT1 = MACHINE_DURABILITY_BATTERY_T1.get();
        machineDurabilityBatteryT2 = MACHINE_DURABILITY_BATTERY_T2.get();
        zombieMachineBreakBlocks = ZOMBIE_MACHINE_BREAK_BLOCKS.get();
        zombieMachineDropBlock = ZOMBIE_MACHINE_DROP_BLOCK.get();
        zombieMachineAttackOnlyActiveMachines = ZOMBIE_MACHINE_ATTACK_ONLY_ACTIVE_MACHINES.get();
        zombieMachineAttackDebugLogs = ZOMBIE_MACHINE_ATTACK_DEBUG_LOGS.get();
        enableDirectMachineAttraction = ENABLE_DIRECT_MACHINE_ATTRACTION.get();
        directMachineAttractionRadius = DIRECT_MACHINE_ATTRACTION_RADIUS.get();
        directMachineAttractionCheckIntervalTicks = DIRECT_MACHINE_ATTRACTION_CHECK_INTERVAL_TICKS.get();
        directMachineAttractionMaxScanBlocksPerZombie = DIRECT_MACHINE_ATTRACTION_MAX_SCAN_BLOCKS_PER_ZOMBIE.get();
        directMachineAttractionOnlyActiveMachines = DIRECT_MACHINE_ATTRACTION_ONLY_ACTIVE_MACHINES.get();
        directMachineAttractionOnlyMicroTech = DIRECT_MACHINE_ATTRACTION_ONLY_MICROTECH.get();
        directMachineAttractionNavigationSpeed = DIRECT_MACHINE_ATTRACTION_NAVIGATION_SPEED.get();
        directMachineIgnoreMachineIfPlayerTargetWithin = DIRECT_MACHINE_IGNORE_PLAYER_TARGET_WITHIN.get();
        directMachineAttractionRepathIntervalTicks = DIRECT_MACHINE_ATTRACTION_REPATH_INTERVAL_TICKS.get();
        zombieNoiseRepathIntervalTicks = ZOMBIE_NOISE_REPATH_INTERVAL_TICKS.get();
        debugZombieCommandFallbackRadius = DEBUG_ZOMBIE_COMMAND_FALLBACK_RADIUS.get();
        daySpawnedZombiesIgnoreSunBurn = DAY_SPAWNED_ZOMBIES_IGNORE_SUN_BURN.get();
        enableDayZombieSpawns = ENABLE_DAY_ZOMBIE_SPAWNS.get();
        enableNightZombieSpawns = ENABLE_NIGHT_ZOMBIE_SPAWNS.get();
        nightSpawnIntervalTicks = NIGHT_SPAWN_INTERVAL_TICKS.get();
        nightSpawnChance = NIGHT_SPAWN_CHANCE.get();
        nightSpawnMinGroupSize = NIGHT_SPAWN_MIN_GROUP_SIZE.get();
        nightSpawnMaxGroupSize = NIGHT_SPAWN_MAX_GROUP_SIZE.get();
        nightSpawnRadiusMin = NIGHT_SPAWN_RADIUS_MIN.get();
        nightSpawnRadiusMax = NIGHT_SPAWN_RADIUS_MAX.get();
        nightSpawnMaxNearbyZombies = NIGHT_SPAWN_MAX_NEARBY_ZOMBIES.get();
        nightSpawnNearbyCheckRadius = NIGHT_SPAWN_NEARBY_CHECK_RADIUS.get();
        nightSpawnOnlyAtNight = NIGHT_SPAWN_ONLY_AT_NIGHT.get();
        nightSpawnAllowDuringThunder = NIGHT_SPAWN_ALLOW_DURING_THUNDER.get();
        nightSpawnRespectDoMobSpawning = NIGHT_SPAWN_RESPECT_DO_MOB_SPAWNING.get();
        nightSpawnAllowedDimensions = NIGHT_SPAWN_ALLOWED_DIMENSIONS.get();
        nightSpawnRequireDarkness = NIGHT_SPAWN_REQUIRE_DARKNESS.get();
        nightSpawnMaxLightLevel = NIGHT_SPAWN_MAX_LIGHT_LEVEL.get();
        nightSpawnedZombiesIgnoreSunBurn = NIGHT_SPAWNED_ZOMBIES_IGNORE_SUN_BURN.get();
        nightSpawnDebugLogs = NIGHT_SPAWN_DEBUG_LOGS.get();
        daySpawnIntervalTicks = DAY_SPAWN_INTERVAL_TICKS.get();
        daySpawnChance = DAY_SPAWN_CHANCE.get();
        daySpawnMinGroupSize = DAY_SPAWN_MIN_GROUP_SIZE.get();
        daySpawnMaxGroupSize = DAY_SPAWN_MAX_GROUP_SIZE.get();
        daySpawnRadiusMin = DAY_SPAWN_RADIUS_MIN.get();
        daySpawnRadiusMax = DAY_SPAWN_RADIUS_MAX.get();
        daySpawnMaxNearbyZombies = DAY_SPAWN_MAX_NEARBY_ZOMBIES.get();
        daySpawnNearbyCheckRadius = DAY_SPAWN_NEARBY_CHECK_RADIUS.get();
        daySpawnRequireSkyAccess = DAY_SPAWN_REQUIRE_SKY_ACCESS.get();
        daySpawnAllowInDarkAreasOnly = DAY_SPAWN_ALLOW_IN_DARK_AREAS_ONLY.get();
        daySpawnRespectDoMobSpawning = DAY_SPAWN_RESPECT_DO_MOB_SPAWNING.get();
        daySpawnMaxLightLevel = DAY_SPAWN_MAX_LIGHT_LEVEL.get();
        daySpawnAllowedDimensions = DAY_SPAWN_ALLOWED_DIMENSIONS.get();
        enableZombieAggressionSystem = ENABLE_ZOMBIE_AGGRESSION_SYSTEM.get();
        aggressionAffectsVanillaZombies = AGGRESSION_AFFECTS_VANILLA_ZOMBIES.get();
        aggressionAffectsDaySpawnedZombies = AGGRESSION_AFFECTS_DAY_SPAWNED_ZOMBIES.get();
        aggressionCheckIntervalTicks = AGGRESSION_CHECK_INTERVAL_TICKS.get();
        zombieExtraTargetRange = ZOMBIE_EXTRA_TARGET_RANGE.get();
        zombieAlertNearbyZombies = ZOMBIE_ALERT_NEARBY_ZOMBIES.get();
        zombieAlertRadius = ZOMBIE_ALERT_RADIUS.get();
        zombieAlertMaxTargets = ZOMBIE_ALERT_MAX_TARGETS.get();
        zombieAlertRequiresLineOfSight = ZOMBIE_ALERT_REQUIRES_LINE_OF_SIGHT.get();
        zombieForgetTargetAfterTicks = ZOMBIE_FORGET_TARGET_AFTER_TICKS.get();
        zombieRetargetChance = ZOMBIE_RETARGET_CHANCE.get();
        zombiePreferNearestPlayer = ZOMBIE_PREFER_NEAREST_PLAYER.get();
        zlogicSystemsAffectOnlyZlogicZombies = ZLOGIC_SYSTEMS_AFFECT_ONLY_ZLOGIC_ZOMBIES.get();
        zlogicTreatLegacyDaySpawnedAsZlogic = ZLOGIC_TREAT_LEGACY_DAY_SPAWNED_AS_ZLOGIC.get();
        zlogicDebugZombieMarkers = ZLOGIC_DEBUG_ZOMBIE_MARKERS.get();
        enableZombieHordeClimb = ENABLE_ZOMBIE_HORDE_CLIMB.get();
        hordeClimbOnlyEligibleZombies = HORDE_CLIMB_ONLY_ELIGIBLE_ZOMBIES.get();
        hordeClimbMinZombies = HORDE_CLIMB_MIN_ZOMBIES.get();
        hordeClimbRadius = HORDE_CLIMB_RADIUS.get();
        hordeClimbBaseHeight = HORDE_CLIMB_BASE_HEIGHT.get();
        hordeClimbHeightPerZombie = HORDE_CLIMB_HEIGHT_PER_ZOMBIE.get();
        hordeClimbMaxHeight = HORDE_CLIMB_MAX_HEIGHT.get();
        hordeClimbCooldownTicks = HORDE_CLIMB_COOLDOWN_TICKS.get();
        hordeClimbObstacleCheckDistance = HORDE_CLIMB_OBSTACLE_CHECK_DISTANCE.get();
        hordeClimbMinObstacleHeight = HORDE_CLIMB_MIN_OBSTACLE_HEIGHT.get();
        hordeClimbMaxObstacleScanHeight = HORDE_CLIMB_MAX_OBSTACLE_SCAN_HEIGHT.get();
        hordeClimbVerticalBoostBase = HORDE_CLIMB_VERTICAL_BOOST_BASE.get();
        hordeClimbVerticalBoostPerHeight = HORDE_CLIMB_VERTICAL_BOOST_PER_HEIGHT.get();
        hordeClimbForwardBoost = HORDE_CLIMB_FORWARD_BOOST.get();
        hordeClimbRequireTarget = HORDE_CLIMB_REQUIRE_TARGET.get();
        hordeClimbWorksAgainstPlayers = HORDE_CLIMB_WORKS_AGAINST_PLAYERS.get();
        hordeClimbWorksAgainstMachines = HORDE_CLIMB_WORKS_AGAINST_MACHINES.get();
        hordeClimbParticles = HORDE_CLIMB_PARTICLES.get();
        hordeClimbSounds = HORDE_CLIMB_SOUNDS.get();
        hordeClimbGroupVisual = HORDE_CLIMB_GROUP_VISUAL.get();
        hordeClimbAttemptChance = HORDE_CLIMB_ATTEMPT_CHANCE.get();
        hordeClimbCheckIntervalTicks = HORDE_CLIMB_CHECK_INTERVAL_TICKS.get();
        hordeClimbDebugLogs = HORDE_CLIMB_DEBUG_LOGS.get();
        enableHordeClimbTargetElevationMode = ENABLE_HORDE_CLIMB_TARGET_ELEVATION_MODE.get();
        hordeClimbTargetMinYDifference = HORDE_CLIMB_TARGET_MIN_Y_DIFFERENCE.get();
        hordeClimbTargetHorizontalRange = HORDE_CLIMB_TARGET_HORIZONTAL_RANGE.get();
        hordeClimbTargetMinBlockYDifference = HORDE_CLIMB_TARGET_MIN_BLOCK_Y_DIFFERENCE.get();
        hordeClimbTargetModeRequireObstacleBetween = HORDE_CLIMB_TARGET_MODE_REQUIRE_OBSTACLE_BETWEEN.get();
        hordeClimbIgnoreSmallTerrainSteps = HORDE_CLIMB_IGNORE_SMALL_TERRAIN_STEPS.get();
        hordeClimbTargetModeMinObstacleHeight = HORDE_CLIMB_TARGET_MODE_MIN_OBSTACLE_HEIGHT.get();
        hordeClimbTargetHorizontalMinRange = HORDE_CLIMB_TARGET_HORIZONTAL_MIN_RANGE.get();
        hordeClimbUseTargetDirectionForBoost = HORDE_CLIMB_USE_TARGET_DIRECTION_FOR_BOOST.get();
        hordeClimbLeaderOnly = HORDE_CLIMB_LEADER_ONLY.get();
        hordeClimbLeaderCheckRadius = HORDE_CLIMB_LEADER_CHECK_RADIUS.get();
        hordeClimbAllowPillarClimb = HORDE_CLIMB_ALLOW_PILLAR_CLIMB.get();
        hordeClimbPillarMinHeight = HORDE_CLIMB_PILLAR_MIN_HEIGHT.get();
        hordeClimbVerboseDebug = HORDE_CLIMB_VERBOSE_DEBUG.get();
        enableHordeSiegeClimb = ENABLE_HORDE_SIEGE_CLIMB.get();
        hordeSiegeClimbMinZombies = HORDE_SIEGE_CLIMB_MIN_ZOMBIES.get();
        hordeSiegeClimbGroupRadius = HORDE_SIEGE_CLIMB_GROUP_RADIUS.get();
        hordeSiegeClimbMaxTargetDistance = HORDE_SIEGE_CLIMB_MAX_TARGET_DISTANCE.get();
        hordeSiegeClimbMaxObstacleDistance = HORDE_SIEGE_CLIMB_MAX_OBSTACLE_DISTANCE.get();
        hordeSiegeClimbMinObstacleHeight = HORDE_SIEGE_CLIMB_MIN_OBSTACLE_HEIGHT.get();
        hordeSiegeClimbMaxObstacleHeight = HORDE_SIEGE_CLIMB_MAX_OBSTACLE_HEIGHT.get();
        hordeSiegeClimbRequireObstacleInFront = HORDE_SIEGE_CLIMB_REQUIRE_OBSTACLE_IN_FRONT.get();
        hordeSiegeClimbRequireTargetAboveOrBehindObstacle = HORDE_SIEGE_CLIMB_REQUIRE_TARGET_ABOVE_OR_BEHIND_OBSTACLE.get();
        hordeSiegeClimbCooldownTicks = HORDE_SIEGE_CLIMB_COOLDOWN_TICKS.get();
        hordeSiegeClimbAreaCooldownTicks = HORDE_SIEGE_CLIMB_AREA_COOLDOWN_TICKS.get();
        hordeSiegeClimbAreaCooldownRadius = HORDE_SIEGE_CLIMB_AREA_COOLDOWN_RADIUS.get();
        hordeSiegeClimbVerticalBoostBase = HORDE_SIEGE_CLIMB_VERTICAL_BOOST_BASE.get();
        hordeSiegeClimbVerticalBoostPerHeight = HORDE_SIEGE_CLIMB_VERTICAL_BOOST_PER_HEIGHT.get();
        hordeSiegeClimbForwardBoost = HORDE_SIEGE_CLIMB_FORWARD_BOOST.get();
        hordeSiegeClimbDebugLogs = HORDE_SIEGE_CLIMB_DEBUG_LOGS.get();
        hordeClimbSuppressOnNaturalSlopes = HORDE_CLIMB_SUPPRESS_ON_NATURAL_SLOPES.get();
        hordeClimbNaturalSlopeMaxStepHeight = HORDE_CLIMB_NATURAL_SLOPE_MAX_STEP_HEIGHT.get();
        hordeClimbNaturalSlopeScanDistance = HORDE_CLIMB_NATURAL_SLOPE_SCAN_DISTANCE.get();
        hordeClimbRequireWallLikeObstacle = HORDE_CLIMB_REQUIRE_WALL_LIKE_OBSTACLE.get();
        enableZombieBarrierBreak = ENABLE_ZOMBIE_BARRIER_BREAK.get();
        barrierBreakOnlyEligibleZombies = BARRIER_BREAK_ONLY_ELIGIBLE_ZOMBIES.get();
        barrierBreakRequireFrontSide = BARRIER_BREAK_REQUIRE_FRONT_SIDE.get();
        barrierBreakFrontWidth = BARRIER_BREAK_FRONT_WIDTH.get();
        barrierBreakFrontDepth = BARRIER_BREAK_FRONT_DEPTH.get();
        barrierBreakMaxZombieDistanceToBarrier = BARRIER_BREAK_MAX_ZOMBIE_DISTANCE_TO_BARRIER.get();
        barrierBreakRequireTargetBehindBarrier = BARRIER_BREAK_REQUIRE_TARGET_BEHIND_BARRIER.get();
        barrierBreakMinPathAlignment = BARRIER_BREAK_MIN_PATH_ALIGNMENT.get();
        barrierBreakRejectZombiesBehindBarrier = BARRIER_BREAK_REJECT_ZOMBIES_BEHIND_BARRIER.get();
        barrierBreakSideDebug = BARRIER_BREAK_SIDE_DEBUG.get();
        barrierBreakMinZombies = BARRIER_BREAK_MIN_ZOMBIES.get();
        barrierBreakZombieRadius = BARRIER_BREAK_ZOMBIE_RADIUS.get();
        barrierBreakCheckIntervalTicks = BARRIER_BREAK_CHECK_INTERVAL_TICKS.get();
        barrierBreakZombieCooldownTicks = BARRIER_BREAK_ZOMBIE_COOLDOWN_TICKS.get();
        barrierBreakBlockCooldownTicks = BARRIER_BREAK_BLOCK_COOLDOWN_TICKS.get();
        barrierBreakRequireTarget = BARRIER_BREAK_REQUIRE_TARGET.get();
        barrierBreakWorksAgainstPlayers = BARRIER_BREAK_WORKS_AGAINST_PLAYERS.get();
        barrierBreakWorksAgainstMachines = BARRIER_BREAK_WORKS_AGAINST_MACHINES.get();
        barrierBreakWoodenDoors = BARRIER_BREAK_WOODEN_DOORS.get();
        barrierBreakWoodenTrapdoors = BARRIER_BREAK_WOODEN_TRAPDOORS.get();
        barrierBreakWoodenFenceGates = BARRIER_BREAK_WOODEN_FENCE_GATES.get();
        barrierBreakGlassBlocks = BARRIER_BREAK_GLASS_BLOCKS.get();
        barrierBreakGlassPanes = BARRIER_BREAK_GLASS_PANES.get();
        barrierBreakStainedGlass = BARRIER_BREAK_STAINED_GLASS.get();
        barrierBreakStainedGlassPanes = BARRIER_BREAK_STAINED_GLASS_PANES.get();
        barrierBreakTintedGlass = BARRIER_BREAK_TINTED_GLASS.get();
        barrierBreakRespectBlockDenylist = BARRIER_BREAK_RESPECT_BLOCK_DENYLIST.get();
        barrierBreakBlockDenylist = BARRIER_BREAK_BLOCK_DENYLIST.get();
        barrierBreakExtraBlockAllowlist = BARRIER_BREAK_EXTRA_BLOCK_ALLOWLIST.get();
        barrierBreakDamagePerHit = BARRIER_BREAK_DAMAGE_PER_HIT.get();
        barrierBreakWoodenDoorDurability = BARRIER_BREAK_WOODEN_DOOR_DURABILITY.get();
        barrierBreakGlassDurability = BARRIER_BREAK_GLASS_DURABILITY.get();
        barrierBreakGlassPaneDurability = BARRIER_BREAK_GLASS_PANE_DURABILITY.get();
        barrierBreakExtraBlockDurability = BARRIER_BREAK_EXTRA_BLOCK_DURABILITY.get();
        barrierBreakDamageMemoryTicks = BARRIER_BREAK_DAMAGE_MEMORY_TICKS.get();
        barrierBreakActuallyBreakBlocks = BARRIER_BREAK_ACTUALLY_BREAK_BLOCKS.get();
        barrierBreakDropBlocks = BARRIER_BREAK_DROP_BLOCKS.get();
        barrierBreakParticles = BARRIER_BREAK_PARTICLES.get();
        barrierBreakSounds = BARRIER_BREAK_SOUNDS.get();
        barrierBreakDebugLogs = BARRIER_BREAK_DEBUG_LOGS.get();
        barrierBreakVerboseDebug = BARRIER_BREAK_VERBOSE_DEBUG.get();
        disableVanillaMonsterSpawns = DISABLE_VANILLA_MONSTER_SPAWNS.get();
        disableVanillaMonsterSpawnsOnlyInOverworld = DISABLE_VANILLA_MONSTER_SPAWNS_ONLY_IN_OVERWORLD.get();
        disableAllNaturalHostileMobSpawns = DISABLE_ALL_NATURAL_HOSTILE_MOB_SPAWNS.get();
        disableVanillaZombieSpawns = DISABLE_VANILLA_ZOMBIE_SPAWNS.get();
        disableVanillaSkeletonSpawns = DISABLE_VANILLA_SKELETON_SPAWNS.get();
        disableVanillaCreeperSpawns = DISABLE_VANILLA_CREEPER_SPAWNS.get();
        disableVanillaSpiderSpawns = DISABLE_VANILLA_SPIDER_SPAWNS.get();
        disableVanillaEndermanSpawns = DISABLE_VANILLA_ENDERMAN_SPAWNS.get();
        disableVanillaSlimeSpawns = DISABLE_VANILLA_SLIME_SPAWNS.get();
        disableVanillaWitchSpawns = DISABLE_VANILLA_WITCH_SPAWNS.get();
        disableVanillaPillagerSpawns = DISABLE_VANILLA_PILLAGER_SPAWNS.get();
        allowSpawnerMonsterSpawns = ALLOW_SPAWNER_MONSTER_SPAWNS.get();
        allowStructureMonsterSpawns = ALLOW_STRUCTURE_MONSTER_SPAWNS.get();
        allowZlogicSpawnedMonsters = ALLOW_ZLOGIC_SPAWNED_MONSTERS.get();
        vanillaSpawnBlockDebugLogs = VANILLA_SPAWN_BLOCK_DEBUG_LOGS.get();
        enablePerformanceDebugLogs = ENABLE_PERFORMANCE_DEBUG_LOGS.get();
        performanceDebugIntervalTicks = PERFORMANCE_DEBUG_INTERVAL_TICKS.get();
    }

    private static boolean validateResourceLocation(final Object value) {
        return value instanceof String stringValue && ResourceLocation.tryParse(stringValue) != null;
    }
}
