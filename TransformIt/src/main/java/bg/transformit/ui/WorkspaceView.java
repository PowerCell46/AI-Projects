package bg.transformit.ui;


import bg.transformit.core.RenderResult;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

import java.awt.image.BufferedImage;


/**
 * The main image canvas, supporting two display modes:
 *
 * <ul>
 *   <li><b>SPLIT_SLIDER</b> (default) — shows the original on the left and the
 *       result on the right, split by a draggable vertical divider.</li>
 *   <li><b>ASCII_TOGGLE</b> — active when the last transform is ASCII; shows a
 *       monospace text area with a Before / After toggle button to switch to
 *       the original image.</li>
 * </ul>
 *
 * <p>Images are always letterboxed (fit-to-area, aspect ratio preserved) so
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
    private final Line      dividerLine  = new Line();
    private final Pane      splitPane    = new Pane();

    // -------------------------------------------------------------------------
    // ASCII-toggle nodes
    // -------------------------------------------------------------------------
    private final TextArea      asciiArea        = new TextArea();
    private final ToggleButton  beforeAfterToggle = new ToggleButton("Show Original");
    private final ImageView     asciiOriginalView = new ImageView();
    private final StackPane     asciiPane         = new StackPane();

    // -------------------------------------------------------------------------
    // Busy overlay
    // -------------------------------------------------------------------------
    private final StackPane busyOverlay = new StackPane();

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private double dividerRatio = 0.5;
    private boolean dragging    = false;

    private BufferedImage currentOriginal;
    private BufferedImage currentResult;


    public WorkspaceView() {
        getStyleClass().add("workspace");
        buildSplitPane();
        buildAsciiPane();
        buildBusyOverlay();

        getChildren().addAll(splitPane, asciiPane, busyOverlay);
        showSplitSlider();

        widthProperty().addListener((obs, old, nv)  -> refresh());
        heightProperty().addListener((obs, old, nv) -> refresh());
    }


    // =========================================================================
    // Public API
    // =========================================================================

    public void setOriginalImage(BufferedImage image) {
        currentOriginal = image;

        javafx.scene.image.Image fx = SwingFXUtils.toFXImage(image, null);
        originalView.setImage(fx);
        asciiOriginalView.setImage(fx);

        refresh();
    }

    /** Update the displayed result from a completed render. */
    public void showResult(RenderResult result) {
        currentResult = result.image();

        if (result.hasAscii()) {
            asciiArea.setText(result.ascii().toText());
            showAsciiToggle();
        } else {
            javafx.scene.image.Image fx = SwingFXUtils.toFXImage(result.image(), null);
            resultView.setImage(fx);
            showSplitSlider();
        }

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

        splitPane.getChildren().addAll(originalView, resultView, dividerLine);
        splitPane.getStyleClass().add("workspace");

        // Show a placeholder when nothing is loaded
        Label placeholder = new Label("Open an image to begin.");
        placeholder.getStyleClass().add("workspace-placeholder");
        StackPane.setAlignment(placeholder, Pos.CENTER);
        splitPane.getChildren().add(placeholder);

        installSplitDragHandlers();
    }

    private void buildAsciiPane() {
        asciiArea.setEditable(false);
        asciiArea.setWrapText(false);
        asciiArea.getStyleClass().add("ascii-area");

        // asciiOriginalView is shown when the toggle is pressed
        configureImageView(asciiOriginalView);
        asciiOriginalView.setVisible(false);

        beforeAfterToggle.getStyleClass().add("before-after-btn");
        beforeAfterToggle.selectedProperty().addListener((obs, old, showing) -> {
            asciiArea.setVisible(!showing);
            asciiArea.setManaged(!showing);
            asciiOriginalView.setVisible(showing);
        });

        StackPane.setAlignment(beforeAfterToggle, Pos.TOP_RIGHT);
        StackPane.setMargin(beforeAfterToggle, new Insets(10));

        asciiPane.getChildren().addAll(asciiArea, asciiOriginalView, beforeAfterToggle);
        asciiPane.setVisible(false);
        asciiPane.setManaged(false);
    }

    private void buildBusyOverlay() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(48, 48);

        busyOverlay.getChildren().add(spinner);
        busyOverlay.getStyleClass().add("busy-overlay");
        busyOverlay.setVisible(false);
    }

    // =========================================================================
    // Display-mode switches
    // =========================================================================

    private void showSplitSlider() {
        splitPane.setVisible(true);
        splitPane.setManaged(true);
        asciiPane.setVisible(false);
        asciiPane.setManaged(false);
    }

    private void showAsciiToggle() {
        splitPane.setVisible(false);
        splitPane.setManaged(false);
        asciiPane.setVisible(true);
        asciiPane.setManaged(true);
        beforeAfterToggle.setSelected(false);
        asciiArea.setVisible(true);
        asciiArea.setManaged(true);
        asciiOriginalView.setVisible(false);
    }

    // =========================================================================
    // Layout / refresh
    // =========================================================================

    /**
     * Recalculates clips and divider position after resize or a new image.
     * Both ImageViews fill the splitPane (preserveRatio=true, fitW/H bound),
     * so the letterboxing is automatic — we only need to update the clip rects.
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

        // Divider line
        dividerLine.setStartX(divX);
        dividerLine.setEndX(divX);
        dividerLine.setStartY(0);
        dividerLine.setEndY(h);

        // ImageViews fill the pane — keep their fit dimensions in sync
        originalView.setFitWidth(w);
        originalView.setFitHeight(h);
        resultView.setFitWidth(w);
        resultView.setFitHeight(h);

        asciiOriginalView.setFitWidth(asciiPane.getWidth());
        asciiOriginalView.setFitHeight(asciiPane.getHeight());
    }

    // =========================================================================
    // Drag handling for the split slider
    // =========================================================================

    private void installSplitDragHandlers() {
        splitPane.setOnMouseMoved(e -> {
            double divX = dividerRatio * splitPane.getWidth();
            boolean nearDivider = Math.abs(e.getX() - divX) < 20;
            splitPane.setCursor(nearDivider ? Cursor.H_RESIZE : Cursor.DEFAULT);
        });

        splitPane.setOnMousePressed(e -> {
            double divX = dividerRatio * splitPane.getWidth();
            dragging = Math.abs(e.getX() - divX) < 20;
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
