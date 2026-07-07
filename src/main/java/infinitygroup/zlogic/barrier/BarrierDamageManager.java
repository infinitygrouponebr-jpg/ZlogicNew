package infinitygroup.zlogic.barrier;

import infinitygroup.zlogic.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class BarrierDamageManager {
    private static final Map<BarrierKey, BarrierDamageState> DAMAGE_BY_BARRIER = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long> LAST_PRUNE_TICK_BY_DIMENSION = new HashMap<>();

    private BarrierDamageManager() {
    }

    public static int getDamage(ServerLevel level, BlockPos pos) {
        BarrierDamageState state = getState(level, pos, false);
        return state == null ? 0 : state.damage();
    }

    public static int getCooldownRemaining(ServerLevel level, BlockPos pos, long gameTime) {
        BarrierDamageState state = getState(level, pos, true);
        if (state == null) {
            return 0;
        }

        int cooldown = Math.max(1, Config.barrierBreakBlockCooldownTicks);
        long remaining = cooldown - Math.max(0L, gameTime - state.lastHitTick());
        return (int) Math.max(0L, remaining);
    }

    public static int addDamage(ServerLevel level, BlockPos pos, int amount, long gameTime) {
        return addDamage(level, pos, amount, Integer.MAX_VALUE, gameTime);
    }

    public static int addDamage(ServerLevel level, BlockPos pos, int amount, int maxDamage, long gameTime) {
        if (level == null || pos == null || amount <= 0) {
            return 0;
        }

        prune(level, gameTime);
        BarrierKey key = new BarrierKey(level.dimension(), pos.asLong());
        BarrierDamageState current = DAMAGE_BY_BARRIER.get(key);
        int newDamage = Math.min(Math.max(0, (current == null ? 0 : current.damage()) + amount), Math.max(0, maxDamage));
        DAMAGE_BY_BARRIER.put(key, new BarrierDamageState(newDamage, gameTime));
        return newDamage;
    }

    public static boolean isOnCooldown(ServerLevel level, BlockPos pos, long gameTime) {
        return getCooldownRemaining(level, pos, gameTime) > 0;
    }

    public static void clearDamage(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }

        DAMAGE_BY_BARRIER.remove(new BarrierKey(level.dimension(), pos.asLong()));
    }

    public static void clearLevel(ServerLevel level) {
        if (level == null) {
            return;
        }

        ResourceKey<Level> dimension = level.dimension();
        Iterator<Map.Entry<BarrierKey, BarrierDamageState>> iterator = DAMAGE_BY_BARRIER.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getKey().dimension().equals(dimension)) {
                iterator.remove();
            }
        }

        LAST_PRUNE_TICK_BY_DIMENSION.remove(dimension);
    }

    public static void prune(ServerLevel level, long gameTime) {
        if (level == null) {
            return;
        }

        ResourceKey<Level> dimension = level.dimension();
        long lastPrune = LAST_PRUNE_TICK_BY_DIMENSION.getOrDefault(dimension, Long.MIN_VALUE);
        int memoryTicks = Math.max(1, Config.barrierBreakDamageMemoryTicks);
        if (lastPrune != Long.MIN_VALUE && gameTime - lastPrune < memoryTicks) {
            return;
        }

        LAST_PRUNE_TICK_BY_DIMENSION.put(dimension, gameTime);
        Iterator<Map.Entry<BarrierKey, BarrierDamageState>> iterator = DAMAGE_BY_BARRIER.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BarrierKey, BarrierDamageState> entry = iterator.next();
            if (!entry.getKey().dimension().equals(dimension)) {
                continue;
            }

            if (gameTime - entry.getValue().lastHitTick() >= memoryTicks) {
                iterator.remove();
            }
        }
    }

    private static BarrierDamageState getState(ServerLevel level, BlockPos pos, boolean pruneIfExpired) {
        if (level == null || pos == null) {
            return null;
        }

        if (pruneIfExpired) {
            prune(level, level.getGameTime());
        }

        BarrierKey key = new BarrierKey(level.dimension(), pos.asLong());
        BarrierDamageState state = DAMAGE_BY_BARRIER.get(key);
        if (state == null) {
            return null;
        }

        int memoryTicks = Math.max(1, Config.barrierBreakDamageMemoryTicks);
        if (level.getGameTime() - state.lastHitTick() >= memoryTicks) {
            DAMAGE_BY_BARRIER.remove(key);
            return null;
        }

        return state;
    }

    private record BarrierKey(ResourceKey<Level> dimension, long packedPos) {
    }

    private record BarrierDamageState(int damage, long lastHitTick) {
    }
}
