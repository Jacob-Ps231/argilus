package re.jerome.argilus.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ContainerUser;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import re.jerome.argilus.ArgilusConfig;

public class ArgilusEntity extends PathfinderMob implements InventoryCarrier, ContainerUser {
	private static final double CONTAINER_REACH = 3.0;

	private final SimpleContainer inventory = new SimpleContainer(ArgilusConfig.get().inventorySize());

	// Stacks that no longer fit after the configured size shrank. The entity is
	// not in the world when save data is read, so they wait for the first tick.
	private final List<ItemStack> overflow = new ArrayList<>();

	private @Nullable BlockPos depositPos;
	private @Nullable BlockPos openedContainerPos;
	private @Nullable BlockPos fieldCenter;
	private int idleTicks;

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
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new DepositGoal(this));
		this.goalSelector.addGoal(2, new HarvestCropGoal(this));
		this.goalSelector.addGoal(3, new TillSoilGoal(this));
		this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
	}

	@Override
	public SimpleContainer getInventory() {
		return this.inventory;
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
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.depositPos = input.read("deposit_pos", BlockPos.CODEC).orElse(null);

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
