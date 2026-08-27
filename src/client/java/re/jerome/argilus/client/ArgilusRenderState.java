package re.jerome.argilus.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import re.jerome.argilus.entity.ArgilusVariant;

// getTextureLocation only receives the render state, never the entity, so this
// is what carries the finish across.
public class ArgilusRenderState extends LivingEntityRenderState {
	public ArgilusVariant variant = ArgilusVariant.SMOOTH;
}
