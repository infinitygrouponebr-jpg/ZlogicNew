package infinitygroup.zlogic.zombie;

import com.mojang.logging.LogUtils;
import infinitygroup.zlogic.Config;
import infinitygroup.zlogic.perf.PerformanceTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ZombieAggressionHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LAST_TARGET_ACQUIRED_TICK_KEY = "zlogic_aggression_last_target_tick";

    private ZombieAggressionHandler() {
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!Config.enableZombieAggressionSystem) {
            return;
        }

        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || !(entity instanceof Zombie zombie)) {
            return;
        }

        if (!(zombie.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!ZombieEligibilityHelper.isEligibleForAggression(zombie)) {
            return;
        }

        PerformanceTracker.recordEntityProcessed();
        PerformanceTracker.recordZombieProcessed();
        PerformanceTracker.recordAggressionCheck();

        int interval = Math.max(1, Config.aggressionCheckIntervalTicks);
        if (zombie.tickCount % interval != 0) {
            return;
        }

        LivingEntity currentTarget = zombie.getTarget();
        if (currentTarget != null && currentTarget.isAlive() && !currentTarget.isRemoved() && !(currentTarget instanceof ServerPlayer currentPlayer)) {
            return;
        }

        if (currentTarget instanceof ServerPlayer currentPlayer && isValidPlayerTarget(currentPlayer)) {
            handleExistingPlayerTarget(serverLevel, zombie, currentPlayer);
            return;
        }

        ServerPlayer newTarget = findPlayerTarget(serverLevel, zombie, null);
        if (newTarget == null) {
            if (currentTarget != null && (!currentTarget.isAlive() || currentTarget.isRemoved() || (currentTarget instanceof ServerPlayer currentPlayer && !isValidPlayerTarget(currentPlayer)))) {
                zombie.setTarget(null);
            }
            return;
        }

        assignTarget(serverLevel, zombie, newTarget, true);
    }

    private static void handleExistingPlayerTarget(ServerLevel level, Zombie zombie, ServerPlayer currentTarget) {
        long acquiredTick = getLastTargetAcquiredTick(zombie);
        long age = acquiredTick > 0L ? zombie.tickCount - acquiredTick : Long.MAX_VALUE;

        if (age < Config.zombieForgetTargetAfterTicks) {
            return;
        }

        ServerPlayer replacement = findPlayerTarget(level, zombie, currentTarget);
        if (replacement == null) {
            setLastTargetAcquiredTick(zombie, zombie.tickCount);
            return;
        }

        if (replacement == currentTarget) {
            setLastTargetAcquiredTick(zombie, zombie.tickCount);
            return;
        }

        if (zombie.getRandom().nextDouble() >= Config.zombieRetargetChance) {
            setLastTargetAcquiredTick(zombie, zombie.tickCount);
            return;
        }

        assignTarget(level, zombie, replacement, true);
    }

    private static ServerPlayer findPlayerTarget(ServerLevel level, Zombie zombie, ServerPlayer currentTarget) {
        double followRange = getFollowRange(zombie);
        double searchRange = Math.max(1.0D, followRange + Config.zombieExtraTargetRange);
        AABB searchBox = zombie.getBoundingBox().inflate(searchRange);

        List<ServerPlayer> candidates = level.getEntitiesOfClass(ServerPlayer.class, searchBox, ZombieAggressionHandler::isValidPlayerTarget);
        if (candidates.isEmpty()) {
            return null;
        }

        if (Config.zombiePreferNearestPlayer) {
            ServerPlayer nearest = null;
            double nearestDistance = Double.MAX_VALUE;

            for (ServerPlayer player : candidates) {
                if (player == currentTarget && candidates.size() > 1) {
                    continue;
                }

                double distance = player.distanceToSqr(zombie);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = player;
                }
            }

            return nearest != null ? nearest : currentTarget;
        }

        if (currentTarget != null && candidates.size() > 1) {
            List<ServerPlayer> alternateTargets = new ArrayList<>();
            for (ServerPlayer player : candidates) {
                if (player != currentTarget) {
                    alternateTargets.add(player);
                }
            }

            if (!alternateTargets.isEmpty()) {
                return alternateTargets.get(zombie.getRandom().nextInt(alternateTargets.size()));
            }
        }

        return candidates.get(zombie.getRandom().nextInt(candidates.size()));
    }

    private static void assignTarget(ServerLevel level, Zombie zombie, ServerPlayer target, boolean alertNearbyZombies) {
        LivingEntity currentTarget = zombie.getTarget();
        if (currentTarget == target && currentTarget != null && currentTarget.isAlive() && !currentTarget.isRemoved()) {
            setLastTargetAcquiredTick(zombie, zombie.tickCount);
            return;
        }

        zombie.setTarget(target);
        setLastTargetAcquiredTick(zombie, zombie.tickCount);

        if (Config.debugLogs) {
            LOGGER.info(
                "Zlogic aggression target acquired: {} -> {} at {} in {}",
                zombie.getType().toShortString(),
                target.getGameProfile().getName(),
                zombie.blockPosition(),
                level.dimension().location()
            );
        }

        if (!alertNearbyZombies) {
            return;
        }

        alertNearbyZombies(level, zombie, target);
    }

    private static void alertNearbyZombies(ServerLevel level, Zombie sourceZombie, ServerPlayer target) {
        if (!Config.zombieAlertNearbyZombies || Config.zombieAlertMaxTargets <= 0 || Config.zombieAlertRadius <= 0.0D) {
            return;
        }

        AABB alertBox = sourceZombie.getBoundingBox().inflate(Config.zombieAlertRadius);
        List<Zombie> nearbyZombies = level.getEntitiesOfClass(Zombie.class, alertBox, candidate -> candidate != sourceZombie && ZombieEligibilityHelper.isEligibleForAggression(candidate));
        if (nearbyZombies.isEmpty()) {
            return;
        }

        nearbyZombies.sort(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(sourceZombie)));

        int alerted = 0;
        for (Zombie candidate : nearbyZombies) {
            if (alerted >= Config.zombieAlertMaxTargets) {
                break;
            }

            if (!isEligibleAlertTarget(candidate)) {
                continue;
            }

            if (candidate.getTarget() == target) {
                continue;
            }

            if (Config.zombieAlertRequiresLineOfSight
                && !candidate.hasLineOfSight(target)
                && !candidate.hasLineOfSight(sourceZombie)) {
                continue;
            }

            candidate.setTarget(target);
            setLastTargetAcquiredTick(candidate, candidate.tickCount);
            alerted++;
        }

        if (Config.debugLogs && alerted > 0) {
            LOGGER.info(
                "Zlogic aggression alert: source={} target={} alerted={} at {} in {}",
                sourceZombie.getType().toShortString(),
                target.getGameProfile().getName(),
                alerted,
                sourceZombie.blockPosition(),
                level.dimension().location()
            );
        }
    }

    private static boolean isEligibleAlertTarget(Zombie zombie) {
        LivingEntity target = zombie.getTarget();
        if (target == null) {
            return true;
        }

        return !target.isAlive() || target.isRemoved();
    }

    private static boolean isValidPlayerTarget(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator() && !player.isCreative();
    }

    private static double getFollowRange(Zombie zombie) {
        AttributeInstance followRange = zombie.getAttribute(Attributes.FOLLOW_RANGE);
        return followRange != null ? followRange.getValue() : 16.0D;
    }

    private static long getLastTargetAcquiredTick(Zombie zombie) {
        return zombie.getPersistentData().getLong(LAST_TARGET_ACQUIRED_TICK_KEY);
    }

    private static void setLastTargetAcquiredTick(Zombie zombie, long tick) {
        zombie.getPersistentData().putLong(LAST_TARGET_ACQUIRED_TICK_KEY, tick);
    }
}
