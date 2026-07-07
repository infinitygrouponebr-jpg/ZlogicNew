package infinitygroup.zlogic.zombie;

import infinitygroup.zlogic.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;

import java.util.Locale;

public final class ZombieSurvivalScalingHelper {
    public enum SurvivalScalingMode {
        WORLD_DAY,
        NEAREST_PLAYER,
        HYBRID,
        DISABLED;

        public static SurvivalScalingMode parse(String raw) {
            if (raw == null) {
                return WORLD_DAY;
            }

            try {
                return SurvivalScalingMode.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return WORLD_DAY;
            }
        }
    }

    public enum ThreatScalingMode {
        MULTI_FACTOR,
        LEGACY_DAY,
        DISABLED;

        public static ThreatScalingMode parse(String raw) {
            if (raw == null) {
                return MULTI_FACTOR;
            }

            try {
                return ThreatScalingMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return MULTI_FACTOR;
            }
        }
    }

    private ZombieSurvivalScalingHelper() {
    }

    public static int getWorldDay(ServerLevel level) {
        if (level == null) {
            return 0;
        }

        long dayTime = level.getDayTime();
        if (dayTime < 0L) {
            return 0;
        }

        return (int) (dayTime / 24000L);
    }

    public static int getScalingDays(int worldDay) {
        return Math.max(0, worldDay - Config.survivalScalingStartDay);
    }

    public static int calculateScalingLevel(int worldDay) {
        int scalingDays = getScalingDays(worldDay);
        double rawLevel = Math.floor(scalingDays * Math.max(0.0D, Config.survivalScalingLevelsPerDay));
        return clampLevel((int) rawLevel);
    }

    public static int clampLevel(int level) {
        return Math.max(0, Math.min(level, getEffectiveLevelCap()));
    }

    public static int getEffectiveLevelCap() {
        return Math.max(0, Math.min(Config.survivalScalingMaxLevel, Config.survivalScalingLevelCap));
    }

    public static double calculateMultiplierFromLevel(int level, double perLevel, double maxMultiplier) {
        if (perLevel <= 0.0D || maxMultiplier <= 1.0D) {
            return 1.0D;
        }

        double multiplier = 1.0D + Math.max(0, level) * perLevel;
        return Math.min(maxMultiplier, multiplier);
    }

    public static double calculateMultiplier(int worldDay, int startDay, double perDay, double maxMultiplier) {
        int scalingDays = Math.max(0, worldDay - startDay);
        int level = clampLevel((int) Math.floor(scalingDays * Math.max(0.0D, Config.survivalScalingLevelsPerDay)));
        return calculateMultiplierFromLevel(level, perDay, maxMultiplier);
    }

    public static int calculateWorldDayLevel(int worldDay) {
        if (!Config.threatWorldDayEnabled) {
            return 0;
        }

        int scalingDays = Math.max(0, worldDay - Config.threatWorldDayStartDay);
        long rawLevel = (long) Math.floor(scalingDays * Math.max(0.0D, Config.threatWorldDayLevelsPerDay));
        return Math.max(0, Math.min((int) rawLevel, Math.max(0, Config.threatWorldDayMaxLevel)));
    }

    public static int calculateDifficultyBonus(ServerLevel level) {
        if (level == null || !Config.threatDifficultyEnabled) {
            return 0;
        }

        Difficulty difficulty = level.getDifficulty();
        return switch (difficulty) {
            case PEACEFUL -> 0;
            case EASY -> Math.max(0, Config.threatDifficultyEasyBonus);
            case NORMAL -> Math.max(0, Config.threatDifficultyNormalBonus);
            case HARD -> Math.max(0, Config.threatDifficultyHardBonus);
        };
    }

    public static double calculateSpawnDistanceBlocks(ServerLevel level, Zombie zombie) {
        if (level == null || zombie == null) {
            return 0.0D;
        }

        BlockPos spawn = level.getSharedSpawnPos();
        double dx = zombie.getX() - (spawn.getX() + 0.5D);
        double dz = zombie.getZ() - (spawn.getZ() + 0.5D);
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static int calculateDistanceBonus(ServerLevel level, Zombie zombie) {
        if (level == null || zombie == null || !Config.threatDistanceEnabled) {
            return 0;
        }

        double distance = calculateSpawnDistanceBlocks(level, zombie);
        if (distance <= Config.threatDistanceStartBlocks) {
            return 0;
        }

        double extraDistance = distance - Config.threatDistanceStartBlocks;
        double perBlock = Math.max(0.0D, Config.threatDistanceLevelsPer1000Blocks) / 1000.0D;
        int bonus = (int) Math.floor(extraDistance * perBlock);
        return Math.max(0, Math.min(bonus, Math.max(0, Config.threatDistanceMaxBonus)));
    }

    public static int calculateRandomVariance(Zombie zombie) {
        if (zombie == null || !Config.threatRandomVarianceEnabled) {
            return 0;
        }

        int min = Math.max(0, Math.min(Config.threatRandomVarianceMin, Config.threatRandomVarianceMax));
        int max = Math.max(min, Math.max(Config.threatRandomVarianceMin, Config.threatRandomVarianceMax));
        if (max <= 0 && min <= 0) {
            return 0;
        }

        return zombie.getRandom().nextInt(max - min + 1) + min;
    }

    public static int calculateNearestPlayerBonus(ServerLevel level, Zombie zombie) {
        if (level == null || zombie == null || !Config.threatNearestPlayerEnabled) {
            return 0;
        }

        return 0;
    }

    public static int calculateThreatFinalLevel(int worldDayLevel, int difficultyBonus, int distanceBonus, int randomVariance, int nearestPlayerBonus) {
        int finalLevel = worldDayLevel + difficultyBonus + distanceBonus + randomVariance + nearestPlayerBonus;
        return Math.max(Math.max(0, Config.threatScalingMinimumLevel), Math.min(finalLevel, Math.max(0, Config.threatScalingFinalLevelCap)));
    }

    public static String buildThreatSource(boolean worldDayEnabled, boolean difficultyEnabled, boolean distanceEnabled, boolean varianceEnabled, boolean nearestPlayerEnabled) {
        StringBuilder builder = new StringBuilder();
        if (worldDayEnabled) {
            builder.append("WORLD_DAY");
        }
        if (difficultyEnabled) {
            appendSource(builder, "DIFFICULTY");
        }
        if (distanceEnabled) {
            appendSource(builder, "DISTANCE");
        }
        if (varianceEnabled) {
            appendSource(builder, "RANDOM");
        }
        if (nearestPlayerEnabled) {
            appendSource(builder, "NEAREST_PLAYER_PREPARED");
        }
        return builder.length() == 0 ? "DISABLED" : builder.toString();
    }

    private static void appendSource(StringBuilder builder, String part) {
        if (builder.length() > 0) {
            builder.append('+');
        }
        builder.append(part);
    }
}
