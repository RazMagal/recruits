package com.talhanation.recruits.entities.ai;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.entities.RocketProjectileEntity;
import com.talhanation.recruits.items.RocketItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

public class RecruitRangedRocketAttackGoal extends Goal {

    private final AbstractRecruitEntity recruit;
    private final double speedModifier;
    private final float attackRadius;
    private final float attackRadiusSqr;
    private int attackCooldown;
    private int seeTime;

    private static final int COOLDOWN_TICKS = 160; // 8 seconds

    public RecruitRangedRocketAttackGoal(AbstractRecruitEntity recruit, double speedModifier, float attackRadius) {
        this.recruit = recruit;
        this.speedModifier = speedModifier;
        this.attackRadius = attackRadius;
        this.attackRadiusSqr = attackRadius * attackRadius;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.recruit.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (this.recruit.getFleeing()) return false;
        return hasRockets();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        this.seeTime = 0;
        this.recruit.setAggressive(false);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.recruit.getTarget();
        if (target == null) return;

        double distSqr = this.recruit.distanceToSqr(target.getX(), target.getY(), target.getZ());
        boolean canSee = this.recruit.getSensing().hasLineOfSight(target);

        if (canSee) {
            this.seeTime++;
        } else {
            this.seeTime = 0;
        }

        // Move toward target if out of range
        if (distSqr > (double) this.attackRadiusSqr || !canSee) {
            this.recruit.getNavigation().moveTo(target, this.speedModifier);
        } else {
            this.recruit.getNavigation().stop();
        }

        this.recruit.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (this.attackCooldown > 0) {
            this.attackCooldown--;
            return;
        }

        if (canSee && distSqr <= (double) this.attackRadiusSqr && this.seeTime >= 5) {
            this.fireRocket(target);
            this.attackCooldown = COOLDOWN_TICKS;
        }
    }

    private void fireRocket(LivingEntity target) {
        if (this.recruit.level().isClientSide()) return;

        double dx = target.getX() - this.recruit.getX();
        double dy = target.getY(0.5) - this.recruit.getY(0.5);
        double dz = target.getZ() - this.recruit.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist == 0) return;

        // Normalize and scale for rocket speed
        double speed = 1.5;
        RocketProjectileEntity rocket = new RocketProjectileEntity(
                this.recruit.level(), this.recruit,
                dx / dist * speed, dy / dist * speed, dz / dist * speed
        );
        rocket.setPos(this.recruit.getX(), this.recruit.getEyeY() - 0.1, this.recruit.getZ());
        this.recruit.level().addFreshEntity(rocket);

        this.recruit.playSound(net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0F, 1.0F);

        // Consume rocket ammo
        consumeRocket();
    }

    private void consumeRocket() {
        for (ItemStack itemStack : this.recruit.getInventory().items) {
            if (itemStack.getItem() instanceof RocketItem) {
                itemStack.shrink(1);
                break;
            }
        }
    }

    private boolean hasRockets() {
        for (ItemStack itemStack : this.recruit.getInventory().items) {
            if (itemStack.getItem() instanceof RocketItem && !itemStack.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
