package com.redsmods.common.entity;

import com.redsmods.common.SpiralStructureBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.stream.Collectors;

enum PHASE {
    DEACTIVATED_IDOL,
    ARENA_BUILDING,
    ACTIVATED_IDOL,
    TRANSITION_TO_RADIANCE,
    RADIANCE,
    TRANSITION_TO_TRUE,
    TRUE_RADIANCE
}

public class Radiance extends Monster {
    private SpiralStructureBuilder arenaBuilder;
    private float floatTimer = 0.0F;
    private double groundY = -1; // Store the ground level
    private PHASE state = PHASE.DEACTIVATED_IDOL;
    private final PHASE[] invulnerablePhases = {PHASE.DEACTIVATED_IDOL,PHASE.ARENA_BUILDING,PHASE.TRANSITION_TO_RADIANCE,PHASE.TRANSITION_TO_TRUE};

    // Boss bar
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.literal("The Radiance"),
            BossEvent.BossBarColor.YELLOW,
            BossEvent.BossBarOverlay.PROGRESS
    );

    // Air slam attack Fields
    private final Map<UUID, Integer> playerAirTime = new HashMap<>();
    private static final int AIR_SLAM_THRESHOLD = 40; // 2 seconds at 20 ticks per second
    private static final double SLAM_RANGE = 128.0; // Range to detect and slam players
    private final Map<UUID, Integer> playerWarningTime = new HashMap<>();
    private static final int WARNING_DURATION = 10; // 0.5 seconds warning before slam
    private static final int UPDATED_AIR_SLAM_THRESHOLD = 20; // Increased to 1 seconds to account for warning

    // Knockback Fields
    private int knockbackChargeTimer = 0;
    private static final int KNOCKBACK_WINDUP_TIME = 5; // 0.25 seconds windup before starting charge
    private static final int KNOCKBACK_CHARGE_TIME = 30; // 1.5 seconds charge time
    private static final int KNOCKBACK_COOLDOWN = 20*15; // 15 second cooldown between attacks
    private int knockbackCooldownTimer = 0;
    private static final double KNOCKBACK_RANGE = 9.0; // 9 blocks range
    private static final double KNOCKBACK_STRENGTH = 15.0; // Horizontal knockback strength
    private static final double LAUNCH_STRENGTH = 15; // Vertical launch strength
    private boolean isChargingKnockback = false;
    private boolean isWindingUp = false;

    // Giant Spear Attack Fields
    private int spearAttackTimer = 0;
    private static final int SPEAR_WARNING_TIME = 60; // 3 seconds warning
    private static final int SPEAR_SPAWN_TIME = 20; // 1 second for spears to fully emerge
    private static final int SPEAR_ATTACK_COOLDOWN = 20 * 15; // 15 second cooldown
    private int spearAttackCooldown = 0;
    private boolean isSpearAttackActive = false;
    private final Map<BlockPos, Integer> spearPositions = new HashMap<>(); // Position -> height
    private final Set<BlockPos> safeZones = new HashSet<>();
    private static final int SPEAR_HEIGHT = 8; // How tall the spears are
    private static final int SAFE_ZONE_RADIUS = 3; // Radius of safe zones around players

    // explode blocks time
    private final Map<BlockPos, Long> recentBlockPlacements = new HashMap<>();
    private static final int BLOCK_DETECTION_RANGE = 8;
    private static final int BLOCK_PLACEMENT_WINDOW = 100; // 5 seconds in ticks
    private int blockExplosionCooldown = 0;
    private static final int BLOCK_EXPLOSION_COOLDOWN = 20; // 1 seconds between explosions
    private boolean isBlockExplosionActive = false;
    private int blockExplosionTimer = 0;
    private static final int BLOCK_EXPLOSION_WARNING_TIME = 40; // 2 seconds warning
    private final Set<BlockPos> blocksToExplode = new HashSet<>();


    public Radiance(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        // Set boss bar properties
        this.bossEvent.setDarkenScreen(true);
        this.bossEvent.setPlayBossMusic(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 600.0D) // Increased health for boss
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D); // Increased damage for boss
    }

    @Override
    protected void registerGoals() {

    }

    // Make the mob completely invulnerable to all damage
    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        if (damageSource.is(DamageTypes.GENERIC_KILL) || !Arrays.asList(invulnerablePhases).contains(this.state)) {
            boolean result = super.hurt(damageSource, amount);

            // Check if health reached 0 and transition to next phase
            if (this.getHealth() <= 0 && !damageSource.is(DamageTypes.GENERIC_KILL)) {
                transitionToNextPhase();
                return false; // Prevent actual death
            }

            // Update boss bar health
            updateBossBar();
            return result;
        }
        return false; // Always return false to prevent any damage
    }

    private void transitionToNextPhase() {
        PHASE nextPhase = getNextPhase();

        if (nextPhase != null) {
            // Set to transition phase first if applicable
            this.state = nextPhase;

            // Restore full health
            this.setHealth(this.getMaxHealth());

            // Play transition sound
            this.playSound(SoundEvents.BEACON_POWER_SELECT, 2.0F, 0.5F);

            // Make invulnerable during transition phases
            if (Arrays.asList(invulnerablePhases).contains(nextPhase)) {

            }

            // Update boss bar immediately
            updateBossBar();
        }
    }

    private PHASE getNextPhase() {
        return switch (this.state) {
            case ACTIVATED_IDOL -> PHASE.TRANSITION_TO_RADIANCE;
            case RADIANCE -> PHASE.TRANSITION_TO_TRUE;
            case TRUE_RADIANCE -> null; // Final phase, boss actually dies
            default -> null;
        };
    }

    @Override
    public void knockback(double strength, double x, double z) {
        return; // no knockback
    }

    @Override
    public boolean isPushable() {
        return false; // no pushing
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public void push(Entity entity) {
        // no pushing
    }

    @Override
    public void push(double x, double y, double z) {
        // no pushing
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide) {
            if (player.getItemInHand(hand).is(Items.STRUCTURE_VOID) && this.state == PHASE.DEACTIVATED_IDOL) {
                this.state = PHASE.ARENA_BUILDING;

                // Play a sound to indicate the change
                this.playSound(SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.0F);

                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    // Add floating behavior
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return; // only update these things on the serverside

        if (this.state == PHASE.DEACTIVATED_IDOL) {
            // Set ground level on first tick or if not set
            if (groundY == -1) {
                groundY = this.getY();
            }

            // Increment the float timer
            floatTimer += 0.05F; // Slower oscillation

            // Calculate the target Y position (ground + floating offset)
            double floatOffset = Math.sin(floatTimer) * 0.5; // Float 0.5 blocks up/down
            double targetY = groundY + 1.0 + floatOffset; // Hover 1 block above ground + float

            // Set position directly instead of adding motion
            this.setPos(this.getX(), targetY, this.getZ());

            // Prevent gravity
            this.setNoGravity(true);
        } else if (this.state == PHASE.ARENA_BUILDING) {
            if (arenaBuilder == null)
                arenaBuilder = new SpiralStructureBuilder(getServer().overworld(),new BlockPos(getBlockX()+1+4,(int) groundY-9,getBlockZ()+1-3),"/boss_arena.schem",200);
            if (arenaBuilder.tick()) {
                this.state = PHASE.ACTIVATED_IDOL;
                showBossBar();
            }
            this.moveTo(this.getX(), groundY, this.getZ());
        } else if (this.state == PHASE.ACTIVATED_IDOL) {
            // if they are in the air, slam them onto the ground
            performAirSlamAttack();

            // if they are within 9 blocks, knock them back (then slam them into the ground xD)
            performKnockbackAttack();

            // spear to bully people
            performGiantSpearAttack();

            // explode if blocks
            performBlockExplosionAttack();
        }

        // Break blocks within hitbox
        breakBlocksInHitbox();

        // Update boss bar
        updateBossBar();
    }

    private void performGiantSpearAttack() {
        // Handle cooldown
        if (spearAttackCooldown > 0) {
            spearAttackCooldown--;
            return;
        }

        // Find all players in arena range
        List<Player> playersInArena = this.level().getEntitiesOfClass(Player.class,
                        new AABB(this.getX() - 50, this.getY() - 20, this.getZ() - 50,
                                this.getX() + 50, this.getY() + 20, this.getZ() + 50))
                .stream()
                .filter(player -> !player.isCreative() && !player.isSpectator())
                .collect(Collectors.toList());

        if (playersInArena.isEmpty()) {
            return;
        }

        // Start attack if not active
        if (!isSpearAttackActive) {
            startSpearAttack(playersInArena);
            return;
        }

        // Handle active attack phases
        spearAttackTimer++;

        if (spearAttackTimer <= SPEAR_WARNING_TIME) {
            // Warning phase - show where spears will spawn
            showSpearWarnings();
        } else if (spearAttackTimer <= SPEAR_WARNING_TIME + SPEAR_SPAWN_TIME) {
            // Spawning phase - gradually build spears
            spawnSpears();
        } else {
            // Attack finished - clean up and start cooldown
            endSpearAttack();
        }
    }

    private void startSpearAttack(List<Player> players) {
        isSpearAttackActive = true;
        spearAttackTimer = 0;
        spearPositions.clear();
        safeZones.clear();

        // Play attack start sound
        this.playSound(SoundEvents.WITHER_SPAWN, 1.5F, 0.8F);

        // Announce attack to players
        for (Player player : players) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal("§4The ground trembles with ancient power..."));
            }
        }

        // Calculate safe zones around each player
        for (Player player : players) {
            BlockPos playerPos = player.blockPosition();

            // Create safe zone around player
            for (int x = -SAFE_ZONE_RADIUS; x <= SAFE_ZONE_RADIUS; x++) {
                for (int z = -SAFE_ZONE_RADIUS; z <= SAFE_ZONE_RADIUS; z++) {
                    if (x * x + z * z <= SAFE_ZONE_RADIUS * SAFE_ZONE_RADIUS) {
                        BlockPos safePos = playerPos.offset(x, 0, z);
                        safeZones.add(safePos);
                    }
                }
            }
        }

        // Generate spear positions across the arena, avoiding safe zones
        generateSpearPositions();
    }

    private void generateSpearPositions() {
        // Define arena bounds (adjust based on your arena size)
        int arenaRadius = 40;
        BlockPos center = this.blockPosition();

        // Generate spear positions in a pattern
        for (int x = -arenaRadius; x <= arenaRadius; x += 3) { // Every 3 blocks
            for (int z = -arenaRadius; z <= arenaRadius; z += 3) {
                BlockPos spearPos = center.offset(x, 0, z);

                // Skip if in safe zone
                if (safeZones.contains(spearPos)) {
                    continue;
                }

                // Skip if too close to safe zones (ensure 10 block safety as requested)
                boolean tooCloseToSafeZone = false;
                for (BlockPos safeZone : safeZones) {
                    double distance = Math.sqrt(spearPos.distSqr(safeZone));
                    if (distance < 5) { // 5 block buffer around safe zones
                        tooCloseToSafeZone = true;
                        break;
                    }
                }

                if (!tooCloseToSafeZone) {
                    // Add some randomness to make it less predictable
                    if (Math.random() < 0.7) { // 70% chance to spawn spear at this position
                        // Find ground level
                        BlockPos groundPos = findGroundLevel(spearPos);
                        if (groundPos != null) {
                            spearPositions.put(groundPos, 0); // Start at height 0
                        }
                    }
                }
            }
        }
    }

    private BlockPos findGroundLevel(BlockPos pos) {
        // Search down from current Y level to find solid ground
        for (int y = (int) this.getY() + 10; y >= (int) this.getY() - 20; y--) {
            BlockPos checkPos = new BlockPos(pos.getX(), y, pos.getZ());
            BlockState blockState = this.level().getBlockState(checkPos);

            if (!blockState.isAir() && blockState.isSolid()) {
                return checkPos.above(); // Return position above the solid block
            }
        }
        return null; // No solid ground found
    }

    private void showSpearWarnings() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        float warningProgress = (float) spearAttackTimer / SPEAR_WARNING_TIME;

        // Show warning particles at spear positions
        for (BlockPos spearPos : spearPositions.keySet()) {
            // Intensity increases as warning progresses
            SimpleParticleType particleType = warningProgress > 0.8f ? ParticleTypes.SOUL_FIRE_FLAME :
                    warningProgress > 0.5f ? ParticleTypes.FLAME :
                            ParticleTypes.SMOKE;

            // Create warning pillar
            for (int i = 0; i < SPEAR_HEIGHT; i++) {
                if (Math.random() < warningProgress) { // More particles as warning progresses
                    serverLevel.sendParticles(particleType,
                            spearPos.getX() + 0.5, spearPos.getY() + i, spearPos.getZ() + 0.5,
                            1, 0.2, 0, 0.2, 0.02);
                }
            }
        }

        // Show safe zone indicators
