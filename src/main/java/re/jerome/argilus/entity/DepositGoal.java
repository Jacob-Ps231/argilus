package re.jerome.argilus.entity;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.jspecify.annotations.Nullable;
import re.jerome.argilus.ArgilusConfig;
import re.jerome.argilus.ArgilusTags;

// Reimplements what TransportItemsBetweenContainers does for the copper golem.
// That class is a Behavior bound to a Brain, so a Goal-driven mob cannot use it,
// and its search strategy — start over on every single item, remember nothing —
// is not the one we want anyway.
public class DepositGoal extends Goal {
	private static final double SPEED = 0.9;
	private static final int OPEN_TICKS = 20;
	private static final int RETRY_COOLDOWN = 200;
	private static final int SEARCH_VERTICAL_REACH = 4;

	private final ArgilusEntity golem;
	private @Nullable BlockPos container;
	private long nextAttemptTime;
	private int openTicks;
	private boolean waitingForSpace;

	public DepositGoal(ArgilusEntity golem) {
		this.golem = golem;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!(this.golem.level() instanceof ServerLevel level)) {
			return false;
		}

		if (this.golem.getInventory().isEmpty()) {
			return false;
		}

		if (level.getGameTime() < this.nextAttemptTime) {
			return false;
		}

		boolean due = this.golem.isInventoryFull()
				|| this.golem.getIdleTicks() >= ArgilusConfig.get().depositIdleTicks();

		if (!due) {
			return false;
		}

		this.container = this.resolveContainer(level);
		return this.container != null;
	}

	@Override
	public boolean canContinueToUse() {
		return !this.golem.getInventory().isEmpty() && (this.container != null || this.waitingForSpace);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void stop() {
		this.closeContainer();
		this.container = null;
		this.waitingForSpace = false;
	}

	@Override
	public void tick() {
		if (!(this.golem.level() instanceof ServerLevel level) || this.container == null) {
			return;
		}

		long now = level.getGameTime();

		// Container full and nowhere left to put anything: stand by it rather
		// than walk back and forth for nothing. A golem waiting at a full chest
		// is something the player can read at a glance.
		if (this.waitingForSpace) {
			this.golem.getNavigation().stop();
			this.lookAtContainer();

			if (now < this.nextAttemptTime) {
				return;
			}

			this.waitingForSpace = false;
		}

		Container target = HopperBlockEntity.getContainerAt(level, this.container);

		if (target == null) {
			// Destroyed or replaced. Forget it and try once more later.
			this.golem.setDepositPos(null);
			this.container = null;
			this.nextAttemptTime = now + RETRY_COOLDOWN;
			return;
		}

		if (!this.container.closerToCenterThan(this.golem.position(), this.golem.getContainerInteractionRange())) {
			this.openTicks = 0;

			if (this.golem.getNavigation().isDone()
					&& !this.golem.getNavigation().moveTo(
							this.container.getX() + 0.5,
							this.container.getY(),
							this.container.getZ() + 0.5,
							SPEED)) {
				// Unreachable: give up on this one for a while.
				this.container = null;
				this.nextAttemptTime = now + RETRY_COOLDOWN;
			}

			return;
		}

		this.golem.getNavigation().stop();
		this.lookAtContainer();

		if (this.openTicks == 0) {
			target.startOpen(this.golem);
			this.golem.setOpenedContainerPos(this.container);
		}

		this.openTicks++;

		// Let the lid animation play before the items move.
		if (this.openTicks < OPEN_TICKS) {
			return;
		}

		boolean leftover = this.unload(target);
		this.closeContainer();
		this.openTicks = 0;
		this.golem.resetIdleTicks();

		if (!leftover) {
			this.container = null;
			return;
		}

		this.nextAttemptTime = now + RETRY_COOLDOWN;

		if (this.golem.isInventoryFull()) {
			this.waitingForSpace = true;
		} else {
			this.container = null;
		}
	}

	// Returns true when something could not be handed over.
	private boolean unload(Container target) {
		boolean leftover = false;

		for (int slot = 0; slot < this.golem.getInventory().getContainerSize(); slot++) {
			ItemStack stack = this.golem.getInventory().getItem(slot);

			if (stack.isEmpty()) {
				continue;
			}

			// addItem mutates what it is given as well as returning the rest,
			// so it gets a copy and the slot is rewritten from the result.
			ItemStack rest = HopperBlockEntity.addItem(null, target, stack.copy(), null);
			this.golem.getInventory().setItem(slot, rest);

			if (!rest.isEmpty()) {
				leftover = true;
			}
		}

		return leftover;
	}

	private void closeContainer() {
		BlockPos opened = this.container;

		if (opened == null || this.openTicks == 0) {
			this.golem.setOpenedContainerPos(null);
			return;
		}

		if (this.golem.level() instanceof ServerLevel level) {
			Container target = HopperBlockEntity.getContainerAt(level, opened);

			if (target != null) {
				target.stopOpen(this.golem);
			}
		}

		this.golem.setOpenedContainerPos(null);
	}

	private void lookAtContainer() {
		if (this.container != null) {
			this.golem.getLookControl().setLookAt(
					this.container.getX() + 0.5, this.container.getY() + 0.5, this.container.getZ() + 0.5);
		}
	}

	private @Nullable BlockPos resolveContainer(ServerLevel level) {
		BlockPos remembered = this.golem.getDepositPos();

		if (remembered != null && HopperBlockEntity.getContainerAt(level, remembered) != null) {
			return remembered;
		}

		BlockPos center = this.golem.getFieldCenter() != null
				? this.golem.getFieldCenter()
				: this.golem.blockPosition();

		int radius = ArgilusConfig.get().radius();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy = -SEARCH_VERTICAL_REACH; dy <= SEARCH_VERTICAL_REACH; dy++) {
					cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);

					if (!level.isLoaded(cursor)
							|| !level.getBlockState(cursor).is(ArgilusTags.DEPOSIT_CONTAINERS)) {
						continue;
					}

					if (HopperBlockEntity.getContainerAt(level, cursor) == null) {
						continue;
					}

					double distance = cursor.distSqr(center);

					if (distance < bestDistance) {
						bestDistance = distance;
						best = cursor.immutable();
					}
				}
			}
		}

		this.golem.setDepositPos(best);

		if (best == null) {
			this.nextAttemptTime = level.getGameTime() + RETRY_COOLDOWN;
		}

		return best;
	}
}
