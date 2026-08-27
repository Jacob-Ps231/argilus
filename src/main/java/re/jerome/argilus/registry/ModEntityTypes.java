package re.jerome.argilus.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import re.jerome.argilus.Argilus;
import re.jerome.argilus.entity.ArgilusEntity;

public final class ModEntityTypes {
	public static final EntityType<ArgilusEntity> ARGILUS = create(
			"argilus",
			EntityType.Builder.of(ArgilusEntity::new, MobCategory.MISC).sized(0.49F, 0.98F).eyeHeight(0.8F));

	private ModEntityTypes() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(ARGILUS, ArgilusEntity.createAttributes());
	}

	private static <T extends Entity> EntityType<T> create(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Argilus.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}
}