//        if (spearAttackTimer % 10 == 0) { // Every 0.5 seconds
//            for (BlockPos safePos : safeZones) {
//                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
//                        safePos.getX() + 0.5, safePos.getY() + 0.1, safePos.getZ() + 0.5,
//                        1, 0, 0, 0, 0);
//            }
//        }

        // Play warning sounds
        if (spearAttackTimer % 20 == 0) { // Every second
            float pitch = 0.5f + (warningProgress * 0.5f);
            this.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, pitch);
        }

        // Final warning
        if (warningProgress > 0.9f && spearAttackTimer % 5 == 0) {
            this.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.5f, 2.0f);
        }
    }

    private void spawnSpears() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        float spawnProgress = (float) (spearAttackTimer - SPEAR_WARNING_TIME) / SPEAR_SPAWN_TIME;

        // Update spear heights
        for (Map.Entry<BlockPos, Integer> entry : spearPositions.entrySet()) {
            BlockPos pos = entry.getKey();
            int currentHeight = entry.getValue();
            int targetHeight = (int) (SPEAR_HEIGHT * spawnProgress);

            if (currentHeight < targetHeight) {
                // Spawn spear blocks
                for (int h = currentHeight; h < targetHeight && h < SPEAR_HEIGHT; h++) {
                    BlockPos spearBlockPos = pos.above(h);

                    // Use different blocks for different parts of the spear
                    BlockState spearBlock;
                    if (h == targetHeight - 1) {
                        spearBlock = Blocks.POINTED_DRIPSTONE.defaultBlockState(); // Sharp tip
                    } else {
                        spearBlock = Blocks.DRIPSTONE_BLOCK.defaultBlockState(); // Body
                    }

                    this.level().setBlock(spearBlockPos, spearBlock, 3);

                    // Spawn particles when block appears
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                            spearBlockPos.getX() + 0.5, spearBlockPos.getY(), spearBlockPos.getZ() + 0.5,
                            3, 0.3, 0.1, 0.3, 0.1);
                }

                entry.setValue(targetHeight);
            }
        }

        // Check for player damage
        checkSpearDamage();

        // Play emerging sound
        if (spearAttackTimer % 3 == 0) {
            this.playSound(SoundEvents.DRIPSTONE_BLOCK_PLACE, 2.0F, 0.7F);
        }
    }

    private void checkSpearDamage() {
        // Find players that might be hit by spears
        for (Player player : this.level().getEntitiesOfClass(Player.class,
                new AABB(this.getX() - 50, this.getY() - 20, this.getZ() - 50,
                        this.getX() + 50, this.getY() + 20, this.getZ() + 50))) {

            if (player.isCreative() || player.isSpectator()) continue;

            BlockPos playerPos = player.blockPosition();

            // Check if player is standing on or inside a spear
            if (spearPositions.containsKey(playerPos) ||
                    spearPositions.containsKey(playerPos.below()) ||
                    spearPositions.containsKey(playerPos.above())) {

                // Deal significant damage
                float damage = 12.0F; // High damage for standing in spears
                player.hurt(this.damageSources().mobAttack(this), damage);

                // Knockback player away from spear
                Vec3 knockback = new Vec3(
                        (Math.random() - 0.5) * 2.0,
                        SPEAR_HEIGHT*1.5,
                        (Math.random() - 0.5) * 2.0
                );
                player.setDeltaMovement(knockback);
                player.hurtMarked = true;

                // Play damage sound
                player.playSound(SoundEvents.PLAYER_HURT, 1.0F, 1.0F);

                // Add brief weakness effect
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
            }
        }
    }

    private void endSpearAttack() {
        isSpearAttackActive = false;
        spearAttackTimer = 0;
        spearAttackCooldown = SPEAR_ATTACK_COOLDOWN;

        // Schedule spear removal after a delay
        scheduleSpearRemoval();

        // Play attack end sound
        this.playSound(SoundEvents.WITHER_DEATH, 1.0F, 1.2F);

        // Clear collections
        safeZones.clear();
    }

    private void scheduleSpearRemoval() {
        // Remove spears after 5 seconds (100 ticks)
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().execute(() -> {
                // Schedule removal for later
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        removeSpears();
                    }
                }, 5000); // 5 seconds delay
            });
        }
    }

    private void removeSpears() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        // Remove all spear blocks
        for (BlockPos spearBase : spearPositions.keySet()) {
            for (int h = 0; h < SPEAR_HEIGHT; h++) {
                BlockPos spearBlockPos = spearBase.above(h);
                BlockState currentState = this.level().getBlockState(spearBlockPos);

                if (currentState.is(Blocks.POINTED_DRIPSTONE) || currentState.is(Blocks.DRIPSTONE_BLOCK)) {
                    this.level().setBlock(spearBlockPos, Blocks.AIR.defaultBlockState(), 3);

                    // Spawn particles when removing
                    serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                            spearBlockPos.getX() + 0.5, spearBlockPos.getY(), spearBlockPos.getZ() + 0.5,
                            2, 0.2, 0.1, 0.2, 0.05);
                }
            }
        }

        spearPositions.clear();
        this.playSound(SoundEvents.DRIPSTONE_BLOCK_BREAK, 1.5F, 0.8F);
    }

    // Replace your performAirSlamAttack method with this enhanced version
    private void performAirSlamAttack() {
        // Find all players within range
        for (Player player : this.level().getEntitiesOfClass(Player.class,
                new AABB(this.getX() - SLAM_RANGE, this.getY() - SLAM_RANGE, this.getZ() - SLAM_RANGE,
                        this.getX() + SLAM_RANGE, this.getY() + SLAM_RANGE, this.getZ() + SLAM_RANGE))) {

            if (player.isCreative())
                continue;

            UUID playerId = player.getUUID();

            // Check if player is on ground
            if (player.onGround()) {
                // Reset air time and warning time if on ground
                playerAirTime.remove(playerId);
                playerWarningTime.remove(playerId);
            } else {
                // Increment air time
                int currentAirTime = playerAirTime.getOrDefault(playerId, 0);
                currentAirTime++;
                playerAirTime.put(playerId, currentAirTime);

                // Start warning phase when approaching threshold
                if (currentAirTime >= AIR_SLAM_THRESHOLD - WARNING_DURATION && currentAirTime < UPDATED_AIR_SLAM_THRESHOLD) {
                    int warningTime = playerWarningTime.getOrDefault(playerId, 0);
                    warningTime++;
                    playerWarningTime.put(playerId, warningTime);

                    // Show visual warning indicator
                    showSlamWarning(player, warningTime);

                } else if (currentAirTime >= UPDATED_AIR_SLAM_THRESHOLD) {
                    // Execute the slam
                    slamPlayer(player);
                    playerAirTime.remove(playerId);
                    playerWarningTime.remove(playerId);
                }
            }
        }
    }

    // Add this new method to show the visual warning
    private void showSlamWarning(Player player, int warningTime) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        // Calculate warning intensity (0.0 to 1.0)
        float intensity = (float) warningTime / WARNING_DURATION;

        // Position the hand above the player
        double handX = player.getX();
        double handY = player.getY() + 8.0 + Math.sin(warningTime * 0.3) * 0.5; // Slight bobbing motion
        double handZ = player.getZ();

        // Create the visual hand using particles
        createWarningParticles(serverLevel, handX, handY, handZ, intensity);

        // Play warning sound with increasing pitch
        if (warningTime % 10 == 0) { // Every 0.5 seconds
            float pitch = 0.8f + (intensity * 0.4f); // Pitch increases from 0.8 to 1.2
            player.playSound(SoundEvents.BEACON_POWER_SELECT, 0.3f + intensity * 0.4f, pitch);
        }

        // Add screen shake effect using particles around player's view
        if (intensity > 0.5f) {
            for (int i = 0; i < 3; i++) {
                double offsetX = (Math.random() - 0.5) * 0.5;
                double offsetY = (Math.random() - 0.5) * 0.5;
                double offsetZ = (Math.random() - 0.5) * 0.5;
                serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        player.getX() + offsetX, player.getY() + 2 + offsetY, player.getZ() + offsetZ,
                        1, 0, 0, 0, 0);
            }
        }
    }

    // Add this method to create warning particle effects (HAND ATTACK)
    private void createWarningParticles(ServerLevel serverLevel, double centerX, double centerY, double centerZ, float intensity) {

        // Add glowing aura around the hand
        createHandAura(serverLevel, centerX, centerY, centerZ, intensity);
    }

    private void createHandAura(ServerLevel serverLevel, double centerX, double centerY, double centerZ, float intensity) {
        // Create a glowing aura around the entire hand
        int particleCount = (int) (intensity * 15 + 5); // More particles as intensity increases

        for (int i = 0; i < particleCount; i++) {
            // Random position around the hand
            double angle = Math.random() * 2 * Math.PI;
            double distance = 2.5 + Math.random() * 1.5;
            double height = Math.random() * 4 - 2; // -2 to +2 blocks around hand center

            double auraX = centerX + Math.cos(angle) * distance;
            double auraY = centerY + height;
            double auraZ = centerZ + Math.sin(angle) * distance;

            // Intense particles for high warning levels
            if (intensity > 0.8f) {
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        auraX, auraY, auraZ, 1, 0.1, 0.1, 0.1, 0.02);
            } else if (intensity > 0.5f) {
                serverLevel.sendParticles(ParticleTypes.FLAME,
                        auraX, auraY, auraZ, 1, 0.1, 0.1, 0.1, 0.01);
            } else {
                serverLevel.sendParticles(ParticleTypes.END_ROD,
                        auraX, auraY, auraZ, 1, 0.1, 0.1, 0.1, 0.01);
            }
        }
    }

    // Also update the slamPlayer method to add more dramatic particles when the slam hits
    private void slamPlayer(Player player) {
        if (!player.isSpectator() && !player.isCreative()) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }

        // Create downward velocity to slam player to ground
        Vec3 slamVelocity = new Vec3(0, -2.0, 0);
        player.setDeltaMovement(slamVelocity);
        player.hurtMarked = true;

        // Calculate damage based on armor
        float armorValue = player.getArmorValue();
        float armorToughness = (float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float baseDamage = 6.0F;
        float armorBasedDamage = baseDamage + (armorValue * 0.5F) + (armorToughness * 0.75F);

        // Deal damage
        player.hurt(this.damageSources().mobAttack(this), armorBasedDamage);

        // Apply debuff effects
        int effectDuration = 40;
        int slownessDuration = 30;

        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slownessDuration, 2));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, effectDuration, 200));

        // Play slam sound with more impact
        this.playSound(SoundEvents.ANVIL_LAND, 2.0F, 0.5F); // Louder and deeper
        player.playSound(SoundEvents.DRAGON_FIREBALL_EXPLODE, 1.5F, 0.8F); // Additional explosion sound

        // Enhanced particle effects for the actual slam
        if (this.level() instanceof ServerLevel serverLevel) {
            double groundY = player.getY();
            BlockPos playerPos = player.blockPosition();

            // Find actual ground level
            for (int i = 0; i < 10; i++) {
                BlockPos checkPos = playerPos.below(i);
                if (!this.level().getBlockState(checkPos).isAir()) {
                    groundY = checkPos.getY() + 1;
                    break;
                }
            }

            // Create massive hand slam impact effect
            createSlamImpactEffect(serverLevel, player.getX(), groundY, player.getZ());
        }

        player.playSound(SoundEvents.PLAYER_HURT, 1.0F, 1.0F);
    }

    // Add this method for the dramatic slam impact effect
    private void createSlamImpactEffect(ServerLevel serverLevel, double centerX, double groundY, double centerZ) {
        // Massive explosion at impact point
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                centerX, groundY, centerZ, 3, 0, 0, 0, 0);

        // Create hand print in particles
        for (double x = -2; x <= 2; x += 0.2) {
            for (double z = -2; z <= 2; z += 0.2) {
                // Create a rough hand print pattern
                boolean isHandPrint = isPartOfHandPrint(x, z);

                if (isHandPrint) {
                    // Multiple layers of particles for depth
                    for (int layer = 0; layer < 3; layer++) {
                        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                                centerX + x, groundY + 0.1 + (layer * 0.2), centerZ + z,
                                2, 0.1, 0.1, 0.1, 0.02);
                    }

                    // Add some fire particles for dramatic effect
                    if (Math.random() < 0.3) {
                        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                                centerX + x, groundY + 0.3, centerZ + z,
                                1, 0, 0.1, 0, 0.05);
                    }
                }
            }
        }

        // Expanding shockwave ring
        for (int ring = 0; ring < 3; ring++) {
            double radius = 3 + (ring * 2);
            int particles = (int) (radius * 8); // More particles for larger rings

            for (int i = 0; i < particles; i++) {
                double angle = (i / (double) particles) * 2 * Math.PI;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;

                serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                        centerX + x, groundY + 0.1, centerZ + z,
                        1, 0, 0, 0, 0);
            }
        }

        // Debris flying upward in hand shape
        for (int i = 0; i < 50; i++) {
            double x = (Math.random() - 0.5) * 4;
            double z = (Math.random() - 0.5) * 4;

            if (isPartOfHandPrint(x, z)) {
                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        centerX + x, groundY, centerZ + z,
                        3, 0, 1.0, 0, 0.3);
            }
        }
    }

    // Helper method to determine if a point is part of the hand print pattern
    private boolean isPartOfHandPrint(double x, double z) {
        // Palm area
        if (Math.abs(x) <= 1.5 && z >= -1.0 && z <= 1.5) {
            return true;
        }

        // Fingers
        for (int finger = 0; finger < 4; finger++) {
            double fingerX = -1.2 + (finger * 0.8);
            if (Math.abs(x - fingerX) <= 0.3 && z >= 1.5 && z <= 4.0) {
                return true;
            }
        }

        // Thumb
        if (x >= -3.0 && x <= -1.5 && z >= 0.0 && z <= 1.5) {
            return true;
        }

        return false;
    }


    private void showBossBar() {
        // Add all nearby players to boss bar
        for (ServerPlayer player : this.level().getEntitiesOfClass(ServerPlayer.class,
                new AABB(this.getX() - 50, this.getY() - 50, this.getZ() - 50,
                        this.getX() + 50, this.getY() + 50, this.getZ() + 50))) {
            this.bossEvent.addPlayer(player);
        }
        this.bossEvent.setVisible(true);
    }

    private void hideBossBar() {
        this.bossEvent.setVisible(false);
        this.bossEvent.removeAllPlayers();
    }

    private void updateBossBar() {
        if (this.state != PHASE.DEACTIVATED_IDOL && this.state != PHASE.ARENA_BUILDING) {
            // Update health percentage
            float healthPercentage = this.getHealth() / this.getMaxHealth();
            this.bossEvent.setProgress(healthPercentage);

            // Update boss bar name based on phase
            String phaseName = switch (this.state) {
                case ACTIVATED_IDOL -> "The Idol";
                case TRANSITION_TO_RADIANCE -> "???";
                case RADIANCE -> "Radiance";
                case TRANSITION_TO_TRUE -> "?????";
                case TRUE_RADIANCE -> "True Radiance";
                default -> "The Radiance";
            };
            this.bossEvent.setName(Component.literal(phaseName));

            // Change color based on health
            if (healthPercentage > 0.66f) {
                this.bossEvent.setColor(BossEvent.BossBarColor.BLUE);
            } else if (healthPercentage > 0.33f) {
                this.bossEvent.setColor(BossEvent.BossBarColor.RED);
            } else {
                this.bossEvent.setColor(BossEvent.BossBarColor.YELLOW);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        // Clean up boss bar when entity is removed
        hideBossBar();
    }

    private void breakBlocksInHitbox() {
        AABB boundingBox = this.getBoundingBox();
        int minX = (int) Math.floor(boundingBox.minX);
        int minY = (int) Math.floor(boundingBox.minY);
        int minZ = (int) Math.floor(boundingBox.minZ);
        int maxX = (int) Math.floor(boundingBox.maxX);
        int maxY = (int) Math.floor(boundingBox.maxY);
        int maxZ = (int) Math.floor(boundingBox.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = this.level().getBlockState(pos);

                    // Only break non-air blocks that aren't bedrock
                    if (!state.isAir() && !state.is(Blocks.BEDROCK)) {
                        this.level().destroyBlock(pos, true); // true = drop items
                    }
                }
            }
        }
    }

    // Save groundY to NBT data
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("GroundY", this.groundY);
        tag.putInt("Phase", this.state.ordinal());

        // Save AOE knockback state
        tag.putInt("KnockbackChargeTimer", this.knockbackChargeTimer);
        tag.putInt("KnockbackCooldownTimer", this.knockbackCooldownTimer);
        tag.putBoolean("IsChargingKnockback", this.isChargingKnockback);
        tag.putBoolean("IsWindingUp", this.isWindingUp);

        // spear
        tag.putInt("SpearAttackTimer", this.spearAttackTimer);
        tag.putInt("SpearAttackCooldown", this.spearAttackCooldown);
        tag.putBoolean("IsSpearAttackActive", this.isSpearAttackActive);

        // block explosions
        tag.putInt("BlockExplosionCooldown", this.blockExplosionCooldown);
        tag.putBoolean("IsBlockExplosionActive", this.isBlockExplosionActive);
        tag.putInt("BlockExplosionTimer", this.blockExplosionTimer);
    }

    // Update the readAdditionalSaveData method to load the new fields:
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("GroundY")) {
            this.groundY = tag.getDouble("GroundY");
        }
        if (tag.contains("Phase")) {
            int stateInt = tag.getInt("Phase");
            this.state = PHASE.values()[stateInt];
        }

        // Load AOE knockback state
        if (tag.contains("KnockbackChargeTimer")) {
            this.knockbackChargeTimer = tag.getInt("KnockbackChargeTimer");
        }
        if (tag.contains("KnockbackCooldownTimer")) {
            this.knockbackCooldownTimer = tag.getInt("KnockbackCooldownTimer");
        }
        if (tag.contains("IsChargingKnockback")) {
            this.isChargingKnockback = tag.getBoolean("IsChargingKnockback");
        }
        if (tag.contains("IsWindingUp")) {
            this.isWindingUp = tag.getBoolean("IsWindingUp");
        }
        if (tag.contains("SpearAttackTimer")) {
            this.spearAttackTimer = tag.getInt("SpearAttackTimer");
        }
        if (tag.contains("SpearAttackCooldown")) {
            this.spearAttackCooldown = tag.getInt("SpearAttackCooldown");
        }
        if (tag.contains("IsSpearAttackActive")) {
            this.isSpearAttackActive = tag.getBoolean("IsSpearAttackActive");
        }

        if (tag.contains("BlockExplosionCooldown")) {
            this.blockExplosionCooldown = tag.getInt("BlockExplosionCooldown");
        }
        if (tag.contains("IsBlockExplosionActive")) {
            this.isBlockExplosionActive = tag.getBoolean("IsBlockExplosionActive");
        }
        if (tag.contains("BlockExplosionTimer")) {
            this.blockExplosionTimer = tag.getInt("BlockExplosionTimer");
        }
    }

    private void performKnockbackAttack() {
        // Handle cooldown timer
        if (knockbackCooldownTimer > 0) {
            knockbackCooldownTimer--;
            return;
        }

        // Find all players within knockback range first
        java.util.List<Player> playersInRange = this.level().getEntitiesOfClass(Player.class,
                        new AABB(this.getX() - KNOCKBACK_RANGE, this.getY() - KNOCKBACK_RANGE, this.getZ() - KNOCKBACK_RANGE,
                                this.getX() + KNOCKBACK_RANGE, this.getY() + KNOCKBACK_RANGE, this.getZ() + KNOCKBACK_RANGE))
                .stream()
                .filter(player -> !player.isCreative() && !player.isSpectator())
                .collect(java.util.stream.Collectors.toList());

        // If no players in range, reset everything
        if (playersInRange.isEmpty()) {
            if (isWindingUp || isChargingKnockback) {
                isWindingUp = false;
                isChargingKnockback = false;
                knockbackChargeTimer = 0;
            }
            return;
        }

        // Start windup if not already started
        if (!isWindingUp && !isChargingKnockback) {
            isWindingUp = true;
            knockbackChargeTimer = 0;

            // Play subtle windup sound
            this.playSound(SoundEvents.BEACON_AMBIENT, 0.8F, 1.2F);
        }

        // Handle windup phase
        if (isWindingUp) {
            knockbackChargeTimer++;
            showWindupEffects(playersInRange);

            if (knockbackChargeTimer >= KNOCKBACK_WINDUP_TIME) {
                // Transition to charge phase
                isWindingUp = false;
                isChargingKnockback = true;
                knockbackChargeTimer = 0;

                // Play charge start sound
                this.playSound(SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.8F);
            }
            return;
        }

        // Handle charge phase
        if (isChargingKnockback) {
            knockbackChargeTimer++;

            // Show charging effects
            showKnockbackChargeEffects(playersInRange);

            // Execute knockback when fully charged
            if (knockbackChargeTimer >= KNOCKBACK_CHARGE_TIME) {
                executeAOEKnockback(playersInRange);

                // Reset for next attack
                isChargingKnockback = false;
                knockbackChargeTimer = 0;
                knockbackCooldownTimer = KNOCKBACK_COOLDOWN;
            }
        }
    }

    // Add this method to show subtle windup visual effects
    private void showWindupEffects(java.util.List<Player> playersInRange) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        float windupProgress = (float) knockbackChargeTimer / KNOCKBACK_WINDUP_TIME;

        // Subtle particle effects during windup - much less intense than charge phase
        if (knockbackChargeTimer % 3 == 0) { // Every 3 ticks for sparse effects
            // Small energy wisps around the boss
            double angle = Math.random() * 2 * Math.PI;
            double distance = 1.5 + Math.random() * 0.5;
            double x = this.getX() + Math.cos(angle) * distance;
            double z = this.getZ() + Math.sin(angle) * distance;
            double y = this.getY() + 0.5 + Math.random() * 0.5;

            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    x, y, z, 1, 0, 0.1, 0, 0.01);
        }

        // Very subtle ground effects at higher windup progress
        if (windupProgress > 0.5f && knockbackChargeTimer % 5 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = (i / 3.0) * 2 * Math.PI + (knockbackChargeTimer * 0.1);
                double radius = 1.0;
                double x = this.getX() + Math.cos(angle) * radius;
                double z = this.getZ() + Math.sin(angle) * radius;

                serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        x, this.getY() + 0.1, z, 1, 0, 0, 0, 0);
            }
        }
    }

    // Add this method to show charging visual effects
    private void showKnockbackChargeEffects(java.util.List<Player> playersInRange) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        float chargeProgress = (float) knockbackChargeTimer / KNOCKBACK_CHARGE_TIME;

        // Create expanding energy ring around the boss
        double radius = KNOCKBACK_RANGE * chargeProgress;
        int particles = Math.max(8, (int) (radius * 4));

        for (int i = 0; i < particles; i++) {
            double angle = (i / (double) particles) * 2 * Math.PI;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            // Different particle types based on charge progress
            SimpleParticleType particleType = chargeProgress > 0.8f ? ParticleTypes.SOUL_FIRE_FLAME :
                    chargeProgress > 0.5f ? ParticleTypes.FLAME :
                            ParticleTypes.END_ROD;

            serverLevel.sendParticles(particleType,
                    this.getX() + x, this.getY() + 0.1, this.getZ() + z,
                    1, 0, 0, 0, 0);
        }

        // Create upward energy spirals around the boss
        for (int i = 0; i < 3; i++) {
            double spiralAngle = (knockbackChargeTimer * 0.3) + (i * 2.094); // 120 degrees apart
            double spiralRadius = 2.0;
            double spiralX = this.getX() + Math.cos(spiralAngle) * spiralRadius;
            double spiralZ = this.getZ() + Math.sin(spiralAngle) * spiralRadius;
            double spiralY = this.getY() + (chargeProgress * 3.0);

            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    spiralX, spiralY, spiralZ, 2, 0.1, 0.1, 0.1, 0.05);
        }

        // Warning effects that intensify as charge builds
        if (chargeProgress > 0.5f) {
            // Play periodic warning sounds
            if (knockbackChargeTimer % 10 == 0) {
                float pitch = 0.8f + (chargeProgress * 0.6f);
                this.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.5f + chargeProgress * 0.5f, pitch);
            }

            // Warn players in range with particles
            for (Player player : playersInRange) {
                if (chargeProgress > 0.8f) {
                    // Intense warning for imminent attack
                    serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                            player.getX(), player.getY() + 2.5, player.getZ(),
                            3, 0.5, 0.3, 0.5, 0);
                } else {
                    // Moderate warning
                    serverLevel.sendParticles(ParticleTypes.CRIT,
                            player.getX(), player.getY() + 2, player.getZ(),
                            1, 0.3, 0.2, 0.3, 0.1);
                }
            }
        }
    }

    // Add this method to execute the AOE knockback
    private void executeAOEKnockback(java.util.List<Player> playersInRange) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        // Play powerful attack sound
