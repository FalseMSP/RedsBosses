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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.stream.Collectors;

enum PHASE {
    DEACTIVATED_IDOL,
    ARENA_BUILDING,
    ACTIVATED_IDOL,
    TRANSITION_TO_RADIANCE,
    ARENA_BUILDING_2,
    RADIANCE,
    TRANSITION_TO_TRUE,
    TRUE_RADIANCE
}

public class Radiance extends Monster {
    private SpiralStructureBuilder arenaBuilder;
    private SpiralStructureBuilder arenaBuilder2;
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

    // arrow stuff
    public static final double ARROW_REFLECTION_RANGE = 10;

    // Armor Steal Attack
    private static final double ARMOR_STEAL_RANGE = 6.0; // a little further than entity reach
    private static final int ARMOR_STEAL_COOLDOWN = 20 * 25; // 25 second cooldown
    private static final int GRAB_DURATION = 60; // 3 seconds of holding player
    private static final int GRAB_HEIGHT = 8; // How high to lift the player
    private int armorStealCooldown = 0;
    private boolean isGrabbingPlayer = false;
    private Player grabbedPlayer = null;
    private int grabTimer = 0;
    private Vec3 grabPosition = null;

    // Sky Transition
    private static final int SKY_TRANSITION_DURATION = 20* 5; // 5 seconds to reach sky
    private static final double TARGET_SKY_Y = 230.0;
    private static final int EXPLOSION_RADIUS = 50; // Radius of the massive explosion
    private int skyTransitionTimer = 0;
    private boolean isSkyTransitionActive = false;
    private double skyTransitionStartY = -1;
    private boolean hasCreatedMassiveExplosion = false;

    // Light Beam Attack Fields
    private int lightBeamAttackTimer = 0;
    private static final int LIGHT_BEAM_WARNING_TIME = 40; // 2 seconds warning
    private static final int LIGHT_BEAM_DURATION = 40; // 2 seconds of beams
    private static final int LIGHT_BEAM_COOLDOWN = 20 * 10; // 10 second cooldown
    private int lightBeamCooldown = 0;
    private boolean isLightBeamActive = false;
    private final Map<BlockPos, Integer> lightBeamPositions = new HashMap<>();
    private static final int LIGHT_BEAM_HEIGHT = 100; // How far down the beams go

    // Orbital Light Orbs Fields
    private int orbitalAttackTimer = 0;
    private static final int ORBITAL_ATTACK_DURATION = 20 * 20; // 20 seconds of orbital attack
    private static final int ORBITAL_ATTACK_COOLDOWN = 20 * 5; // 7 second cooldown
    private int orbitalAttackCooldown = 0;
    private boolean isOrbitalAttackActive = false;
    private final List<OrbitalLightOrb> lightOrbs = new ArrayList<>();
    private static final int NUM_LIGHT_ORBS = 32;
    private static final double ORBITAL_RADIUS = 10.0;
    private static final double ORB_SPEED = 0.1; // Radians per tick

    // Radiant Burst Fields (area denial)
    private int radiantBurstTimer = 0;
    private static final int RADIANT_BURST_WARNING = 20; // 1 second warning
    private static final int RADIANT_BURST_DURATION = 80; // 4 seconds active
    private static final int RADIANT_BURST_COOLDOWN = 20 * 15; // 15 second cooldown
    private int radiantBurstCooldown = 0;
    private boolean isRadiantBurstActive = false;
    private final Set<BlockPos> radiantBurstZones = new HashSet<>();


    public Radiance(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        // Set boss bar properties
        this.bossEvent.setDarkenScreen(true);
        this.bossEvent.setPlayBossMusic(true);

        this.setCanPickUpLoot(true);
    }

    @Override
    public boolean canReplaceCurrentItem(net.minecraft.world.item.ItemStack candidate, net.minecraft.world.item.ItemStack existing) {
        if (candidate.isEmpty()) return false;

        // Always allow replacement during armor stealing
        return true;
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
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    public void checkDespawn() {

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

            // reflect arrows if not using knockback attack and spears are not up
            if (!isWindingUp && !isChargingKnockback && !isSpearAttackActive && !isGrabbingPlayer) {
                performArrowReflection();
            }

            // armor steal attack IF KNOCKBACK IS ON COOLDOWN
            if (knockbackCooldownTimer > 1 && knockbackCooldownTimer < KNOCKBACK_COOLDOWN - 20*4 && !this.hasEffect(MobEffects.WEAKNESS)) // only attack if knockback is on cooldown AFTER 4 seconds AND does not have weakness.
                performArmorStealAttack();
        } else if (this.state == PHASE.TRANSITION_TO_RADIANCE) {
            performSkyTransition();
        } else if (this.state == PHASE.ARENA_BUILDING_2) {
            if (arenaBuilder2 == null)
                arenaBuilder2 = new SpiralStructureBuilder(getServer().overworld(),new BlockPos(getBlockX()+1+4+6,230-35,getBlockZ()+1-3+6),"/arena2.schem",600); // 30 second building
            if (arenaBuilder2.tick()) {
                this.state = PHASE.RADIANCE;
            }
            this.moveTo(this.getX(), 230-9, this.getZ());
        } else if (this.state == PHASE.RADIANCE) {
            performLightBeamAttack();

            performOrbitalLightAttack();

            performRadiantBurstAttack();
        }

        // Break blocks within hitbox
        breakBlocksInHitbox();

        // Update boss bar
        updateBossBar();
    }

    private void performSkyTransition() {
        if (!isSkyTransitionActive) {
            startSkyTransition();
            return;
        }

        skyTransitionTimer++;

        // Calculate progress (0.0 to 1.0)
        float progress = Math.min(1.0f, (float) skyTransitionTimer / SKY_TRANSITION_DURATION);

        // Move boss upward
        moveBossToSky(progress);

        // Show transition effects
        showSkyTransitionEffects(progress);

        // Check if we've reached the sky
        if (progress >= 1.0f && !hasCreatedMassiveExplosion) {
            createMassiveSkyExplosion();
            hasCreatedMassiveExplosion = true;

            // Reset transition state immediately after explosion
            isSkyTransitionActive = false;
            skyTransitionTimer = 0;

            // Keep boss at sky level
            this.setPos(this.getX(), TARGET_SKY_Y, this.getZ());
        }
    }

    private void startSkyTransition() {
        isSkyTransitionActive = true;
        skyTransitionTimer = 0;
        skyTransitionStartY = this.getY();
        hasCreatedMassiveExplosion = false;

        // Disable gravity for smooth flight
        this.setNoGravity(true);

        // Play dramatic ascension sound
        this.playSound(SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.3F);
        this.playSound(SoundEvents.WITHER_SPAWN, 2.0F, 0.8F);

        // Announce to all players in range
        announceToPlayers("§6The Radiance begins to ascend to the heavens!");
    }

    private void moveBossToSky(float progress) {
        // Smooth interpolation from start Y to target Y
        double currentY = skyTransitionStartY + (TARGET_SKY_Y - skyTransitionStartY) * progress;

        // Add some floating motion for dramatic effect
        double floatOffset = Math.sin(skyTransitionTimer * 0.1) * 2.0 * progress;

        this.setPos(this.getX(), currentY + floatOffset, this.getZ());

        // Prevent any downward movement
        this.setDeltaMovement(0, Math.max(0, this.getDeltaMovement().y), 0);
    }

    private void showSkyTransitionEffects(float progress) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        // Ascending spiral of particles around the boss
        for (int i = 0; i < 8; i++) {
            double angle = (skyTransitionTimer * 0.2) + (i * 0.785); // 45 degrees apart
            double radius = 4.0 + Math.sin(skyTransitionTimer * 0.1 + i) * 1.0;
            double x = this.getX() + Math.cos(angle) * radius;
            double z = this.getZ() + Math.sin(angle) * radius;
            double y = this.getY() + (Math.random() - 0.5) * 6.0;

            SimpleParticleType particleType = progress > 0.8f ? ParticleTypes.SOUL_FIRE_FLAME :
                    progress > 0.5f ? ParticleTypes.END_ROD : ParticleTypes.ENCHANTED_HIT;

            serverLevel.sendParticles(particleType, x, y, z, 1, 0, 0.5, 0, 0.1);
        }

        // Create ascending beam of light
        double beamHeight = 20.0 * progress;
        for (int i = 0; i < beamHeight; i += 2) {
            double beamY = this.getY() - beamHeight + i;

            // Multiple beams for thickness
            for (int beam = 0; beam < 3; beam++) {
                double beamRadius = 0.5 + beam * 0.3;
                for (int p = 0; p < 4; p++) {
                    double beamAngle = (p / 4.0) * 2 * Math.PI;
                    double beamX = this.getX() + Math.cos(beamAngle) * beamRadius;
                    double beamZ = this.getZ() + Math.sin(beamAngle) * beamRadius;

                    serverLevel.sendParticles(ParticleTypes.END_ROD,
                            beamX, beamY, beamZ, 1, 0, 0, 0, 0);
                }
            }
        }

        // Intensifying sound effects
        if (skyTransitionTimer % 40 == 0) { // Every 2 seconds
            float pitch = 0.5f + (progress * 1.0f);
            this.playSound(SoundEvents.BEACON_AMBIENT, 2.0f + progress, pitch);
        }

