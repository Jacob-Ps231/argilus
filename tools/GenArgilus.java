// Source of the six entity textures, kept in the repository because a generated
// file whose generator is lost stops being editable.
//
// Run it from the project root with the single file launcher, no build needed:
//   java tools/GenArgilus.java .
//
// One texture per ArgilusVariant. Add a Finish below and a constant to that enum
// together, or the pair falls out of step and a variant renders with a missing
// texture.
//
// It deliberately writes nothing else. The mod icon and the spawn egg are drawn
// artwork now, and generating them here would silently overwrite that artwork on
// the next run: the mirror image of the problem this file exists to prevent.
//
// The geometry comes from tools/argilus.bbmodel, and the regions below are the
// texOffs it exports into ArgilusModel. Nothing follows anything: a box that
// moves has to be moved in all three places by hand, the model, ArgilusModel
// and here. Unwrapping a box lays its four side faces in a single horizontal
// strip, which is how the hat band is drawn without any geometry.
//
// The hat is deliberately identical across variants. Only the clay changes, so
// the golems read as one creature in different states rather than six creatures.

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

public class GenArgilus {
	// The model declares a 64x64 UV space, and ModelPart.Polygon divides every
	// coordinate by it when the layer is baked, so what reaches the GPU is
	// already in 0..1. The PNG is therefore free to be bigger than that space:
	// at SCALE 4 each model unit gets sixteen texels and ArgilusModel needs no
	// change at all. Only edges spend that budget - the eyes, the hat band, the
	// flowers in the moss, cracks, the line of mud. Clay and moss keep the
	// vanilla grain of one roll per unit, because subdividing them reads as
	// static rather than as stone.
	static final int SCALE = 4;
	static final int SIZE = 64 * SCALE;

	// Regions stay written in model units, the unit ArgilusModel and Blockbench
	// both speak, and are scaled on the way in.
	static Rectangle box(int x, int y, int width, int height) {
		return new Rectangle(x * SCALE, y * SCALE, width * SCALE, height * SCALE);
	}

	// Boxes as laid out by ArgilusModel. The head sits under the rest of them
	// because a deeper head unwraps wider than the row it used to share with the
	// crown: 32 by 12 at its old offset would have run into the crown and the
	// top of the body.
	static final Rectangle HEAD = box(0, 42, 32, 12);
	static final Rectangle BODY = box(0, 26, 28, 14);
	static final Rectangle RIGHT_ARM = box(28, 26, 8, 10);
	static final Rectangle LEFT_ARM = box(36, 26, 8, 10);
	static final Rectangle RIGHT_LEG = box(44, 26, 14, 8);
	static final Rectangle LEFT_LEG = box(44, 34, 14, 8);
	static final Rectangle HAT_CROWN = box(30, 15, 30, 9);
	static final Rectangle HAT_BRIM = box(0, 0, 56, 15);

	// Lower rows of each leg strip, for the ones that walk in mud.
	static final Rectangle RIGHT_BOOT = box(44, 32, 14, 2);
	static final Rectangle LEFT_BOOT = box(44, 40, 14, 2);

	// Sampled off the concept sheet, then lifted about a tenth: the sheet is a
	// lit render, so its pixels sit below the flat colours a texture holds.
	static final int STRAW = 0xB08C45;
	static final int STRAW_DARK = 0x97783C;
	static final int STRAW_LIGHT = 0xC3A052;

	static final int BAND = 0x821B2C;
	static final int BAND_DARK = 0x701926;
	static final int BAND_LIGHT = 0x931E31;

	static final int EYE = 0x0C0C0E;

	// The four side faces of a box land in one strip, under its top and bottom
	// faces. Only that strip is seen from the ground, and its rows run from the
	// top of the box down to its bottom, which is what lets moss know how high
	// up it sits.
	record Strip(Rectangle area, int topY, int bottomY) {
	}

	// Named because the cracks are drawn on them too, and one definition of each
	// is one thing to move the day a box changes size.
	static final Rectangle BODY_SIDES = box(0, 32, 28, 8);
	static final Rectangle RIGHT_ARM_SIDES = box(28, 28, 8, 8);
	static final Rectangle LEFT_ARM_SIDES = box(36, 28, 8, 8);

