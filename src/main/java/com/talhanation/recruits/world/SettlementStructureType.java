package com.talhanation.recruits.world;

public enum SettlementStructureType {
    TOWN_HALL(0, 1, 200, 0, 0, "town_hall/town_hall_1", 7, 5, 7),
    HOUSE(0, 6, 20, 0, 0, "house/house_1", 7, 5, 7),
    FARM(0, 4, 10, 0, 0, "farm/farm_1", 9, 3, 9),
    WELL(0, 1, 10, 0, 0, "well/well_1", 3, 5, 3),
    BARRACKS(1, 2, 60, 5, 0, "barracks/barracks_1", 11, 6, 7),
    MARKET(1, 1, 30, 0, 0, "market/market_1", 9, 5, 9),
    WALL_SEGMENT(1, 16, 40, 0, 0, "wall/wall_segment", 5, 4, 1),
    WATCHTOWER(1, 4, 50, 1, 32, "watchtower/watchtower_1", 5, 10, 5),
    GATE(2, 2, 80, 0, 0, "gate/gate_1", 5, 6, 3),
    CORNER_TOWER(2, 4, 70, 1, 48, "corner_tower/corner_tower_1", 5, 12, 5),
    FORGE(2, 1, 30, 2, 0, "forge/forge_1", 7, 5, 7),
    STABLES(2, 1, 30, 3, 0, "stables/stables_1", 11, 5, 7);

    private final int tier;
    private final int maxPerSettlement;
    private final int healthBonus;
    private final int garrisonBonus;
    private final int detectionBonus;
    private final String templatePath;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;

    SettlementStructureType(int tier, int maxPerSettlement, int healthBonus, int garrisonBonus, int detectionBonus, String templatePath, int sizeX, int sizeY, int sizeZ) {
        this.tier = tier;
        this.maxPerSettlement = maxPerSettlement;
        this.healthBonus = healthBonus;
        this.garrisonBonus = garrisonBonus;
        this.detectionBonus = detectionBonus;
        this.templatePath = templatePath;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    public int getTier() { return tier; }
    public int getMaxPerSettlement() { return maxPerSettlement; }
    public int getHealthBonus() { return healthBonus; }
    public int getGarrisonBonus() { return garrisonBonus; }
    public int getDetectionBonus() { return detectionBonus; }
    public String getTemplatePath() { return templatePath; }
    public int getSizeX() { return sizeX; }
    public int getSizeY() { return sizeY; }
    public int getSizeZ() { return sizeZ; }

    public boolean isPerimeter() {
        return this == WALL_SEGMENT || this == WATCHTOWER || this == GATE || this == CORNER_TOWER;
    }

    public boolean isCore() {
        return this == TOWN_HALL || this == HOUSE || this == FARM || this == WELL || this == MARKET;
    }
}
