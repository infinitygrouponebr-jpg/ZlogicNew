package infinitygroup.zlogic.barrier;

import com.mojang.logging.LogUtils;
import infinitygroup.zlogic.Config;
import infinitygroup.zlogic.Zlogic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.slf4j.Logger;

public final class ZombieBarrierBreakHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ZombieBarrierBreakHandler() {
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!Config.enableZombieBarrierBreak) {
            return;
        }

        Entity entity = event.getEntity();
        if (!(entity instanceof Zombie zombie) || zombie.level().isClientSide() || !(zombie.level() instanceof ServerLevel level)) {
            return;
        }

        if (!ZombieBarrierBreakHelper.isEligibleZombie(zombie)) {
            return;
        }

        if (level.getDifficulty() == Difficulty.PEACEFUL || zombie.isNoAi() || !zombie.isAlive() || zombie.isRemoved() || zombie.isInWaterOrBubble() || zombie.isInLava()) {
            return;
        }

        long gameTime = level.getGameTime();
        BarrierDamageManager.prune(level, gameTime);

        long nextTick = ZombieBarrierBreakHelper.getNextTick(zombie);
        if (nextTick > gameTime) {
            return;
        }

        ZombieBarrierBreakHelper.BarrierBreakAssessment assessment = ZombieBarrierBreakHelper.inspect(level, zombie);
        ZombieBarrierBreakHelper.recordState(zombie, assessment, gameTime);
        ZombieBarrierBreakHelper.updateBarrierFocus(zombie, assessment, gameTime);
        if (!assessment.active()) {
            if (Config.barrierBreakNavigateToBarrier && assessment.supported() && assessment.normalizedPos() != null && assessment.hasTarget()) {
                zombie.getNavigation().moveTo(
                    assessment.normalizedPos().getX() + 0.5D,
                    assessment.normalizedPos().getY(),
                    assessment.normalizedPos().getZ() + 0.5D,
                    1.0D
                );
            }
            ZombieBarrierBreakHelper.scheduleNextTick(zombie, gameTime, false);
            if (Config.barrierBreakVerboseDebug || Config.barrierBreakDebugLogs || Config.debugLogs) {
                debug(
                    "Zombie barrier break skipped: zombie={} pos={} reason={} group={} cooldown={} target={}",
                    zombie.getType().toShortString(),
                    zombie.blockPosition(),
                    assessment.reason(),
                    assessment.groupSize(),
                    assessment.cooldownRemaining(),
                    assessment.targetType()
                );
            }
            return;
        }

        BlockPos barrierPos = assessment.normalizedPos();
        ZombieBarrierBreakHelper.BarrierBlockInspection blockInspection = ZombieBarrierBreakHelper.inspectBlock(level, barrierPos);
        if (!blockInspection.supported()) {
            ZombieBarrierBreakHelper.scheduleNextTick(zombie, gameTime, false);
            ZombieBarrierBreakHelper.recordState(zombie, assessment, gameTime);
            return;
        }

        int damagePerHit = Math.max(1, Config.barrierBreakDamagePerHit);
        BlockState barrierState = level.getBlockState(barrierPos);
        int newDamage = BarrierDamageManager.addDamage(level, barrierPos, damagePerHit, Math.max(1, blockInspection.durability()), gameTime);
        ZombieBarrierBreakHelper.recordState(
            zombie,
            new ZombieBarrierBreakHelper.BarrierBreakAssessment(
                assessment.enabled(),
                assessment.eligible(),
                assessment.active(),
                assessment.reason(),
                assessment.targetType(),
                assessment.barrierKind(),
                assessment.barrierPos(),
                assessment.normalizedPos(),
                assessment.blockId(),
                assessment.groupSize(),
                assessment.nearbyZombieCount(),
                assessment.durability(),
                newDamage,
                assessment.cooldownRemaining(),
                assessment.hasTarget(),
                assessment.targetAllowed(),
                assessment.denylisted(),
                assessment.tintedBlocked(),
                assessment.supported(),
                assessment.eligibleZombieCount(),
                assessment.attackSideZombieCount(),
                assessment.zombieY(),
                assessment.targetY(),
                assessment.yDifference(),
                assessment.horizontalDistance(),
                assessment.direction()
            ),
            gameTime
        );

        zombie.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        zombie.getLookControl().setLookAt(Vec3.atCenterOf(barrierPos));

        if (newDamage >= blockInspection.durability() && Config.barrierBreakActuallyBreakBlocks) {
            breakBarrier(level, barrierPos, blockInspection);
            BarrierDamageManager.clearDamage(level, barrierPos);
            BarrierBreakVisualHelper.playBreak(level, barrierPos, barrierState, blockInspection.barrierKind());

            if (Config.barrierBreakDebugLogs || Config.barrierBreakVerboseDebug || Config.debugLogs) {
                debug(
                    "Zombie barrier broken: zombie={} pos={} type={} damage={}/{} group={}",
                    zombie.getType().toShortString(),
                    barrierPos,
                    blockInspection.barrierKind(),
                    newDamage,
                    blockInspection.durability(),
                    assessment.groupSize()
                );
            }
        } else {
            BarrierBreakVisualHelper.playImpact(level, barrierPos, barrierState, blockInspection.barrierKind());
            if (Config.barrierBreakDebugLogs || Config.barrierBreakVerboseDebug || Config.debugLogs) {
                debug(
                    "Zombie barrier damaged: zombie={} pos={} type={} damage={}/{} group={}",
                    zombie.getType().toShortString(),
                    barrierPos,
                    blockInspection.barrierKind(),
                    newDamage,
                    blockInspection.durability(),
                    assessment.groupSize()
                );
            }
        }

        ZombieBarrierBreakHelper.scheduleNextTick(zombie, gameTime, true);
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BarrierDamageManager.clearLevel(level);
        }
    }

    private static void breakBarrier(ServerLevel level, BlockPos barrierPos, ZombieBarrierBreakHelper.BarrierBlockInspection inspection) {
        if (level == null || barrierPos == null || inspection == null) {
            return;
        }

        if (inspection.barrierKind() == ZombieBarrierBreakHelper.BarrierKind.WOODEN_DOOR) {
            BlockPos lower = inspection.normalizedPos();
            BlockPos upper = lower.above();

            if (Config.barrierBreakDropBlocks) {
                level.destroyBlock(lower, true, null, 512);
            } else {
                level.removeBlock(lower, false);
            }

            BlockState upperState = level.getBlockState(upper);
            if (upperState.getBlock() instanceof DoorBlock && upperState.hasProperty(DoorBlock.HALF) && upperState.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
                level.removeBlock(upper, false);
            }
            return;
        }

        if (Config.barrierBreakDropBlocks) {
            level.destroyBlock(inspection.normalizedPos(), true, null, 512);
        } else {
            level.removeBlock(inspection.normalizedPos(), false);
        }
    }

    private static void debug(String message, Object... args) {
        if (Config.barrierBreakDebugLogs || Config.barrierBreakVerboseDebug || Config.debugLogs) {
            LOGGER.info("[" + Zlogic.MODID + "] " + message, args);
        }
    }
}