        // Warning sounds as we approach the explosion
        if (progress > 0.8f && skyTransitionTimer % 20 == 0) {
            this.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 3.0f, 2.0f);
        }
    }

    private void createPlayerLevitationEffect(ServerLevel serverLevel, Player player) {
        // Upward spiral around player
        for (int i = 0; i < 6; i++) {
            double angle = (skyTransitionTimer * 0.3) + (i * 1.047); // 60 degrees apart
            double radius = 1.5;
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;
            double y = player.getY() + Math.random() * 3.0;

            serverLevel.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0.2, 0, 0.05);
        }

        // Upward flowing particles
        for (int i = 0; i < 3; i++) {
            double offsetX = (Math.random() - 0.5) * 2.0;
            double offsetZ = (Math.random() - 0.5) * 2.0;

            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    player.getX() + offsetX, player.getY(), player.getZ() + offsetZ,
                    1, 0, 1.0, 0, 0.3);
        }
    }

    private void createMassiveSkyExplosion() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        // Play massive explosion sounds
        this.playSound(SoundEvents.DRAGON_FIREBALL_EXPLODE, 5.0F, 0.2F);
        this.playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 4.0F, 0.5F);

        // Create the massive chasm with explosive effects
        createSkyHole(serverLevel);

        // Create dramatic explosion effects
        createExplosionEffects(serverLevel);

        // Create the explosive chasm drilling effect
        createChasmDrillingEffect(serverLevel);

        // Damage and affect players
        affectPlayersInExplosion();

        // Announce the transformation
        announceToPlayers("§4The heavens themselves tremble before the true power of Radiance!");

        // Immediately transition to RADIANCE phase
        this.state = PHASE.ARENA_BUILDING_2;
    }

    private void createSkyHole(ServerLevel serverLevel) {
        BlockPos centerPos = this.blockPosition();

        // Create a massive chasm that gets wider as it goes down
        int maxDepth = (int) (TARGET_SKY_Y - groundY + 50); // Go deep below ground level

        for (int y = 0; y >= -maxDepth; y--) {
            // Calculate radius - gets bigger as we go down, creating a chasm effect
            double depthProgress = Math.abs(y) / (double) maxDepth;
            double currentRadius = EXPLOSION_RADIUS * (0.3 + depthProgress * 1.5); // Start small, get huge

            // Create expanding explosion effects as we go down
            if (y % 10 == 0) { // Every 10 blocks down, create explosion effects
                createChasmExplosionRing(serverLevel, centerPos.offset(0, y, 0), currentRadius);
            }

            // Destroy blocks in a circular pattern at this Y level
            for (int x = -(int)currentRadius - 5; x <= (int)currentRadius + 5; x++) {
                for (int z = -(int)currentRadius - 5; z <= (int)currentRadius + 5; z++) {
                    double distance = Math.sqrt(x*x + z*z);

                    if (distance <= currentRadius) {
                        BlockPos pos = centerPos.offset(x, y, z);
                        BlockState state = serverLevel.getBlockState(pos);

                        // Don't destroy bedrock
                        if (!state.is(Blocks.BEDROCK)) {
                            // Calculate destroy chance - more aggressive destruction
                            double destroyChance = 1.0 - (distance / currentRadius) * 0.2;

                            // Add some randomness for more natural chasm walls
                            destroyChance += (Math.random() - 0.5) * 0.3;
                            destroyChance = Math.max(0.0, Math.min(1.0, destroyChance));

                            if (Math.random() < destroyChance) {
                                serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);

                                // Create explosion particles at destroyed blocks
                                if (Math.random() < 0.1) { // 10% chance for particles
                                    serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                            1, 0, 0, 0, 0);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void createExplosionEffects(ServerLevel serverLevel) {
        Vec3 explosionCenter = new Vec3(this.getX(), this.getY(), this.getZ());

        // Massive explosion particles at center
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                explosionCenter.x, explosionCenter.y, explosionCenter.z, 20, 5, 5, 5, 0);

        // Multiple expanding shockwave rings
        for (int ring = 0; ring < 8; ring++) {
            double radius = 5.0 + (ring * 4.0);
            int particlesPerRing = (int) (radius * 6);

            for (int i = 0; i < particlesPerRing; i++) {
                double angle = (i / (double) particlesPerRing) * 2 * Math.PI;

                // Horizontal ring
                double x = explosionCenter.x + Math.cos(angle) * radius;
                double z = explosionCenter.z + Math.sin(angle) * radius;
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        x, explosionCenter.y, z, 3, 0.5, 0.5, 0.5, 0.2);

                // Vertical rings
                double y = explosionCenter.y + Math.cos(angle) * radius;
                serverLevel.sendParticles(ParticleTypes.FLAME,
                        explosionCenter.x, y, explosionCenter.z + Math.sin(angle) * radius,
                        2, 0.3, 0.3, 0.3, 0.1);
            }
        }

        // Debris and energy bursts in all directions
        for (int i = 0; i < 100; i++) {
            double angle1 = Math.random() * 2 * Math.PI;
            double angle2 = Math.random() * Math.PI;
            double distance = EXPLOSION_RADIUS * (0.5 + Math.random() * 0.5);

            double x = explosionCenter.x + Math.cos(angle1) * Math.sin(angle2) * distance;
            double y = explosionCenter.y + Math.cos(angle2) * distance;
            double z = explosionCenter.z + Math.sin(angle1) * Math.sin(angle2) * distance;

            serverLevel.sendParticles(ParticleTypes.LAVA,
                    x, y, z, 1, 0, 0, 0, 0.5);

            if (i % 3 == 0) {
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        x, y, z, 2, 1.0, 1.0, 1.0, 0.3);
            }
        }
    }

    private void affectPlayersInExplosion() {
        double explosionRange = EXPLOSION_RADIUS * 1.5;

        for (Player player : this.level().getEntitiesOfClass(Player.class,
                new AABB(this.getX() - explosionRange, this.getY() - explosionRange, this.getZ() - explosionRange,
                        this.getX() + explosionRange, this.getY() + explosionRange, this.getZ() + explosionRange))) {

            if (player.isCreative() || player.isSpectator()) continue;

            double distance = player.distanceTo(this);
            double maxDistance = explosionRange;
            double distanceMultiplier = Math.max(0.2, 1.0 - (distance / maxDistance));

            // Deal massive damage
            float baseDamage = 15.0F;
            float actualDamage = (float) (baseDamage * distanceMultiplier);
            player.hurt(this.damageSources().explosion(this, this), actualDamage);

            // Apply powerful effects
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 1));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 150, 2));
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0)); // Prevent fall damage

            // Dramatic knockback
            Vec3 knockbackDirection = new Vec3(
                    player.getX() - this.getX(),
                    0.5,
                    player.getZ() - this.getZ()
            ).normalize();

            double knockbackStrength = 3.0 * distanceMultiplier;
            player.setDeltaMovement(knockbackDirection.scale(knockbackStrength));
            player.hurtMarked = true;

            // Individual player message
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal("§4You are overwhelmed by the divine transformation!"));
            }
        }
    }