	static final Strip[] SIDES = {
		new Strip(BODY_SIDES, 12, 4),
		new Strip(RIGHT_ARM_SIDES, 11, 3),
		new Strip(LEFT_ARM_SIDES, 11, 3),
		new Strip(box(44, 30, 14, 4), 4, 0),
		new Strip(box(44, 38, 14, 4), 4, 0),
	};

	// The nominal size of one moss patch, which is what the seeding rate is
	// divided by to keep the total coverage where it was. Patches overlap and
	// get clipped at the bottom of a strip, so the real coverage lands a little
	// under this and the constant errs small on purpose.
	static final int PATCH_UNITS = 8;

	// The yellow centre two of the five kinds carry. The other three keep a core
	// of their own colour, so that not every bloom reads the same way.
	static final int POLLEN = 0xE0C645;

	// Petals and core of each kind of flower. They are drawn inside a single
	// model unit, which at SCALE 4 is four texels across: enough for a round
	// bloom up close, still one coloured dot at the distance you fight at.
	static final int[][] FLOWERS = {
		{ 0xE8E8E0, POLLEN },
		{ 0xE8D24A, 0xB8942F },
		{ 0xB33A2E, POLLEN },
		{ 0x5D6FC4, 0x3E4C8F },
		{ 0xA86FC4, 0x7A4A95 },
	};

	// Index of the blue kind, which is kept off the moss.
	static final int BLUE = 3;

	// One unit of moss edge in this many blooms, and one bare unit in this many.
	static final int FLOWER_ODDS = 4;
	static final int BARE_ODDS = 40;

	// Surfaces that carry flowers but no moss, in the unwrapped layout: the four
	// side faces of the head, the top and the upper side of the crown, and the
	// top of the brim. The undersides are left out; nothing looks at them.
	static final Rectangle HEAD_SIDES = box(0, 50, 32, 4);
	static final Rectangle CROWN_TOP = box(37, 15, 8, 7);
	static final Rectangle CROWN_SIDE = box(30, 22, 30, 1);

	// The lower row of the same strip, painted as the band.
	static final Rectangle CROWN_BAND = box(30, 23, 30, 1);
	static final Rectangle BRIM_TOP = box(14, 0, 14, 14);

	// The front face of the head, where the eyes, the cracks and one flower go.
	static final Rectangle FACE = box(8, 50, 8, 4);

	// The cracks on the face are written down rather than rolled. They were
	// settled by eye over several passes - one dropped for crossing the strip
	// between the eyes, where a crack stops reading as damage and starts reading
	// as a nose, and two moved clear of the flower - and a roll cannot be asked
	// to reproduce a choice. Writing them down also puts the same face on every
	// finish, which is what the hat already does and for the same reason: the
	// golems should read as one creature in six states.
	//
	// Texels, relative to the corner of FACE, so that they follow it if the head
	// is ever unwrapped somewhere else again.
	static final int[][] FACE_CRACKS = {
		{ 8, 8, 8, 9, 7, 9, 6, 9, 5, 9, 4, 9, 3, 9 },
		{ 24, 11, 25, 11, 26, 11, 27, 11, 27, 10, 27, 9, 28, 9 },
		{ 26, 11, 26, 12, 26, 13, 26, 14, 26, 15, 27, 14 },
	};

	// One bloom is placed rather than rolled: on the face, in the unit under the
	// eye at u 13, where the roll had left a red one that read as a wound. Its
	// kind is the yellow row of FLOWERS.
	static final Rectangle FACE_BLOOM = box(13, 52, 1, 1);
	static final int FACE_BLOOM_KIND = 1;

	// Which kind was used last. Drawing a kind freely put four of one colour on
	// the front, and stepping on by one landed the same colour on the same face
	// of every box; both happened. Stepping on by a random amount does neither,
	// and never repeats the kind before it.
	static int bloom;

	record Finish(String name, int base, int dark, int light, int accent, String treatment) {
	}

