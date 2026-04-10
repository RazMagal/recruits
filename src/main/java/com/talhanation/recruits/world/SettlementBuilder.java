package com.talhanation.recruits.world;

import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.Main;
import com.talhanation.recruits.config.RecruitsServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import javax.annotation.Nullable;
import java.util.*;

public class SettlementBuilder {

    public static boolean tryBuildNextStructure(ServerLevel level, RecruitsFaction faction, RecruitsClaim claim) {
        if (!RecruitsServerConfig.NpcFactionBuildEnabled.get()) return false;

        SettlementData data = faction.getSettlementData();
        if (data.getStructures().size() >= RecruitsServerConfig.NpcFactionMaxStructures.get()) return false;

        SettlementStructureType nextType = data.getNextToBuild();
        if (nextType == null) return false;

        BlockPos center = faction.getVillageCenter();
        if (center == null) return false;

        BlockPos placementPos = findPlacementPosition(level, center, nextType, data, claim);
        if (placementPos == null) {
            Main.LOGGER.debug("NPC Faction '{}': no suitable position for {}.", faction.getTeamDisplayName(), nextType.name());
            return false;
        }

        int rotation = level.random.nextInt(4);
        boolean placed = placeStructure(level, placementPos, nextType, rotation);
        if (!placed) return false;

        SettlementStructure structure = new SettlementStructure(nextType, placementPos, rotation, level.getGameTime());
        data.addStructure(structure);

        // Add occupied chunks to claim
        int added = 0;
        for (ChunkPos cp : structure.getOccupiedChunks()) {
            if (!claim.containsChunk(cp) && ClaimEvents.recruitsClaimManager.getClaim(cp) == null) {
                claim.addChunk(cp);
                added++;
            }
        }

        // Recalculate center
        recalculateClaimCenter(claim);

        ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, claim);
        ClaimEvents.recruitsClaimManager.save(level);

