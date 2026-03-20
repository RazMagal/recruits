package com.talhanation.recruits.entities.ai;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.entities.MortarProjectileEntity;
import com.talhanation.recruits.items.MortarRoundItem;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

public class RecruitRangedMortarAttackGoal extends Goal {

    private final AbstractRecruitEntity recruit;
    private final double speedModifier;
    private final float maxRange;
    private final float maxRangeSqr;
    private final float minRange;
    private final float minRangeSqr;
    private int attackCooldown;
    private int seeTime;

    public RecruitRangedMortarAttackGoal(AbstractRecruitEntity recruit, double speedModifier, float minRange, float maxRange) {
        this.recruit = recruit;
        this.speedModifier = speedModifier;
        this.minRange = minRange;
        this.minRangeSqr = minRange * minRange;
        this.maxRange = maxRange;
        this.maxRangeSqr = maxRange * maxRange;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.recruit.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (this.recruit.getFleeing()) return false;
        return hasMortarRounds();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void stop() {
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

        // Move toward target if too far, flee if too close
        if (distSqr < (double) this.minRangeSqr) {
            // Too close — back away
            double dx = this.recruit.getX() - target.getX();
            double dz = this.recruit.getZ() - target.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0) {
                double fleeX = this.recruit.getX() + (dx / dist) * 10;
                double fleeZ = this.recruit.getZ() + (dz / dist) * 10;
                this.recruit.getNavigation().moveTo(fleeX, this.recruit.getY(), fleeZ, this.speedModifier);
            }
        } else if (distSqr > (double) this.maxRangeSqr || !canSee) {
            this.recruit.getNavigation().moveTo(target, this.speedModifier);
        } else {
            this.recruit.getNavigation().stop();
        }

        this.recruit.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (this.attackCooldown > 0) {
            this.attackCooldown--;
            return;
        }

        if (canSee && distSqr >= (double) this.minRangeSqr && distSqr <= (double) this.maxRangeSqr && this.seeTime >= 10) {
            this.fireMortar(target);
            this.attackCooldown = 80 + this.recruit.getRandom().nextInt(40); // 4-6 seconds
        }
    }

    private void fireMortar(LivingEntity target) {
        if (this.recruit.level().isClientSide()) return;

        // Add intentional inaccuracy — ~20 block spread
        double inaccuracyX = this.recruit.getRandom().nextGaussian() * 10.0;
        double inaccuracyZ = this.recruit.getRandom().nextGaussian() * 10.0;

        double targetX = target.getX() + inaccuracyX;
        double targetZ = target.getZ() + inaccuracyZ;
        double targetY = target.getY();

        double dx = targetX - this.recruit.getX();
        double dy = targetY - this.recruit.getEyeY();
        double dz = targetZ - this.recruit.getZ();
        double horizontalDist = Mth.sqrt((float) (dx * dx + dz * dz));

        MortarProjectileEntity mortar = new MortarProjectileEntity(this.recruit.level(), this.recruit);
        // High arc trajectory
        mortar.shoot(dx, dy + horizontalDist * 0.6, dz, 1.5F, 0F);
        this.recruit.level().addFreshEntity(mortar);

        this.recruit.playSound(SoundEvents.GENERIC_EXPLODE, 0.8F, 1.5F);

        // Consume mortar round
        consumeMortarRound();
    }

    private void consumeMortarRound() {
        for (ItemStack itemStack : this.recruit.getInventory().items) {
            if (itemStack.getItem() instanceof MortarRoundItem) {
                itemStack.shrink(1);
                break;
            }
        }
    }

    private boolean hasMortarRounds() {
        for (ItemStack itemStack : this.recruit.getInventory().items) {
            if (itemStack.getItem() instanceof MortarRoundItem && !itemStack.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
