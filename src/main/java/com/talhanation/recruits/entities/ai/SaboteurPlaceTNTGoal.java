package com.talhanation.recruits.entities.ai;

import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.recruits.world.RecruitsDiplomacyManager;
import net.minecraft.core.BlockPos;
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

        // Find nearby enemy claim if we don't have a target
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
        // Find a suitable spot to place TNT
        BlockPos sabPos = this.saboteur.blockPosition();
        BlockPos placePos = null;

        // Search in 3-block radius for a valid placement
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
            this.saboteur.level().setBlock(placePos, Blocks.TNT.defaultBlockState(), 3);
            this.tntPlacedPos = placePos;
            consumeTNT();
            this.state = State.IGNITING;
            this.stateTimer = 10; // short delay before ignition
        } else {
            // Can't find spot, abort
            this.state = State.IDLE;
        }
    }

    private void tickIgniting() {
        this.stateTimer--;
        if (this.stateTimer <= 0 && this.tntPlacedPos != null) {
            // Ignite the TNT
            if (this.saboteur.level().getBlockState(this.tntPlacedPos).is(Blocks.TNT)) {
                TntBlock.explode(this.saboteur.level(), this.tntPlacedPos);
                this.saboteur.level().removeBlock(this.tntPlacedPos, false);
            }
            this.state = State.FLEEING;
            this.stateTimer = 100; // flee for 5 seconds
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

            // Check if this claim belongs to an enemy
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
