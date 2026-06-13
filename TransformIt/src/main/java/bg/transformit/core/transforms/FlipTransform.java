package bg.transformit.core.transforms;


import bg.transformit.core.Transform;

import java.awt.image.BufferedImage;


/** Mirrors the image horizontally, vertically, or both. */
public class FlipTransform implements Transform {

    private boolean flipHorizontal = true;
    private boolean flipVertical   = false;


    @Override
    public BufferedImage apply(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int destX = flipHorizontal ? (w - 1 - x) : x;
                int destY = flipVertical   ? (h - 1 - y) : y;

                out.setRGB(destX, destY, src.getRGB(x, y));
            }
        }

        return out;
    }

    @Override
    public String displayName() {
        return "Flip / Mirror";
    }

    public boolean isFlipHorizontal()             { return flipHorizontal; }
    public void    setFlipHorizontal(boolean v)   { flipHorizontal = v; }

    public boolean isFlipVertical()               { return flipVertical; }
    public void    setFlipVertical(boolean v)     { flipVertical = v; }
}
