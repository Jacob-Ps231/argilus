package re.jerome.argilus.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import re.jerome.argilus.ArgilusSummon;

// Vanilla keeps its golem patterns in private fields with no registry to add to,
// and Fabric API offers no event for this, so injection is the only route.
//
// trySpawnGolem is the right place rather than a block-placed callback: placing
// a head block, shearing a pumpkin already sitting on the clay, and a dispenser
// placing one all funnel through onPlace into here. Hooking an interaction event
// instead would catch placing and silently miss shearing.
@Mixin(CarvedPumpkinBlock.class)
public class CarvedPumpkinBlockMixin {
	@Inject(method = "trySpawnGolem", at = @At("HEAD"), cancellable = true)
	private void argilus$summonClayGolem(Level level, BlockPos pos, CallbackInfo ci) {
		if (ArgilusSummon.trySummon(level, pos)) {
			// Our pattern consumed the blocks, so vanilla must not go looking for
			// its own golems in what is now air.
			ci.cancel();
		}
	}

	// Without this a dispenser refuses to place the head on our pattern, even
	// though placing it by hand works.
	@Inject(method = "canSpawnGolem", at = @At("HEAD"), cancellable = true)
	private void argilus$allowClayGolem(
			LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (ArgilusSummon.canSummon(level, pos)) {
			cir.setReturnValue(true);
		}
	}
}
