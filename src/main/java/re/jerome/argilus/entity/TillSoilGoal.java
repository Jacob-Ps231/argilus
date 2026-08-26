package re.jerome.argilus.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import re.jerome.argilus.ArgilusConfig;

// Repairs trampled tiles and grows the field outwards, by adjacency alone and
// with no memory of its own.
//
// Only plain dirt qualifies. A hoe turns coarse and rooted dirt into ordinary
// dirt rather than farmland, and does nothing at all to podzol or mycelium, so
// including them would either do nothing or quietly eat the terrain. Grass and
// dirt paths are left alone on purpose: the golem must not chew through the
// lawn or the paths around the farm.
public class TillSoilGoal extends Goal {
	private static final double REACH = 2.5;
	private static final double SPEED = 0.9;

	private final ArgilusEntity golem;
	private final List<BlockPos> tillable = new ArrayList<>();
	private BlockPos target;
	private long nextScanTime;
	private long nextTillTime;

	public TillSoilGoal(ArgilusEntity golem) {
		this.golem = golem;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!(this.golem.level() instanceof ServerLevel level)) {
			return false;
		}

		// The mandatory guard: never break ground without a seed to put in it.
		// Tilling alone would leave bare farmland, which dries out, reverts to
		// dirt, and gets tilled again forever.
		if (this.findSeedSlot() < 0) {
			return false;
		}

		this.rescanIfDue(level);
		return !this.tillable.isEmpty();
	}

	@Override
	public boolean canContinueToUse() {
		return !this.tillable.isEmpty() && this.findSeedSlot() >= 0;
	}

	@Override
	public void start() {
		this.target = null;
	}

	@Override
	public void stop() {
		this.target = null;
		this.tillable.clear();
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

		if (this.target != null && !isTillable(level, this.target)) {
			this.tillable.remove(this.target);
			this.target = null;
		}

		if (this.target == null) {
			this.target = this.nearestTillable();

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
			if (now >= this.nextTillTime) {
				this.till(level, this.target);
				this.tillable.remove(this.target);
				this.target = null;
				this.nextTillTime = now + ArgilusConfig.get().harvestIntervalTicks();
			}
		} else if (this.golem.getNavigation().isDone()) {
			this.tillable.remove(this.target);
			this.target = null;
		}
	}

	// Till and sow in one go. Splitting them would recreate the loop the guard
	// above exists to prevent.
	private void till(ServerLevel level, BlockPos pos) {
		int slot = this.findSeedSlot();

		if (slot < 0 || !isTillable(level, pos)) {
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

		BlockPos above = pos.above();
		BlockState seedling = crop.getStateForAge(0);
		level.setBlock(above, seedling, Block.UPDATE_ALL);
		level.gameEvent(GameEvent.BLOCK_PLACE, above, GameEvent.Context.of(this.golem, seedling));
		level.playSound(
				null, above.getX(), above.getY(), above.getZ(),
				SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);

		seed.shrink(1);

		if (seed.isEmpty()) {
			this.golem.getInventory().setItem(slot, ItemStack.EMPTY);
		}
	}

	private int findSeedSlot() {
		SimpleContainer inventory = this.golem.getInventory();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (!stack.isEmpty()
					&& stack.getItem() instanceof BlockItem item
					&& item.getBlock() instanceof CropBlock) {
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
		this.tillable.clear();

		int radius = ArgilusConfig.get().radius();
		BlockPos origin = this.golem.blockPosition();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy = -2; dy <= 2; dy++) {
					cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);

					if (level.isLoaded(cursor) && isTillable(level, cursor)) {
						this.tillable.add(cursor.immutable());
					}
				}
			}
		}
	}

	private BlockPos nearestTillable() {
		BlockPos nearest = null;
		double best = Double.MAX_VALUE;

		for (BlockPos pos : this.tillable) {
			double distance = pos.distToCenterSqr(this.golem.position());

			if (distance < best) {
				best = distance;
				nearest = pos;
			}
		}

		return nearest;
	}

	private static boolean isTillable(ServerLevel level, BlockPos pos) {
		if (level.getBlockState(pos).getBlock() != Blocks.DIRT) {
			return false;
		}

		if (!level.getBlockState(pos.above()).isAir()) {
			return false;
		}

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (level.getBlockState(pos.relative(direction)).getBlock() instanceof FarmlandBlock) {
				return true;
			}
		}

		return false;
	}
}
