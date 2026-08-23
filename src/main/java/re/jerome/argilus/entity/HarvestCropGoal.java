package re.jerome.argilus.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import re.jerome.argilus.ArgilusConfig;

// Detection mirrors the villager's HarvestFarmland behavior: any CropBlock at
// max age, no hardcoded block list, so modded crops extending CropBlock work.
// Unlike the villager, targets are cached and rescanned periodically instead of
// being rebuilt from a 3x3x3 cube every time.
public class HarvestCropGoal extends Goal {
	private static final int VERTICAL_REACH = 2;
	private static final double HARVEST_REACH = 2.5;
	private static final double SPEED = 0.9;

	private final ArgilusEntity golem;
	private final List<BlockPos> ripe = new ArrayList<>();
	private BlockPos target;
	private long nextScanTime;
	private long nextHarvestTime;

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
		return !this.ripe.isEmpty();
	}

	@Override
	public boolean canContinueToUse() {
		return !this.ripe.isEmpty();
	}

	@Override
	public void start() {
		this.target = null;
	}

	@Override
	public void stop() {
		this.target = null;
		this.ripe.clear();
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
		if (this.target != null && !isRipe(level, this.target)) {
			this.ripe.remove(this.target);
			this.target = null;
		}

		if (this.target == null) {
			this.target = this.nearestRipe();

			if (this.target == null) {
				return;
			}

			this.golem.getNavigation().moveTo(
					this.target.getX() + 0.5, this.target.getY(), this.target.getZ() + 0.5, SPEED);
		}

		this.golem.getLookControl().setLookAt(
				this.target.getX() + 0.5, this.target.getY() + 0.5, this.target.getZ() + 0.5);

		long now = level.getGameTime();

		if (this.target.closerToCenterThan(this.golem.position(), HARVEST_REACH)) {
			if (now >= this.nextHarvestTime) {
				level.destroyBlock(this.target, true, this.golem);
				this.ripe.remove(this.target);
				this.target = null;
				this.nextHarvestTime = now + ArgilusConfig.get().harvestIntervalTicks();
			}
		} else if (this.golem.getNavigation().isDone()) {
			// Unreachable target: drop it rather than stall on it forever.
			this.ripe.remove(this.target);
			this.target = null;
		}
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
		this.ripe.clear();

		int radius = ArgilusConfig.get().radius();
		BlockPos origin = this.golem.blockPosition();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy = -VERTICAL_REACH; dy <= VERTICAL_REACH; dy++) {
					cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);

					if (level.isLoaded(cursor) && isRipe(level, cursor)) {
						this.ripe.add(cursor.immutable());
					}
				}
			}
		}
	}

	private BlockPos nearestRipe() {
		BlockPos nearest = null;
		double best = Double.MAX_VALUE;

		for (BlockPos pos : this.ripe) {
			double distance = pos.distToCenterSqr(this.golem.position());

			if (distance < best) {
				best = distance;
				nearest = pos;
			}
		}

		return nearest;
	}

	private static boolean isRipe(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
	}
}