// Add these new methods after createMassiveSkyExplosion():

    private void createChasmExplosionRing(ServerLevel serverLevel, BlockPos center, double radius) {
        // Create explosion ring effects at this depth level
        int particles = (int) (radius * 3);

        for (int i = 0; i < particles; i++) {
            double angle = (i / (double) particles) * 2 * Math.PI;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            double y = center.getY();

            // Different particle types based on depth
            SimpleParticleType particleType;
            if (y > TARGET_SKY_Y - 20) {
                particleType = ParticleTypes.SOUL_FIRE_FLAME; // Sky level - divine fire
            } else if (y > groundY) {
                particleType = ParticleTypes.FLAME; // Above ground - regular fire
            } else if (y > groundY - 30) {
                particleType = ParticleTypes.LARGE_SMOKE; // Underground - smoke and debris
            } else {
                particleType = ParticleTypes.LAVA; // Deep underground - molten rock
            }

            serverLevel.sendParticles(particleType, x, y, z, 2, 0.5, 0.5, 0.5, 0.1);

            // Add some upward shooting particles for dramatic effect
            if (Math.random() < 0.3) {
                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        x, y, z, 1, 0, 2.0, 0, 0.5);
            }
        }

        // Central explosion burst at this level
        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                3, radius * 0.1, 0.5, radius * 0.1, 0);
    }

    private void createChasmDrillingEffect(ServerLevel serverLevel) {
        Vec3 explosionCenter = new Vec3(this.getX(), this.getY(), this.getZ());
        int maxDepth = (int) (TARGET_SKY_Y - groundY + 50);

        // Create dramatic drilling beam effect going downward
        for (int depth = 0; depth < maxDepth; depth += 5) {
            double currentY = explosionCenter.y - depth;
            double beamRadius = 2.0 + (depth / 50.0) * 8.0; // Expanding beam

            // Create circular beam cross-section
            for (int i = 0; i < 16; i++) {
                double angle = (i / 16.0) * 2 * Math.PI;
                double x = explosionCenter.x + Math.cos(angle) * beamRadius;
                double z = explosionCenter.z + Math.sin(angle) * beamRadius;

                // Different effects based on depth
                if (currentY > groundY) {
                    // Above ground - divine energy
                    serverLevel.sendParticles(ParticleTypes.END_ROD,
                            x, currentY, z, 2, 0.1, 0.1, 0.1, 0.05);
                } else {
                    // Underground - destructive force
                    serverLevel.sendParticles(ParticleTypes.LAVA,
                            x, currentY, z, 1, 0.2, 0.2, 0.2, 0.1);
                }
            }

            // Central drilling beam
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    explosionCenter.x, currentY, explosionCenter.z, 5, 1.0, 0.5, 1.0, 0.3);
        }

        // Create massive upward explosion from the bottom of the chasm
        double bottomY = explosionCenter.y - maxDepth;

        // Upward eruption effect
        for (int i = 0; i < 100; i++) {
            double angle = Math.random() * 2 * Math.PI;
            double radius = Math.random() * (EXPLOSION_RADIUS * 2);
            double x = explosionCenter.x + Math.cos(angle) * radius;
            double z = explosionCenter.z + Math.sin(angle) * radius;

            serverLevel.sendParticles(ParticleTypes.LAVA,
                    x, bottomY, z, 3, 0, maxDepth * 0.1, 0, 0.8);

            if (i % 5 == 0) {
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        x, bottomY + 10, z, 2, radius * 0.1, maxDepth * 0.05, radius * 0.1, 0.4);
            }
        }

        // Sound effects for the drilling
        this.playSound(SoundEvents.FIRE_EXTINGUISH, 3.0F, 0.1F); // Deep rumbling
        this.playSound(SoundEvents.LAVA_POP, 4.0F, 0.3F); // Molten rock sounds
    }

    private void announceToPlayers(String message) {
        for (Player player : this.level().getEntitiesOfClass(Player.class,
                new AABB(this.getX() - 100, this.getY() - 100, this.getZ() - 100,
                        this.getX() + 100, this.getY() + 100, this.getZ() + 100))) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal(message));
            }
        }
    }

    // Add this method to handle the armor stealing attack
    private void performArmorStealAttack() {
        // Handle cooldown
        if (armorStealCooldown > 0) {
            armorStealCooldown--;
            return;
        }

        // Handle active grab
        if (isGrabbingPlayer) {
            handleActiveGrab();
            return;
        }

        // Find target for new grab
        List<Player> playersInRange = this.level().getEntitiesOfClass(Player.class,
                        new AABB(this.getX() - ARMOR_STEAL_RANGE, this.getY() - ARMOR_STEAL_RANGE, this.getZ() - ARMOR_STEAL_RANGE,
                                this.getX() + ARMOR_STEAL_RANGE, this.getY() + ARMOR_STEAL_RANGE, this.getZ() + ARMOR_STEAL_RANGE))
                .stream()
                .filter(player -> !player.isCreative() && !player.isSpectator() && hasStealableArmor(player))
                .collect(java.util.stream.Collectors.toList());

        if (!playersInRange.isEmpty()) {
            // Select random player with armor
            Player target = playersInRange.get(this.random.nextInt(playersInRange.size()));
            startArmorGrab(target);
        }
    }

    // Check if player has armor that can be stolen
    private boolean hasStealableArmor(Player player) {
        return !player.getInventory().armor.get(0).isEmpty() || // Boots
                !player.getInventory().armor.get(1).isEmpty() || // Leggings
                !player.getInventory().armor.get(2).isEmpty() || // Chestplate
                !player.getInventory().armor.get(3).isEmpty();   // Helmet
    }

    // Start grabbing a player
    private void startArmorGrab(Player target) {
        isGrabbingPlayer = true;
        grabbedPlayer = target;
        grabTimer = 0;

        // Calculate grab position (above the boss)
        grabPosition = new Vec3(this.getX(), this.getY() + GRAB_HEIGHT, this.getZ());

        // Play dramatic grab sound
        this.playSound(SoundEvents.WITHER_SPAWN, 2.0F, 0.6F);
        this.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.5F, 0.8F);

        // Announce the grab
        if (target instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal("§4You are caught in the boss's grasp!"));
        }

        // Show initial grab effects
        if (this.level() instanceof ServerLevel serverLevel) {
            createGrabStartEffect(serverLevel, target);
        }
    }

    // Handle the active grab sequence
    private void handleActiveGrab() {

        if (this.hasEffect(MobEffects.WEAKNESS)) {
            // Steal armor immediately if we haven't already (at halfway point)
            if (grabTimer < GRAB_DURATION / 2) {
                stealArmorFromPlayer();

                // Announce early armor steal due to weakness
                if (grabbedPlayer instanceof ServerPlayer serverPlayer) {
                    serverPlayer.sendSystemMessage(Component.literal("§6The boss's weakness causes it to hastily steal your armor!"));
                }
            }

            // Drop the player immediately
            endGrab(false); // false = interrupted, not completed normally

            // Play weakness sound
            this.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 0.8F);

            return;
        }

        grabTimer++;

        if (grabbedPlayer == null || grabbedPlayer.isRemoved() || grabbedPlayer.isCreative()) {
            endGrab(false);
            return;
        }

        // Keep player in grab position
        maintainGrabPosition();

        // Show ongoing grab effects
        if (this.level() instanceof ServerLevel serverLevel) {
            showGrabEffects(serverLevel);
        }

        // Play periodic sounds
        if (grabTimer % 20 == 0) {
            this.playSound(SoundEvents.BEACON_AMBIENT, 1.0F, 0.7F);
            grabbedPlayer.playSound(SoundEvents.PLAYER_HURT, 0.5F, 1.2F);
        }

        // Execute armor steal at halfway point
        if (grabTimer == GRAB_DURATION / 2) {
            stealArmorFromPlayer();
        }

        // End grab when duration is complete
        if (grabTimer >= GRAB_DURATION) {
            endGrab(true);
        }
    }

    // Keep the grabbed player at the grab position
    private void maintainGrabPosition() {
        if (grabbedPlayer != null && grabPosition != null) {
            // Disable player abilities
            if (!grabbedPlayer.isSpectator() && !grabbedPlayer.isCreative()) {
                grabbedPlayer.getAbilities().flying = false;
                grabbedPlayer.onUpdateAbilities();
            }

            // Force player position
            grabbedPlayer.teleportTo(grabPosition.x, grabPosition.y, grabPosition.z);
            grabbedPlayer.setDeltaMovement(Vec3.ZERO);
            grabbedPlayer.setOnGround(false);
            grabbedPlayer.hurtMarked = true;

            // Apply slow falling to prevent fall damage when released
            grabbedPlayer.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0));
        }
    }

    // Show visual effects during grab
    private void showGrabEffects(ServerLevel serverLevel) {
        if (grabbedPlayer == null || grabPosition == null) return;

        // Create energy chains connecting boss to player
        Vec3 bossPos = new Vec3(this.getX(), this.getY() + 2, this.getZ());
        Vec3 playerPos = grabPosition;

        // Draw energy line between boss and player
        int chainSegments = 10;
        for (int i = 0; i <= chainSegments; i++) {
            double t = i / (double) chainSegments;
            Vec3 chainPos = bossPos.lerp(playerPos, t);

            // Add slight wave to the chain
            double wave = Math.sin(grabTimer * 0.3 + i * 0.5) * 0.3;
            chainPos = chainPos.add(wave, 0, 0);

            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    chainPos.x, chainPos.y, chainPos.z, 2, 0.1, 0.1, 0.1, 0.02);
        }

        // Create containment field around player
        double radius = 2.0;
        int particles = 12;
        for (int i = 0; i < particles; i++) {
            double angle = (i / (double) particles) * 2 * Math.PI + (grabTimer * 0.1);
            double x = playerPos.x + Math.cos(angle) * radius;
            double z = playerPos.z + Math.sin(angle) * radius;
            double y = playerPos.y + Math.sin(grabTimer * 0.2 + i) * 0.5;

            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    x, y, z, 1, 0, 0, 0, 0);
        }

        // Energy swirl around boss
        for (int i = 0; i < 5; i++) {
            double angle = (grabTimer * 0.2) + (i * 1.256); // 72 degrees apart
            double distance = 3.0;
            double x = this.getX() + Math.cos(angle) * distance;
            double z = this.getZ() + Math.sin(angle) * distance;
            double y = this.getY() + 1 + Math.sin(grabTimer * 0.1 + i) * 1.0;

            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    x, y, z, 1, 0, 0.1, 0, 0.05);
        }
    }

    // Create initial grab effect
    private void createGrabStartEffect(ServerLevel serverLevel, Player target) {
        // Explosion of particles at target location
        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                target.getX(), target.getY() + 1, target.getZ(), 20, 1.0, 1.0, 1.0, 0.3);

        // Create magical circle on ground below target
        BlockPos targetPos = target.blockPosition();
        for (int i = 0; i < 16; i++) {
            double angle = (i / 16.0) * 2 * Math.PI;
            double radius = 3.0;
            double x = targetPos.getX() + 0.5 + Math.cos(angle) * radius;
            double z = targetPos.getZ() + 0.5 + Math.sin(angle) * radius;

            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    x, targetPos.getY() + 0.1, z, 2, 0, 0, 0, 0);
        }
    }

    // Steal armor from the grabbed player
    private void stealArmorFromPlayer() {
        if (grabbedPlayer == null) return;

        // Get all armor pieces that can be stolen
        List<Integer> armorSlots = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (!grabbedPlayer.getInventory().armor.get(i).isEmpty()) {
                armorSlots.add(i);
            }
        }

        if (armorSlots.isEmpty()) {
            return; // No armor to steal
        }

        // Select random armor piece
        int selectedSlot = armorSlots.get(this.random.nextInt(armorSlots.size()));
        net.minecraft.world.item.ItemStack stolenArmor = grabbedPlayer.getInventory().armor.get(selectedSlot);

        // Remove armor from player
        grabbedPlayer.getInventory().armor.set(selectedSlot, net.minecraft.world.item.ItemStack.EMPTY);

        // Try to equip the armor on the boss if it's better
        equipStolenArmor(stolenArmor, selectedSlot);

        // Play steal sound
        this.playSound(SoundEvents.ITEM_PICKUP, 2.0F, 0.7F);
        this.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.5F, 1.2F);

        // Show steal effects
        if (this.level() instanceof ServerLevel serverLevel) {
            createArmorStealEffect(serverLevel, stolenArmor);
        }

        // Announce what was stolen
        String armorName = getArmorTypeName(selectedSlot);
        if (grabbedPlayer instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal("§4Your " + armorName + " has been stolen!"));
        }

        // Announce to nearby players what boss stole
        for (Player nearbyPlayer : this.level().getEntitiesOfClass(Player.class,
                new AABB(this.getX() - 30, this.getY() - 30, this.getZ() - 30,
                        this.getX() + 30, this.getY() + 30, this.getZ() + 30))) {
            if (nearbyPlayer instanceof ServerPlayer serverPlayer && nearbyPlayer != grabbedPlayer) {
                serverPlayer.sendSystemMessage(Component.literal("§6The boss has stolen " +
                        grabbedPlayer.getName().getString() + "'s " + armorName + "!"));
            }
        }
    }

    // Get armor type name for announcements
    private String getArmorTypeName(int slot) {
        return switch (slot) {
            case 0 -> "boots";
            case 1 -> "leggings";
            case 2 -> "chestplate";
            case 3 -> "helmet";
            default -> "armor";
        };
    }

    // Equip stolen armor on boss if it's better than current
    private void equipStolenArmor(net.minecraft.world.item.ItemStack stolenArmor, int armorSlot) {
        if (stolenArmor.isEmpty()) return;

        net.minecraft.world.entity.EquipmentSlot equipmentSlot = getEquipmentSlotForArmorSlot(armorSlot);

        // Get current armor in that slot
        net.minecraft.world.item.ItemStack currentArmor = this.getItemBySlot(equipmentSlot);

        // Compare armor values (simplified comparison)
        boolean shouldEquip = currentArmor.isEmpty() || getArmorValue(stolenArmor) > getArmorValue(currentArmor);

        if (shouldEquip) {
            // Drop current armor if any
            if (!currentArmor.isEmpty()) {
                this.spawnAtLocation(currentArmor);
            }

            // Force equip the stolen armor
            this.setItemSlot(equipmentSlot, stolenArmor.copy());

            // Play equip sound
            this.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1.5F, 0.8F);

            // Announce successful equipment
            for (Player nearbyPlayer : this.level().getEntitiesOfClass(Player.class,
                    new AABB(this.getX() - 30, this.getY() - 30, this.getZ() - 30,
                            this.getX() + 30, this.getY() + 30, this.getZ() + 30))) {
                if (nearbyPlayer instanceof ServerPlayer serverPlayer) {
                    serverPlayer.sendSystemMessage(Component.literal("§4The boss equips the stolen " +
                            getArmorTypeName(armorSlot) + "!"));
                }
            }
        } else {
            // Drop the stolen armor if it's not better
            this.spawnAtLocation(stolenArmor);

            // Announce that armor was discarded
            for (Player nearbyPlayer : this.level().getEntitiesOfClass(Player.class,
                    new AABB(this.getX() - 20, this.getY() - 20, this.getZ() - 20,
                            this.getX() + 20, this.getY() + 20, this.getZ() + 20))) {
                if (nearbyPlayer instanceof ServerPlayer serverPlayer) {
                    serverPlayer.sendSystemMessage(Component.literal("§7The boss discards the weak " +
                            getArmorTypeName(armorSlot) + "."));
                }
            }
        }
    }

    // Convert armor slot index to EquipmentSlot
    private net.minecraft.world.entity.EquipmentSlot getEquipmentSlotForArmorSlot(int armorSlot) {
        return switch (armorSlot) {
            case 0 -> net.minecraft.world.entity.EquipmentSlot.FEET;
            case 1 -> net.minecraft.world.entity.EquipmentSlot.LEGS;
            case 2 -> net.minecraft.world.entity.EquipmentSlot.CHEST;
            case 3 -> net.minecraft.world.entity.EquipmentSlot.HEAD;
            default -> net.minecraft.world.entity.EquipmentSlot.CHEST;
        };
    }

    // Get armor protection value (enhanced)
    private int getArmorValue(net.minecraft.world.item.ItemStack armor) {
        if (armor.getItem() instanceof net.minecraft.world.item.ArmorItem armorItem) {
            int baseDefense = armorItem.getDefense();
            int enchantmentBonus = 0;

            // Add enchantment bonuses to make enchanted armor more valuable
            if (armor.isEnchanted()) {
                enchantmentBonus = armor.getTagEnchantments().size() * 2; // Simple bonus for enchanted items
            }

            return baseDefense + enchantmentBonus;
        }
        return 0;
    }

    // Show visual effects when armor is stolen
    private void createArmorStealEffect(ServerLevel serverLevel, net.minecraft.world.item.ItemStack stolenArmor) {
        if (grabbedPlayer == null) return;

        Vec3 playerPos = grabbedPlayer.position().add(0, 1, 0);
        Vec3 bossPos = new Vec3(this.getX(), this.getY() + 1, this.getZ());

        // Create trail of particles from player to boss
        int trailSegments = 15;
        for (int i = 0; i <= trailSegments; i++) {
            double t = i / (double) trailSegments;
            Vec3 trailPos = playerPos.lerp(bossPos, t);

            // Add upward arc to the trail
            double arc = Math.sin(t * Math.PI) * 2.0;
            trailPos = trailPos.add(0, arc, 0);

            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    trailPos.x, trailPos.y, trailPos.z, 3, 0.2, 0.2, 0.2, 0.1);

//            // Add some item particles
//            if (i % 3 == 0) {
//                serverLevel.sendParticles(ParticleTypes.ITEM(),
//                        trailPos.x, trailPos.y, trailPos.z, 2, 0.1, 0.1, 0.1, 0.05);
//            }
        }

        // Explosion at boss when armor arrives
        serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                bossPos.x, bossPos.y, bossPos.z, 20, 1.0, 1.0, 1.0, 0.2);
    }

    // End the grab sequence
    private void endGrab(boolean completed) {
        if (grabbedPlayer != null) {
            // Give player brief immunity to fall damage
            grabbedPlayer.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));

            if (completed) {
                // Apply some damage and effects for being grabbed
                grabbedPlayer.hurt(this.damageSources().mobAttack(this), 4.0F);
                grabbedPlayer.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
                grabbedPlayer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
            }

            // Play release sound
            this.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 1.5F);

            if (grabbedPlayer instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal("§7You have been released from the boss's grasp."));
            }
        }

        // Reset grab state
        isGrabbingPlayer = false;
        grabbedPlayer = null;
        grabTimer = 0;
        grabPosition = null;
        armorStealCooldown = ARMOR_STEAL_COOLDOWN;
    }

    private void performArrowReflection() {
        // Only reflect arrows when not in deactivated or building phases
        if (this.state == PHASE.DEACTIVATED_IDOL || this.state == PHASE.ARENA_BUILDING) {
            return;
        }

        // Find all arrows within reflection range
        List<net.minecraft.world.entity.projectile.Arrow> arrowsInRange = this.level().getEntitiesOfClass(
                net.minecraft.world.entity.projectile.Arrow.class,
                new AABB(this.getX() - ARROW_REFLECTION_RANGE, this.getY() - ARROW_REFLECTION_RANGE, this.getZ() - ARROW_REFLECTION_RANGE,
                        this.getX() + ARROW_REFLECTION_RANGE, this.getY() + ARROW_REFLECTION_RANGE, this.getZ() + ARROW_REFLECTION_RANGE));

        for (net.minecraft.world.entity.projectile.Arrow arrow : arrowsInRange) {
            // Only reflect arrows shot by players
            if (arrow.getOwner() instanceof Player player && !player.isCreative() && !player.isSpectator()) {
                reflectArrow(arrow, player);
            }
        }
    }

    // Method to reflect an individual arrow back at the player
    private void reflectArrow(net.minecraft.world.entity.projectile.Arrow arrow, Player targetPlayer) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        // Calculate direction from boss to player
        Vec3 bossPos = new Vec3(this.getX(), this.getY() + 1, this.getZ());
        Vec3 playerPos = new Vec3(targetPlayer.getX(), targetPlayer.getY() + 1, targetPlayer.getZ());
        Vec3 direction = playerPos.subtract(bossPos).normalize();

        // Remove the original arrow
        arrow.discard();

        // Create a new arrow going towards the player
        net.minecraft.world.entity.projectile.Arrow reflectedArrow = new net.minecraft.world.entity.projectile.Arrow(
                net.minecraft.world.entity.EntityType.ARROW, this.level());

        // Set arrow position near the boss
        reflectedArrow.setPos(bossPos.x, bossPos.y, bossPos.z);

        // Set the boss as the owner of the reflected arrow
        reflectedArrow.setOwner(this);

        // Set arrow velocity towards the player with increased speed
        double reflectionSpeed = 2.5; // Faster than normal arrows
        reflectedArrow.setDeltaMovement(direction.scale(reflectionSpeed));

        // Make the arrow deal more damage
        reflectedArrow.setBaseDamage(8.0); // Increased damage for reflected arrows

        // Add the reflected arrow to the world
        this.level().addFreshEntity(reflectedArrow);

        // Create reflection visual effects
        createArrowReflectionEffect(serverLevel, bossPos);

        // Play reflection sound
        this.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.5F, 1.2F);
        this.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 0.8F);
    }

    // Method to create visual effects for arrow reflection
    private void createArrowReflectionEffect(ServerLevel serverLevel, Vec3 position) {
        // Create magical barrier effect around the boss
        for (int i = 0; i < 20; i++) {
            double angle = (i / 20.0) * 2 * Math.PI;
            double radius = 3.0;
            double x = position.x + Math.cos(angle) * radius;
            double z = position.z + Math.sin(angle) * radius;
            double y = position.y + (Math.random() - 0.5) * 2.0;

            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    x, y, z, 2, 0.1, 0.1, 0.1, 0.1);
        }

        // Create burst effect at boss center
        serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                position.x, position.y, position.z, 15, 0.5, 0.5, 0.5, 0.2);

        // Add some sparkle effects
        for (int i = 0; i < 10; i++) {
            double offsetX = (Math.random() - 0.5) * 4.0;
            double offsetY = (Math.random() - 0.5) * 4.0;
            double offsetZ = (Math.random() - 0.5) * 4.0;

            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    position.x + offsetX, position.y + offsetY, position.z + offsetZ,
                    1, 0, 0, 0, 0.05);
        }
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

    private void slamPlayer(Player player) {
        if (!player.isSpectator() && !player.isCreative()) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }

        // Create downward velocity to slam player to ground
        Vec3 slamVelocity = new Vec3(0, -2.0, 0);
        player.setDeltaMovement(slamVelocity);
        player.hurtMarked = true;

        // Check if player is using a shield before damage
        boolean wasUsingShield = player.isUsingItem() &&
                player.getUseItem().getItem() instanceof net.minecraft.world.item.ShieldItem;
        net.minecraft.world.item.ItemStack shield = null;

        if (wasUsingShield) {
            shield = player.getUseItem().copy();

            // Stop blocking
            player.disableShield();

            // Play shield break sound
            player.playSound(SoundEvents.SHIELD_BREAK, 1.0F, 1.0F);

            // Damage the shield significantly
            net.minecraft.world.entity.EquipmentSlot shieldSlot = player.getUsedItemHand() == InteractionHand.MAIN_HAND ?
                    net.minecraft.world.entity.EquipmentSlot.MAINHAND :
                    net.minecraft.world.entity.EquipmentSlot.OFFHAND;
            shield.hurtAndBreak(250, player, shieldSlot);

            // Put shield on cooldown
            player.getCooldowns().addCooldown(shield.getItem(), 200); // 10 second cooldown
        }

        // Calculate damage based on armor
        float armorValue = player.getArmorValue();
        float armorToughness = (float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float baseDamage = 6.0F;
        float finalDamage = baseDamage + (armorValue * 0.5F) + (armorToughness * 0.75F);

        // Bonus damage if they tried to block
        if (wasUsingShield) {
            finalDamage += 3.0F; // Extra damage for attempting to block divine wrath
        }

        // Deal the damage (shield is already broken/disabled)
        player.hurt(this.damageSources().mobAttack(this), finalDamage);

        // Apply debuff effects (stronger if they tried to block)
        int effectDuration = wasUsingShield ? 60 : 40;
        int slownessDuration = wasUsingShield ? 50 : 30;
        int weaknessLevel = wasUsingShield ? 2 : 1;

        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slownessDuration, 2));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, effectDuration, weaknessLevel));

        if (wasUsingShield) {
            // Additional punishment for trying to block divine power
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 60, 1));
        }

        // Play slam sound with more impact
        this.playSound(SoundEvents.ANVIL_LAND, 2.0F, 0.5F);
        player.playSound(SoundEvents.DRAGON_FIREBALL_EXPLODE, 1.5F, 0.8F);

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

            // Create massive hand slam impact effect (enhanced if shield was broken)
            createSlamImpactEffect(serverLevel, player.getX(), groundY, player.getZ());

            if (wasUsingShield) {
                // Extra dramatic effects for shield breaking
                createShieldBreakEffect(serverLevel, player.getX(), player.getY() + 1, player.getZ());
            }
        }

        player.playSound(SoundEvents.PLAYER_HURT, 1.0F, 1.0F);
    }

    // Add visual effect for shield breaking
    private void createShieldBreakEffect(ServerLevel serverLevel, double x, double y, double z) {
        // Explosion of shield particles
        serverLevel.sendParticles(ParticleTypes.CRIT,
                x, y, z, 20, 1.0, 1.0, 1.0, 0.3);

        // Angry particles to show divine wrath
        serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                x, y, z, 10, 0.5, 0.5, 0.5, 0);
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
                case ARENA_BUILDING_2 -> "????";
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

        // steal data
        tag.putInt("ArmorStealCooldown", this.armorStealCooldown);
        tag.putBoolean("IsGrabbingPlayer", this.isGrabbingPlayer);
        tag.putInt("GrabTimer", this.grabTimer);

        // sky transition
        tag.putInt("SkyTransitionTimer", this.skyTransitionTimer);
        tag.putBoolean("IsSkyTransitionActive", this.isSkyTransitionActive);
        tag.putDouble("SkyTransitionStartY", this.skyTransitionStartY);
        tag.putBoolean("HasCreatedMassiveExplosion", this.hasCreatedMassiveExplosion);
    }

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

        // spear attack
        if (tag.contains("SpearAttackTimer")) {
            this.spearAttackTimer = tag.getInt("SpearAttackTimer");
        }
        if (tag.contains("SpearAttackCooldown")) {
            this.spearAttackCooldown = tag.getInt("SpearAttackCooldown");
        }
        if (tag.contains("IsSpearAttackActive")) {
            this.isSpearAttackActive = tag.getBoolean("IsSpearAttackActive");
        }

        // block explosion
        if (tag.contains("BlockExplosionCooldown")) {
            this.blockExplosionCooldown = tag.getInt("BlockExplosionCooldown");
        }
        if (tag.contains("IsBlockExplosionActive")) {
            this.isBlockExplosionActive = tag.getBoolean("IsBlockExplosionActive");
        }
        if (tag.contains("BlockExplosionTimer")) {
            this.blockExplosionTimer = tag.getInt("BlockExplosionTimer");
        }

        // steal
        if (tag.contains("ArmorStealCooldown")) {
            this.armorStealCooldown = tag.getInt("ArmorStealCooldown");
        }
        if (tag.contains("IsGrabbingPlayer")) {
            this.isGrabbingPlayer = tag.getBoolean("IsGrabbingPlayer");
        }
        if (tag.contains("GrabTimer")) {
            this.grabTimer = tag.getInt("GrabTimer");
        }

        // sky thing
        if (tag.contains("SkyTransitionTimer")) {
            this.skyTransitionTimer = tag.getInt("SkyTransitionTimer");
        }
        if (tag.contains("IsSkyTransitionActive")) {
            this.isSkyTransitionActive = tag.getBoolean("IsSkyTransitionActive");
        }
        if (tag.contains("SkyTransitionStartY")) {
            this.skyTransitionStartY = tag.getDouble("SkyTransitionStartY");
        }
        if (tag.contains("HasCreatedMassiveExplosion")) {
            this.hasCreatedMassiveExplosion = tag.getBoolean("HasCreatedMassiveExplosion");
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

    private static class OrbitalLightOrb {
        public double horizontalAngle; // Rotation around Y-axis
        public double verticalAngle;   // Up/down angle
        public double radius;          // Distance from boss (now variable)
        public double height;
        public int life;
        public boolean isDescending;
        public int lastLaserTime;      // Track when last laser was fired

        public OrbitalLightOrb(double horizontalAngle, double verticalAngle, double radius, double height) {
            this.horizontalAngle = horizontalAngle;
            this.verticalAngle = verticalAngle;
            this.radius = radius;
            this.height = height;
            this.life = 0;
            this.isDescending = false;
            this.lastLaserTime = 0;
        }

        public Vec3 getPosition(double bossX, double bossY, double bossZ) {
            // Calculate 3D position using spherical coordinates
            double x = bossX + radius * Math.cos(verticalAngle) * Math.cos(horizontalAngle);
            double z = bossZ + radius * Math.cos(verticalAngle) * Math.sin(horizontalAngle);
            double y = bossY + height + radius * Math.sin(verticalAngle);

            return new Vec3(x, y, z);
        }
    }

    private void performLightBeamAttack() {
        // Handle cooldown
        if (lightBeamCooldown > 0) {
            lightBeamCooldown--;
            return;
        }

        // Find players to target
        List<Player> playersInRange = this.level().getEntitiesOfClass(Player.class,
                        new AABB(this.getX() - 60, this.getY() - 50, this.getZ() - 60,
                                this.getX() + 60, this.getY() + 50, this.getZ() + 60))
                .stream()
                .filter(player -> !player.isCreative() && !player.isSpectator())
                .collect(Collectors.toList());

        if (playersInRange.isEmpty()) return;

        // Start attack if not active
        if (!isLightBeamActive) {
            startLightBeamAttack(playersInRange);
            return;
        }

        // Handle active attack
        lightBeamAttackTimer++;

        if (lightBeamAttackTimer <= LIGHT_BEAM_WARNING_TIME) {
            showLightBeamWarning();
        } else if (lightBeamAttackTimer <= LIGHT_BEAM_WARNING_TIME + LIGHT_BEAM_DURATION) {
            executeLightBeams();
        } else {
            endLightBeamAttack();
        }
    }

    private void startLightBeamAttack(List<Player> players) {
        isLightBeamActive = true;
        lightBeamAttackTimer = 0;
        lightBeamPositions.clear();

        // Play dramatic sky rumbling sound
        this.playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 0.3F);

        // Generate beam positions targeting players and random locations
        for (Player player : players) {
            // Target player's current position
            BlockPos playerPos = player.blockPosition();
            lightBeamPositions.put(playerPos, 0);

            // Add some random positions around each player for area coverage
            for (int i = 0; i < 7; i++) {
                int offsetX = this.random.nextInt(21) - 10; // -10 to +10
                int offsetZ = this.random.nextInt(21) - 10;
                BlockPos randomPos = playerPos.offset(offsetX, 0, offsetZ);
                lightBeamPositions.put(randomPos, 0);
            }
        }

        // Add some completely random beam positions for chaos
        BlockPos bossPos = this.blockPosition();
        for (int i = 0; i < 50; i++) { // 50 beams of light to smash yo ass
            int offsetX = this.random.nextInt(101) - 50; // -50 to +50
            int offsetZ = this.random.nextInt(101) - 50;
            BlockPos randomPos = bossPos.offset(offsetX, 0, offsetZ);
            lightBeamPositions.put(randomPos, 0);
        }

        // Announce attack
        announceToPlayers("§6The heavens prepare to rain down divine judgment!");
    }

    private void showLightBeamWarning() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        float warningProgress = (float) lightBeamAttackTimer / LIGHT_BEAM_WARNING_TIME;

        // Show warning indicators high in the sky above each beam position
        for (BlockPos beamPos : lightBeamPositions.keySet()) {
            double warningY = this.getY() + 30 + Math.sin(lightBeamAttackTimer * 0.2) * 2;

            // Create warning light pillar
            for (int i = 0; i < 10; i++) {
                double offsetY = i * 2.0;
                double intensity = warningProgress * (1.0 - (i / 10.0));

                if (intensity > 0.1) {
                    SimpleParticleType particleType = warningProgress > 0.8f ? ParticleTypes.SOUL_FIRE_FLAME :
                            warningProgress > 0.5f ? ParticleTypes.END_ROD : ParticleTypes.ENCHANTED_HIT;

                    serverLevel.sendParticles(particleType,
                            beamPos.getX() + 0.5, warningY + offsetY, beamPos.getZ() + 0.5,
                            (int)(intensity * 3 + 1), 0.2, 0.2, 0.2, 0.02);
                }
            }

            // Ground warning circle
            if (lightBeamAttackTimer % 10 == 0) {
                double radius = 2.0;
                int circleParticles = 16;

                for (int i = 0; i < circleParticles; i++) {
                    double angle = (i / (double) circleParticles) * 2 * Math.PI;
                    double x = beamPos.getX() + 0.5 + Math.cos(angle) * radius;
                    double z = beamPos.getZ() + 0.5 + Math.sin(angle) * radius;

                    // Find ground level
                    BlockPos groundPos = findGroundLevel(new BlockPos((int)x, beamPos.getY(), (int)z));
                    if (groundPos != null) {
                        serverLevel.sendParticles(ParticleTypes.END_ROD,
                                x, groundPos.getY() + 0.1, z, 1, 0, 0, 0, 0);
                    }
                }
            }
        }

        // Play escalating warning sounds
        if (lightBeamAttackTimer % 15 == 0) {
            float pitch = 0.5f + (warningProgress * 1.0f);
            this.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.5f, pitch);
        }
    }

    private void executeLightBeams() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        // Create the light beams
        for (BlockPos beamPos : lightBeamPositions.keySet()) {
            createLightBeam(serverLevel, beamPos);

            // Damage players in beam area
            damagePlayersInLightBeam(beamPos);
        }

        // Play beam sound every few ticks
        if (lightBeamAttackTimer % 10 == 0) {
            this.playSound(SoundEvents.BEACON_POWER_SELECT, 2.0F, 1.5F);
        }
    }

    private void createLightBeam(ServerLevel serverLevel, BlockPos beamCenter) {
        // Find the highest point to start the beam
        double startY = this.getY() + 50;

        // Find ground level to end the beam
        BlockPos groundPos = findGroundLevel(beamCenter);
        double endY = groundPos != null ? groundPos.getY() : beamCenter.getY();

        // Create the beam from sky to ground
        double beamHeight = startY - endY;
        int segments = (int) Math.max(10, beamHeight / 2);

        for (int i = 0; i < segments; i++) {
            double progress = i / (double) segments;
            double currentY = startY - (beamHeight * progress);

            // Main beam particles
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    beamCenter.getX() + 0.5, currentY, beamCenter.getZ() + 0.5,
                    3, 0.1, 0, 0.1, 0.01);

            // Wider beam effect
            if (i % 2 == 0) {
                for (int j = 0; j < 4; j++) {
                    double angle = (j / 4.0) * 2 * Math.PI;
                    double radius = 0.5;
                    double x = beamCenter.getX() + 0.5 + Math.cos(angle) * radius;
                    double z = beamCenter.getZ() + 0.5 + Math.sin(angle) * radius;

                    serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            x, currentY, z, 1, 0.05, 0, 0.05, 0.01);
                }
            }
        }

        // Ground impact effect
        if (groundPos != null) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                    beamCenter.getX() + 0.5, groundPos.getY() + 0.5, beamCenter.getZ() + 0.5,
                    5, 0.5, 0.1, 0.5, 0.1);
        }
    }

    private void damagePlayersInLightBeam(BlockPos beamCenter) {
        double beamRadius = 1.5;

        // Check all Y levels from sky to ground
        BlockPos groundPos = findGroundLevel(beamCenter);
        if (groundPos == null) return;

        double startY = this.getY() + 50;
        double endY = groundPos.getY();

        for (Player player : this.level().getEntitiesOfClass(Player.class,
                new AABB(beamCenter.getX() - beamRadius, endY, beamCenter.getZ() - beamRadius,
                        beamCenter.getX() + beamRadius, startY, beamCenter.getZ() + beamRadius))) {

            if (player.isCreative() || player.isSpectator()) continue;

            double distanceFromBeam = Math.sqrt(
                    Math.pow(player.getX() - (beamCenter.getX() + 0.5), 2) +
                            Math.pow(player.getZ() - (beamCenter.getZ() + 0.5), 2)
            );

            if (distanceFromBeam <= beamRadius) {
                // Deal high damage - this is a powerful attack
                float damage = 8.0F;
                player.hurt(this.damageSources().mobAttack(this), damage);

                // Apply radiance effect
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));

                // Play damage sound
                player.playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0F, 1.5F);
            }
        }
    }

    private void endLightBeamAttack() {
        isLightBeamActive = false;
        lightBeamAttackTimer = 0;
        lightBeamCooldown = LIGHT_BEAM_COOLDOWN;
        lightBeamPositions.clear();

        this.playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0F, 2.0F);
    }

    private void performOrbitalLightAttack() {
        // Handle cooldown
        if (orbitalAttackCooldown > 0) {
            orbitalAttackCooldown--;
            return;
        }

        // Start attack if not active
        if (!isOrbitalAttackActive) {
            startOrbitalAttack();
            return;
        }

        // Handle active attack
        orbitalAttackTimer++;

        // Update orb positions and effects
        updateLightOrbs();

        // End attack when duration is reached
        if (orbitalAttackTimer >= ORBITAL_ATTACK_DURATION) {
            endOrbitalAttack();
        }
    }

    private void startOrbitalAttack() {
        isOrbitalAttackActive = true;
        orbitalAttackTimer = 0;
        lightOrbs.clear();

        // Create orbital light orbs around the boss with varied 3D positions
        for (int i = 0; i < NUM_LIGHT_ORBS; i++) {
            double horizontalAngle = (i / (double) NUM_LIGHT_ORBS) * 2 * Math.PI;
            double verticalAngle = (Math.random() - 0.5) * Math.PI * 0.6; // -54° to +54°
            double radius = ORBITAL_RADIUS * (0.7 + Math.random() * 0.6); // 70% to 130% of base radius
            double height = 8.0 + Math.random() * 4.0; // Vary heights slightly

            lightOrbs.add(new OrbitalLightOrb(horizontalAngle, verticalAngle, radius, height));
        }

        this.playSound(SoundEvents.BEACON_POWER_SELECT, 2.0F, 0.8F);
        announceToPlayers("§eRadiant orbs begin their deadly dance!");
    }

    private void updateLightOrbs() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        Iterator<OrbitalLightOrb> orbIterator = lightOrbs.iterator();
        while (orbIterator.hasNext()) {
            OrbitalLightOrb orb = orbIterator.next();
            orb.life++;

            // Update orb angles (3D orbital motion)
            orb.horizontalAngle += ORB_SPEED * (0.8 + Math.sin(orb.life * 0.05) * 0.4); // Variable speed
            orb.verticalAngle += ORB_SPEED * 0.3 * Math.cos(orb.life * 0.03); // Slower vertical oscillation

            // Gradually change radius for dynamic movement
            orb.radius += Math.sin(orb.life * 0.02) * 0.05;
            orb.radius = Math.max(ORBITAL_RADIUS * 0.5, Math.min(orb.radius, ORBITAL_RADIUS * 1.5));

            // Some orbs occasionally descend to attack players
            if (!orb.isDescending && orb.life > 60 && Math.random() < 0.02) { // 2% chance per tick after 3 seconds
                orb.isDescending = true;
            }

            // Laser attack logic
            if (!orb.isDescending && orb.life > 40 && orb.life % 60 == 0) { // Every 3 seconds after 2 seconds
                Player nearestPlayer = findNearestPlayer(orb);
                if (nearestPlayer != null && hasLineOfSight(orb, nearestPlayer)) {
                    fireLaser(orb, nearestPlayer, serverLevel);
                }
            }

            if (orb.isDescending) {
                orb.height -= 0.3; // Descend speed

                // Check if orb reached ground or hit player
                Vec3 orbPos = orb.getPosition(this.getX(), this.getY(), this.getZ());

                // Check for player collision
                for (Player player : this.level().getEntitiesOfClass(Player.class,
                        new AABB(orbPos.x - 1.5, orbPos.y - 1.5, orbPos.z - 1.5,
                                orbPos.x + 1.5, orbPos.y + 1.5, orbPos.z + 1.5))) {

                    if (!player.isCreative() && !player.isSpectator()) {
                        // Deal damage and remove orb
                        player.hurt(this.damageSources().mobAttack(this), 6.0F);
                        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));

                        // Create impact effect
                        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                                orbPos.x, orbPos.y, orbPos.z, 10, 0.5, 0.5, 0.5, 0.2);

                        player.playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 1.5F);
                        orbIterator.remove();
                        break;
                    }
                }

                // Check if reached ground level
                if (orb.height <= 1.0) {
                    // Create ground explosion
                    Vec3 groundPos = orb.getPosition(this.getX(), this.getY() - orb.height + 1.0, this.getZ());

                    serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                            groundPos.x, groundPos.y, groundPos.z, 8, 1.0, 0.2, 1.0, 0.1);

                    // Damage nearby players
                    for (Player player : this.level().getEntitiesOfClass(Player.class,
                            new AABB(groundPos.x - 3, groundPos.y - 2, groundPos.z - 3,
                                    groundPos.x + 3, groundPos.y + 2, groundPos.z + 3))) {

                        if (!player.isCreative() && !player.isSpectator()) {
                            double distance = player.distanceTo(this);

                            if (distance <= 3.0) {
                                float damage = (float) (4.0F * (1.0 - distance / 3.0));
                                player.hurt(this.damageSources().explosion(this, this), damage);
                            }
                        }
                    }

                    orbIterator.remove();
                }
            }

            // Show orb visual effects
            if (orb.life % 2 == 0) { // Every other tick to reduce particle spam
                Vec3 orbPos = orb.getPosition(this.getX(), this.getY(), this.getZ());

                // Main orb particle
                serverLevel.sendParticles(ParticleTypes.END_ROD,
                        orbPos.x, orbPos.y, orbPos.z, 2, 0.1, 0.1, 0.1, 0.05);

                // Trailing effect
                serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        orbPos.x, orbPos.y, orbPos.z, 1, 0.2, 0.2, 0.2, 0.02);

                // Special effect for descending orbs
                if (orb.isDescending) {
                    serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            orbPos.x, orbPos.y, orbPos.z, 3, 0.3, 0.3, 0.3, 0.1);
                }
            }
        }

        // Play ambient orb sound
        if (orbitalAttackTimer % 40 == 0) {
            this.playSound(SoundEvents.BEACON_AMBIENT, 1.0F, 1.5F);
        }
    }

    private Player findNearestPlayer(OrbitalLightOrb orb) {
        Vec3 orbPos = orb.getPosition(this.getX(), this.getY(), this.getZ());
        Player nearestPlayer = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Player player : this.level().getEntitiesOfClass(Player.class,
                new AABB(orbPos.x - 20, orbPos.y - 20, orbPos.z - 20,
                        orbPos.x + 20, orbPos.y + 20, orbPos.z + 20))) {

            if (!player.isCreative() && !player.isSpectator()) {
                double distance = orbPos.distanceTo(player.position());
                if (distance < nearestDistance && distance <= 15.0) { // 15 block range
                    nearestDistance = distance;
                    nearestPlayer = player;
                }
            }
        }

        return nearestPlayer;
    }

    private boolean hasLineOfSight(OrbitalLightOrb orb, Player player) {
        Vec3 orbPos = orb.getPosition(this.getX(), this.getY(), this.getZ());
        Vec3 playerPos = player.getEyePosition();

        // Simple line of sight check using raycast
        BlockHitResult hitResult = this.level().clip(new ClipContext(
                orbPos, playerPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this));

        return hitResult.getType() == HitResult.Type.MISS ||
                hitResult.getLocation().distanceTo(playerPos) < 1.0;
    }

    private void fireLaser(OrbitalLightOrb orb, Player target, ServerLevel serverLevel) {
        Vec3 orbPos = orb.getPosition(this.getX(), this.getY(), this.getZ());
        Vec3 targetPos = target.getEyePosition();

        // Create laser beam effect with particles
        Vec3 direction = targetPos.subtract(orbPos).normalize();
        double distance = orbPos.distanceTo(targetPos);

        // Create particle trail for laser
        for (double d = 0; d < distance; d += 0.5) {
            Vec3 particlePos = orbPos.add(direction.scale(d));
            serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    particlePos.x, particlePos.y, particlePos.z, 1, 0.05, 0.05, 0.05, 0.01);

            // Add some electric effect
            if (Math.random() < 0.3) {
                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        particlePos.x, particlePos.y, particlePos.z, 1, 0.1, 0.1, 0.1, 0.02);
            }
        }

        // Deal damage to target
        target.hurt(this.damageSources().mobAttack(this), 4.0F);
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));

        // Sound and visual effects
        this.playSound(SoundEvents.GUARDIAN_ATTACK, 1.0F, 1.8F);
        serverLevel.sendParticles(ParticleTypes.CRIT,
                targetPos.x, targetPos.y, targetPos.z, 8, 0.5, 0.5, 0.5, 0.2);

        // Mark the orb as having fired recently to prevent spam
        orb.lastLaserTime = orb.life;
    }

    private void endOrbitalAttack() {
        isOrbitalAttackActive = false;
        orbitalAttackTimer = 0;
        orbitalAttackCooldown = ORBITAL_ATTACK_COOLDOWN;

        // Create final explosion effect for remaining orbs
        if (this.level() instanceof ServerLevel serverLevel) {
            for (OrbitalLightOrb orb : lightOrbs) {
                Vec3 orbPos = orb.getPosition(this.getX(), this.getY(), this.getZ());
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        orbPos.x, orbPos.y, orbPos.z, 5, 0.5, 0.5, 0.5, 0.2);
            }
        }

        lightOrbs.clear();
        this.playSound(SoundEvents.BEACON_DEACTIVATE, 1.5F, 1.2F);
    }

    private void performRadiantBurstAttack() {
        // Handle cooldown
        if (radiantBurstCooldown > 0) {
            radiantBurstCooldown--;
            return;
        }

        // Find players for targeting
        List<Player> playersInRange = this.level().getEntitiesOfClass(Player.class,
                        new AABB(this.getX() - 50, this.getY() - 30, this.getZ() - 50,
                                this.getX() + 50, this.getY() + 30, this.getZ() + 50))
                .stream()
                .filter(player -> !player.isCreative() && !player.isSpectator())
                .collect(Collectors.toList());

        if (playersInRange.isEmpty()) return;

        // Start attack if not active
        if (!isRadiantBurstActive) {
            startRadiantBurstAttack(playersInRange);
            return;
        }

        // Handle active attack
        radiantBurstTimer++;

        if (radiantBurstTimer <= RADIANT_BURST_WARNING) {
            showRadiantBurstWarning();
        } else if (radiantBurstTimer <= RADIANT_BURST_WARNING + RADIANT_BURST_DURATION) {
            executeRadiantBursts();
        } else {
            endRadiantBurstAttack();
        }
    }

    private void startRadiantBurstAttack(List<Player> players) {
        isRadiantBurstActive = true;
        radiantBurstTimer = 0;
        radiantBurstZones.clear();

        // Create burst zones - some following players, some random
        for (Player player : players) {
            // Add zones around each player
            BlockPos playerPos = player.blockPosition();

            // Multiple bursts per player area
            for (int i = 0; i < 4; i++) {
                int offsetX = this.random.nextInt(15) - 7; // -7 to +7
                int offsetZ = this.random.nextInt(15) - 7;
                BlockPos burstPos = playerPos.offset(offsetX, 0, offsetZ);
                radiantBurstZones.add(burstPos);
            }
        }

        // Add some random burst zones across the arena
        BlockPos bossPos = this.blockPosition();
        for (int i = 0; i < 10; i++) {
            int offsetX = this.random.nextInt(61) - 30; // -30 to +30
            int offsetZ = this.random.nextInt(61) - 30;
            BlockPos randomPos = bossPos.offset(offsetX, 0, offsetZ);
            radiantBurstZones.add(randomPos);
        }

        this.playSound(SoundEvents.WITHER_SPAWN, 2.0F, 0.4F);
        announceToPlayers("§4The very air begins to burn with divine wrath!");
    }

    private void showRadiantBurstWarning() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        float warningProgress = (float) radiantBurstTimer / RADIANT_BURST_WARNING;

        for (BlockPos burstPos : radiantBurstZones) {
            // Find ground level for this position
            BlockPos groundPos = findGroundLevel(burstPos);
            if (groundPos == null) continue;

            // Warning pillar effect
            double pillarHeight = 8.0 * warningProgress;
            for (int i = 0; i < pillarHeight; i++) {
                double y = groundPos.getY() + i;

                SimpleParticleType particleType = warningProgress > 0.8f ? ParticleTypes.SOUL_FIRE_FLAME :
                        warningProgress > 0.5f ? ParticleTypes.FLAME : ParticleTypes.SMOKE;

                serverLevel.sendParticles(particleType,
                        burstPos.getX() + 0.5, y, burstPos.getZ() + 0.5,
                        1, 0.2, 0.1, 0.2, 0.02);
            }

            // Ground warning circle
            if (radiantBurstTimer % 8 == 0) {
                double radius = 3.0 * warningProgress;
                int particles = Math.max(8, (int)(radius * 4));

                for (int i = 0; i < particles; i++) {
                    double angle = (i / (double) particles) * 2 * Math.PI;
                    double x = burstPos.getX() + 0.5 + Math.cos(angle) * radius;
                    double z = burstPos.getZ() + 0.5 + Math.sin(angle) * radius;

                    serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                            x, groundPos.getY() + 0.1, z, 1, 0, 0, 0, 0);
                }
            }
        }

        // Warning sounds
        if (radiantBurstTimer % 12 == 0) {
            float pitch = 0.8f + (warningProgress * 0.6f);
            this.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f + warningProgress, pitch);
        }
    }

    private void executeRadiantBursts() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        for (BlockPos burstPos : radiantBurstZones) {
            BlockPos groundPos = findGroundLevel(burstPos);
            if (groundPos == null) continue;

            // Create the radiant burst effect
            createRadiantBurst(serverLevel, groundPos);

            // Damage players in burst area
            damagePlayersInRadiantBurst(groundPos);
        }

        // Play burst sound
        if (radiantBurstTimer % 15 == 0) {
            this.playSound(SoundEvents.BEACON_POWER_SELECT, 2.0F, 2.0F);
        }
    }

    private void createRadiantBurst(ServerLevel serverLevel, BlockPos center) {
        // Main burst explosion
        serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5,
                20, 1.5, 1.0, 1.5, 0.3);

        // Expanding rings of light
        for (int ring = 0; ring < 3; ring++) {
            double radius = 1.0 + (ring * 1.5);
            int particles = (int)(radius * 8);

            for (int i = 0; i < particles; i++) {
                double angle = (i / (double) particles) * 2 * Math.PI;
                double x = center.getX() + 0.5 + Math.cos(angle) * radius;
                double z = center.getZ() + 0.5 + Math.sin(angle) * radius;

                serverLevel.sendParticles(ParticleTypes.END_ROD,
                        x, center.getY() + 0.2, z, 2, 0, 0.5, 0, 0.1);
            }
        }

        // Upward light pillars
        for (int i = 0; i < 12; i++) {
            double height = i * 0.8;
            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    center.getX() + 0.5, center.getY() + height, center.getZ() + 0.5,
                    3, 0.3, 0.1, 0.3, 0.05);
        }
    }


    private void damagePlayersInRadiantBurst(BlockPos center) {
        double burstRadius = 3.5; // Slightly larger than visual effect for better hit detection

        // Create damage area from ground up to account for players at different heights
        double minY = center.getY();
        double maxY = center.getY() + 4; // Allow some vertical range

        AABB damageArea = new AABB(
                center.getX() - burstRadius, minY, center.getZ() - burstRadius,
                center.getX() + burstRadius, maxY, center.getZ() + burstRadius
        );

        for (Player player : this.level().getEntitiesOfClass(Player.class, damageArea)) {
            if (player.isCreative() || player.isSpectator()) continue;

            // Calculate distance from burst center (2D distance for ground-based attack)
            double distanceFromCenter = Math.sqrt(
                    Math.pow(player.getX() - (center.getX() + 0.5), 2) +
                            Math.pow(player.getZ() - (center.getZ() + 0.5), 2)
            );

            if (distanceFromCenter <= burstRadius) {
                // Distance-based damage scaling (more damage closer to center)
                double damageMultiplier = Math.max(0.3, 1.0 - (distanceFromCenter / burstRadius));
                float damage = (float)(7.0F * damageMultiplier);

                // Apply damage
                player.hurt(this.damageSources().mobAttack(this), damage);

                // Apply burning effect based on proximity
                int burnDuration = (int)(60 * damageMultiplier); // 3 seconds max
                if (burnDuration > 0) {
                    player.setRemainingFireTicks(burnDuration / 20);
                }

                // Apply weakness effect
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));

                // Knockback effect - push players away from burst center
                if (distanceFromCenter > 0.1) { // Avoid division by zero
                    double knockbackForce = 0.8 * damageMultiplier;
                    double deltaX = player.getX() - (center.getX() + 0.5);
                    double deltaZ = player.getZ() - (center.getZ() + 0.5);

                    // Normalize and apply knockback
                    double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                    deltaX = (deltaX / distance) * knockbackForce;
                    deltaZ = (deltaZ / distance) * knockbackForce;

                    player.push(deltaX, 0.3, deltaZ); // Small upward push too
                }

                // Visual and audio feedback for hit player
                player.playSound(SoundEvents.BLAZE_HURT, 1.0F, 1.2F);

                // Create impact particles around the player
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.CRIT,
                            player.getX(), player.getY() + 1, player.getZ(),
                            8, 0.5, 0.5, 0.5, 0.1);
                }
            }
        }
    }

    private void endRadiantBurstAttack() {
        isRadiantBurstActive = false;
        radiantBurstTimer = 0;
        radiantBurstCooldown = RADIANT_BURST_COOLDOWN;

        // Create final spectacular effect
        if (this.level() instanceof ServerLevel serverLevel) {
            // Final explosion at boss position
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    this.getX(), this.getY() + 2, this.getZ(),
                    30, 3.0, 2.0, 3.0, 0.5);

            // Create ascending light spirals around remaining burst zones
            for (BlockPos burstPos : radiantBurstZones) {
                BlockPos groundPos = findGroundLevel(burstPos);
                if (groundPos != null) {
                    // Spiral effect
                    for (int i = 0; i < 20; i++) {
                        double angle = (i / 20.0) * 4 * Math.PI; // 2 full rotations
                        double height = i * 0.5;
                        double radius = 1.0 + (i * 0.1);

                        double x = groundPos.getX() + 0.5 + Math.cos(angle) * radius;
                        double z = groundPos.getZ() + 0.5 + Math.sin(angle) * radius;
                        double y = groundPos.getY() + height;

                        serverLevel.sendParticles(ParticleTypes.END_ROD,
                                x, y, z, 1, 0, 0, 0, 0.05);
                    }

                    // Final upward burst from each zone
                    serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                            groundPos.getX() + 0.5, groundPos.getY() + 0.5, groundPos.getZ() + 0.5,
                            10, 0.8, 3.0, 0.8, 0.3);
                }
            }
        }

        // Clear burst zones
        radiantBurstZones.clear();

        // Play ending sound
        this.playSound(SoundEvents.WITHER_DEATH, 1.5F, 1.8F);

        // Optional: Announce attack completion
        announceToPlayers("§6The divine flames subside... for now.");
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