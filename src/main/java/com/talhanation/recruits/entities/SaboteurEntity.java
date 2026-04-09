package com.talhanation.recruits.entities;

import com.talhanation.recruits.config.RecruitsServerConfig;
import com.talhanation.recruits.entities.ai.FleeTNT;
import com.talhanation.recruits.entities.ai.RecruitMoveTowardsTargetGoal;
import com.talhanation.recruits.entities.ai.SaboteurPlaceTNTGoal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.common.ForgeMod;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

public class SaboteurEntity extends AbstractRecruitEntity {

    private final Predicate<ItemEntity> ALLOWED_ITEMS = (item) ->
            (!item.hasPickUpDelay() && item.isAlive() && getInventory().canAddItem(item.getItem()) && this.wantsToPickUp(item.getItem()));

    public SaboteurEntity(EntityType<? extends AbstractRecruitEntity> entityType, Level world) {
        super(entityType, world);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new FleeTNT(this));
        this.goalSelector.addGoal(3, new SaboteurPlaceTNTGoal(this));
        this.goalSelector.addGoal(8, new RecruitMoveTowardsTargetGoal(this, 1.2D, (float) this.getMeleeStartRange()));
    }

    @Override
    public double getMeleeStartRange() {
        return 3D;
    }

    public static AttributeSupplier.Builder setAttributes() {
        return Mob.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 18.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(ForgeMod.SWIM_SPEED.get(), 0.3D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(ForgeMod.ENTITY_REACH.get(), 0D)
                .add(Attributes.ATTACK_SPEED);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficultyInstance, MobSpawnType reason, @Nullable SpawnGroupData data, @Nullable CompoundTag nbt) {
        RandomSource randomsource = world.getRandom();
        SpawnGroupData ilivingentitydata = super.finalizeSpawn(world, difficultyInstance, reason, data, nbt);
        this.populateDefaultEquipmentEnchantments(randomsource, difficultyInstance);
        this.initSpawn();
        return ilivingentitydata;
    }

    @Override
    public void initSpawn() {
        this.setCustomName(Component.literal("Saboteur"));
        this.setCost(RecruitsServerConfig.SaboteurCost.get());
        this.setEquipment();
        this.setRandomSpawnBonus();
        this.setPersistenceRequired();

        // Hold TNT in main hand
        this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.TNT));

        // Give 3 TNT blocks in slot 6
        ItemStack tnt = new ItemStack(Items.TNT);
        tnt.setCount(RecruitsServerConfig.SaboteurTNTCount.get());
        this.inventory.setItem(6, tnt);

        AbstractRecruitEntity.applySpawnValues(this);
    }

    @Override
    public boolean canHoldItem(ItemStack itemStack) {
        return !(itemStack.getItem() instanceof BowItem || itemStack.getItem() instanceof CrossbowItem);
    }

    @Override
    public boolean wantsToPickUp(ItemStack itemStack) {
        if (itemStack.getItem() instanceof SwordItem && this.getMainHandItem().isEmpty()) {
            return !hasSameTypeOfItem(itemStack);
        }
        if (itemStack.is(Items.TNT)) return true;
        return super.wantsToPickUp(itemStack);
    }

    @Override
    public Predicate<ItemEntity> getAllowedItems() {
        return ALLOWED_ITEMS;
    }

    public List<List<String>> getEquipment() {
        return RecruitsServerConfig.SaboteurStartEquipments.get();
    }

    public Predicate<ItemStack> getWeaponType() {
        return itemStack -> itemStack.getItem() instanceof SwordItem;
    }
}
