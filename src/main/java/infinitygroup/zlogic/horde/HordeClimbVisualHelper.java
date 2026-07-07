package infinitygroup.zlogic.horde;

import infinitygroup.zlogic.Config;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;

import java.util.List;

public final class HordeClimbVisualHelper {
    private HordeClimbVisualHelper() {
    }

    public static void playClimbEffects(ServerLevel level, Zombie sourceZombie, List<Zombie> nearbyZombies, HordeClimbHelper.HordeClimbAssessment assessment) {
        if (level == null || sourceZombie == null || assessment == null) {
            return;
        }

        if (Config.hordeClimbSounds) {
            level.playSound(null, sourceZombie.blockPosition(), SoundEvents.ZOMBIE_STEP, SoundSource.HOSTILE, 0.7F, 0.9F + sourceZombie.getRandom().nextFloat() * 0.2F);
            level.playSound(null, sourceZombie.blockPosition(), SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 0.35F, 0.9F + sourceZombie.getRandom().nextFloat() * 0.2F);
        }

        if (Config.hordeClimbParticles) {
            Vec3 center = sourceZombie.position();
            level.sendParticles(ParticleTypes.POOF, center.x, center.y + 0.35D, center.z, 8, 0.35D, 0.2D, 0.35D, 0.01D);
            level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.85D, center.z, 4, 0.2D, 0.2D, 0.2D, 0.01D);
        }

        if (Config.hordeClimbGroupVisual && nearbyZombies != null && !nearbyZombies.isEmpty()) {
            for (Zombie zombie : nearbyZombies) {
                if (zombie == null || zombie == sourceZombie) {
                    continue;
                }

                zombie.getLookControl().setLookAt(sourceZombie, 20.0F, 20.0F);
            }
        }
    }
}
