package infinitygroup.zlogic.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import infinitygroup.zlogic.Config;
import infinitygroup.zlogic.compat.OptionalModCompat;
import infinitygroup.zlogic.compat.microtech.MicroTechCompat;
import infinitygroup.zlogic.compat.microtech.MicroTechNoiseHandler;
import infinitygroup.zlogic.barrier.ZombieBarrierBreakHelper;
import infinitygroup.zlogic.horde.HordeBodyStackAssistHelper;
import infinitygroup.zlogic.horde.HordeClimbHelper;
import infinitygroup.zlogic.machine.MachineAttackHandler;
import infinitygroup.zlogic.perf.PerformanceTracker;
import infinitygroup.zlogic.noise.NoiseEvent;
import infinitygroup.zlogic.noise.NoiseManager;
import infinitygroup.zlogic.noise.NoiseSourceType;
import infinitygroup.zlogic.senses.BloodTrace;
import infinitygroup.zlogic.senses.BloodTraceManager;
import infinitygroup.zlogic.senses.LightInterest;
import infinitygroup.zlogic.senses.LightInterestManager;
import infinitygroup.zlogic.senses.SenseTarget;
import infinitygroup.zlogic.senses.ZombieSenseManager;
import infinitygroup.zlogic.zombie.ZombieThreatLevelManager;
import infinitygroup.zlogic.zombie.ZombieScalingLevelManager;
import infinitygroup.zlogic.zombie.ZombieBaseDamageHandler;
import infinitygroup.zlogic.zombie.ZombieFamilyHelper;
import infinitygroup.zlogic.zombie.ZombieEligibilityHelper;
import infinitygroup.zlogic.zombie.ZombieMarkingHelper;
import infinitygroup.zlogic.zombie.NightZombieSpawnController;
import infinitygroup.zlogic.zombie.ZombieNightBuffHandler;
import infinitygroup.zlogic.zombie.ZombieSurvivalScalingHelper;
import infinitygroup.zlogic.zombie.ZombieSurvivalScalingHandler;
import infinitygroup.zlogic.zombie.ZombieTankHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;

