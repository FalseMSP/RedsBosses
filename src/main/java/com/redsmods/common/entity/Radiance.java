package com.redsmods.common.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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

import java.util.Arrays;

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
    private float floatTimer = 0.0F;
    private double groundY = -1; // Store the ground level
    private PHASE state = PHASE.DEACTIVATED_IDOL;
    private PHASE[] invulnerablePhases = {PHASE.DEACTIVATED_IDOL,PHASE.ARENA_BUILDING,PHASE.TRANSITION_TO_RADIANCE,PHASE.TRANSITION_TO_TRUE};

    public Radiance(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    protected void registerGoals() {

    }

    // Make the mob completely invulnerable to all damage
    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        if (damageSource.is(DamageTypes.GENERIC_KILL) || !Arrays.asList(invulnerablePhases).contains(this.state)) {
            return super.hurt(damageSource, amount);
        }
        return false; // Always return false to prevent any damage
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
            this.moveTo(this.getX(), groundY, this.getZ());
        } else if (this.state == PHASE.ACTIVATED_IDOL) {
            this.moveTo(this.getX(), groundY+1, this.getZ());
        }

        // Break blocks within hitbox
        breakBlocksInHitbox();
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