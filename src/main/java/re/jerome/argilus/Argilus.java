package re.jerome.argilus;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import re.jerome.argilus.registry.ModEntityTypes;
import re.jerome.argilus.registry.ModItems;

public class Argilus implements ModInitializer {
	public static final String MOD_ID = "argilus";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ArgilusConfig.load();
		ModEntityTypes.register();
		ModItems.register();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
