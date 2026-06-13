package bg.transformit.core;


import bg.transformit.core.transforms.AsciiResult;

import java.awt.image.BufferedImage;


/**
 * The output of one full pipeline execution.
 *
 * <p>{@code ascii} is non-null only when the last transform in the pipeline
 * was an {@link bg.transformit.core.transforms.AsciiTransform}.  In that case
 * {@code image} holds the rendered bitmap and {@code ascii} carries the raw
 * character grid for the {@code .txt} export path.
 */
public record RenderResult(BufferedImage image, AsciiResult ascii) {

    /** Convenience factory for a plain pixel-transform result. */
    public static RenderResult ofImage(BufferedImage image) {
        return new RenderResult(image, null);
    }

    public boolean hasAscii() {
        return ascii != null;
    }
}
