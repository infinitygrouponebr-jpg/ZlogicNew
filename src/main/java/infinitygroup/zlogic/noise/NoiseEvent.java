package infinitygroup.zlogic.noise;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;

public final class NoiseEvent {
    private final ResourceKey<Level> dimension;
    private final BlockPos pos;
    private final double radius;
    private int remainingTicks;
    private final NoiseSourceType type;
    private final UUID sourceEntityId;
    private final boolean attractsZombies;

    public NoiseEvent(
        ResourceKey<Level> dimension,
        BlockPos pos,
        double radius,
        int remainingTicks,
        NoiseSourceType type,
        UUID sourceEntityId,
        boolean attractsZombies
    ) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.pos = Objects.requireNonNull(pos, "pos");
        this.radius = radius;
        this.remainingTicks = remainingTicks;
        this.type = Objects.requireNonNull(type, "type");
        this.sourceEntityId = sourceEntityId;
        this.attractsZombies = attractsZombies;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos pos() {
        return pos;
    }

    public double radius() {
        return radius;
    }

    public int remainingTicks() {
        return remainingTicks;
    }

    public NoiseSourceType type() {
        return type;
    }

    public UUID sourceEntityId() {
        return sourceEntityId;
    }

    public boolean attractsZombies() {
        return attractsZombies;
    }

    public void tick() {
        if (remainingTicks > 0) {
            remainingTicks--;
        }
    }

    public boolean isExpired() {
        return remainingTicks <= 0;
    }
}
