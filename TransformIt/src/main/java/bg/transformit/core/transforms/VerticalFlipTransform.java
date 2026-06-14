package bg.transformit.core.transforms;


import bg.transformit.core.Transform;

import java.awt.image.BufferedImage;


/** Mirrors the image top-to-bottom (vertical flip). */
public class VerticalFlipTransform implements Transform {

    @Override
    public BufferedImage apply(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, h - 1 - y, src.getRGB(x, y));
            }
        }

        return out;
    }

    @Override
    public String displayName() {
        return "Flip Vertical";
    }
}
