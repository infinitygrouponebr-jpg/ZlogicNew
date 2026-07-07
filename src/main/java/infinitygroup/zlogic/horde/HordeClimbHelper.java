package infinitygroup.zlogic.horde;

import infinitygroup.zlogic.Config;
import infinitygroup.zlogic.machine.MachineAttackHandler;
import infinitygroup.zlogic.zombie.ZombieEligibilityHelper;
import infinitygroup.zlogic.zombie.ZombieFamilyHelper;
import net.minecraft.core.BlockPos;
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

public final class HordeClimbHelper {
    public static final String NEXT_TICK_KEY = "zlogic_horde_climb_next_tick";
    public static final String LAST_GROUP_SIZE_KEY = "zlogic_horde_climb_last_group_size";
    public static final String LAST_HEIGHT_KEY = "zlogic_horde_climb_last_height";
    public static final String LAST_TICK_KEY = "zlogic_horde_climb_last_tick";
    public static final String LAST_MODE_KEY = "zlogic_horde_climb_last_mode";

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
        boolean targetAbove = targetContext.hasTarget() && targetContext.yDifference() >= Config.hordeClimbTargetMinYDifference;
        boolean targetWithinRange = targetContext.hasTarget()
            && targetContext.horizontalDistance() >= Config.hordeClimbTargetHorizontalMinRange
            && targetContext.horizontalDistance() <= Config.hordeClimbTargetHorizontalRange;
        boolean targetElevationActive = false;
        boolean obstacleModeActive = false;
        boolean isLeader = !Config.hordeClimbLeaderOnly;
        String targetElevationReason = "disabled";
        String obstacleReason = "disabled";
        ActivationMode activationMode = ActivationMode.NONE;
        String reason = "disabled";
        int obstacleHeight = 0;
        BlockPos obstaclePos = null;
        TargetElevationResult elevationResult = TargetElevationResult.inactive("disabled");

        if (enabled && eligible && alive && aiEnabled && hydrated && !peaceful && groupEnough && cooldownRemaining <= 0 && targetRequirementMet) {
            Zombie leader = findLeaderZombie(level, zombie, targetContext, nearbyZombies);
            isLeader = !Config.hordeClimbLeaderOnly || leader == zombie;

            ObstacleResult obstacleResult = detectObstacle(level, zombie, boostDirection, effectiveHeight);
            obstacleModeActive = obstacleResult.detected();
            obstacleReason = obstacleResult.reason();
            obstacleHeight = obstacleResult.height();
            obstaclePos = obstacleResult.pos();

            elevationResult = detectTargetElevation(level, zombie, targetContext, effectiveHeight, obstacleResult, targetContext.blockYDifference(), isLeader);
            targetElevationActive = elevationResult.active();
            targetElevationReason = elevationResult.reason();

            if (obstacleModeActive) {
                activationMode = ActivationMode.OBSTACLE;
                reason = "success";
            } else if (targetElevationActive) {
                activationMode = ActivationMode.TARGET_ELEVATION;
                reason = "success";
            } else {
                reason = elevationResult.reason().equals("inactive") ? obstacleReason : elevationResult.reason();
            }
        } else {
            reason = determineReason(enabled, eligible, alive, aiEnabled, hydrated, peaceful, groupEnough, cooldownRemaining, targetContext, targetRequirementMet, isLeader);
        }

        boolean active = activationMode != ActivationMode.NONE;
        if (!active && Config.hordeClimbVerboseDebug && targetContext.hasTarget()) {
            reason = appendVerboseReason(reason, targetContext, effectiveHeight, obstacleReason, targetElevationReason, isLeader);
        }

