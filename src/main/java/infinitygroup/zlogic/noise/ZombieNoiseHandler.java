package infinitygroup.zlogic.noise;

import com.mojang.logging.LogUtils;
import infinitygroup.zlogic.Config;
import infinitygroup.zlogic.machine.MachineAttackHandler;
import infinitygroup.zlogic.perf.PerformanceTracker;
import infinitygroup.zlogic.zombie.ZombieEligibilityHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.List;

public final class ZombieNoiseHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NEXT_NOISE_NAV_TICK_KEY = "zlogic_next_noise_nav_tick";
    private static final String HAS_NOISE_TARGET_KEY = "zlogic_has_noise_target";
    private static final String NOISE_TARGET_X_KEY = "zlogic_noise_target_x";
    private static final String NOISE_TARGET_Y_KEY = "zlogic_noise_target_y";
    private static final String NOISE_TARGET_Z_KEY = "zlogic_noise_target_z";

    private ZombieNoiseHandler() {
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!Config.enableNoiseSystem) {
            return;
        }

        if (!(event.getEntity() instanceof Zombie zombie)) {
            return;
        }

        if (zombie.level().isClientSide() || !(zombie.level() instanceof ServerLevel level)) {
            return;
        }

        if (!ZombieEligibilityHelper.isEligibleForMachineAttack(zombie)) {
            return;
        }

        int interval = Math.max(1, Config.noiseCheckIntervalTicks);
        if (zombie.tickCount % interval != 0) {
            return;
        }

        if (MachineAttackHandler.hasValidMachineTarget(level, zombie)) {
            if (Config.debugLogs || Config.zombieNoiseDebugLogs) {
                debug(
                    "Zombie noise skipped due to active machine target: zombie={} pos={} dimension={}",
                    zombie.getType().toShortString(),
                    zombie.blockPosition(),
                    level.dimension().location()
                );
            }
            return;
        }

        LivingEntity currentTarget = zombie.getTarget();
        if (currentTarget instanceof ServerPlayer playerTarget && isValidPlayerTarget(playerTarget)) {
            return;
        }

        if (currentTarget != null && currentTarget.isAlive() && !currentTarget.isRemoved() && !(currentTarget instanceof ServerPlayer)) {
            return;
        }

        List<NoiseEvent> noises = NoiseManager.getNoises(level);
        if (noises.isEmpty()) {
            return;
        }

        PerformanceTracker.recordEntityProcessed();
        PerformanceTracker.recordZombieProcessed();
        PerformanceTracker.recordNoiseChecks(noises.size());

        NoiseEvent selectedNoise = selectNoise(zombie, noises);
        if (selectedNoise == null) {
            return;
        }

        ReactionResult reaction = reactToNoise(level, zombie, selectedNoise, currentTarget, true);
        if (!reaction.reacted()) {
            return;
        }

        alertNearbyZombies(level, zombie, selectedNoise, reaction.playerTarget());
    }

    private static NoiseEvent selectNoise(Zombie zombie, List<NoiseEvent> noises) {
        double multiplier = Math.max(0.0D, Config.zombieHearNoiseRadiusMultiplier);
        if (multiplier <= 0.0D) {
            return null;
        }

        NoiseEvent bestNoise = null;
        double bestDistance = Double.MAX_VALUE;
        double bestRadius = -1.0D;
        int bestRemaining = -1;
        Vec3 zombieCenter = zombie.position();

        for (NoiseEvent noise : noises) {
            if (!noise.attractsZombies()) {
                continue;
            }

            double effectiveRadius = noise.radius() * multiplier;
            if (effectiveRadius <= 0.0D) {
                continue;
            }

            double distance = zombieCenter.distanceToSqr(Vec3.atCenterOf(noise.pos()));
            if (distance > effectiveRadius * effectiveRadius) {
                continue;
            }

            boolean better = false;
            if (distance < bestDistance) {
                better = true;
            } else if (distance == bestDistance && noise.radius() > bestRadius) {
                better = true;
            } else if (distance == bestDistance && noise.radius() == bestRadius && noise.remainingTicks() > bestRemaining) {
                better = true;
            }

            if (better) {
                bestNoise = noise;
                bestDistance = distance;
                bestRadius = noise.radius();
                bestRemaining = noise.remainingTicks();
            }
        }

        return bestNoise;
    }

    private static ReactionResult reactToNoise(ServerLevel level, Zombie zombie, NoiseEvent noise, LivingEntity currentTarget, boolean allowMove) {
        ServerPlayer playerTarget = null;
        boolean canOverrideTarget = canOverrideCurrentTarget(zombie, currentTarget);
        if (Config.zombieTargetPlayerNearNoise) {
            playerTarget = findPlayerNearNoise(level, noise);
        }

        if (playerTarget != null) {
            if (!canOverrideTarget) {
                return ReactionResult.notReacted();
            }

            if (currentTarget == playerTarget && currentTarget.isAlive() && !currentTarget.isRemoved()) {
                if (isSameNoiseTarget(zombie, noise.pos()) && !shouldRepathNoise(zombie, noise.pos(), level.getGameTime())) {
                    return ReactionResult.reacted(playerTarget);
                }

                storeNoiseTarget(zombie, noise.pos());
                scheduleNoiseRepath(zombie, level.getGameTime());
                return ReactionResult.reacted(playerTarget);
            }

            if (isSameNoiseTarget(zombie, noise.pos()) && !shouldRepathNoise(zombie, noise.pos(), level.getGameTime())) {
                if (Config.debugLogs || Config.zombieNoiseDebugLogs) {
                    debug(
                        "Zombie noise repath skipped by cooldown: zombie={} pos={} dimension={}",
                        zombie.getType().toShortString(),
                        noise.pos(),
                        level.dimension().location()
                    );
                }
                return ReactionResult.reacted(playerTarget);
            }

            zombie.setTarget(playerTarget);
            storeNoiseTarget(zombie, noise.pos());
            scheduleNoiseRepath(zombie, level.getGameTime());
            debug(
                "Zombie reacted to noise with player target: zombie={} player={} type={} pos={} dimension={}",
                zombie.getType().toShortString(),
                playerTarget.getGameProfile().getName(),
                noise.type(),
                noise.pos(),
                level.dimension().location()
            );
            return ReactionResult.reacted(playerTarget);
        }

        if (!allowMove || !Config.zombieMoveToNoise || !canOverrideTarget) {
            return ReactionResult.notReacted();
        }

        if (isSameNoiseTarget(zombie, noise.pos()) && !shouldRepathNoise(zombie, noise.pos(), level.getGameTime())) {
            if (Config.debugLogs || Config.zombieNoiseDebugLogs) {
                debug(
                    "Zombie noise repath skipped by cooldown: zombie={} pos={} dimension={}",
                    zombie.getType().toShortString(),
                    noise.pos(),
                    level.dimension().location()
                );
            }
            return ReactionResult.reacted(null);
        }

        PathNavigation navigation = zombie.getNavigation();
        navigation.moveTo(noise.pos().getX() + 0.5D, noise.pos().getY(), noise.pos().getZ() + 0.5D, 1.0D);
        storeNoiseTarget(zombie, noise.pos());
        scheduleNoiseRepath(zombie, level.getGameTime());
        debug(
            "Zombie moved toward noise: zombie={} type={} pos={} dimension={}",
            zombie.getType().toShortString(),
            noise.type(),
            noise.pos(),
            level.dimension().location()
        );
        return ReactionResult.reacted(null);
    }

    private static void alertNearbyZombies(ServerLevel level, Zombie sourceZombie, NoiseEvent noise, ServerPlayer playerTarget) {
        if (Config.zombieNoiseAlertMaxZombies <= 0 || Config.zombieNoiseAlertRadius <= 0.0D) {
            return;
        }

        List<Zombie> nearbyZombies = level.getEntitiesOfClass(
            Zombie.class,
            sourceZombie.getBoundingBox().inflate(Config.zombieNoiseAlertRadius),
            candidate -> candidate != sourceZombie && candidate.isAlive() && !candidate.isRemoved() && ZombieEligibilityHelper.isEligibleForMachineAttack(candidate)
        );

        if (nearbyZombies.isEmpty()) {
            return;
        }

        nearbyZombies.sort(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(sourceZombie)));

        int alerted = 0;
        for (Zombie candidate : nearbyZombies) {
            if (alerted >= Config.zombieNoiseAlertMaxZombies) {
                break;
            }

            if (candidate.getTarget() != null && candidate.getTarget().isAlive() && !candidate.getTarget().isRemoved() && !(candidate.getTarget() instanceof ServerPlayer)) {
                continue;
            }

            if (Config.zombieNoiseRequiresLineOfSight
                && !candidate.hasLineOfSight(sourceZombie)
                && (playerTarget == null || !candidate.hasLineOfSight(playerTarget))) {
                continue;
            }

            ReactionResult reaction = reactToNoise(level, candidate, noise, candidate.getTarget(), true);
            if (!reaction.reacted()) {
                continue;
            }

            alerted++;
        }

        if (alerted > 0) {
            debug(
                "Zombie noise alert propagated: source={} alerted={} type={} pos={} dimension={}",
                sourceZombie.getType().toShortString(),
                alerted,
                noise.type(),
                noise.pos(),
                level.dimension().location()
            );
        }
    }

    private static ServerPlayer findPlayerNearNoise(ServerLevel level, NoiseEvent noise) {
        double radius = Math.max(0.0D, Config.zombieNoiseTargetPlayerRadius);
        if (radius <= 0.0D) {
            return null;
        }

        Vec3 center = Vec3.atCenterOf(noise.pos());
        List<ServerPlayer> players = level.getEntitiesOfClass(
            ServerPlayer.class,
            new net.minecraft.world.phys.AABB(center.x - radius, center.y - radius, center.z - radius, center.x + radius, center.y + radius, center.z + radius),
            ZombieNoiseHandler::isValidPlayerTarget
        );

        if (players.isEmpty()) {
            return null;
        }

        if (noise.sourceEntityId() != null) {
            for (ServerPlayer player : players) {
                if (noise.sourceEntityId().equals(player.getUUID())) {
                    return player;
                }
            }
        }

        players.sort(Comparator.comparingDouble(player -> player.distanceToSqr(center)));
        return players.get(0);
    }

    private static boolean canOverrideCurrentTarget(Zombie zombie, LivingEntity currentTarget) {
        if (currentTarget == null || currentTarget.isRemoved() || !currentTarget.isAlive()) {
            return true;
        }

        if (!(currentTarget instanceof ServerPlayer)) {
            return false;
        }

        return zombie.getRandom().nextDouble() < Config.zombieNoiseRetargetChance;
    }

    private static boolean isSameNoiseTarget(Zombie zombie, BlockPos pos) {
        BlockPos current = readNoiseTarget(zombie);
        return current != null && current.equals(pos);
    }

    private static boolean shouldRepathNoise(Zombie zombie, BlockPos pos, long gameTime) {
        if (zombie == null || pos == null) {
            return false;
        }

        PathNavigation navigation = zombie.getNavigation();
        if (navigation.isDone()) {
            return true;
        }

        long nextNavTick = getNextNoiseNavTick(zombie);
        if (nextNavTick <= 0L || gameTime >= nextNavTick) {
            return true;
        }

        double repathDistance = Math.max(0.0D, Config.zombieMachineAttackRange + 1.5D);
        return zombie.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > repathDistance * repathDistance;
    }

    private static void scheduleNoiseRepath(Zombie zombie, long gameTime) {
        if (zombie == null) {
            return;
        }

        zombie.getPersistentData().putLong(NEXT_NOISE_NAV_TICK_KEY, gameTime + Math.max(1, Config.zombieNoiseRepathIntervalTicks));
        zombie.getPersistentData().putBoolean(HAS_NOISE_TARGET_KEY, true);
    }

    private static BlockPos readNoiseTarget(Zombie zombie) {
        if (zombie == null || !zombie.getPersistentData().getBoolean(HAS_NOISE_TARGET_KEY)) {
            return null;
        }

        if (!zombie.getPersistentData().contains(NOISE_TARGET_X_KEY) || !zombie.getPersistentData().contains(NOISE_TARGET_Y_KEY) || !zombie.getPersistentData().contains(NOISE_TARGET_Z_KEY)) {
            return null;
        }

        return new BlockPos(
            zombie.getPersistentData().getInt(NOISE_TARGET_X_KEY),
            zombie.getPersistentData().getInt(NOISE_TARGET_Y_KEY),
            zombie.getPersistentData().getInt(NOISE_TARGET_Z_KEY)
        );
    }

    private static void storeNoiseTarget(Zombie zombie, BlockPos pos) {
        if (zombie == null || pos == null) {
            return;
        }

        zombie.getPersistentData().putBoolean(HAS_NOISE_TARGET_KEY, true);
        zombie.getPersistentData().putInt(NOISE_TARGET_X_KEY, pos.getX());
        zombie.getPersistentData().putInt(NOISE_TARGET_Y_KEY, pos.getY());
        zombie.getPersistentData().putInt(NOISE_TARGET_Z_KEY, pos.getZ());
    }

    private static long getNextNoiseNavTick(Zombie zombie) {
        if (zombie == null || !zombie.getPersistentData().contains(NEXT_NOISE_NAV_TICK_KEY)) {
            return 0L;
        }

        return zombie.getPersistentData().getLong(NEXT_NOISE_NAV_TICK_KEY);
    }

    private static boolean isValidPlayerTarget(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator() && !player.isCreative();
    }

    private static void debug(String message, Object... args) {
        if (Config.debugLogs || Config.zombieNoiseDebugLogs) {
            LOGGER.info("[" + infinitygroup.zlogic.Zlogic.MODID + "] " + message, args);
        }
    }

    private record ReactionResult(boolean reacted, ServerPlayer playerTarget) {
        private static ReactionResult notReacted() {
            return new ReactionResult(false, null);
        }

        private static ReactionResult reacted(ServerPlayer playerTarget) {
            return new ReactionResult(true, playerTarget);
        }
    }
}
