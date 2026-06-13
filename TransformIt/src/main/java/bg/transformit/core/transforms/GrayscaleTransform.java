package bg.transformit.core.transforms;


import bg.transformit.core.Transform;

import java.awt.image.BufferedImage;


/**
 * Converts every pixel to its luminance value using the perceptual weights
 * {@code 0.299 R + 0.587 G + 0.114 B}, then writes that value back to all
 * three channels.  Alpha is preserved.
 */
public class GrayscaleTransform implements Transform {

    @Override
    public BufferedImage apply(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb  = src.getRGB(x, y);
                int alpha = (argb >> 24) & 0xFF;
                int r     = (argb >> 16) & 0xFF;
                int g     = (argb >>  8) & 0xFF;
                int b     =  argb        & 0xFF;

                int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);

                out.setRGB(x, y, (alpha << 24) | (gray << 16) | (gray << 8) | gray);
            }
        }

        return out;
    }

    @Override
    public String displayName() {
        return "Grayscale";
    }
}
