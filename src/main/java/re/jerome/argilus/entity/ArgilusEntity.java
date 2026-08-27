package re.jerome.argilus.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import re.jerome.argilus.ArgilusConfig;

public class ArgilusEntity extends PathfinderMob implements InventoryCarrier, ContainerUser {
	private static final double CONTAINER_REACH = 3.0;
	private static final int BONE_MEAL_SUPPRESSION = 1200;

	// Borrowed from the clay block until the golem gets sounds of its own, read
	// from the block rather than hardcoded so it follows any vanilla change.
	private static final SoundType CLAY = Blocks.CLAY.defaultBlockState().getSoundType();

	// Rendering happens on the client, so the finish has to be synched rather
	// than kept in a plain field, which would never leave the server.
	private static final EntityDataAccessor<Integer> VARIANT =
			SynchedEntityData.defineId(ArgilusEntity.class, EntityDataSerializers.INT);

	private final SimpleContainer inventory = new SimpleContainer(ArgilusConfig.get().inventorySize());

	// Stacks that no longer fit after the configured size shrank. The entity is
	// not in the world when save data is read, so they wait for the first tick.
	private final List<ItemStack> overflow = new ArrayList<>();

	private @Nullable BlockPos depositPos;
	private @Nullable BlockPos openedContainerPos;
	private @Nullable BlockPos fieldCenter;
	private int idleTicks;
	private final Map<BlockPos, Long> boneMealSuppressed = new HashMap<>();

	public ArgilusEntity(EntityType<? extends ArgilusEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(VARIANT, ArgilusVariant.SMOOTH.ordinal());
	}

	public ArgilusVariant getVariant() {
		return ArgilusVariant.byId(this.entityData.get(VARIANT));
	}

	public void setVariant(ArgilusVariant variant) {
		this.entityData.set(VARIANT, variant.ordinal());
	}

	public void randomiseVariant() {
		this.setVariant(ArgilusVariant.random(this.getRandom()));
	}

	// Covers the spawn egg. The clay pattern goes through EntityType.create and
	// never reaches here, so ArgilusSummon draws its own.
	@Override
	public SpawnGroupData finalizeSpawn(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason reason,
			SpawnGroupData group) {
		this.randomiseVariant();
		return super.finalizeSpawn(level, difficulty, reason, group);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new DepositGoal(this));
		this.goalSelector.addGoal(2, new BoneMealGoal(this));
		this.goalSelector.addGoal(3, new HarvestCropGoal(this));
		this.goalSelector.addGoal(4, new CollectItemsGoal(this));
		this.goalSelector.addGoal(5, new TillSoilGoal(this));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	@Override
	public SimpleContainer getInventory() {
		return this.inventory;
	}

	// Bone meal never leaves the golem at a deposit, so it permanently occupies
	// one slot. That is the slot SPEC.md asks to reserve, without the second
	// container and second NBT entry a hard index would cost.
	// A crop the golem just replanted is age 0, so it would be an obvious bone
	// meal target the instant it is planted: feed, ripen, harvest, replant, feed
	// again, forever on one tile. Harvesting parks the tile here instead, long
	// enough for the golem to work the rest of the field first.
	public void suppressBoneMeal(BlockPos pos, long now) {
		this.boneMealSuppressed.put(pos, now + BONE_MEAL_SUPPRESSION);
	}

	public boolean isBoneMealSuppressed(BlockPos pos, long now) {
		this.boneMealSuppressed.values().removeIf(until -> until <= now);
		return this.boneMealSuppressed.containsKey(pos);
	}

	public int boneMealSlot() {
		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			if (this.inventory.getItem(slot).getItem() == Items.BONE_MEAL) {
				return slot;
			}
		}

		return -1;
	}

	public boolean isInventoryFull() {
		for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
			if (this.inventory.getItem(slot).isEmpty()) {
				return false;
			}
		}

		return true;
	}

	// ContainerOpenersCounter rechecks its openers periodically and asks every
	// nearby entity this question. Answer no and the lid snaps shut on its own.
	@Override
	public boolean hasContainerOpen(ContainerOpenersCounter counter, BlockPos pos) {
		if (this.openedContainerPos == null) {
			return false;
		}

		if (this.openedContainerPos.equals(pos)) {
			return true;
		}

		// The far half of a double chest asks about its own position.
		BlockState state = this.level().getBlockState(this.openedContainerPos);
		return state.getBlock() instanceof ChestBlock
				&& state.getValue(ChestBlock.TYPE) != ChestType.SINGLE
				&& ChestBlock.getConnectedBlockPos(this.openedContainerPos, state).equals(pos);
	}

	@Override
	public double getContainerInteractionRange() {
		return CONTAINER_REACH;
	}

	public @Nullable BlockPos getDepositPos() {
		return this.depositPos;
	}

	public void setDepositPos(@Nullable BlockPos pos) {
		this.depositPos = pos;
	}

	public void setOpenedContainerPos(@Nullable BlockPos pos) {
		this.openedContainerPos = pos;
	}

	public @Nullable BlockPos getFieldCenter() {
		return this.fieldCenter;
	}

	public void setFieldCenter(@Nullable BlockPos pos) {
		this.fieldCenter = pos;
	}

	public int getIdleTicks() {
		return this.idleTicks;
	}

	public void resetIdleTicks() {
		this.idleTicks = 0;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return CLAY.getHitSound();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return CLAY.getBreakSound();
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(CLAY.getStepSound(), 0.4F, 1.0F);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (!this.overflow.isEmpty()) {
			for (ItemStack stack : this.overflow) {
				Block.popResource(level, this.blockPosition(), stack);
			}

			this.overflow.clear();
		}

		if (this.inventory.isEmpty()) {
			this.idleTicks = 0;
		} else {
			this.idleTicks++;
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		this.writeInventoryToTag(output);
		output.storeNullable("deposit_pos", BlockPos.CODEC, this.depositPos);
		output.putString("variant", this.getVariant().getName());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.depositPos = input.read("deposit_pos", BlockPos.CODEC).orElse(null);
		this.setVariant(ArgilusVariant.byName(input.getStringOr("variant", ArgilusVariant.SMOOTH.getName())));

		// Not readInventoryFromTag: it funnels through SimpleContainer.addItem,
		// which silently drops whatever no longer fits once the configured size
		// has shrunk. Keep the leftovers instead and hand them back in world.
		this.inventory.clearContent();
		this.overflow.clear();

		for (ItemStack stack : input.listOrEmpty(InventoryCarrier.TAG_INVENTORY, ItemStack.CODEC)) {
			ItemStack rest = this.inventory.addItem(stack);

			if (!rest.isEmpty()) {
				this.overflow.add(rest);
			}
		}
	}

	// Mob persists this flag and readAdditionalSaveData restores it from the
	// save, so setting it in the constructor is silently undone for any golem
	// placed before this behaviour existed. Answering here cannot be overwritten.
	@Override
	public boolean canPickUpLoot() {
		return true;
	}

	// Vanilla only offers items the golem physically walks over, so this stays
	// scoped to its working area without any radius logic of our own. It also
	// lets it recover the contents of a chest a player broke.
	@Override
	public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
		return this.inventory.canAddItem(stack);
	}

	@Override
	protected void pickUpItem(ServerLevel level, ItemEntity itemEntity) {
		InventoryCarrier.pickUpItem(level, this, this, itemEntity);
	}
}