	static final Finish[] FINISHES = {
		new Finish("smooth", 0x84848F, 0x7D7D88, 0x8B8B96, 0x000000, "none"),
		new Finish("cracked", 0xA9663F, 0x8A5133, 0xC07C50, 0x5E3722, "veins"),
		new Finish("pale", 0xD8D2C4, 0xBEB8A9, 0xE8E3D7, 0xB09C82, "patches"),
		new Finish("wet", 0x8A909E, 0x737988, 0x9AA0AE, 0x5A4A3A, "mud"),
		new Finish("earthy", 0xC2A98C, 0xA68F74, 0xD6C0A5, 0x7A6247, "smudges"),
		new Finish("mossy", 0x84848F, 0x7D7D88, 0x8B8B96, 0x5D6530, "moss"),
	};

	public static void main(String[] args) throws Exception {
		File root = new File(args[0]);
		File textures = new File(root, "src/main/resources/assets/argilus/textures");
		File entity = new File(textures, "entity");

		for (Finish finish : FINISHES) {
			BufferedImage image = paint(finish);
			File out = new File(entity, "argilus_" + finish.name() + ".png");
			ImageIO.write(image, "PNG", out);
			System.out.println("wrote " + out.getName() + " (" + out.length() + " bytes)");
		}
	}

	static BufferedImage paint(Finish finish) {
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);

		// A fixed seed per finish keeps the output reproducible byte for byte.
		Random random = new Random(finish.name().hashCode());

		fill(image, random, box(0, 0, 64, 64), finish.base(), finish.dark(), finish.light());

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
				// Rock reads as facets rather than as grain, so before the moss
				// goes on, each region takes a few broad patches of its own dark
				// and light over the per-unit speckle the base fill laid down.
				for (Rectangle region : new Rectangle[] { HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }) {
					blobs(image, random, region, finish.dark(), 3, 3);
					blobs(image, random, region, finish.light(), 2, 3);
				}

