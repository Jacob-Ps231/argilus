package re.jerome.argilus.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import re.jerome.argilus.entity.ArgilusEntity;

// The model stays typed on LivingEntityRenderState, which satisfies the
// EntityModel<? super S> bound, so adding a state of our own costs it nothing.
public class ArgilusRenderer extends MobRenderer<ArgilusEntity, ArgilusRenderState, ArgilusModel> {
	public ArgilusRenderer(EntityRendererProvider.Context context) {
		super(context, new ArgilusModel(context.bakeLayer(ArgilusClient.ARGILUS_LAYER)), 0.3F);
	}

	@Override
	public ArgilusRenderState createRenderState() {
		return new ArgilusRenderState();
	}

	@Override
	public void extractRenderState(ArgilusEntity golem, ArgilusRenderState state, float partialTick) {
		super.extractRenderState(golem, state, partialTick);
		state.variant = golem.getVariant();
	}

	@Override
	public Identifier getTextureLocation(ArgilusRenderState state) {
		return state.variant.texture();
	}
}
