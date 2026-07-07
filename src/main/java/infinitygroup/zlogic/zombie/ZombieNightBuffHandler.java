package infinitygroup.zlogic.zombie;

import infinitygroup.zlogic.Config;
import infinitygroup.zlogic.Zlogic;
import infinitygroup.zlogic.perf.PerformanceTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public final class ZombieNightBuffHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHECK_INTERVAL_TICKS = 20;

    private static final ResourceLocation SPEED_MODIFIER_ID = ResourceLocation.parse("zlogic:night_speed");
    public static final ResourceLocation DAMAGE_MODIFIER_ID = ResourceLocation.parse("zlogic:night_damage");
    private static final ResourceLocation FOLLOW_RANGE_MODIFIER_ID = ResourceLocation.parse("zlogic:night_follow_range");
    private static final ResourceLocation ARMOR_MODIFIER_ID = ResourceLocation.parse("zlogic:night_armor");

    private ZombieNightBuffHandler() {
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Zombie zombie) || zombie.level().isClientSide()) {
            return;
        }

        if (!ZombieFamilyHelper.isZombieFamily(zombie)) {
            return;
        }

        PerformanceTracker.recordEntityProcessed();
        PerformanceTracker.recordZombieProcessed();

        if (zombie.tickCount % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        boolean shouldBuff = Config.enableNightZombieBuffs && entity.level().isNight() && ZombieEligibilityHelper.isEligibleForNightBuffs(zombie);
        boolean changed = false;

        changed |= syncModifier(zombie.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER_ID, Config.nightSpeedMultiplier, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, shouldBuff);
        changed |= syncModifier(zombie.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_MODIFIER_ID, Config.nightAttackDamageBonus, AttributeModifier.Operation.ADD_VALUE, shouldBuff);
        changed |= syncModifier(zombie.getAttribute(Attributes.FOLLOW_RANGE), FOLLOW_RANGE_MODIFIER_ID, Config.nightFollowRangeBonus, AttributeModifier.Operation.ADD_VALUE, shouldBuff);
        changed |= syncModifier(zombie.getAttribute(Attributes.ARMOR), ARMOR_MODIFIER_ID, Config.nightArmorBonus, AttributeModifier.Operation.ADD_VALUE, shouldBuff);

        if (Config.debugLogs && changed) {
            LOGGER.info("Zlogic night buff sync for {} at {} (night={}, enabled={})", entity.getType().toShortString(), entity.blockPosition(), shouldBuff, Config.enableNightZombieBuffs);
        }
    }
    private static boolean syncModifier(AttributeInstance instance, ResourceLocation id, double amount, AttributeModifier.Operation operation, boolean shouldApply) {
        if (instance == null) {
            return false;
        }

        AttributeModifier current = instance.getModifier(id);
        if (!shouldApply || amount <= 0.0D) {
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
}
