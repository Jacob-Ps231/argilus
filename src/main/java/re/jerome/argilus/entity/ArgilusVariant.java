package re.jerome.argilus.entity;

import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import re.jerome.argilus.Argilus;

// Six clay finishes for one model. An int plus an array in the renderer would
// work, but naming them makes an out of range index impossible and the save
// data readable.
public enum ArgilusVariant {
	SMOOTH("smooth"),
	CRACKED("cracked"),
	PALE("pale"),
	WET("wet"),
	EARTHY("earthy"),
	MOSSY("mossy");

	private static final ArgilusVariant[] VALUES = values();

	private final String name;
	private final Identifier texture;

	ArgilusVariant(String name) {
		this.name = name;
		this.texture = Argilus.id("textures/entity/argilus_" + name + ".png");
	}

	public static ArgilusVariant random(RandomSource random) {
		return VALUES[random.nextInt(VALUES.length)];
	}

	public static ArgilusVariant byId(int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : SMOOTH;
	}

	// Save data holds the name, never the ordinal: reordering this enum would
	// otherwise repaint every golem already in the world. An unknown name falls
	// back rather than failing the load.
	public static ArgilusVariant byName(String name) {
		for (ArgilusVariant variant : VALUES) {
			if (variant.name.equals(name)) {
				return variant;
			}
		}

		return SMOOTH;
	}

	public String getName() {
		return this.name;
	}

	public Identifier texture() {
		return this.texture;
	}
}
