package re.jerome.argilus.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import re.jerome.argilus.Argilus;
import re.jerome.argilus.entity.ArgilusEntity;

// The golem needs no render state of its own: LivingEntityRenderState already
// carries the walk animation and the head rotation, which is all the model reads.
public class ArgilusRenderer extends MobRenderer<ArgilusEntity, LivingEntityRenderState, ArgilusModel> {
	private static final Identifier TEXTURE = Argilus.id("textures/entity/argilus.png");

	public ArgilusRenderer(EntityRendererProvider.Context context) {
		super(context, new ArgilusModel(context.bakeLayer(ArgilusClient.ARGILUS_LAYER)), 0.3F);
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}

	@Override
	public Identifier getTextureLocation(LivingEntityRenderState state) {
		return TEXTURE;
	}
}
