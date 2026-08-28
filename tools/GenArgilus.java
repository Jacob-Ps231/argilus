// Source of every image in the mod, kept in the repository because a generated
// file whose generator is lost stops being editable.
//
// Run it from the project root with the single file launcher, no build needed:
//   java tools/GenArgilus.java .
//
// It writes one entity texture per ArgilusVariant, the spawn egg, and the icon
// in both sizes. Add a Finish below and a constant to that enum together, or
// the pair falls out of step and a variant renders with a missing texture.
//
// The regions are dictated by the texOffs values in ArgilusModel: change one and
// the other has to follow. Unwrapping a box lays its four side faces in a single
// horizontal strip, which is how the hat band is drawn without any geometry.
//
// The hat is deliberately identical across variants. Only the clay changes, so
// the golems read as one creature in different states rather than six creatures.

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

public class GenArgilus {
	// Boxes as laid out by ArgilusModel.
	static final Rectangle HEAD = new Rectangle(0, 0, 24, 12);
	static final Rectangle BODY = new Rectangle(0, 16, 20, 12);
	static final Rectangle RIGHT_ARM = new Rectangle(24, 16, 8, 10);
	static final Rectangle LEFT_ARM = new Rectangle(24, 32, 8, 10);
	static final Rectangle RIGHT_LEG = new Rectangle(40, 16, 8, 6);
	static final Rectangle LEFT_LEG = new Rectangle(40, 32, 8, 6);
	static final Rectangle HAT_CROWN = new Rectangle(0, 32, 24, 8);
	static final Rectangle HAT_BRIM = new Rectangle(0, 44, 40, 11);

	// Lower half of each leg, for the ones that walk in mud.
	static final Rectangle RIGHT_BOOT = new Rectangle(40, 20, 8, 2);
	static final Rectangle LEFT_BOOT = new Rectangle(40, 36, 8, 2);

	static final int STRAW = 0xC7A44E;
	static final int STRAW_DARK = 0xA8873C;
	static final int STRAW_LIGHT = 0xDCBC6A;

	static final int BAND = 0x8B3A2E;
	static final int BAND_DARK = 0x743026;
	static final int BAND_LIGHT = 0xA04739;

	static final int EYE = 0x3A3F4C;

	record Finish(String name, int base, int dark, int light, int accent, String treatment) {
	}

	static final Finish[] FINISHES = {
		new Finish("smooth", 0xA4A8B8, 0x8B90A2, 0xB8BCCA, 0x000000, "none"),
		new Finish("cracked", 0xA9663F, 0x8A5133, 0xC07C50, 0x5E3722, "veins"),
		new Finish("pale", 0xD8D2C4, 0xBEB8A9, 0xE8E3D7, 0xB09C82, "patches"),
		new Finish("wet", 0x8A909E, 0x737988, 0x9AA0AE, 0x5A4A3A, "mud"),
		new Finish("earthy", 0xC2A98C, 0xA68F74, 0xD6C0A5, 0x7A6247, "smudges"),
		new Finish("mossy", 0xA4A8B8, 0x8B90A2, 0xB8BCCA, 0x6E8C3A, "moss"),
	};

	// Spawn eggs stopped being a tinted generic template: since 1.21 each one is
	// its own drawing, so ours is a hand placed 16x16 rather than two colours.
	// Legend: K outline, L C D clay light to dark, S T U straw light to dark,
	// R band, dot transparent.
	static final String[] SPAWN_EGG = {
		"................",
		".....KKKKKK.....",
		"....KSSTTUK.....",
		"....KSTTUUK.....",
		"...KKRRRRRKK....",
		"..KTTTTTTTTTK...",
		".KTSTUTTSTUTTK..",
		".KKKKKKKKKKKKK..",
		"....KLCCCCK.....",
		"...KLCKCCKCK....",
		"...KLCCCCCCK....",
		"...KLCCCCCCK....",
		"...KLCCCCCDK....",
		"....KLCCCDK.....",
		".....KKKKKK.....",
		"................",
	};

	// The golem head rather than the egg, since an icon reads better as a face.
	static final String[] ICON = {
		"................",
		"................",
		".....KKKKKK.....",
		"....KSSTTUK.....",
		"...KKRRRRRKK....",
		"..KTTTTTTTTTK...",
		".KTSTUTTSTUTTK..",
		".KKKKKKKKKKKKK..",
		"....KLCCCCCK....",
		"....KCKCCKCK....",
		"....KCCCCCCK....",
		"....KCCCCCDK....",
		"....KCCCCCDK....",
		"....KKKKKKKK....",
		"................",
		"................",
	};

	public static void main(String[] args) throws Exception {
		File root = new File(args[0]);
		File textures = new File(root, "src/main/resources/assets/argilus/textures");
		File entity = new File(textures, "entity");
		File item = new File(textures, "item");

		// 128 for the in game mod list, 512 for a store page, both scaled with
		// nearest neighbour so the pixels stay hard instead of turning to mush.
		ImageIO.write(scale(paintMap(ICON), 8), "PNG",
				new File(root, "src/main/resources/assets/argilus/icon.png"));
		ImageIO.write(scale(paintMap(ICON), 32), "PNG", new File(root, "icon.png"));
		System.out.println("ecrit : icon.png (128 et 512)");

		for (Finish finish : FINISHES) {
			BufferedImage image = paint(finish);
			File out = new File(entity, "argilus_" + finish.name() + ".png");
			ImageIO.write(image, "PNG", out);
			System.out.println("ecrit : " + out.getName() + " (" + out.length() + " o)");
		}

		File egg = new File(item, "argilus_spawn_egg.png");
		ImageIO.write(paintSpawnEgg(), "PNG", egg);
		System.out.println("ecrit : " + egg.getName() + " (" + egg.length() + " o)");
	}

