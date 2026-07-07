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

public final class DayZombieSpawnController {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static long serverTickCounter;

    private DayZombieSpawnController() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!Config.enableDayZombieSpawns) {
            return;
        }

        MinecraftServer server = event.getServer();
        if (server.getTickCount() == 0) {
            return;
        }

        int interval = Math.max(1, Config.daySpawnIntervalTicks);
        if (serverTickCounter++ % interval != 0) {
            return;
        }

        List<ServerPlayer> eligiblePlayers = collectEligiblePlayers(server);
        if (eligiblePlayers.isEmpty()) {
            debug("Day spawn skipped: no eligible players online.");
            return;
        }

        ServerPlayer player = eligiblePlayers.get(ThreadLocalRandom.current().nextInt(eligiblePlayers.size()));
        ServerLevel level = player.serverLevel();
        RandomSource random = level.getRandom();

        debug(
            "Day spawn attempt: player={}, dimension={}, tick={}",
            player.getGameProfile().getName(),
            level.dimension().location(),
            server.getTickCount()
        );

        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            debug("Day spawn skipped: level {} is in Peaceful.", level.dimension().location());
            return;
        }

        if (!isAllowedDimension(level)) {
            debug("Day spawn skipped: dimension {} is not allowed.", level.dimension().location());
            return;
        }

        if (!level.isDay()) {
            debug("Day spawn skipped: level {} is not in daytime.", level.dimension().location());
            return;
        }

        if (Config.daySpawnRespectDoMobSpawning && !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
            debug("Day spawn skipped: doMobSpawning gamerule is disabled.");
            return;
        }

        if (random.nextDouble() > Config.daySpawnChance) {
            debug("Day spawn skipped: chance roll failed (chance={}).", Config.daySpawnChance);
            return;
        }

        int groupSize = clampGroupSize(Config.daySpawnMinGroupSize, Config.daySpawnMaxGroupSize, random);
        if (groupSize <= 0) {
            debug("Day spawn skipped: group size resolved to 0.");
            return;
        }

        BlockPos baseSpawnPos = findBaseSpawnPosition(level, player, random);
        if (baseSpawnPos == null) {
            debug("Day spawn skipped: no valid spawn position found near {}.", player.blockPosition());
            return;
        }

        int nearbyCount = countNearbyZombies(level, baseSpawnPos);
        if (nearbyCount >= Config.daySpawnMaxNearbyZombies) {
            debug(
                "Day spawn skipped: nearby zombie cap reached (nearby={}, cap={}, pos={}).",
                nearbyCount,
                Config.daySpawnMaxNearbyZombies,
                baseSpawnPos
            );
            return;
        }

        int allowedSpawns = Math.min(groupSize, Config.daySpawnMaxNearbyZombies - nearbyCount);
        int created = 0;

        debug("Day spawn base position resolved at {} with group size {}.", baseSpawnPos, allowedSpawns);

        for (int i = 0; i < allowedSpawns; i++) {
            if (countNearbyZombies(level, baseSpawnPos) >= Config.daySpawnMaxNearbyZombies) {
                break;
            }

            BlockPos spawnPos = findSpawnPositionAround(level, player, baseSpawnPos, random);
            if (spawnPos == null) {
                continue;
            }

            if (!level.hasChunkAt(spawnPos)) {
                continue;
            }

            if (countNearbyZombies(level, spawnPos) >= Config.daySpawnMaxNearbyZombies) {
                continue;
            }

            Zombie zombie = EntityType.ZOMBIE.create(level);
            if (zombie == null) {
                continue;
            }

            ZombieMarkingHelper.markDaySpawned(zombie);
            zombie.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null);

            if (level.addFreshEntity(zombie)) {
                created++;
            }
        }

        debug("Day spawn finished: created {} zombie(s) near {} in {}.", created, player.getGameProfile().getName(), level.dimension().location());
    }

    private static List<ServerPlayer> collectEligiblePlayers(MinecraftServer server) {
        List<ServerPlayer> eligiblePlayers = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
                continue;
            }

            ServerLevel level = player.serverLevel();
            if (!isAllowedDimension(level) || !level.isDay()) {
                continue;
            }

            if (Config.daySpawnRespectDoMobSpawning && !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
                continue;
            }

            eligiblePlayers.add(player);
        }

        return eligiblePlayers;
    }

    private static boolean isAllowedDimension(ServerLevel level) {
        String dimensionId = level.dimension().location().toString();
        return Config.daySpawnAllowedDimensions.isEmpty() || Config.daySpawnAllowedDimensions.contains(dimensionId);
    }

    private static int clampGroupSize(int min, int max, RandomSource random) {
        int lower = Math.max(1, Math.min(min, max));
        int upper = Math.max(lower, Math.max(min, max));
        return lower + random.nextInt(upper - lower + 1);
    }

    private static BlockPos findBaseSpawnPosition(ServerLevel level, ServerPlayer player, RandomSource random) {
        int attempts = 12;
        int minRadius = Math.max(1, Math.min(Config.daySpawnRadiusMin, Config.daySpawnRadiusMax));
        int maxRadius = Math.max(minRadius, Math.max(Config.daySpawnRadiusMin, Config.daySpawnRadiusMax));

        for (int i = 0; i < attempts; i++) {
            double angle = random.nextDouble() * (Math.PI * 2.0D);
            double distance = minRadius + random.nextDouble() * (maxRadius - minRadius);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);

            BlockPos surfacePos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1, z);
            if (isValidSpawnPos(level, surfacePos)) {
                return surfacePos;
            }

            if (Config.daySpawnRequireSkyAccess) {
                continue;
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

            if (!Config.daySpawnRequireSkyAccess) {
                BlockPos fallback = findNearbyCavePosition(level, player, candidate.getX(), candidate.getZ());
                if (fallback != null) {
                    return fallback;
                }
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

        if (Config.daySpawnRequireSkyAccess && !level.canSeeSky(pos)) {
            return false;
        }

        if (Config.daySpawnAllowInDarkAreasOnly && level.getMaxLocalRawBrightness(pos) > Config.daySpawnMaxLightLevel) {
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
        int radius = Math.max(1, Config.daySpawnNearbyCheckRadius);
        return level.getEntitiesOfClass(Zombie.class, AABB.ofSize(Vec3.atCenterOf(center), radius * 2.0D, radius * 2.0D, radius * 2.0D), entity -> entity.isAlive()).size();
    }

    private static void debug(String message, Object... args) {
        if (Config.debugLogs) {
            LOGGER.info(message, args);
        }
    }
}
