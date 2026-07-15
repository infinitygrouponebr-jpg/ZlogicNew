package infinitygroup.zlogic.compat.microtech;

import com.mojang.logging.LogUtils;
import infinitygroup.zlogic.Config;
import infinitygroup.zlogic.noise.NoiseManager;
import infinitygroup.zlogic.noise.NoiseSourceType;
import infinitygroup.zlogic.perf.PerformanceTracker;
import infinitygroup.zlogic.senses.LightInterestManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MicroTechNoiseHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Set<ResourceLocation> NOISY_MACHINE_IDS = Set.of(
        rl("microtech:energy_converter_t1"),
        rl("microtech:energy_converter"),
        rl("microtech:battery_t1"),
        rl("microtech:battery_t2"),
        rl("microtech:battery"),
        rl("microtech:tech_battery"),
        rl("microtech:evo_table"),
        rl("microtech:electric_furnace_t1"),
        rl("microtech:solar_panel_t1")
    );

    private static final Set<ResourceLocation> NOISY_MACHINE_DENYLIST = Set.of(
        rl("microtech:cable_t1")
    );

    private static final Set<String> ACTIVE_PROPERTY_NAMES = Set.of(
        "active",
        "lit",
        "running",
        "working",
        "powered"
    );

    private static final long COOLDOWN_PRUNE_MULTIPLIER = 4L;
    private static final Map<MachineNoiseKey, Long> LAST_NOISE_TICK_BY_MACHINE = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long> LAST_SCAN_TICK_BY_DIMENSION = new HashMap<>();
    private static final Map<Integer, List<ChunkOffset>> CHUNK_OFFSETS_BY_RADIUS = new HashMap<>();

    private MicroTechNoiseHandler() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!MicroTechCompat.isAvailable() || !Config.microTechMachineNoiseEnabled) {
            return;
        }

        long serverTick = event.getServer().getTickCount();
        int scanInterval = Math.max(1, Config.microTechMachineScanIntervalTicks);
        if (serverTick % scanInterval != 0) {
            return;
        }

        int interval = Math.max(1, Config.microTechMachineNoiseIntervalTicks);
        pruneCooldowns(serverTick, interval);

        int inspected = 0;
        int emitted = 0;
        Set<ChunkKey> scannedChunks = new HashSet<>();

        for (ServerLevel level : event.getServer().getAllLevels()) {
            List<ServerPlayer> players = collectEligiblePlayers(level);
            if (players.isEmpty()) {
                continue;
            }

            LAST_SCAN_TICK_BY_DIMENSION.put(level.dimension(), serverTick);
            int chunkRadius = Math.max(1, (int) Math.ceil(Config.microTechMachineNoiseRadius / 16.0D) + 1);
            List<ChunkOffset> chunkOffsets = CHUNK_OFFSETS_BY_RADIUS.computeIfAbsent(chunkRadius, MicroTechNoiseHandler::buildChunkOffsets);
            int levelInspected = 0;

            for (ServerPlayer player : players) {
                ChunkPos playerChunk = new ChunkPos(player.blockPosition());

                for (ChunkOffset offset : chunkOffsets) {
                    if (inspected >= Config.microTechMachineMaxBlocksPerScan) {
                        debug("MicroTech scan capped after {} block entities at tick {}", inspected, serverTick);
                        PerformanceTracker.recordMachineScan(levelInspected);
                        return;
                    }

                    int cx = playerChunk.x + offset.x();
                    int cz = playerChunk.z + offset.z();

                    ChunkKey chunkKey = new ChunkKey(level.dimension(), cx, cz);
                    if (!scannedChunks.add(chunkKey)) {
                        continue;
                    }

                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) {
                        continue;
                    }

                    for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                        if (inspected >= Config.microTechMachineMaxBlocksPerScan) {
                            debug("MicroTech scan capped after {} block entities at tick {}", inspected, serverTick);
                            PerformanceTracker.recordMachineScan(levelInspected);
                            return;
                        }

                        inspected++;
                        levelInspected++;
                        if (blockEntity == null || blockEntity.isRemoved() || blockEntity.getLevel() != level) {
                            continue;
                        }

                        MachineInspection inspection = inspectMachine(level, blockEntity.getBlockPos());
                        if (!inspection.treatedAsMicroTechMachine()) {
                            continue;
                        }

                        if (Config.microTechOnlyRunningMachinesMakeNoise && !inspection.wouldEmitNoise()) {
                            debug(
                                "MicroTech machine skipped: id={} pos={} dimension={} blockClass={} blockEntityClass={} keys={} allowlist={} denylist={} marker={} reason={}",
                                inspection.blockId(),
                                inspection.pos(),
                                level.dimension().location(),
                                inspection.blockClassName(),
                                inspection.blockEntityClassName(),
                                inspection.persistentKeys(),
                                inspection.inExplicitAllowlist(),
                                inspection.inDenylist(),
                                inspection.hasMicroTechActiveMarker(),
                                inspection.reason()
                            );
                            continue;
                        }

                        if (shouldRespectCooldown(level.dimension(), blockEntity.getBlockPos(), serverTick, interval)) {
                            continue;
                        }

                        NoiseManager.emitNoise(
                            level,
                            blockEntity.getBlockPos(),
                            Config.microTechMachineNoiseRadius,
                            Config.microTechMachineNoiseDurationTicks,
                            NoiseSourceType.MACHINE
                        );
                        LightInterestManager.emitMachineLight(level, blockEntity.getBlockPos());
                        LAST_NOISE_TICK_BY_MACHINE.put(new MachineNoiseKey(level.dimension(), blockEntity.getBlockPos().asLong()), serverTick);
                        emitted++;
                        debug(
                            "MicroTech machine noise emitted: id={} pos={} dimension={} reason={}",
                            inspection.blockId(),
                            inspection.pos(),
                            level.dimension().location(),
                            inspection.reason()
                        );
                    }
                }
            }

            PerformanceTracker.recordMachineScan(levelInspected);
        }

        if (emitted > 0) {
            debug("MicroTech machine scan completed: inspected={} emitted={} tick={}", inspected, emitted, serverTick);
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        ResourceKey<Level> dimension = level.dimension();
        LAST_NOISE_TICK_BY_MACHINE.keySet().removeIf(key -> key.dimension().equals(dimension));
        LAST_SCAN_TICK_BY_DIMENSION.remove(dimension);
    }

    public static MachineInspection inspectMachine(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return MachineInspection.empty();
        }

        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String blockClassName = state.getBlock().getClass().getName();
        String blockEntityClassName = blockEntity != null ? blockEntity.getClass().getName() : null;
        List<String> persistentKeys = blockEntity != null ? new ArrayList<>(blockEntity.getPersistentData().getAllKeys()) : List.of();
        boolean inExplicitAllowlist = isExplicitlyAllowedMicroTechMachine(blockId);
        boolean inDenylist = isDeniedMicroTechMachine(blockId);
        boolean hasMicroTechActiveMarker = blockEntity != null && blockEntity.getPersistentData().contains("microtech_active");
        Boolean microTechActive = hasMicroTechActiveMarker ? blockEntity.getPersistentData().getBoolean("microtech_active") : null;
        MachineActivity activity = evaluateMachineActivity(state, blockEntity, hasMicroTechActiveMarker, microTechActive);
        boolean microTechInstalled = MicroTechCompat.isAvailable();
        boolean compatEnabled = Config.enableMicroTechCompat;
        boolean machineNoiseEnabled = Config.microTechMachineNoiseEnabled;
        boolean onlyRunning = Config.microTechOnlyRunningMachinesMakeNoise;
        boolean treatedAsMicroTechMachine = !inDenylist && (inExplicitAllowlist || hasMicroTechActiveMarker);
        boolean wouldEmitNoise = microTechInstalled
            && compatEnabled
            && machineNoiseEnabled
            && treatedAsMicroTechMachine
            && (!onlyRunning || activity.shouldEmit());
        String reason = determineReason(
            inDenylist,
            inExplicitAllowlist,
            hasMicroTechActiveMarker,
            microTechActive,
            treatedAsMicroTechMachine,
            activity,
            machineNoiseEnabled,
            compatEnabled,
            microTechInstalled,
            onlyRunning,
            wouldEmitNoise
        );

        return new MachineInspection(
            pos.immutable(),
            blockId,
            blockClassName,
            blockEntityClassName,
            blockId != null && "microtech".equals(blockId.getNamespace()),
            inExplicitAllowlist,
            inDenylist,
            blockEntity != null,
            persistentKeys,
            hasMicroTechActiveMarker,
            microTechActive,
            activity,
            compatEnabled,
            machineNoiseEnabled,
            onlyRunning,
            microTechInstalled,
            treatedAsMicroTechMachine,
            wouldEmitNoise,
            reason
        );
    }

    public static boolean isMachineNoiseOnCooldown(ServerLevel level, BlockPos pos, long serverTick) {
        if (level == null || pos == null) {
            return false;
        }

        return getMachineNoiseCooldownRemainingTicks(level, pos, serverTick) > 0;
    }

    public static int getMachineNoiseCooldownRemainingTicks(ServerLevel level, BlockPos pos, long serverTick) {
        if (level == null || pos == null) {
            return 0;
        }

        Long lastTick = LAST_NOISE_TICK_BY_MACHINE.get(new MachineNoiseKey(level.dimension(), pos.asLong()));
        if (lastTick == null) {
            return 0;
        }

        int interval = Math.max(1, Config.microTechMachineNoiseIntervalTicks);
        long remaining = interval - (serverTick - lastTick);
        return (int) Math.max(0L, remaining);
    }

    public static Long getLastMachineNoiseTick(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }

        return LAST_NOISE_TICK_BY_MACHINE.get(new MachineNoiseKey(level.dimension(), pos.asLong()));
    }

    public static Long getLastMachineScanTick(ServerLevel level) {
        if (level == null) {
            return null;
        }

        return LAST_SCAN_TICK_BY_DIMENSION.get(level.dimension());
    }

    public static boolean isExplicitlyAllowedMicroTechMachine(ResourceLocation blockId) {
        return blockId != null && "microtech".equals(blockId.getNamespace()) && NOISY_MACHINE_IDS.contains(blockId);
    }

    public static boolean isDeniedMicroTechMachine(ResourceLocation blockId) {
        return blockId != null
            && "microtech".equals(blockId.getNamespace())
            && (NOISY_MACHINE_DENYLIST.contains(blockId) || blockId.getPath().contains("cable"));
    }

    private static List<ServerPlayer> collectEligiblePlayers(ServerLevel level) {
        List<ServerPlayer> result = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player == null || !player.isAlive() || player.isRemoved() || player.isSpectator() || player.isCreative()) {
                continue;
            }

            result.add(player);
        }

        return result;
    }

    private static MachineActivity evaluateMachineActivity(BlockState state, BlockEntity blockEntity, boolean hasMicroTechActiveMarker, Boolean microTechActive) {
        if (hasMicroTechActiveMarker) {
            return new MachineActivity(true, Boolean.TRUE.equals(microTechActive), Boolean.TRUE.equals(microTechActive));
        }

        if (blockEntity == null) {
            return MachineActivity.inactive(false, false);
        }

        boolean fallbackActive = hasActiveProperty(state)
            || hasTruthyPersistentFlag(blockEntity, "active", "lit", "running", "working", "powered")
            || hasPositivePersistentInt(blockEntity, "progress", "Progress", "energy", "Energy", "charge", "Charge", "burnTime", "BurnTime");
        return new MachineActivity(false, false, fallbackActive);
    }

    @SuppressWarnings("unchecked")
    private static boolean hasActiveProperty(BlockState state) {
        for (var property : state.getProperties()) {
            if (!ACTIVE_PROPERTY_NAMES.contains(property.getName())) {
                continue;
            }

            Object value;
            try {
                value = state.getValue((net.minecraft.world.level.block.state.properties.Property) property);
            } catch (RuntimeException ex) {
                continue;
            }

            if (value instanceof Boolean boolValue && boolValue) {
                return true;
            }

            if (value instanceof Number number && number.doubleValue() > 0.0D) {
                return true;
            }

            String stringValue = String.valueOf(value).toLowerCase();
            if (stringValue.equals("true") || stringValue.equals("on") || stringValue.equals("active") || stringValue.equals("running") || stringValue.equals("working")) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasTruthyPersistentFlag(BlockEntity blockEntity, String... keys) {
        if (blockEntity == null) {
            return false;
        }

        var tag = blockEntity.getPersistentData();
        for (String key : keys) {
            if (tag.getBoolean(key)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasPositivePersistentInt(BlockEntity blockEntity, String... keys) {
        if (blockEntity == null) {
            return false;
        }

        var tag = blockEntity.getPersistentData();
        for (String key : keys) {
            if (tag.getInt(key) > 0) {
                return true;
            }
        }

        return false;
    }

    private static boolean shouldRespectCooldown(ResourceKey<Level> dimension, BlockPos pos, long serverTick, int interval) {
        MachineNoiseKey key = new MachineNoiseKey(dimension, pos.asLong());
        Long lastTick = LAST_NOISE_TICK_BY_MACHINE.get(key);
        return lastTick != null && serverTick - lastTick < interval;
    }

    private static void pruneCooldowns(long serverTick, int interval) {
        long maxAge = Math.max(1L, interval) * COOLDOWN_PRUNE_MULTIPLIER;
        LAST_NOISE_TICK_BY_MACHINE.entrySet().removeIf(entry -> serverTick - entry.getValue() > maxAge);
    }

    private static List<ChunkOffset> buildChunkOffsets(int radius) {
        List<ChunkOffset> offsets = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                offsets.add(new ChunkOffset(dx, dz));
            }
        }

        offsets.sort((a, b) -> {
            double aDistance = a.distanceSq();
            double bDistance = b.distanceSq();
            return Double.compare(aDistance, bDistance);
        });
        return offsets;
    }

    private static void debug(String message, Object... args) {
        if (Config.debugLogs || Config.microTechDebugLogs) {
            LOGGER.info("[" + MicroTechCompat.MODID + "] " + message, args);
        }
    }

    private static ResourceLocation rl(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            throw new IllegalArgumentException("Invalid ResourceLocation: " + id);
        }

        return location;
    }

    private static String determineReason(
        boolean inDenylist,
        boolean inExplicitAllowlist,
        boolean hasMicroTechActiveMarker,
        Boolean microTechActive,
        boolean treatedAsMicroTechMachine,
        MachineActivity activity,
        boolean machineNoiseEnabled,
        boolean compatEnabled,
        boolean microTechInstalled,
        boolean onlyRunning,
        boolean wouldEmitNoise
    ) {
        if (inDenylist) {
            return "denylisted cable";
        }

        if (!machineNoiseEnabled) {
            return "microTechMachineNoiseEnabled=false";
        }

        if (!compatEnabled) {
            return "enableMicroTechCompat=false";
        }

        if (!microTechInstalled) {
            return "microtech not installed";
        }

        if (!treatedAsMicroTechMachine) {
            if (!inExplicitAllowlist && !hasMicroTechActiveMarker) {
                return "not in allowlist and no microtech_active marker";
            }

            return "not treated as MicroTech machine";
        }

        if (onlyRunning && !activity.shouldEmit()) {
            return hasMicroTechActiveMarker ? "inactive machine" : (activity.fallbackActive() ? "fallback active" : "inactive machine");
        }

        if (wouldEmitNoise) {
            return hasMicroTechActiveMarker ? "microtech_active marker" : "explicit allowlist";
        }

        return "unknown";
    }

    private record MachineNoiseKey(ResourceKey<Level> dimension, long packedPos) {
    }

    private record ChunkKey(ResourceKey<Level> dimension, int x, int z) {
    }

    private record ChunkOffset(int x, int z) {
        private double distanceSq() {
            return (double) x * x + (double) z * z;
        }
    }

    private record MachineActivity(boolean hasMicroTechActive, boolean microTechActive, boolean fallbackActive) {
        private static MachineActivity inactive(boolean hasMicroTechActive, boolean microTechActive) {
            return new MachineActivity(hasMicroTechActive, microTechActive, false);
        }

        private boolean shouldEmit() {
            return hasMicroTechActive ? microTechActive : fallbackActive;
        }
    }

    public record MachineInspection(
        BlockPos pos,
        ResourceLocation blockId,
        String blockClassName,
        String blockEntityClassName,
        boolean microTechNamespace,
        boolean inExplicitAllowlist,
        boolean inDenylist,
        boolean hasBlockEntity,
        List<String> persistentKeys,
        boolean hasMicroTechActiveMarker,
        Boolean microTechActive,
        MachineActivity activity,
        boolean microTechCompatEnabled,
        boolean machineNoiseEnabled,
        boolean onlyRunningMachinesMakeNoise,
        boolean microTechInstalled,
        boolean treatedAsMicroTechMachine,
        boolean wouldEmitNoise,
        String reason
    ) {
        public static MachineInspection empty() {
            return new MachineInspection(
                BlockPos.ZERO,
                null,
                "unknown",
                null,
                false,
                false,
                false,
                false,
                List.of(),
                false,
                null,
                MachineActivity.inactive(false, false),
                false,
                false,
                false,
                false,
                false,
                false,
                "empty"
            );
        }

        public String detectedState() {
            if (inDenylist) {
                return "INACTIVE";
            }

            if (hasMicroTechActiveMarker) {
                return Boolean.TRUE.equals(microTechActive) ? "ACTIVE" : "INACTIVE";
            }

            if (activity.fallbackActive()) {
                return "ACTIVE";
            }

            return "UNKNOWN";
        }
    }
}
