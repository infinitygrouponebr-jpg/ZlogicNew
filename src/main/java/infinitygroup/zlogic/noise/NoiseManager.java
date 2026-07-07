package infinitygroup.zlogic.noise;

import com.mojang.logging.LogUtils;
import infinitygroup.zlogic.Config;
import infinitygroup.zlogic.Zlogic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NoiseManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<net.minecraft.resources.ResourceKey<Level>, Deque<NoiseEvent>> NOISES = new HashMap<>();

    private NoiseManager() {
    }

    public static void emitNoise(ServerLevel level, BlockPos pos, double radius, int durationTicks, NoiseSourceType type) {
        emitNoise(level, pos, radius, durationTicks, type, null);
    }

    public static void emitNoise(ServerLevel level, BlockPos pos, double radius, int durationTicks, NoiseSourceType type, UUID sourceEntityId) {
        emitNoise(level, pos, radius, durationTicks, type, sourceEntityId, true);
    }

    public static void emitNoise(ServerLevel level, BlockPos pos, double radius, int durationTicks, NoiseSourceType type, UUID sourceEntityId, boolean attractsZombies) {
        if (level == null || pos == null || type == null) {
            return;
        }

        if (radius <= 0.0D || durationTicks <= 0 || Config.noiseMaxEventsPerLevel <= 0) {
            return;
        }

        int maxDuration = Math.max(1, Config.noiseMemoryTicks);
        int effectiveDuration = Math.min(durationTicks, maxDuration);
        if (effectiveDuration <= 0) {
            return;
        }

        NoiseEvent event = new NoiseEvent(level.dimension(), pos, radius, effectiveDuration, type, sourceEntityId, attractsZombies);
        Deque<NoiseEvent> events = NOISES.computeIfAbsent(level.dimension(), key -> new ArrayDeque<>());
        pruneExpiredEvents(events);

        while (events.size() >= Config.noiseMaxEventsPerLevel) {
            events.pollFirst();
        }

        events.addLast(event);
        debug(
            "Noise emitted: type={} pos={} radius={} duration={} dimension={} source={} attractsZombies={}",
            type,
            pos,
            radius,
            effectiveDuration,
            level.dimension().location(),
            sourceEntityId,
            attractsZombies
        );
    }

    public static List<NoiseEvent> getNearbyNoises(ServerLevel level, BlockPos pos, double radius) {
        if (level == null || pos == null || radius <= 0.0D) {
            return List.of();
        }

        Deque<NoiseEvent> events = NOISES.get(level.dimension());
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        double maxDistanceSq = radius * radius;
        List<NoiseEvent> result = new ArrayList<>();
        for (NoiseEvent event : events) {
            if (event.isExpired()) {
                continue;
            }

            if (!event.attractsZombies()) {
                continue;
            }

            if (!event.dimension().equals(level.dimension())) {
                continue;
            }

            if (distanceToCenterSqr(event.pos(), pos) <= maxDistanceSq) {
                result.add(event);
            }
        }

        return result;
    }

    public static NoiseEvent getNearestNoise(ServerLevel level, BlockPos pos, double radius, NoiseSourceType type) {
        if (level == null || pos == null || radius <= 0.0D || type == null) {
            return null;
        }

        Deque<NoiseEvent> events = NOISES.get(level.dimension());
        if (events == null || events.isEmpty()) {
            return null;
        }

        double maxDistanceSq = radius * radius;
        NoiseEvent best = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (NoiseEvent event : events) {
            if (event.isExpired() || event.type() != type || !event.dimension().equals(level.dimension())) {
                continue;
            }

            double distanceSq = distanceToCenterSqr(event.pos(), pos);
            if (distanceSq > maxDistanceSq) {
                continue;
            }

            if (best == null
                || distanceSq < bestDistanceSq
                || (distanceSq == bestDistanceSq && event.remainingTicks() > best.remainingTicks())
                || (distanceSq == bestDistanceSq && event.remainingTicks() == best.remainingTicks() && event.radius() > best.radius())) {
                best = event;
                bestDistanceSq = distanceSq;
            }
        }

        return best;
    }

    public static List<NoiseEvent> getNoises(ServerLevel level) {
        if (level == null) {
            return List.of();
        }

        Deque<NoiseEvent> events = NOISES.get(level.dimension());
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        List<NoiseEvent> result = new ArrayList<>(events.size());
        for (NoiseEvent event : events) {
            if (!event.isExpired() && event.dimension().equals(level.dimension())) {
                result.add(event);
            }
        }

        return result;
    }

    public static boolean hasNearbyNoise(ServerLevel level, BlockPos pos, double radius, NoiseSourceType type) {
        if (level == null || pos == null || radius <= 0.0D || type == null) {
            return false;
        }

        Deque<NoiseEvent> events = NOISES.get(level.dimension());
        if (events == null || events.isEmpty()) {
            return false;
        }

        double maxDistanceSq = radius * radius;
        for (NoiseEvent event : events) {
            if (event.isExpired() || event.type() != type || !event.dimension().equals(level.dimension())) {
                continue;
            }

            if (distanceToCenterSqr(event.pos(), pos) <= maxDistanceSq) {
                return true;
            }
        }

        return false;
    }

    public static void tick(ServerLevel level) {
        if (level == null) {
            return;
        }

        Deque<NoiseEvent> events = NOISES.get(level.dimension());
        if (events == null || events.isEmpty()) {
            return;
        }

        for (NoiseEvent event : events) {
            event.tick();
        }

        pruneExpiredEvents(events);
        events.removeIf(NoiseEvent::isExpired);
        if (events.isEmpty()) {
            NOISES.remove(level.dimension());
        }
    }

    public static void clearLevel(ServerLevel level) {
        if (level == null) {
            return;
        }

        NOISES.remove(level.dimension());
    }

    private static void pruneExpiredEvents(Deque<NoiseEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        events.removeIf(NoiseEvent::isExpired);
    }

    private static double distanceToCenterSqr(BlockPos a, BlockPos b) {
        double dx = (a.getX() + 0.5D) - (b.getX() + 0.5D);
        double dy = (a.getY() + 0.5D) - (b.getY() + 0.5D);
        double dz = (a.getZ() + 0.5D) - (b.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }

    private static void debug(String message, Object... args) {
        if (Config.debugLogs || Config.zombieNoiseDebugLogs) {
            LOGGER.info("[" + Zlogic.MODID + "] " + message, args);
        }
    }
}
