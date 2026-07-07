package infinitygroup.zlogic.barrier;

import infinitygroup.zlogic.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class BarrierBreakVisualHelper {
    private BarrierBreakVisualHelper() {
    }

    public static void playImpact(ServerLevel level, BlockPos pos, BlockState state, ZombieBarrierBreakHelper.BarrierKind kind) {
        play(level, pos, state, kind, false);
    }

    public static void playBreak(ServerLevel level, BlockPos pos, BlockState state, ZombieBarrierBreakHelper.BarrierKind kind) {
        play(level, pos, state, kind, true);
    }

    private static void play(ServerLevel level, BlockPos pos, BlockState state, ZombieBarrierBreakHelper.BarrierKind kind, boolean broken) {
        if (level == null || pos == null || state == null) {
            return;
        }

        if (Config.barrierBreakParticles) {
            BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, state);
            int count = broken ? 16 : 8;
            double spread = broken ? 0.35D : 0.20D;
            level.sendParticles(particle, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, count, spread, spread, spread, 0.01D);
        }

        if (!Config.barrierBreakSounds) {
            return;
        }

        boolean wood = kind == ZombieBarrierBreakHelper.BarrierKind.WOODEN_DOOR
            || kind == ZombieBarrierBreakHelper.BarrierKind.WOODEN_TRAPDOOR
            || kind == ZombieBarrierBreakHelper.BarrierKind.WOODEN_FENCE_GATE;

        if (wood) {
            level.playSound(null, pos, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.85F, broken ? 0.95F : 0.75F);
            return;
        }

        level.playSound(null, pos, broken ? SoundEvents.GLASS_BREAK : SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.80F, broken ? 1.0F : 0.85F);
    }
}
