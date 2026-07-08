package infinitygroup.zlogic.zombie;

import com.mojang.logging.LogUtils;
import infinitygroup.zlogic.Config;
import infinitygroup.zlogic.Zlogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class ZombieTankHandler {
    public static final String TANK_ZOMBIE_KEY = "zlogic_tank_zombie";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SCALE_MODIFIER_ID = ResourceLocation.parse("zlogic:tank_scale");
    private static final ResourceLocation HEALTH_MODIFIER_ID = ResourceLocation.parse("zlogic:tank_health");
    private static final ResourceLocation DAMAGE_MODIFIER_ID = ResourceLocation.parse("zlogic:tank_damage");
    private static final ResourceLocation SPEED_MODIFIER_ID = ResourceLocation.parse("zlogic:tank_speed");
    private static final ResourceLocation KNOCKBACK_MODIFIER_ID = ResourceLocation.parse("zlogic:tank_knockback");
    private static final String NEXT_BLOCK_BREAK_TICK_KEY = "zlogic_tank_next_block_break_tick";
    private static final String FOCUS_BLOCK_X_KEY = "zlogic_tank_focus_block_x";
    private static final String FOCUS_BLOCK_Y_KEY = "zlogic_tank_focus_block_y";
    private static final String FOCUS_BLOCK_Z_KEY = "zlogic_tank_focus_block_z";
    private static final String FOCUS_BLOCK_UNTIL_TICK_KEY = "zlogic_tank_focus_block_until_tick";
    private static final Map<TankBlockKey, TankBlockDamageState> DAMAGE_BY_BLOCK = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long> LAST_PRUNE_TICK_BY_DIMENSION = new HashMap<>();

    private ZombieTankHandler() {
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Zombie zombie) || entity.level().isClientSide() || !(entity.level() instanceof ServerLevel level)) {
            return;
        }

        if (!ZombieFamilyHelper.isZombieFamily(zombie)) {
            return;
        }

        if (Config.disableBabyZombies && zombie.isBaby()) {
            zombie.setBaby(false);
        }

        if (event.loadedFromDisk()) {
            if (isTankZombie(zombie)) {
                if (!Config.enableTankZombieVariant) {
                    clearTankModifiers(zombie);
                    return;
                }
                syncTankModifiers(zombie, true);
            }
            return;
        }

        if (!Config.enableTankZombieVariant || !ZombieEligibilityHelper.isEligibleForZlogicSystems(zombie)) {
            return;
        }

        if (!isTankZombie(zombie) && zombie.getRandom().nextDouble() < Config.tankZombieSpawnChance) {
            zombie.getPersistentData().putBoolean(TANK_ZOMBIE_KEY, true);
            debug(
                "Tank zombie spawned: type={} pos={} dim={}",
                zombie.getType().toShortString(),
                zombie.blockPosition(),
                level.dimension().location()
            );
        }

        if (isTankZombie(zombie)) {
            syncTankModifiers(zombie, true);
        }
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Zombie zombie) || zombie.level().isClientSide() || !(zombie.level() instanceof ServerLevel level)) {
            return;
        }

        if (!isTankZombie(zombie)) {
            return;
        }

        if (!Config.enableTankZombieVariant) {
            clearTankModifiers(zombie);
            return;
        }

        if (zombie.tickCount % 40 == 0) {
            syncTankModifiers(zombie, false);
        }

        if (!Config.enableTankZombieVariant || !Config.tankZombieBlockBreakEnabled) {
            return;
        }

        if (!zombie.isAlive() || zombie.isRemoved() || zombie.isNoAi() || zombie.isInWaterOrBubble() || zombie.isInLava()) {
            return;
        }

        ServerPlayer target = getPlayerTarget(zombie);
        if (target == null) {
            return;
        }

        double maxTargetDistance = Math.max(1.0D, Config.tankZombieBlockBreakMaxTargetDistance);
        if (zombie.distanceToSqr(target) > maxTargetDistance * maxTargetDistance) {
            return;
        }

        long gameTime = level.getGameTime();
        long nextTick = zombie.getPersistentData().getLong(NEXT_BLOCK_BREAK_TICK_KEY);
        if (nextTick > gameTime) {
            return;
        }

        prune(level, gameTime);

        BlockPos candidatePos = getFocusedBreakBlock(level, zombie, target, gameTime);
        if (candidatePos == null) {
            candidatePos = findBreakCandidate(level, zombie, target);
            if (candidatePos != null) {
                storeFocusedBreakBlock(zombie, candidatePos, gameTime);
            }
        }

        if (candidatePos == null) {
            clearFocusedBreakBlock(zombie);
            scheduleNextBreakTick(zombie, gameTime, false);
            return;
        }

        if (Config.tankZombieNavigateToFocusedBlock) {
            double distanceSq = zombie.distanceToSqr(candidatePos.getX() + 0.5D, candidatePos.getY() + 0.5D, candidatePos.getZ() + 0.5D);
            if (distanceSq > 2.8D * 2.8D) {
                zombie.getNavigation().moveTo(candidatePos.getX() + 0.5D, candidatePos.getY(), candidatePos.getZ() + 0.5D, 0.95D);
                scheduleNextBreakTick(zombie, gameTime, false);
                return;
            }
        }

        if (getCooldownRemaining(level, candidatePos, gameTime) > 0) {
            scheduleNextBreakTick(zombie, gameTime, false);
            debug(
                "Tank zombie block break cooldown: pos={} remaining={} zombie={}",
                candidatePos,
                getCooldownRemaining(level, candidatePos, gameTime),
                zombie.blockPosition()
            );
            return;
        }

        BlockState state = level.getBlockState(candidatePos);
        int durability = getDurability(level, candidatePos, state);
        if (durability <= 0) {
            scheduleNextBreakTick(zombie, gameTime, false);
            return;
        }

        zombie.getLookControl().setLookAt(Vec3.atCenterOf(candidatePos));
        zombie.swing(InteractionHand.MAIN_HAND);

        int damage = addDamage(level, candidatePos, Math.max(1, Config.tankZombieBlockBreakDamagePerHit), durability, gameTime);
        playImpact(level, candidatePos, state, damage >= durability);

        if (damage >= durability) {
            if (Config.tankZombieBlockBreakDropBlocks) {
                level.destroyBlock(candidatePos, true, zombie, 512);
            } else {
                level.removeBlock(candidatePos, false);
            }
            clearDamage(level, candidatePos);
            clearFocusedBreakBlock(zombie);
            debug(
                "Tank zombie broke block: block={} pos={} durability={} zombie={}",
                state.getBlock().getName().getString(),
                candidatePos,
                durability,
                zombie.blockPosition()
            );
        } else {
            debug(
                "Tank zombie damaged block: block={} pos={} damage={}/{} zombie={}",
                state.getBlock().getName().getString(),
                candidatePos,
                damage,
                durability,
                zombie.blockPosition()
            );
        }

        scheduleNextBreakTick(zombie, gameTime, true);
    }

    public static boolean isTankZombie(Entity entity) {
        return entity instanceof Zombie zombie && zombie.getPersistentData().getBoolean(TANK_ZOMBIE_KEY);
    }

    private static void syncTankModifiers(Zombie zombie, boolean refillHealth) {
        if (zombie == null) {
            return;
        }

        boolean changed = false;
        changed |= syncModifier(
            zombie.getAttribute(Attributes.SCALE),
            SCALE_MODIFIER_ID,
            Math.max(0.0625D, Config.tankZombieScaleMultiplier) - 1.0D,
            AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
            true
        );
        changed |= syncModifier(
            zombie.getAttribute(Attributes.MAX_HEALTH),
            HEALTH_MODIFIER_ID,
            Math.max(1.0D, Config.tankZombieHealthMultiplier) - 1.0D,
            AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
            true
        );
        changed |= syncModifier(
            zombie.getAttribute(Attributes.ATTACK_DAMAGE),
            DAMAGE_MODIFIER_ID,
            Math.max(0.0D, Config.tankZombieAttackDamageBonus),
            AttributeModifier.Operation.ADD_VALUE,
            true
        );
        changed |= syncModifier(
            zombie.getAttribute(Attributes.MOVEMENT_SPEED),
            SPEED_MODIFIER_ID,
            Math.max(-0.95D, Math.min(4.0D, Config.tankZombieMovementSpeedMultiplier) - 1.0D),
            AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
            true
        );
        changed |= syncModifier(
            zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE),
            KNOCKBACK_MODIFIER_ID,
            Math.max(0.0D, Config.tankZombieKnockbackResistanceBonus),
            AttributeModifier.Operation.ADD_VALUE,
            true
        );

        if (refillHealth || changed) {
            AttributeInstance maxHealth = zombie.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null) {
                zombie.setHealth((float) Math.min(maxHealth.getValue(), Math.max(zombie.getHealth(), maxHealth.getValue())));
            }
        }
    }

    private static boolean syncModifier(
        AttributeInstance instance,
        ResourceLocation id,
        double amount,
        AttributeModifier.Operation operation,
        boolean shouldApply
    ) {
        if (instance == null) {
            return false;
        }

        AttributeModifier current = instance.getModifier(id);
        if (!shouldApply) {
            if (current != null) {
                instance.removeModifier(id);
                return true;
            }
            return false;
        }

        AttributeModifier expected = new AttributeModifier(id, amount, operation);
        if (expected.equals(current)) {
            return false;
        }

        if (current != null) {
            instance.removeModifier(id);
        }

        instance.addPermanentModifier(expected);
        return true;
    }

    private static void clearTankModifiers(Zombie zombie) {
        if (zombie == null) {
            return;
        }

        removeModifier(zombie.getAttribute(Attributes.SCALE), SCALE_MODIFIER_ID);
        removeModifier(zombie.getAttribute(Attributes.MAX_HEALTH), HEALTH_MODIFIER_ID);
        removeModifier(zombie.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_MODIFIER_ID);
        removeModifier(zombie.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER_ID);
        removeModifier(zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE), KNOCKBACK_MODIFIER_ID);
    }

    private static void removeModifier(AttributeInstance instance, ResourceLocation id) {
        if (instance != null && instance.getModifier(id) != null) {
            instance.removeModifier(id);
        }
    }

    private static ServerPlayer getPlayerTarget(Zombie zombie) {
        if (zombie == null) {
            return null;
        }

        if (!(zombie.getTarget() instanceof ServerPlayer player)) {
            return null;
        }

        if (!player.isAlive() || player.isRemoved() || player.isCreative() || player.isSpectator()) {
            return null;
        }

        return player;
    }

    private static BlockPos getFocusedBreakBlock(ServerLevel level, Zombie zombie, ServerPlayer target, long gameTime) {
        if (level == null || zombie == null || target == null) {
            return null;
        }

        long untilTick = zombie.getPersistentData().getLong(FOCUS_BLOCK_UNTIL_TICK_KEY);
        if (untilTick <= gameTime) {
            clearFocusedBreakBlock(zombie);
            return null;
        }

        if (!zombie.getPersistentData().contains(FOCUS_BLOCK_X_KEY)
            || !zombie.getPersistentData().contains(FOCUS_BLOCK_Y_KEY)
            || !zombie.getPersistentData().contains(FOCUS_BLOCK_Z_KEY)) {
            clearFocusedBreakBlock(zombie);
            return null;
        }

        BlockPos pos = new BlockPos(
            zombie.getPersistentData().getInt(FOCUS_BLOCK_X_KEY),
            zombie.getPersistentData().getInt(FOCUS_BLOCK_Y_KEY),
            zombie.getPersistentData().getInt(FOCUS_BLOCK_Z_KEY)
        );

        if (!isBreakableTankBlock(level, pos)) {
            clearFocusedBreakBlock(zombie);
            return null;
        }

        double driftRadius = Math.max(0.5D, Config.tankZombieBlockFocusTargetDrift);
        if (target.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > driftRadius * driftRadius
            && !isFocusedBlockStillBetween(zombie, target, pos)) {
            clearFocusedBreakBlock(zombie);
            return null;
        }

        return pos;
    }

    private static boolean isFocusedBlockStillBetween(Zombie zombie, ServerPlayer target, BlockPos pos) {
        if (zombie == null || target == null || pos == null) {
            return false;
        }

        Vec3 start = zombie.position().add(0.0D, zombie.getBbHeight() * 0.55D, 0.0D);
        Vec3 end = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        Vec3 direction = end.subtract(start);
        double totalDistance = direction.length();
        if (totalDistance <= 0.0001D) {
            return false;
        }

        Vec3 normalized = direction.scale(1.0D / totalDistance);
        double maxDistance = Math.min(totalDistance, Math.max(0.8D, Config.tankZombieBlockBreakMaxBlockDistance));
        return pathIntersectsBreakableBlock(start, normalized, maxDistance, pos);
    }

    private static void storeFocusedBreakBlock(Zombie zombie, BlockPos pos, long gameTime) {
        if (zombie == null || pos == null) {
            return;
        }

        zombie.getPersistentData().putInt(FOCUS_BLOCK_X_KEY, pos.getX());
        zombie.getPersistentData().putInt(FOCUS_BLOCK_Y_KEY, pos.getY());
        zombie.getPersistentData().putInt(FOCUS_BLOCK_Z_KEY, pos.getZ());
        zombie.getPersistentData().putLong(FOCUS_BLOCK_UNTIL_TICK_KEY, gameTime + Math.max(1, Config.tankZombieBlockFocusTicks));
    }

    private static void clearFocusedBreakBlock(Zombie zombie) {
        if (zombie == null) {
            return;
        }

        zombie.getPersistentData().remove(FOCUS_BLOCK_X_KEY);
        zombie.getPersistentData().remove(FOCUS_BLOCK_Y_KEY);
        zombie.getPersistentData().remove(FOCUS_BLOCK_Z_KEY);
        zombie.getPersistentData().remove(FOCUS_BLOCK_UNTIL_TICK_KEY);
    }

    private static BlockPos findBreakCandidate(ServerLevel level, Zombie zombie, ServerPlayer target) {
        Vec3 start = zombie.position().add(0.0D, zombie.getBbHeight() * 0.55D, 0.0D);
        Vec3 end = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        Vec3 direction = end.subtract(start);
        double totalDistance = direction.length();
        if (totalDistance <= 0.0001D) {
            return null;
        }

        Vec3 normalized = direction.scale(1.0D / totalDistance);
        double maxDistance = Math.min(totalDistance, Math.max(0.8D, Config.tankZombieBlockBreakMaxBlockDistance));
        return findBreakableBlockAlongPath(level, start, normalized, maxDistance);
    }

    private static BlockPos findBreakableBlockAlongPath(ServerLevel level, Vec3 start, Vec3 normalized, double maxDistance) {
        if (level == null || start == null || normalized == null || maxDistance <= 0.0D) {
            return null;
        }

        Vec3 perpendicular = new Vec3(-normalized.z, 0.0D, normalized.x);
        if (perpendicular.lengthSqr() > 0.0001D) {
            perpendicular = perpendicular.normalize();
        } else {
            perpendicular = Vec3.ZERO;
        }

        double[] lateralOffsets = {0.0D, 0.45D, -0.45D};
        double[] verticalOffsets = {-0.45D, 0.10D, 0.85D};
        for (double step = 0.6D; step <= maxDistance; step += 0.2D) {
            Vec3 center = start.add(normalized.scale(step));
            for (double lateral : lateralOffsets) {
                Vec3 sample = center.add(perpendicular.scale(lateral));
                for (double vertical : verticalOffsets) {
                    BlockPos pos = BlockPos.containing(sample.x, sample.y + vertical, sample.z);
                    if (isBreakableTankBlock(level, pos)) {
                        return pos;
                    }
                }
            }
        }

        return null;
    }

    private static boolean pathIntersectsBreakableBlock(Vec3 start, Vec3 normalized, double maxDistance, BlockPos expectedPos) {
        if (start == null || normalized == null || expectedPos == null || maxDistance <= 0.0D) {
            return false;
        }

        Vec3 perpendicular = new Vec3(-normalized.z, 0.0D, normalized.x);
        if (perpendicular.lengthSqr() > 0.0001D) {
            perpendicular = perpendicular.normalize();
        } else {
            perpendicular = Vec3.ZERO;
        }

        double[] lateralOffsets = {0.0D, 0.45D, -0.45D};
        double[] verticalOffsets = {-0.45D, 0.10D, 0.85D};
        for (double step = 0.6D; step <= maxDistance; step += 0.2D) {
            Vec3 center = start.add(normalized.scale(step));
            for (double lateral : lateralOffsets) {
                Vec3 sample = center.add(perpendicular.scale(lateral));
                for (double vertical : verticalOffsets) {
                    BlockPos sampledPos = BlockPos.containing(sample.x, sample.y + vertical, sample.z);
                    if (sampledPos.equals(expectedPos)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean isBreakableTankBlock(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) {
            return false;
        }

        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            return false;
        }

        ResourceLocation blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null) {
            return false;
        }

        if (Config.tankZombieBlockBreakRespectDenylist && Config.tankZombieBlockBreakDenylist.contains(blockId.toString())) {
            return false;
        }

        return true;
    }

    private static int getDurability(ServerLevel level, BlockPos pos, BlockState state) {
        if (level == null || pos == null || state == null) {
            return 0;
        }

        double destroySpeed = Math.max(0.0D, state.getDestroySpeed(level, pos));
        double explosionResistance = Math.max(0.0D, state.getBlock().getExplosionResistance());
        double durability = Math.max(1.0D, Config.tankZombieBlockBreakBaseDurability)
            + destroySpeed * Math.max(0.0D, Config.tankZombieBlockBreakDurabilityPerHardness)
            + explosionResistance * Math.max(0.0D, Config.tankZombieBlockBreakDurabilityPerResistance);
        return Mth.clamp(Mth.ceil(durability), 1, Math.max(1, Config.tankZombieBlockBreakMaxDurability));
    }

    private static void playImpact(ServerLevel level, BlockPos pos, BlockState state, boolean broken) {
        if (level == null || pos == null || state == null) {
            return;
        }

        level.sendParticles(
            new BlockParticleOption(ParticleTypes.BLOCK, state),
            pos.getX() + 0.5D,
            pos.getY() + 0.5D,
            pos.getZ() + 0.5D,
            broken ? 16 : 8,
            broken ? 0.30D : 0.18D,
            broken ? 0.30D : 0.18D,
            broken ? 0.30D : 0.18D,
            0.01D
        );
        level.playSound(null, pos, state.getSoundType().getHitSound(), SoundSource.HOSTILE, 0.9F, broken ? 0.85F : 0.70F);
    }

    private static void scheduleNextBreakTick(Zombie zombie, long gameTime, boolean attempted) {
        if (zombie == null) {
            return;
        }

        int interval = attempted
            ? Math.max(1, Config.tankZombieBlockBreakZombieCooldownTicks)
            : Math.max(1, Config.tankZombieBlockBreakCheckIntervalTicks);
        zombie.getPersistentData().putLong(NEXT_BLOCK_BREAK_TICK_KEY, gameTime + interval);
    }

    private static int getCooldownRemaining(ServerLevel level, BlockPos pos, long gameTime) {
        TankBlockDamageState state = getState(level, pos, true);
        if (state == null) {
            return 0;
        }

        long remaining = Math.max(1, Config.tankZombieBlockBreakBlockCooldownTicks) - Math.max(0L, gameTime - state.lastHitTick());
        return (int) Math.max(0L, remaining);
    }

    private static int addDamage(ServerLevel level, BlockPos pos, int amount, int maxDamage, long gameTime) {
        if (level == null || pos == null || amount <= 0) {
            return 0;
        }

        prune(level, gameTime);
        TankBlockKey key = new TankBlockKey(level.dimension(), pos.asLong());
        TankBlockDamageState current = DAMAGE_BY_BLOCK.get(key);
        int damage = current == null ? 0 : current.damage();
        int newDamage = Math.min(Math.max(0, damage + amount), Math.max(1, maxDamage));
        DAMAGE_BY_BLOCK.put(key, new TankBlockDamageState(newDamage, gameTime));
        return newDamage;
    }

    private static void clearDamage(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }

        DAMAGE_BY_BLOCK.remove(new TankBlockKey(level.dimension(), pos.asLong()));
    }

    private static void prune(ServerLevel level, long gameTime) {
        if (level == null) {
            return;
        }

        ResourceKey<Level> dimension = level.dimension();
        long lastPrune = LAST_PRUNE_TICK_BY_DIMENSION.getOrDefault(dimension, Long.MIN_VALUE);
        int memoryTicks = Math.max(1, Config.tankZombieBlockBreakDamageMemoryTicks);
        if (lastPrune != Long.MIN_VALUE && gameTime - lastPrune < memoryTicks) {
            return;
        }

        LAST_PRUNE_TICK_BY_DIMENSION.put(dimension, gameTime);
        Iterator<Map.Entry<TankBlockKey, TankBlockDamageState>> iterator = DAMAGE_BY_BLOCK.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TankBlockKey, TankBlockDamageState> entry = iterator.next();
            if (!entry.getKey().dimension().equals(dimension)) {
                continue;
            }

            if (gameTime - entry.getValue().lastHitTick() >= memoryTicks) {
                iterator.remove();
            }
        }
    }

    private static TankBlockDamageState getState(ServerLevel level, BlockPos pos, boolean pruneIfExpired) {
        if (level == null || pos == null) {
            return null;
        }

        if (pruneIfExpired) {
            prune(level, level.getGameTime());
        }

        TankBlockKey key = new TankBlockKey(level.dimension(), pos.asLong());
        TankBlockDamageState state = DAMAGE_BY_BLOCK.get(key);
        if (state == null) {
            return null;
        }

        int memoryTicks = Math.max(1, Config.tankZombieBlockBreakDamageMemoryTicks);
        if (level.getGameTime() - state.lastHitTick() >= memoryTicks) {
            DAMAGE_BY_BLOCK.remove(key);
            return null;
        }

        return state;
    }

    private static void debug(String message, Object... args) {
        if (Config.tankZombieDebugLogs || Config.debugLogs) {
            LOGGER.info("[" + Zlogic.MODID + "] " + message, args);
        }
    }

    private record TankBlockKey(ResourceKey<Level> dimension, long packedPos) {
    }

    private record TankBlockDamageState(int damage, long lastHitTick) {
    }
}