//        this.playSound(SoundEvents.GENERIC_EXPLODE, 2.0F, 0.6F);
        this.playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5F, 1.2F);

        // Create massive explosion effect at boss location
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                this.getX(), this.getY() + 1, this.getZ(), 5, 1, 1, 1, 0);

        // Create expanding shockwave
        createKnockbackShockwave(serverLevel);

        // Apply knockback to all players in range
        for (Player player : playersInRange) {
            applyKnockbackToPlayer(player);
        }

        // Screen shake effect for all players in extended range
        for (Player player : this.level().getEntitiesOfClass(Player.class,
                new AABB(this.getX() - 20, this.getY() - 20, this.getZ() - 20,
                        this.getX() + 20, this.getY() + 20, this.getZ() + 20))) {

            createScreenShakeEffect(serverLevel, player);
        }
    }

    // Add this helper method to apply knockback to individual players
    private void applyKnockbackToPlayer(Player player) {
        // Calculate distance and direction
        double deltaX = player.getX() - this.getX();
        double deltaZ = player.getZ() - this.getZ();
        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        if (distance == 0) distance = 0.1; // Prevent division by zero

        // Calculate knockback direction (away from boss)
        double directionX = deltaX / distance;
        double directionZ = deltaZ / distance;

        // Scale knockback based on distance (closer = stronger knockback)
        double distanceMultiplier = Math.max(0.5, 1.0 - (distance / KNOCKBACK_RANGE));

        // Apply knockback velocity
        Vec3 knockbackVelocity = new Vec3(
                directionX * KNOCKBACK_STRENGTH * distanceMultiplier,
                LAUNCH_STRENGTH * distanceMultiplier,
                directionZ * KNOCKBACK_STRENGTH * distanceMultiplier
        );

        player.setDeltaMovement(knockbackVelocity);
        player.setOnGround(false);
        player.hurtMarked = true;

        // Deal damage based on distance
        float baseDamage = 4.0F;
        float actualDamage = (float) (baseDamage * distanceMultiplier);
        player.hurt(this.damageSources().mobAttack(this), actualDamage);

        // Play individual player sounds
        player.playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.5F, 0.7F);
    }

    // Add this method to create the shockwave visual effect
    private void createKnockbackShockwave(ServerLevel serverLevel) {
        // Multiple expanding rings for dramatic effect
        for (int ring = 0; ring < 4; ring++) {
            double baseRadius = 2.0 + (ring * 2.5);
            int particlesPerRing = (int) (baseRadius * 6);

            for (int i = 0; i < particlesPerRing; i++) {
                double angle = (i / (double) particlesPerRing) * 2 * Math.PI;

                // Create multiple radius points for thickness
                for (double radiusOffset = -0.5; radiusOffset <= 0.5; radiusOffset += 0.25) {
                    double radius = baseRadius + radiusOffset;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;

                    SimpleParticleType particleType = ring == 0 ? ParticleTypes.SOUL_FIRE_FLAME :
                            ring == 1 ? ParticleTypes.FLAME :
                                    ring == 2 ? ParticleTypes.LARGE_SMOKE :
                                            ParticleTypes.CLOUD;

                    serverLevel.sendParticles(particleType,
                            this.getX() + x, this.getY() + 0.1, this.getZ() + z,
                            2, 0, 0.2, 0, 0.1);
                }
            }
        }

        // Create debris effect
        for (int i = 0; i < 30; i++) {
            double angle = Math.random() * 2 * Math.PI;
            double distance = Math.random() * KNOCKBACK_RANGE;
            double x = Math.cos(angle) * distance;
            double z = Math.sin(angle) * distance;

            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    this.getX() + x, this.getY(), this.getZ() + z,
                    3, 0.2, 1.5, 0.2, 0.3);
        }
    }

    // Add this method for screen shake effect
    private void createScreenShakeEffect(ServerLevel serverLevel, Player player) {
        // Create particles around player's view to simulate screen shake
        for (int i = 0; i < 8; i++) {
            double offsetX = (Math.random() - 0.5) * 2;
            double offsetY = (Math.random() - 0.5) * 2;
            double offsetZ = (Math.random() - 0.5) * 2;

            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    player.getX() + offsetX, player.getEyeY() + offsetY, player.getZ() + offsetZ,
                    1, 0, 0, 0, 0);
        }
    }

    private void performBlockExplosionAttack() {
        // Handle cooldown
        if (blockExplosionCooldown > 0) {
            blockExplosionCooldown--;
            return;
        }

        // Clean up old block placement records
        cleanOldBlockPlacements();

        // Check for recently placed blocks in range
        Set<BlockPos> dangerousBlocks = findDangerousBlocks();

        if (!dangerousBlocks.isEmpty() && !isBlockExplosionActive) {
            startBlockExplosion(dangerousBlocks);
        }

        // Handle active explosion sequence
        if (isBlockExplosionActive) {
            handleActiveBlockExplosion();
        }
    }

    // Method to detect when blocks are placed (call this from a block placement event or check periodically)
    public void onBlockPlaced(BlockPos pos) {
        // Check if block is within range of boss
        double distance = Math.sqrt(pos.distSqr(this.blockPosition()));
        if (distance <= BLOCK_DETECTION_RANGE && state != PHASE.DEACTIVATED_IDOL) {
            recentBlockPlacements.put(pos, this.level().getGameTime());

            // Play warning sound immediately
            this.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F, 0.5F);

            // Show immediate warning particles
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                        3, 0.2, 0.2, 0.2, 0);
            }
        }
    }

    // Alternative method to scan for blocks if you can't hook into placement events
    private void scanForNewBlocks() {
        BlockPos bossPos = this.blockPosition();

        for (int x = -BLOCK_DETECTION_RANGE; x <= BLOCK_DETECTION_RANGE; x++) {
            for (int y = -BLOCK_DETECTION_RANGE; y <= BLOCK_DETECTION_RANGE; y++) {
                for (int z = -BLOCK_DETECTION_RANGE; z <= BLOCK_DETECTION_RANGE; z++) {
                    BlockPos checkPos = bossPos.offset(x, y, z);

                    // Skip if too far
                    if (checkPos.distSqr(bossPos) > BLOCK_DETECTION_RANGE * BLOCK_DETECTION_RANGE) {
                        continue;
                    }

                    BlockState state = this.level().getBlockState(checkPos);

                    // Check if this is a player-placed block (you might need to adjust this logic)
                    if (!state.isAir() &&
                            !state.is(Blocks.BEDROCK) &&
                            !recentBlockPlacements.containsKey(checkPos) &&
                            isPlayerPlaceableBlock(state)) {

                        // Assume it's recently placed if we haven't seen it before
                        recentBlockPlacements.put(checkPos, this.level().getGameTime());
                    }
                }
            }
        }
    }

    private boolean isPlayerPlaceableBlock(BlockState state) {
        // Add logic to determine if this is likely a player-placed block
        // This is a simple implementation - you might want to expand it
        return !state.is(Blocks.STONE) &&
                !state.is(Blocks.DIRT) &&
                !state.is(Blocks.GRASS_BLOCK) &&
                !state.is(Blocks.DEEPSLATE) &&
                !state.is(Blocks.POINTED_DRIPSTONE) &&
                !state.is(Blocks.DRIPSTONE_BLOCK);
    }

    private void cleanOldBlockPlacements() {
        long currentTime = this.level().getGameTime();
        recentBlockPlacements.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > BLOCK_PLACEMENT_WINDOW);
    }

    private Set<BlockPos> findDangerousBlocks() {
        Set<BlockPos> dangerous = new HashSet<>();
        long currentTime = this.level().getGameTime();

        for (Map.Entry<BlockPos, Long> entry : recentBlockPlacements.entrySet()) {
            // Only consider blocks placed within the time window
            if (currentTime - entry.getValue() <= BLOCK_PLACEMENT_WINDOW) {
                BlockPos pos = entry.getKey();

                // Verify block still exists and is within range
                if (!this.level().getBlockState(pos).isAir() &&
                        pos.distSqr(this.blockPosition()) <= BLOCK_DETECTION_RANGE * BLOCK_DETECTION_RANGE) {
                    dangerous.add(pos);
                }
            }
        }

        return dangerous;
    }

    private void startBlockExplosion(Set<BlockPos> dangerousBlocks) {
        isBlockExplosionActive = true;
        blockExplosionTimer = 0;
        blocksToExplode.clear();
        blocksToExplode.addAll(dangerousBlocks);

        // Play dramatic warning sound
        this.playSound(SoundEvents.WITHER_SPAWN, 2.0F, 0.6F);
    }

    private void handleActiveBlockExplosion() {
        blockExplosionTimer++;

        if (blockExplosionTimer <= BLOCK_EXPLOSION_WARNING_TIME) {
            // Warning phase
            showBlockExplosionWarning();
        } else {
            // Execute explosion
            executeBlockExplosion();

            // End explosion sequence
            isBlockExplosionActive = false;
            blockExplosionTimer = 0;
            blockExplosionCooldown = BLOCK_EXPLOSION_COOLDOWN;
            blocksToExplode.clear();
        }
    }

    private void showBlockExplosionWarning() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        float warningProgress = (float) blockExplosionTimer / BLOCK_EXPLOSION_WARNING_TIME;

        // Show boss charging up
        for (int i = 0; i < 5; i++) {
            double angle = Math.random() * 2 * Math.PI;
            double distance = 2.0 + Math.random() * 1.0;
            double x = this.getX() + Math.cos(angle) * distance;
            double z = this.getZ() + Math.sin(angle) * distance;
            double y = this.getY() + 0.5 + Math.random() * 2.0;

            SimpleParticleType particleType = warningProgress > 0.8f ? ParticleTypes.SOUL_FIRE_FLAME :
                    warningProgress > 0.5f ? ParticleTypes.FLAME : ParticleTypes.ENCHANTED_HIT;

            serverLevel.sendParticles(particleType, x, y, z, 1, 0.1, 0.1, 0.1, 0.05);
        }

        // Show warning effects on target blocks
        for (BlockPos blockPos : blocksToExplode) {
            // Verify block still exists
            if (!this.level().getBlockState(blockPos).isAir()) {
                // Intensifying warning particles
                int particleCount = (int) (warningProgress * 10 + 2);

                for (int i = 0; i < particleCount; i++) {
                    double offsetX = (Math.random() - 0.5) * 1.2;
                    double offsetY = Math.random() * 1.5;
                    double offsetZ = (Math.random() - 0.5) * 1.2;

                    SimpleParticleType particleType = warningProgress > 0.9f ? ParticleTypes.SOUL_FIRE_FLAME :
                            warningProgress > 0.7f ? ParticleTypes.FLAME :
                                    warningProgress > 0.4f ? ParticleTypes.SMOKE : ParticleTypes.CRIT;

                    serverLevel.sendParticles(particleType,
                            blockPos.getX() + 0.5 + offsetX,
                            blockPos.getY() + offsetY,
                            blockPos.getZ() + 0.5 + offsetZ,
                            1, 0, 0, 0, 0.02);
                }

                // Create warning aura around block
                if (blockExplosionTimer % 5 == 0) {
                    double radius = 1.5;
                    int auraParticles = 8;

                    for (int i = 0; i < auraParticles; i++) {
                        double angle = (i / (double) auraParticles) * 2 * Math.PI;
                        double x = blockPos.getX() + 0.5 + Math.cos(angle) * radius;
                        double z = blockPos.getZ() + 0.5 + Math.sin(angle) * radius;

                        serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                                x, blockPos.getY() + 1, z, 1, 0, 0, 0, 0);
                    }
                }
            }
        }

        // Escalating warning sounds
        if (blockExplosionTimer % 10 == 0) {
            float pitch = 0.5f + (warningProgress * 1.0f);
            this.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f + warningProgress, pitch);
        }

        // Final countdown
        if (warningProgress > 0.8f && blockExplosionTimer % 5 == 0) {
            this.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 2.0f, 2.0f);
        }
    }

    private void executeBlockExplosion() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        // Play massive explosion sound
        this.playSound(SoundEvents.DRAGON_FIREBALL_EXPLODE, 3.0F, 0.4F);
        this.playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 0.8F);

        // Create explosion effects and destroy blocks
        for (BlockPos blockPos : new HashSet<>(blocksToExplode)) {
            // Verify block still exists before exploding
            if (!this.level().getBlockState(blockPos).isAir()) {
                // Create explosion at block location
                createBlockExplosionEffect(serverLevel, blockPos);

                // Destroy the block and surrounding blocks
                explodeBlockArea(blockPos);

                // Damage nearby players
                damagePlayersNearBlock(blockPos);
            }
        }

        // Create boss explosion effect
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                this.getX(), this.getY() + 1, this.getZ(), 3, 1, 1, 1, 0);

        // Clear the placement records for exploded blocks
        for (BlockPos explodedPos : blocksToExplode) {
            recentBlockPlacements.remove(explodedPos);
        }
    }

    private void createBlockExplosionEffect(ServerLevel serverLevel, BlockPos blockPos) {
        // Main explosion at block
        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5,
                5, 0.5, 0.5, 0.5, 0);

        // Flame burst
        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5,
                20, 1.0, 1.0, 1.0, 0.2);

        // Debris effect
        for (int i = 0; i < 15; i++) {
            double velocityX = (Math.random() - 0.5) * 2.0;
            double velocityY = Math.random() * 2.0;
            double velocityZ = (Math.random() - 0.5) * 2.0;

            serverLevel.sendParticles(ParticleTypes.LAVA,
                    blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5,
                    1, velocityX, velocityY, velocityZ, 0.5);
        }

        // Shockwave ring
        int ringParticles = 12;
        for (int i = 0; i < ringParticles; i++) {
            double angle = (i / (double) ringParticles) * 2 * Math.PI;
            double radius = 2.0;
            double x = blockPos.getX() + 0.5 + Math.cos(angle) * radius;
            double z = blockPos.getZ() + 0.5 + Math.sin(angle) * radius;

            serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                    x, blockPos.getY() + 0.1, z, 1, 0, 0, 0, 0);
        }
    }

    private void explodeBlockArea(BlockPos center) {
        // Destroy the target block and nearby blocks in a small radius
        int explosionRadius = 2;

        for (int x = -explosionRadius; x <= explosionRadius; x++) {
            for (int y = -explosionRadius; y <= explosionRadius; y++) {
                for (int z = -explosionRadius; z <= explosionRadius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    double distance = Math.sqrt(x*x + y*y + z*z);

                    if (distance <= explosionRadius) {
                        BlockState state = this.level().getBlockState(pos);

                        // Don't destroy bedrock or air
                        if (!state.isAir() && !state.is(Blocks.BEDROCK)) {
                            // Higher chance to destroy blocks closer to center
                            double destroyChance = 1.0 - (distance / explosionRadius) * 0.5;

                            if (Math.random() < destroyChance) {
                                this.level().destroyBlock(pos, true); // Drop items
                            }
                        }
                    }
                }
            }
        }
    }

    private void damagePlayersNearBlock(BlockPos blockPos) {
        double damageRadius = 4.0;

        for (Player player : this.level().getEntitiesOfClass(Player.class,
                new AABB(blockPos.getX() - damageRadius, blockPos.getY() - damageRadius, blockPos.getZ() - damageRadius,
                        blockPos.getX() + damageRadius, blockPos.getY() + damageRadius, blockPos.getZ() + damageRadius))) {

            if (player.isCreative() || player.isSpectator()) continue;

            double distance = Math.sqrt(player.blockPosition().distSqr(blockPos));
            if (distance <= damageRadius) {
                // Calculate damage based on distance
                float baseDamage = 45.0F;
                float distanceMultiplier = (float) Math.max(0.3, 1.0 - (distance / damageRadius));
                float actualDamage = baseDamage * distanceMultiplier;

                // Deal damage
                player.hurt(this.damageSources().explosion(this, this), actualDamage);

                // Apply knockback away from explosion
                Vec3 knockbackDirection = new Vec3(
                        player.getX() - (blockPos.getX() + 0.5),
                        0.5, // Slight upward knockback
                        player.getZ() - (blockPos.getZ() + 0.5)
                ).normalize();

                double knockbackStrength = 1.5 * distanceMultiplier;
                player.setDeltaMovement(player.getDeltaMovement().add(
                        knockbackDirection.x * knockbackStrength,
                        knockbackDirection.y * knockbackStrength,
                        knockbackDirection.z * knockbackStrength
                ));
                player.hurtMarked = true;

                // Apply brief blindness effect
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0));
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BEACON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.BEACON_POWER_SELECT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BEACON_DEACTIVATE;
    }
}