        Main.LOGGER.info("NPC Faction '{}' built {} at [{}, {}, {}] (tier {}, +{} chunks).",
                faction.getTeamDisplayName(), nextType.name(),
                placementPos.getX(), placementPos.getY(), placementPos.getZ(),
                data.getTier(), added);
        return true;
    }

    @Nullable
    static BlockPos findPlacementPosition(ServerLevel level, BlockPos center, SettlementStructureType type, SettlementData data, RecruitsClaim claim) {
        Set<ChunkPos> claimedChunks = new HashSet<>(claim.getClaimedChunks());

        if (type.isPerimeter()) {
            return findPerimeterPosition(level, center, type, data, claimedChunks);
        } else {
            return findCorePosition(level, center, type, data, claimedChunks);
        }
    }

    @Nullable
    private static BlockPos findCorePosition(ServerLevel level, BlockPos center, SettlementStructureType type, SettlementData data, Set<ChunkPos> claimedChunks) {
        int searchRadius = type == SettlementStructureType.TOWN_HALL ? 16 : 80;
        int minRadius = type == SettlementStructureType.TOWN_HALL ? 4 : 40;

        List<BlockPos> candidates = new ArrayList<>();

        // Spiral scan outward from center — skip vanilla village footprint for non-TOWN_HALL
        for (int r = minRadius; r <= searchRadius; r += 4) {
            for (int dx = -r; dx <= r; dx += 4) {
                for (int dz = -r; dz <= r; dz += 4) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r && r > 4) continue; // Only ring edges for r > 4

                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;
                    int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                    BlockPos pos = new BlockPos(x, y, z);

                    if (isTerrainSuitable(level, pos, type.getSizeX(), type.getSizeZ())
                            && !overlapsExisting(pos, type, data)
                            && isWithinOrAdjacentToClaim(pos, type, claimedChunks)) {
                        candidates.add(pos);
                    }
                }
            }
            if (!candidates.isEmpty()) break; // Use closest ring that has candidates
        }

        if (candidates.isEmpty()) return null;

        // Pick closest to center
        candidates.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        return candidates.get(0);
    }

    @Nullable
    private static BlockPos findPerimeterPosition(ServerLevel level, BlockPos center, SettlementStructureType type, SettlementData data, Set<ChunkPos> claimedChunks) {
        // Find boundary chunks
        List<ChunkPos> boundary = new ArrayList<>();
        for (ChunkPos cp : claimedChunks) {
            boolean isEdge = false;
            ChunkPos[] neighbors = {
                new ChunkPos(cp.x + 1, cp.z), new ChunkPos(cp.x - 1, cp.z),
                new ChunkPos(cp.x, cp.z + 1), new ChunkPos(cp.x, cp.z - 1)
            };
            for (ChunkPos n : neighbors) {
                if (!claimedChunks.contains(n)) { isEdge = true; break; }
            }
            if (isEdge) boundary.add(cp);
        }

        if (boundary.isEmpty()) return null;

        // For walls: prefer boundary chunks adjacent to already-placed walls so the
        // ring grows consecutively rather than scattering around the perimeter.
        if (type == SettlementStructureType.WALL_SEGMENT) {
            Set<ChunkPos> existingWallChunks = new HashSet<>();
            for (SettlementStructure s : data.getStructures()) {
                if (s.getType() == SettlementStructureType.WALL_SEGMENT) {
                    existingWallChunks.add(new ChunkPos(s.getOrigin()));
                }
            }
            if (!existingWallChunks.isEmpty()) {
                boundary.sort(Comparator.comparingInt(cp -> {
                    int best = Integer.MAX_VALUE;
                    for (ChunkPos w : existingWallChunks) {
                        int d = Math.abs(cp.x - w.x) + Math.abs(cp.z - w.z);
                        if (d > 0 && d < best) best = d;
                    }
                    return best;
                }));
            } else {
                // First wall: start at the chunk furthest from center
                boundary.sort(Comparator.comparingDouble(cp ->
                        -((cp.getMiddleBlockX() - center.getX()) * (double)(cp.getMiddleBlockX() - center.getX())
                        + (cp.getMiddleBlockZ() - center.getZ()) * (double)(cp.getMiddleBlockZ() - center.getZ()))));
            }
        } else {
            // For watchtowers / gates / corner towers: prefer furthest from center
            boundary.sort(Comparator.comparingDouble(cp ->
                    -((cp.getMiddleBlockX() - center.getX()) * (double)(cp.getMiddleBlockX() - center.getX())
                    + (cp.getMiddleBlockZ() - center.getZ()) * (double)(cp.getMiddleBlockZ() - center.getZ()))));
        }

        // Walk the ordered boundary and return the first chunk that yields a valid placement.
        for (ChunkPos cp : boundary) {
            int x = cp.getMiddleBlockX();
            int z = cp.getMiddleBlockZ();
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            BlockPos pos = new BlockPos(x, y, z);

            if (isTerrainSuitable(level, pos, type.getSizeX(), type.getSizeZ())
                    && !overlapsExisting(pos, type, data)) {
                return pos;
            }
        }
        return null;
    }

    static boolean isTerrainSuitable(ServerLevel level, BlockPos pos, int sizeX, int sizeZ) {
        int[] sampleOffsets = { 0, sizeX / 2, sizeX - 1 };
        int[] sampleOffsetsZ = { 0, sizeZ / 2, sizeZ - 1 };

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int dx : sampleOffsets) {
            for (int dz : sampleOffsetsZ) {
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX() + dx, pos.getZ() + dz);
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;

                // Check for water/lava at surface
                BlockState surface = level.getBlockState(new BlockPos(pos.getX() + dx, y - 1, pos.getZ() + dz));
                if (surface.is(Blocks.WATER) || surface.is(Blocks.LAVA)) return false;
            }
        }

        return (maxY - minY) <= 4;
    }

    private static boolean overlapsExisting(BlockPos pos, SettlementStructureType type, SettlementData data) {
        // Wall segments are tiny perimeter pieces and need to sit next to each other,
        // so they get no margin. Other structures keep a buffer to avoid clipping.
        int margin = type == SettlementStructureType.WALL_SEGMENT ? 0 : 4;
        int minX = pos.getX() - margin;
        int maxX = pos.getX() + type.getSizeX() + margin;
        int minZ = pos.getZ() - margin;
        int maxZ = pos.getZ() + type.getSizeZ() + margin;

        for (SettlementStructure existing : data.getStructures()) {
            int eMinX = existing.getOrigin().getX();
            int eMaxX = eMinX + existing.getType().getSizeX();
            int eMinZ = existing.getOrigin().getZ();
            int eMaxZ = eMinZ + existing.getType().getSizeZ();

            if (minX < eMaxX && maxX > eMinX && minZ < eMaxZ && maxZ > eMinZ) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWithinOrAdjacentToClaim(BlockPos pos, SettlementStructureType type, Set<ChunkPos> claimedChunks) {
        List<ChunkPos> occupied = SettlementStructure.getOccupiedChunks(pos, type.getSizeX(), type.getSizeZ(), 0);
        for (ChunkPos cp : occupied) {
            if (claimedChunks.contains(cp)) return true;
            // Check adjacent to claimed
            ChunkPos[] neighbors = {
                new ChunkPos(cp.x + 1, cp.z), new ChunkPos(cp.x - 1, cp.z),
                new ChunkPos(cp.x, cp.z + 1), new ChunkPos(cp.x, cp.z - 1)
            };
            for (ChunkPos n : neighbors) {
                if (claimedChunks.contains(n)) return true;
            }
        }
        return false;
    }

    private static boolean placeStructure(ServerLevel level, BlockPos pos, SettlementStructureType type, int rotationIndex) {
        StructureTemplateManager templateManager = level.getStructureManager();
        ResourceLocation templateId = new ResourceLocation(Main.MOD_ID, "settlement/" + type.getTemplatePath());

        Optional<StructureTemplate> templateOpt = templateManager.get(templateId);
        if (templateOpt.isEmpty()) {
            // No .nbt template found — place placeholder cobblestone box
            placePlaceholder(level, pos, type, rotationIndex);
            return true;
        }

        StructureTemplate template = templateOpt.get();
        Rotation rotation = Rotation.values()[rotationIndex % 4];
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(false);

        // Flatten terrain to average height under footprint
        int avgY = getAverageHeight(level, pos, type.getSizeX(), type.getSizeZ());
        BlockPos placePos = new BlockPos(pos.getX(), avgY, pos.getZ());

        template.placeInWorld(level, placePos, placePos, settings, level.random, 2);
        return true;
    }

    private static void placePlaceholder(ServerLevel level, BlockPos pos, SettlementStructureType type, int rotationIndex) {
        int sizeX = (rotationIndex == 1 || rotationIndex == 3) ? type.getSizeZ() : type.getSizeX();
        int sizeZ = (rotationIndex == 1 || rotationIndex == 3) ? type.getSizeX() : type.getSizeZ();
        int avgY = getAverageHeight(level, pos, sizeX, sizeZ);

        BlockState wallBlock = Blocks.COBBLESTONE.defaultBlockState();
        BlockState floorBlock = Blocks.OAK_PLANKS.defaultBlockState();

        // Floor
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                level.setBlock(new BlockPos(pos.getX() + dx, avgY, pos.getZ() + dz), floorBlock, 2);
            }
        }

        // Walls (1 block high on edges)
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                if (dx == 0 || dx == sizeX - 1 || dz == 0 || dz == sizeZ - 1) {
                    level.setBlock(new BlockPos(pos.getX() + dx, avgY + 1, pos.getZ() + dz), wallBlock, 2);
                }
            }
        }

        // Corner pillars for non-tiny structures
        if (sizeX >= 5 && sizeZ >= 5) {
            for (int dy = 2; dy < Math.min(type.getSizeY(), 4); dy++) {
                level.setBlock(new BlockPos(pos.getX(), avgY + dy, pos.getZ()), wallBlock, 2);
                level.setBlock(new BlockPos(pos.getX() + sizeX - 1, avgY + dy, pos.getZ()), wallBlock, 2);
                level.setBlock(new BlockPos(pos.getX(), avgY + dy, pos.getZ() + sizeZ - 1), wallBlock, 2);
                level.setBlock(new BlockPos(pos.getX() + sizeX - 1, avgY + dy, pos.getZ() + sizeZ - 1), wallBlock, 2);
            }
        }
    }

    private static int getAverageHeight(ServerLevel level, BlockPos pos, int sizeX, int sizeZ) {
        int total = 0;
        int count = 0;
        int step = Math.max(1, Math.min(sizeX, sizeZ) / 3);

        for (int dx = 0; dx < sizeX; dx += step) {
            for (int dz = 0; dz < sizeZ; dz += step) {
                total += level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX() + dx, pos.getZ() + dz);
                count++;
            }
        }
        return count > 0 ? total / count : pos.getY();
    }

    private static void recalculateClaimCenter(RecruitsClaim claim) {
        List<ChunkPos> chunks = claim.getClaimedChunks();
        if (chunks.isEmpty()) return;

        long totalX = 0, totalZ = 0;
        for (ChunkPos cp : chunks) {
            totalX += cp.x;
            totalZ += cp.z;
        }
        claim.setCenter(new ChunkPos((int)(totalX / chunks.size()), (int)(totalZ / chunks.size())));
    }

    public static void registerVanillaStructures(ServerLevel level, RecruitsFaction faction, RecruitsClaim claim, BlockPos center) {
        SettlementData data = faction.getSettlementData();
        long tick = level.getGameTime();
        int scanRadius = 5 * 16; // 5 chunks

        // Register town hall at center
        data.addStructure(new SettlementStructure(SettlementStructureType.TOWN_HALL, center, 0, tick));

        // Scan for vanilla village indicators
        int housesFound = 0;
        int farmsFound = 0;

        for (int dx = -scanRadius; dx <= scanRadius; dx += 4) {
            for (int dz = -scanRadius; dz <= scanRadius; dz += 4) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);

                // Check several Y levels for blocks
                for (int dy = -3; dy <= 5; dy++) {
                    BlockPos checkPos = new BlockPos(x, y + dy, z);
                    BlockState state = level.getBlockState(checkPos);

                    if (state.is(Blocks.CRAFTING_TABLE) || isBed(state)) {
                        if (housesFound < SettlementStructureType.HOUSE.getMaxPerSettlement()) {
                            data.addStructure(new SettlementStructure(SettlementStructureType.HOUSE, checkPos, 0, tick));
                            housesFound++;
                        }
                        break;
                    } else if (state.is(Blocks.COMPOSTER) || state.is(Blocks.HAY_BLOCK)) {
                        if (farmsFound < SettlementStructureType.FARM.getMaxPerSettlement()) {
                            data.addStructure(new SettlementStructure(SettlementStructureType.FARM, checkPos, 0, tick));
                            farmsFound++;
                        }
                        break;
                    }
                }
            }
        }

        Main.LOGGER.info("NPC Faction '{}' registered {} houses and {} farms from vanilla village.",
                faction.getTeamDisplayName(), housesFound, farmsFound);
    }

    private static boolean isBed(BlockState state) {
        return state.is(Blocks.WHITE_BED) || state.is(Blocks.ORANGE_BED) || state.is(Blocks.MAGENTA_BED)
                || state.is(Blocks.LIGHT_BLUE_BED) || state.is(Blocks.YELLOW_BED) || state.is(Blocks.LIME_BED)
                || state.is(Blocks.PINK_BED) || state.is(Blocks.GRAY_BED) || state.is(Blocks.LIGHT_GRAY_BED)
                || state.is(Blocks.CYAN_BED) || state.is(Blocks.PURPLE_BED) || state.is(Blocks.BLUE_BED)
                || state.is(Blocks.BROWN_BED) || state.is(Blocks.GREEN_BED) || state.is(Blocks.RED_BED)
                || state.is(Blocks.BLACK_BED);
    }
}
