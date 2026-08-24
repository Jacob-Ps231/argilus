package re.jerome.argilus;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ArgilusTags {
	// Chests and barrels by default. A datapack can widen it without a code
	// change, which a hardcoded block list would not allow.
	public static final TagKey<Block> DEPOSIT_CONTAINERS =
			TagKey.create(Registries.BLOCK, Argilus.id("deposit_containers"));

	private ArgilusTags() {
	}
}
