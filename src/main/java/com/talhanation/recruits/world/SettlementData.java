package com.talhanation.recruits.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class SettlementData {
    private final List<SettlementStructure> structures = new ArrayList<>();
    @Nullable
    private BlockPos townHallPos;

    public List<SettlementStructure> getStructures() { return structures; }

    @Nullable
    public BlockPos getTownHallPos() { return townHallPos; }

    public void setTownHallPos(@Nullable BlockPos pos) { this.townHallPos = pos; }

    public void addStructure(SettlementStructure structure) {
        structures.add(structure);
        if (structure.getType() == SettlementStructureType.TOWN_HALL) {
            townHallPos = structure.getOrigin();
        }
    }

    public int getCountOf(SettlementStructureType type) {
        int count = 0;
        for (SettlementStructure s : structures) {
            if (s.getType() == type) count++;
        }
        return count;
    }

    public int getTier() {
        boolean hasBarracks = getCountOf(SettlementStructureType.BARRACKS) > 0;
        boolean hasWalls = getCountOf(SettlementStructureType.WALL_SEGMENT) >= 4;

        if (hasBarracks && hasWalls) return 2;

        boolean hasTownHall = getCountOf(SettlementStructureType.TOWN_HALL) > 0;
        boolean hasHouses = getCountOf(SettlementStructureType.HOUSE) >= 3;
        boolean hasFarms = getCountOf(SettlementStructureType.FARM) >= 2;

        if (hasTownHall && hasHouses && hasFarms) return 1;

        return 0;
    }

    public int getTotalHealthBonus() {
        int total = 0;
        for (SettlementStructure s : structures) {
            total += s.getType().getHealthBonus();
        }
        return total;
    }

    public int getTotalGarrisonBonus() {
        int total = 0;
        for (SettlementStructure s : structures) {
            total += s.getType().getGarrisonBonus();
        }
        return total;
    }

    public int getTotalDetectionBonus() {
        int total = 0;
        for (SettlementStructure s : structures) {
            total += s.getType().getDetectionBonus();
        }
        return total;
    }

    public boolean canBuild(SettlementStructureType type) {
        if (type.getTier() > getTier()) return false;
        return getCountOf(type) < type.getMaxPerSettlement();
    }

    @Nullable
    public SettlementStructureType getNextToBuild() {
        int tier = getTier();

        // Priority order per tier: fill required structures first, then optional
        SettlementStructureType[][] priorities = {
            // Tier 0 priorities
            { SettlementStructureType.TOWN_HALL, SettlementStructureType.HOUSE, SettlementStructureType.FARM, SettlementStructureType.WELL },
            // Tier 1 priorities
            { SettlementStructureType.BARRACKS, SettlementStructureType.WALL_SEGMENT, SettlementStructureType.WATCHTOWER, SettlementStructureType.MARKET },
            // Tier 2 priorities
            { SettlementStructureType.GATE, SettlementStructureType.CORNER_TOWER, SettlementStructureType.FORGE, SettlementStructureType.STABLES }
        };

        // Round-robin: prioritize unbuilt types first, then lowest fill ratio
        for (int t = 0; t <= Math.min(tier, 2); t++) {
            // First pass: any type not yet built at all
            for (SettlementStructureType type : priorities[t]) {
                if (canBuild(type) && getCountOf(type) == 0) return type;
            }
            // Second pass: pick type with lowest fill ratio (most room to grow)
            SettlementStructureType best = null;
            double bestRatio = Double.MAX_VALUE;
            for (SettlementStructureType type : priorities[t]) {
                if (canBuild(type)) {
                    double ratio = (double) getCountOf(type) / type.getMaxPerSettlement();
                    if (ratio < bestRatio) {
                        bestRatio = ratio;
                        best = type;
                    }
                }
            }
            if (best != null) return best;
        }

        return null;
    }

    public CompoundTag toNBT() {
        CompoundTag nbt = new CompoundTag();
        ListTag structureList = new ListTag();
        for (SettlementStructure s : structures) {
            structureList.add(s.toNBT());
        }
        nbt.put("structures", structureList);
        if (townHallPos != null) nbt.putLong("townHallPos", townHallPos.asLong());
        return nbt;
    }

    public static SettlementData fromNBT(CompoundTag nbt) {
        SettlementData data = new SettlementData();
        if (nbt.contains("townHallPos")) {
            data.townHallPos = BlockPos.of(nbt.getLong("townHallPos"));
        }
        ListTag structureList = nbt.getList("structures", 10);
        for (int i = 0; i < structureList.size(); i++) {
            SettlementStructure s = SettlementStructure.fromNBT(structureList.getCompound(i));
            if (s != null) data.structures.add(s);
        }
        return data;
    }
}
