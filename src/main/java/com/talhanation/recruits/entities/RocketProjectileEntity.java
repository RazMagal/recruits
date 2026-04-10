package com.talhanation.recruits.entities;

import com.talhanation.recruits.init.ModEntityTypes;
import com.talhanation.recruits.init.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkHooks;

public class RocketProjectileEntity extends AbstractHurtingProjectile implements ItemSupplier {

    public RocketProjectileEntity(EntityType<? extends AbstractHurtingProjectile> entityType, Level level) {
        super(entityType, level);
    }

    public RocketProjectileEntity(Level level, LivingEntity shooter, double dx, double dy, double dz) {
        super(ModEntityTypes.ROCKET_PROJECTILE.get(), shooter, dx, dy, dz, level);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!this.level().isClientSide) {
            result.getEntity().hurt(this.damageSources().mobProjectile(this, (LivingEntity) this.getOwner()), 8.0F);
            result.getEntity().setSecondsOnFire(3);
            explode();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide) {
            explode();
        }
    }

    private void explode() {
        this.level().explode(this, this.getX(), this.getY(), this.getZ(), 1.5F, Level.ExplosionInteraction.NONE);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            ServerLevel serverLevel = (ServerLevel) this.level();

            serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                    this.getX(), this.getY(), this.getZ(),
                    10, 0.5, 0.5, 0.5, 0.05);

            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    this.getX(), this.getY(), this.getZ(),
                    20, 0.5, 0.5, 0.5, 0.1);

            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 1.0F);

            this.discard();
        }
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    private static final ItemStack ROCKET_STACK = new ItemStack(ModItems.ROCKET_ITEM.get());

    @Override
    public ItemStack getItem() {
        return ROCKET_STACK;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
