package re.jerome.argilus.entity;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import re.jerome.argilus.ArgilusConfig;

// Walks to dropped items instead of only collecting what it happens to tread on.
//
// There is no pickup code here on purpose: Mob.aiStep already takes anything
// within reach once canPickUpLoot is true, so this goal is pure navigation and
// the collecting stays vanilla.
//
// No block cache either, unlike the other goals. Querying entities in a box is
// indexed and cheap, which is why vanilla itself does it every other tick.
public class CollectItemsGoal extends Goal {
	private static final double SPEED = 0.9;
	private static final int SCAN_INTERVAL = 20;
	private static final int UNREACHABLE_COOLDOWN = 100;
	private static final int VERTICAL_REACH = 4;

	private final ArgilusEntity golem;
	private ItemEntity target;
	private long nextScanTime;

	public CollectItemsGoal(ArgilusEntity golem) {
		this.golem = golem;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!(this.golem.level() instanceof ServerLevel level)) {
			return false;
		}

		long now = level.getGameTime();

		if (now < this.nextScanTime) {
			return false;
		}

		this.nextScanTime = now + SCAN_INTERVAL;
		this.target = this.findNearest(level);
		return this.target != null;
	}

	@Override
	public boolean canContinueToUse() {
		return this.golem.level() instanceof ServerLevel level && this.isWanted(level, this.target);
	}

	@Override
	public void stop() {
		this.target = null;
		this.golem.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (!(this.golem.level() instanceof ServerLevel level) || this.target == null) {
			return;
		}

		this.golem.resetIdleTicks();
		this.golem.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

		if (!this.golem.getNavigation().isDone()) {
			return;
		}

		// Re-path as the stack drifts, and give up on what cannot be reached
		// rather than stand there recomputing the same failed route.
		if (!this.golem.getNavigation().moveTo(this.target, SPEED)) {
			this.target = null;
			this.nextScanTime = level.getGameTime() + UNREACHABLE_COOLDOWN;
		}
	}

	private ItemEntity findNearest(ServerLevel level) {
		int radius = ArgilusConfig.get().collectRadius();
		AABB box = this.golem.getBoundingBox().inflate(radius, VERTICAL_REACH, radius);

		List<ItemEntity> items =
				level.getEntitiesOfClass(ItemEntity.class, box, item -> this.isWanted(level, item));

		ItemEntity nearest = null;
		double best = Double.MAX_VALUE;

		for (ItemEntity item : items) {
			double distance = item.distanceToSqr(this.golem);

			if (distance < best) {
				best = distance;
				nearest = item;
			}
		}

		return nearest;
	}

	// wantsToPickUp already answers no when the inventory is full, so the golem
	// ignores the overflow it just dropped and comes back for it after a deposit.
	private boolean isWanted(ServerLevel level, ItemEntity item) {
		return item != null
				&& item.isAlive()
				&& !item.isRemoved()
				&& !item.hasPickUpDelay()
				&& !item.getItem().isEmpty()
				&& this.golem.wantsToPickUp(level, item.getItem());
	}
}
