package com.talhanation.recruits.util;

import com.talhanation.recruits.world.RecruitsClaim;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class ClaimUtil {
    public static List<LivingEntity> getLivingEntitiesInClaim(Level level, RecruitsClaim claim, Predicate<LivingEntity> filter) {
        return getLivingEntitiesInClaim(level, claim, filter, 0);
    }

    public static List<LivingEntity> getLivingEntitiesInClaim(Level level, RecruitsClaim claim, Predicate<LivingEntity> filter, int detectionBonus) {
        List<LivingEntity> list = new ArrayList<>();
        for (ChunkPos chunkPos : claim.getClaimedChunks()) {
            list.addAll(getLivingEntitiesInChunk(level, chunkPos, filter));
        }

        // Extended detection range from watchtowers
        if (detectionBonus > 0 && !claim.getClaimedChunks().isEmpty()) {
            // Build AABB covering all claimed chunks, then inflate by detection bonus
            int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (ChunkPos cp : claim.getClaimedChunks()) {
                if (cp.getMinBlockX() < minX) minX = cp.getMinBlockX();
                if (cp.getMinBlockZ() < minZ) minZ = cp.getMinBlockZ();
                if (cp.getMaxBlockX() > maxX) maxX = cp.getMaxBlockX();
                if (cp.getMaxBlockZ() > maxZ) maxZ = cp.getMaxBlockZ();
            }
            AABB claimBox = new AABB(minX, -64, minZ, maxX + 1, level.getMaxBuildHeight(), maxZ + 1);
            AABB extendedBox = claimBox.inflate(detectionBonus, 0, detectionBonus);

            // Get entities in extended area that aren't already in the claim
            List<LivingEntity> extended = level.getEntitiesOfClass(LivingEntity.class, extendedBox, filter);
            for (LivingEntity entity : extended) {
                if (!list.contains(entity)) {
                    list.add(entity);
                }
            }
        }
        return list;
    }

    public static List<LivingEntity> getLivingEntitiesInChunk(Level level, ChunkPos chunkPos, Predicate<LivingEntity> filter) {
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxX = chunkPos.getMaxBlockX();
        int maxZ = chunkPos.getMaxBlockZ();
        int minY = -64;
        int maxY = level.getMaxBuildHeight();
        AABB box = new AABB(minX, minY, minZ, maxX + 1, maxY, maxZ + 1);
        return level.getEntitiesOfClass(LivingEntity.class, box, filter);
    }
    public static List<LivingEntity> getLivingEntitiesInArea(Level level, AABB area, Predicate<LivingEntity> filter) {
        return level.getEntitiesOfClass(LivingEntity.class, area, filter);
    }
}

