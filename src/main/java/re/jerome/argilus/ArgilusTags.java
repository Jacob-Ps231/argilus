package re.jerome.argilus;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ArgilusTags {
	// Chests and barrels by default. A datapack can widen it without a code
	// change, which a hardcoded block list would not allow.
	public static final TagKey<Block> DEPOSIT_CONTAINERS =
			TagKey.create(Registries.BLOCK, Argilus.id("deposit_containers"));

	// Crops that grow by stacking copies of themselves on a foot that regrows
	// them, sugar cane by default. A modded cane growing the same way only has
	// to join the tag; nothing in the code names a block.
	public static final TagKey<Block> STACKED_CROPS =
			TagKey.create(Registries.BLOCK, Argilus.id("stacked_crops"));

	private ArgilusTags() {
	}
}
