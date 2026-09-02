package re.jerome.argilus.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import re.jerome.argilus.ArgilusConfig;

// Grows the field outwards and repairs trampled tiles, by adjacency alone and
// with no memory of its own. Two substrates, one rule shared between them: a
// bare tile is worth preparing only when it touches one already in production.
//
// Dirt beside farmland is tilled and sown in the same action. Only plain dirt
// qualifies. A hoe turns coarse and rooted dirt into ordinary dirt rather than
// farmland, and does nothing at all to podzol or mycelium, so including them
// would either do nothing or quietly eat the terrain. Grass and dirt paths are
// left alone on purpose: the golem must not chew through the lawn or the paths
// around the farm.
//
// Nether wart needs no tilling, so its ground is sown directly. What may carry
// it comes from #minecraft:supports_nether_wart rather than from naming soul
// sand, so a datapack that widens it is followed for free.
public class SowGoal extends Goal {
	private static final double REACH = 2.5;
	private static final double SPEED = 0.9;

	private final ArgilusEntity golem;
	private final List<BlockPos> sowable = new ArrayList<>();
	private BlockPos target;
	private long nextScanTime;
	private long nextSowTime;

	public SowGoal(ArgilusEntity golem) {
		this.golem = golem;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!(this.golem.level() instanceof ServerLevel level)) {
			return false;
		}

		// The mandatory guard: never break ground without something to put in
		// it. Tilling alone would leave bare farmland, which dries out, reverts
		// to dirt, and gets tilled again forever.
		if (!this.hasAnySeed()) {
			return false;
		}

