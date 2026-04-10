package com.talhanation.recruits.entities;

import com.talhanation.recruits.init.ModEntityTypes;
import com.talhanation.recruits.init.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkHooks;

public class ThrownRockEntity extends ThrowableItemProjectile {

    public ThrownRockEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level level) {
        super(entityType, level);
    }

    public ThrownRockEntity(Level level, LivingEntity shooter) {
        super(ModEntityTypes.THROWN_ROCK.get(), shooter, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.ROCK.get();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity entity = result.getEntity();
        entity.hurt(this.damageSources().thrown(this, this.getOwner()), 2.0F);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);

        if (!this.level().isClientSide) {
            ServerLevel serverLevel = (ServerLevel) this.level();

            serverLevel.sendParticles(ParticleTypes.CRIT,
                    this.getX(), this.getY(), this.getZ(),
                    5, 0.1, 0.1, 0.1, 0.02);

            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.STONE_HIT, SoundSource.NEUTRAL, 0.6F, 1.2F);

            this.discard();
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