        return new HordeClimbAssessment(
            enabled,
            eligible,
            active,
            reason,
            groupSize,
            calculatedHeight,
            effectiveHeight,
            obstacleModeActive,
            targetElevationActive,
            obstacleHeight,
            obstaclePos,
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
        if (zombie == null) {
            return;
        }

        zombie.getPersistentData().putLong(NEXT_TICK_KEY, gameTime + Math.max(1, Config.hordeClimbCooldownTicks));
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

    private static boolean isFrontZombie(Zombie zombie, List<Zombie> nearbyZombies, Vec3 direction) {
        Vec3 normalized = normalizeFlat(direction);
        if (normalized.lengthSqr() <= 0.0001D) {
            normalized = normalizeFlat(zombie.getViewVector(1.0F));
        }

        if (normalized.lengthSqr() <= 0.0001D) {
            normalized = new Vec3(0.0D, 0.0D, 1.0D);
        }

        Vec3 origin = zombie.position();
        double selfScore = scoreForward(origin, origin, normalized);
        for (Zombie candidate : nearbyZombies) {
            double candidateScore = scoreForward(origin, candidate.position(), normalized);
            if (candidateScore > selfScore + 0.08D) {
                return false;
            }
        }

        return true;
    }

    private static ObstacleResult detectObstacle(ServerLevel level, Zombie zombie, Vec3 direction, double maxHeight) {
        Vec3 normalized = normalizeFlat(direction);
        if (normalized.lengthSqr() <= 0.0001D) {
            normalized = normalizeFlat(zombie.getViewVector(1.0F));
        }

        if (normalized.lengthSqr() <= 0.0001D) {
            normalized = new Vec3(0.0D, 0.0D, 1.0D);
        }

        Vec3 ahead = zombie.position().add(normalized.scale(Math.max(0.1D, Config.hordeClimbObstacleCheckDistance)));
        int scanHeight = Math.max(1, Math.min(Config.hordeClimbMaxObstacleScanHeight, (int) Math.ceil(Math.max(maxHeight, Config.hordeClimbMaxHeight))));
        int baseY = Mth.floor(zombie.getY());
        BlockPos start = BlockPos.containing(ahead.x, baseY, ahead.z);
        int solidHeight = 0;

        for (int i = 0; i < scanHeight; i++) {
            BlockPos pos = start.above(i);
            BlockState state = level.getBlockState(pos);
            if (!blocksMovement(state, level, pos)) {
                break;
            }

            solidHeight++;
            if (solidHeight >= scanHeight) {
                break;
            }
        }

        if (solidHeight < Math.max(1, Config.hordeClimbMinObstacleHeight)) {
            return ObstacleResult.empty("no obstacle");
        }

        int landingHeight = Mth.ceil(Math.max(1.0D, maxHeight));
        for (int i = solidHeight; i < solidHeight + landingHeight; i++) {
            BlockPos pos = start.above(i);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return ObstacleResult.empty("no safe landing");
            }
        }

        return new ObstacleResult(true, solidHeight, start.immutable(), "success obstacle");
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
            return TargetElevationResult.inactive("disabled");
        }

        if (!targetContext.hasTarget()) {
            return TargetElevationResult.inactive("no target");
        }

        if (Config.hordeClimbLeaderOnly && !isLeader) {
            return TargetElevationResult.inactive("not leader");
        }

        if (targetContext.yDifference() < Config.hordeClimbTargetMinYDifference) {
            return TargetElevationResult.inactive("target elevation y too small");
        }

        if (blockYDifference < Config.hordeClimbTargetMinBlockYDifference) {
            return TargetElevationResult.inactive("target elevation block y too small");
        }

        if (targetContext.horizontalDistance() < Config.hordeClimbTargetHorizontalMinRange) {
            return TargetElevationResult.inactive("target horizontal too close");
        }

        if (targetContext.horizontalDistance() > Config.hordeClimbTargetHorizontalRange) {
            return TargetElevationResult.inactive("target horizontal too far");
        }

        if (Config.hordeClimbIgnoreSmallTerrainSteps && blockYDifference <= 1) {
            return TargetElevationResult.inactive("terrain step ignored");
        }

        ObstacleResult targetObstacle = detectObstacle(level, zombie, normalizeFlat(targetContext.targetPosition().subtract(zombie.position())), effectiveHeight);
        boolean obstacleBetween = targetObstacle.detected() && targetObstacle.height() >= Math.max(1, Config.hordeClimbTargetModeMinObstacleHeight);
        if (Config.hordeClimbTargetModeRequireObstacleBetween && !obstacleBetween) {
            return TargetElevationResult.inactive("no obstacle between target");
        }