		this.rescanIfDue(level);
		return !this.sowable.isEmpty();
	}

	@Override
	public boolean canContinueToUse() {
		return !this.sowable.isEmpty() && this.hasAnySeed();
	}

	@Override
	public void start() {
		this.target = null;
	}

	@Override
	public void stop() {
		this.target = null;
		this.sowable.clear();
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

		if (this.target != null && !this.isSowable(level, this.target)) {
			this.sowable.remove(this.target);
			this.target = null;
		}

		if (this.target == null) {
			this.target = this.nearestSowable();

			if (this.target == null) {
				return;
			}

			this.golem.getNavigation().moveTo(
					this.target.getX() + 0.5, this.target.getY(), this.target.getZ() + 0.5, SPEED);
		}

		this.golem.resetIdleTicks();
		this.golem.getLookControl().setLookAt(
				this.target.getX() + 0.5, this.target.getY() + 0.5, this.target.getZ() + 0.5);

		long now = level.getGameTime();

		if (this.target.closerToCenterThan(this.golem.position(), REACH)) {
			if (now >= this.nextSowTime) {
				this.sow(level, this.target);
				this.sowable.remove(this.target);
				this.target = null;
				this.nextSowTime = now + ArgilusConfig.get().harvestIntervalTicks();
			}
		} else if (this.golem.getNavigation().isDone()) {
			this.sowable.remove(this.target);
			this.target = null;
		}
	}

	private void sow(ServerLevel level, BlockPos pos) {
		if (this.isTillable(level, pos)) {
			this.tillAndSow(level, pos);
		} else if (this.isWartGround(level, pos)) {
			this.sowWart(level, pos);
		}
	}

	// Till and sow in one go. Splitting them would recreate the loop the guard
	// in canUse exists to prevent.
	private void tillAndSow(ServerLevel level, BlockPos pos) {
		int slot = this.findCropSeedSlot();

		if (slot < 0) {
			return;
		}

		ItemStack seed = this.golem.getInventory().getItem(slot);

		if (!(seed.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof CropBlock crop)) {
			return;
		}

		BlockState farmland = Blocks.FARMLAND.defaultBlockState();
		level.setBlock(pos, farmland, Block.UPDATE_ALL);
		level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(this.golem, farmland));
		level.playSound(
				null, pos.getX(), pos.getY(), pos.getZ(),
				SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);

		this.plant(level, pos.above(), crop.getStateForAge(0), SoundEvents.CROP_PLANTED);
		this.consume(slot, seed);
	}

	// No tilling here: nether wart goes straight onto its support block, which
	// does not degrade the way bare farmland does, so the loop that forces the
	// seed check above cannot happen. The check stays anyway, because sowing
	// without a seed is simply nothing to do.
	private void sowWart(ServerLevel level, BlockPos pos) {
		int slot = this.findWartSlot();

		if (slot < 0) {
			return;
		}

		ItemStack seed = this.golem.getInventory().getItem(slot);
		this.plant(
				level, pos.above(),
				Blocks.NETHER_WART.defaultBlockState(), SoundEvents.NETHER_WART_PLANTED);
		this.consume(slot, seed);
	}

	private void plant(ServerLevel level, BlockPos pos, BlockState seedling, SoundEvent sound) {
		level.setBlock(pos, seedling, Block.UPDATE_ALL);
		level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(this.golem, seedling));
		level.playSound(
				null, pos.getX(), pos.getY(), pos.getZ(), sound, SoundSource.BLOCKS, 1.0F, 1.0F);
	}

	private void consume(int slot, ItemStack seed) {
		seed.shrink(1);

		if (seed.isEmpty()) {
			this.golem.getInventory().setItem(slot, ItemStack.EMPTY);
		}
	}

	private boolean hasAnySeed() {
		return this.findCropSeedSlot() >= 0 || this.findWartSlot() >= 0;
	}

	private int findCropSeedSlot() {
		return this.findSlot(block -> block instanceof CropBlock);
	}

	private int findWartSlot() {
		return this.findSlot(block -> block instanceof NetherWartBlock);
	}

	private int findSlot(Predicate<Block> wanted) {
		SimpleContainer inventory = this.golem.getInventory();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (!stack.isEmpty()
					&& stack.getItem() instanceof BlockItem item
					&& wanted.test(item.getBlock())) {
				return slot;
			}
		}

		return -1;
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
		this.sowable.clear();

		int radius = ArgilusConfig.get().radius();
		BlockPos origin = this.golem.blockPosition();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy = -2; dy <= 2; dy++) {
					cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);

					if (level.isLoaded(cursor) && this.isSowable(level, cursor)) {
						this.sowable.add(cursor.immutable());
					}
				}
			}
		}
	}

	private boolean isSowable(ServerLevel level, BlockPos pos) {
		return this.isTillable(level, pos) || this.isWartGround(level, pos);
	}

	private BlockPos nearestSowable() {
		BlockPos nearest = null;
		double best = Double.MAX_VALUE;

		for (BlockPos pos : this.sowable) {
			double distance = pos.distToCenterSqr(this.golem.position());

			if (distance < best) {
				best = distance;
				nearest = pos;
			}
		}

		return nearest;
	}

	// Block reads first, inventory search last. The scan asks this of every
	// position in the radius, and walking the container for each one would put
	// real work into a loop that is meant to be cheap.
	private boolean isTillable(ServerLevel level, BlockPos pos) {
		if (level.getBlockState(pos).getBlock() != Blocks.DIRT) {
			return false;
		}

		if (!level.getBlockState(pos.above()).isAir() || isFruitSpot(level, pos)) {
			return false;
		}

		if (!hasNeighbour(level, pos, FarmlandBlock.class)) {
			return false;
		}

		return this.findCropSeedSlot() >= 0;
	}

	// A stem grows its fruit onto a tile beside it, resting on whatever is
	// underneath. Tilling that dirt and sowing it takes the space away, and the
	// player's melon patch quietly stops producing. Both stem blocks count: an
	// attached stem reverts to a plain one the moment its fruit is picked, and
	// can then aim anywhere again.
	private static boolean isFruitSpot(ServerLevel level, BlockPos pos) {
		BlockPos fruit = pos.above();

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			Block neighbour = level.getBlockState(fruit.relative(direction)).getBlock();

			if (neighbour instanceof StemBlock || neighbour instanceof AttachedStemBlock) {
				return true;
			}
		}

		return false;
	}

	private boolean isWartGround(ServerLevel level, BlockPos pos) {
		if (!level.getBlockState(pos).is(BlockTags.SUPPORTS_NETHER_WART)) {
			return false;
		}

		if (!level.getBlockState(pos.above()).isAir()) {
			return false;
		}

		// Adjacency is judged where the wart would stand, not where its footing
		// is, so a bed one block lower does not count as touching.
		if (!hasNeighbour(level, pos.above(), NetherWartBlock.class)) {
			return false;
		}

		return this.findWartSlot() >= 0;
	}

	private static boolean hasNeighbour(ServerLevel level, BlockPos pos, Class<? extends Block> type) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (type.isInstance(level.getBlockState(pos.relative(direction)).getBlock())) {
				return true;
			}
		}

		return false;
	}
}
