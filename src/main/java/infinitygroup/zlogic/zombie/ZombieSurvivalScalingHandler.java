package infinitygroup.zlogic.zombie;

import infinitygroup.zlogic.Config;
import infinitygroup.zlogic.perf.PerformanceTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class ZombieSurvivalScalingHandler {
    private ZombieSurvivalScalingHandler() {
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Zombie zombie) || entity.level().isClientSide() || !(entity.level() instanceof ServerLevel level)) {
            return;
        }

        if (!ZombieEligibilityHelper.isEligibleForThreatScaling(zombie)) {
            ZombieThreatLevelManager.clearAllScalingModifiers(zombie);
            return;
        }

        if (isThreatScalingActive()) {
            PerformanceTracker.recordEntityProcessed();
            PerformanceTracker.recordZombieProcessed();
            ZombieThreatLevelManager.ApplyResult result = ZombieThreatLevelManager.apply(level, zombie, true, true);
            if (result.applied()) {
                PerformanceTracker.recordThreatJoinApplication();
            }
            return;
        }

        if (isLegacySurvivalScalingActive()) {
            PerformanceTracker.recordEntityProcessed();
            PerformanceTracker.recordZombieProcessed();
            ZombieThreatLevelManager.clearThreatStateAndModifiers(zombie);
            ZombieScalingLevelManager.ApplyResult result = ZombieScalingLevelManager.apply(level, zombie, true);
            PerformanceTracker.recordSurvivalScalingApplications(result.applied() ? 1 : 0);
            return;
        }

        ZombieThreatLevelManager.clearAllScalingModifiers(zombie);
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Zombie zombie) || zombie.level().isClientSide() || !(zombie.level() instanceof ServerLevel level)) {
            return;
        }

        if (!ZombieEligibilityHelper.isEligibleForThreatScaling(zombie)) {
            ZombieThreatLevelManager.clearAllScalingModifiers(zombie);
            return;
        }

        PerformanceTracker.recordEntityProcessed();
        PerformanceTracker.recordZombieProcessed();

        if (isThreatScalingActive()) {
            int interval = Math.max(1, Config.threatScalingRecheckIntervalTicks);
            if (zombie.tickCount % interval != 0) {
                return;
            }

            if (!ZombieThreatLevelManager.isApplied(zombie) || ZombieThreatLevelManager.shouldReapply(level, zombie)) {
                ZombieThreatLevelManager.ApplyResult result = ZombieThreatLevelManager.apply(level, zombie, false, false);
                if (result.applied()) {
                    PerformanceTracker.recordThreatFallbackApplication();
                }
            }
            return;
        }

        if (isLegacySurvivalScalingActive()) {
            int interval = Math.max(1, Config.survivalScalingRecheckIntervalTicks);
            if (zombie.tickCount % interval != 0) {
                return;
            }

            if (!ZombieScalingLevelManager.isApplied(zombie) || ZombieScalingLevelManager.shouldReapply(level, zombie)) {
                ZombieThreatLevelManager.clearThreatStateAndModifiers(zombie);
                ZombieScalingLevelManager.ApplyResult result = ZombieScalingLevelManager.apply(level, zombie, false);
                if (result.applied()) {
                    PerformanceTracker.recordSurvivalScalingApplications(1);
                }
            }
            return;
        }

        ZombieThreatLevelManager.clearAllScalingModifiers(zombie);
    }

    public static ZombieSurvivalScalingInspection inspectZombie(ServerLevel level, LivingEntity living) {
        if (level == null || living == null) {
            return ZombieSurvivalScalingInspection.empty();
        }

        if (!(living instanceof Zombie zombie)) {
            return ZombieSurvivalScalingInspection.empty();
        }

        ZombieScalingLevelManager.ScalingComputation computation = ZombieScalingLevelManager.compute(level, zombie);
        return new ZombieSurvivalScalingInspection(
            living.blockPosition(),
            living.getType().toShortString(),
            computation.worldDay(),
            computation.scalingDays(),
            computation.active(),
            computation.startDay(),
            Config.survivalScalingUseNearestPlayer,
            Config.survivalScalingRadius,
            computation.recheckIntervalTicks(),
            computation.active(),
            computation.damageMultiplier(),
            computation.speedMultiplier(),
            computation.followRangeMultiplier(),
            computation.armorMultiplier(),
            computation.healthMultiplier(),
            zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) != null && zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).getModifier(ZombieScalingLevelManager.DAMAGE_MODIFIER_ID) != null,
            zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) != null && zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getModifier(ZombieScalingLevelManager.SPEED_MODIFIER_ID) != null,
            zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE) != null && zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).getModifier(ZombieScalingLevelManager.FOLLOW_RANGE_MODIFIER_ID) != null,
            zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) != null && zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).getModifier(ZombieScalingLevelManager.ARMOR_MODIFIER_ID) != null,
            zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH) != null && zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getModifier(ZombieScalingLevelManager.HEALTH_MODIFIER_ID) != null,
            zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) != null ? zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).getValue() : 0.0D,
            zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) != null ? zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getValue() : 0.0D,
            zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE) != null ? zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).getValue() : 0.0D,
            zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) != null ? zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).getValue() : 0.0D,
            zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH) != null ? zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getValue() : 0.0D,
            zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) != null && zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).getModifier(ZombieScalingLevelManager.DAMAGE_MODIFIER_ID) != null ? zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).getModifier(ZombieScalingLevelManager.DAMAGE_MODIFIER_ID).amount() : 0.0D,
            zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) != null && zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getModifier(ZombieScalingLevelManager.SPEED_MODIFIER_ID) != null ? zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getModifier(ZombieScalingLevelManager.SPEED_MODIFIER_ID).amount() : 0.0D,
            zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE) != null && zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).getModifier(ZombieScalingLevelManager.FOLLOW_RANGE_MODIFIER_ID) != null ? zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).getModifier(ZombieScalingLevelManager.FOLLOW_RANGE_MODIFIER_ID).amount() : 0.0D,
            zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) != null && zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).getModifier(ZombieScalingLevelManager.ARMOR_MODIFIER_ID) != null ? zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).getModifier(ZombieScalingLevelManager.ARMOR_MODIFIER_ID).amount() : 0.0D,
            zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH) != null && zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getModifier(ZombieScalingLevelManager.HEALTH_MODIFIER_ID) != null ? zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getModifier(ZombieScalingLevelManager.HEALTH_MODIFIER_ID).amount() : 0.0D,
            computation.active(),
            determineReason(computation, zombie)
        );
    }

    private static boolean isThreatScalingActive() {
        return Config.enableThreatLevelScaling && ZombieSurvivalScalingHelper.ThreatScalingMode.parse(Config.threatScalingMode) != ZombieSurvivalScalingHelper.ThreatScalingMode.DISABLED;
    }

    private static boolean isLegacySurvivalScalingActive() {
        return Config.enableSurvivalDaysScaling && ZombieSurvivalScalingHelper.SurvivalScalingMode.parse(Config.survivalScalingMode) != ZombieSurvivalScalingHelper.SurvivalScalingMode.DISABLED;
    }

    private static String determineReason(ZombieScalingLevelManager.ScalingComputation computation, Zombie zombie) {
        if (computation == null) {
            return "empty";
        }

        if (isThreatScalingActive()) {
            return "threat active";
        }

        if (!Config.enableSurvivalDaysScaling) {
            return "feature disabled";
        }

        if (ZombieSurvivalScalingHelper.SurvivalScalingMode.parse(Config.survivalScalingMode) == ZombieSurvivalScalingHelper.SurvivalScalingMode.DISABLED) {
            return "mode disabled";
        }

        if (!computation.active()) {
            return "inactive";
        }

        if (!ZombieFamilyHelper.isZombieFamily(zombie)) {
            return "not zombie family";
        }

        return "active";
    }

    public record ZombieSurvivalScalingInspection(
        net.minecraft.core.BlockPos pos,
        String entityType,
        int worldDay,
        int scalingDays,
        boolean enabled,
        int startDay,
        boolean useNearestPlayer,
        double radius,
        int checkIntervalTicks,
        boolean nearPlayer,
        double damageMultiplier,
        double speedMultiplier,
        double followRangeMultiplier,
        double armorMultiplier,
        double healthMultiplier,
        boolean hasDamageModifier,
        boolean hasSpeedModifier,
        boolean hasFollowRangeModifier,
        boolean hasArmorModifier,
        boolean hasHealthModifier,
        double attackDamageValue,
        double movementSpeedValue,
        double followRangeValue,
        double armorValue,
        double maxHealthValue,
        double damageModifierAmount,
        double speedModifierAmount,
        double followRangeModifierAmount,
        double armorModifierAmount,
        double healthModifierAmount,
        boolean active,
        String reason
    ) {
        private static ZombieSurvivalScalingInspection empty() {
            return new ZombieSurvivalScalingInspection(
                net.minecraft.core.BlockPos.ZERO,
                "unknown",
                0,
                0,
                false,
                0,
                false,
                0.0D,
                0,
                false,
                1.0D,
                1.0D,
                1.0D,
                1.0D,
                1.0D,
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
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                false,
                "empty"
            );
        }
    }
}
