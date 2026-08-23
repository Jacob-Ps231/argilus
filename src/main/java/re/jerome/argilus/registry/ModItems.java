package re.jerome.argilus.registry;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

public final class ModItems {
	// CreativeModeTabs keeps its tab keys private, so the vanilla one is rebuilt here.
	private static final ResourceKey<CreativeModeTab> SPAWN_EGGS = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("spawn_eggs"));

	public static final Item ARGILUS_SPAWN_EGG = Registry.register(
			BuiltInRegistries.ITEM,
			ModItemIds.ARGILUS_SPAWN_EGG,
			new SpawnEggItem(new Item.Properties()
					.spawnEgg(ModEntityTypes.ARGILUS)
					.setId(ModItemIds.ARGILUS_SPAWN_EGG)));

	private ModItems() {
	}

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(SPAWN_EGGS)
				.register(output -> output.accept(ARGILUS_SPAWN_EGG));
	}
}
