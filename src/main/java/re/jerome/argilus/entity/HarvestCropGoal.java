package re.jerome.argilus.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import re.jerome.argilus.ArgilusConfig;

// Detection mirrors the villager's HarvestFarmland behavior: any CropBlock at
// max age, no hardcoded block list, so modded crops extending CropBlock work.
// Unlike the villager, targets are cached and rescanned periodically instead of
// being rebuilt from a 3x3x3 cube every time.
//
// Pumpkins and melons are found from their stem, never by looking for fruit
// blocks. Nothing in 26.2 identifies a fruit block: no shared superclass, no
// tag, and StemBlock keeps its fruit and seed fields private with no accessor.
// But an attached stem only exists while its fruit does, so whatever it points
// at is its fruit by construction. Decorative pumpkins have no stem aimed at
// them and are therefore invisible to the golem.
public class HarvestCropGoal extends Goal {
	private static final int VERTICAL_REACH = 2;
	private static final double HARVEST_REACH = 2.5;
	private static final double SPEED = 0.9;

	private final ArgilusEntity golem;
	private final List<BlockPos> targets = new ArrayList<>();
	private BlockPos target;
	private long nextScanTime;
	private long nextHarvestTime;
	private ItemStack silkTouch;

	public HarvestCropGoal(ArgilusEntity golem) {
		this.golem = golem;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!(this.golem.level() instanceof ServerLevel level)) {
			return false;
		}

