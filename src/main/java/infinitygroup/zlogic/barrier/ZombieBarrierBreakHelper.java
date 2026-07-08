package infinitygroup.zlogic.barrier;

import infinitygroup.zlogic.Config;
import infinitygroup.zlogic.machine.MachineAttackHandler;
import infinitygroup.zlogic.zombie.ZombieEligibilityHelper;
import infinitygroup.zlogic.zombie.ZombieFamilyHelper;
import infinitygroup.zlogic.zombie.ZombieMarkingHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

public final class ZombieBarrierBreakHelper {
    private static final String FOCUS_X_KEY = "zlogic_barrier_break_focus_x";
    private static final String FOCUS_Y_KEY = "zlogic_barrier_break_focus_y";
    private static final String FOCUS_Z_KEY = "zlogic_barrier_break_focus_z";
    private static final String FOCUS_UNTIL_TICK_KEY = "zlogic_barrier_break_focus_until_tick";
    private static final Set<String> STAINED_GLASS_IDS = Set.of(
        "minecraft:white_stained_glass",
        "minecraft:orange_stained_glass",
        "minecraft:magenta_stained_glass",
        "minecraft:light_blue_stained_glass",
        "minecraft:yellow_stained_glass",
        "minecraft:lime_stained_glass",
        "minecraft:pink_stained_glass",
        "minecraft:gray_stained_glass",
        "minecraft:light_gray_stained_glass",
        "minecraft:cyan_stained_glass",
        "minecraft:purple_stained_glass",
        "minecraft:blue_stained_glass",
        "minecraft:brown_stained_glass",
        "minecraft:green_stained_glass",
        "minecraft:red_stained_glass",
        "minecraft:black_stained_glass"
    );

    private static final Set<String> STAINED_GLASS_PANE_IDS = Set.of(
        "minecraft:white_stained_glass_pane",
        "minecraft:orange_stained_glass_pane",
        "minecraft:magenta_stained_glass_pane",
        "minecraft:light_blue_stained_glass_pane",
        "minecraft:yellow_stained_glass_pane",
        "minecraft:lime_stained_glass_pane",
        "minecraft:pink_stained_glass_pane",
        "minecraft:gray_stained_glass_pane",
        "minecraft:light_gray_stained_glass_pane",
        "minecraft:cyan_stained_glass_pane",
        "minecraft:purple_stained_glass_pane",
        "minecraft:blue_stained_glass_pane",
        "minecraft:brown_stained_glass_pane",
        "minecraft:green_stained_glass_pane",
        "minecraft:red_stained_glass_pane",
        "minecraft:black_stained_glass_pane"
    );

    private ZombieBarrierBreakHelper() {
    }

    public static boolean isEligibleZombie(Zombie zombie) {
        return ZombieEligibilityHelper.isEligibleForBarrierBreak(zombie);
    }

