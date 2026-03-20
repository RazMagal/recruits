package com.talhanation.recruits.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SettlementStructure {
    private final UUID id;
    private final SettlementStructureType type;
    private final BlockPos origin;
    private final int rotation;
    private final long placedAtTick;
    private final List<ChunkPos> occupiedChunks;

    public SettlementStructure(UUID id, SettlementStructureType type, BlockPos origin, int rotation, long placedAtTick, List<ChunkPos> occupiedChunks) {
        this.id = id;
        this.type = type;
        this.origin = origin;
        this.rotation = rotation;
        this.placedAtTick = placedAtTick;
        this.occupiedChunks = occupiedChunks;
    }

    public SettlementStructure(SettlementStructureType type, BlockPos origin, int rotation, long placedAtTick) {
        this(UUID.randomUUID(), type, origin, rotation, placedAtTick, getOccupiedChunks(origin, type.getSizeX(), type.getSizeZ(), rotation));
    }

    public UUID getId() { return id; }
    public SettlementStructureType getType() { return type; }
    public BlockPos getOrigin() { return origin; }
    public int getRotation() { return rotation; }
    public long getPlacedAtTick() { return placedAtTick; }
    public List<ChunkPos> getOccupiedChunks() { return occupiedChunks; }

    public static List<ChunkPos> getOccupiedChunks(BlockPos origin, int sizeX, int sizeZ, int rotation) {
        int effSizeX = (rotation == 1 || rotation == 3) ? sizeZ : sizeX;
        int effSizeZ = (rotation == 1 || rotation == 3) ? sizeX : sizeZ;

        List<ChunkPos> chunks = new ArrayList<>();
        int minChunkX = origin.getX() >> 4;
        int maxChunkX = (origin.getX() + effSizeX - 1) >> 4;
        int minChunkZ = origin.getZ() >> 4;
        int maxChunkZ = (origin.getZ() + effSizeZ - 1) >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                ChunkPos cp = new ChunkPos(cx, cz);
                if (!chunks.contains(cp)) {
                    chunks.add(cp);
                }
            }
        }
        return chunks;
    }

    public CompoundTag toNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putUUID("id", id);
        nbt.putString("type", type.name());
        nbt.putLong("origin", origin.asLong());
        nbt.putInt("rotation", rotation);
        nbt.putLong("placedAtTick", placedAtTick);

        ListTag chunkList = new ListTag();
        for (ChunkPos cp : occupiedChunks) {
            CompoundTag chunkTag = new CompoundTag();
            chunkTag.putInt("x", cp.x);
            chunkTag.putInt("z", cp.z);
            chunkList.add(chunkTag);
        }
        nbt.put("occupiedChunks", chunkList);
        return nbt;
    }

    public static SettlementStructure fromNBT(CompoundTag nbt) {
        UUID id = nbt.getUUID("id");
        SettlementStructureType type;
        try {
            type = SettlementStructureType.valueOf(nbt.getString("type"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        BlockPos origin = BlockPos.of(nbt.getLong("origin"));
        int rotation = nbt.getInt("rotation");
        long placedAtTick = nbt.getLong("placedAtTick");

        List<ChunkPos> chunks = new ArrayList<>();
        ListTag chunkList = nbt.getList("occupiedChunks", 10);
        for (int i = 0; i < chunkList.size(); i++) {
            CompoundTag chunkTag = chunkList.getCompound(i);
            chunks.add(new ChunkPos(chunkTag.getInt("x"), chunkTag.getInt("z")));
        }

        return new SettlementStructure(id, type, origin, rotation, placedAtTick, chunks);
    }
}
