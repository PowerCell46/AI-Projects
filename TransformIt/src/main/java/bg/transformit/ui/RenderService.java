package bg.transformit.ui;


import bg.transformit.core.RenderResult;
import bg.transformit.core.Transform;
import bg.transformit.core.TransformPipeline;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.util.Duration;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;


/**
 * Manages async rendering of the transform pipeline.
 *
 * <p>All pixel work runs on a daemon {@link Thread} via a JavaFX {@link Task},
 * so the UI thread is never blocked.  Slider drags are debounced — a new
 * render fires only after {@value DEBOUNCE_MS} ms of inactivity, preventing
 * dozens of redundant renders during a single drag.
 *
 * <p>Usage:
 * <ol>
 *   <li>Call {@link #setOriginalImage} once when an image is opened.</li>
 *   <li>Wire up {@link #setTransformsSupplier} and {@link #setOnResult} from the UI.</li>
 *   <li>Call {@link #scheduleRender} whenever pipeline state or params change.</li>
 * </ol>
 */
public class RenderService {

    private static final int DEBOUNCE_MS = 150;


    private final TransformPipeline pipeline = new TransformPipeline();

    private BufferedImage          originalImage;
    private Supplier<List<Transform>> transformsSupplier;
    private Consumer<RenderResult> onResult;
    private Consumer<Boolean>      onBusy;

    private Timeline debounceTimer;
    private Task<RenderResult> currentTask;


    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    public void setOriginalImage(BufferedImage image) {
        this.originalImage = image;
        scheduleRender();
    }

    /** Called before each render to fetch the current ordered, enabled transform list. */
    public void setTransformsSupplier(Supplier<List<Transform>> supplier) {
        this.transformsSupplier = supplier;
    }

    /** Invoked on the FX thread when a render completes successfully. */
    public void setOnResult(Consumer<RenderResult> onResult) {
        this.onResult = onResult;
    }

    /**
     * Invoked with {@code true} when a render starts and {@code false} when it
     * finishes.  Use this to show / hide a loading overlay.
     */
    public void setOnBusy(Consumer<Boolean> onBusy) {
        this.onBusy = onBusy;
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    /** Debounces and schedules a render. Safe to call from the FX thread. */
    public void scheduleRender() {
        if (originalImage == null || transformsSupplier == null || onResult == null) return;

        stopDebounce();

        debounceTimer = new Timeline(new KeyFrame(Duration.millis(DEBOUNCE_MS), e -> executeRender()));
        debounceTimer.play();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void executeRender() {
        cancelCurrentTask();

        // Snapshot state so the background thread doesn't race with UI changes
        List<Transform>  transforms   = transformsSupplier.get();
        BufferedImage    original     = originalImage;

        pipeline.setTransforms(transforms);

        currentTask = new Task<>() {
            @Override
            protected RenderResult call() {
                return pipeline.apply(original);
            }
        };

        currentTask.setOnSucceeded(e -> {
            notifyBusy(false);
            onResult.accept(currentTask.getValue());
        });

        currentTask.setOnFailed(e -> notifyBusy(false));
        currentTask.setOnCancelled(e -> notifyBusy(false));

        notifyBusy(true);

        Thread worker = new Thread(currentTask, "transformit-render");
        worker.setDaemon(true);
        worker.start();
    }

    private void stopDebounce() {
        if (debounceTimer != null) {
            debounceTimer.stop();
            debounceTimer = null;
        }
    }

    private void cancelCurrentTask() {
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
        }
    }

    private void notifyBusy(boolean busy) {
        if (onBusy != null) onBusy.accept(busy);
    }
}
