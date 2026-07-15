package infinitygroup.zlogic.horde;

import com.mojang.logging.LogUtils;
import infinitygroup.zlogic.Config;
import infinitygroup.zlogic.perf.PerformanceTracker;
import infinitygroup.zlogic.zombie.ZombieFamilyHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.slf4j.Logger;

import java.util.List;

public final class ZombieHordeClimbHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ZombieHordeClimbHandler() {
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!Config.enableZombieHordeClimb) {
            return;
        }

        Entity entity = event.getEntity();
        if (!(entity instanceof Zombie zombie)) {
            return;
        }

        if (!(zombie.level() instanceof ServerLevel level) || level.isClientSide()) {
            return;
        }

        if (!ZombieFamilyHelper.isZombieFamily(zombie) || !zombie.isAlive() || zombie.isRemoved() || zombie.isNoAi()) {
            return;
        }

        PerformanceTracker.recordEntityProcessed();
        PerformanceTracker.recordZombieProcessed();

        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }

        if (zombie.isInWaterOrBubble() || zombie.isInLava()) {
            return;
        }

        long gameTime = level.getGameTime();
        long nextTick = HordeClimbHelper.getNextTick(zombie);
        if (nextTick > gameTime) {
            return;
        }

        long lastTick = HordeClimbHelper.getLastTick(zombie);
        int interval = Math.max(1, Config.hordeClimbCheckIntervalTicks);
        if (lastTick > 0L && gameTime - lastTick < interval) {
            return;
        }

        HordeClimbHelper.HordeClimbAssessment assessment = HordeClimbHelper.inspect(level, zombie);
        HordeClimbHelper.recordEvaluation(zombie, assessment, gameTime);

        if (!assessment.active()) {
            if (HordeBodyStackAssistHelper.tryBodyStackAssist(level, zombie, assessment, gameTime)) {
                HordeClimbHelper.recordActivationMode(zombie, HordeClimbHelper.ActivationMode.BODY_STACK_ASSIST);
                debug(
                    "ZLOGIC_HORDE_BODY_STACK_TRIGGERED reason={} groupSize={} targetDistance={} cooldown={} modeFallback=inactive",
                    assessment.reason(),
                    assessment.groupSize(),
                    formatDouble(assessment.horizontalDistance()),
                    Config.hordeClimbBodyStackCooldownTicks
                );
                return;
            }

            HordeClimbHelper.recordActivationMode(zombie, HordeClimbHelper.ActivationMode.NONE);
            debug(
                "ZLOGIC_HORDE_CLIMB_SKIPPED mode={} reason={} groupSize={} siegeGroupSize={} targetDistance={} obstacleDistance={} obstacleHeight={} naturalSlope={} wallLikeObstacle={}",
                assessment.activationMode(),
                assessment.reason(),
                assessment.groupSize(),
                assessment.siegeGroupSize(),
                formatDouble(assessment.horizontalDistance()),
                formatDouble(assessment.obstacleDistance()),
                assessment.obstacleHeight(),
                assessment.naturalSlopeSuppressed(),
                assessment.wallLikeObstacle()
            );
            return;
        }

        if (zombie.getRandom().nextDouble() > Config.hordeClimbAttemptChance) {
            if (HordeBodyStackAssistHelper.tryBodyStackAssist(level, zombie, assessment, gameTime)) {
                HordeClimbHelper.recordActivationMode(zombie, HordeClimbHelper.ActivationMode.BODY_STACK_ASSIST);
                debug(
                    "ZLOGIC_HORDE_BODY_STACK_TRIGGERED reason={} groupSize={} targetDistance={} cooldown={} modeFallback=chance",
                    assessment.reason(),
                    assessment.groupSize(),
                    formatDouble(assessment.horizontalDistance()),
                    Config.hordeClimbBodyStackCooldownTicks
                );
                return;
            }

            HordeClimbHelper.recordActivationMode(zombie, HordeClimbHelper.ActivationMode.NONE);
            return;
        }

        Vec3 direction = assessment.direction();
        Vec3 horizontal = new Vec3(direction.x, 0.0D, direction.z);
        if (horizontal.lengthSqr() <= 0.0001D) {
            horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            horizontal = horizontal.normalize();
        }

        double verticalBoost;
        double forwardBoost;
        if (assessment.activationMode() == HordeClimbHelper.ActivationMode.SIEGE_CLIMB) {
            double climbHeight = Math.max(0.0D, assessment.obstacleHeight());
            verticalBoost = Math.max(0.0D, Config.hordeSiegeClimbVerticalBoostBase + climbHeight * Config.hordeSiegeClimbVerticalBoostPerHeight);
            forwardBoost = Math.max(0.0D, Config.hordeSiegeClimbForwardBoost);
        } else {
            verticalBoost = Math.max(0.0D, Config.hordeClimbVerticalBoostBase + assessment.effectiveHeight() * Config.hordeClimbVerticalBoostPerHeight);
            forwardBoost = Math.max(0.0D, Config.hordeClimbForwardBoost);
        }
        Vec3 impulse = horizontal.scale(forwardBoost).add(0.0D, verticalBoost, 0.0D);
        zombie.setDeltaMovement(zombie.getDeltaMovement().add(impulse));
        zombie.hasImpulse = true;

        List<Zombie> nearbyZombies = HordeClimbHelper.collectEligibleNearbyZombies(level, zombie);
        HordeClimbVisualHelper.playClimbEffects(level, zombie, nearbyZombies, assessment);
        HordeClimbHelper.scheduleCooldown(zombie, gameTime, assessment.activationMode());
        if (assessment.activationMode() == HordeClimbHelper.ActivationMode.SIEGE_CLIMB && assessment.obstaclePos() != null) {
            HordeClimbHelper.scheduleSiegeAreaCooldown(level, assessment.obstaclePos(), gameTime);
        }
        HordeClimbHelper.recordActivationMode(zombie, assessment.activationMode());

        if (assessment.activationMode() == HordeClimbHelper.ActivationMode.SIEGE_CLIMB) {
            debug(
                "ZLOGIC_HORDE_SIEGE_CLIMB_TRIGGERED groupSize={} siegeGroupSize={} targetDistance={} obstacleHeight={} impulse={} areaCooldown={}",
                assessment.groupSize(),
                assessment.siegeGroupSize(),
                formatDouble(assessment.horizontalDistance()),
                assessment.obstacleHeight(),
                formatVec(impulse),
                Math.max(1, Config.hordeSiegeClimbAreaCooldownTicks)
            );
        } else {
            debug(
                "Horde climb triggered: zombie={} mode={} group={} height={} impulse={} targetKind={} nextTick={}",
                zombie.getType().toShortString(),
                assessment.activationMode(),
                assessment.groupSize(),
                formatDouble(assessment.effectiveHeight()),
                formatVec(impulse),
                assessment.targetKind(),
                HordeClimbHelper.getNextTick(zombie)
            );
        }
    }

    private static void debug(String message, Object... args) {
        if (Config.hordeClimbDebugLogs || Config.hordeSiegeClimbDebugLogs || Config.debugLogs) {
            LOGGER.debug(message, args);
        }
    }

    private static String formatDouble(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String formatVec(Vec3 vec) {
        if (vec == null) {
            return "0.00,0.00,0.00";
        }

        return formatDouble(vec.x) + "," + formatDouble(vec.y) + "," + formatDouble(vec.z);
    }
}
