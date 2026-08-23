package re.jerome.argilus.client;

import net.minecraft.client.model.animal.golem.IronGolemModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.resources.Identifier;
import re.jerome.argilus.Argilus;
import re.jerome.argilus.entity.ArgilusEntity;

// Placeholder skin: the vanilla iron golem mesh with a flat clay texture.
// Step 7 replaces it with a Blockbench model and a model layer of our own.
// IronGolemModel.setupAnim only reads primitives, so the render state's
// golem-specific object fields can safely stay unset.
public class ArgilusRenderer extends MobRenderer<ArgilusEntity, IronGolemRenderState, IronGolemModel> {
	private static final Identifier TEXTURE = Argilus.id("textures/entity/argilus.png");

	public ArgilusRenderer(EntityRendererProvider.Context context) {
		super(context, new IronGolemModel(context.bakeLayer(ModelLayers.IRON_GOLEM)), 0.7F);
	}

	@Override
	public IronGolemRenderState createRenderState() {
		return new IronGolemRenderState();
	}

	@Override
	public Identifier getTextureLocation(IronGolemRenderState state) {
		return TEXTURE;
	}
}
