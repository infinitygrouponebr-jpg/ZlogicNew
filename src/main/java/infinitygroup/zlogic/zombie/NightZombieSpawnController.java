package infinitygroup.zlogic.zombie;

import com.mojang.logging.LogUtils;
import infinitygroup.zlogic.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class NightZombieSpawnController {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static long serverTickCounter;
    private static long lastAttemptTick = -1L;
    private static int lastSpawnedCount;
    private static String lastFailureReason = "not attempted";

    private NightZombieSpawnController() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!Config.enableNightZombieSpawns) {
            return;
        }

        MinecraftServer server = event.getServer();
        if (server.getTickCount() == 0) {
            return;
        }

        int interval = Math.max(1, Config.nightSpawnIntervalTicks);
        if (serverTickCounter++ % interval != 0) {
            return;
        }

        NightSpawnAttemptResult result = attemptFromServer(server, false, false);
        updateLastResult(server.getTickCount(), result);
        if (!result.success()) {
            debug("Night spawn failed: reason={} tick={}", result.reason(), server.getTickCount());
        }
    }

    public static NightSpawnAttemptResult forceSpawnNearPlayer(ServerPlayer player) {
        if (player == null) {
            return NightSpawnAttemptResult.failed("player null");
        }

        NightSpawnAttemptResult result = attemptWave(player.serverLevel(), player, true, true);
        updateLastResult(player.serverLevel().getGameTime(), result);
        if (!result.success()) {
            debug("Force night spawn failed: reason={} tick={}", result.reason(), player.serverLevel().getGameTime());
        }
        return result;
    }

    public static NightSpawnAttemptResult attemptFromServer(MinecraftServer server, boolean ignoreInterval, boolean ignoreChance) {
        if (server == null) {
            return NightSpawnAttemptResult.failed("server null");
        }

        List<ServerPlayer> eligiblePlayers = collectEligiblePlayers(server);
        if (eligiblePlayers.isEmpty()) {
            debug("Night spawn skipped: no eligible players online.");
            return NightSpawnAttemptResult.failed("no eligible players");
        }

        ServerPlayer player = eligiblePlayers.get(ThreadLocalRandom.current().nextInt(eligiblePlayers.size()));
        return attemptWave(player.serverLevel(), player, ignoreInterval, ignoreChance);
    }

    public static int getLastAttemptTick() {
        return (int) Math.max(0L, lastAttemptTick);
    }

    public static int getLastSpawnedCount() {
        return lastSpawnedCount;
    }

    public static String getLastFailureReason() {
        return lastFailureReason;
    }

    private static NightSpawnAttemptResult attemptWave(ServerLevel level, ServerPlayer anchorPlayer, boolean ignoreInterval, boolean ignoreChance) {
        if (level == null || anchorPlayer == null) {
            return NightSpawnAttemptResult.failed("level or player null");
        }

        long tick = level.getServer().getTickCount();
        debug(
            "Night spawn attempt: player={}, dimension={}, tick={}",
            anchorPlayer.getGameProfile().getName(),
            level.dimension().location(),
            tick
        );

        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return NightSpawnAttemptResult.failed("peaceful");
        }

        if (!isAllowedDimension(level)) {
            return NightSpawnAttemptResult.failed("dimension not allowed");
        }

        if (!isSpawnWindowOpen(level)) {
            return NightSpawnAttemptResult.failed("spawn window closed");
        }

        if (Config.nightSpawnRespectDoMobSpawning && !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
            return NightSpawnAttemptResult.failed("doMobSpawning disabled");
        }

        if (!ignoreChance && level.getRandom().nextDouble() > Config.nightSpawnChance) {
            return NightSpawnAttemptResult.failed("chance roll failed");
        }

        int groupSize = clampGroupSize(Config.nightSpawnMinGroupSize, Config.nightSpawnMaxGroupSize, level.getRandom());
        if (groupSize <= 0) {
            return NightSpawnAttemptResult.failed("group size resolved to 0");
        }

        BlockPos baseSpawnPos = findBaseSpawnPosition(level, anchorPlayer, level.getRandom());
        if (baseSpawnPos == null) {
            return NightSpawnAttemptResult.failed("no valid spawn position");
        }

        int nearbyCount = countNearbyZombies(level, baseSpawnPos);
        if (nearbyCount >= Config.nightSpawnMaxNearbyZombies) {
            return NightSpawnAttemptResult.failed("nearby zombie cap reached");
        }

        int allowedSpawns = Math.min(groupSize, Config.nightSpawnMaxNearbyZombies - nearbyCount);
        int created = 0;
        for (int i = 0; i < allowedSpawns; i++) {
            if (countNearbyZombies(level, baseSpawnPos) >= Config.nightSpawnMaxNearbyZombies) {
                break;
            }

            BlockPos spawnPos = findSpawnPositionAround(level, anchorPlayer, baseSpawnPos, level.getRandom());
            if (spawnPos == null || !level.hasChunkAt(spawnPos)) {
                continue;
            }

            if (countNearbyZombies(level, spawnPos) >= Config.nightSpawnMaxNearbyZombies) {
                continue;
            }

            Zombie zombie = EntityType.ZOMBIE.create(level);
            if (zombie == null) {
                continue;
            }

            ZombieMarkingHelper.markNightSpawned(zombie);
            zombie.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, level.getRandom().nextFloat() * 360.0F, 0.0F);
            zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null);
            ZombieMarkingHelper.markNightSpawned(zombie);

            if (level.addFreshEntity(zombie)) {
                created++;
            }
        }

        debug(
            "Night spawn finished: created {} zombie(s) near {} in {}.",
            created,
            anchorPlayer.getGameProfile().getName(),
            level.dimension().location()
        );

        return created > 0
            ? NightSpawnAttemptResult.success(created, baseSpawnPos, "spawned")
            : NightSpawnAttemptResult.failed("no zombies created");
    }

    private static void updateLastResult(long tick, NightSpawnAttemptResult result) {
        lastAttemptTick = tick;
        if (result == null) {
            lastSpawnedCount = 0;
            lastFailureReason = "no result";
            return;
        }

        lastSpawnedCount = result.spawnedCount();
        lastFailureReason = result.reason();
    }

    private static List<ServerPlayer> collectEligiblePlayers(MinecraftServer server) {
        List<ServerPlayer> eligiblePlayers = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
                continue;
            }

            ServerLevel level = player.serverLevel();
            if (!isAllowedDimension(level)) {
                continue;
            }

            if (Config.nightSpawnRespectDoMobSpawning && !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                continue;
            }

            eligiblePlayers.add(player);
        }

        return eligiblePlayers;
    }

    private static boolean isAllowedDimension(ServerLevel level) {
        String dimensionId = level.dimension().location().toString();
        return Config.nightSpawnAllowedDimensions.isEmpty() || Config.nightSpawnAllowedDimensions.contains(dimensionId);
    }

    private static boolean isSpawnWindowOpen(ServerLevel level) {
        if (Config.nightSpawnOnlyAtNight && level.isDay()) {
            return Config.nightSpawnAllowDuringThunder && level.isThundering();
        }

        if (level.isNight()) {
            return true;
        }

        return Config.nightSpawnAllowDuringThunder && level.isThundering();
    }

    private static int clampGroupSize(int min, int max, RandomSource random) {
        int lower = Math.max(1, Math.min(min, max));
        int upper = Math.max(lower, Math.max(min, max));
        return lower + random.nextInt(upper - lower + 1);
    }

    private static BlockPos findBaseSpawnPosition(ServerLevel level, ServerPlayer player, RandomSource random) {
        int attempts = 12;
        int minRadius = Math.max(1, Math.min(Config.nightSpawnRadiusMin, Config.nightSpawnRadiusMax));
        int maxRadius = Math.max(minRadius, Math.max(Config.nightSpawnRadiusMin, Config.nightSpawnRadiusMax));

        for (int i = 0; i < attempts; i++) {
            double angle = random.nextDouble() * (Math.PI * 2.0D);
            double distance = minRadius + random.nextDouble() * (maxRadius - minRadius);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);

            BlockPos surfacePos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1, z);
            if (isValidSpawnPos(level, surfacePos)) {
                return surfacePos;
            }

            BlockPos cavePos = findNearbyCavePosition(level, player, x, z);
            if (cavePos != null) {
                return cavePos;
            }
        }

        return null;
    }

    private static BlockPos findSpawnPositionAround(ServerLevel level, ServerPlayer player, BlockPos basePos, RandomSource random) {
        int attempts = 8;
        for (int i = 0; i < attempts; i++) {
            int xOffset = random.nextInt(5) - 2;
            int zOffset = random.nextInt(5) - 2;
            int yOffset = random.nextInt(3) - 1;
            BlockPos candidate = basePos.offset(xOffset, yOffset, zOffset);
            if (isValidSpawnPos(level, candidate)) {
                return candidate;
            }

            BlockPos fallback = findNearbyCavePosition(level, player, candidate.getX(), candidate.getZ());
            if (fallback != null) {
                return fallback;
            }
        }

        return null;
    }

    private static BlockPos findNearbyCavePosition(ServerLevel level, ServerPlayer player, int x, int z) {
        int playerY = Mth.floor(player.getY());
        int lowerY = Math.max(level.getMinBuildHeight() + 1, playerY - 8);
        int upperY = Math.min(level.getMaxBuildHeight() - 2, playerY + 8);

        for (int y = upperY; y >= lowerY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (isValidSpawnPos(level, pos)) {
                return pos;
            }
        }

        return null;
    }

    private static boolean isValidSpawnPos(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }

        if (Config.nightSpawnRequireDarkness && level.getMaxLocalRawBrightness(pos) > Config.nightSpawnMaxLightLevel) {
            return false;
        }

        BlockState feetState = level.getBlockState(pos);
        BlockState headState = level.getBlockState(pos.above());
        BlockState groundState = level.getBlockState(pos.below());
        FluidState feetFluid = level.getFluidState(pos);
        FluidState headFluid = level.getFluidState(pos.above());
        FluidState groundFluid = level.getFluidState(pos.below());

        if (!feetState.isAir() || !headState.isAir() || !feetFluid.isEmpty() || !headFluid.isEmpty() || !groundFluid.isEmpty()) {
            return false;
        }

        if (!groundState.isFaceSturdy(level, pos.below(), Direction.UP)) {
            return false;
        }

        if (groundState.is(Blocks.MAGMA_BLOCK) || groundState.is(Blocks.CACTUS) || groundState.is(Blocks.CAMPFIRE) || groundState.is(Blocks.SOUL_CAMPFIRE) || groundState.is(Blocks.FIRE) || groundState.is(Blocks.SOUL_FIRE)) {
            return false;
        }

        return true;
    }

    private static int countNearbyZombies(ServerLevel level, BlockPos center) {
        int radius = Math.max(1, Config.nightSpawnNearbyCheckRadius);
        return level.getEntitiesOfClass(Zombie.class, AABB.ofSize(Vec3.atCenterOf(center), radius * 2.0D, radius * 2.0D, radius * 2.0D), entity -> entity.isAlive()).size();
    }

    private static void debug(String message, Object... args) {
        if (Config.nightSpawnDebugLogs || Config.debugLogs) {
            LOGGER.info(message, args);
        }
    }

    public record NightSpawnAttemptResult(boolean success, int spawnedCount, BlockPos basePos, String reason) {
        public static NightSpawnAttemptResult failed(String reason) {
            return new NightSpawnAttemptResult(false, 0, null, reason);
        }

        public static NightSpawnAttemptResult success(int spawnedCount, BlockPos basePos, String reason) {
            return new NightSpawnAttemptResult(true, spawnedCount, basePos, reason);
        }
    }
}
