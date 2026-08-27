package re.jerome.argilus.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import re.jerome.argilus.Argilus;
import re.jerome.argilus.registry.ModEntityTypes;

public class ArgilusClient implements ClientModInitializer {
	public static final ModelLayerLocation ARGILUS_LAYER =
			new ModelLayerLocation(Argilus.id("argilus"), "main");

	@Override
	public void onInitializeClient() {
		ModelLayerRegistry.registerModelLayer(ARGILUS_LAYER, ArgilusModel::createBodyLayer);
		EntityRendererRegistry.register(ModEntityTypes.ARGILUS, ArgilusRenderer::new);
	}
}