				for (Strip side : SIDES) {
					moss(image, random, side, finish.accent());
				}
			}
			default -> {
			}
		}

		fill(image, random, HAT_CROWN, STRAW, STRAW_DARK, STRAW_LIGHT);
		fill(image, random, HAT_BRIM, STRAW, STRAW_DARK, STRAW_LIGHT);

		// The four side faces of the crown land in one strip, so colouring its
		// lower row bands the hat all the way round.
		fill(image, random, CROWN_BAND, BAND, BAND_DARK, BAND_LIGHT);

		// The clay has cracked around the eyes. One texel wide, because these are
		// drawn at the resolution of the sheet rather than of the model, which is
		// the difference between a crack and a gouge.
		//
		// Before the eyes, so that a crack never runs across one, and before the
		// flowers, so a bloom that lands on the face still sits on top.
		int crazing = shade(finish.dark(), 0.78F);

		for (int[] line : FACE_CRACKS) {
			for (int i = 0; i < line.length; i += 2) {
				int x = FACE.x + line[i];
				int y = FACE.y + line[i + 1];

				// An offset past the face would land on the side of the head without
				// a word; this keeps the table safe to edit by hand.
				if (FACE.contains(x, y)) {
					set(image, x, y, crazing);
				}
			}
		}

		// Flowers come after the hat, so the straw cannot paint over one, and
		// before the eyes, so a bloom can never land on one.
		if (finish.treatment().equals("moss")) {
			flowers(image, finish);
		}

		// Five more at most, down the body and the arms, and longer than the ones
		// on the face because they have the room: the clay should read as old, not
		// as broken.
		//
		// They go on after the flowers, and that order is load-bearing: the flower
		// pass finds the moss by reading the image back, and a crack drawn across a
		// unit of moss makes that unit read as bare stone. Cracking the body was
		// quietly moving every bloom on the golem. Drawn last instead, a crack can
		// cross a moss patch or a bloom, which is what a crack does.
		//
		// The three on the body get a third of the wrap each. Handed the whole
		// strip they all start in the middle half of it, and the middle half of a
		// wrap is one face: the golem came out cracked down one side and nowhere
		// else. A walk turns back at the edge of what it is given, so a third is
		// both the edge it comes in by and the ground it stays on.
		for (int i = 0; i < 3; i++) {
			int width = BODY_SIDES.width / 3;
			Rectangle third = new Rectangle(BODY_SIDES.x + i * width, BODY_SIDES.y, width, BODY_SIDES.height);

			split(image, random, third, crazing, 2 + random.nextInt(3));
		}

		split(image, random, RIGHT_ARM_SIDES, crazing, 2 + random.nextInt(3));
		split(image, random, LEFT_ARM_SIDES, crazing, 2 + random.nextInt(3));

		// Front face of the head: u 8..16, v 50..54 for an 8x4x8 box at texOffs(0, 42).
		// One unit square each, two units apart, two units of clay on each side:
		// the concept sheet measures 1.3 by 1.0, set 1.8 apart.
		fill(image, random, box(10, 51, 1, 1), EYE, EYE, EYE);
		fill(image, random, box(13, 51, 1, 1), EYE, EYE, EYE);

		return image;
	}

	// One roll per model unit, not per texel. The extra resolution is there for
	// edges - eyes, hat band, the line of mud - not for the clay, which keeps the
	// vanilla grain of one speck per unit. Four independent rolls inside a unit
	// read as static rather than as stone.
	static void fill(BufferedImage image, Random random, Rectangle area, int base, int dark, int light) {
		for (int y = area.y; y < area.y + area.height; y += SCALE) {
			for (int x = area.x; x < area.x + area.width; x += SCALE) {
				int roll = random.nextInt(10);
				int colour = roll == 0 ? dark : roll == 1 ? light : base;

				for (int py = y; py < Math.min(y + SCALE, area.y + area.height); py++) {
					for (int px = x; px < Math.min(x + SCALE, area.x + area.width); px++) {
						set(image, px, py, colour);
					}
				}
			}
		}
	}

	// Moss arrives in a few masses rather than as speckle, and it is spread
	// around the wrap at even intervals rather than rolled per unit: a roll
	// leaves whole faces bare, and the face it leaves bare is as often the front
	// as the back. The gradient is still the one measured on the concept sheet -
	// none on the head, a trace at the shoulders, heaviest at the hips and down
	// the legs - but it is read once per box now, as the share of the box that
	// should end up green, and it is set higher than the scattered version
	// carried: the same amount of moss in clumps reads as less of it.
	static void moss(BufferedImage image, Random random, Strip side, int colour) {
		Rectangle area = side.area();
		int columns = area.width / SCALE;
		int rows = area.height / SCALE;

		float height = (side.topY() + side.bottomY()) / 2.0F;
		float density = 0.14F + 0.45F * (1.0F - Math.min(height, 12.0F) / 12.0F);
		int patches = Math.max(1, Math.round(columns * rows * density / PATCH_UNITS));
		// spacing is a whole number, so the columns past patches * spacing are
		// never seeded: on an arm that is its back face, which gets moss only when
		// a neighbouring patch spreads onto it. Known and kept - evening it out
		// re-rolls every patch on the golem, and this moss was chosen by eye.
		int spacing = Math.max(1, columns / patches);

		for (int i = 0; i < patches; i++) {
			int column = i * spacing + random.nextInt(spacing);

			// Two draws, keep the lower: the sheet hangs moss near the ground.
			int row = Math.max(random.nextInt(rows), random.nextInt(rows));

			patch(image, random, area, columns, rows, column, row, colour);
		}
	}

	// A patch is a run of neighbouring columns hanging down from the seed by a
	// jittered number of units, taller than it is wide. Growing down rather than
	// in every direction is what gives it the ragged lower edge the sheet draws
	// instead of a band across the belly, and a run that reaches the end of a
	// face carries on around the corner of the box, the way moss crosses a
	// corner rather than stopping at it.
	static void patch(BufferedImage image, Random random, Rectangle area, int columns, int rows, int column, int row, int colour) {
		int width = 1 + random.nextInt(3);

		for (int i = 0; i < width && column + i < columns; i++) {
			int length = 3 + random.nextInt(3);

			for (int r = row; r < Math.min(row + length, rows); r++) {
				int roll = random.nextInt(3);

				unit(image, area, column + i, r, roll == 0 ? shade(colour, 0.80F) : roll == 1 ? shade(colour, 1.18F) : colour);
			}
		}
	}

	// Flowers go over every surface the player sees - after the hat and before the
	// eyes and the body cracks, for reasons given in paint(): mostly on
	// the top edge of the moss - a green unit with nothing green above it, the
	// edge one would grow out of - and sparsely on everything else, including the
	// head and the straw of the hat, which carry no moss at all. The two rates
	// are far apart because there is far more bare surface than there is edge.
	//
	// It is a pass of its own because blooming a patch as it is drawn lets the
	// next patch bury the flower, and at this density patches do overlap.
	static void flowers(BufferedImage image, Finish finish) {
		// A stream of its own, so that neither adding flowers nor changing how
		// many there are moves a single patch of the moss.
		Random blooms = new Random(finish.name().hashCode() + 1L);
		bloom = 0;

		for (Strip side : SIDES) {
			bloomRegion(image, blooms, side.area(), finish.accent());
		}

		bloomRegion(image, blooms, HEAD_SIDES, finish.accent());
		bloomRegion(image, blooms, CROWN_TOP, finish.accent());
		bloomRegion(image, blooms, CROWN_SIDE, finish.accent());
		bloomRegion(image, blooms, BRIM_TOP, finish.accent());

		// After the pass, so that it wins the unit whatever the roll left there.
		flower(image, HEAD, (FACE_BLOOM.x - HEAD.x) / SCALE, (FACE_BLOOM.y - HEAD.y) / SCALE, FLOWERS[FACE_BLOOM_KIND]);
	}

	static void bloomRegion(BufferedImage image, Random blooms, Rectangle area, int colour) {
		int columns = area.width / SCALE;
		int rows = area.height / SCALE;

		for (int column = 0; column < columns; column++) {
			for (int row = 0; row < rows; row++) {
				boolean moss = green(image, area, column, row, colour);
				boolean edge = moss && (row == 0 || !green(image, area, column, row - 1, colour));

				if (moss && !edge) {
					continue;
				}

				if (blooms.nextInt(edge ? FLOWER_ODDS : BARE_ODDS) != 0) {
					continue;
				}

				bloom = (bloom + 1 + blooms.nextInt(FLOWERS.length - 1)) % FLOWERS.length;

				// Blue is for the stone. On the moss it disappeared into the
				// green and only added to the count, so a blue roll on a moss
				// edge does not bloom at all - that is where the flowers the
				// golem had too many of were taken from.
				if (edge && bloom == BLUE) {
					continue;
				}

				flower(image, area, column, row, FLOWERS[bloom]);
			}
		}
	}

	// Read at the corner texel of the unit, the one flower leaves alone, so that
	// a unit which has already bloomed still answers for the moss beneath it and
	// the unit below it does not read as a second top edge.
	static boolean green(BufferedImage image, Rectangle area, int column, int row, int colour) {
		int texel = image.getRGB(area.x + column * SCALE, area.y + row * SCALE) & 0xFFFFFF;

		return texel == colour || texel == shade(colour, 0.80F) || texel == shade(colour, 1.18F);
	}

	static void unit(BufferedImage image, Rectangle area, int column, int row, int colour) {
		for (int y = 0; y < SCALE; y++) {
			for (int x = 0; x < SCALE; x++) {
				set(image, area.x + column * SCALE + x, area.y + row * SCALE + y, colour);
			}
		}
	}

	// Two tones either side of the accent, one unit in three, so a patch reads
	// as growth and not as a flat green rectangle. Deriving them from the accent
	// keeps one colour per finish in the table above.
	static int shade(int colour, float factor) {
		int red = Math.clamp(Math.round(((colour >> 16) & 0xFF) * factor), 0, 255);
		int green = Math.clamp(Math.round(((colour >> 8) & 0xFF) * factor), 0, 255);
		int blue = Math.clamp(Math.round((colour & 0xFF) * factor), 0, 255);

		return red << 16 | green << 8 | blue;
	}

	// Petals round a core, inside one model unit. The four corners are left as
	// they were, which is what rounds the bloom off against the moss under it.
	static void flower(BufferedImage image, Rectangle area, int column, int row, int[] colours) {
		int left = area.x + column * SCALE;
		int top = area.y + row * SCALE;

		for (int y = 0; y < SCALE; y++) {
			for (int x = 0; x < SCALE; x++) {
				boolean corner = (x == 0 || x == SCALE - 1) && (y == 0 || y == SCALE - 1);
				boolean core = x > 0 && x < SCALE - 1 && y > 0 && y < SCALE - 1;

				if (!corner) {
					set(image, left + x, top + y, core ? colours[1] : colours[0]);
				}
			}
		}
	}

	// A split comes in over the top rim of the strip and works down. The rim is
	// an edge of the box, where clay really does part; left and right are a seam,
	// or on the body an arbitrary cut between two thirds, and two cracks entering
	// either side of that cut landed on each other in one tangle.
	//
	// Top rather than bottom because moss climbs from the ground: the foot of
	// every side strip is green, and a crack that starts there is lost in it.
	static void split(BufferedImage image, Random random, Rectangle area, int colour, int segments) {
		int x = area.x + random.nextInt(area.width);

		// Straight down, give or take a fifth of a turn. Image y runs downward,
		// so that is a quarter turn.
		float angle = (float) (Math.PI / 2.0) + (random.nextFloat() - 0.5F) * 0.8F;

		branch(image, random, area, colour, x, area.y, angle, segments, 4, 2);
	}

	// A crack is a polyline, not a wander: it runs dead straight for a stretch,
	// then breaks at an angle and runs straight again, which is what the plates
	// of real cracked clay show. Picking a direction afresh at every texel, which
	// is what this did before, doubles back on itself and closes loops - and a
	// closed loop reads as a glyph stamped into the clay rather than as damage.
	//
	// A branch leaves a joint at a sharper angle than a joint turns, and carries
	// fewer segments and shorter ones. That difference is the whole of what tells
	// the eye which line is the crack and which came off it. depth is how many
	// more times a branch may itself fork; without a limit it turns into a bush,
	// which is what one chance in five of forking gave.
	static void branch(BufferedImage image, Random random, Rectangle area, int colour, float x, float y, float angle, int segments, int reach, int depth) {
		for (int i = 0; i < segments; i++) {
			angle += (random.nextFloat() - 0.5F) * 0.9F;

			int length = reach + random.nextInt(reach);
			float toX = x + (float) Math.cos(angle) * length;
			float toY = y + (float) Math.sin(angle) * length;

			line(image, area, colour, x, y, toX, toY);

			x = toX;
			y = toY;

			// Where a walk turned back at the edge and retraced itself, a crack
			// simply ends there.
			if (!area.contains(Math.round(x), Math.round(y))) {
				return;
			}

			if (depth > 0 && random.nextInt(3) == 0) {
				float away = (random.nextBoolean() ? 1.0F : -1.0F) * (0.6F + random.nextFloat() * 0.6F);

				branch(image, random, area, colour, x, y, angle + away, 1 + random.nextInt(2), Math.max(2, reach - 1), depth - 1);
			}
		}
	}

	// One texel wide, clipped to the area rather than stopped by it, so a stretch
	// that leaves is simply not drawn where it is outside.
	static void line(BufferedImage image, Rectangle area, int colour, float fromX, float fromY, float toX, float toY) {
		int steps = Math.max(1, Math.round(Math.max(Math.abs(toX - fromX), Math.abs(toY - fromY))));

		for (int i = 0; i <= steps; i++) {
			int x = Math.round(fromX + (toX - fromX) * i / steps);
			int y = Math.round(fromY + (toY - fromY) * i / steps);

			if (area.contains(x, y)) {
				set(image, x, y, colour);
			}
		}
	}

	// Short horizontal or vertical runs of the accent, which the cracked finish
	// uses for its veins.
	static void veins(BufferedImage image, Random random, Rectangle area, int colour, int count) {
		for (int i = 0; i < count; i++) {
			int x = area.x + random.nextInt(area.width);
			int y = area.y + random.nextInt(area.height);
			int length = (2 + random.nextInt(3)) * SCALE;
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
			int width = (1 + random.nextInt(size)) * SCALE;
			int height = (1 + random.nextInt(size)) * SCALE;
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
