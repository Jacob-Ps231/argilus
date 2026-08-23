package re.jerome.argilus.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import re.jerome.argilus.Argilus;

// Minecraft 26.2 stores item ids separately from the items themselves.
public final class ModItemIds {
	public static final ResourceKey<Item> ARGILUS_SPAWN_EGG = create("argilus_spawn_egg");

	private ModItemIds() {
	}

	private static ResourceKey<Item> create(String name) {
		return ResourceKey.create(Registries.ITEM, Argilus.id(name));
	}
}