		this.rescanIfDue(level);
		return !this.targets.isEmpty();
	}

	@Override
	public boolean canContinueToUse() {
		return !this.targets.isEmpty();
	}

	@Override
	public void start() {
		this.target = null;
	}

	@Override
	public void stop() {
		this.target = null;
		this.targets.clear();
		this.golem.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (!(this.golem.level() instanceof ServerLevel level)) {
			return;
		}

		this.rescanIfDue(level);

		// The player may have harvested it first, or it may have been broken.
		if (this.target != null && !isHarvestable(level, this.target)) {
			this.targets.remove(this.target);
			this.target = null;
		}

		if (this.target == null) {
			this.target = this.nearestTarget();

			if (this.target == null) {
				return;
			}

			this.golem.getNavigation().moveTo(
					this.target.getX() + 0.5, this.target.getY(), this.target.getZ() + 0.5, SPEED);
		}

		// Walking to a crop is work, not idleness. Counting the walk as idle time
		// sent the golem to the chest after every single crop on a sparse field.
		this.golem.resetIdleTicks();

		this.golem.getLookControl().setLookAt(
				this.target.getX() + 0.5, this.target.getY() + 0.5, this.target.getZ() + 0.5);

		long now = level.getGameTime();

		if (this.target.closerToCenterThan(this.golem.position(), HARVEST_REACH)) {
			if (now >= this.nextHarvestTime) {
				this.harvest(level, this.target);
				this.targets.remove(this.target);
				this.target = null;
				this.nextHarvestTime = now + ArgilusConfig.get().harvestIntervalTicks();
			}
		} else if (this.golem.getNavigation().isDone()) {
			// Unreachable target: drop it rather than stall on it forever.
			this.targets.remove(this.target);
			this.target = null;
		}
	}

	private void harvest(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);

		if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
			this.harvestCrop(level, pos, state, crop);
		} else if (hasStemPointingAt(level, pos)) {
			this.harvestFruit(level, pos, state);
		}
	}

	private void harvestCrop(ServerLevel level, BlockPos pos, BlockState state, CropBlock crop) {
		// Read the drops while the block still stands. getDrops does not spawn
		// item entities, unlike every dropResources overload.
		List<ItemStack> drops = Block.getDrops(state, level, pos, null, this.golem, ItemStack.EMPTY);
		boolean replanting = takeSeed(drops, state.getBlock());

		// Nothing in the harvest can reseed it, so fall back on what the golem
		// already carries rather than leave the tile bare.
		if (!replanting) {
			replanting = this.takeSeedFromInventory(state.getBlock());
		}

		// false: keep the break particles and sound, skip the vanilla drops,
		// which we hand out ourselves once a seed has been set aside.
		level.destroyBlock(pos, false, this.golem);

		if (replanting) {
			BlockState seedling = crop.getStateForAge(0);
			level.setBlock(pos, seedling, Block.UPDATE_ALL);
			level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(this.golem, seedling));
			level.playSound(
					null, pos.getX(), pos.getY(), pos.getZ(),
					SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
		}

		this.collect(level, pos, drops);
	}

	// Melons drop slices to a bare hand and a whole block to silk touch, and the
	// loot table decides from the enchantment component of the tool. Asking for
	// the block generalises to modded stem fruit, unlike special-casing an item.
	// The stem itself is never touched: only the position it points at.
	private void harvestFruit(ServerLevel level, BlockPos pos, BlockState state) {
		List<ItemStack> drops =
				Block.getDrops(state, level, pos, null, this.golem, this.silkTouchTool(level));

		level.destroyBlock(pos, false, this.golem);
		this.collect(level, pos, drops);
	}

	private void collect(ServerLevel level, BlockPos pos, List<ItemStack> drops) {
		for (ItemStack stack : drops) {
			if (stack.isEmpty()) {
				continue;
			}

			ItemStack rest = this.golem.getInventory().addItem(stack);

			if (!rest.isEmpty()) {
				Block.popResource(level, pos, rest);
			}
		}

		this.golem.resetIdleTicks();
	}

	private ItemStack silkTouchTool(ServerLevel level) {
		if (this.silkTouch == null) {
			ItemStack tool = new ItemStack(Items.SHEARS);
			tool.enchant(
					level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
							.getOrThrow(Enchantments.SILK_TOUCH),
					1);
			this.silkTouch = tool;
		}

		return this.silkTouch;
	}

	// The seed is the drop whose item places the very block just harvested.
	// CropBlock.getBaseSeedId is protected and unreachable from a mod, and a
	// hardcoded table would not survive contact with modded crops. Consuming one
	// seed keeps the net yield identical to a player replanting behind himself.
	private static boolean takeSeed(List<ItemStack> drops, Block harvested) {
		for (ItemStack stack : drops) {
			if (isSeedFor(stack, harvested)) {
				stack.shrink(1);
				return true;
			}
		}

		return false;
	}

	private boolean takeSeedFromInventory(Block harvested) {
		SimpleContainer inventory = this.golem.getInventory();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (isSeedFor(stack, harvested)) {
				stack.shrink(1);

				if (stack.isEmpty()) {
					inventory.setItem(slot, ItemStack.EMPTY);
				}

				return true;
			}
		}

		return false;
	}

	private static boolean isSeedFor(ItemStack stack, Block harvested) {
		return !stack.isEmpty()
				&& stack.getItem() instanceof BlockItem item
				&& item.getBlock() == harvested;
	}

	private void rescanIfDue(ServerLevel level) {
		long now = level.getGameTime();

		if (now < this.nextScanTime) {
			return;
		}

		this.nextScanTime = now + ArgilusConfig.get().scanIntervalTicks();
		this.scan(level);
	}

	private void scan(ServerLevel level) {
		this.targets.clear();

		int radius = ArgilusConfig.get().radius();
		BlockPos origin = this.golem.blockPosition();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		long sumX = 0;
		long sumY = 0;
		long sumZ = 0;

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy = -VERTICAL_REACH; dy <= VERTICAL_REACH; dy++) {
					cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);

					if (!level.isLoaded(cursor)) {
						continue;
					}

					BlockState state = level.getBlockState(cursor);
					BlockPos found = null;

					if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
						found = cursor.immutable();
					} else if (state.getBlock() instanceof AttachedStemBlock) {
						// Two stems can point at the same pumpkin.
						BlockPos fruit = cursor.relative(state.getValue(AttachedStemBlock.FACING));
						found = this.targets.contains(fruit) ? null : fruit;
					}

					if (found != null) {
						this.targets.add(found);
						sumX += found.getX();
						sumY += found.getY();
						sumZ += found.getZ();
					}
				}
			}
		}

		// The deposit goal looks for a container around the field, not around
		// wherever the golem happens to be standing.
		if (!this.targets.isEmpty()) {
			int count = this.targets.size();
			this.golem.setFieldCenter(new BlockPos(
					(int) (sumX / count), (int) (sumY / count), (int) (sumZ / count)));
		}
	}

	private BlockPos nearestTarget() {
		BlockPos nearest = null;
		double best = Double.MAX_VALUE;

		for (BlockPos pos : this.targets) {
			double distance = pos.distToCenterSqr(this.golem.position());

			if (distance < best) {
				best = distance;
				nearest = pos;
			}
		}

		return nearest;
	}

	private static boolean isHarvestable(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);

		if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
			return true;
		}

		return hasStemPointingAt(level, pos);
	}

	private static boolean hasStemPointingAt(ServerLevel level, BlockPos pos) {
		if (level.getBlockState(pos).isAir()) {
			return false;
		}

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockState neighbour = level.getBlockState(pos.relative(direction));

			if (neighbour.getBlock() instanceof AttachedStemBlock
					&& neighbour.getValue(AttachedStemBlock.FACING) == direction.getOpposite()) {
				return true;
			}
		}

		return false;
	}
}
