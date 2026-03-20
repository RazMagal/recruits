package com.talhanation.recruits.entities.ai;

import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.Main;
import com.talhanation.recruits.config.RecruitsServerConfig;
import com.talhanation.recruits.entities.AbstractLeaderEntity;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.entities.CommanderEntity;
import com.talhanation.recruits.entities.VillagerNobleEntity;
import com.talhanation.recruits.init.ModEntityTypes;
import com.talhanation.recruits.util.NPCArmy;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.recruits.world.RecruitsFaction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class NpcOffensiveManager {

    public static boolean marshalAttackForce(
            ServerLevel level, VillagerNobleEntity noble,
            RecruitsFaction faction, RecruitsClaim targetClaim
    ) {
        if (faction.getVillageCenter() == null) return false;

        PlayerTeam playerTeam = level.getScoreboard().getPlayerTeam(faction.getStringID());
        if (playerTeam == null) return false;

        BlockPos center = faction.getVillageCenter();
        AABB searchBox = new AABB(center).inflate(200);

        // Find faction recruits (exclude existing leaders)
        List<AbstractRecruitEntity> garrison = new ArrayList<>(level.getEntitiesOfClass(
                AbstractRecruitEntity.class, searchBox,
                e -> e.isAlive() && e.getTeam() != null
                        && e.getTeam().getName().equals(faction.getStringID())
                        && !(e instanceof AbstractLeaderEntity)
        ));

        int reserve = RecruitsServerConfig.NpcHomeDefenseReserve.get();
        int available = garrison.size() - reserve;
        if (available <= 0) return false;

        // Sort by distance to village center — keep closest ones home as defense
        garrison.sort(Comparator.comparingDouble(e -> e.blockPosition().distSqr(center)));

        // Spawn commander at village center
        BlockPos spawnPos = level.getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, center);

        CommanderEntity commander = ModEntityTypes.PATROL_LEADER.get().create(level);
        if (commander == null) return false;

        commander.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
        commander.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos),
                MobSpawnType.MOB_SUMMONED, null, null);
        commander.setPersistenceRequired();

        // Equip commander
        commander.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        commander.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        commander.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        commander.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));

        level.addFreshEntity(commander);
        level.getScoreboard().addPlayerToTeam(commander.getStringUUID(), playerTeam);

        // Set waypoints — push in reverse order since it's a Stack (LIFO)
        // Second waypoint (center of enemy claim) pushed first
        ChunkPos claimCenter = targetClaim.getCenter();
        BlockPos centerPos = claimCenter.getMiddleBlockPosition(64);
        commander.WAYPOINTS.push(centerPos);

        // First waypoint (nearest edge of enemy claim) pushed second so it's popped first
        ChunkPos nearestChunk = findNearestChunk(targetClaim, center);
        if (nearestChunk != null) {
            BlockPos edgePos = nearestChunk.getMiddleBlockPosition(64);
            commander.WAYPOINTS.push(edgePos);
        }

        // Assign attack force — skip the first 'reserve' recruits (closest to village)
        List<AbstractRecruitEntity> attackForce = garrison.subList(reserve, garrison.size());
        List<LivingEntity> armyUnits = new ArrayList<>(attackForce);

        for (AbstractRecruitEntity recruit : attackForce) {
            recruit.setProtectUUID(Optional.of(commander.getUUID()));
            recruit.setShouldProtect(true);
        }

        commander.army = new NPCArmy(level, armyUnits, null);

        // Start patrol
        commander.setPatrolState(AbstractLeaderEntity.State.PATROLLING);

        Main.LOGGER.info("NPC Faction '{}' is launching an attack on '{}' with {} recruits!",
                faction.getTeamDisplayName(),
                targetClaim.getOwnerFaction().getTeamDisplayName(),
                attackForce.size());

        return true;
    }

    private static ChunkPos findNearestChunk(RecruitsClaim claim, BlockPos from) {
        ChunkPos fromChunk = new ChunkPos(from);
        ChunkPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (ChunkPos cp : claim.getClaimedChunks()) {
            double dist = Math.pow(cp.x - fromChunk.x, 2) + Math.pow(cp.z - fromChunk.z, 2);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = cp;
            }
        }
        return nearest;
    }
}
