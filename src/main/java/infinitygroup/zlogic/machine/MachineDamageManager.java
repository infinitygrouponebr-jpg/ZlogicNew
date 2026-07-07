package infinitygroup.zlogic.machine;

import infinitygroup.zlogic.Config;
import infinitygroup.zlogic.compat.microtech.MicroTechNoiseHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class MachineDamageManager {
    private static final Map<MachineDamageKey, Integer> DAMAGE_BY_MACHINE = new HashMap<>();

    private MachineDamageManager() {
    }

    public static int damageMachine(ServerLevel level, BlockPos pos, int amount) {
        if (!isValidRequest(level, pos, amount)) {
            return 0;
        }

        if (!level.hasChunkAt(pos)) {
            return 0;
        }

        if (!isTrackedMachine(level, pos)) {
            clearDamage(level, pos);
            return 0;
        }

        MachineDamageKey key = new MachineDamageKey(level.dimension(), pos.asLong());
        int durability = getDurabilityFor(level.getBlockState(pos), pos);
        int newDamage = Math.min(durability, Math.max(0, DAMAGE_BY_MACHINE.getOrDefault(key, 0) + amount));
        DAMAGE_BY_MACHINE.put(key, newDamage);
        return newDamage;
    }

    public static int getDamage(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return 0;
        }

        MachineDamageKey key = new MachineDamageKey(level.dimension(), pos.asLong());
        Integer damage = DAMAGE_BY_MACHINE.get(key);
        if (damage == null) {
            return 0;
        }

        if (!level.hasChunkAt(pos) || !isTrackedMachine(level, pos)) {
            DAMAGE_BY_MACHINE.remove(key);
            return 0;
        }

        return damage;
    }

    public static int getDurabilityFor(BlockState state, BlockPos pos) {
        ResourceLocation blockId = state != null ? BuiltInRegistries.BLOCK.getKey(state.getBlock()) : null;
        if (blockId == null) {
            return Config.machineDurabilityDefault;
        }

        String id = blockId.toString();
        if (id.equals("microtech:energy_converter_t1") || id.equals("microtech:energy_converter")) {
            return Config.machineDurabilityEnergyConverterT1;
        }

        if (id.equals("microtech:evo_table")) {
            return Config.machineDurabilityEvoTable;
        }

        if (id.equals("microtech:battery_t1")) {
            return Config.machineDurabilityBatteryT1;
        }

        if (id.equals("microtech:battery_t2")) {
            return Config.machineDurabilityBatteryT2;
        }

        return Config.machineDurabilityDefault;
    }

    public static void clearDamage(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }

        DAMAGE_BY_MACHINE.remove(new MachineDamageKey(level.dimension(), pos.asLong()));
    }

    public static void clearLevel(ServerLevel level) {
        if (level == null) {
            return;
        }

        ResourceKey<Level> dimension = level.dimension();
        Iterator<Map.Entry<MachineDamageKey, Integer>> iterator = DAMAGE_BY_MACHINE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<MachineDamageKey, Integer> entry = iterator.next();
            if (entry.getKey().dimension().equals(dimension)) {
                iterator.remove();
            }
        }
    }

    private static boolean isTrackedMachine(ServerLevel level, BlockPos pos) {
        return MicroTechNoiseHandler.inspectMachine(level, pos).treatedAsMicroTechMachine();
    }

    private static boolean isValidRequest(ServerLevel level, BlockPos pos, int amount) {
        return level != null && pos != null && amount > 0;
    }

    private record MachineDamageKey(ResourceKey<Level> dimension, long packedPos) {
    }
}
