package com.talhanation.recruits.world;

import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.Main;
import com.talhanation.recruits.config.RecruitsServerConfig;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.entities.VillagerNobleEntity;
import com.talhanation.recruits.util.NpcArmySpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
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
                tryRecruitArmy(level, faction, currentTick);
                evaluateDiplomacy(level, faction, currentTick);
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

    private void tryRecruitArmy(ServerLevel level, RecruitsFaction faction, long currentTick) {
        if (faction.getVillageCenter() == null) return;

        long recruitIntervalTicks = RecruitsServerConfig.NpcFactionRecruitInterval.get() * 60L * 20L;
        long elapsed = currentTick - faction.getCreatedAtTick();

        // Only recruit at intervals — use modulo to gate recruitment to fixed intervals
        if (elapsed % recruitIntervalTicks > 1200) return;

        // Find the noble leader entity
        VillagerNobleEntity noble = null;
        for (Entity entity : level.getEntities().getAll()) {
            if (entity instanceof VillagerNobleEntity candidate && candidate.isAlive()) {
                if (candidate.getUUID().equals(faction.getTeamLeaderUUID())) {
                    noble = candidate;
                    break;
                }
            }
        }

        if (noble == null) return;

        PlayerTeam playerTeam = level.getScoreboard().getPlayerTeam(faction.getStringID());
        if (playerTeam == null) return;

        BlockPos center = faction.getVillageCenter();
        AABB searchBox = new AABB(center).inflate(200);

        // Count existing faction recruits
        List<AbstractRecruitEntity> existing = level.getEntitiesOfClass(
                AbstractRecruitEntity.class, searchBox,
                e -> e.isAlive() && e.getTeam() != null && e.getTeam().getName().equals(faction.getStringID())
        );

        // Count villagers for desired garrison calculation
        int villagerCount = level.getEntitiesOfClass(Villager.class, searchBox, Villager::isAlive).size();
        int maxGarrison = RecruitsServerConfig.NpcFactionMaxGarrison.get();
        int desired = Math.min(villagerCount * 2, maxGarrison);

        if (existing.size() >= desired) return;

        int toSpawn = Math.min(RecruitsServerConfig.NpcFactionRecruitsPerCycle.get(), desired - existing.size());

        int spawned = NpcArmySpawner.spawnFactionGarrison(level, center, playerTeam, noble, toSpawn);
        if (spawned > 0) {
            faction.addNPCs(spawned);
            FactionEvents.recruitsFactionManager.save(level);
            Main.LOGGER.info("NPC Faction '{}' recruited {} garrison members ({}/{}).",
                    faction.getTeamDisplayName(), spawned, existing.size() + spawned, desired);
        }
    }

    private void evaluateDiplomacy(ServerLevel level, RecruitsFaction faction, long currentTick) {
        long intervalTicks = RecruitsServerConfig.NpcDiplomacyEvaluationInterval.get() * 60L * 20L;
        long elapsed = currentTick - faction.getCreatedAtTick();
        if (elapsed % intervalTicks > 1200) return;

        // Faction must be old enough to declare war
        long ageMinutes = elapsed / (60L * 20L);
        boolean canDeclareWar = ageMinutes >= RecruitsServerConfig.NpcMinAgeForWar.get();

        List<RecruitsFaction> allFactions = new ArrayList<>(FactionEvents.recruitsFactionManager.getFactions());

        for (RecruitsFaction other : allFactions) {
            if (other.equalsFaction(faction)) continue;

            RecruitsDiplomacyManager.DiplomacyStatus currentRelation =
                    FactionEvents.recruitsDiplomacyManager.getRelation(faction.getStringID(), other.getStringID());

            // War declaration: NEUTRAL → ENEMY (neighbors only)
            if (currentRelation == RecruitsDiplomacyManager.DiplomacyStatus.NEUTRAL
                    && canDeclareWar && areNeighbors(faction, other)) {
                double warChance = RecruitsServerConfig.NpcWarDeclarationChance.get();
                if (hasAllyAtWarWith(faction, other, allFactions)) warChance *= 1.5;

                if (level.random.nextDouble() < warChance) {
                    FactionEvents.recruitsDiplomacyManager.setRelation(
                            faction.getStringID(), other.getStringID(),
                            RecruitsDiplomacyManager.DiplomacyStatus.ENEMY, level, true
                    );
                    Main.LOGGER.info("NPC Faction '{}' declared war on '{}'!",
                            faction.getTeamDisplayName(), other.getTeamDisplayName());
                }
            }

            // Alliance formation: NEUTRAL → ALLY (common enemy, NPC factions only)
            if (currentRelation == RecruitsDiplomacyManager.DiplomacyStatus.NEUTRAL
                    && other.isNpcFaction() && shareCommonEnemy(faction, other, allFactions)) {
                if (level.random.nextDouble() < RecruitsServerConfig.NpcAllianceChance.get()) {
                    FactionEvents.recruitsDiplomacyManager.setRelation(
                            faction.getStringID(), other.getStringID(),
                            RecruitsDiplomacyManager.DiplomacyStatus.ALLY, level, true
                    );
                    Main.LOGGER.info("NPC Factions '{}' and '{}' formed an alliance!",
                            faction.getTeamDisplayName(), other.getTeamDisplayName());
                }
            }
        }
    }

    private boolean areNeighbors(RecruitsFaction a, RecruitsFaction b) {
        // If both have village centers, use distance check
        if (a.getVillageCenter() != null && b.getVillageCenter() != null) {
            double distSq = a.getVillageCenter().distSqr(b.getVillageCenter());
            return distSq < 200.0 * 200.0;
        }
        // For player factions (no village center), check claim proximity
        return haveAdjacentClaims(a, b);
    }

    private boolean haveAdjacentClaims(RecruitsFaction a, RecruitsFaction b) {
        List<RecruitsClaim> allClaims = ClaimEvents.recruitsClaimManager.getAllClaims();
        Set<ChunkPos> chunksA = new HashSet<>();

        for (RecruitsClaim claim : allClaims) {
            if (claim.getOwnerFaction().getStringID().equals(a.getStringID())) {
                chunksA.addAll(claim.getClaimedChunks());
            }
        }
        if (chunksA.isEmpty()) return false;

        for (RecruitsClaim claim : allClaims) {
            if (!claim.getOwnerFaction().getStringID().equals(b.getStringID())) continue;
            for (ChunkPos cb : claim.getClaimedChunks()) {
                for (ChunkPos ca : chunksA) {
                    if (Math.abs(ca.x - cb.x) + Math.abs(ca.z - cb.z) <= 2) return true;
                }
            }
        }
        return false;
    }

    private boolean shareCommonEnemy(RecruitsFaction a, RecruitsFaction b, List<RecruitsFaction> allFactions) {
        for (RecruitsFaction third : allFactions) {
            if (third.equalsFaction(a) || third.equalsFaction(b)) continue;
            RecruitsDiplomacyManager.DiplomacyStatus relA =
                    FactionEvents.recruitsDiplomacyManager.getRelation(a.getStringID(), third.getStringID());
            RecruitsDiplomacyManager.DiplomacyStatus relB =
                    FactionEvents.recruitsDiplomacyManager.getRelation(b.getStringID(), third.getStringID());
            if (relA == RecruitsDiplomacyManager.DiplomacyStatus.ENEMY
                    && relB == RecruitsDiplomacyManager.DiplomacyStatus.ENEMY) return true;
        }
        return false;
    }

    private boolean hasAllyAtWarWith(RecruitsFaction faction, RecruitsFaction enemy, List<RecruitsFaction> allFactions) {
        for (RecruitsFaction ally : allFactions) {
            if (ally.equalsFaction(faction) || ally.equalsFaction(enemy)) continue;
            RecruitsDiplomacyManager.DiplomacyStatus relToUs =
                    FactionEvents.recruitsDiplomacyManager.getRelation(faction.getStringID(), ally.getStringID());
            RecruitsDiplomacyManager.DiplomacyStatus relToEnemy =
                    FactionEvents.recruitsDiplomacyManager.getRelation(ally.getStringID(), enemy.getStringID());
            if (relToUs == RecruitsDiplomacyManager.DiplomacyStatus.ALLY
                    && relToEnemy == RecruitsDiplomacyManager.DiplomacyStatus.ENEMY) return true;
        }
        return false;
    }
}