	static BufferedImage scale(BufferedImage source, int factor) {
		int size = source.getWidth() * factor;
		BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				scaled.setRGB(x, y, source.getRGB(x / factor, y / factor));
			}
		}

		return scaled;
	}

	static BufferedImage paintSpawnEgg() {
		return paintMap(SPAWN_EGG);
	}

	static BufferedImage paintMap(String[] map) {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < 16; y++) {
			String row = map[y];

			for (int x = 0; x < 16; x++) {
				int rgb = switch (row.charAt(x)) {
					case 'K' -> 0x1D1D21;
					case 'L' -> 0xB8BCCA;
					case 'C' -> 0xA4A8B8;
					case 'D' -> 0x8B90A2;
					case 'S' -> 0xDCBC6A;
					case 'T' -> 0xC7A44E;
					case 'U' -> 0xA8873C;
					case 'R' -> 0x8B3A2E;
					default -> -1;
				};

				if (rgb >= 0) {
					set(image, x, y, rgb);
				}
			}
		}

		return image;
	}

	static BufferedImage paint(Finish finish) {
		BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);

		// A fixed seed per finish keeps the output reproducible byte for byte.
		Random random = new Random(finish.name().hashCode());

		fill(image, random, new Rectangle(0, 0, 64, 64), finish.base(), finish.dark(), finish.light());

		switch (finish.treatment()) {
			case "veins" -> {
				veins(image, random, HEAD, finish.accent(), 6);
				veins(image, random, BODY, finish.accent(), 10);
				veins(image, random, RIGHT_ARM, finish.accent(), 3);
				veins(image, random, LEFT_ARM, finish.accent(), 3);
			}
			case "patches" -> {
				blobs(image, random, BODY, finish.accent(), 4, 3);
				blobs(image, random, HEAD, finish.accent(), 2, 2);
			}
			case "mud" -> {
				fill(image, random, RIGHT_BOOT, finish.accent(), finish.accent(), finish.base());
				fill(image, random, LEFT_BOOT, finish.accent(), finish.accent(), finish.base());
				blobs(image, random, BODY, finish.accent(), 3, 2);
			}
			case "smudges" -> {
				blobs(image, random, BODY, finish.accent(), 6, 3);
				blobs(image, random, RIGHT_LEG, finish.accent(), 2, 2);
				blobs(image, random, LEFT_LEG, finish.accent(), 2, 2);
			}
			case "moss" -> {
				veins(image, random, BODY, finish.accent(), 12);
				veins(image, random, RIGHT_ARM, finish.accent(), 4);
				veins(image, random, LEFT_ARM, finish.accent(), 4);
				veins(image, random, RIGHT_LEG, finish.accent(), 3);
				veins(image, random, LEFT_LEG, finish.accent(), 3);
				veins(image, random, HEAD, finish.accent(), 4);
			}
			default -> {
			}
		}

		fill(image, random, HAT_CROWN, STRAW, STRAW_DARK, STRAW_LIGHT);
		fill(image, random, HAT_BRIM, STRAW, STRAW_DARK, STRAW_LIGHT);

		// The four side faces of the crown land in one strip, so colouring its
		// lower row bands the hat all the way round.
		fill(image, random, new Rectangle(0, 39, 24, 1), BAND, BAND_DARK, BAND_LIGHT);

		// Front face of the head: u 6..12, v 6..11 for a 6x5x6 box at texOffs(0, 0).
		for (int y = 8; y <= 9; y++) {
			set(image, 7, y, EYE);
			set(image, 10, y, EYE);
		}

		return image;
	}

	static void fill(BufferedImage image, Random random, Rectangle area, int base, int dark, int light) {
		for (int y = area.y; y < area.y + area.height; y++) {
			for (int x = area.x; x < area.x + area.width; x++) {
				int roll = random.nextInt(10);
				set(image, x, y, roll == 0 ? dark : roll == 1 ? light : base);
			}
		}
	}

	// Short horizontal or vertical runs, which read as cracks or as moss caught
	// in them depending on the colour.
	static void veins(BufferedImage image, Random random, Rectangle area, int colour, int count) {
		for (int i = 0; i < count; i++) {
			int x = area.x + random.nextInt(area.width);
			int y = area.y + random.nextInt(area.height);
			int length = 2 + random.nextInt(3);
			boolean horizontal = random.nextBoolean();

			for (int step = 0; step < length; step++) {
				int px = horizontal ? x + step : x;
				int py = horizontal ? y : y + step;

				if (area.contains(px, py)) {
					set(image, px, py, colour);
				}
			}
		}
	}

	static void blobs(BufferedImage image, Random random, Rectangle area, int colour, int count, int size) {
		for (int i = 0; i < count; i++) {
			int width = 1 + random.nextInt(size);
			int height = 1 + random.nextInt(size);
			int x = area.x + random.nextInt(Math.max(1, area.width - width));
			int y = area.y + random.nextInt(Math.max(1, area.height - height));

			for (int py = y; py < y + height; py++) {
				for (int px = x; px < x + width; px++) {
					if (area.contains(px, py)) {
						set(image, px, py, colour);
					}
				}
			}
		}
	}

	static void set(BufferedImage image, int x, int y, int rgb) {
		image.setRGB(x, y, 0xFF000000 | rgb);
	}
}
