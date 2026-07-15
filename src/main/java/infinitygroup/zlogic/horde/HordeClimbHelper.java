package infinitygroup.zlogic.horde;

import infinitygroup.zlogic.Config;
import infinitygroup.zlogic.barrier.ZombieBarrierBreakHelper;
import infinitygroup.zlogic.machine.MachineAttackHandler;
import infinitygroup.zlogic.zombie.ZombieEligibilityHelper;
import infinitygroup.zlogic.zombie.ZombieFamilyHelper;
import infinitygroup.zlogic.zombie.ZombieTankHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HordeClimbHelper {
    public static final String NEXT_TICK_KEY = "zlogic_horde_climb_next_tick";
    public static final String LAST_GROUP_SIZE_KEY = "zlogic_horde_climb_last_group_size";
    public static final String LAST_HEIGHT_KEY = "zlogic_horde_climb_last_height";
    public static final String LAST_TICK_KEY = "zlogic_horde_climb_last_tick";
    public static final String LAST_MODE_KEY = "zlogic_horde_climb_last_mode";

    private static final Map<String, Long> SIEGE_AREA_COOLDOWNS = new ConcurrentHashMap<>();

    private HordeClimbHelper() {
    }

    public static HordeClimbAssessment inspect(ServerLevel level, Zombie zombie) {
        if (level == null || zombie == null || !ZombieFamilyHelper.isZombieFamily(zombie)) {
            return HordeClimbAssessment.empty();
        }

        boolean enabled = Config.enableZombieHordeClimb;
        boolean eligible = isEligibleZombie(zombie);
        boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
        boolean alive = zombie.isAlive() && !zombie.isRemoved();
        boolean aiEnabled = !zombie.isNoAi();
        boolean hydrated = !zombie.isInWaterOrBubble() && !zombie.isInLava();
        int interval = Math.max(1, Config.hordeClimbCheckIntervalTicks);
        long gameTime = level.getGameTime();
        int cooldownRemaining = getCooldownRemaining(zombie, gameTime);

        TargetContext targetContext = resolveTarget(level, zombie);
        List<Zombie> nearbyZombies = collectEligibleNearbyZombies(level, zombie);
        int groupSize = nearbyZombies.size() + 1;
        double calculatedHeight = calculateHeight(groupSize);
        double effectiveHeight = Math.min(calculatedHeight, Math.max(0.0D, Config.hordeClimbMaxHeight));
        boolean groupEnough = groupSize >= Math.max(1, Config.hordeClimbMinZombies);

        Vec3 boostDirection = resolveBoostDirection(zombie, targetContext);
        boolean targetRequirementMet = !Config.hordeClimbRequireTarget || targetContext.hasTarget();
        boolean targetElevationActive = false;
        boolean obstacleModeActive = false;
        boolean isLeader = !Config.hordeClimbLeaderOnly;
        String targetElevationReason = "DISABLED";
        String obstacleReason = "DISABLED";
        ActivationMode activationMode = ActivationMode.NONE;
        String reason = "DISABLED";
        int obstacleHeight = 0;
        double obstacleDistance = 0.0D;
        BlockPos obstaclePos = null;
        boolean wallLikeObstacle = false;
        boolean naturalSlopeSuppressed = false;
        boolean siegeEligible = false;
        int siegeGroupSize = 0;
        int areaCooldownRemaining = 0;
        TargetElevationResult elevationResult = TargetElevationResult.inactive("DISABLED");

        pruneSiegeAreaCooldowns(gameTime);

        if (enabled && eligible && alive && aiEnabled && hydrated && !peaceful && groupEnough && cooldownRemaining <= 0 && targetRequirementMet) {
            Zombie leader = findLeaderZombie(level, zombie, targetContext, nearbyZombies);
            isLeader = !Config.hordeClimbLeaderOnly || leader == zombie;

            if (Config.enableHordeSiegeClimb) {
                SiegeEvaluation siege = evaluateSiegeClimb(level, zombie, targetContext, nearbyZombies, boostDirection, isLeader, gameTime);
                siegeEligible = siege.eligible();
                siegeGroupSize = siege.groupSize();
                obstacleHeight = siege.obstacle().height();
                obstacleDistance = siege.obstacle().distance();
                obstaclePos = siege.obstacle().pos();
                wallLikeObstacle = siege.obstacle().wallLike();
                naturalSlopeSuppressed = siege.naturalSlopeSuppressed();
                areaCooldownRemaining = siege.areaCooldownRemaining();
                obstacleReason = siege.obstacle().reason();
                reason = siege.reason();

                if (siege.active()) {
                    activationMode = ActivationMode.SIEGE_CLIMB;
                    effectiveHeight = Math.max(0.0D, Math.min(siege.obstacle().height(), Config.hordeSiegeClimbMaxObstacleHeight));
                }
            } else {
                ObstacleResult obstacleResult = detectObstacle(
                    level,
                    zombie,
                    boostDirection,
                    effectiveHeight,
                    Math.max(0.1D, Config.hordeClimbObstacleCheckDistance),
                    Math.max(1, Config.hordeClimbMinObstacleHeight),
                    Math.max(1, Config.hordeClimbMaxObstacleScanHeight),
                    false
                );
                obstacleModeActive = obstacleResult.detected();
                obstacleReason = obstacleResult.reason();
                obstacleHeight = obstacleResult.height();
                obstacleDistance = obstacleResult.distance();
                obstaclePos = obstacleResult.pos();
                wallLikeObstacle = obstacleResult.wallLike();

                elevationResult = detectTargetElevation(level, zombie, targetContext, effectiveHeight, obstacleResult, targetContext.blockYDifference(), isLeader);
                targetElevationActive = elevationResult.active();
                targetElevationReason = elevationResult.reason();

                if (obstacleModeActive) {
                    activationMode = ActivationMode.OBSTACLE;
                    reason = "VALID_LEGACY_OBSTACLE";
                } else if (targetElevationActive) {
                    activationMode = ActivationMode.TARGET_ELEVATION;
                    reason = "VALID_LEGACY_TARGET_ELEVATION";
                } else {
                    reason = elevationResult.reason().equals("INACTIVE") ? obstacleReason : elevationResult.reason();
                }
            }
        } else {
            reason = determineReason(enabled, eligible, alive, aiEnabled, hydrated, peaceful, groupEnough, cooldownRemaining, targetContext, targetRequirementMet, isLeader);
        }

        boolean active = activationMode != ActivationMode.NONE;
        if (!active && Config.hordeClimbVerboseDebug && targetContext.hasTarget()) {
            reason = appendVerboseReason(
                reason,
                targetContext,
                effectiveHeight,
                obstacleReason,
                targetElevationReason,
                isLeader,
                siegeGroupSize,
                obstacleDistance,
                naturalSlopeSuppressed,
                wallLikeObstacle,
                areaCooldownRemaining
            );
        }

        return new HordeClimbAssessment(
            enabled,
            eligible,
            active,
            reason,
            groupSize,
            siegeGroupSize,
            calculatedHeight,
            effectiveHeight,
            obstacleModeActive,
            targetElevationActive,
            siegeEligible,
            obstacleHeight,
            obstacleDistance,
            obstaclePos,
            wallLikeObstacle,
            naturalSlopeSuppressed,
            elevationResult.obstacleBetween(),
            elevationResult.obstacleHeight(),
            targetContext.kind(),
            targetContext.targetY(),
            targetContext.zombieY(),
            targetContext.yDifference(),
            targetContext.blockYDifference(),
            targetContext.horizontalDistance(),
            activationMode,
            obstacleReason,
            targetElevationReason,
            cooldownRemaining,
            areaCooldownRemaining,
            targetContext.hasTarget(),
            targetRequirementMet,
            isLeader,
            targetContext.kind(),
            boostDirection,
            interval
        );
    }

    public static boolean canAttempt(ServerLevel level, Zombie zombie) {
        return inspect(level, zombie).active();
    }

    public static int getCooldownRemaining(Zombie zombie, long gameTime) {
        if (zombie == null) {
            return 0;
        }

        long nextTick = getNextTick(zombie);
        return (int) Math.max(0L, nextTick - gameTime);
    }

    public static void recordEvaluation(Zombie zombie, HordeClimbAssessment assessment, long gameTime) {
        if (zombie == null || assessment == null) {
            return;
        }

        var tag = zombie.getPersistentData();
        tag.putInt(LAST_GROUP_SIZE_KEY, assessment.groupSize());
        tag.putDouble(LAST_HEIGHT_KEY, assessment.effectiveHeight());
        tag.putLong(LAST_TICK_KEY, gameTime);
    }

    public static void recordActivationMode(Zombie zombie, ActivationMode mode) {
        if (zombie == null || mode == null) {
            return;
        }

        zombie.getPersistentData().putString(LAST_MODE_KEY, mode.name());
    }

    public static void scheduleCooldown(Zombie zombie, long gameTime) {
        scheduleCooldown(zombie, gameTime, ActivationMode.OBSTACLE);
    }

    public static void scheduleCooldown(Zombie zombie, long gameTime, ActivationMode mode) {
        if (zombie == null) {
            return;
        }

        int cooldown = mode == ActivationMode.SIEGE_CLIMB
            ? Math.max(1, Config.hordeSiegeClimbCooldownTicks)
            : Math.max(1, Config.hordeClimbCooldownTicks);
        zombie.getPersistentData().putLong(NEXT_TICK_KEY, gameTime + cooldown);
    }

    public static void scheduleSiegeAreaCooldown(ServerLevel level, BlockPos anchor, long gameTime) {
        if (level == null || anchor == null) {
            return;
        }

        SIEGE_AREA_COOLDOWNS.put(createAreaCooldownKey(level, anchor), gameTime);
    }

    public static long getNextTick(Zombie zombie) {
        if (zombie == null || !zombie.getPersistentData().contains(NEXT_TICK_KEY)) {
            return 0L;
        }

        return zombie.getPersistentData().getLong(NEXT_TICK_KEY);
    }

    public static int getLastGroupSize(Zombie zombie) {
        return zombie != null && zombie.getPersistentData().contains(LAST_GROUP_SIZE_KEY) ? zombie.getPersistentData().getInt(LAST_GROUP_SIZE_KEY) : 0;
    }

    public static double getLastHeight(Zombie zombie) {
        return zombie != null && zombie.getPersistentData().contains(LAST_HEIGHT_KEY) ? zombie.getPersistentData().getDouble(LAST_HEIGHT_KEY) : 0.0D;
    }

    public static long getLastTick(Zombie zombie) {
        return zombie != null && zombie.getPersistentData().contains(LAST_TICK_KEY) ? zombie.getPersistentData().getLong(LAST_TICK_KEY) : 0L;
    }

    public static String getLastActivationMode(Zombie zombie) {
        return zombie != null && zombie.getPersistentData().contains(LAST_MODE_KEY) ? zombie.getPersistentData().getString(LAST_MODE_KEY) : ActivationMode.NONE.name();
    }

    public static double calculateHeight(int groupSize) {
        if (groupSize < Math.max(1, Config.hordeClimbMinZombies)) {
            return 0.0D;
        }

        double height = Config.hordeClimbBaseHeight + ((groupSize - Config.hordeClimbMinZombies) * Config.hordeClimbHeightPerZombie);
        return Math.min(Math.max(0.0D, height), Math.max(0.0D, Config.hordeClimbMaxHeight));
    }

    public static boolean isEligibleZombie(Zombie zombie) {
        if (zombie == null || !ZombieFamilyHelper.isZombieFamily(zombie)) {
            return false;
        }

        if (ZombieTankHandler.isTankZombie(zombie)) {
            return false;
        }

        if (Config.hordeClimbOnlyEligibleZombies || Config.zlogicSystemsAffectOnlyZlogicZombies) {
            return ZombieEligibilityHelper.isEligibleForZlogicSystems(zombie);
        }

        return true;
    }

    public static List<Zombie> collectEligibleNearbyZombies(ServerLevel level, Zombie zombie) {
        double radius = Math.max(0.1D, Config.hordeClimbRadius);
        AABB box = zombie.getBoundingBox().inflate(radius);
        return level.getEntitiesOfClass(Zombie.class, box, candidate -> candidate != null && candidate != zombie && candidate.isAlive() && !candidate.isRemoved() && isEligibleZombie(candidate));
    }

    public static List<Zombie> collectSiegeEligibleNearbyZombies(ServerLevel level, Zombie zombie, TargetContext targetContext, ObstacleResult obstacleResult) {
        double radius = Math.max(0.1D, Config.hordeSiegeClimbGroupRadius);
        AABB box = zombie.getBoundingBox().inflate(radius);
        double targetMatchRadiusSq = 4.0D * 4.0D;
        double obstacleRadius = Math.max(radius, Math.max(0.1D, Config.hordeSiegeClimbMaxObstacleDistance) + 2.0D);
        double obstacleRadiusSq = obstacleRadius * obstacleRadius;

        return level.getEntitiesOfClass(Zombie.class, box, candidate -> {
            if (candidate == null || candidate == zombie || !candidate.isAlive() || candidate.isRemoved() || candidate.isInWaterOrBubble() || candidate.isInLava() || !isEligibleZombie(candidate)) {
                return false;
            }

            if (candidate.distanceToSqr(zombie) > radius * radius) {
                return false;
            }

            if (obstacleResult.detected()) {
                Vec3 obstacleCenter = Vec3.atCenterOf(obstacleResult.pos());
                if (candidate.position().distanceToSqr(obstacleCenter) > obstacleRadiusSq) {
                    return false;
                }
            }

            if (!targetContext.hasTarget()) {
                return true;
            }

            TargetContext candidateTarget = resolveTarget(level, candidate);
            if (!candidateTarget.hasTarget()) {
                return true;
            }

            if (candidateTarget.kind() != targetContext.kind()) {
                return false;
            }

            return candidateTarget.targetPosition().distanceToSqr(targetContext.targetPosition()) <= targetMatchRadiusSq;
        });
    }

    public static TargetContext resolveTargetContext(ServerLevel level, Zombie zombie) {
        if (level == null || zombie == null) {
            return new TargetContext(false, TargetKind.NONE, Vec3.ZERO, BlockPos.ZERO, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0);
        }

        return resolveTarget(level, zombie);
    }

    public static Vec3 resolveClimbDirection(Zombie zombie, TargetContext targetContext) {
        if (zombie == null) {
            return Vec3.ZERO;
        }

        return resolveBoostDirection(zombie, targetContext == null
            ? new TargetContext(false, TargetKind.NONE, Vec3.ZERO, BlockPos.containing(zombie.position()), 0.0D, zombie.getY(), 0.0D, 0.0D, 0.0D, zombie.blockPosition().getY())
            : targetContext);
    }

    private static SiegeEvaluation evaluateSiegeClimb(
        ServerLevel level,
        Zombie zombie,
        TargetContext targetContext,
        List<Zombie> nearbyZombies,
        Vec3 boostDirection,
        boolean isLeader,
        long gameTime
    ) {
        if (!targetContext.hasTarget()) {
            return SiegeEvaluation.inactive("NO_TARGET");
        }

        if (Config.hordeClimbLeaderOnly && !isLeader) {
            return SiegeEvaluation.inactive("NOT_LEADER");
        }

        if (targetContext.horizontalDistance() > Math.max(0.0D, Config.hordeSiegeClimbMaxTargetDistance)) {
            return SiegeEvaluation.inactive("TARGET_TOO_FAR");
        }

        ObstacleResult obstacle = detectObstacle(
            level,
            zombie,
            boostDirection,
            Math.max(1.0D, Config.hordeSiegeClimbMaxObstacleHeight),
            Math.max(0.1D, Config.hordeSiegeClimbMaxObstacleDistance),
            Math.max(1, Config.hordeSiegeClimbMinObstacleHeight),
            Math.max(1, Config.hordeSiegeClimbMaxObstacleHeight),
            Config.hordeClimbRequireWallLikeObstacle
        );

        if (Config.hordeSiegeClimbRequireObstacleInFront && !obstacle.detected()) {
            return new SiegeEvaluation(false, false, 0, obstacle, false, 0, "NO_WALL_LIKE_OBSTACLE");
        }

        if (!obstacle.detected()) {
            return new SiegeEvaluation(false, false, 0, obstacle, false, 0, obstacle.reason());
        }

        if (obstacle.height() > Math.max(1, Config.hordeSiegeClimbMaxObstacleHeight)) {
            return new SiegeEvaluation(false, false, 0, obstacle, false, 0, "OBSTACLE_TOO_HIGH");
        }

        if (Config.hordeClimbRequireWallLikeObstacle && !obstacle.wallLike()) {
            return new SiegeEvaluation(false, false, 0, obstacle, false, 0, "NO_WALL_LIKE_OBSTACLE");
        }

        NaturalSlopeResult slopeResult = inspectNaturalSlope(level, zombie, targetContext, obstacle);
        if (slopeResult.suppressed()) {
            return new SiegeEvaluation(false, false, 0, obstacle, true, 0, "NATURAL_SLOPE_SUPPRESSED");
        }

        if (Config.hordeSiegeClimbRequireTargetAboveOrBehindObstacle && !isTargetAboveOrBehindObstacle(zombie, targetContext, obstacle)) {
            return new SiegeEvaluation(false, false, 0, obstacle, false, 0, "NO_WALL_LIKE_OBSTACLE");
        }

        if (shouldPreferBarrierBreak(level, obstacle)) {
            return new SiegeEvaluation(false, false, 0, obstacle, false, 0, "PREFER_BARRIER_BREAK");
        }

        List<Zombie> siegeGroup = collectSiegeEligibleNearbyZombies(level, zombie, targetContext, obstacle);
        int siegeGroupSize = siegeGroup.size() + 1;
        if (siegeGroupSize < Math.max(1, Config.hordeSiegeClimbMinZombies)) {
            return new SiegeEvaluation(false, false, siegeGroupSize, obstacle, false, 0, "NOT_ENOUGH_ZOMBIES");
        }

        int areaCooldownRemaining = getSiegeAreaCooldownRemaining(level, obstacle.pos(), gameTime);
        if (areaCooldownRemaining > 0) {
            return new SiegeEvaluation(false, true, siegeGroupSize, obstacle, false, areaCooldownRemaining, "AREA_COOLDOWN");
        }

        return new SiegeEvaluation(true, true, siegeGroupSize, obstacle, false, 0, "VALID_SIEGE");
    }

    private static Zombie findLeaderZombie(ServerLevel level, Zombie self, TargetContext targetContext, List<Zombie> nearbyZombies) {
        if (!Config.hordeClimbLeaderOnly) {
            return self;
        }

        if (!targetContext.hasTarget()) {
            return self;
        }

        double leaderRadiusSq = Math.max(0.0D, Config.hordeClimbLeaderCheckRadius * Config.hordeClimbLeaderCheckRadius);
        Zombie leader = self;
        double bestDistance = self.distanceToSqr(targetContext.targetPosition());
        if (leaderRadiusSq > 0.0D && bestDistance > leaderRadiusSq) {
            bestDistance = Double.MAX_VALUE;
        }

        for (Zombie candidate : nearbyZombies) {
            double distance = candidate.distanceToSqr(targetContext.targetPosition());
            if (leaderRadiusSq > 0.0D && distance > leaderRadiusSq) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                leader = candidate;
            }
        }

        double selfDistance = self.distanceToSqr(targetContext.targetPosition());
        if (leaderRadiusSq <= 0.0D || selfDistance <= leaderRadiusSq) {
            if (selfDistance <= bestDistance) {
                leader = self;
            }
        }

        return leader;
    }

    private static ObstacleResult detectObstacle(
        ServerLevel level,
        Zombie zombie,
        Vec3 direction,
        double maxHeight,
        double maxDistance,
        int minObstacleHeight,
        int maxObstacleHeight,
        boolean requireWallLike
    ) {
        Vec3 normalized = normalizeFlat(direction);
        if (normalized.lengthSqr() <= 0.0001D) {
            normalized = normalizeFlat(zombie.getViewVector(1.0F));
        }

        if (normalized.lengthSqr() <= 0.0001D) {
            normalized = new Vec3(0.0D, 0.0D, 1.0D);
        }

        int scanHeight = Math.max(1, Math.min(Math.max(1, Config.hordeClimbMaxObstacleScanHeight), Math.max(maxObstacleHeight, (int) Math.ceil(Math.max(maxHeight, Config.hordeClimbMaxHeight)))));
        int baseY = Mth.floor(zombie.getY());
        ObstacleResult fallback = ObstacleResult.empty(requireWallLike ? "NO_WALL_LIKE_OBSTACLE" : "NO_WALL_LIKE_OBSTACLE");

        for (double step = 0.65D; step <= Math.max(0.65D, maxDistance); step += 0.25D) {
            Vec3 ahead = zombie.position().add(normalized.scale(step));
            BlockPos start = BlockPos.containing(ahead.x, baseY, ahead.z);
            int solidHeight = countSolidHeight(level, start, scanHeight);
            if (solidHeight < Math.max(1, minObstacleHeight)) {
                continue;
            }

            if (!hasSafeLanding(level, start, solidHeight, maxObstacleHeight)) {
                fallback = ObstacleResult.empty("NO_SAFE_LANDING");
                continue;
            }

            boolean wallLike = isWallLikeObstacle(level, start, solidHeight, normalized, baseY);
            if (requireWallLike && !wallLike) {
                fallback = ObstacleResult.empty("NO_WALL_LIKE_OBSTACLE");
                continue;
            }

            return new ObstacleResult(true, solidHeight, start.immutable(), "VALID_OBSTACLE", step, wallLike);
        }

        return fallback;
    }

    private static TargetElevationResult detectTargetElevation(
        ServerLevel level,
        Zombie zombie,
        TargetContext targetContext,
        double effectiveHeight,
        ObstacleResult obstacleResult,
        double blockYDifference,
        boolean isLeader
    ) {
        if (!Config.enableHordeClimbTargetElevationMode) {
            return TargetElevationResult.inactive("DISABLED");
        }

        if (!targetContext.hasTarget()) {
            return TargetElevationResult.inactive("NO_TARGET");
        }

        if (Config.hordeClimbLeaderOnly && !isLeader) {
            return TargetElevationResult.inactive("NOT_LEADER");
        }

        if (targetContext.yDifference() < Config.hordeClimbTargetMinYDifference) {
            return TargetElevationResult.inactive("TARGET_ELEVATION_Y_TOO_SMALL");
        }

        if (blockYDifference < Config.hordeClimbTargetMinBlockYDifference) {
            return TargetElevationResult.inactive("TARGET_ELEVATION_BLOCK_Y_TOO_SMALL");
        }

        if (targetContext.horizontalDistance() < Config.hordeClimbTargetHorizontalMinRange) {
            return TargetElevationResult.inactive("TARGET_HORIZONTAL_TOO_CLOSE");
        }

        if (targetContext.horizontalDistance() > Config.hordeClimbTargetHorizontalRange) {
            return TargetElevationResult.inactive("TARGET_HORIZONTAL_TOO_FAR");
        }

        if (Config.hordeClimbIgnoreSmallTerrainSteps && blockYDifference <= 1) {
            return TargetElevationResult.inactive("TERRAIN_STEP_IGNORED");
        }

        ObstacleResult targetObstacle = detectObstacle(
            level,
            zombie,
            normalizeFlat(targetContext.targetPosition().subtract(zombie.position())),
            effectiveHeight,
            Math.max(0.1D, Math.min(targetContext.horizontalDistance(), Config.hordeClimbTargetHorizontalRange)),
            Math.max(1, Config.hordeClimbTargetModeMinObstacleHeight),
            Math.max(1, Config.hordeClimbMaxObstacleScanHeight),
            false
        );
        boolean obstacleBetween = targetObstacle.detected() && targetObstacle.height() >= Math.max(1, Config.hordeClimbTargetModeMinObstacleHeight);
        if (Config.hordeClimbTargetModeRequireObstacleBetween && !obstacleBetween) {
            return TargetElevationResult.inactive("NO_OBSTACLE_BETWEEN_TARGET");
        }

        if (targetObstacle.detected() && targetObstacle.height() < Math.max(1, Config.hordeClimbTargetModeMinObstacleHeight)) {
            return TargetElevationResult.inactive("TARGET_OBSTACLE_TOO_LOW");
        }

        if (effectiveHeight < targetContext.yDifference()) {
            return TargetElevationResult.inactive("TARGET_TOO_HIGH_FOR_GROUP");
        }

        if (Config.hordeClimbAllowPillarClimb && !hasPillarOpportunity(level, targetContext)) {
            if (!obstacleBetween && !obstacleResult.detected()) {
                return TargetElevationResult.inactive("NO_SAFE_LANDING");
            }
        }

        return new TargetElevationResult(true, obstacleBetween, targetObstacle.height(), "VALID_TARGET_ELEVATION");
    }

    private static boolean hasPillarOpportunity(ServerLevel level, TargetContext targetContext) {
        if (!targetContext.hasTarget()) {
            return false;
        }

        BlockPos targetBase = BlockPos.containing(targetContext.targetPosition().x, Mth.floor(targetContext.zombieY()), targetContext.targetPosition().z);
        int scanHeight = Math.max(1, Math.min(Config.hordeClimbMaxObstacleScanHeight, Math.max(1, Config.hordeClimbPillarMinHeight)));
        int radius = 1;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos base = targetBase.offset(dx, 0, dz);
                int solidHeight = countSolidHeight(level, base, scanHeight);
                if (solidHeight >= Math.max(1, Config.hordeClimbPillarMinHeight)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static NaturalSlopeResult inspectNaturalSlope(ServerLevel level, Zombie zombie, TargetContext targetContext, ObstacleResult obstacle) {
        if (!Config.hordeClimbSuppressOnNaturalSlopes || !targetContext.hasTarget()) {
            return NaturalSlopeResult.clear();
        }

        Vec3 direction = normalizeFlat(targetContext.targetPosition().subtract(zombie.position()));
        if (direction.lengthSqr() <= 0.0001D) {
            return NaturalSlopeResult.clear();
        }

        int maxDistance = Math.max(1, Math.min(Config.hordeClimbNaturalSlopeScanDistance, Mth.ceil(targetContext.horizontalDistance())));
        int minSurfaceY = zombie.blockPosition().getY() - 2;
        int maxSurfaceY = zombie.blockPosition().getY() + Math.max(4, Config.hordeClimbNaturalSlopeScanDistance + Config.hordeSiegeClimbMaxObstacleHeight);
        int previousSurface = findSurfaceTopY(level, zombie.blockPosition(), minSurfaceY, maxSurfaceY);
        if (previousSurface == Integer.MIN_VALUE) {
            return NaturalSlopeResult.clear();
        }

        boolean sawStepUp = false;
        for (int step = 1; step <= maxDistance; step++) {
            Vec3 sample = zombie.position().add(direction.scale(step));
            BlockPos samplePos = BlockPos.containing(sample.x, zombie.getY(), sample.z);
            int currentSurface = findSurfaceTopY(level, samplePos, minSurfaceY, maxSurfaceY);
            if (currentSurface == Integer.MIN_VALUE) {
                continue;
            }

            int delta = currentSurface - previousSurface;
            if (delta > Math.max(0, Config.hordeClimbNaturalSlopeMaxStepHeight)) {
                return NaturalSlopeResult.clear();
            }

            if (delta > 0) {
                sawStepUp = true;
            }

            previousSurface = currentSurface;
        }

        boolean suppressed = sawStepUp && (!obstacle.detected() || !obstacle.wallLike());
        return suppressed ? new NaturalSlopeResult(true) : NaturalSlopeResult.clear();
    }

    private static boolean isTargetAboveOrBehindObstacle(Zombie zombie, TargetContext targetContext, ObstacleResult obstacle) {
        if (!targetContext.hasTarget() || !obstacle.detected() || obstacle.pos() == null) {
            return false;
        }

        Vec3 direction = normalizeFlat(targetContext.targetPosition().subtract(zombie.position()));
        if (direction.lengthSqr() <= 0.0001D) {
            return false;
        }

        Vec3 obstacleCenter = Vec3.atCenterOf(obstacle.pos());
        double targetProjection = projectForward(zombie.position(), targetContext.targetPosition(), direction);
        double obstacleProjection = projectForward(zombie.position(), obstacleCenter, direction);
        boolean behind = targetProjection >= obstacleProjection - 0.25D;
        boolean above = targetContext.targetY() >= obstacle.pos().getY() + obstacle.height() - 0.25D
            || targetContext.blockYDifference() >= Math.max(1, Config.hordeSiegeClimbMinObstacleHeight - 1);
        return behind || above;
    }

    private static boolean shouldPreferBarrierBreak(ServerLevel level, ObstacleResult obstacle) {
        if (!Config.enableZombieBarrierBreak || obstacle == null || !obstacle.detected() || obstacle.pos() == null) {
            return false;
        }

        ZombieBarrierBreakHelper.BarrierBlockInspection inspection = ZombieBarrierBreakHelper.inspectBlock(level, obstacle.pos());
        return inspection.supported() && !inspection.denylisted() && !inspection.tintedBlocked();
    }

    private static int getSiegeAreaCooldownRemaining(ServerLevel level, BlockPos anchor, long gameTime) {
        if (level == null || anchor == null) {
            return 0;
        }

        Long lastTick = SIEGE_AREA_COOLDOWNS.get(createAreaCooldownKey(level, anchor));
        if (lastTick == null) {
            return 0;
        }

        return (int) Math.max(0L, lastTick + Math.max(1, Config.hordeSiegeClimbAreaCooldownTicks) - gameTime);
    }

    private static void pruneSiegeAreaCooldowns(long gameTime) {
        if (SIEGE_AREA_COOLDOWNS.isEmpty()) {
            return;
        }

        long expiration = Math.max(200L, Math.max(1, Config.hordeSiegeClimbAreaCooldownTicks) * 4L);
        SIEGE_AREA_COOLDOWNS.entrySet().removeIf(entry -> gameTime - entry.getValue() > expiration);
    }

    private static String createAreaCooldownKey(ServerLevel level, BlockPos anchor) {
        double radius = Math.max(1.0D, Config.hordeSiegeClimbAreaCooldownRadius);
        int cellX = Mth.floor(anchor.getX() / radius);
        int cellY = Mth.floor(anchor.getY() / radius);
        int cellZ = Mth.floor(anchor.getZ() / radius);
        return level.dimension().location() + "|" + cellX + "|" + cellY + "|" + cellZ;
    }

    private static int countSolidHeight(ServerLevel level, BlockPos start, int scanHeight) {
        int solidHeight = 0;
        for (int i = 0; i < scanHeight; i++) {
            BlockPos pos = start.above(i);
            BlockState state = level.getBlockState(pos);
            if (!blocksMovement(state, level, pos)) {
                break;
            }
            solidHeight++;
        }
        return solidHeight;
    }

    private static boolean hasSafeLanding(ServerLevel level, BlockPos start, int solidHeight, int extraHeight) {
        int landingHeight = Mth.ceil(Math.max(1.0D, extraHeight));
        for (int i = solidHeight; i < solidHeight + landingHeight; i++) {
            BlockPos pos = start.above(i);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWallLikeObstacle(ServerLevel level, BlockPos start, int solidHeight, Vec3 direction, int baseY) {
        Direction horizontal = horizontalDirection(direction);
        BlockPos behind = start.relative(horizontal.getOpposite());
        BlockPos ahead = start.relative(horizontal);
        int behindHeight = countSolidHeight(level, new BlockPos(behind.getX(), baseY, behind.getZ()), solidHeight + 1);
        int aheadHeight = countSolidHeight(level, new BlockPos(ahead.getX(), baseY, ahead.getZ()), solidHeight + 1);
        int requiredRise = Math.max(1, Config.hordeClimbNaturalSlopeMaxStepHeight + 1);
        return solidHeight - behindHeight >= requiredRise || solidHeight - aheadHeight >= requiredRise;
    }

    private static int findSurfaceTopY(ServerLevel level, BlockPos samplePos, int minY, int maxY) {
        if (level == null || samplePos == null) {
            return Integer.MIN_VALUE;
        }

        for (int y = maxY; y >= minY; y--) {
            BlockPos ground = new BlockPos(samplePos.getX(), y, samplePos.getZ());
            BlockPos above = ground.above();
            if (blocksMovement(level.getBlockState(ground), level, ground) && level.getBlockState(above).getCollisionShape(level, above).isEmpty()) {
                return y + 1;
            }
        }

        return Integer.MIN_VALUE;
    }

    private static Direction horizontalDirection(Vec3 direction) {
        if (Math.abs(direction.x) > Math.abs(direction.z)) {
            return direction.x >= 0.0D ? Direction.EAST : Direction.WEST;
        }
        return direction.z >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    private static boolean blocksMovement(BlockState state, ServerLevel level, BlockPos pos) {
        return state != null && !state.getCollisionShape(level, pos).isEmpty();
    }

    private static Vec3 normalizeFlat(Vec3 vec) {
        if (vec == null) {
            return Vec3.ZERO;
        }

        Vec3 flat = new Vec3(vec.x, 0.0D, vec.z);
        double length = flat.length();
        return length > 0.0001D ? flat.scale(1.0D / length) : Vec3.ZERO;
    }

    private static double projectForward(Vec3 origin, Vec3 position, Vec3 direction) {
        Vec3 delta = position.subtract(origin);
        return delta.dot(direction);
    }

    private static TargetContext resolveTarget(ServerLevel level, Zombie zombie) {
        LivingEntity target = zombie.getTarget();
        if (target instanceof ServerPlayer player && isValidPlayerTarget(player) && Config.hordeClimbWorksAgainstPlayers) {
            Vec3 position = player.position();
            return createTargetContext(position, TargetKind.PLAYER, zombie);
        }

        if (Config.hordeClimbWorksAgainstMachines && MachineAttackHandler.hasValidMachineTarget(level, zombie)) {
            BlockPos machinePos = MachineAttackHandler.getMachineTarget(zombie);
            if (machinePos != null) {
                return createTargetContext(Vec3.atCenterOf(machinePos), TargetKind.MACHINE, zombie);
            }
        }

        if (target != null && target.isAlive() && !target.isRemoved()) {
            return createTargetContext(target.position(), TargetKind.OTHER, zombie);
        }

        return new TargetContext(false, TargetKind.NONE, Vec3.ZERO, BlockPos.containing(zombie.position()), 0.0D, zombie.getY(), 0.0D, 0.0D, 0.0D, zombie.blockPosition().getY());
    }

    private static TargetContext createTargetContext(Vec3 targetPosition, TargetKind kind, Zombie zombie) {
        double targetY = targetPosition.y;
        double zombieY = zombie.getY();
        double yDifference = targetY - zombieY;
        int targetBlockY = BlockPos.containing(targetPosition).getY();
        int zombieBlockY = zombie.blockPosition().getY();
        double blockYDifference = targetBlockY - zombieBlockY;
        Vec3 horizontal = new Vec3(targetPosition.x - zombie.getX(), 0.0D, targetPosition.z - zombie.getZ());
        double horizontalDistance = horizontal.length();
        return new TargetContext(true, kind, targetPosition, BlockPos.containing(targetPosition), targetY, zombieY, yDifference, blockYDifference, horizontalDistance, zombieBlockY);
    }

    private static Vec3 resolveBoostDirection(Zombie zombie, TargetContext targetContext) {
        if (Config.hordeClimbUseTargetDirectionForBoost && targetContext.hasTarget()) {
            return normalizeFlat(targetContext.targetPosition().subtract(zombie.position()));
        }

        Vec3 movement = zombie.getDeltaMovement();
        Vec3 direction = normalizeFlat(movement.lengthSqr() > 0.0001D ? movement : zombie.getViewVector(1.0F));
        return direction.lengthSqr() > 0.0001D ? direction : new Vec3(0.0D, 0.0D, 1.0D);
    }

    private static boolean isValidPlayerTarget(ServerPlayer player) {
        return player != null && player.isAlive() && !player.isRemoved() && !player.isSpectator() && !player.isCreative();
    }

    private static String determineReason(
        boolean enabled,
        boolean eligible,
        boolean alive,
        boolean aiEnabled,
        boolean hydrated,
        boolean peaceful,
        boolean groupEnough,
        int cooldownRemaining,
        TargetContext targetContext,
        boolean targetRequirementMet,
        boolean isLeader
    ) {
        if (!enabled) {
            return "DISABLED";
        }
        if (!eligible) {
            return "NOT_ELIGIBLE";
        }
        if (!alive) {
            return "NOT_ALIVE";
        }
        if (!aiEnabled) {
            return "NO_AI";
        }
        if (!hydrated) {
            return "IN_WATER_OR_LAVA";
        }
        if (peaceful) {
            return "PEACEFUL";
        }
        if (!groupEnough) {
            return "NOT_ENOUGH_ZOMBIES";
        }
        if (cooldownRemaining > 0) {
            return "COOLDOWN";
        }
        if (Config.hordeClimbRequireTarget && !targetContext.hasTarget()) {
            return "NO_TARGET";
        }
        if (Config.hordeClimbRequireTarget && !targetRequirementMet) {
            return "TARGET_NOT_ALLOWED";
        }
        if (Config.hordeClimbLeaderOnly && !isLeader) {
            return "NOT_LEADER";
        }
        return "INACTIVE";
    }

    private static String appendVerboseReason(
        String reason,
        TargetContext targetContext,
        double effectiveHeight,
        String obstacleReason,
        String targetElevationReason,
        boolean isLeader,
        int siegeGroupSize,
        double obstacleDistance,
        boolean naturalSlopeSuppressed,
        boolean wallLikeObstacle,
        int areaCooldownRemaining
    ) {
        return reason
            + " | targetY=" + formatDouble(targetContext.targetY())
            + " zombieY=" + formatDouble(targetContext.zombieY())
            + " yDiff=" + formatDouble(targetContext.yDifference())
            + " blockYDiff=" + formatDouble(targetContext.blockYDifference())
            + " horizontal=" + formatDouble(targetContext.horizontalDistance())
            + " height=" + formatDouble(effectiveHeight)
            + " obstacle=" + obstacleReason
            + " targetElevation=" + targetElevationReason
            + " leader=" + isLeader
            + " siegeGroup=" + siegeGroupSize
            + " obstacleDistance=" + formatDouble(obstacleDistance)
            + " naturalSlope=" + naturalSlopeSuppressed
            + " wallLike=" + wallLikeObstacle
            + " areaCooldown=" + areaCooldownRemaining;
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public enum ActivationMode {
        NONE,
        BODY_STACK_ASSIST,
        OBSTACLE,
        TARGET_ELEVATION,
        SIEGE_CLIMB
    }

    public enum TargetKind {
        NONE,
        PLAYER,
        MACHINE,
        OTHER
    }

    public record HordeClimbAssessment(
        boolean enabled,
        boolean eligible,
        boolean active,
        String reason,
        int groupSize,
        int siegeGroupSize,
        double calculatedHeight,
        double effectiveHeight,
        boolean obstacleModeActive,
        boolean targetElevationModeActive,
        boolean siegeEligible,
        int obstacleHeight,
        double obstacleDistance,
        BlockPos obstaclePos,
        boolean wallLikeObstacle,
        boolean naturalSlopeSuppressed,
        boolean targetObstacleBetween,
        int targetObstacleHeight,
        TargetKind targetKind,
        double targetY,
        double zombieY,
        double yDifference,
        double blockYDifference,
        double horizontalDistance,
        ActivationMode activationMode,
        String obstacleReason,
        String targetElevationReason,
        int cooldownRemaining,
        int areaCooldownRemaining,
        boolean hasTarget,
        boolean targetAllowed,
        boolean leader,
        TargetKind resolvedTargetKind,
        Vec3 direction,
        int checkIntervalTicks
    ) {
        private static HordeClimbAssessment empty() {
            return new HordeClimbAssessment(false, false, false, "EMPTY", 0, 0, 0.0D, 0.0D, false, false, false, 0, 0.0D, null, false, false, false, 0, TargetKind.NONE, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, ActivationMode.NONE, "EMPTY", "EMPTY", 0, 0, false, false, false, TargetKind.NONE, Vec3.ZERO, 0);
        }
    }

    public record TargetContext(
        boolean hasTarget,
        TargetKind kind,
        Vec3 targetPosition,
        BlockPos targetBlockPos,
        double targetY,
        double zombieY,
        double yDifference,
        double blockYDifference,
        double horizontalDistance,
        int zombieBlockY
    ) {
    }

    private record ObstacleResult(boolean detected, int height, BlockPos pos, String reason, double distance, boolean wallLike) {
        private static ObstacleResult empty(String reason) {
            return new ObstacleResult(false, 0, null, reason, 0.0D, false);
        }
    }

    private record TargetElevationResult(boolean active, boolean obstacleBetween, int obstacleHeight, String reason) {
        private static TargetElevationResult inactive(String reason) {
            return new TargetElevationResult(false, false, 0, reason);
        }
    }

    private record NaturalSlopeResult(boolean suppressed) {
        private static NaturalSlopeResult clear() {
            return new NaturalSlopeResult(false);
        }
    }

    private record SiegeEvaluation(
        boolean active,
        boolean eligible,
        int groupSize,
        ObstacleResult obstacle,
        boolean naturalSlopeSuppressed,
        int areaCooldownRemaining,
        String reason
    ) {
        private static SiegeEvaluation inactive(String reason) {
            return new SiegeEvaluation(false, false, 0, ObstacleResult.empty("NO_WALL_LIKE_OBSTACLE"), false, 0, reason);
        }
    }
}
