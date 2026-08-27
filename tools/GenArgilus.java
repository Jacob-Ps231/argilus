// Source of assets/argilus/textures/entity/argilus.png, kept in the repository
// because a generated file whose generator is lost stops being editable.
//
// Run it with the single file launcher, no build needed:
//   java tools/GenArgilus.java src/main/resources/assets/argilus/textures/entity/argilus.png
//
// The regions below are dictated by the texOffs values in ArgilusModel: change
// one and the other has to follow. Unwrapping a box lays its four side faces in
// one horizontal strip, which is how the hat band is drawn without geometry.
//
// Nothing here ships in the mod. Repainting the png by hand is fine, it just
// means this file no longer describes what is on disk.

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

public class GenArgilus {
    static final int CLAY = 0xA4A8B8;
    static final int CLAY_DARK = 0x8B90A2;
    static final int CLAY_LIGHT = 0xB8BCCA;

    static final int STRAW = 0xC7A44E;
    static final int STRAW_DARK = 0xA8873C;
    static final int STRAW_LIGHT = 0xDCBC6A;

    static final int BAND = 0x8B3A2E;
    static final int BAND_DARK = 0x743026;
    static final int BAND_LIGHT = 0xA04739;

    static final int EYE = 0x3A3F4C;

    public static void main(String[] args) throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(7);

        // Flat clay with a light speckle, so the unwrap never shows a seam.
        speckle(image, random, 0, 0, 64, 64, CLAY, CLAY_DARK, CLAY_LIGHT);

        // Hat crown at texOffs(0, 32), a 6x2x6 box: u 0..24, v 32..40.
        speckle(image, random, 0, 32, 24, 8, STRAW, STRAW_DARK, STRAW_LIGHT);

        // The four side faces of the crown land in one strip, v 38..40 across
        // u 0..24, so colouring its lower row bands the hat all the way round.
        speckle(image, random, 0, 39, 24, 1, BAND, BAND_DARK, BAND_LIGHT);

        // Hat brim at texOffs(0, 44), a 10x1x10 box: u 0..40, v 44..55.
        speckle(image, random, 0, 44, 40, 11, STRAW, STRAW_DARK, STRAW_LIGHT);

        // Front face of the head: u 6..12, v 6..11 for a 6x5x6 box at texOffs(0, 0).
        for (int y = 8; y <= 9; y++) {
            image.setRGB(7, y, 0xFF000000 | EYE);
            image.setRGB(10, y, 0xFF000000 | EYE);
        }

        File out = new File(args[0]);
        ImageIO.write(image, "PNG", out);
        System.out.println("ecrit : " + out + " (" + out.length() + " o)");
    }

    static void speckle(BufferedImage image, Random random,
            int x0, int y0, int width, int height, int base, int dark, int light) {
        for (int y = y0; y < y0 + height; y++) {
            for (int x = x0; x < x0 + width; x++) {
                int roll = random.nextInt(10);
                int rgb = roll == 0 ? dark : roll == 1 ? light : base;
                image.setRGB(x, y, 0xFF000000 | rgb);
            }
        }
    }
}
