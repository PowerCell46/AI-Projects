package bg.transformit.core;


import java.awt.image.BufferedImage;


/**
 * A single, stateless (or param-holding) image transformation.
 *
 * <p>Contract: {@link #apply} must never mutate {@code src} — it returns a
 * new {@link BufferedImage}.  The pipeline always re-runs from the original
 * image, so immutability of the source is critical.
 *
 * <p>Implementations live in {@code bg.transformit.core.transforms} and must
 * import <em>no</em> JavaFX types — this package is UI-free by design.
 */
public interface Transform {

    /**
     * Apply the transformation and return the result image.
     *
     * @param src source image — must not be mutated
     * @return transformed copy
     */
    BufferedImage apply(BufferedImage src);

    /** Human-readable name shown in the pipeline panel. */
    String displayName();
}
