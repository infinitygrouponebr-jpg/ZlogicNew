package infinitygroup.zlogic;

import infinitygroup.zlogic.zombie.ZombieNightBuffHandler;
import infinitygroup.zlogic.zombie.ZombieDaySunProtectionHandler;
import infinitygroup.zlogic.zombie.DayZombieSpawnController;
import infinitygroup.zlogic.zombie.NightZombieSpawnController;
import infinitygroup.zlogic.zombie.ZlogicSpawnControlHandler;
import infinitygroup.zlogic.zombie.ZombieAggressionHandler;
import infinitygroup.zlogic.horde.ZombieHordeClimbHandler;
import infinitygroup.zlogic.barrier.ZombieBarrierBreakHandler;
import infinitygroup.zlogic.noise.NoiseEventHandler;
import infinitygroup.zlogic.noise.ZombieNoiseHandler;
import infinitygroup.zlogic.compat.microtech.MicroTechCompat;
import infinitygroup.zlogic.command.ZlogicDebugCommand;
import infinitygroup.zlogic.machine.MachineAttackHandler;
import infinitygroup.zlogic.perf.PerformanceTracker;
import infinitygroup.zlogic.zombie.ZombieBaseDamageHandler;
import infinitygroup.zlogic.zombie.ZombiePassiveHuntHandler;
import infinitygroup.zlogic.zombie.ZombieSurvivalScalingHandler;
import infinitygroup.zlogic.zombie.ZombieTankHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Zlogic.MODID)
public class Zlogic {
    public static final String MODID = "zlogicnew";

    public Zlogic(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        NeoForge.EVENT_BUS.addListener(DayZombieSpawnController::onServerTick);
        NeoForge.EVENT_BUS.addListener(NightZombieSpawnController::onServerTick);
        NeoForge.EVENT_BUS.addListener(ZombieNightBuffHandler::onEntityTick);
        NeoForge.EVENT_BUS.addListener(ZombieDaySunProtectionHandler::onEntityTick);
        NeoForge.EVENT_BUS.addListener(ZombieAggressionHandler::onEntityTick);
        NeoForge.EVENT_BUS.addListener(ZombiePassiveHuntHandler::onEntityTick);
        NeoForge.EVENT_BUS.addListener(ZombiePassiveHuntHandler::onLivingDrops);
        NeoForge.EVENT_BUS.addListener(ZombieTankHandler::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(ZombieTankHandler::onEntityTick);
        NeoForge.EVENT_BUS.addListener(ZombieHordeClimbHandler::onEntityTick);
        NeoForge.EVENT_BUS.addListener(ZombieBarrierBreakHandler::onEntityTick);
        NeoForge.EVENT_BUS.addListener(NoiseEventHandler::onServerTick);
        NeoForge.EVENT_BUS.addListener(NoiseEventHandler::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(NoiseEventHandler::onExplosionDetonate);
        NeoForge.EVENT_BUS.addListener(NoiseEventHandler::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(ZombieSurvivalScalingHandler::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(ZlogicSpawnControlHandler::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(MachineAttackHandler::onEntityTick);
        NeoForge.EVENT_BUS.addListener(ZombieNoiseHandler::onEntityTick);
        NeoForge.EVENT_BUS.addListener(ZombieBaseDamageHandler::onEntityTick);
        NeoForge.EVENT_BUS.addListener(ZombieSurvivalScalingHandler::onEntityTick);
        NeoForge.EVENT_BUS.addListener(PerformanceTracker::onServerTick);
        NeoForge.EVENT_BUS.addListener(MachineAttackHandler::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(ZombieBarrierBreakHandler::onLevelUnload);
        MicroTechCompat.registerIfAvailable(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(ZlogicDebugCommand::register);
    }
}
