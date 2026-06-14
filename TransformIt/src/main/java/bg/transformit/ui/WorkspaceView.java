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
 *   <li><b>ASCII_WIPE</b> — active when the last transform is ASCII; shows the
 *       monospace text alongside the original image, wiped between by a slider
 *       (slider far-right = full ASCII, dragging left reveals the original).</li>
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
    // ASCII wipe-reveal nodes
    // -------------------------------------------------------------------------
    private final TextArea  asciiArea         = new TextArea();
    private final ImageView asciiOriginalView = new ImageView();
    private final Pane      asciiPane         = new Pane();
    private final Rectangle asciiClip         = new Rectangle();
    private final Rectangle asciiOriginalClip = new Rectangle();
    private final Line      asciiDividerLine  = new Line();

    // -------------------------------------------------------------------------
    // Busy overlay
    // -------------------------------------------------------------------------
    private final StackPane busyOverlay = new StackPane();

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private double dividerRatio     = 0.5;
    private double asciiRevealRatio = 0.5;
    private boolean dragging        = false;
    private boolean asciiDragging   = false;

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
            showAsciiView();
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
        asciiArea.setClip(asciiClip);
        asciiArea.setMouseTransparent(true);   // let the divider drag pass through

        configureImageView(asciiOriginalView);
        asciiOriginalView.setClip(asciiOriginalClip);

        asciiDividerLine.getStyleClass().add("divider-line");
        asciiDividerLine.setStrokeWidth(2);
        asciiDividerLine.setStroke(Color.web("#cba6f7"));

        // Same structure as the split pane: a plain Pane with children pinned at (0,0).
        asciiPane.getChildren().addAll(asciiOriginalView, asciiArea, asciiDividerLine);
        asciiPane.getStyleClass().add("workspace");
        asciiPane.setVisible(false);
        asciiPane.setManaged(false);

        asciiPane.widthProperty().addListener((obs, old, nv)  -> layoutAsciiWipe());
        asciiPane.heightProperty().addListener((obs, old, nv) -> layoutAsciiWipe());

        installAsciiDragHandlers();
    }

    /**
     * Lays out the ASCII wipe: the original image fills {@code [0 .. divX]} on the
     * left, the ASCII text fills {@code [divX .. width]} on the right, meeting at
     * the draggable divider, where {@code divX = paneWidth * asciiRevealRatio}.
     */
    private void layoutAsciiWipe() {
        double w = asciiPane.getWidth();
        double h = asciiPane.getHeight();

        if (w <= 0 || h <= 0) return;

        double divX = asciiRevealRatio * w;

        // Original image: clip to [0 .. divX]
        asciiOriginalClip.setX(0);
        asciiOriginalClip.setY(0);
        asciiOriginalClip.setWidth(divX);
        asciiOriginalClip.setHeight(h);

        // ASCII text: clip to [divX .. w]
        asciiClip.setX(divX);
        asciiClip.setY(0);
        asciiClip.setWidth(w - divX);
        asciiClip.setHeight(h);

        // Divider line
        asciiDividerLine.setStartX(divX);
        asciiDividerLine.setEndX(divX);
        asciiDividerLine.setStartY(0);
        asciiDividerLine.setEndY(h);

        // Image fills the pane (letterboxed); TextArea filled via its preferred size.
        asciiOriginalView.setFitWidth(w);
        asciiOriginalView.setFitHeight(h);
        asciiArea.setPrefSize(w, h);
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

    private void showAsciiView() {
        splitPane.setVisible(false);
        splitPane.setManaged(false);
        asciiPane.setVisible(true);
        asciiPane.setManaged(true);

        asciiRevealRatio = 0.5;   // start half original / half ASCII
        layoutAsciiWipe();
    }

    // =========================================================================
    // Layout / refresh
    // =========================================================================

    /** Re-lays both display modes after a resize or new image; each self-guards. */
    private void refresh() {
        layoutSplitSlider();
        layoutAsciiWipe();
    }

    /**
     * Positions the split-slider clips and divider. Both ImageViews fill the
     * splitPane (preserveRatio=true), so letterboxing is automatic — we only
     * need to update the clip rects and fit dimensions.
     */
    private void layoutSplitSlider() {
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

    /** Same divider drag as the split slider, driving the ASCII wipe ratio. */
    private void installAsciiDragHandlers() {
        asciiPane.setOnMouseMoved(e -> {
            double divX = asciiRevealRatio * asciiPane.getWidth();
            boolean nearDivider = Math.abs(e.getX() - divX) < 20;
            asciiPane.setCursor(nearDivider ? Cursor.H_RESIZE : Cursor.DEFAULT);
        });

        asciiPane.setOnMousePressed(e -> {
            double divX = asciiRevealRatio * asciiPane.getWidth();
            asciiDragging = Math.abs(e.getX() - divX) < 20;
        });

        asciiPane.setOnMouseDragged(e -> {
            if (!asciiDragging) return;
            asciiRevealRatio = Math.clamp(e.getX() / asciiPane.getWidth(), 0.02, 0.98);
            layoutAsciiWipe();
        });

        asciiPane.setOnMouseReleased(e -> asciiDragging = false);
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private void configureImageView(ImageView view) {
        view.setPreserveRatio(true);
        view.setSmooth(true);
    }
}
