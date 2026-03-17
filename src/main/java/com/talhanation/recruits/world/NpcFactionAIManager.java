package com.talhanation.recruits.world;

import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.Main;
import com.talhanation.recruits.config.RecruitsServerConfig;
import com.talhanation.recruits.entities.VillagerNobleEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NpcFactionAIManager {

    public void tick(ServerLevel level) {
        List<RecruitsFaction> npcFactions = new ArrayList<>();
        for (RecruitsFaction faction : FactionEvents.recruitsFactionManager.getFactions()) {
            if (faction.isNpcFaction()) {
                npcFactions.add(faction);
            }
        }

        long currentTick = level.getGameTime();

        for (RecruitsFaction faction : npcFactions) {
            if (faction.getLeaderDeadTick() > 0) {
                handleLeaderlessFaction(level, faction, currentTick);
            } else {
                tryClaimTerritory(level, faction);
            }
        }
    }

    private void handleLeaderlessFaction(ServerLevel level, RecruitsFaction faction, long currentTick) {
        long gracePeriodTicks = RecruitsServerConfig.NpcFactionLeaderGracePeriod.get() * 60L * 20L;
        long elapsed = currentTick - faction.getLeaderDeadTick();

        // Check if a new noble has joined the faction's team
        PlayerTeam playerTeam = level.getScoreboard().getPlayerTeam(faction.getStringID());
        if (playerTeam != null) {
            for (Entity entity : level.getEntities().getAll()) {
                if (entity instanceof VillagerNobleEntity noble && noble.isAlive()) {
                    if (noble.getTeam() != null && noble.getTeam().getName().equals(faction.getStringID())) {
                        // New noble found on the team — promote as leader
                        faction.setLeaderDeadTick(0);
                        faction.setTeamLeaderID(noble.getUUID());
                        faction.setTeamLeaderName(noble.getName().getString());
                        Main.LOGGER.info("NPC Faction '{}' got a new Noble leader: {}", faction.getTeamDisplayName(), noble.getName().getString());
                        FactionEvents.recruitsFactionManager.save(level);
                        return;
                    }
                }
            }
        }

        // Grace period expired — dissolve faction
        if (elapsed > gracePeriodTicks) {
            Main.LOGGER.info("NPC Faction '{}' dissolved — no noble leader found within grace period.", faction.getTeamDisplayName());
            FactionEvents.removeTeam(level, faction.getStringID());
        }
    }

    public void tryClaimTerritory(ServerLevel level, RecruitsFaction faction) {
        if (faction.getVillageCenter() == null) return;

        ChunkPos centerChunk = new ChunkPos(faction.getVillageCenter());

        // Find existing claim for this faction
        RecruitsClaim existingClaim = null;
        for (RecruitsClaim claim : ClaimEvents.recruitsClaimManager.getAllClaims()) {
            if (claim.getOwnerFaction().getStringID().equals(faction.getStringID())) {
                existingClaim = claim;
                break;
            }
        }

        if (existingClaim == null) {
            createInitialClaim(level, faction, centerChunk);
        } else {
            tryExpandClaim(level, faction, existingClaim);
        }
    }

    private void createInitialClaim(ServerLevel level, RecruitsFaction faction, ChunkPos centerChunk) {
        int size = RecruitsServerConfig.NpcFactionInitialClaimSize.get();
        int radius = (int) Math.floor(Math.sqrt(size) / 2.0);

        RecruitsClaim claim = new RecruitsClaim(faction);
        claim.setCenter(centerChunk);
        claim.setPlayer(new RecruitsPlayerInfo(faction.getTeamLeaderUUID(), faction.getTeamLeaderName(), faction));

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos pos = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                if (claim.getClaimedChunks().size() >= size) break;
                // Skip if already claimed by another faction
                if (ClaimEvents.recruitsClaimManager.getClaim(pos) != null) continue;
                claim.addChunk(pos);
            }
        }

        if (!claim.getClaimedChunks().isEmpty()) {
            ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, claim);
            ClaimEvents.recruitsClaimManager.save(level);
            Main.LOGGER.info("NPC Faction '{}' claimed {} chunks around village.", faction.getTeamDisplayName(), claim.getClaimedChunks().size());
        }
    }

    private void tryExpandClaim(ServerLevel level, RecruitsFaction faction, RecruitsClaim claim) {
        int maxSize = RecruitsServerConfig.NpcFactionMaxClaimSize.get();
        if (claim.getClaimedChunks().size() >= maxSize) return;

        long expansionIntervalTicks = RecruitsServerConfig.NpcFactionExpansionInterval.get() * 60L * 20L;
        long currentTick = level.getGameTime();
        long elapsed = currentTick - faction.getCreatedAtTick();

        // Only expand at intervals — check if enough time passed since last potential expansion
        // Use modulo to gate expansion to fixed intervals
        if (elapsed % expansionIntervalTicks > 1200) return; // only within the first minute of each interval

        // Find border chunks to expand into
        Set<ChunkPos> currentChunks = new HashSet<>(claim.getClaimedChunks());
        List<ChunkPos> candidates = new ArrayList<>();

        for (ChunkPos existing : currentChunks) {
            ChunkPos[] neighbors = {
                new ChunkPos(existing.x + 1, existing.z),
                new ChunkPos(existing.x - 1, existing.z),
                new ChunkPos(existing.x, existing.z + 1),
                new ChunkPos(existing.x, existing.z - 1)
            };
            for (ChunkPos neighbor : neighbors) {
                if (!currentChunks.contains(neighbor) && ClaimEvents.recruitsClaimManager.getClaim(neighbor) == null) {
                    if (!candidates.contains(neighbor)) {
                        candidates.add(neighbor);
                    }
                }
            }
        }

        // Add up to 3 chunks per cycle
        int added = 0;
        for (ChunkPos candidate : candidates) {
            if (claim.getClaimedChunks().size() >= maxSize || added >= 3) break;
            claim.addChunk(candidate);
            added++;
        }

        if (added > 0) {
            ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, claim);
            ClaimEvents.recruitsClaimManager.save(level);
            Main.LOGGER.debug("NPC Faction '{}' expanded claim by {} chunks.", faction.getTeamDisplayName(), added);
        }
    }
}
