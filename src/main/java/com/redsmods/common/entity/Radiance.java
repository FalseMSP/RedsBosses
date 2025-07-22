package com.redsmods.common.entity;

import com.redsmods.common.SpiralStructureBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

    // Air slam attack tracking
    private final Map<UUID, Integer> playerAirTime = new HashMap<>();
    private static final int AIR_SLAM_THRESHOLD = 40; // 2 seconds at 20 ticks per second
    private static final double SLAM_RANGE = 128.0; // Range to detect and slam players

    public Radiance(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        // Set boss bar properties
        this.bossEvent.setDarkenScreen(true);
        this.bossEvent.setPlayBossMusic(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0D) // Increased health for boss
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
            // different phases.
            performAirSlamAttack();
        }

        // Break blocks within hitbox
        breakBlocksInHitbox();

        // Update boss bar
        updateBossBar();
    }

    private void performAirSlamAttack() {
        // Find all players within range
        for (Player player : this.level().getEntitiesOfClass(Player.class,
                new AABB(this.getX() - SLAM_RANGE, this.getY() - SLAM_RANGE, this.getZ() - SLAM_RANGE,
                        this.getX() + SLAM_RANGE, this.getY() + SLAM_RANGE, this.getZ() + SLAM_RANGE))) {

            UUID playerId = player.getUUID();

            // Check if player is on ground
            if (player.onGround()) {
                // Reset air time if on ground
                playerAirTime.remove(playerId);
            } else {
                // Increment air time
                int currentAirTime = playerAirTime.getOrDefault(playerId, 0);
                currentAirTime++;
                playerAirTime.put(playerId, currentAirTime);

                // Check if player has been in air too long
                if (currentAirTime >= AIR_SLAM_THRESHOLD) {
                    slamPlayer(player);
                    playerAirTime.remove(playerId); // Reset after slam
                }
            }
        }
    }

    private void slamPlayer(Player player) {
        // Create downward velocity to slam player to ground
        Vec3 slamVelocity = new Vec3(0, -2.0, 0); // Strong downward force
        player.setDeltaMovement(slamVelocity);

        // Deal damage
        player.hurt(this.damageSources().mobAttack(this), 6.0F);

        // Play slam sound
        this.playSound(SoundEvents.ANVIL_LAND, 1.0F, 0.8F);

        // Visual effect - you could add particle effects here if desired
        // For now, we'll just play a sound at the player's location
        player.playSound(SoundEvents.PLAYER_HURT, 1.0F, 1.0F);
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
    }

    // Load groundY from NBT data
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