    public static BarrierBreakAssessment inspect(ServerLevel level, Zombie zombie) {
        if (level == null || zombie == null || !ZombieFamilyHelper.isZombieFamily(zombie)) {
            return BarrierBreakAssessment.empty();
        }

        boolean enabled = Config.enableZombieBarrierBreak;
        boolean eligible = isEligibleZombie(zombie);
        boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
        boolean alive = zombie.isAlive() && !zombie.isRemoved();
        boolean aiEnabled = !zombie.isNoAi();
        boolean hydrated = !zombie.isInWaterOrBubble() && !zombie.isInLava();

        TargetContext targetContext = resolveTarget(level, zombie);
        Vec3 direction = resolveDirection(zombie, targetContext);
        double targetY = targetContext.hasTarget() ? targetContext.targetPosition().y : 0.0D;
        double yDifference = targetContext.hasTarget() ? targetY - zombie.getY() : 0.0D;
        double horizontalDistance = targetContext.hasTarget() ? horizontalDistance(zombie.position(), targetContext.targetPosition()) : 0.0D;
        BarrierBlockInspection barrier = findBarrierCandidate(level, zombie, targetContext, direction);
        int cooldownRemaining = barrier != null ? BarrierDamageManager.getCooldownRemaining(level, barrier.normalizedPos(), level.getGameTime()) : 0;
        int nearbyCount = barrier != null ? countEligibleZombiesNearBarrier(level, barrier.normalizedPos()) : 0;
        int attackSideCount = barrier != null ? countZombiesOnAttackSide(level, barrier.normalizedPos(), barrier, targetContext) : 0;
        boolean targetAllowed = targetContext.allowed();
        boolean hasTarget = targetContext.hasTarget();
        int groupSize = Config.barrierBreakRequireFrontSide ? attackSideCount : nearbyCount;
        BarrierKind barrierKind = barrier != null ? barrier.barrierKind() : BarrierKind.NONE;
        String reason = "disabled";
        boolean active = false;

        if (!enabled) {
            reason = "feature disabled";
        } else if (!eligible) {
            reason = "not eligible";
        } else if (!alive) {
            reason = "not alive";
        } else if (!aiEnabled) {
            reason = "no ai";
        } else if (!hydrated) {
            reason = "in water or lava";
        } else if (peaceful) {
            reason = "peaceful";
        } else if (Config.barrierBreakRequireTarget && !hasTarget) {
            reason = "no valid target";
        } else if (!targetAllowed) {
            reason = targetContext.reason();
        } else if (cooldownRemaining > 0) {
            reason = "cooldown";
        } else if (barrier == null || !barrier.supported()) {
            reason = barrier == null ? "no supported barrier found" : barrier.reason();
        } else if (Config.barrierBreakRequireTargetBehindBarrier && hasTarget && barrier != null && !isBarrierBetweenZombieAndTarget(zombie, barrier.normalizedPos(), targetContext.targetPosition())) {
            reason = "target not behind barrier";
        } else if (groupSize < Math.max(1, Config.barrierBreakMinZombies)) {
            reason = "not enough zombies on front side";
        } else if (barrier.denylisted()) {
            reason = "denied by denylist";
        } else if (barrier.tintedBlocked()) {
            reason = "tinted glass disabled";
        } else if (barrier.currentDamage() >= barrier.durability() && !Config.barrierBreakActuallyBreakBlocks) {
            reason = "durability reached but breaking disabled";
            active = true;
        } else {
            reason = "allowed";
            active = true;
        }

        return new BarrierBreakAssessment(
            enabled,
            eligible,
            active,
            reason,
            targetContext.kind(),
            barrierKind,
            barrier == null ? zombie.blockPosition() : barrier.hitPos(),
            barrier == null ? zombie.blockPosition() : barrier.normalizedPos(),
            barrier == null ? null : barrier.blockId(),
            groupSize,
            nearbyCount,
            barrier == null ? 0 : barrier.durability(),
            barrier == null ? 0 : barrier.currentDamage(),
            cooldownRemaining,
            hasTarget,
            targetAllowed,
            barrier != null && barrier.denylisted(),
            barrier != null && barrier.tintedBlocked(),
            barrier != null && barrier.supported(),
            nearbyCount,
            attackSideCount,
            zombie.getY(),
            targetY,
            yDifference,
            horizontalDistance,
            direction
        );
    }

    public static BarrierBlockInspection inspectBlock(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return BarrierBlockInspection.empty();
        }

        BarrierBlockInspection inspection = inspectBarrierState(level, pos);
        if (!inspection.supported()) {
            int nearbyCount = countEligibleZombiesNearBarrier(level, pos);
            return inspection.withZombieCounts(nearbyCount, 0);
        }

        int nearbyCount = countEligibleZombiesNearBarrier(level, inspection.normalizedPos());
        int attackSideCount = countZombiesOnAttackSide(level, inspection.normalizedPos(), inspection, null);
        int groupSize = Config.barrierBreakRequireFrontSide ? attackSideCount : nearbyCount;
        int damage = BarrierDamageManager.getDamage(level, inspection.normalizedPos());
        int cooldown = BarrierDamageManager.getCooldownRemaining(level, inspection.normalizedPos(), level.getGameTime());
        String reason = determineStaticReason(inspection, groupSize, cooldown);

