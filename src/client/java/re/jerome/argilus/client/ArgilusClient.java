package re.jerome.argilus.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import re.jerome.argilus.registry.ModEntityTypes;

public class ArgilusClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntityTypes.ARGILUS, ArgilusRenderer::new);
	}
}
