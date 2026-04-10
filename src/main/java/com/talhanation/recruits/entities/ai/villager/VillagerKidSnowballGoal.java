package com.talhanation.recruits.entities.ai.villager;

import com.talhanation.recruits.config.RecruitsServerConfig;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import com.talhanation.recruits.entities.ThrownRockEntity;

import java.util.List;

public class VillagerKidSnowballGoal extends Goal {

    private final Villager villager;
    private LivingEntity target;
    private int throwCooldown;

    public VillagerKidSnowballGoal(Villager villager) {
        this.villager = villager;
    }

    @Override
    public boolean canUse() {
        if (!this.villager.isBaby()) return false;
        if (!RecruitsServerConfig.VillageKidsThrowSnowballs.get()) return false;
        if (this.villager.level().isClientSide()) return false;

        List<LivingEntity> nearby = this.villager.level().getEntitiesOfClass(
                LivingEntity.class,
                this.villager.getBoundingBox().inflate(16),
                e -> e.isAlive() && (e instanceof Player || e instanceof AbstractRecruitEntity) && !isSameTeam(e)
        );

        if (nearby.isEmpty()) return false;

        // Pick closest
        LivingEntity closest = nearby.stream()
                .min((a, b) -> Double.compare(
                        this.villager.distanceToSqr(a),
                        this.villager.distanceToSqr(b)))
                .orElse(null);

        if (closest == null) return false;
        this.target = closest;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.target != null
                && this.target.isAlive()
                && this.villager.isBaby()
                && this.villager.distanceToSqr(this.target) < 16 * 16
                && !isSameTeam(this.target);
    }

    @Override
    public void start() {
        this.throwCooldown = 40 + this.villager.getRandom().nextInt(40);
    }

    @Override
    public void stop() {
        this.target = null;
        this.throwCooldown = 0;
    }

    @Override
    public void tick() {
        if (this.target == null) return;

        this.villager.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        if (this.throwCooldown > 0) {
            this.throwCooldown--;
            return;
        }

        // Throw snowball
        double dx = this.target.getX() - this.villager.getX();
        double dy = this.target.getEyeY() - this.villager.getEyeY();
        double dz = this.target.getZ() - this.villager.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        ThrownRockEntity rock = new ThrownRockEntity(this.villager.level(), this.villager);
        rock.shoot(dx, dy + dist * 0.1, dz, 0.7F, 10.0F);
        this.villager.level().addFreshEntity(rock);

        this.villager.playSound(SoundEvents.SNOW_GOLEM_SHOOT, 0.5F, 1.2F);

        // Reset cooldown — 3-7 seconds between throws
        this.throwCooldown = 60 + this.villager.getRandom().nextInt(80);
    }

    private boolean isSameTeam(LivingEntity entity) {
        if (this.villager.getTeam() == null) return false;
        return this.villager.getTeam().equals(entity.getTeam());
    }
}
