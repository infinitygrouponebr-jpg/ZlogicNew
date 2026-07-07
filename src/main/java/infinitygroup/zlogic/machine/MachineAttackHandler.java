package infinitygroup.zlogic.machine;

import com.mojang.logging.LogUtils;
import infinitygroup.zlogic.Config;
import infinitygroup.zlogic.Zlogic;
import infinitygroup.zlogic.compat.microtech.MicroTechNoiseHandler;
import infinitygroup.zlogic.noise.NoiseManager;
import infinitygroup.zlogic.noise.NoiseSourceType;
import infinitygroup.zlogic.perf.PerformanceTracker;
import infinitygroup.zlogic.zombie.ZombieEligibilityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MachineAttackHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LAST_ATTACK_TICK_KEY = "zlogic_machine_attack_last_tick";
    private static final String LAST_DIRECT_SCAN_TICK_KEY = "zlogic_machine_direct_scan_last_tick";
    private static final String TARGET_X_KEY = "zlogic_machine_target_x";
    private static final String TARGET_Y_KEY = "zlogic_machine_target_y";
    private static final String TARGET_Z_KEY = "zlogic_machine_target_z";
    private static final String NEXT_NAV_TICK_KEY = "zlogic_next_machine_nav_tick";
    private static final String HAS_TARGET_KEY = "zlogic_has_machine_target";
    private static final Map<Integer, List<BlockOffset>> DIRECT_SCAN_OFFSETS = new HashMap<>();

    private MachineAttackHandler() {
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!Config.enableZombieMachineAttacks) {
            return;
        }

        Entity entity = event.getEntity();
        if (!(entity instanceof Zombie zombie) || zombie.level().isClientSide() || !(zombie.level() instanceof ServerLevel level)) {
            return;
        }

        if (!ZombieEligibilityHelper.isEligibleForMachineAttack(zombie)) {
            return;
        }

        PerformanceTracker.recordEntityProcessed();
        PerformanceTracker.recordZombieProcessed();

        if (Config.zombieMachineAttackUseDirectDetection && Config.enableDirectMachineAttraction) {
            DirectMachineSelection selection = selectDirectMachine(level, zombie);
            if (selection == null || selection.pos() == null) {
                return;
            }

            MachineAttackAssessment assessment = selection.assessment();
            BlockPos pos = selection.pos();
            double attackRange = Math.max(0.1D, Config.zombieMachineAttackRange);
            double distanceSq = zombie.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);

            if (assessment == null && distanceSq <= attackRange * attackRange) {
                assessment = inspectMachineAttack(level, pos);
            }

            if (assessment != null && !assessment.attackable()) {
                if (Config.zombieMachineAttackDebugLogs || Config.debugLogs) {
                    debug(
                        "Zombie direct machine target refused: zombie={} pos={} block={} reason={}",
                        zombie.getType().toShortString(),
                        pos,
                        assessment.inspection().blockId(),
                        assessment.reason()
                    );
                }
                return;
            }

            if (isOnAttackCooldown(zombie)) {
                return;
            }

            if (distanceSq > attackRange * attackRange) {
                if (shouldRepathMachine(zombie, pos, level.getGameTime())) {
                    navigateToTarget(zombie, pos);
                    scheduleMachineRepath(zombie, level.getGameTime());
                    debug(
                        "Zombie repathed toward machine: zombie={} pos={} nextTick={}",
                        zombie.getType().toShortString(),
                        pos,
                        getNextMachineNavTick(zombie)
                    );
                } else if (Config.zombieMachineAttackDebugLogs || Config.debugLogs) {
                    debug(
                        "Zombie skipped machine repath by cooldown: zombie={} pos={} nextTick={}",
                        zombie.getType().toShortString(),
                        pos,
                        getNextMachineNavTick(zombie)
                    );
                }
                return;
            }

            performAttack(level, zombie, pos, assessment != null ? assessment : inspectMachineAttack(level, pos));
            return;
        }

        LegacyMachineSelection selection = selectLegacyMachine(level, zombie);
        if (selection == null || selection.assessment() == null || selection.pos() == null || !selection.assessment().attackable()) {
            return;
        }

        if (isOnAttackCooldown(zombie)) {
            return;
        }

        BlockPos pos = selection.pos();
        double attackRange = Math.max(0.1D, Config.zombieMachineAttackRange);
        double distanceSq = zombie.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        if (distanceSq > attackRange * attackRange) {
            return;
        }

        performAttack(level, zombie, pos, selection.assessment());
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            MachineDamageManager.clearLevel(level);
        }
    }

    public static MachineAttackAssessment inspectMachineAttack(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return MachineAttackAssessment.empty();
        }

        MicroTechNoiseHandler.MachineInspection inspection = MicroTechNoiseHandler.inspectMachine(level, pos);
        int currentDamage = MachineDamageManager.getDamage(level, pos);
        int durability = MachineDamageManager.getDurabilityFor(level.getBlockState(pos), pos);
        boolean recentNoise = NoiseManager.hasNearbyNoise(
            level,
            pos,
            Math.max(Config.microTechMachineNoiseRadius, Config.zombieMachineAttackRange),
            NoiseSourceType.MACHINE
        );

        boolean directDetectionEnabled = Config.zombieMachineAttackUseDirectDetection;
        boolean directAttractionEnabled = Config.enableDirectMachineAttraction;
        double directAttractionRadius = Config.directMachineAttractionRadius;
        boolean directValidMachine = isDirectMachineCandidate(inspection);
        boolean directMachineActive = isDirectMachineActive(inspection);
        boolean directMachineEligible = directValidMachine && (!Config.directMachineAttractionOnlyActiveMachines || directMachineActive);
        int attackCooldownRemaining = 0;
        boolean attackable = determineAttackable(inspection, recentNoise, directMachineEligible, directDetectionEnabled);
        String reason = determineReason(inspection, recentNoise, directMachineEligible, directDetectionEnabled, attackable);

        return new MachineAttackAssessment(
            pos.immutable(),
            inspection,
            currentDamage,
            durability,
            recentNoise,
            directDetectionEnabled,
            directAttractionEnabled,
            directAttractionRadius,
            directValidMachine,
            directMachineActive,
            directMachineEligible,
            attackCooldownRemaining,
            attackable,
            reason
        );
    }

    private static void performAttack(ServerLevel level, Zombie zombie, BlockPos pos, MachineAttackAssessment assessment) {
        if (pos == null || assessment == null) {
            return;
        }

        zombie.getLookControl().setLookAt(Vec3.atCenterOf(pos));
        zombie.swing(InteractionHand.MAIN_HAND);

        int newDamage = MachineDamageManager.damageMachine(level, pos, Math.max(1, Config.zombieMachineDamagePerHit));
        setLastAttackTick(zombie, zombie.tickCount);

        level.playSound(null, pos, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.8F, 0.85F + zombie.getRandom().nextFloat() * 0.3F);
        level.sendParticles(
            ParticleTypes.SMOKE,
            pos.getX() + 0.5D,
            pos.getY() + 0.75D,
            pos.getZ() + 0.5D,
            2,
            0.15D,
            0.10D,
            0.15D,
            0.01D
        );

        if (Config.zombieMachineAttackDebugLogs || Config.debugLogs) {
            debug(
                "Zombie hit machine: zombie={} pos={} block={} damage={}/{} reason={}",
                zombie.getType().toShortString(),
                pos,
                assessment.inspection().blockId(),
                newDamage,
                assessment.durability(),
                assessment.reason()
            );
        }

        if (newDamage < assessment.durability() || !Config.zombieMachineBreakBlocks) {
            return;
        }

        if (Config.zombieMachineBreakBlocks) {
            if (Config.zombieMachineDropBlock) {
                level.destroyBlock(pos, true, zombie, 512);
            } else {
                level.removeBlock(pos, false);
            }
        }

        MachineDamageManager.clearDamage(level, pos);

        if (Config.zombieMachineAttackDebugLogs || Config.debugLogs) {
            debug(
                "Zombie broke machine: zombie={} pos={} block={} drop={}",
                zombie.getType().toShortString(),
                pos,
                assessment.inspection().blockId(),
                Config.zombieMachineDropBlock
            );
        }
    }

    private static DirectMachineSelection selectDirectMachine(ServerLevel level, Zombie zombie) {
        LivingEntity currentTarget = zombie.getTarget();
        double ignoreRadius = Math.max(0.0D, Config.directMachineIgnoreMachineIfPlayerTargetWithin);
        if (currentTarget instanceof Player playerTarget && playerTarget.isAlive() && !playerTarget.isRemoved()) {
            if (zombie.distanceToSqr(playerTarget) <= ignoreRadius * ignoreRadius) {
                return null;
            }
        } else if (currentTarget != null && currentTarget.isAlive() && !currentTarget.isRemoved()) {
            return null;
        }

        long gameTime = level.getGameTime();
        int scanInterval = Math.max(1, Config.directMachineAttractionCheckIntervalTicks);
        Long lastScanTick = getLastDirectScanTick(zombie);
        BlockPos storedTarget = readStoredTarget(zombie);
        boolean shouldRescan = lastScanTick == null || gameTime - lastScanTick >= scanInterval || storedTarget == null;

        if (storedTarget != null && !shouldRescan) {
            if (Config.enableDirectMachineAttraction && shouldRepathMachine(zombie, storedTarget, gameTime)) {
                navigateToTarget(zombie, storedTarget);
                scheduleMachineRepath(zombie, gameTime);
            }

            return new DirectMachineSelection(storedTarget, null);
        }

        if (!shouldRescan) {
            return null;
        }

        updateDirectScanTick(zombie, gameTime);

        MachineAttackTarget best = findBestDirectTarget(level, zombie);
        if (best == null) {
            clearStoredTarget(zombie);
            return null;
        }

        storeTarget(zombie, best.pos());
        if (Config.enableDirectMachineAttraction) {
            navigateToTarget(zombie, best.pos());
            scheduleMachineRepath(zombie, level.getGameTime());
        }

        if (Config.zombieMachineAttackDebugLogs || Config.debugLogs) {
            debug(
                "Zombie found direct machine target: zombie={} pos={} block={} state={} reason={}",
                zombie.getType().toShortString(),
                best.pos(),
                best.assessment().inspection().blockId(),
                best.assessment().inspection().detectedState(),
                best.assessment().reason()
            );
        }

        return new DirectMachineSelection(best.pos(), best.assessment());
    }

    private static LegacyMachineSelection selectLegacyMachine(ServerLevel level, Zombie zombie) {
        double range = Math.max(0.1D, Config.zombieMachineAttackRange);
        double rangeSq = range * range;
        Vec3 zombieCenter = zombie.position();

        LegacyMachineSelection best = null;
        double bestDistance = Double.MAX_VALUE;
        int checkedBlocks = 0;

        for (int x = floor(zombie.getX() - range); x <= floor(zombie.getX() + range); x++) {
            for (int y = floor(zombie.getY() - range); y <= floor(zombie.getY() + range); y++) {
                for (int z = floor(zombie.getZ() - range); z <= floor(zombie.getZ() + range); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.hasChunkAt(pos)) {
                        continue;
                    }

                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity == null || blockEntity.isRemoved()) {
                        continue;
                    }

                    if (zombieCenter.distanceToSqr(Vec3.atCenterOf(pos)) > rangeSq) {
                        continue;
                    }

                    MachineAttackAssessment assessment = inspectMachineAttack(level, pos);
                    checkedBlocks++;
                    if (!assessment.attackable()) {
                        continue;
                    }

                    double distance = zombie.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = new LegacyMachineSelection(pos, assessment);
                    }
                }
            }
        }

        PerformanceTracker.recordMachineScan(checkedBlocks);

        return best;
    }

    private static MachineAttackTarget findBestDirectTarget(ServerLevel level, Zombie zombie) {
        double radius = Math.max(0.1D, Config.directMachineAttractionRadius);
        int scanLimit = Math.max(1, Config.directMachineAttractionMaxScanBlocksPerZombie);
        int intRadius = Math.max(1, (int) Math.ceil(radius));
        List<BlockOffset> offsets = DIRECT_SCAN_OFFSETS.computeIfAbsent(intRadius, MachineAttackHandler::buildOffsets);

        BlockPos origin = zombie.blockPosition();
        MachineAttackTarget best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        int scanned = 0;

        for (BlockOffset offset : offsets) {
            if (scanned >= scanLimit) {
                break;
            }

            BlockPos pos = origin.offset(offset.x(), offset.y(), offset.z());
            if (!level.hasChunkAt(pos)) {
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null || blockEntity.isRemoved()) {
                continue;
            }

            scanned++;
            MachineAttackAssessment assessment = inspectMachineAttack(level, pos);
            if (!assessment.directMachineEligible() || !assessment.attackable()) {
                continue;
            }

            double distanceSq = zombie.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            if (distanceSq > radius * radius) {
                continue;
            }

            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                best = new MachineAttackTarget(pos.immutable(), assessment);
            }
        }

        PerformanceTracker.recordMachineScan(scanned);

        return best;
    }

    private static List<BlockOffset> buildOffsets(int radius) {
        List<BlockOffset> offsets = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if ((double) dx * dx + (double) dy * dy + (double) dz * dz > (double) radius * radius) {
                        continue;
                    }

                    offsets.add(new BlockOffset(dx, dy, dz));
                }
            }
        }

        offsets.sort(Comparator.comparingDouble(BlockOffset::distanceSq));
        return offsets;
    }

    private static boolean isOnAttackCooldown(Zombie zombie) {
        return getAttackCooldownRemaining(zombie) > 0;
    }

    private static int getAttackCooldownRemaining(Zombie zombie) {
        if (zombie == null) {
            return 0;
        }

        int cooldown = Math.max(1, Config.zombieMachineAttackCooldownTicks);
        long lastAttackTick = zombie.getPersistentData().getLong(LAST_ATTACK_TICK_KEY);
        if (lastAttackTick <= 0L) {
            return 0;
        }

        long remaining = cooldown - (zombie.tickCount - lastAttackTick);
        return (int) Math.max(0L, remaining);
    }

    private static void setLastAttackTick(Zombie zombie, int tick) {
        zombie.getPersistentData().putLong(LAST_ATTACK_TICK_KEY, tick);
    }

    private static BlockPos readStoredTarget(Zombie zombie) {
        if (zombie == null) {
            return null;
        }

        if (!zombie.getPersistentData().contains(TARGET_X_KEY) || !zombie.getPersistentData().contains(TARGET_Y_KEY) || !zombie.getPersistentData().contains(TARGET_Z_KEY)) {
            return null;
        }

        return new BlockPos(
            zombie.getPersistentData().getInt(TARGET_X_KEY),
            zombie.getPersistentData().getInt(TARGET_Y_KEY),
            zombie.getPersistentData().getInt(TARGET_Z_KEY)
        );
    }

    private static Long getLastDirectScanTick(Zombie zombie) {
        if (zombie == null || !zombie.getPersistentData().contains(LAST_DIRECT_SCAN_TICK_KEY)) {
            return null;
        }

        return zombie.getPersistentData().getLong(LAST_DIRECT_SCAN_TICK_KEY);
    }

    private static void updateDirectScanTick(Zombie zombie, long tick) {
        if (zombie != null) {
            zombie.getPersistentData().putLong(LAST_DIRECT_SCAN_TICK_KEY, tick);
        }
    }

    private static void storeTarget(Zombie zombie, BlockPos pos) {
        if (zombie == null || pos == null) {
            return;
        }

        zombie.getPersistentData().putBoolean(HAS_TARGET_KEY, true);
        zombie.getPersistentData().putInt(TARGET_X_KEY, pos.getX());
        zombie.getPersistentData().putInt(TARGET_Y_KEY, pos.getY());
        zombie.getPersistentData().putInt(TARGET_Z_KEY, pos.getZ());
    }

    private static void clearStoredTarget(Zombie zombie) {
        if (zombie == null) {
            return;
        }

        zombie.getPersistentData().remove(TARGET_X_KEY);
        zombie.getPersistentData().remove(TARGET_Y_KEY);
        zombie.getPersistentData().remove(TARGET_Z_KEY);
        zombie.getPersistentData().remove(LAST_DIRECT_SCAN_TICK_KEY);
        zombie.getPersistentData().remove(NEXT_NAV_TICK_KEY);
        zombie.getPersistentData().remove(HAS_TARGET_KEY);
        debug("Zombie cleared machine target: zombie={} pos={}", zombie.getType().toShortString(), zombie.blockPosition());
    }

    private static void navigateToTarget(Zombie zombie, BlockPos pos) {
        if (zombie == null || pos == null) {
            return;
        }

        PathNavigation navigation = zombie.getNavigation();
        BlockPos currentTarget = readStoredTarget(zombie);
        if (currentTarget != null && currentTarget.equals(pos) && !shouldRepathMachine(zombie, pos, zombie.level().getGameTime())) {
            return;
        }

        navigation.moveTo(
            pos.getX() + 0.5D,
            pos.getY(),
            pos.getZ() + 0.5D,
            Math.max(0.0D, Config.directMachineAttractionNavigationSpeed)
        );
    }

    private static boolean isDirectMachineCandidate(MicroTechNoiseHandler.MachineInspection inspection) {
        if (inspection == null || isAttackDenied(inspection.blockId())) {
            return false;
        }

        if (!inspection.treatedAsMicroTechMachine()) {
            return false;
        }

        if (Config.directMachineAttractionOnlyMicroTech && !inspection.microTechNamespace()) {
            return false;
        }

        if (Config.directMachineAttractionOnlyActiveMachines && !"ACTIVE".equals(inspection.detectedState())) {
            return false;
        }

        return true;
    }

    private static boolean isDirectMachineActive(MicroTechNoiseHandler.MachineInspection inspection) {
        return inspection != null && "ACTIVE".equals(inspection.detectedState());
    }

    private static boolean determineAttackable(
        MicroTechNoiseHandler.MachineInspection inspection,
        boolean recentNoise,
        boolean directMachineEligible,
        boolean directDetectionEnabled
    ) {
        if (inspection == null || isAttackDenied(inspection.blockId())) {
            return false;
        }

        if (!Config.enableZombieMachineAttacks) {
            return false;
        }

        if (Config.zombieMachineAttackOnlyMicroTech && !inspection.microTechNamespace()) {
            return false;
        }

        if (!inspection.treatedAsMicroTechMachine()) {
            return false;
        }

        if (Config.zombieMachineAttackOnlyActiveMachines && !isDirectMachineActive(inspection)) {
            return false;
        }

        if (directDetectionEnabled) {
            return directMachineEligible;
        }

        if (Config.zombieMachineAttackRequireNoise && !recentNoise) {
            return false;
        }

        return true;
    }

    private static String determineReason(
        MicroTechNoiseHandler.MachineInspection inspection,
        boolean recentNoise,
        boolean directMachineEligible,
        boolean directDetectionEnabled,
        boolean attackable
    ) {
        if (inspection == null) {
            return "empty";
        }

        if (isAttackDenied(inspection.blockId())) {
            return "denylisted";
        }

        if (!Config.enableZombieMachineAttacks) {
            return "feature disabled";
        }

        if (Config.zombieMachineAttackOnlyMicroTech && !inspection.microTechNamespace()) {
            return "not MicroTech namespace";
        }

        if (!inspection.treatedAsMicroTechMachine()) {
            return "not a recognized MicroTech machine";
        }

        if (Config.zombieMachineAttackOnlyActiveMachines && !isDirectMachineActive(inspection)) {
            return "inactive machine";
        }

        if (directDetectionEnabled) {
            if (!directMachineEligible) {
                return "not eligible for direct attack";
            }

            return attackable ? "direct detection" : "not attackable";
        }

        if (Config.zombieMachineAttackRequireNoise && !recentNoise) {
            return "no recent MACHINE noise";
        }

        return attackable ? "attackable" : "not attackable";
    }

    private static boolean isAttackDenied(ResourceLocation blockId) {
        if (blockId == null || !"microtech".equals(blockId.getNamespace())) {
            return false;
        }

        String path = blockId.getPath();
        return path.contains("cable") || path.contains("solar");
    }

    public static boolean hasMachineTarget(Zombie zombie) {
        return zombie != null && zombie.getPersistentData().getBoolean(HAS_TARGET_KEY) && readStoredTarget(zombie) != null;
    }

    public static boolean hasValidMachineTarget(ServerLevel level, Zombie zombie) {
        BlockPos target = readStoredTarget(zombie);
        if (level == null || zombie == null || target == null) {
            return false;
        }

        MachineAttackAssessment assessment = inspectMachineAttack(level, target);
        return assessment.directMachineEligible() && assessment.attackable();
    }

    public static BlockPos getMachineTarget(Zombie zombie) {
        return readStoredTarget(zombie);
    }

    private static boolean shouldRepathMachine(Zombie zombie, BlockPos pos, long gameTime) {
        if (zombie == null || pos == null) {
            return false;
        }

        PathNavigation navigation = zombie.getNavigation();
        if (navigation.isDone()) {
            return true;
        }

        BlockPos currentTarget = readStoredTarget(zombie);
        if (currentTarget == null || !currentTarget.equals(pos)) {
            return true;
        }

        long nextNavTick = getNextMachineNavTick(zombie);
        if (nextNavTick <= 0L || gameTime >= nextNavTick) {
            return true;
        }

        double repathDistance = Math.max(0.0D, Config.zombieMachineAttackRange + 1.5D);
        return zombie.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > repathDistance * repathDistance;
    }

    private static void scheduleMachineRepath(Zombie zombie, long gameTime) {
        if (zombie == null) {
            return;
        }

        zombie.getPersistentData().putLong(NEXT_NAV_TICK_KEY, gameTime + Math.max(1, Config.directMachineAttractionRepathIntervalTicks));
    }

    private static long getNextMachineNavTick(Zombie zombie) {
        if (zombie == null || !zombie.getPersistentData().contains(NEXT_NAV_TICK_KEY)) {
            return 0L;
        }

        return zombie.getPersistentData().getLong(NEXT_NAV_TICK_KEY);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static void debug(String message, Object... args) {
        if (Config.debugLogs || Config.zombieMachineAttackDebugLogs) {
            LOGGER.info("[" + Zlogic.MODID + "] " + message, args);
        }
    }

    public record MachineAttackAssessment(
        BlockPos pos,
        MicroTechNoiseHandler.MachineInspection inspection,
        int currentDamage,
        int durability,
        boolean hasRecentMachineNoise,
        boolean directDetectionEnabled,
        boolean directAttractionEnabled,
        double directAttractionRadius,
        boolean directValidMachine,
        boolean directMachineActive,
        boolean directMachineEligible,
        int attackCooldownRemaining,
        boolean attackable,
        String reason
    ) {
        private static MachineAttackAssessment empty() {
            return new MachineAttackAssessment(
                BlockPos.ZERO,
                MicroTechNoiseHandler.MachineInspection.empty(),
                0,
                Config.machineDurabilityDefault,
                false,
                Config.zombieMachineAttackUseDirectDetection,
                Config.enableDirectMachineAttraction,
                Config.directMachineAttractionRadius,
                false,
                false,
                false,
                0,
                false,
                "empty"
            );
        }
    }

    private record MachineAttackTarget(BlockPos pos, MachineAttackAssessment assessment) {
    }

    private record DirectMachineSelection(BlockPos pos, MachineAttackAssessment assessment) {
    }

    private record LegacyMachineSelection(BlockPos pos, MachineAttackAssessment assessment) {
    }

    private record BlockOffset(int x, int y, int z) {
        private double distanceSq() {
            return (double) x * x + (double) y * y + (double) z * z;
        }
    }
}
