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
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
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
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
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

	// Subclassed for one answer. A bare SimpleContainer says every player may
	// keep using it, forever: the screen would stay open and workable from
	// across the map, and outlive the golem's death. Vanilla's chest boat
	// answers with reach and aliveness, and so does this.
	private final SimpleContainer inventory =
			new SimpleContainer(ArgilusConfig.get().inventoryRows() * 9) {
				@Override
				public boolean stillValid(Player player) {
					return !ArgilusEntity.this.isRemoved()
							&& player.isWithinEntityInteractionRange(
									ArgilusEntity.this.getBoundingBox(), CONTAINER_REACH);
				}
			};

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

		// A berry bush is PathType.DAMAGING, which the navigator refuses to enter
		// at all, and every tile beside one is DAMAGING_IN_NEIGHBOR at a malus of
		// eight. Left at that, a berry patch is a wall: the golem cannot reach
		// the bushes in the middle of it, only the ones on the edge.
		//
		// Cacti share DAMAGING, and there is no per-block malus, so opening one
		// opens the other. That is why the golem is made proof against both
		// below rather than against berries alone.
		this.setPathfindingMalus(PathType.DAMAGING, 0.0F);
		this.setPathfindingMalus(PathType.DAMAGING_IN_NEIGHBOR, 0.0F);
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
		this.goalSelector.addGoal(5, new SowGoal(this));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	@Override
	public SimpleContainer getInventory() {
		return this.inventory;
	}

	// A farm keeps working while its owner is away, so the golem must survive
	// the owner walking off to mine. Mob.checkDespawn deletes anything past the
	// category's despawn distance that answers yes here, which is what emptied a
	// fenced pen with nobody near it. Answering in code rather than setting the
	// persistence flag at summon time also rescues the golems already placed,
	// since that flag comes back from the save file.
	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	// The counterpart to clearing the DAMAGING malus. The golem now walks
	// straight through berry patches, and would walk into cacti given the
	// chance, so both have to stop hurting it. It has no regeneration of any
	// kind and works unattended for hours: a point of damage per step would be
	// a slow death sentence for doing its job.
	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return source.is(DamageTypes.SWEET_BERRY_BUSH)
				|| source.is(DamageTypes.CACTUS)
				|| super.isInvulnerableTo(level, source);
	}

	// Right click opens the same screen a chest of this size would, sharing the
	// golem's own container. Handing over write access as well as read is what
	// makes it useful: bone meal and seeds can be loaded straight into the
	// golem instead of being routed through its deposit chest.
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		int rows = this.inventory.getContainerSize() / 9;

		// No side check: Player.openMenu is a no-op on the client and the real
		// one lives on ServerPlayer, which is how vanilla splits it too.
		player.openMenu(new SimpleMenuProvider(
				(id, playerInventory, opener) ->
						new ChestMenu(menuTypeForRows(rows), id, playerInventory, this.inventory, rows),
				this.getDisplayName()));

		this.gameEvent(GameEvent.CONTAINER_OPEN, player);
		return InteractionResult.SUCCESS;
	}

	private static MenuType<ChestMenu> menuTypeForRows(int rows) {
		return switch (rows) {
			case 1 -> MenuType.GENERIC_9x1;
			case 3 -> MenuType.GENERIC_9x3;
			default -> MenuType.GENERIC_9x2;
		};
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

	// Nothing does this by default. InventoryCarrier carries no drop code at
	// all, so a mob that stays silent here takes its cargo to the grave the way
	// a villager does. The clay on top is what the golem was made of, in the
	// spirit of the iron golem returning some of its ingots.
	@Override
	protected void dropEquipment(ServerLevel level) {
		super.dropEquipment(level);
		Containers.dropContents(level, this, this.inventory);
		this.spawnAtLocation(level, new ItemStack(Items.CLAY_BALL, 1 + this.random.nextInt(2)));
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
