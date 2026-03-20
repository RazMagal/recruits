package com.talhanation.recruits.entities;

import com.talhanation.recruits.init.ModEntityTypes;
import com.talhanation.recruits.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class BackTabEntity extends ThrowableItemProjectile {

    private static final int FIRE_RADIUS = 3;

    public BackTabEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level level) {
        super(entityType, level);
    }

    public BackTabEntity(Level level, LivingEntity shooter) {
        super(ModEntityTypes.BACKTAB_PROJECTILE.get(), shooter, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.BACKTAB.get();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        result.getEntity().setSecondsOnFire(5);
        createFireArea(this.level(), this.blockPosition());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        createFireArea(this.level(), result.getBlockPos().relative(result.getDirection()));
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);

        if (!this.level().isClientSide) {
            ServerLevel serverLevel = (ServerLevel) this.level();

            serverLevel.sendParticles(ParticleTypes.FLAME,
                    this.getX(), this.getY(), this.getZ(),
                    40, 0.5, 0.5, 0.5, 0.05);

            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    this.getX(), this.getY(), this.getZ(),
                    20, 0.5, 0.5, 0.5, 0.05);

            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);

            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);

            this.discard();
        }
    }

    private void createFireArea(Level level, BlockPos center) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) return;

        for (int x = -FIRE_RADIUS; x <= FIRE_RADIUS; x++) {
            for (int z = -FIRE_RADIUS; z <= FIRE_RADIUS; z++) {
                if (x * x + z * z > FIRE_RADIUS * FIRE_RADIUS) continue;

                for (int y = 1; y >= -1; y--) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockPos below = pos.below();

                    if (level.isEmptyBlock(pos) && level.getBlockState(below).isSolidRender(level, below)) {
                        BlockState fireState = BaseFireBlock.getState(level, pos);
                        level.setBlockAndUpdate(pos, fireState);
                        break;
                    }
                }
            }
        }
    }
}