        if (targetObstacle.detected() && targetObstacle.height() < Math.max(1, Config.hordeClimbTargetModeMinObstacleHeight)) {
            return TargetElevationResult.inactive("target obstacle too low");
        }

        if (effectiveHeight < targetContext.yDifference()) {
            return TargetElevationResult.inactive("target too high for current group height");
        }

        if (Config.hordeClimbAllowPillarClimb && !hasPillarOpportunity(level, targetContext)) {
            if (!obstacleBetween && !obstacleResult.detected()) {
                return TargetElevationResult.inactive("no safe landing");
            }
        }

        return new TargetElevationResult(true, obstacleBetween, targetObstacle.height(), "success target elevation");
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
                int solidHeight = 0;
                for (int dy = 0; dy < scanHeight; dy++) {
                    BlockPos pos = base.above(dy);
                    BlockState state = level.getBlockState(pos);
                    if (!blocksMovement(state, level, pos)) {
                        break;
                    }

                    solidHeight++;
                }

                if (solidHeight >= Math.max(1, Config.hordeClimbPillarMinHeight)) {
                    return true;
                }
            }
        }

        return false;
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

    private static double scoreForward(Vec3 origin, Vec3 position, Vec3 direction) {
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

        return new TargetContext(false, false, TargetKind.NONE, Vec3.ZERO, BlockPos.containing(zombie.position()), 0.0D, zombie.getY(), 0.0D, 0.0D, 0.0D, zombie.blockPosition().getY());
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
        return new TargetContext(true, true, kind, targetPosition, BlockPos.containing(targetPosition), targetY, zombieY, yDifference, blockYDifference, horizontalDistance, zombieBlockY);
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
            return "disabled";
        }
        if (!eligible) {
            return "not eligible";
        }
        if (!alive) {
            return "dead";
        }
        if (!aiEnabled) {
            return "no ai";
        }
        if (!hydrated) {
            return "water or lava";
        }
        if (peaceful) {
            return "peaceful";
        }
        if (!groupEnough) {
            return "group too small";
        }
        if (cooldownRemaining > 0) {
            return "cooldown";
        }
        if (Config.hordeClimbRequireTarget && !targetContext.hasTarget()) {
            return "no target";
        }
        if (Config.hordeClimbRequireTarget && !targetRequirementMet) {
            return "target not allowed";
        }
        if (Config.hordeClimbLeaderOnly && !isLeader) {
            return "not leader";
        }
        return "inactive";
    }

    private static String appendVerboseReason(
        String reason,
        TargetContext targetContext,
        double effectiveHeight,
        String obstacleReason,
        String targetElevationReason,
        boolean isLeader
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
            + " leader=" + isLeader;
    }

    private static String formatDouble(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    public enum ActivationMode {
        NONE,
        OBSTACLE,
        TARGET_ELEVATION
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
        double calculatedHeight,
        double effectiveHeight,
        boolean obstacleModeActive,
        boolean targetElevationModeActive,
        int obstacleHeight,
        BlockPos obstaclePos,
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
        boolean hasTarget,
        boolean targetAllowed,
        boolean leader,
        TargetKind resolvedTargetKind,
        Vec3 direction,
        int checkIntervalTicks
    ) {
        private static HordeClimbAssessment empty() {
            return new HordeClimbAssessment(false, false, false, "empty", 0, 0.0D, 0.0D, false, false, 0, null, false, 0, TargetKind.NONE, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, ActivationMode.NONE, "empty", "empty", 0, false, false, false, TargetKind.NONE, Vec3.ZERO, 0);
        }
    }

    private record TargetContext(
        boolean hasTarget,
        boolean allowed,
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
        private TargetKind targetKind() {
            return kind;
        }
    }

    private record ObstacleResult(boolean detected, int height, BlockPos pos, String reason) {
        private static ObstacleResult empty(String reason) {
            return new ObstacleResult(false, 0, null, reason);
        }
    }

    private record TargetElevationResult(boolean active, boolean obstacleBetween, int obstacleHeight, String reason) {
        private static TargetElevationResult inactive(String reason) {
            return new TargetElevationResult(false, false, 0, reason);
        }
    }
}