public final class ZlogicDebugCommand {
    private ZlogicDebugCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("zlogic")
                .then(
                    Commands.literal("debug")
                        .then(
                            Commands.literal("machine")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> executeMachineDebug(context.getSource()))
                        )
                        .then(
                            Commands.literal("emit_machine_noise")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> executeEmitMachineNoise(context.getSource()))
                        )
                        .then(
                            Commands.literal("night_spawn")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> executeNightSpawnDebug(context.getSource()))
                        )
                        .then(
                            Commands.literal("force_night_spawn")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> executeForceNightSpawn(context.getSource()))
                        )
                        .then(
                            Commands.literal("zombie")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> executeZombieDebug(context.getSource()))
                        )
                        .then(
                            Commands.literal("performance")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> executePerformanceDebug(context.getSource()))
                        )
                        .then(
                            Commands.literal("horde_climb")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> executeHordeClimbDebug(context.getSource()))
                        )
                        .then(
                            Commands.literal("barrier_break")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> executeBarrierBreakDebug(context.getSource()))
                        )
                        .then(
                            Commands.literal("blood")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> executeBloodDebug(context.getSource()))
                        )
                        .then(
                            Commands.literal("light")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> executeLightDebug(context.getSource()))
                        )
                        .then(
                            Commands.literal("rescale_zombie")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> executeRescaleZombie(context.getSource()))
                        )
                        .then(
                            Commands.literal("spawn")
                                .requires(source -> source.hasPermission(2))
                                .then(createSpawnLiteral("vanilla", SpawnVariant.VANILLA))
                                .then(createSpawnLiteral("zlogic", SpawnVariant.ZLOGIC))
                                .then(createSpawnLiteral("day", SpawnVariant.DAY))
                                .then(createSpawnLiteral("night", SpawnVariant.NIGHT))
                                .then(createSpawnLiteral("tank", SpawnVariant.TANK))
                        )
                )
        );
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createSpawnLiteral(String literal, SpawnVariant variant) {
        return Commands.literal(literal)
            .executes(context -> executeSpawnZombie(context.getSource(), variant, 1))
            .then(
                Commands.argument("count", IntegerArgumentType.integer(1, 32))
                    .executes(context -> executeSpawnZombie(context.getSource(), variant, IntegerArgumentType.getInteger(context, "count")))
            );
    }

    private static int executeMachineDebug(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }

        HitResult hitResult = player.pick(8.0D, 0.0F, false);
        StringBuilder output = new StringBuilder();
        output.append("Zlogic Machine Debug\n");

        if (!(hitResult instanceof BlockHitResult blockHitResult)) {
            output.append("Target: no block hit within 8 blocks\n");
            send(source, output.toString());
            return 1;
        }

        BlockPos pos = blockHitResult.getBlockPos();
        MicroTechNoiseHandler.MachineInspection inspection = MicroTechNoiseHandler.inspectMachine(player.serverLevel(), pos);
        MachineAttackHandler.MachineAttackAssessment attackAssessment = MachineAttackHandler.inspectMachineAttack(player.serverLevel(), pos);
        long serverTick = player.serverLevel().getGameTime();
        double recentNoiseRadius = Math.max(Config.microTechMachineNoiseRadius, Config.zombieMachineAttackRange);
        NoiseEvent nearestMachineNoise = NoiseManager.getNearestNoise(player.serverLevel(), pos, recentNoiseRadius, NoiseSourceType.MACHINE);
        Long lastScanTick = MicroTechNoiseHandler.getLastMachineScanTick(player.serverLevel());
        Long lastNoiseTick = MicroTechNoiseHandler.getLastMachineNoiseTick(player.serverLevel(), pos);
        int cooldownRemaining = MicroTechNoiseHandler.getMachineNoiseCooldownRemainingTicks(player.serverLevel(), pos, serverTick);

        output.append("Block: ").append(formatBlockId(inspection.blockId())).append('\n');
        output.append("Namespace: ").append(formatNamespace(inspection.blockId())).append('\n');
        output.append("Block class: ").append(inspection.blockClassName()).append('\n');
        output.append("BlockEntity class: ").append(inspection.blockEntityClassName() == null ? "none" : inspection.blockEntityClassName()).append('\n');
        output.append("Has BlockEntity: ").append(inspection.hasBlockEntity()).append('\n');
        output.append("Persistent keys: ").append(inspection.persistentKeys()).append('\n');
        output.append("MicroTech namespace: ").append(inspection.microTechNamespace()).append('\n');
        output.append("MicroTech installed: ").append(OptionalModCompat.isLoaded(MicroTechCompat.MODID)).append('\n');
        output.append("MicroTech compat enabled: ").append(inspection.microTechCompatEnabled()).append('\n');
        output.append("Machine noise enabled: ").append(inspection.machineNoiseEnabled()).append('\n');
        output.append("Only running machines make noise: ").append(inspection.onlyRunningMachinesMakeNoise()).append('\n');
        output.append("In explicit allowlist: ").append(inspection.inExplicitAllowlist()).append('\n');
        output.append("In denylist: ").append(inspection.inDenylist()).append('\n');
        output.append("Has microtech_active marker: ").append(inspection.hasMicroTechActiveMarker()).append('\n');
        output.append("microtech_active: ").append(inspection.microTechActive() == null ? "n/a" : inspection.microTechActive()).append('\n');
        output.append("Treated as MicroTech machine: ").append(inspection.treatedAsMicroTechMachine()).append('\n');
        output.append("Detected state: ").append(inspection.detectedState()).append('\n');
        output.append("Would emit MACHINE noise: ").append(inspection.wouldEmitNoise() ? "YES" : "NO").append('\n');
        output.append("Direct attraction enabled: ").append(attackAssessment.directAttractionEnabled()).append('\n');
        output.append("Direct detection enabled: ").append(attackAssessment.directDetectionEnabled()).append('\n');
        output.append("Direct attraction radius: ").append(formatDouble(attackAssessment.directAttractionRadius())).append('\n');
        output.append("Direct valid machine: ").append(attackAssessment.directValidMachine()).append('\n');
        output.append("Active for direct attack: ").append(attackAssessment.directMachineActive()).append('\n');
        output.append("Attack requires noise: ").append(Config.zombieMachineAttackRequireNoise).append('\n');
        output.append("Attack uses direct detection: ").append(attackAssessment.directDetectionEnabled()).append('\n');
        output.append("MACHINE noise radius check: ").append(formatDouble(recentNoiseRadius)).append('\n');
        output.append("Actually has recent MACHINE noise: ").append(attackAssessment.hasRecentMachineNoise()).append('\n');
        output.append("Nearest MACHINE noise distance: ").append(formatNoiseDistance(pos, nearestMachineNoise)).append('\n');
        output.append("Nearest MACHINE noise remaining ticks: ").append(nearestMachineNoise == null ? "none" : nearestMachineNoise.remainingTicks()).append('\n');
        output.append("Last machine scan tick: ").append(lastScanTick == null ? "none" : lastScanTick).append('\n');
        output.append("Emission cooldown active: ").append(cooldownRemaining > 0).append('\n');
        output.append("Emission cooldown remaining ticks: ").append(cooldownRemaining).append('\n');
        output.append("Last emission tick: ").append(lastNoiseTick == null ? "none" : lastNoiseTick).append('\n');
        output.append("Machine damage: ").append(attackAssessment.currentDamage()).append('\n');
        output.append("Machine durability: ").append(attackAssessment.durability()).append('\n');
        output.append("Attackable by zombies: ").append(attackAssessment.attackable()).append('\n');
        output.append("Attack reason: ").append(attackAssessment.reason()).append('\n');
        output.append("Reason: ").append(inspection.reason()).append('\n');
        output.append("Recent noise gap reason: ").append(describeNoiseGap(inspection, attackAssessment, nearestMachineNoise, cooldownRemaining, lastScanTick, serverTick));

        send(source, output.toString());
        return 1;
    }

    private static int executeEmitMachineNoise(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }

        HitResult hitResult = player.pick(8.0D, 0.0F, false);
        if (!(hitResult instanceof BlockHitResult blockHitResult)) {
            source.sendFailure(Component.literal("No block hit within 8 blocks."));
            return 0;
        }

        BlockPos pos = blockHitResult.getBlockPos();
        MicroTechNoiseHandler.MachineInspection inspection = MicroTechNoiseHandler.inspectMachine(player.serverLevel(), pos);
        if (!inspection.treatedAsMicroTechMachine() || !inspection.wouldEmitNoise()) {
            source.sendFailure(Component.literal("Target is not an eligible active MicroTech machine."));
            return 0;
        }

        NoiseManager.emitNoise(
            player.serverLevel(),
            pos,
            Config.microTechMachineNoiseRadius,
            Config.microTechMachineNoiseDurationTicks,
            NoiseSourceType.MACHINE
        );
        source.sendSuccess(() -> Component.literal("Emitted MACHINE noise manually at " + pos), false);
        return 1;
    }

    private static int executeZombieDebug(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }

        ZombieTargetSearchResult targetSearch = findZombieTarget(player, 8.0D, Config.debugZombieCommandFallbackRadius);
        StringBuilder output = new StringBuilder();
        output.append("Zlogic Zombie Debug\n");

        if (targetSearch == null || targetSearch.entity() == null) {
            output.append("Target: Nenhum zombie encontrado na mira ou proximo.\n");
            output.append("Fallback zombie count: ").append(targetSearch == null ? 0 : targetSearch.fallbackCount()).append('\n');
            send(source, output.toString());
            return 1;
        }

        LivingEntity living = targetSearch.entity();
        output.append("Target mode: ").append(targetSearch.exactHit() ? "raytrace" : "fallback").append('\n');
        output.append("Fallback zombie count: ").append(targetSearch.fallbackCount()).append('\n');
        ZombieBaseDamageHandler.ZombieBaseDamageInspection baseInspection = ZombieBaseDamageHandler.inspectZombie(player.serverLevel(), living);
        ZombieThreatLevelManager.ThreatInspection threatInspection = ZombieThreatLevelManager.inspectZombie(player.serverLevel(), (net.minecraft.world.entity.monster.Zombie) living);
        ZombieSurvivalScalingHandler.ZombieSurvivalScalingInspection legacyScalingInspection = ZombieSurvivalScalingHandler.inspectZombie(player.serverLevel(), living);
        AttributeInstance attackDamage = living.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance movementSpeed = living.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance followRange = living.getAttribute(Attributes.FOLLOW_RANGE);
        AttributeInstance armor = living.getAttribute(Attributes.ARMOR);
        AttributeInstance maxHealth = living.getAttribute(Attributes.MAX_HEALTH);
        AttributeModifier baseModifier = attackDamage != null ? attackDamage.getModifier(ZombieBaseDamageHandler.BASE_DAMAGE_MODIFIER_ID) : null;
        AttributeModifier nightModifier = attackDamage != null ? attackDamage.getModifier(ZombieNightBuffHandler.DAMAGE_MODIFIER_ID) : null;
        int worldDay = threatInspection.worldDay();
        int scalingDays = Math.max(0, worldDay - Config.threatWorldDayStartDay);

        output.append("Entity type: ").append(living.getType().toShortString()).append('\n');
        output.append("zlogic_spawned: ").append(ZombieMarkingHelper.isZlogicSpawned(living)).append('\n');
        output.append("zlogic_day_spawned: ").append(ZombieMarkingHelper.isDaySpawned(living)).append('\n');
        output.append("zlogic_night_spawned: ").append(ZombieMarkingHelper.isNightSpawned(living)).append('\n');
        output.append("treated_as_zlogic_zombie: ").append(ZombieMarkingHelper.isZlogicSpawned(living)).append('\n');
        output.append("eligible_for_zlogic_systems: ").append(ZombieEligibilityHelper.isEligibleForZlogicSystems(living)).append('\n');
        output.append("zlogicSystemsAffectOnlyZlogicZombies: ").append(Config.zlogicSystemsAffectOnlyZlogicZombies).append('\n');
        output.append("eligible_for_night_buffs: ").append(ZombieEligibilityHelper.isEligibleForNightBuffs(living)).append('\n');
        output.append("eligible_for_base_damage: ").append(living instanceof net.minecraft.world.entity.monster.Zombie zombie ? ZombieEligibilityHelper.isEligibleForBaseDamage(zombie) : false).append('\n');
        output.append("eligible_for_threat_scaling: ").append(ZombieEligibilityHelper.isEligibleForThreatScaling(living)).append('\n');
        output.append("eligible_for_aggression: ").append(living instanceof net.minecraft.world.entity.monster.Zombie zombie ? ZombieEligibilityHelper.isEligibleForAggression(zombie) : false).append('\n');
        output.append("eligible_for_machine_attack: ").append(ZombieEligibilityHelper.isEligibleForMachineAttack(living)).append('\n');
        if (living instanceof net.minecraft.world.entity.monster.Zombie zombieEntity) {
            HordeClimbHelper.HordeClimbAssessment hordeAssessment = HordeClimbHelper.inspect(player.serverLevel(), zombieEntity);
            output.append("horde climb enabled: ").append(hordeAssessment.enabled()).append('\n');
            output.append("horde climb eligible: ").append(hordeAssessment.eligible()).append('\n');
            output.append("horde climb active: ").append(hordeAssessment.active()).append('\n');
            output.append("horde climb target elevation mode enabled: ").append(Config.enableHordeClimbTargetElevationMode).append('\n');
            output.append("horde climb last activation mode: ").append(HordeClimbHelper.getLastActivationMode(zombieEntity)).append('\n');
            output.append("horde climb target kind: ").append(hordeAssessment.targetKind()).append('\n');
            output.append("horde climb last group size: ").append(HordeClimbHelper.getLastGroupSize(zombieEntity)).append('\n');
            output.append("horde climb last height: ").append(formatDouble(HordeClimbHelper.getLastHeight(zombieEntity))).append('\n');
            output.append("horde climb cooldown remaining: ").append(HordeClimbHelper.getCooldownRemaining(zombieEntity, player.serverLevel().getGameTime())).append('\n');
            output.append("horde climb has target: ").append(hordeAssessment.hasTarget()).append('\n');
            output.append("horde climb target allowed: ").append(hordeAssessment.targetAllowed()).append('\n');
            output.append("horde climb leader: ").append(hordeAssessment.leader()).append('\n');
            output.append("horde climb zombie Y: ").append(formatDouble(hordeAssessment.zombieY())).append('\n');
            output.append("horde climb target Y: ").append(formatDouble(hordeAssessment.targetY())).append('\n');
            output.append("horde climb Y difference: ").append(formatDouble(hordeAssessment.yDifference())).append('\n');
            output.append("horde climb block Y difference: ").append(formatDouble(hordeAssessment.blockYDifference())).append('\n');
            output.append("horde climb horizontal distance: ").append(formatDouble(hordeAssessment.horizontalDistance())).append('\n');
            output.append("horde climb obstacle mode: ").append(hordeAssessment.obstacleModeActive()).append(" reason=").append(hordeAssessment.obstacleReason()).append('\n');
            output.append("horde climb target elevation mode: ").append(hordeAssessment.targetElevationModeActive()).append(" reason=").append(hordeAssessment.targetElevationReason()).append('\n');
            output.append("horde climb target obstacle between: ").append(hordeAssessment.targetObstacleBetween()).append('\n');
            output.append("horde climb target obstacle height: ").append(hordeAssessment.targetObstacleHeight()).append('\n');
            output.append("horde climb obstacle detected: ").append(hordeAssessment.obstacleModeActive()).append('\n');
            output.append("horde climb obstacle height: ").append(hordeAssessment.obstacleHeight()).append('\n');
            output.append("horde climb reason: ").append(hordeAssessment.reason()).append('\n');
            ZombieBarrierBreakHelper.BarrierBreakAssessment barrierAssessment = ZombieBarrierBreakHelper.inspect(player.serverLevel(), zombieEntity);
            output.append("barrier break enabled: ").append(barrierAssessment.enabled()).append('\n');
            output.append("barrier break eligible: ").append(barrierAssessment.eligible()).append('\n');
            output.append("barrier break last barrier type: ").append(ZombieBarrierBreakHelper.getLastBarrierType(zombieEntity)).append('\n');
            output.append("barrier break last group size: ").append(ZombieBarrierBreakHelper.getLastGroupSize(zombieEntity)).append('\n');
            output.append("barrier break last barrier damage: ").append(ZombieBarrierBreakHelper.getLastDamage(zombieEntity)).append('\n');
            output.append("barrier break last barrier reason: ").append(ZombieBarrierBreakHelper.getLastReason(zombieEntity)).append('\n');
            output.append("barrier break cooldown remaining: ").append(Math.max(0L, ZombieBarrierBreakHelper.getNextTick(zombieEntity) - player.serverLevel().getGameTime())).append('\n');
            output.append("barrier break target type: ").append(barrierAssessment.targetType()).append('\n');
            output.append("barrier break target allowed: ").append(barrierAssessment.targetAllowed()).append('\n');
            output.append("barrier break active: ").append(barrierAssessment.active()).append('\n');
            output.append("barrier break eligible zombies nearby: ").append(barrierAssessment.eligibleZombieCount()).append('\n');
            output.append("barrier break eligible zombies on attack side: ").append(barrierAssessment.attackSideZombieCount()).append('\n');
            output.append("barrier break zombies rejected by side: ").append(Math.max(0, barrierAssessment.eligibleZombieCount() - barrierAssessment.attackSideZombieCount())).append('\n');
            output.append("barrier break required front side: ").append(Config.barrierBreakRequireFrontSide).append('\n');
            output.append("barrier break front width: ").append(formatDouble(Config.barrierBreakFrontWidth)).append('\n');
            output.append("barrier break front depth: ").append(formatDouble(Config.barrierBreakFrontDepth)).append('\n');
            output.append("barrier break max distance to barrier: ").append(formatDouble(Config.barrierBreakMaxZombieDistanceToBarrier)).append('\n');
            output.append("barrier break require target behind barrier: ").append(Config.barrierBreakRequireTargetBehindBarrier).append('\n');
            output.append("barrier break min path alignment: ").append(formatDouble(Config.barrierBreakMinPathAlignment)).append('\n');
            output.append("barrier break reject zombies behind barrier: ").append(Config.barrierBreakRejectZombiesBehindBarrier).append('\n');
            output.append("barrier break side debug: ").append(Config.barrierBreakSideDebug).append('\n');
            output.append("barrier break reason: ").append(barrierAssessment.reason()).append('\n');
        }
        output.append("Difficulty: ").append(player.serverLevel().getDifficulty()).append('\n');
        output.append("zlogic_day_spawned: ").append(baseInspection.daySpawned()).append('\n');
        output.append("Eligible for base damage: ").append(baseInspection.eligible()).append('\n');
        output.append("Current ATTACK_DAMAGE: ").append(formatDouble(attackDamage == null ? 0.0D : attackDamage.getValue())).append('\n');
        output.append("Has zlogic:base_difficulty_damage: ").append(baseModifier != null).append('\n');
        output.append("Base damage modifier amount: ").append(baseModifier == null ? "none" : formatDouble(baseModifier.amount())).append('\n');
        output.append("Base damage modifier operation: ").append(baseModifier == null ? "none" : baseModifier.operation()).append('\n');
        output.append("Has night damage modifier: ").append(nightModifier != null).append('\n');
        output.append("Night damage modifier amount: ").append(nightModifier == null ? "none" : formatDouble(nightModifier.amount())).append('\n');
        output.append("Current MOVEMENT_SPEED: ").append(formatDouble(movementSpeed == null ? 0.0D : movementSpeed.getValue())).append('\n');
        output.append("Current FOLLOW_RANGE: ").append(formatDouble(followRange == null ? 0.0D : followRange.getValue())).append('\n');
        output.append("Current ARMOR: ").append(formatDouble(armor == null ? 0.0D : armor.getValue())).append('\n');
        output.append("Current MAX_HEALTH: ").append(formatDouble(maxHealth == null ? 0.0D : maxHealth.getValue())).append('\n');
        output.append("Threat scaling enabled: ").append(threatInspection.enabled()).append('\n');
        output.append("Threat scaling mode: ").append(threatInspection.mode()).append('\n');
        output.append("Threat final level saved: ").append(threatInspection.storedFinalLevel()).append('\n');
        output.append("Threat world day current: ").append(threatInspection.worldDay()).append('\n');
        output.append("Threat world day saved: ").append(threatInspection.storedWorldDay()).append('\n');
        output.append("Threat world day contribution: ").append(threatInspection.worldDayLevel()).append('\n');
        output.append("Threat difficulty contribution: ").append(threatInspection.difficultyBonus()).append('\n');
        output.append("Threat distance contribution: ").append(threatInspection.distanceBonus()).append('\n');
        output.append("Threat random variance: ").append(threatInspection.randomVariance()).append('\n');
        output.append("Threat nearest player contribution: ").append(threatInspection.nearestPlayerBonus()).append('\n');
        output.append("Threat final level cap: ").append(threatInspection.levelCap()).append('\n');
        output.append("Threat source: ").append(threatInspection.storedSource()).append('\n');
        output.append("Threat applied: ").append(threatInspection.applied()).append('\n');
        output.append("Threat last apply tick: ").append(threatInspection.lastApplyTick()).append('\n');
        output.append("Threat modifiers match saved threat level: ").append(ZombieThreatLevelManager.modifiersMatch((net.minecraft.world.entity.monster.Zombie) living, threatInspection.computation())).append('\n');
        output.append("Threat damage multiplier: ").append(formatDouble(threatInspection.damageMultiplier())).append('\n');
        output.append("Threat speed multiplier: ").append(formatDouble(threatInspection.speedMultiplier())).append('\n');
        output.append("Threat follow range multiplier: ").append(formatDouble(threatInspection.followRangeMultiplier())).append('\n');
        output.append("Threat armor multiplier: ").append(formatDouble(threatInspection.armorMultiplier())).append('\n');
        output.append("Threat health multiplier: ").append(formatDouble(threatInspection.healthMultiplier())).append('\n');
        output.append("Threat ATTACK_DAMAGE value: ").append(formatDouble(threatInspection.attackDamageValue())).append('\n');
        output.append("Threat MOVEMENT_SPEED value: ").append(formatDouble(threatInspection.movementSpeedValue())).append('\n');
        output.append("Threat FOLLOW_RANGE value: ").append(formatDouble(threatInspection.followRangeValue())).append('\n');
        output.append("Threat ARMOR value: ").append(formatDouble(threatInspection.armorValue())).append('\n');
        output.append("Threat MAX_HEALTH value: ").append(formatDouble(threatInspection.maxHealthValue())).append('\n');
        output.append("Threat has new damage modifier: ").append(threatInspection.hasDamageModifier()).append('\n');
        output.append("Threat has new speed modifier: ").append(threatInspection.hasSpeedModifier()).append('\n');
        output.append("Threat has new follow range modifier: ").append(threatInspection.hasFollowRangeModifier()).append('\n');
        output.append("Threat has new armor modifier: ").append(threatInspection.hasArmorModifier()).append('\n');
        output.append("Threat has new health modifier: ").append(threatInspection.hasHealthModifier()).append('\n');
        output.append("Threat has legacy damage modifier: ").append(threatInspection.hasLegacyDamageModifier()).append('\n');
        output.append("Threat has legacy speed modifier: ").append(threatInspection.hasLegacySpeedModifier()).append('\n');
        output.append("Threat has legacy follow range modifier: ").append(threatInspection.hasLegacyFollowRangeModifier()).append('\n');
        output.append("Threat has legacy armor modifier: ").append(threatInspection.hasLegacyArmorModifier()).append('\n');
        output.append("Threat has legacy health modifier: ").append(threatInspection.hasLegacyHealthModifier()).append('\n');
        output.append("Threat stored level: ").append(threatInspection.storedLevel()).append('\n');
        output.append("Threat stored final level: ").append(threatInspection.storedFinalLevel()).append('\n');
        output.append("Threat store on mob: ").append(threatInspection.storeOnMob()).append('\n');
        output.append("Threat apply on spawn: ").append(threatInspection.applyOnSpawn()).append('\n');
        output.append("Threat recheck interval: ").append(threatInspection.recheckIntervalTicks()).append('\n');
        output.append("Threat reapply missing modifiers: ").append(threatInspection.reapplyMissingModifiers()).append('\n');
        output.append("Threat disable heavy tick logic: ").append(threatInspection.disableHeavyEntityTickLogic()).append('\n');
        output.append("Threat health adjusted once: ").append(threatInspection.healthAdjustedOnce()).append('\n');
        output.append("Threat current source string: ").append(threatInspection.source()).append('\n');
        output.append("Threat stored random variance: ").append(threatInspection.storedRandomVariance()).append('\n');
        output.append("Threat stored distance bonus: ").append(threatInspection.storedDistanceBonus()).append('\n');
        output.append("Threat stored difficulty bonus: ").append(threatInspection.storedDifficultyBonus()).append('\n');
        output.append("Threat stored world day level: ").append(threatInspection.storedWorldDayLevel()).append('\n');
        output.append("Threat nearest player radius: ").append(formatDouble(Config.threatNearestPlayerRadius)).append('\n');
        output.append("Threat nearest player enabled: ").append(Config.threatNearestPlayerEnabled).append('\n');
        output.append("Threat nearest player survival days enabled: ").append(Config.threatNearestPlayerSurvivalDaysEnabled).append('\n');
        output.append("Threat world day start day: ").append(Config.threatWorldDayStartDay).append('\n');
        output.append("Threat world day levels per day: ").append(formatDouble(Config.threatWorldDayLevelsPerDay)).append('\n');
        output.append("Threat difficulty bonuses: E=").append(Config.threatDifficultyEasyBonus).append(" N=").append(Config.threatDifficultyNormalBonus).append(" H=").append(Config.threatDifficultyHardBonus).append('\n');
        output.append("Threat distance config: start=").append(Config.threatDistanceStartBlocks).append(" per1000=").append(Config.threatDistanceLevelsPer1000Blocks).append(" max=").append(Config.threatDistanceMaxBonus).append('\n');
        output.append("Threat variance config: min=").append(Config.threatRandomVarianceMin).append(" max=").append(Config.threatRandomVarianceMax).append('\n');
        output.append("Threat multiplier caps: dmg=").append(formatDouble(Config.threatMaxDamageMultiplier)).append(" spd=").append(formatDouble(Config.threatMaxSpeedMultiplier)).append(" fr=").append(formatDouble(Config.threatMaxFollowRangeMultiplier)).append(" arm=").append(formatDouble(Config.threatMaxArmorMultiplier)).append(" hp=").append(formatDouble(Config.threatMaxHealthMultiplier)).append('\n');
        ZombieScalingLevelManager.ScalingComputation scalingNow = ZombieScalingLevelManager.compute(player.serverLevel(), (net.minecraft.world.entity.monster.Zombie) living);
        output.append("Legacy survival scaling mode: ").append(scalingNow.mode()).append('\n');
        output.append("Legacy survival scaling source: ").append(scalingNow.source()).append('\n');
        output.append("Legacy stored scaling level: ").append(scalingNow.storedLevel()).append('\n');
        output.append("Legacy stored world day: ").append(scalingNow.storedWorldDay()).append('\n');
        output.append("Legacy stored applied flag: ").append(scalingNow.applied()).append('\n');
        output.append("Legacy stored last apply tick: ").append(scalingNow.lastApplyTick()).append('\n');
        output.append("Legacy current world day: ").append(scalingNow.worldDay()).append('\n');
        output.append("Legacy computed level now: ").append(scalingNow.scalingLevel()).append('\n');
        output.append("Legacy level cap: ").append(scalingNow.levelCap()).append('\n');
        output.append("Legacy modifiers match stored level: ").append(ZombieScalingLevelManager.modifiersMatch((net.minecraft.world.entity.monster.Zombie) living, scalingNow)).append('\n');
        output.append("Legacy apply on spawn: ").append(scalingNow.applyOnSpawn()).append('\n');
        output.append("Legacy store level on mob: ").append(scalingNow.storeLevelOnMob()).append('\n');
        output.append("Legacy reapply missing modifiers: ").append(scalingNow.reapplyMissingModifiers()).append('\n');
        output.append("Legacy disable heavy tick logic: ").append(scalingNow.disableHeavyEntityTickLogic()).append('\n');
        output.append("Legacy recheck interval: ").append(scalingNow.recheckIntervalTicks()).append('\n');
        output.append("Legacy expected damage multiplier: ").append(formatDouble(scalingNow.damageMultiplier())).append('\n');
        output.append("Legacy expected speed multiplier: ").append(formatDouble(scalingNow.speedMultiplier())).append('\n');
        output.append("Legacy expected follow range multiplier: ").append(formatDouble(scalingNow.followRangeMultiplier())).append('\n');
        output.append("Legacy expected armor multiplier: ").append(formatDouble(scalingNow.armorMultiplier())).append('\n');
        output.append("Legacy expected health multiplier: ").append(formatDouble(scalingNow.healthMultiplier())).append('\n');
        output.append("Day of world: ").append(worldDay).append('\n');
        output.append("Scaling start day: ").append(Config.survivalScalingStartDay).append('\n');
        output.append("Scaling days: ").append(scalingDays).append('\n');
        output.append("Damage scaling multiplier (future): ").append(formatDouble(threatInspection.damageMultiplier())).append('\n');
        output.append("Survival scaling enabled: ").append(Config.enableSurvivalDaysScaling).append('\n');
        output.append("Survival scaling active: ").append(legacyScalingInspection.active()).append('\n');
        output.append("Survival scaling use nearest player: ").append(Config.survivalScalingUseNearestPlayer).append('\n');
        output.append("Survival scaling radius: ").append(formatDouble(Config.survivalScalingRadius)).append('\n');
        output.append("Survival scaling check interval: ").append(Config.survivalScalingCheckIntervalTicks).append('\n');
        output.append("Base damage mode: ").append(Config.zombieBaseDamageUseMultiplier ? "MULTIPLIER" : "FIXED_BONUS").append('\n');
        output.append("Expected base modifier amount: ").append(formatDouble(baseInspection.expectedModifierAmount())).append('\n');
        output.append("Base damage reason: ").append(baseInspection.reason()).append('\n');
        output.append("Survival scaling reason: ").append(legacyScalingInspection.reason()).append('\n');

        send(source, output.toString());
        return 1;
    }

    private static int executeRescaleZombie(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }

        ZombieTargetSearchResult targetSearch = findZombieTarget(player, 8.0D, Config.debugZombieCommandFallbackRadius);
        if (targetSearch == null || !(targetSearch.entity() instanceof net.minecraft.world.entity.monster.Zombie zombie)) {
            source.sendFailure(Component.literal("Nenhum zombie encontrado na mira ou proximo."));
            return 0;
        }

        boolean threatActive = Config.enableThreatLevelScaling && ZombieSurvivalScalingHelper.ThreatScalingMode.parse(Config.threatScalingMode) != ZombieSurvivalScalingHelper.ThreatScalingMode.DISABLED;
        boolean legacyActive = Config.enableSurvivalDaysScaling && ZombieSurvivalScalingHelper.SurvivalScalingMode.parse(Config.survivalScalingMode) != ZombieSurvivalScalingHelper.SurvivalScalingMode.DISABLED;
        ZombieThreatLevelManager.ApplyResult threatResult = null;
        ZombieScalingLevelManager.ApplyResult legacyResult = null;
        if (threatActive) {
            threatResult = ZombieThreatLevelManager.apply(player.serverLevel(), zombie, true, true);
            PerformanceTracker.recordThreatCommandRescale();
        } else if (legacyActive) {
            ZombieThreatLevelManager.clearThreatStateAndModifiers(zombie);
            legacyResult = ZombieScalingLevelManager.apply(player.serverLevel(), zombie, true);
        } else {
            ZombieThreatLevelManager.clearAllScalingModifiers(zombie);
        }
        StringBuilder output = new StringBuilder();
        output.append("Zlogic Zombie Rescale\n");
        output.append("Target mode: ").append(targetSearch.exactHit() ? "raytrace" : "fallback").append('\n');
        output.append("Target type: ").append(zombie.getType().toShortString()).append('\n');
        if (threatResult != null) {
            output.append("Applied: ").append(threatResult.applied()).append('\n');
            output.append("Computed level: ").append(threatResult.computation().finalLevel()).append('\n');
            output.append("Source: ").append(threatResult.computation().source()).append('\n');
            output.append("World day: ").append(threatResult.computation().worldDay()).append('\n');
            output.append("Last apply tick: ").append(threatResult.computation().lastApplyTick()).append('\n');
            output.append("World day contribution: ").append(threatResult.computation().worldDayLevel()).append('\n');
            output.append("Difficulty contribution: ").append(threatResult.computation().difficultyBonus()).append('\n');
            output.append("Distance contribution: ").append(threatResult.computation().distanceBonus()).append('\n');
            output.append("Random variance: ").append(threatResult.computation().randomVariance()).append('\n');
            output.append("Nearest player contribution: ").append(threatResult.computation().nearestPlayerBonus()).append('\n');
            output.append("Final level cap: ").append(threatResult.computation().levelCap()).append('\n');
            output.append("Changes: ").append(threatResult.changes()).append('\n');
            output.append("Health adjusted: ").append(threatResult.healthAdjusted()).append('\n');
        } else if (legacyResult != null) {
            output.append("Applied: ").append(legacyResult.applied()).append('\n');
            output.append("Computed level: ").append(legacyResult.computation().scalingLevel()).append('\n');
            output.append("Source: ").append(legacyResult.computation().source()).append('\n');
            output.append("World day: ").append(legacyResult.computation().worldDay()).append('\n');
            output.append("Last apply tick: ").append(legacyResult.computation().lastApplyTick()).append('\n');
            output.append("Changes: ").append(legacyResult.changes()).append('\n');
            output.append("Health adjusted: ").append(legacyResult.healthAdjusted()).append('\n');
        } else {
            output.append("Applied: false\n");
            output.append("Computed level: 0\n");
            output.append("Source: disabled\n");
            output.append("World day: ").append(player.serverLevel().getDayTime() / 24000L).append('\n');
            output.append("Last apply tick: ").append(zombie.level().getGameTime()).append('\n');
            output.append("World day contribution: 0\n");
            output.append("Difficulty contribution: 0\n");
            output.append("Distance contribution: 0\n");
            output.append("Random variance: 0\n");
            output.append("Nearest player contribution: 0\n");
            output.append("Final level cap: ").append(Config.threatScalingFinalLevelCap).append('\n');
            output.append("Changes: 0\n");
            output.append("Health adjusted: false\n");
        }
        send(source, output.toString());
        return 1;
    }

    private static int executeSpawnZombie(CommandSourceStack source, SpawnVariant variant, int count) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }

        int safeCount = Mth.clamp(count, 1, 32);
        int spawned = 0;
        BlockPos anchor = player.blockPosition();
        for (int i = 0; i < safeCount; i++) {
            BlockPos spawnPos = resolveSpawnPos(player, i);
            var zombie = EntityType.ZOMBIE.create(player.serverLevel());
            if (zombie == null) {
                continue;
            }

            applySpawnVariant(zombie, variant);
            zombie.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, player.getRandom().nextFloat() * 360.0F, 0.0F);
            zombie.finalizeSpawn(player.serverLevel(), player.serverLevel().getCurrentDifficultyAt(spawnPos), MobSpawnType.COMMAND, null);
            applySpawnVariant(zombie, variant);
            zombie.setTarget(player);

            if (player.serverLevel().addFreshEntity(zombie)) {
                spawned++;
            }
        }

        int spawnedCount = spawned;
        String variantName = variant.name().toLowerCase(Locale.ROOT);
        source.sendSuccess(
            () -> Component.literal("Spawned " + spawnedCount + " zombie(s) variant=" + variantName + " near " + anchor),
            false
        );
        return spawned > 0 ? 1 : 0;
    }

    private static int executeHordeClimbDebug(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }

        ZombieTargetSearchResult targetSearch = findZombieTarget(player, 8.0D, Config.debugZombieCommandFallbackRadius);
        StringBuilder output = new StringBuilder();
        output.append("Zlogic Horde Climb Debug\n");
        output.append("enableZombieHordeClimb: ").append(Config.enableZombieHordeClimb).append('\n');
        output.append("hordeClimbOnlyEligibleZombies: ").append(Config.hordeClimbOnlyEligibleZombies).append('\n');
        output.append("hordeClimbMinZombies: ").append(Config.hordeClimbMinZombies).append('\n');
        output.append("hordeClimbRadius: ").append(formatDouble(Config.hordeClimbRadius)).append('\n');
        output.append("hordeClimbBaseHeight: ").append(formatDouble(Config.hordeClimbBaseHeight)).append('\n');
        output.append("hordeClimbHeightPerZombie: ").append(formatDouble(Config.hordeClimbHeightPerZombie)).append('\n');
        output.append("hordeClimbMaxHeight: ").append(formatDouble(Config.hordeClimbMaxHeight)).append('\n');
        output.append("hordeClimbCooldownTicks: ").append(Config.hordeClimbCooldownTicks).append('\n');
        output.append("hordeClimbCheckIntervalTicks: ").append(Config.hordeClimbCheckIntervalTicks).append('\n');
        output.append("hordeClimbObstacleCheckDistance: ").append(formatDouble(Config.hordeClimbObstacleCheckDistance)).append('\n');
        output.append("hordeClimbMinObstacleHeight: ").append(Config.hordeClimbMinObstacleHeight).append('\n');
        output.append("hordeClimbMaxObstacleScanHeight: ").append(Config.hordeClimbMaxObstacleScanHeight).append('\n');
        output.append("hordeClimbVerticalBoostBase: ").append(formatDouble(Config.hordeClimbVerticalBoostBase)).append('\n');
        output.append("hordeClimbVerticalBoostPerHeight: ").append(formatDouble(Config.hordeClimbVerticalBoostPerHeight)).append('\n');
        output.append("hordeClimbForwardBoost: ").append(formatDouble(Config.hordeClimbForwardBoost)).append('\n');
        output.append("hordeClimbRequireTarget: ").append(Config.hordeClimbRequireTarget).append('\n');
        output.append("hordeClimbWorksAgainstPlayers: ").append(Config.hordeClimbWorksAgainstPlayers).append('\n');
        output.append("hordeClimbWorksAgainstMachines: ").append(Config.hordeClimbWorksAgainstMachines).append('\n');
        output.append("hordeClimbAttemptChance: ").append(formatDouble(Config.hordeClimbAttemptChance)).append('\n');
        output.append("hordeClimbParticles: ").append(Config.hordeClimbParticles).append('\n');
        output.append("hordeClimbSounds: ").append(Config.hordeClimbSounds).append('\n');
        output.append("hordeClimbGroupVisual: ").append(Config.hordeClimbGroupVisual).append('\n');
        output.append("enableHordeClimbBodyStackAssist: ").append(Config.enableHordeClimbBodyStackAssist).append('\n');
        output.append("hordeClimbBodyStackMinZombies: ").append(Config.hordeClimbBodyStackMinZombies).append('\n');
        output.append("hordeClimbBodyStackRadius: ").append(formatDouble(Config.hordeClimbBodyStackRadius)).append('\n');
        output.append("hordeClimbBodyStackVerticalBoostMin: ").append(formatDouble(Config.hordeClimbBodyStackVerticalBoostMin)).append('\n');
        output.append("hordeClimbBodyStackVerticalBoostMax: ").append(formatDouble(Config.hordeClimbBodyStackVerticalBoostMax)).append('\n');
        output.append("hordeClimbBodyStackForwardBoost: ").append(formatDouble(Config.hordeClimbBodyStackForwardBoost)).append('\n');
        output.append("hordeClimbBodyStackCooldownTicks: ").append(Config.hordeClimbBodyStackCooldownTicks).append('\n');
        output.append("hordeClimbBodyStackRequireTargetAbove: ").append(Config.hordeClimbBodyStackRequireTargetAbove).append('\n');
        output.append("hordeClimbBodyStackMinTargetHeightDiff: ").append(formatDouble(Config.hordeClimbBodyStackMinTargetHeightDiff)).append('\n');
        output.append("hordeClimbBodyStackMaxExtraHeight: ").append(formatDouble(Config.hordeClimbBodyStackMaxExtraHeight)).append('\n');
        output.append("hordeClimbBodyStackOnlyWhenBlocked: ").append(Config.hordeClimbBodyStackOnlyWhenBlocked).append('\n');
        output.append("hordeClimbBodyStackChance: ").append(formatDouble(Config.hordeClimbBodyStackChance)).append('\n');
        output.append("hordeClimbBodyStackApplySlowFallingTicks: ").append(Config.hordeClimbBodyStackApplySlowFallingTicks).append('\n');
        output.append("enableHordeClimbTargetElevationMode: ").append(Config.enableHordeClimbTargetElevationMode).append('\n');
        output.append("hordeClimbTargetMinYDifference: ").append(formatDouble(Config.hordeClimbTargetMinYDifference)).append('\n');
        output.append("hordeClimbTargetMinBlockYDifference: ").append(Config.hordeClimbTargetMinBlockYDifference).append('\n');
        output.append("hordeClimbTargetHorizontalRange: ").append(formatDouble(Config.hordeClimbTargetHorizontalRange)).append('\n');
        output.append("hordeClimbTargetHorizontalMinRange: ").append(formatDouble(Config.hordeClimbTargetHorizontalMinRange)).append('\n');
        output.append("hordeClimbTargetModeRequireObstacleBetween: ").append(Config.hordeClimbTargetModeRequireObstacleBetween).append('\n');
        output.append("hordeClimbIgnoreSmallTerrainSteps: ").append(Config.hordeClimbIgnoreSmallTerrainSteps).append('\n');
        output.append("hordeClimbTargetModeMinObstacleHeight: ").append(Config.hordeClimbTargetModeMinObstacleHeight).append('\n');
        output.append("hordeClimbUseTargetDirectionForBoost: ").append(Config.hordeClimbUseTargetDirectionForBoost).append('\n');
        output.append("hordeClimbLeaderOnly: ").append(Config.hordeClimbLeaderOnly).append('\n');
        output.append("hordeClimbLeaderCheckRadius: ").append(formatDouble(Config.hordeClimbLeaderCheckRadius)).append('\n');
        output.append("hordeClimbAllowPillarClimb: ").append(Config.hordeClimbAllowPillarClimb).append('\n');
        output.append("hordeClimbPillarMinHeight: ").append(Config.hordeClimbPillarMinHeight).append('\n');
        output.append("hordeClimbVerboseDebug: ").append(Config.hordeClimbVerboseDebug).append('\n');

        if (targetSearch != null && targetSearch.entity() instanceof net.minecraft.world.entity.monster.Zombie zombie) {
            HordeClimbHelper.HordeClimbAssessment assessment = HordeClimbHelper.inspect(player.serverLevel(), zombie);
            output.append("Target mode: ").append(targetSearch.exactHit() ? "raytrace" : "fallback").append('\n');
            output.append("Fallback zombie count: ").append(targetSearch.fallbackCount()).append('\n');
            output.append("Zombie type: ").append(zombie.getType().toShortString()).append('\n');
            output.append("eligible_for_horde_climb: ").append(HordeClimbHelper.isEligibleZombie(zombie)).append('\n');
            output.append("current group size: ").append(assessment.groupSize()).append('\n');
            output.append("calculated height: ").append(formatDouble(assessment.calculatedHeight())).append('\n');
            output.append("effective height: ").append(formatDouble(assessment.effectiveHeight())).append('\n');
            output.append("target Y: ").append(formatDouble(assessment.targetY())).append('\n');
            output.append("zombie Y: ").append(formatDouble(assessment.zombieY())).append('\n');
            output.append("Y difference: ").append(formatDouble(assessment.yDifference())).append('\n');
            output.append("horizontal distance: ").append(formatDouble(assessment.horizontalDistance())).append('\n');
            output.append("is leader: ").append(assessment.leader()).append('\n');
            output.append("obstacle detected: ").append(assessment.obstacleModeActive()).append('\n');
            output.append("obstacle mode result: ").append(assessment.obstacleModeActive()).append(" reason=").append(assessment.obstacleReason()).append('\n');
            output.append("target elevation mode result: ").append(assessment.targetElevationModeActive()).append(" reason=").append(assessment.targetElevationReason()).append('\n');
            output.append("target obstacle between: ").append(assessment.targetObstacleBetween()).append('\n');
            output.append("target obstacle height: ").append(assessment.targetObstacleHeight()).append('\n');
            output.append("obstacle height: ").append(assessment.obstacleHeight()).append('\n');
            output.append("obstacle pos: ").append(assessment.obstaclePos() == null ? "none" : assessment.obstaclePos()).append('\n');
            output.append("has target: ").append(assessment.hasTarget()).append('\n');
            output.append("target allowed: ").append(assessment.targetAllowed()).append('\n');
            output.append("target kind: ").append(assessment.targetKind()).append('\n');
            output.append("body stack supports: ").append(HordeBodyStackAssistHelper.countNearbySupportZombies(player.serverLevel(), zombie)).append('\n');
            output.append("body stack blocked: ").append(HordeBodyStackAssistHelper.isBlockedOrPressingObstacle(player.serverLevel(), zombie, HordeClimbHelper.resolveTargetContext(player.serverLevel(), zombie))).append('\n');
            output.append("body stack cooldown remaining: ").append(HordeBodyStackAssistHelper.getCooldownRemaining(zombie, player.serverLevel().getGameTime())).append('\n');
            output.append("last activation mode: ").append(HordeClimbHelper.getLastActivationMode(zombie)).append('\n');
            output.append("reason: ").append(assessment.reason()).append('\n');
            output.append("cooldown remaining: ").append(assessment.cooldownRemaining()).append('\n');
            output.append("last group size: ").append(HordeClimbHelper.getLastGroupSize(zombie)).append('\n');
            output.append("last height: ").append(formatDouble(HordeClimbHelper.getLastHeight(zombie))).append('\n');
            output.append("last tick: ").append(HordeClimbHelper.getLastTick(zombie)).append('\n');
        } else {
            output.append("Target mode: none\n");
            output.append("Fallback zombie count: ").append(targetSearch == null ? 0 : targetSearch.fallbackCount()).append('\n');
            output.append("Target: no zombie found within reach or fallback radius\n");
        }

        send(source, output.toString());
        return 1;
    }

    private static int executeBarrierBreakDebug(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }

        HitResult hitResult = player.pick(8.0D, 0.0F, false);
        StringBuilder output = new StringBuilder();
        output.append("Zlogic Barrier Break Debug\n");
        output.append("enableZombieBarrierBreak: ").append(Config.enableZombieBarrierBreak).append('\n');
        output.append("barrierBreakOnlyEligibleZombies: ").append(Config.barrierBreakOnlyEligibleZombies).append('\n');
        output.append("barrierBreakMinZombies: ").append(Config.barrierBreakMinZombies).append('\n');
        output.append("barrierBreakZombieRadius: ").append(formatDouble(Config.barrierBreakZombieRadius)).append('\n');
        output.append("barrierBreakCheckIntervalTicks: ").append(Config.barrierBreakCheckIntervalTicks).append('\n');
        output.append("barrierBreakZombieCooldownTicks: ").append(Config.barrierBreakZombieCooldownTicks).append('\n');
        output.append("barrierBreakBlockCooldownTicks: ").append(Config.barrierBreakBlockCooldownTicks).append('\n');
        output.append("barrierBreakRequireTarget: ").append(Config.barrierBreakRequireTarget).append('\n');
        output.append("barrierBreakWorksAgainstPlayers: ").append(Config.barrierBreakWorksAgainstPlayers).append('\n');
        output.append("barrierBreakWorksAgainstMachines: ").append(Config.barrierBreakWorksAgainstMachines).append('\n');
        output.append("barrierBreakWoodenDoors: ").append(Config.barrierBreakWoodenDoors).append('\n');
        output.append("barrierBreakWoodenTrapdoors: ").append(Config.barrierBreakWoodenTrapdoors).append('\n');
        output.append("barrierBreakWoodenFenceGates: ").append(Config.barrierBreakWoodenFenceGates).append('\n');
        output.append("barrierBreakGlassBlocks: ").append(Config.barrierBreakGlassBlocks).append('\n');
        output.append("barrierBreakGlassPanes: ").append(Config.barrierBreakGlassPanes).append('\n');
        output.append("barrierBreakStainedGlass: ").append(Config.barrierBreakStainedGlass).append('\n');
        output.append("barrierBreakStainedGlassPanes: ").append(Config.barrierBreakStainedGlassPanes).append('\n');
        output.append("barrierBreakTintedGlass: ").append(Config.barrierBreakTintedGlass).append('\n');
        output.append("barrierBreakRespectBlockDenylist: ").append(Config.barrierBreakRespectBlockDenylist).append('\n');
        output.append("barrierBreakBlockDenylist: ").append(Config.barrierBreakBlockDenylist).append('\n');
        output.append("barrierBreakExtraBlockAllowlist: ").append(Config.barrierBreakExtraBlockAllowlist).append('\n');
        output.append("barrierBreakDamagePerHit: ").append(Config.barrierBreakDamagePerHit).append('\n');
        output.append("barrierBreakWoodenDoorDurability: ").append(Config.barrierBreakWoodenDoorDurability).append('\n');
        output.append("barrierBreakGlassDurability: ").append(Config.barrierBreakGlassDurability).append('\n');
        output.append("barrierBreakGlassPaneDurability: ").append(Config.barrierBreakGlassPaneDurability).append('\n');
        output.append("barrierBreakExtraBlockDurability: ").append(Config.barrierBreakExtraBlockDurability).append('\n');
        output.append("barrierBreakActuallyBreakBlocks: ").append(Config.barrierBreakActuallyBreakBlocks).append('\n');
        output.append("barrierBreakDropBlocks: ").append(Config.barrierBreakDropBlocks).append('\n');
        output.append("barrierBreakParticles: ").append(Config.barrierBreakParticles).append('\n');
        output.append("barrierBreakSounds: ").append(Config.barrierBreakSounds).append('\n');
        output.append("barrierBreakDebugLogs: ").append(Config.barrierBreakDebugLogs).append('\n');
        output.append("barrierBreakVerboseDebug: ").append(Config.barrierBreakVerboseDebug).append('\n');
        output.append("dimension current: ").append(player.serverLevel().dimension().location()).append('\n');
        output.append("difficulty current: ").append(player.serverLevel().getDifficulty()).append('\n');

        if (!(hitResult instanceof BlockHitResult blockHitResult)) {
            output.append("Target: no block hit within 8 blocks\n");
            send(source, output.toString());
            return 1;
        }

        BlockPos pos = blockHitResult.getBlockPos();
        ZombieBarrierBreakHelper.BarrierBlockInspection inspection = ZombieBarrierBreakHelper.inspectBlock(player.serverLevel(), pos);
        output.append("Target block: ").append(pos).append('\n');
        output.append("Normalized block: ").append(inspection.normalizedPos()).append('\n');
        output.append("Block id: ").append(inspection.blockId() == null ? "unknown" : inspection.blockId()).append('\n');
        output.append("Block class: ").append(inspection.blockClass()).append('\n');
        output.append("Detected type: ").append(inspection.barrierKind()).append('\n');
        output.append("Can break: ").append(inspection.canBreak()).append('\n');
        output.append("Supported: ").append(inspection.supported()).append('\n');
        output.append("In denylist: ").append(inspection.denylisted()).append('\n');
        output.append("Tinted glass blocked: ").append(inspection.tintedBlocked()).append('\n');
        output.append("Block facing: ");
        if (inspection.state() != null && inspection.state().getBlock() instanceof net.minecraft.world.level.block.DoorBlock doorBlock && inspection.state().hasProperty(net.minecraft.world.level.block.DoorBlock.FACING)) {
            output.append(inspection.state().getValue(net.minecraft.world.level.block.DoorBlock.FACING));
        } else {
            output.append("n/a");
        }
        output.append('\n');
        output.append("Required front side enabled: ").append(Config.barrierBreakRequireFrontSide).append('\n');
        output.append("Target behind barrier required: ").append(Config.barrierBreakRequireTargetBehindBarrier).append('\n');
        output.append("Eligible zombies nearby: ").append(inspection.eligibleZombieCount()).append('\n');
        output.append("Eligible zombies on valid attack side: ").append(inspection.attackSideZombieCount()).append('\n');
        output.append("Zombies rejected by side: ").append(Math.max(0, inspection.eligibleZombieCount() - inspection.attackSideZombieCount())).append('\n');
        output.append("Barrier side debug: ").append(Config.barrierBreakSideDebug).append('\n');
        output.append("Durability: ").append(inspection.durability()).append('\n');
        output.append("Current damage: ").append(inspection.currentDamage()).append('\n');
        output.append("Reason: ").append(inspection.reason()).append('\n');

        send(source, output.toString());
        return 1;
    }

    private static int executeBloodDebug(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }

        ZombieTargetSearchResult targetSearch = findZombieTarget(player, 8.0D, Config.debugZombieCommandFallbackRadius);
        StringBuilder output = new StringBuilder();
        output.append("Zlogic Blood Sense Debug\n");
        output.append("enableBloodSense: ").append(Config.enableBloodSense).append('\n');
        output.append("bloodSenseRadius: ").append(formatDouble(Config.bloodSenseRadius)).append('\n');
        output.append("bloodTraceLifetimeTicks: ").append(Config.bloodTraceLifetimeTicks).append('\n');
        output.append("bloodTraceMaxEventsGlobal: ").append(Config.bloodTraceMaxEventsGlobal).append('\n');
        output.append("bloodTraceMaxEventsPerChunk: ").append(Config.bloodTraceMaxEventsPerChunk).append('\n');
        output.append("bloodTraceMinDamage: ").append(formatDouble(Config.bloodTraceMinDamage)).append('\n');
        output.append("bloodTraceBaseIntensity: ").append(formatDouble(Config.bloodTraceBaseIntensity)).append('\n');
        output.append("bloodTraceDamageIntensityMultiplier: ").append(formatDouble(Config.bloodTraceDamageIntensityMultiplier)).append('\n');
        output.append("bloodTraceLowHealthBonus: ").append(Config.bloodTraceLowHealthBonus).append('\n');
        output.append("bloodTraceLowHealthThreshold: ").append(formatDouble(Config.bloodTraceLowHealthThreshold)).append('\n');
        output.append("bloodTraceLowHealthBonusIntensity: ").append(formatDouble(Config.bloodTraceLowHealthBonusIntensity)).append('\n');
        output.append("bloodTraceRainDecayMultiplier: ").append(formatDouble(Config.bloodTraceRainDecayMultiplier)).append('\n');
        output.append("bloodTraceWaterDecayMultiplier: ").append(formatDouble(Config.bloodTraceWaterDecayMultiplier)).append('\n');
        output.append("bloodTraceAttractPlayers: ").append(Config.bloodTraceAttractPlayers).append('\n');
        output.append("bloodTraceAttractMobs: ").append(Config.bloodTraceAttractMobs).append('\n');
        output.append("bloodTraceOnlyAttractZombies: ").append(Config.bloodTraceOnlyAttractZombies).append('\n');
        output.append("bloodTraceInvestigationSpeed: ").append(formatDouble(Config.bloodTraceInvestigationSpeed)).append('\n');
        output.append("bloodTraceScanIntervalTicks: ").append(Config.bloodTraceScanIntervalTicks).append('\n');
        output.append("bloodTraceMinScore: ").append(formatDouble(Config.bloodTraceMinScore)).append('\n');
        output.append("enableLowHealthBloodTrail: ").append(Config.enableLowHealthBloodTrail).append('\n');
        output.append("lowHealthBloodTrailIntervalTicks: ").append(Config.lowHealthBloodTrailIntervalTicks).append('\n');
        output.append("lowHealthBloodTrailIntensity: ").append(formatDouble(Config.lowHealthBloodTrailIntensity)).append('\n');
        output.append("bloodTraceDebug: ").append(Config.bloodTraceDebug).append('\n');
        output.append("active traces in level: ").append(BloodTraceManager.getActiveTraceCount(player.serverLevel())).append('\n');
        output.append("active traces global: ").append(BloodTraceManager.getActiveTraceCountGlobal()).append('\n');

        BloodTrace lastTrace = BloodTraceManager.getLastTrace(player.serverLevel());
        output.append("last trace pos: ").append(lastTrace == null ? "none" : lastTrace.pos()).append('\n');
        output.append("last trace intensity: ").append(lastTrace == null ? "none" : formatDouble(lastTrace.intensity())).append('\n');
        output.append("last trace fromPlayer: ").append(lastTrace != null && lastTrace.fromPlayer()).append('\n');

        SenseTarget nearestTrace = BloodTraceManager.findBestTrace(player.serverLevel(), player.blockPosition(), Config.bloodSenseRadius);
        output.append("nearest trace pos: ").append(nearestTrace == null ? "none" : nearestTrace.pos()).append('\n');
        output.append("nearest trace score: ").append(nearestTrace == null ? "none" : formatDouble(nearestTrace.score())).append('\n');

        if (targetSearch != null && targetSearch.entity() instanceof net.minecraft.world.entity.monster.Zombie zombie) {
            output.append("zombie blood target pos: ").append(ZombieSenseManager.getStoredBloodTarget(zombie)).append('\n');
            output.append("zombie blood target until: ").append(ZombieSenseManager.getBloodTargetUntilTick(zombie)).append('\n');
        }

        send(source, output.toString());
        return 1;
    }

    private static int executeLightDebug(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }

        ZombieTargetSearchResult targetSearch = findZombieTarget(player, 8.0D, Config.debugZombieCommandFallbackRadius);
        StringBuilder output = new StringBuilder();
        output.append("Zlogic Light Sense Debug\n");
        output.append("enableLightSense: ").append(Config.enableLightSense).append('\n');
        output.append("lightSenseRadius: ").append(formatDouble(Config.lightSenseRadius)).append('\n');
        output.append("lightInterestLifetimeTicks: ").append(Config.lightInterestLifetimeTicks).append('\n');
        output.append("lightInterestMaxEventsGlobal: ").append(Config.lightInterestMaxEventsGlobal).append('\n');
        output.append("lightInterestMaxEventsPerChunk: ").append(Config.lightInterestMaxEventsPerChunk).append('\n');
        output.append("lightSenseOnlyInDarkness: ").append(Config.lightSenseOnlyInDarkness).append('\n');
        output.append("lightSenseMaxSkyLight: ").append(Config.lightSenseMaxSkyLight).append('\n');
        output.append("lightSenseMinBlockLight: ").append(Config.lightSenseMinBlockLight).append('\n');
        output.append("lightSenseReactToPlacedLights: ").append(Config.lightSenseReactToPlacedLights).append('\n');
        output.append("lightSenseReactToRedstoneLights: ").append(Config.lightSenseReactToRedstoneLights).append('\n');
        output.append("lightSenseReactToFlicker: ").append(Config.lightSenseReactToFlicker).append('\n');
        output.append("lightSenseReactToMachineLight: ").append(Config.lightSenseReactToMachineLight).append('\n');
        output.append("lightSenseReactToHeldLights: ").append(Config.lightSenseReactToHeldLights).append('\n');
        output.append("lightPlacedIntensity: ").append(formatDouble(Config.lightPlacedIntensity)).append('\n');
        output.append("lightFlickerIntensity: ").append(formatDouble(Config.lightFlickerIntensity)).append('\n');
        output.append("lightRedstoneIntensity: ").append(formatDouble(Config.lightRedstoneIntensity)).append('\n');
        output.append("lightMachineIntensity: ").append(formatDouble(Config.lightMachineIntensity)).append('\n');
        output.append("lightHeldIntensity: ").append(formatDouble(Config.lightHeldIntensity)).append('\n');
        output.append("lightSenseInvestigationSpeed: ").append(formatDouble(Config.lightSenseInvestigationSpeed)).append('\n');
        output.append("lightSenseScanIntervalTicks: ").append(Config.lightSenseScanIntervalTicks).append('\n');
        output.append("lightSenseMinScore: ").append(formatDouble(Config.lightSenseMinScore)).append('\n');
        output.append("lightInterestSamePosCooldownTicks: ").append(Config.lightInterestSamePosCooldownTicks).append('\n');
        output.append("lightSenseDebug: ").append(Config.lightSenseDebug).append('\n');
        output.append("active interests in level: ").append(LightInterestManager.getActiveInterestCount(player.serverLevel())).append('\n');
        output.append("active interests global: ").append(LightInterestManager.getActiveInterestCountGlobal()).append('\n');

        LightInterest lastInterest = LightInterestManager.getLastInterest(player.serverLevel());
        output.append("last light pos: ").append(lastInterest == null ? "none" : lastInterest.pos()).append('\n');
        output.append("last light type: ").append(lastInterest == null ? "none" : lastInterest.type()).append('\n');
        output.append("last light intensity: ").append(lastInterest == null ? "none" : formatDouble(lastInterest.intensity())).append('\n');
        output.append("last light machineRelated: ").append(lastInterest != null && lastInterest.machineRelated()).append('\n');

        SenseTarget nearestInterest = LightInterestManager.findBestInterest(player.serverLevel(), player.blockPosition(), Config.lightSenseRadius);
        output.append("nearest light pos: ").append(nearestInterest == null ? "none" : nearestInterest.pos()).append('\n');
        output.append("nearest light score: ").append(nearestInterest == null ? "none" : formatDouble(nearestInterest.score())).append('\n');
        output.append("nearest light type: ").append(nearestInterest == null || nearestInterest.lightInterest() == null ? "none" : nearestInterest.lightInterest().type()).append('\n');

        if (targetSearch != null && targetSearch.entity() instanceof net.minecraft.world.entity.monster.Zombie zombie) {
            output.append("zombie light target pos: ").append(ZombieSenseManager.getStoredLightTarget(zombie)).append('\n');
            output.append("zombie light target until: ").append(ZombieSenseManager.getLightTargetUntilTick(zombie)).append('\n');
        }

        send(source, output.toString());
        return 1;
    }

    private static int executeNightSpawnDebug(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }

        ServerPlayer serverPlayer = player;
        StringBuilder output = new StringBuilder();
        output.append("Zlogic Night Spawn Debug\n");
        output.append("enableNightZombieSpawns: ").append(Config.enableNightZombieSpawns).append('\n');
        output.append("nightSpawnIntervalTicks: ").append(Config.nightSpawnIntervalTicks).append('\n');
        output.append("nightSpawnChance: ").append(formatDouble(Config.nightSpawnChance)).append('\n');
        output.append("nightSpawnMinGroupSize: ").append(Config.nightSpawnMinGroupSize).append('\n');
        output.append("nightSpawnMaxGroupSize: ").append(Config.nightSpawnMaxGroupSize).append('\n');
        output.append("nightSpawnRadiusMin: ").append(Config.nightSpawnRadiusMin).append('\n');
        output.append("nightSpawnRadiusMax: ").append(Config.nightSpawnRadiusMax).append('\n');
        output.append("nightSpawnMaxNearbyZombies: ").append(Config.nightSpawnMaxNearbyZombies).append('\n');
        output.append("nightSpawnNearbyCheckRadius: ").append(Config.nightSpawnNearbyCheckRadius).append('\n');
        output.append("nightSpawnOnlyAtNight: ").append(Config.nightSpawnOnlyAtNight).append('\n');
        output.append("nightSpawnAllowDuringThunder: ").append(Config.nightSpawnAllowDuringThunder).append('\n');
        output.append("nightSpawnRespectDoMobSpawning: ").append(Config.nightSpawnRespectDoMobSpawning).append('\n');
        output.append("nightSpawnRequireDarkness: ").append(Config.nightSpawnRequireDarkness).append('\n');
        output.append("nightSpawnMaxLightLevel: ").append(Config.nightSpawnMaxLightLevel).append('\n');
        output.append("nightSpawnedZombiesIgnoreSunBurn: ").append(Config.nightSpawnedZombiesIgnoreSunBurn).append('\n');
        output.append("nightSpawnAllowedDimensions: ").append(Config.nightSpawnAllowedDimensions).append('\n');
        output.append("dimension current: ").append(serverPlayer.serverLevel().dimension().location()).append('\n');
        output.append("difficulty current: ").append(serverPlayer.serverLevel().getDifficulty()).append('\n');
        output.append("doMobSpawning current: ").append(serverPlayer.serverLevel().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING)).append('\n');
        output.append("last attempt tick: ").append(NightZombieSpawnController.getLastAttemptTick()).append('\n');
        output.append("last spawned count: ").append(NightZombieSpawnController.getLastSpawnedCount()).append('\n');
        output.append("last failure reason: ").append(NightZombieSpawnController.getLastFailureReason()).append('\n');

        send(source, output.toString());
        return 1;
    }

    private static int executeForceNightSpawn(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }

        NightZombieSpawnController.NightSpawnAttemptResult result = NightZombieSpawnController.forceSpawnNearPlayer(player);
        StringBuilder output = new StringBuilder();
        output.append("Zlogic Force Night Spawn\n");
        output.append("success: ").append(result.success()).append('\n');
        output.append("spawnedCount: ").append(result.spawnedCount()).append('\n');
        output.append("reason: ").append(result.reason()).append('\n');
        output.append("basePos: ").append(result.basePos() == null ? "none" : result.basePos()).append('\n');
        output.append("dimension: ").append(player.serverLevel().dimension().location()).append('\n');

        send(source, output.toString());
        return 1;
    }

    private static int executePerformanceDebug(CommandSourceStack source) {
        PerformanceTracker.PerformanceSnapshot snapshot = PerformanceTracker.snapshot();
        StringBuilder output = new StringBuilder();
        output.append("Zlogic Performance Debug\n");
        output.append("entities processed: ").append(snapshot.entitiesProcessed()).append('\n');
        output.append("zombies processed: ").append(snapshot.zombiesProcessed()).append('\n');
        output.append("machine scans: ").append(snapshot.machineScans()).append('\n');
        output.append("block positions checked: ").append(snapshot.blockPositionsChecked()).append('\n');
        output.append("noise checks: ").append(snapshot.noiseChecks()).append('\n');
        output.append("survival scaling applications: ").append(snapshot.survivalScalingApplications()).append('\n');
        output.append("threat join applications: ").append(snapshot.threatJoinApplications()).append('\n');
        output.append("threat fallback applications: ").append(snapshot.threatFallbackApplications()).append('\n');
        output.append("threat command rescales: ").append(snapshot.threatCommandRescales()).append('\n');
        output.append("aggression checks: ").append(snapshot.aggressionChecks()).append('\n');
        output.append("last reset tick: ").append(snapshot.lastResetTick());
        send(source, output.toString());
        return 1;
    }

    private static String formatBlockId(net.minecraft.resources.ResourceLocation blockId) {
        return blockId == null ? "unknown" : blockId.toString();
    }

    private static String formatNamespace(net.minecraft.resources.ResourceLocation blockId) {
        return blockId == null ? "unknown" : blockId.getNamespace();
    }

    private static String formatNoiseDistance(BlockPos pos, NoiseEvent noise) {
        if (pos == null || noise == null) {
            return "none";
        }

        double dx = (pos.getX() + 0.5D) - (noise.pos().getX() + 0.5D);
        double dy = (pos.getY() + 0.5D) - (noise.pos().getY() + 0.5D);
        double dz = (pos.getZ() + 0.5D) - (noise.pos().getZ() + 0.5D);
        return formatDouble(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static BlockPos resolveSpawnPos(ServerPlayer player, int index) {
        BlockPos base = player.blockPosition().offset(2 + (index % 4), 0, 2 + (index / 4));
        int topY = player.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX(), base.getZ());
        int clampedY = Math.max(player.serverLevel().getMinBuildHeight() + 1, topY + 1);
        return new BlockPos(base.getX(), clampedY, base.getZ());
    }

    private static void applySpawnVariant(net.minecraft.world.entity.monster.Zombie zombie, SpawnVariant variant) {
        if (zombie == null || variant == null) {
            return;
        }

        switch (variant) {
            case VANILLA -> {
            }
            case ZLOGIC -> ZombieMarkingHelper.markZlogicSpawned(zombie);
            case DAY -> ZombieMarkingHelper.markDaySpawned(zombie);
            case NIGHT -> ZombieMarkingHelper.markNightSpawned(zombie);
            case TANK -> {
                ZombieMarkingHelper.markZlogicSpawned(zombie);
                zombie.getPersistentData().putBoolean(ZombieTankHandler.TANK_ZOMBIE_KEY, true);
            }
        }
    }

    private static ZombieTargetSearchResult findZombieTarget(ServerPlayer player, double reach, double fallbackRadius) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(reach));
        AABB rayBox = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D);

        var exactResult = ProjectileUtil.getEntityHitResult(
            player.serverLevel(),
            player,
            eye,
            end,
            rayBox,
            entity -> entity != null && entity.isAlive() && ZombieFamilyHelper.isZombieFamily(entity),
            (float) reach
        );

        if (exactResult != null && exactResult.getEntity() instanceof LivingEntity living && ZombieFamilyHelper.isZombieFamily(living)) {
            return new ZombieTargetSearchResult(living, true, 0);
        }

        int fallbackCount = 0;
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        AABB fallbackBox = new AABB(
            eye.x - fallbackRadius,
            eye.y - fallbackRadius,
            eye.z - fallbackRadius,
            eye.x + fallbackRadius,
            eye.y + fallbackRadius,
            eye.z + fallbackRadius
        ).inflate(0.5D);

        for (LivingEntity candidate : player.serverLevel().getEntitiesOfClass(LivingEntity.class, fallbackBox, ZombieFamilyHelper::isZombieFamily)) {
            fallbackCount++;
            Vec3 toCandidate = candidate.getBoundingBox().getCenter().subtract(eye);
            double distanceSq = toCandidate.lengthSqr();
            if (distanceSq <= 0.0001D) {
                return new ZombieTargetSearchResult(candidate, false, fallbackCount);
            }

            Vec3 normalized = toCandidate.normalize();
            double alignment = normalized.dot(look);
            double forward = Math.max(0.0D, toCandidate.dot(look));
            double score = distanceSq + Math.max(0.0D, 1.0D - alignment) * 6.0D - forward * 0.25D;
            if (alignment < 0.0D) {
                score += 10.0D;
            }

            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best != null ? new ZombieTargetSearchResult(best, false, fallbackCount) : new ZombieTargetSearchResult(null, false, fallbackCount);
    }

    private record ZombieTargetSearchResult(LivingEntity entity, boolean exactHit, int fallbackCount) {
    }

    private enum SpawnVariant {
        VANILLA,
        ZLOGIC,
        DAY,
        NIGHT,
        TANK
    }

    private static String describeNoiseGap(
        MicroTechNoiseHandler.MachineInspection inspection,
        MachineAttackHandler.MachineAttackAssessment attackAssessment,
        NoiseEvent nearestMachineNoise,
        int cooldownRemaining,
        Long lastScanTick,
        long serverTick
    ) {
        if (!inspection.wouldEmitNoise()) {
            return "machine would not emit";
        }

        if (attackAssessment.directDetectionEnabled()) {
            if (attackAssessment.attackable()) {
                return "direct detection active";
            }

            return attackAssessment.reason();
        }

        if (cooldownRemaining > 0) {
            return "waiting cooldown";
        }

        if (lastScanTick == null) {
            return "handler has not scanned yet";
        }

        if (nearestMachineNoise == null) {
            if (serverTick - lastScanTick < Math.max(1, Config.microTechMachineNoiseIntervalTicks)) {
                return "waiting scan interval";
            }

            if (!attackAssessment.hasRecentMachineNoise()) {
                return "no stored MACHINE noise found";
            }
        }

        if (attackAssessment.hasRecentMachineNoise()) {
            return "recent MACHINE noise present";
        }

        return "noise expired or machine outside scan";
    }

    private static void send(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }
}
