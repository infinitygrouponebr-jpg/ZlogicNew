package infinitygroup.zlogic.perf;

import infinitygroup.zlogic.Config;
import infinitygroup.zlogic.Zlogic;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public final class PerformanceTracker {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static long entitiesProcessed;
    private static long zombiesProcessed;
    private static long machineScans;
    private static long blockPositionsChecked;
    private static long noiseChecks;
    private static long survivalScalingApplications;
    private static long threatJoinApplications;
    private static long threatFallbackApplications;
    private static long threatCommandRescales;
    private static long aggressionChecks;
    private static long lastResetTick;

    private PerformanceTracker() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!Config.enablePerformanceDebugLogs) {
            return;
        }

        long tick = event.getServer().getTickCount();
        int interval = Math.max(1, Config.performanceDebugIntervalTicks);
        if (lastResetTick == 0L) {
            lastResetTick = tick;
        }

        if (tick - lastResetTick < interval) {
            return;
        }

        PerformanceSnapshot snapshot = snapshot();
        LOGGER.info(
            "[{}] performance window tick={} entities={} zombies={} machineScans={} blockPositions={} noiseChecks={} survivalApps={} threatJoinApps={} threatFallbackApps={} threatCommandRescales={} aggressionChecks={}",
            Zlogic.MODID,
            tick,
            snapshot.entitiesProcessed(),
            snapshot.zombiesProcessed(),
            snapshot.machineScans(),
            snapshot.blockPositionsChecked(),
            snapshot.noiseChecks(),
            snapshot.survivalScalingApplications(),
            snapshot.threatJoinApplications(),
            snapshot.threatFallbackApplications(),
            snapshot.threatCommandRescales(),
            snapshot.aggressionChecks()
        );
        reset();
        lastResetTick = tick;
    }

    public static void recordEntityProcessed() {
        entitiesProcessed++;
    }

    public static void recordZombieProcessed() {
        zombiesProcessed++;
    }

    public static void recordMachineScan(int blockChecks) {
        machineScans++;
        if (blockChecks > 0) {
            blockPositionsChecked += blockChecks;
        }
    }

    public static void recordNoiseChecks(int checks) {
        if (checks > 0) {
            noiseChecks += checks;
        }
    }

    public static void recordSurvivalScalingApplications(int applications) {
        if (applications > 0) {
            survivalScalingApplications += applications;
        }
    }

    public static void recordThreatJoinApplication() {
        threatJoinApplications++;
    }

    public static void recordThreatFallbackApplication() {
        threatFallbackApplications++;
    }

    public static void recordThreatCommandRescale() {
        threatCommandRescales++;
    }

    public static void recordAggressionCheck() {
        aggressionChecks++;
    }

    public static PerformanceSnapshot snapshot() {
        return new PerformanceSnapshot(
            entitiesProcessed,
            zombiesProcessed,
            machineScans,
            blockPositionsChecked,
            noiseChecks,
            survivalScalingApplications,
            threatJoinApplications,
            threatFallbackApplications,
            threatCommandRescales,
            aggressionChecks,
            lastResetTick
        );
    }

    public static void reset() {
        entitiesProcessed = 0L;
        zombiesProcessed = 0L;
        machineScans = 0L;
        blockPositionsChecked = 0L;
        noiseChecks = 0L;
        survivalScalingApplications = 0L;
        threatJoinApplications = 0L;
        threatFallbackApplications = 0L;
        threatCommandRescales = 0L;
        aggressionChecks = 0L;
    }

    public record PerformanceSnapshot(
        long entitiesProcessed,
        long zombiesProcessed,
        long machineScans,
        long blockPositionsChecked,
        long noiseChecks,
        long survivalScalingApplications,
        long threatJoinApplications,
        long threatFallbackApplications,
        long threatCommandRescales,
        long aggressionChecks,
        long lastResetTick
    ) {
    }
}
