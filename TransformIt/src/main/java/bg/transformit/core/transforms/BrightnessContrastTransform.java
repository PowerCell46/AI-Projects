package bg.transformit.core.transforms;


import bg.transformit.core.Transform;

import java.awt.image.BufferedImage;


/**
 * Adjusts brightness and contrast per channel using the formula:
 * <pre>
 *   out = clamp( (in - 128) * contrast + 128 + brightness )
 * </pre>
 * {@code brightness} is in [-128, 128] (0 = no change).
 * {@code contrast}   is in [0.0, 3.0]  (1.0 = no change).
 */
public class BrightnessContrastTransform implements Transform {

    private float brightness = 0f;
    private float contrast   = 1f;


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

                int nr = adjust(r);
                int ng = adjust(g);
                int nb = adjust(b);

                out.setRGB(x, y, (alpha << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }

        return out;
    }

    @Override
    public String displayName() {
        return "Brightness / Contrast";
    }

    public float getBrightness()          { return brightness; }
    public void  setBrightness(float v)   { brightness = v; }

    public float getContrast()            { return contrast; }
    public void  setContrast(float v)     { contrast = v; }

    // -------------------------------------------------------------------------

    private int adjust(int channel) {
        float v = (channel - 128f) * contrast + 128f + brightness;
        return Math.clamp(Math.round(v), 0, 255);
    }
}
