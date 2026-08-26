package re.jerome.argilus;

import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import org.jspecify.annotations.Nullable;
import re.jerome.argilus.entity.ArgilusEntity;
import re.jerome.argilus.registry.ModEntityTypes;

// A carved pumpkin or a jack o'lantern on a block of clay, deliberately as
// permissive as the vanilla golems: no farmland nearby is required, and an
// accidental summon is accepted the same way it is for the snow golem.
//
// Both head blocks are CarvedPumpkinBlock instances, and shearing a pumpkin in
// place goes through setBlock without UPDATE_SKIP_ON_PLACE, so placing, shearing
// and dispensing all reach CarvedPumpkinBlock.onPlace. One injection point
// covers every route in.
public final class ArgilusSummon {
	private static @Nullable BlockPattern base;
	private static @Nullable BlockPattern full;

	private ArgilusSummon() {
	}

	public static boolean canSummon(LevelReader level, BlockPos pos) {
		return basePattern().find(level, pos) != null;
	}

	public static boolean trySummon(Level level, BlockPos pos) {
		BlockPattern.BlockPatternMatch match = fullPattern().find(level, pos);

		if (match == null) {
			return false;
		}

		ArgilusEntity golem = ModEntityTypes.ARGILUS.create(level, EntitySpawnReason.TRIGGERED);

		if (golem == null) {
			return false;
		}

		// Stand where the clay was rather than where the head was: unlike the
		// copper golem this one is tall, and the head slot is the top of it.
		BlockPos feet = match.getBlock(0, 1, 0).getPos();

		CarvedPumpkinBlock.clearPatternBlocks(level, match);
		golem.snapTo(feet.getX() + 0.5, feet.getY() + 0.05, feet.getZ() + 0.5, 0.0F, 0.0F);
		level.addFreshEntity(golem);

		for (ServerPlayer player :
				level.getEntitiesOfClass(ServerPlayer.class, golem.getBoundingBox().inflate(5.0))) {
			CriteriaTriggers.SUMMONED_ENTITY.trigger(player, golem);
		}

		CarvedPumpkinBlock.updatePatternBlocks(level, match);
		return true;
	}

	private static BlockPattern basePattern() {
		if (base == null) {
			base = BlockPatternBuilder.start()
					.aisle(" ", "#")
					.where('#', BlockInWorld.hasState(ArgilusSummon::isBody))
					.build();
		}

		return base;
	}

	private static BlockPattern fullPattern() {
		if (full == null) {
			full = BlockPatternBuilder.start()
					.aisle("^", "#")
					.where('^', BlockInWorld.hasState(ArgilusSummon::isHead))
					.where('#', BlockInWorld.hasState(ArgilusSummon::isBody))
					.build();
		}

		return full;
	}

	private static boolean isBody(BlockState state) {
		return state.getBlock() == Blocks.CLAY;
	}

	// The same pair vanilla accepts for all three of its golems.
	private static boolean isHead(BlockState state) {
		return state.getBlock() == Blocks.CARVED_PUMPKIN || state.getBlock() == Blocks.JACK_O_LANTERN;
	}
}
