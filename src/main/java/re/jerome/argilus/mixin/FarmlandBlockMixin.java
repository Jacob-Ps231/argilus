package re.jerome.argilus.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.jerome.argilus.entity.ArgilusEntity;

// Farmland only turns to dirt when something falls on it, and the golem falls
// every time it steps down a block. Without this it would undo its own farm.
// Entity has no canTrample hook in 26.2, the condition is inlined in the block.
//
// Cancelling at HEAD also skips the fall damage the parent Block.fallOn would
// deal. That only matters for a drop of four blocks or more onto farmland,
// which is not worth a more intricate injection.
@Mixin(FarmlandBlock.class)
public class FarmlandBlockMixin {
	@Inject(method = "fallOn", at = @At("HEAD"), cancellable = true)
	private void argilus$keepFarmlandUnderGolem(
			Level level,
			BlockState state,
			BlockPos pos,
			Entity entity,
			double fallDistance,
			CallbackInfo ci) {
		if (entity instanceof ArgilusEntity) {
			ci.cancel();
		}
	}
}
