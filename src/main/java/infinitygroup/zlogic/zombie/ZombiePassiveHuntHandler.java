package infinitygroup.zlogic.zombie;

import com.mojang.logging.LogUtils;
import infinitygroup.zlogic.Config;
import infinitygroup.zlogic.Zlogic;
import infinitygroup.zlogic.machine.MachineAttackHandler;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class ZombiePassiveHuntHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FEEDING_TICKS_KEY = "zlogic_passive_hunt_feeding_ticks";
    private static final String FEEDING_FOOD_ITEM_KEY = "zlogic_passive_hunt_food_item";
    private static final Set<EntityType<?>> HUNTABLE_PASSIVE_TYPES = Set.of(
        EntityType.PIG,
        EntityType.COW,
        EntityType.SHEEP,
        EntityType.CHICKEN,
        EntityType.RABBIT,
        EntityType.MOOSHROOM,
        EntityType.GOAT
    );
    private static final Set<Item> MEAT_ITEMS = Set.of(
        Items.PORKCHOP,
        Items.COOKED_PORKCHOP,
        Items.BEEF,
        Items.COOKED_BEEF,
        Items.MUTTON,
        Items.COOKED_MUTTON,
        Items.CHICKEN,
        Items.COOKED_CHICKEN,
        Items.RABBIT,
        Items.COOKED_RABBIT
    );

    private ZombiePassiveHuntHandler() {
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!Config.enableZombiePassiveHunt) {
            return;
        }

        Entity entity = event.getEntity();
        if (!(entity instanceof Zombie zombie) || zombie.level().isClientSide() || !(zombie.level() instanceof ServerLevel level)) {
            return;
        }

        if (!ZombieEligibilityHelper.isEligibleForPassiveHunt(zombie)) {
            return;
        }

        if (processFeeding(level, zombie)) {
            return;
        }

        int interval = Math.max(1, Config.zombiePassiveHuntCheckIntervalTicks);
        if (zombie.tickCount % interval != 0) {
            return;
        }

        if (MachineAttackHandler.hasValidMachineTarget(level, zombie)) {
            debug(
                "Zombie passive hunt skipped by active machine target: zombie={} pos={} dimension={}",
                zombie.getType().toShortString(),
                zombie.blockPosition(),
                level.dimension().location()
            );
            return;
        }

        LivingEntity currentTarget = zombie.getTarget();
        if (currentTarget instanceof ServerPlayer playerTarget && isValidPlayerTarget(playerTarget)) {
            return;
        }

        if (currentTarget != null && currentTarget.isAlive() && !currentTarget.isRemoved()) {
            if (isValidPassiveHuntTarget(currentTarget)) {
                return;
            }
            return;
        }

        if (!zombie.getNavigation().isDone()) {
            return;
        }

        if (zombie.getRandom().nextDouble() >= Config.zombiePassiveHuntChance) {
            return;
        }

        Animal target = findPassiveTarget(level, zombie);
        if (target == null) {
            return;
        }

        zombie.setTarget(target);
        debug(
            "Zombie passive hunt target acquired: zombie={} target={} pos={} dimension={}",
            zombie.getType().toShortString(),
            target.getType().toShortString(),
            target.blockPosition(),
            level.dimension().location()
        );
    }

    public static void onLivingDrops(LivingDropsEvent event) {
        if (!Config.enableZombiePassiveHunt) {
            return;
        }

        LivingEntity victim = event.getEntity();
        if (!isValidPassiveHuntTarget(victim)) {
            return;
        }

        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Zombie zombie) || !ZombieEligibilityHelper.isEligibleForPassiveHunt(zombie)) {
            return;
        }

        ItemStack consumedMeat = consumeDroppedMeat(event.getDrops());
        if (consumedMeat.isEmpty()) {
            return;
        }

        startFeeding(zombie, consumedMeat);
        zombie.setTarget(null);

        debug(
            "Zombie passive hunt feeding started: zombie={} victim={} food={} pos={}",
            zombie.getType().toShortString(),
            victim.getType().toShortString(),
            BuiltInRegistries.ITEM.getKey(consumedMeat.getItem()),
            zombie.blockPosition()
        );
    }

    private static Animal findPassiveTarget(ServerLevel level, Zombie zombie) {
        double range = Math.max(1.0D, Config.zombiePassiveHuntRange);
        AABB searchBox = zombie.getBoundingBox().inflate(range);
        List<Animal> candidates = level.getEntitiesOfClass(
            Animal.class,
            searchBox,
            candidate -> isValidPassiveHuntTarget(candidate)
                && zombie.hasLineOfSight(candidate)
                && zombie.distanceToSqr(candidate) <= range * range
        );

        if (candidates.isEmpty()) {
            return null;
        }

        candidates.sort(Comparator.comparingDouble(zombie::distanceToSqr));
        return candidates.get(0);
    }

    private static boolean processFeeding(ServerLevel level, Zombie zombie) {
        int remainingTicks = zombie.getPersistentData().getInt(FEEDING_TICKS_KEY);
        if (remainingTicks <= 0) {
            clearFeeding(zombie);
            return false;
        }

        LivingEntity currentTarget = zombie.getTarget();
        if (currentTarget instanceof ServerPlayer playerTarget && isValidPlayerTarget(playerTarget)) {
            clearFeeding(zombie);
            return false;
        }

        if (currentTarget != null && currentTarget.isAlive() && !currentTarget.isRemoved() && !isValidPassiveHuntTarget(currentTarget)) {
            clearFeeding(zombie);
            return false;
        }

        zombie.getNavigation().stop();

        ItemStack displayFood = getStoredFoodStack(zombie);
        if (!displayFood.isEmpty() && remainingTicks % 8 == 0) {
            zombie.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, zombie.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.HOSTILE, 0.65F, 0.9F + zombie.getRandom().nextFloat() * 0.2F);
            level.sendParticles(
                new ItemParticleOption(ParticleTypes.ITEM, displayFood),
                zombie.getX(),
                zombie.getY() + zombie.getBbHeight() * 0.75D,
                zombie.getZ(),
                6,
                0.18D,
                0.12D,
                0.18D,
                0.01D
            );
        }

        if (remainingTicks <= 1) {
            if (Config.zombiePassiveHuntHealAmount > 0.0D) {
                zombie.heal((float) Config.zombiePassiveHuntHealAmount);
            }
            clearFeeding(zombie);
            debug(
                "Zombie passive hunt feeding finished: zombie={} heal={} pos={}",
                zombie.getType().toShortString(),
                Config.zombiePassiveHuntHealAmount,
                zombie.blockPosition()
            );
            return false;
        }

        zombie.getPersistentData().putInt(FEEDING_TICKS_KEY, remainingTicks - 1);
        return true;
    }

    private static ItemStack consumeDroppedMeat(Collection<ItemEntity> drops) {
        Iterator<ItemEntity> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemEntity itemEntity = iterator.next();
            ItemStack stack = itemEntity.getItem();
            if (!isMeatItem(stack)) {
                continue;
            }

            ItemStack consumed = stack.copy();
            consumed.setCount(1);

            if (stack.getCount() <= 1) {
                iterator.remove();
            } else {
                stack.shrink(1);
                itemEntity.setItem(stack);
            }

            return consumed;
        }

        return ItemStack.EMPTY;
    }

    private static boolean isValidPassiveHuntTarget(LivingEntity entity) {
        if (!(entity instanceof Animal animal)) {
            return false;
        }

        if (!animal.isAlive() || animal.isRemoved() || animal.isBaby()) {
            return false;
        }

        if (!HUNTABLE_PASSIVE_TYPES.contains(animal.getType())) {
            return false;
        }

        return !(animal instanceof TamableAnimal tamableAnimal) || !tamableAnimal.isTame();
    }

    private static boolean isMeatItem(ItemStack stack) {
        return !stack.isEmpty() && MEAT_ITEMS.contains(stack.getItem());
    }

    private static boolean isValidPlayerTarget(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator() && !player.isCreative();
    }

    private static void startFeeding(Zombie zombie, ItemStack foodStack) {
        if (zombie == null || foodStack.isEmpty()) {
            return;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(foodStack.getItem());
        if (itemId == null) {
            return;
        }

        zombie.getPersistentData().putInt(FEEDING_TICKS_KEY, Math.max(1, Config.zombiePassiveHuntEatDurationTicks));
        zombie.getPersistentData().putString(FEEDING_FOOD_ITEM_KEY, itemId.toString());
    }

    private static ItemStack getStoredFoodStack(Zombie zombie) {
        if (zombie == null || !zombie.getPersistentData().contains(FEEDING_FOOD_ITEM_KEY)) {
            return ItemStack.EMPTY;
        }

        ResourceLocation itemId = ResourceLocation.tryParse(zombie.getPersistentData().getString(FEEDING_FOOD_ITEM_KEY));
        if (itemId == null) {
            return ItemStack.EMPTY;
        }

        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.PORKCHOP);
        return new ItemStack(item);
    }

    private static void clearFeeding(Zombie zombie) {
        if (zombie == null) {
            return;
        }

        zombie.getPersistentData().remove(FEEDING_TICKS_KEY);
        zombie.getPersistentData().remove(FEEDING_FOOD_ITEM_KEY);
    }

    private static void debug(String message, Object... args) {
        if (Config.zombiePassiveHuntDebugLogs || Config.debugLogs) {
            LOGGER.info("[" + Zlogic.MODID + "] " + message, args);
        }
    }
}
