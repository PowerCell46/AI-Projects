package bg.transformit.ui;


import bg.transformit.core.RenderResult;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

import java.awt.image.BufferedImage;


/**
 * The main image canvas: the original on the left and the result on the right,
 * split by a draggable vertical divider.
 *
 * <p>When the last transform is ASCII, the result is the <em>rendered</em> ASCII
 * image — because the renderer is aspect-corrected to the source, it letterboxes
 * to the same rectangle as the original, so the two sides align at the divider
 * exactly like any other transform. No separate display mode is needed.
 *
 * <p>Both images are always letterboxed (fit-to-area, aspect ratio preserved) so
 * they never crop, overflow, or stretch.
 */
public class WorkspaceView extends StackPane {

    // -------------------------------------------------------------------------
    // Split-slider nodes
    // -------------------------------------------------------------------------
    private final ImageView originalView = new ImageView();
    private final ImageView resultView   = new ImageView();
    private final Rectangle originalClip = new Rectangle();
    private final Rectangle resultClip   = new Rectangle();
    private final Line      dividerLine   = new Line();
    private final Circle    dividerHandle = new Circle(9);
    private final Pane      splitPane     = new Pane();
    private final Label     placeholder   = new Label("Open an image to begin.");

    /** Half-width (px) of the zone around the divider that starts a drag. */
    private static final double GRAB_ZONE = 36;

    // -------------------------------------------------------------------------
    // Busy overlay
    // -------------------------------------------------------------------------
    private final StackPane busyOverlay = new StackPane();

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private double  dividerRatio = 0.5;
    private boolean dragging     = false;

    private BufferedImage currentOriginal;
    private BufferedImage currentResult;


    public WorkspaceView() {
        getStyleClass().add("workspace");
        buildSplitPane();
        buildBusyOverlay();

        getChildren().addAll(splitPane, busyOverlay);

        // Listen on splitPane (not this) so refresh() reads its size only once it
        // has actually been resized — otherwise the divider/handle lag a layout
        // pass behind the real width and the grab zone ends up off the knob.
        splitPane.widthProperty().addListener((obs, old, nv)  -> refresh());
        splitPane.heightProperty().addListener((obs, old, nv) -> refresh());
    }


    // =========================================================================
    // Public API
    // =========================================================================

    public void setOriginalImage(BufferedImage image) {
        currentOriginal = image;
        originalView.setImage(SwingFXUtils.toFXImage(image, null));
        placeholder.setVisible(false);
        refresh();
    }

    /** Update the displayed result from a completed render. */
    public void showResult(RenderResult result) {
        currentResult = result.image();
        resultView.setImage(SwingFXUtils.toFXImage(result.image(), null));
        refresh();
    }

    /** Show / hide the semi-transparent loading overlay. */
    public void setBusy(boolean busy) {
        busyOverlay.setVisible(busy);
    }

    /** True if there is nothing to display yet. */
    public boolean isEmpty() {
        return currentOriginal == null;
    }

    // =========================================================================
    // Node builders
    // =========================================================================

    private void buildSplitPane() {
        configureImageView(originalView);
        configureImageView(resultView);

        originalView.setClip(originalClip);
        resultView.setClip(resultClip);

        dividerLine.getStyleClass().add("divider-line");
        dividerLine.setStrokeWidth(2);
        dividerLine.setStroke(Color.web("#cba6f7"));

        // Visible grab knob so the divider clearly reads (and behaves) as a slider.
        dividerHandle.getStyleClass().add("divider-handle");
        dividerHandle.setFill(Color.web("#cba6f7"));
        dividerHandle.setStroke(Color.web("#1e1e2e"));
        dividerHandle.setStrokeWidth(2);
        dividerHandle.setCursor(Cursor.H_RESIZE);

        splitPane.getChildren().addAll(originalView, resultView, dividerLine, dividerHandle);
        splitPane.getStyleClass().add("workspace");

        // Shown until the first image loads (see setOriginalImage)
        placeholder.getStyleClass().add("workspace-placeholder");
        placeholder.layoutXProperty().bind(splitPane.widthProperty().subtract(placeholder.widthProperty()).divide(2));
        placeholder.layoutYProperty().bind(splitPane.heightProperty().subtract(placeholder.heightProperty()).divide(2));
        splitPane.getChildren().add(placeholder);

        installSplitDragHandlers();
    }

    private void buildBusyOverlay() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(48, 48);

        busyOverlay.getChildren().add(spinner);
        busyOverlay.getStyleClass().add("busy-overlay");
        busyOverlay.setVisible(false);
    }

    // =========================================================================
    // Layout / refresh
    // =========================================================================

    /**
     * Positions the clips and divider. Both ImageViews fill the splitPane
     * (preserveRatio=true), so letterboxing is automatic — we only need to update
     * the clip rects and fit dimensions on every resize or divider move.
     */
    private void refresh() {
        double w = splitPane.getWidth();
        double h = splitPane.getHeight();

        if (w <= 0 || h <= 0) return;

        double divX = dividerRatio * w;

        // Original: clip to [0 .. divX]
        originalClip.setX(0);
        originalClip.setY(0);
        originalClip.setWidth(divX);
        originalClip.setHeight(h);

        // Result: clip to [divX .. w]
        resultClip.setX(divX);
        resultClip.setY(0);
        resultClip.setWidth(w - divX);
        resultClip.setHeight(h);

        // Divider line + grab handle
        dividerLine.setStartX(divX);
        dividerLine.setEndX(divX);
        dividerLine.setStartY(0);
        dividerLine.setEndY(h);
        dividerHandle.setCenterX(divX);
        dividerHandle.setCenterY(h / 2);

        // ImageViews fill the pane — keep their fit dimensions in sync
        originalView.setFitWidth(w);
        originalView.setFitHeight(h);
        resultView.setFitWidth(w);
        resultView.setFitHeight(h);
    }

    // =========================================================================
    // Drag handling for the split slider
    // =========================================================================

    private void installSplitDragHandlers() {
        splitPane.setOnMouseMoved(e -> {
            double divX = dividerRatio * splitPane.getWidth();
            boolean nearDivider = Math.abs(e.getX() - divX) < GRAB_ZONE;
            splitPane.setCursor(nearDivider ? Cursor.H_RESIZE : Cursor.DEFAULT);
        });

        splitPane.setOnMousePressed(e -> {
            double divX = dividerRatio * splitPane.getWidth();
            dragging = Math.abs(e.getX() - divX) < GRAB_ZONE;
        });

        splitPane.setOnMouseDragged(e -> {
            if (!dragging) return;
            dividerRatio = Math.clamp(e.getX() / splitPane.getWidth(), 0.02, 0.98);
            refresh();
        });

        splitPane.setOnMouseReleased(e -> dragging = false);
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private void configureImageView(ImageView view) {
        view.setPreserveRatio(true);
        view.setSmooth(true);
    }
}
