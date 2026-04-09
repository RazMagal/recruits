package com.talhanation.recruits.entities.ai;

import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.recruits.world.RecruitsDiplomacyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class SaboteurPlaceTNTGoal extends Goal {

    private final AbstractRecruitEntity saboteur;
    private BlockPos targetPos;
    private BlockPos tntPlacedPos;
    private State state;
    private int stateTimer;

    private static final int FUSE_TICKS = 80; // 4 seconds

    private enum State {
        IDLE, INFILTRATING, PLACING, IGNITING, FLEEING
    }

    public SaboteurPlaceTNTGoal(AbstractRecruitEntity saboteur) {
        this.saboteur = saboteur;
        this.state = State.IDLE;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.saboteur.getFleeing()) return false;
        if (!hasTNT()) return false;

        if (this.targetPos == null) {
            this.targetPos = findEnemyClaimCenter();
        }
        return this.targetPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.state == State.FLEEING && this.stateTimer <= 0) return false;
        if (this.state == State.IDLE) return false;
        return true;
    }

    @Override
    public void start() {
        this.state = State.INFILTRATING;
        this.stateTimer = 0;
        this.saboteur.getNavigation().moveTo(targetPos.getX(), targetPos.getY(), targetPos.getZ(), 1.2D);
    }

    @Override
    public void stop() {
        this.state = State.IDLE;
        this.targetPos = null;
        this.tntPlacedPos = null;
        this.stateTimer = 0;
        this.saboteur.setShiftKeyDown(false);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        switch (this.state) {
            case INFILTRATING -> tickInfiltrating();
            case PLACING -> tickPlacing();
            case IGNITING -> tickIgniting();
            case FLEEING -> tickFleeing();
        }
    }

    private void tickInfiltrating() {
        if (this.targetPos == null) {
            this.state = State.IDLE;
            return;
        }

        double distSqr = this.saboteur.blockPosition().distSqr(this.targetPos);

        // Crouch when getting close
        this.saboteur.setShiftKeyDown(distSqr < 24 * 24);

        // Re-path periodically
        if (this.saboteur.getNavigation().isDone() || this.stateTimer % 40 == 0) {
            this.saboteur.getNavigation().moveTo(targetPos.getX(), targetPos.getY(), targetPos.getZ(), 1.2D);
        }
        this.stateTimer++;

        // Within 16 blocks — start placing
        if (distSqr < 16 * 16) {
            this.state = State.PLACING;
            this.stateTimer = 0;
        }

        // Timeout after 30 seconds of infiltrating
        if (this.stateTimer > 600) {
            this.state = State.IDLE;
        }
    }

    private void tickPlacing() {
        BlockPos sabPos = this.saboteur.blockPosition();
        BlockPos placePos = null;

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                BlockPos candidate = sabPos.offset(dx, 0, dz);
                BlockPos below = candidate.below();
                if (this.saboteur.level().isEmptyBlock(candidate)
                        && this.saboteur.level().getBlockState(below).isSolidRender(this.saboteur.level(), below)) {
                    placePos = candidate;
                    break;
                }
            }
            if (placePos != null) break;
        }

        if (placePos != null) {
            // Place the TNT block
            this.saboteur.level().setBlock(placePos, Blocks.TNT.defaultBlockState(), 3);
            this.tntPlacedPos = placePos;
            consumeTNT();

            // Visual: swing arm + look at TNT
            this.saboteur.swing(InteractionHand.MAIN_HAND);
            this.saboteur.getLookControl().setLookAt(placePos.getX() + 0.5, placePos.getY() + 0.5, placePos.getZ() + 0.5);

            // Audio: block place sound
            this.saboteur.level().playSound(null, placePos,
                    SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);

            this.state = State.IGNITING;
            this.stateTimer = FUSE_TICKS;
        } else {
            this.state = State.IDLE;
        }
    }

    private void tickIgniting() {
        if (this.tntPlacedPos == null) {
            this.state = State.IDLE;
            return;
        }

        // Saboteur stares at the TNT
        this.saboteur.getLookControl().setLookAt(
                tntPlacedPos.getX() + 0.5, tntPlacedPos.getY() + 0.5, tntPlacedPos.getZ() + 0.5);
        this.saboteur.getNavigation().stop();

        // Play TNT hiss on first tick
        if (this.stateTimer == FUSE_TICKS) {
            this.saboteur.level().playSound(null, tntPlacedPos,
                    SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        // Smoke particles every few ticks
        if (this.stateTimer % 4 == 0 && this.saboteur.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    tntPlacedPos.getX() + 0.5, tntPlacedPos.getY() + 1.0, tntPlacedPos.getZ() + 0.5,
                    3, 0.2, 0.1, 0.2, 0.01);
        }

        this.stateTimer--;

        // Fuse done — detonate
        if (this.stateTimer <= 0) {
            if (this.saboteur.level().getBlockState(this.tntPlacedPos).is(Blocks.TNT)) {
                TntBlock.explode(this.saboteur.level(), this.tntPlacedPos);
                this.saboteur.level().removeBlock(this.tntPlacedPos, false);
            }
            this.saboteur.setShiftKeyDown(false);
            this.state = State.FLEEING;
            this.stateTimer = 100;
            flee();
        }
    }

    private void tickFleeing() {
        this.stateTimer--;
        if (this.stateTimer % 20 == 0) {
            flee();
        }
    }

    private void flee() {
        if (this.tntPlacedPos == null) return;
        Vec3 fleeDir = this.saboteur.position().subtract(Vec3.atCenterOf(this.tntPlacedPos)).normalize();
        double fleeX = this.saboteur.getX() + fleeDir.x * 20;
        double fleeZ = this.saboteur.getZ() + fleeDir.z * 20;
        this.saboteur.getNavigation().moveTo(fleeX, this.saboteur.getY(), fleeZ, 1.3D);
    }

    private BlockPos findEnemyClaimCenter() {
        if (ClaimEvents.recruitsClaimManager == null) return null;
        String ownTeam = this.saboteur.getTeam() != null ? this.saboteur.getTeam().getName() : null;
        if (ownTeam == null) return null;

        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;

        for (RecruitsClaim claim : ClaimEvents.recruitsClaimManager.getAllClaims()) {
            if (claim.getOwnerFaction() == null) continue;
            String claimFaction = claim.getOwnerFaction().getStringID();

            if (claimFaction.equals(ownTeam)) continue;

            RecruitsDiplomacyManager.DiplomacyStatus relation =
                    FactionEvents.recruitsDiplomacyManager.getRelation(ownTeam, claimFaction);
            if (relation != RecruitsDiplomacyManager.DiplomacyStatus.ENEMY) continue;

            BlockPos center = claim.getCenter().getMiddleBlockPosition(0);
            double dist = this.saboteur.blockPosition().distSqr(center);
            if (dist < closestDist && dist < 200 * 200) {
                closestDist = dist;
                closest = center;
            }
        }
        return closest;
    }

    private boolean hasTNT() {
        for (ItemStack itemStack : this.saboteur.getInventory().items) {
            if (itemStack.is(Items.TNT) && !itemStack.isEmpty()) return true;
        }
        return false;
    }

    private void consumeTNT() {
        for (ItemStack itemStack : this.saboteur.getInventory().items) {
            if (itemStack.is(Items.TNT)) {
                itemStack.shrink(1);
                break;
            }
        }
    }
}
