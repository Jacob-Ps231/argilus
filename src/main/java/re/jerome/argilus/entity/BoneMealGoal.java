package re.jerome.argilus.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import re.jerome.argilus.ArgilusConfig;

// Speeds up whatever is still growing, using bone meal the golem happens to
// carry. It never goes looking for more: restocking happens during a deposit,
// where it already is.
//
// It takes the nearest unripe crop and stays on it until it ripens, then moves
// to the next nearest, which reads as a deliberate sweep across the field.
//
// That only works because this goal outranks harvesting. Below it, the two fed
// each other: harvesting replants at age 0 right under the golem, that seedling
// is the closest unripe crop, so it got bone mealed, ripened, harvested and
// replanted on the spot until the stack was gone. Running the whole bone meal
// pass first removes the interleaving, and ArgilusEntity parks each replanted
// tile for a while so the pass cannot restart on what harvesting just sowed.
//
// Only CropBlock is targeted. Melon and pumpkin stems are bonemealable too, but
// bone meal grows the stem and never produces the fruit, and SPEC.md says not to
// work around that vanilla behaviour. Not aiming at them at all is the simplest
// way to honour it.
public class BoneMealGoal extends Goal {
	private static final int VERTICAL_REACH = 2;
	private static final double REACH = 2.5;
	private static final double SPEED = 0.9;

	private final ArgilusEntity golem;
	private final List<BlockPos> growing = new ArrayList<>();
	private BlockPos target;
	private long nextScanTime;
	private long nextUseTime;

	public BoneMealGoal(ArgilusEntity golem) {
		this.golem = golem;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!(this.golem.level() instanceof ServerLevel level)) {
			return false;
		}

		if (this.golem.boneMealSlot() < 0) {
			return false;
		}

		this.rescanIfDue(level);
		return !this.growing.isEmpty();
	}

	@Override
	public boolean canContinueToUse() {
		return !this.growing.isEmpty() && this.golem.boneMealSlot() >= 0;
	}

	@Override
	public void start() {
		this.target = null;
	}

	@Override
	public void stop() {
		this.target = null;
		this.growing.clear();
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

		if (this.target != null && !isGrowing(level, this.target)) {
			this.growing.remove(this.target);
			this.target = null;
		}

		if (this.target == null) {
			this.target = this.pickGrowing();

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
			if (now >= this.nextUseTime) {
				this.apply(level, this.target, now);
				this.target = null;
				this.nextUseTime = now + ArgilusConfig.get().harvestIntervalTicks();
			}
		} else if (this.golem.getNavigation().isDone()) {
			this.growing.remove(this.target);
			this.target = null;
		}
	}

	// growCrop already checks validity, rolls for success, grows the block and
	// consumes one item, so the vanilla odds are kept rather than reinvented.
	private void apply(ServerLevel level, BlockPos pos, long now) {
		int slot = this.golem.boneMealSlot();

		if (slot < 0) {
			return;
		}

		ItemStack stack = this.golem.getInventory().getItem(slot);

		if (BoneMealItem.growCrop(stack, level, pos)) {
			level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 0);
		}

		if (stack.isEmpty()) {
			this.golem.getInventory().setItem(slot, ItemStack.EMPTY);
		}

		// A crop usually needs several doses. Keep it as a target until it is
		// actually ripe, otherwise the golem walks off after every single dose.
		if (!isGrowing(level, pos)) {
			this.growing.remove(pos);
		}
	}

	private void rescanIfDue(ServerLevel level) {
		long now = level.getGameTime();

		if (now < this.nextScanTime) {
			return;
		}

		this.nextScanTime = now + ArgilusConfig.get().scanIntervalTicks();
		this.scan(level, now);
	}

	private void scan(ServerLevel level, long now) {
		this.growing.clear();

		int radius = ArgilusConfig.get().radius();
		BlockPos origin = this.golem.blockPosition();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy = -VERTICAL_REACH; dy <= VERTICAL_REACH; dy++) {
					cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);

					if (!level.isLoaded(cursor) || !isGrowing(level, cursor)) {
						continue;
					}

					BlockPos pos = cursor.immutable();

					if (!this.golem.isBoneMealSuppressed(pos, now)) {
						this.growing.add(pos);
					}
				}
			}
		}
	}

	private BlockPos pickGrowing() {
		BlockPos nearest = null;
		double best = Double.MAX_VALUE;

		for (BlockPos pos : this.growing) {
			double distance = pos.distToCenterSqr(this.golem.position());

			if (distance < best) {
				best = distance;
				nearest = pos;
			}
		}

		return nearest;
	}

	private static boolean isGrowing(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state);
	}
}
