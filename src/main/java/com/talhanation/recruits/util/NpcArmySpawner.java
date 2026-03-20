package com.talhanation.recruits.util;

import com.talhanation.recruits.config.RecruitsServerConfig;
import com.talhanation.recruits.entities.*;
import com.talhanation.recruits.init.ModEntityTypes;
import com.talhanation.recruits.init.ModItems;
import com.talhanation.recruits.world.RecruitsPatrolSpawn;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.PlayerTeam;

import java.util.Optional;
import java.util.Random;

public class NpcArmySpawner {

    private static final Random random = new Random();

    public static AbstractRecruitEntity spawnFactionRecruit(
            ServerLevel level, BlockPos spawnPos, PlayerTeam team, VillagerNobleEntity noble
    ) {
        // Weighted distribution using nextInt(1000) for finer granularity
        // 39% recruit, 19% shieldman, 19% bowman, 10% crossbowman, 9.5% horseman, 2.7% rocketeer, 0.8% mortarman
        int roll = random.nextInt(1000);
        AbstractRecruitEntity entity;

        if (roll < 390) {
            // Infantry
            RecruitEntity recruit = ModEntityTypes.RECRUIT.get().create(level);
            equipInfantry(recruit);
            entity = recruit;
        } else if (roll < 580) {
            // Shieldman
            RecruitShieldmanEntity shieldman = ModEntityTypes.RECRUIT_SHIELDMAN.get().create(level);
            equipShieldman(shieldman);
            entity = shieldman;
        } else if (roll < 770) {
            // Bowman
            BowmanEntity bowman = ModEntityTypes.BOWMAN.get().create(level);
            equipBowman(bowman);
            entity = bowman;
        } else if (roll < 870) {
            // Crossbowman
            CrossBowmanEntity crossbowman = ModEntityTypes.CROSSBOWMAN.get().create(level);
            equipCrossbowman(crossbowman);
            entity = crossbowman;
        } else if (roll < 965) {
            // Horseman
            HorsemanEntity horseman = ModEntityTypes.HORSEMAN.get().create(level);
            equipHorseman(horseman);
            horseman.isPatrol = true; // triggers automatic horse spawning in tick()
            entity = horseman;
        } else if (roll < 992) {
            // Rocketeer (2.7%)
            RocketeerEntity rocketeer = ModEntityTypes.ROCKETEER.get().create(level);
            equipRocketeer(rocketeer);
            entity = rocketeer;
        } else {
            // Mortarman (0.8%)
            MortarmanEntity mortarman = ModEntityTypes.MORTARMAN.get().create(level);
            equipMortarman(mortarman);
            entity = mortarman;
        }

        entity.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY() + 0.5D, spawnPos.getZ() + 0.5D, random.nextFloat() * 360 - 180F, 0);
        entity.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.MOB_SUMMONED, null, null);
        entity.setPersistenceRequired();

        // Assign to scoreboard team
        level.getScoreboard().addPlayerToTeam(entity.getStringUUID(), team);

        // Set protect mode on noble
        entity.setProtectUUID(Optional.of(noble.getUUID()));
        entity.setShouldProtect(true);

        // Set reasonable stats
        entity.setHunger(100);
        entity.setMoral(100);
        int xpLevel = 1 + random.nextInt(3);
        entity.setXpLevel(xpLevel);
        entity.addLevelBuffsForLevel(xpLevel);

        // Give food
        RecruitsPatrolSpawn.setRecruitFood(entity);

        level.addFreshEntity(entity);

        return entity;
    }

    public static int spawnFactionGarrison(
            ServerLevel level, BlockPos center, PlayerTeam team, VillagerNobleEntity noble, int count
    ) {
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            int offsetX = 3 + random.nextInt(6); // 3-8 blocks
            int offsetZ = 3 + random.nextInt(6);
            if (random.nextBoolean()) offsetX = -offsetX;
            if (random.nextBoolean()) offsetZ = -offsetZ;

            BlockPos spawnPos = new BlockPos(
                    center.getX() + offsetX,
                    center.getY(),
                    center.getZ() + offsetZ
            );
            // Adjust Y to surface
            spawnPos = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, spawnPos);

            AbstractRecruitEntity recruit = spawnFactionRecruit(level, spawnPos, team, noble);
            if (recruit != null) {
                spawned++;
            }
        }
        return spawned;
    }

    private static void equipInfantry(RecruitEntity recruit) {
        recruit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        recruit.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        recruit.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
    }

    private static void equipShieldman(RecruitShieldmanEntity recruit) {
        recruit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        recruit.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        recruit.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        recruit.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    }

    private static void equipBowman(BowmanEntity recruit) {
        recruit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        recruit.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        recruit.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
        recruit.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));

        ItemStack arrows = new ItemStack(Items.ARROW);
        arrows.setCount(64);
        recruit.inventory.setItem(6, arrows);
    }

    private static void equipCrossbowman(CrossBowmanEntity recruit) {
        recruit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        recruit.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        recruit.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
        recruit.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));

        ItemStack arrows = new ItemStack(Items.ARROW);
        arrows.setCount(64);
        recruit.inventory.setItem(6, arrows);
    }

    private static void equipHorseman(HorsemanEntity recruit) {
        recruit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        recruit.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        recruit.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        recruit.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        recruit.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
    }

    private static void equipRocketeer(RocketeerEntity recruit) {
        recruit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        recruit.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));

        ItemStack rockets = new ItemStack(ModItems.ROCKET_ITEM.get());
        rockets.setCount(5);
        recruit.inventory.setItem(6, rockets);
    }

    private static void equipMortarman(MortarmanEntity recruit) {
        recruit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        recruit.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));

        ItemStack rounds = new ItemStack(ModItems.MORTAR_ROUND.get());
        rounds.setCount(5);
        recruit.inventory.setItem(6, rounds);
    }

    public static AbstractRecruitEntity spawnSaboteur(
            ServerLevel level, BlockPos spawnPos, PlayerTeam team, VillagerNobleEntity noble
    ) {
        SaboteurEntity saboteur = ModEntityTypes.SABOTEUR.get().create(level);
        saboteur.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        saboteur.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        saboteur.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));

        ItemStack tnt = new ItemStack(Items.TNT);
        tnt.setCount(RecruitsServerConfig.SaboteurTNTCount.get());
        saboteur.inventory.setItem(6, tnt);

        saboteur.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY() + 0.5D, spawnPos.getZ() + 0.5D, random.nextFloat() * 360 - 180F, 0);
        saboteur.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.MOB_SUMMONED, null, null);
        saboteur.setPersistenceRequired();

        level.getScoreboard().addPlayerToTeam(saboteur.getStringUUID(), team);

        saboteur.setProtectUUID(Optional.of(noble.getUUID()));
        saboteur.setShouldProtect(true);
        saboteur.setHunger(100);
        saboteur.setMoral(100);
        int xpLevel = 1 + random.nextInt(3);
        saboteur.setXpLevel(xpLevel);
        saboteur.addLevelBuffsForLevel(xpLevel);

        RecruitsPatrolSpawn.setRecruitFood(saboteur);
        level.addFreshEntity(saboteur);

        return saboteur;
    }
}