        return inspection.withZombieCounts(nearbyCount, attackSideCount).withCurrentDamage(damage).withReason(reason).withCanBreak("allowed".equals(reason));
    }

    public static int countEligibleZombiesNearBarrier(ServerLevel level, BlockPos barrierPos) {
        if (level == null || barrierPos == null) {
            return 0;
        }

        Vec3 center = Vec3.atCenterOf(barrierPos);
        double radius = Math.max(0.1D, Config.barrierBreakZombieRadius);
        AABB box = new AABB(
            center.x - radius,
            center.y - radius,
            center.z - radius,
            center.x + radius,
            center.y + radius,
            center.z + radius
        );

        List<Zombie> zombies = level.getEntitiesOfClass(Zombie.class, box, candidate -> candidate != null && candidate.isAlive() && !candidate.isRemoved() && isEligibleZombie(candidate));
        return zombies.size();
    }

    public static int countZombiesOnAttackSide(ServerLevel level, BlockPos barrierPos, BarrierBlockInspection inspection, TargetContext targetContext) {
        if (level == null || barrierPos == null || inspection == null || !inspection.supported()) {
            return 0;
        }

        Vec3 center = Vec3.atCenterOf(barrierPos);
        double radius = Math.max(0.1D, Math.max(Config.barrierBreakZombieRadius, Math.max(Config.barrierBreakFrontWidth, Config.barrierBreakFrontDepth)));
        AABB box = new AABB(
            center.x - radius,
            center.y - 2.5D,
            center.z - radius,
            center.x + radius,
            center.y + 3.5D,
            center.z + radius
        );

        List<Zombie> zombies = level.getEntitiesOfClass(Zombie.class, box, candidate -> candidate != null && candidate.isAlive() && !candidate.isRemoved() && isEligibleZombie(candidate));
        int count = 0;
        Vec3 targetPos = targetContext != null && targetContext.hasTarget() ? targetContext.targetPosition() : null;
        for (Zombie candidate : zombies) {
            if (!isZombieOnValidAttackSide(candidate, barrierPos, inspection.state(), targetPos)) {
                continue;
            }

            count++;
        }

        return count;
    }

    public static int getDurabilityFor(BlockState state, BarrierKind kind, BlockPos pos) {
        if (kind == null) {
            return 0;
        }

        return switch (kind) {
            case WOODEN_DOOR -> Config.barrierBreakWoodenDoorDurability;
            case GLASS_BLOCK, STAINED_GLASS -> Config.barrierBreakGlassDurability;
            case GLASS_PANE, STAINED_GLASS_PANE -> Config.barrierBreakGlassPaneDurability;
            case WOODEN_TRAPDOOR, WOODEN_FENCE_GATE, EXTRA_ALLOWLIST -> Config.barrierBreakExtraBlockDurability;
            case TINTED_GLASS, UNSUPPORTED, NONE -> 0;
        };
    }

    public static BarrierKind detectBarrierKind(BlockState state, ResourceLocation blockId) {
        if (state == null || blockId == null) {
            return BarrierKind.NONE;
        }

        String id = blockId.toString();
        String path = blockId.getPath();

        if (isDenylisted(id)) {
            return BarrierKind.UNSUPPORTED;
        }

        if ("minecraft:tinted_glass".equals(id)) {
            return Config.barrierBreakTintedGlass && !isDenylisted(id) ? BarrierKind.TINTED_GLASS : BarrierKind.UNSUPPORTED;
        }

        if (isWoodenDoor(state, id)) {
            return Config.barrierBreakWoodenDoors ? BarrierKind.WOODEN_DOOR : BarrierKind.UNSUPPORTED;
        }

        if (isWoodenTrapdoor(state, id)) {
            return Config.barrierBreakWoodenTrapdoors ? BarrierKind.WOODEN_TRAPDOOR : BarrierKind.UNSUPPORTED;
        }

        if (isWoodenFenceGate(state, id)) {
            return Config.barrierBreakWoodenFenceGates ? BarrierKind.WOODEN_FENCE_GATE : BarrierKind.UNSUPPORTED;
        }

        if ("minecraft:glass".equals(id) || "glass".equals(path)) {
            return Config.barrierBreakGlassBlocks ? BarrierKind.GLASS_BLOCK : BarrierKind.UNSUPPORTED;
        }

        if ("minecraft:glass_pane".equals(id) || "glass_pane".equals(path)) {
            return Config.barrierBreakGlassPanes ? BarrierKind.GLASS_PANE : BarrierKind.UNSUPPORTED;
        }

        if (STAINED_GLASS_IDS.contains(id) || (path.endsWith("_stained_glass") && !path.equals("tinted_glass"))) {
            return Config.barrierBreakStainedGlass ? BarrierKind.STAINED_GLASS : BarrierKind.UNSUPPORTED;
        }

        if (STAINED_GLASS_PANE_IDS.contains(id) || path.endsWith("_stained_glass_pane")) {
            return Config.barrierBreakStainedGlassPanes ? BarrierKind.STAINED_GLASS_PANE : BarrierKind.UNSUPPORTED;
        }

        if (isExtraAllowlisted(id)) {
            return BarrierKind.EXTRA_ALLOWLIST;
        }

        return BarrierKind.UNSUPPORTED;
    }

    public static BarrierBlockInspection inspectBarrierState(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) {
            return BarrierBlockInspection.empty();
        }

        BlockState state = level.getBlockState(pos);
        if (state == null || state.isAir()) {
            return BarrierBlockInspection.emptyAt(pos);
        }

        BlockPos normalizedPos = normalizeBarrierPos(level, pos, state);
        BlockState normalizedState = level.getBlockState(normalizedPos);
        if (normalizedState == null || normalizedState.isAir()) {
            return BarrierBlockInspection.emptyAt(pos);
        }

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(normalizedState.getBlock());
        BarrierKind kind = detectBarrierKind(normalizedState, blockId);
        boolean supported = kind != BarrierKind.NONE && kind != BarrierKind.UNSUPPORTED;
        boolean denylisted = blockId != null && isDenylisted(blockId.toString());
        boolean tintedBlocked = "minecraft:tinted_glass".equals(blockId == null ? null : blockId.toString()) && !Config.barrierBreakTintedGlass;
        int durability = supported ? getDurabilityFor(normalizedState, kind, normalizedPos) : 0;
        int currentDamage = BarrierDamageManager.getDamage(level, normalizedPos);
        String reason = determineStaticReason(blockId, kind, supported, denylisted, tintedBlocked);
        boolean canBreak = "allowed".equals(reason) && currentDamage < durability;

        return new BarrierBlockInspection(
            pos,
            normalizedPos,
            blockId,
            kind,
            supported,
            denylisted,
            tintedBlocked,
            durability,
            currentDamage,
            0,
            0,
            reason,
            canBreak,
            normalizedState.getBlock().getClass().getSimpleName(),
            normalizedState
        );
    }

    public static void recordState(Zombie zombie, BarrierBreakAssessment assessment, long gameTime) {
        if (zombie == null || assessment == null) {
            return;
        }

        var tag = zombie.getPersistentData();
        tag.putString("zlogic_barrier_break_last_type", assessment.barrierKind().name());
        tag.putInt("zlogic_barrier_break_last_group_size", assessment.groupSize());
        tag.putInt("zlogic_barrier_break_last_nearby_count", assessment.nearbyZombieCount());
        tag.putInt("zlogic_barrier_break_last_attack_side_count", assessment.attackSideZombieCount());
        tag.putInt("zlogic_barrier_break_last_damage", assessment.damage());
        tag.putString("zlogic_barrier_break_last_reason", assessment.reason());
        tag.putLong("zlogic_barrier_break_last_tick", gameTime);
    }

    public static void updateBarrierFocus(Zombie zombie, BarrierBreakAssessment assessment, long gameTime) {
        if (zombie == null || assessment == null) {
            return;
        }

        if (assessment.supported() && assessment.normalizedPos() != null) {
            zombie.getPersistentData().putInt(FOCUS_X_KEY, assessment.normalizedPos().getX());
            zombie.getPersistentData().putInt(FOCUS_Y_KEY, assessment.normalizedPos().getY());
            zombie.getPersistentData().putInt(FOCUS_Z_KEY, assessment.normalizedPos().getZ());
            zombie.getPersistentData().putLong(FOCUS_UNTIL_TICK_KEY, gameTime + Math.max(1, Config.barrierBreakFocusTicks));
            return;
        }

        if (gameTime >= zombie.getPersistentData().getLong(FOCUS_UNTIL_TICK_KEY)) {
            clearBarrierFocus(zombie);
        }
    }

    public static void scheduleNextTick(Zombie zombie, long gameTime, boolean attempted) {
        if (zombie == null) {
            return;
        }

        int interval = Math.max(1, attempted ? Config.barrierBreakZombieCooldownTicks : Config.barrierBreakCheckIntervalTicks);
        zombie.getPersistentData().putLong("zlogic_barrier_break_next_tick", gameTime + interval);
    }

    public static long getNextTick(Zombie zombie) {
        if (zombie == null || !zombie.getPersistentData().contains("zlogic_barrier_break_next_tick")) {
            return 0L;
        }

        return zombie.getPersistentData().getLong("zlogic_barrier_break_next_tick");
    }

    public static int getLastGroupSize(Zombie zombie) {
        return zombie != null && zombie.getPersistentData().contains("zlogic_barrier_break_last_group_size")
            ? zombie.getPersistentData().getInt("zlogic_barrier_break_last_group_size")
            : 0;
    }

    public static int getLastNearbyCount(Zombie zombie) {
        return zombie != null && zombie.getPersistentData().contains("zlogic_barrier_break_last_nearby_count")
            ? zombie.getPersistentData().getInt("zlogic_barrier_break_last_nearby_count")
            : 0;
    }

    public static int getLastAttackSideCount(Zombie zombie) {
        return zombie != null && zombie.getPersistentData().contains("zlogic_barrier_break_last_attack_side_count")
            ? zombie.getPersistentData().getInt("zlogic_barrier_break_last_attack_side_count")
            : 0;
    }

    public static int getLastDamage(Zombie zombie) {
        return zombie != null && zombie.getPersistentData().contains("zlogic_barrier_break_last_damage")
            ? zombie.getPersistentData().getInt("zlogic_barrier_break_last_damage")
            : 0;
    }

    public static String getLastReason(Zombie zombie) {
        return zombie != null && zombie.getPersistentData().contains("zlogic_barrier_break_last_reason")
            ? zombie.getPersistentData().getString("zlogic_barrier_break_last_reason")
            : "none";
    }

    public static long getLastTick(Zombie zombie) {
        return zombie != null && zombie.getPersistentData().contains("zlogic_barrier_break_last_tick")
            ? zombie.getPersistentData().getLong("zlogic_barrier_break_last_tick")
            : 0L;
    }

    public static String getLastBarrierType(Zombie zombie) {
        return zombie != null && zombie.getPersistentData().contains("zlogic_barrier_break_last_type")
            ? zombie.getPersistentData().getString("zlogic_barrier_break_last_type")
            : BarrierKind.NONE.name();
    }

    private static BarrierBlockInspection inspectBarrierState(ServerLevel level, BlockPos pos, boolean normalize) {
        return inspectBarrierState(level, pos);
    }

    private static String determineStaticReason(BarrierBlockInspection inspection, int zombieCount, int cooldownRemaining) {
        if (inspection == null) {
            return "not supported block";
        }

        if (inspection.denylisted()) {
            return "denied by denylist";
        }

        if (inspection.tintedBlocked()) {
            return "tinted glass disabled";
        }

        if (!inspection.supported()) {
            return "not supported block";
        }

        if (zombieCount < Math.max(1, Config.barrierBreakMinZombies)) {
            return "not enough zombies";
        }

        if (cooldownRemaining > 0) {
            return "cooldown";
        }

        if (inspection.currentDamage() >= inspection.durability() && !Config.barrierBreakActuallyBreakBlocks) {
            return "durability reached but breaking disabled";
        }

        return "allowed";
    }

    private static String determineStaticReason(ResourceLocation blockId, BarrierKind kind, boolean supported, boolean denylisted, boolean tintedBlocked) {
        if (blockId == null) {
            return "unknown block";
        }

        if (denylisted) {
            return "denied by denylist";
        }

        if (tintedBlocked) {
            return "tinted glass disabled";
        }

        if (!supported) {
            return "not supported block";
        }

        return "allowed";
    }

    private static TargetContext resolveTarget(ServerLevel level, Zombie zombie) {
        if (level == null || zombie == null) {
            return TargetContext.none();
        }

        if (Config.barrierBreakWorksAgainstPlayers) {
            LivingEntity currentTarget = zombie.getTarget();
            if (currentTarget instanceof Player player && player.isAlive() && !player.isRemoved() && !player.isCreative() && !player.isSpectator()) {
                Vec3 targetPos = player.getBoundingBox().getCenter();
                return new TargetContext(TargetType.PLAYER, targetPos, true, "player");
            }
        }

        if (Config.barrierBreakWorksAgainstMachines && MachineAttackHandler.hasValidMachineTarget(level, zombie)) {
            BlockPos machineTarget = MachineAttackHandler.getMachineTarget(zombie);
            if (machineTarget != null) {
                return new TargetContext(TargetType.MACHINE, Vec3.atCenterOf(machineTarget), true, "machine");
            }
        }

        if (!Config.barrierBreakRequireTarget) {
            Vec3 look = zombie.getLookAngle();
            Vec3 flat = normalizeFlat(look);
            if (flat.lengthSqr() > 0.0001D) {
                return new TargetContext(TargetType.NONE, zombie.position().add(flat), true, "look");
            }
        }

        return TargetContext.none();
    }

    private static BarrierBlockInspection findBarrierCandidate(ServerLevel level, Zombie zombie, TargetContext targetContext, Vec3 direction) {
        if (level == null || zombie == null || targetContext == null || !targetContext.allowed()) {
            return BarrierBlockInspection.empty();
        }

        BarrierBlockInspection focusedBarrier = getFocusedBarrier(level, zombie, targetContext);
        if (focusedBarrier != null) {
            return focusedBarrier;
        }

        if (direction.lengthSqr() <= 0.0001D) {
            return BarrierBlockInspection.empty();
        }

        Vec3 perpendicular = normalizeFlat(new Vec3(-direction.z, 0.0D, direction.x));
        Vec3 origin = zombie.position().add(0.0D, zombie.getBbHeight() * 0.5D, 0.0D);
        double maxDistance = Math.max(1.2D, Config.barrierBreakZombieRadius + 0.75D);
        double[] lateralOffsets = {0.0D, 0.35D, -0.35D};
        double[] verticalOffsets = {-0.45D, 0.25D, 1.10D};

        for (double step = 0.35D; step <= maxDistance; step += 0.25D) {
            Vec3 center = origin.add(direction.scale(step));
            for (double lateral : lateralOffsets) {
                Vec3 sample = center.add(perpendicular.scale(lateral));
                for (double vertical : verticalOffsets) {
                    BlockPos candidate = BlockPos.containing(sample.x, sample.y + vertical, sample.z);
                    BarrierBlockInspection inspection = inspectBarrierState(level, candidate);
                    if (inspection.supported()) {
                        return inspection;
                    }
                }
            }
        }

        return BarrierBlockInspection.empty();
    }

    private static BarrierBlockInspection getFocusedBarrier(ServerLevel level, Zombie zombie, TargetContext targetContext) {
        if (level == null || zombie == null || targetContext == null) {
            return null;
        }

        long untilTick = zombie.getPersistentData().getLong(FOCUS_UNTIL_TICK_KEY);
        if (untilTick <= level.getGameTime()) {
            clearBarrierFocus(zombie);
            return null;
        }

        if (!zombie.getPersistentData().contains(FOCUS_X_KEY)
            || !zombie.getPersistentData().contains(FOCUS_Y_KEY)
            || !zombie.getPersistentData().contains(FOCUS_Z_KEY)) {
            clearBarrierFocus(zombie);
            return null;
        }

        BlockPos pos = new BlockPos(
            zombie.getPersistentData().getInt(FOCUS_X_KEY),
            zombie.getPersistentData().getInt(FOCUS_Y_KEY),
            zombie.getPersistentData().getInt(FOCUS_Z_KEY)
        );
        BarrierBlockInspection inspection = inspectBarrierState(level, pos);
        if (!inspection.supported()) {
            clearBarrierFocus(zombie);
            return null;
        }

        if (!shouldKeepFocusedBarrier(zombie, targetContext, inspection.normalizedPos())) {
            clearBarrierFocus(zombie);
            return null;
        }

        return inspection;
    }

    private static boolean shouldKeepFocusedBarrier(Zombie zombie, TargetContext targetContext, BlockPos barrierPos) {
        if (zombie == null || targetContext == null || barrierPos == null) {
            return false;
        }

        if (!targetContext.hasTarget()) {
            return true;
        }

        double driftRadius = Math.max(0.5D, Config.barrierBreakFocusTargetDrift);
        Vec3 barrierCenter = Vec3.atCenterOf(barrierPos);
        boolean targetNearBarrier = barrierCenter.distanceTo(targetContext.targetPosition()) <= driftRadius;
        boolean stillBetween = isBarrierBetweenZombieAndTarget(zombie, barrierPos, targetContext.targetPosition());
        return targetNearBarrier || stillBetween;
    }

    public static void clearBarrierFocus(Zombie zombie) {
        if (zombie == null) {
            return;
        }

        zombie.getPersistentData().remove(FOCUS_X_KEY);
        zombie.getPersistentData().remove(FOCUS_Y_KEY);
        zombie.getPersistentData().remove(FOCUS_Z_KEY);
        zombie.getPersistentData().remove(FOCUS_UNTIL_TICK_KEY);
    }

    private static Vec3 resolveDirection(Zombie zombie, TargetContext targetContext) {
        if (zombie == null || targetContext == null) {
            return Vec3.ZERO;
        }

        if (targetContext.hasTarget()) {
            Vec3 toTarget = targetContext.targetPosition().subtract(zombie.position());
            Vec3 flat = normalizeFlat(toTarget);
            if (flat.lengthSqr() > 0.0001D) {
                return flat;
            }
        }

        return normalizeFlat(zombie.getLookAngle());
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        if (a == null || b == null) {
            return 0.0D;
        }

        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static BlockPos normalizeBarrierPos(ServerLevel level, BlockPos pos, BlockState state) {
        if (state != null && state.getBlock() instanceof DoorBlock && state.hasProperty(DoorBlock.HALF)) {
            if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
                return pos.below();
            }
        }

        return pos;
    }

    private static boolean isWoodenDoor(BlockState state, String id) {
        if (state == null || id == null || !(state.getBlock() instanceof DoorBlock)) {
            return false;
        }

        return state.is(net.minecraft.tags.BlockTags.WOODEN_DOORS) || id.endsWith("_door") && !id.contains("iron_door");
    }

    private static boolean isWoodenTrapdoor(BlockState state, String id) {
        if (state == null || id == null || !(state.getBlock() instanceof TrapDoorBlock)) {
            return false;
        }

        return state.is(net.minecraft.tags.BlockTags.WOODEN_TRAPDOORS) || id.endsWith("_trapdoor") && !id.contains("iron_trapdoor");
    }

    private static boolean isWoodenFenceGate(BlockState state, String id) {
        if (state == null || id == null || !(state.getBlock() instanceof FenceGateBlock)) {
            return false;
        }

        return id.endsWith("_fence_gate");
    }

    private static boolean isExtraAllowlisted(String id) {
        if (id == null || Config.barrierBreakExtraBlockAllowlist.isEmpty()) {
            return false;
        }

        return Config.barrierBreakExtraBlockAllowlist.contains(id);
    }

    private static boolean isDenylisted(String id) {
        if (id == null || !Config.barrierBreakRespectBlockDenylist || Config.barrierBreakBlockDenylist.isEmpty()) {
            return false;
        }

        return Config.barrierBreakBlockDenylist.contains(id);
    }

    private static Vec3 normalizeFlat(Vec3 vec) {
        if (vec == null) {
            return Vec3.ZERO;
        }

        Vec3 flat = new Vec3(vec.x, 0.0D, vec.z);
        if (flat.lengthSqr() <= 0.0001D) {
            return Vec3.ZERO;
        }

        return flat.normalize();
    }

    public static boolean isBarrierBetweenZombieAndTarget(Zombie zombie, BlockPos barrierPos, Vec3 targetPos) {
        if (zombie == null || barrierPos == null || targetPos == null) {
            return false;
        }

        Vec3 start = zombie.position().add(0.0D, zombie.getBbHeight() * 0.5D, 0.0D);
        Vec3 end = targetPos;
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length <= 0.0001D) {
            return false;
        }

        int steps = Math.max(4, (int) Math.ceil(length * 4.0D));
        for (int i = 1; i < steps; i++) {
            double t = (double) i / (double) steps;
            Vec3 sample = start.add(delta.scale(t));
            if (isSameBarrierBlock(zombie, barrierPos, BlockPos.containing(sample))) {
                return true;
            }
        }

        return false;
    }

    public static boolean isZombieOnValidAttackSide(Zombie zombie, BlockPos barrierPos, BlockState state, Vec3 targetPos) {
        if (zombie == null || barrierPos == null || state == null) {
            return false;
        }

        Vec3 barrierCenter = Vec3.atCenterOf(barrierPos);
        Vec3 zombieCenter = zombie.position().add(0.0D, zombie.getBbHeight() * 0.5D, 0.0D);
        double distanceToBarrier = zombieCenter.distanceTo(barrierCenter);
        if (distanceToBarrier > Math.max(0.1D, Config.barrierBreakMaxZombieDistanceToBarrier)) {
            return false;
        }

        if (Config.barrierBreakRequireTargetBehindBarrier) {
            if (targetPos == null) {
                return false;
            }

            if (!isBarrierBetweenZombieAndTarget(zombie, barrierPos, targetPos)) {
                return false;
            }
        }

        if (state.getBlock() instanceof DoorBlock && state.hasProperty(DoorBlock.FACING)) {
            Vec3 front = resolveDoorAttackFront(barrierCenter, state, targetPos);
            Vec3 lateralAxis = new Vec3(-front.z, 0.0D, front.x);
            Vec3 local = zombieCenter.subtract(barrierCenter);
            double frontSide = local.dot(front);
            double lateral = Math.abs(local.dot(lateralAxis));
            double maxDepth = Math.max(0.1D, Config.barrierBreakFrontDepth);
            if (Config.barrierBreakRejectZombiesBehindBarrier && targetPos != null && frontSide < 0.0D) {
                return false;
            }

            boolean depthAllowed = targetPos != null
                ? frontSide >= 0.0D && frontSide <= maxDepth
                : Math.abs(frontSide) <= maxDepth;

            return lateral <= Math.max(0.1D, Config.barrierBreakFrontWidth)
                && depthAllowed;
        }

        if (targetPos != null) {
            Vec3 toTarget = targetPos.subtract(zombieCenter);
            Vec3 toBarrier = barrierCenter.subtract(zombieCenter);
            Vec3 targetFlat = normalizeFlat(toTarget);
            Vec3 barrierFlat = normalizeFlat(toBarrier);
            if (targetFlat.lengthSqr() > 0.0001D && barrierFlat.lengthSqr() > 0.0001D) {
                double alignment = barrierFlat.dot(targetFlat);
                if (alignment < Math.max(0.0D, Config.barrierBreakMinPathAlignment)) {
                    return false;
                }

                Vec3 projected = targetFlat.scale(toBarrier.dot(targetFlat));
                double lateralDistance = toBarrier.subtract(projected).length();
                if (lateralDistance > Math.max(0.1D, Config.barrierBreakFrontWidth)) {
                    return false;
                }
            }
        }

        double dx = Math.abs(zombieCenter.x - barrierCenter.x);
        double dz = Math.abs(zombieCenter.z - barrierCenter.z);
        return dx <= Math.max(0.1D, Config.barrierBreakFrontWidth)
            || dz <= Math.max(0.1D, Config.barrierBreakFrontWidth)
            || zombieCenter.distanceTo(barrierCenter) <= Math.max(0.1D, Config.barrierBreakFrontDepth);
    }

    private static boolean isSameBarrierBlock(Zombie zombie, BlockPos normalizedBarrierPos, BlockPos sampledPos) {
        if (normalizedBarrierPos == null || sampledPos == null) {
            return false;
        }

        if (normalizedBarrierPos.equals(sampledPos)) {
            return true;
        }

        if (zombie == null) {
            return false;
        }

        BlockState state = zombie.level().getBlockState(normalizedBarrierPos);
        return state.getBlock() instanceof DoorBlock && normalizedBarrierPos.above().equals(sampledPos);
    }

    private static Vec3 resolveDoorAttackFront(Vec3 barrierCenter, BlockState state, Vec3 targetPos) {
        if (barrierCenter != null && targetPos != null) {
            Vec3 attackFront = normalizeFlat(barrierCenter.subtract(targetPos));
            if (attackFront.lengthSqr() > 0.0001D) {
                return attackFront;
            }
        }

        Direction facing = state.getValue(DoorBlock.FACING);
        return new Vec3(facing.getStepX(), 0.0D, facing.getStepZ());
    }

    public enum BarrierKind {
        NONE,
        WOODEN_DOOR,
        WOODEN_TRAPDOOR,
        WOODEN_FENCE_GATE,
        GLASS_BLOCK,
        GLASS_PANE,
        STAINED_GLASS,
        STAINED_GLASS_PANE,
        TINTED_GLASS,
        EXTRA_ALLOWLIST,
        UNSUPPORTED
    }

    public enum TargetType {
        NONE,
        PLAYER,
        MACHINE
    }

    public record TargetContext(TargetType kind, Vec3 targetPosition, boolean allowed, String reason) {
        public static TargetContext none() {
            return new TargetContext(TargetType.NONE, Vec3.ZERO, false, "no valid target");
        }

        public boolean hasTarget() {
            return kind != TargetType.NONE;
        }

        public double targetY() {
            return targetPosition == null ? 0.0D : targetPosition.y;
        }

        public double zombieY() {
            return 0.0D;
        }

        public double yDifference() {
            return 0.0D;
        }

        public double horizontalDistance() {
            return 0.0D;
        }

        public Vec3 direction() {
            return targetPosition;
        }
    }

    public record BarrierBreakAssessment(
        boolean enabled,
        boolean eligible,
        boolean active,
        String reason,
        TargetType targetType,
        BarrierKind barrierKind,
        BlockPos barrierPos,
        BlockPos normalizedPos,
        ResourceLocation blockId,
        int groupSize,
        int nearbyZombieCount,
        int durability,
        int damage,
        int cooldownRemaining,
        boolean hasTarget,
        boolean targetAllowed,
        boolean denylisted,
        boolean tintedBlocked,
        boolean supported,
        int eligibleZombieCount,
        int attackSideZombieCount,
        double zombieY,
        double targetY,
        double yDifference,
        double horizontalDistance,
        Vec3 direction
    ) {
        private static BarrierBreakAssessment empty() {
            return new BarrierBreakAssessment(
                false,
                false,
                false,
                "disabled",
                TargetType.NONE,
                BarrierKind.NONE,
                BlockPos.ZERO,
                BlockPos.ZERO,
                null,
                0,
                0,
                0,
                0,
                0,
                false,
                false,
                false,
                false,
                false,
                0,
                0,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                Vec3.ZERO
            );
        }
    }

    public record BarrierBlockInspection(
        BlockPos hitPos,
        BlockPos normalizedPos,
        ResourceLocation blockId,
        BarrierKind barrierKind,
        boolean supported,
        boolean denylisted,
        boolean tintedBlocked,
        int durability,
        int currentDamage,
        int eligibleZombieCount,
        int attackSideZombieCount,
        String reason,
        boolean canBreak,
        String blockClass,
        BlockState state
    ) {
        private static BarrierBlockInspection empty() {
            return emptyAt(BlockPos.ZERO);
        }

        private static BarrierBlockInspection emptyAt(BlockPos pos) {
            return new BarrierBlockInspection(
                pos == null ? BlockPos.ZERO : pos,
                pos == null ? BlockPos.ZERO : pos,
                null,
                BarrierKind.NONE,
                false,
                false,
                false,
                0,
                0,
                0,
                0,
                "not supported block",
                false,
                "unknown",
                null
            );
        }

        private BarrierBlockInspection withZombieCounts(int nearbyCount, int attackSideCount) {
            return new BarrierBlockInspection(hitPos, normalizedPos, blockId, barrierKind, supported, denylisted, tintedBlocked, durability, currentDamage, nearbyCount, attackSideCount, reason, canBreak, blockClass, state);
        }

        private BarrierBlockInspection withCurrentDamage(int damage) {
            return new BarrierBlockInspection(hitPos, normalizedPos, blockId, barrierKind, supported, denylisted, tintedBlocked, durability, damage, eligibleZombieCount, attackSideZombieCount, reason, canBreak, blockClass, state);
        }

        private BarrierBlockInspection withReason(String newReason) {
            return new BarrierBlockInspection(hitPos, normalizedPos, blockId, barrierKind, supported, denylisted, tintedBlocked, durability, currentDamage, eligibleZombieCount, attackSideZombieCount, newReason, canBreak, blockClass, state);
        }

        private BarrierBlockInspection withCanBreak(boolean newCanBreak) {
            return new BarrierBlockInspection(hitPos, normalizedPos, blockId, barrierKind, supported, denylisted, tintedBlocked, durability, currentDamage, eligibleZombieCount, attackSideZombieCount, reason, newCanBreak, blockClass, state);
        }
    }
}
