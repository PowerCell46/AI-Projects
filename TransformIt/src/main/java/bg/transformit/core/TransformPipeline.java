package bg.transformit.core;


import bg.transformit.core.transforms.AsciiTransform;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;


/**
 * An ordered sequence of {@link Transform}s applied left-to-right from the
 * untouched original image.
 *
 * <p>The pipeline is intentionally <em>non-destructive</em>: the original is
 * never mutated.  Any param change or reorder re-runs the full chain from the
 * original, which keeps the model simple and the output deterministic.
 */
public class TransformPipeline {

    private final List<Transform> transforms = new ArrayList<>();


    /** Replace the active transform list. Called by the UI before each render. */
    public void setTransforms(List<Transform> transforms) {
        this.transforms.clear();
        this.transforms.addAll(transforms);
    }

    /**
     * Run every transform in order, starting from {@code original}.
     *
     * <p>If the last transform is an {@link AsciiTransform}, the returned
     * {@link RenderResult} also carries the ASCII character grid.
     */
    public RenderResult apply(BufferedImage original) {
        BufferedImage current = original;

        for (Transform t : transforms) {
            current = t.apply(current);
        }

        AsciiTransform asciiTransform = lastAsciiTransform();

        if (asciiTransform != null) {
            return new RenderResult(current, asciiTransform.getLastResult());
        }

        return RenderResult.ofImage(current);
    }

    public boolean isEmpty() {
        return transforms.isEmpty();
    }

    // -------------------------------------------------------------------------

    private AsciiTransform lastAsciiTransform() {
        if (transforms.isEmpty()) return null;

        Transform last = transforms.getLast();

        return last instanceof AsciiTransform at ? at : null;
    }
}
