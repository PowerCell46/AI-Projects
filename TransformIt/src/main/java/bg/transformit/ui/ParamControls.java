package bg.transformit.ui;


import bg.transformit.core.Transform;
import bg.transformit.core.transforms.*;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;


/**
 * Dynamically builds the parameter controls for whichever {@link Transform} is
 * currently selected in the pipeline.
 *
 * <p>Uses Java 21+ pattern-matching switch to dispatch on the concrete type and
 * binds every control directly to the transform's fields.  Any change fires the
 * {@code onChanged} runnable so the {@link RenderService} can schedule a new render.
 */
public class ParamControls extends VBox {

    private final Runnable onChanged;


    public ParamControls(Runnable onChanged) {
        this.onChanged = onChanged;
        getStyleClass().add("param-controls");
        setSpacing(10);
        setPadding(new Insets(8, 0, 0, 0));
        showPlaceholder("Select a transform\nto adjust its parameters.");
    }


    // =========================================================================
    // Public API
    // =========================================================================

    /** Replaces the current controls with those appropriate for {@code transform}. */
    public void show(Transform transform) {
        getChildren().clear();

        Node controls = switch (transform) {
            case GrayscaleTransform      gt  -> buildGrayscaleControls();
            case HorizontalFlipTransform hf  -> infoHint("Mirrors the image left ↔ right.");
            case VerticalFlipTransform   vf  -> infoHint("Mirrors the image top ↕ bottom.");
            case BrightnessContrastTransform bct -> buildBrightnessContrastControls(bct);
            case ColorRemapTransform     crt -> buildColorRemapControls(crt);
            case AsciiTransform          at  -> buildAsciiControls(at);
            default                          -> new Label("No parameters.");
        };

        getChildren().add(controls);
    }

    public void clear() {
        getChildren().clear();
        showPlaceholder("Select a transform\nto adjust its parameters.");
    }

    // =========================================================================
    // Per-transform builders
    // =========================================================================

    private Node buildGrayscaleControls() {
        return infoHint("Converts to luminance:\n0.299 R + 0.587 G + 0.114 B");
    }

    /** A read-only hint used for transforms that have no adjustable parameters. */
    private Node infoHint(String text) {
        Label info = new Label(text);
        info.getStyleClass().add("param-hint");
        info.setWrapText(true);
        return info;
    }

    private Node buildBrightnessContrastControls(BrightnessContrastTransform bct) {
        Slider brightnessSlider = slider(-128, 128, bct.getBrightness());
        Slider contrastSlider   = slider(0.5, 3.0, bct.getContrast());

        brightnessSlider.valueProperty().addListener((obs, old, nv) -> {
            bct.setBrightness(nv.floatValue());
            onChanged.run();
        });

        contrastSlider.valueProperty().addListener((obs, old, nv) -> {
            bct.setContrast(nv.floatValue());
            onChanged.run();
        });

        VBox box = new VBox(6,
                labeled("Brightness", brightnessSlider),
                labeled("Contrast",   contrastSlider)
        );

        return box;
    }

    private Node buildColorRemapControls(ColorRemapTransform crt) {
        // Mode radio buttons
        ToggleGroup modeGroup   = new ToggleGroup();
        RadioButton autoBtn     = new RadioButton("Auto swap");
        RadioButton paletteBtn  = new RadioButton("Palette");

        autoBtn.setToggleGroup(modeGroup);
        paletteBtn.setToggleGroup(modeGroup);

        autoBtn.setSelected(crt.getMode() == ColorRemapTransform.Mode.AUTO_SWAP);
        paletteBtn.setSelected(crt.getMode() == ColorRemapTransform.Mode.PALETTE);

        // Auto-mode: bucket count slider
        Label bucketLabel  = new Label("Buckets: " + crt.getBucketCount());
        Slider bucketSlider = slider(2, 32, crt.getBucketCount());
        bucketSlider.setMajorTickUnit(1);
        bucketSlider.setSnapToTicks(true);
        VBox autoPane = new VBox(4, bucketLabel, bucketSlider);

        bucketSlider.valueProperty().addListener((obs, old, nv) -> {
            int k = nv.intValue();
            crt.setBucketCount(k);
            bucketLabel.setText("Buckets: " + k);
            onChanged.run();
        });

        // Palette-mode: palette combo
        ComboBox<ColorPalette> paletteCombo = new ComboBox<>();
        paletteCombo.getItems().addAll(ColorPalette.values());
        paletteCombo.setValue(crt.getSelectedPalette());
        VBox palettePane = new VBox(4, new Label("Palette"), paletteCombo);

        paletteCombo.valueProperty().addListener((obs, old, nv) -> {
            crt.setSelectedPalette(nv);
            onChanged.run();
        });

        // Show / hide sub-panes based on mode
        Runnable updateVisibility = () -> {
            boolean isAuto = crt.getMode() == ColorRemapTransform.Mode.AUTO_SWAP;
            autoPane.setVisible(isAuto);
            autoPane.setManaged(isAuto);
            palettePane.setVisible(!isAuto);
            palettePane.setManaged(!isAuto);
        };

        autoBtn.setOnAction(e -> { crt.setMode(ColorRemapTransform.Mode.AUTO_SWAP); updateVisibility.run(); onChanged.run(); });
        paletteBtn.setOnAction(e -> { crt.setMode(ColorRemapTransform.Mode.PALETTE);   updateVisibility.run(); onChanged.run(); });

        updateVisibility.run();

        return new VBox(8,
                labeled("Mode", new HBox(12, autoBtn, paletteBtn)),
                autoPane,
                palettePane
        );
    }

    private Node buildAsciiControls(AsciiTransform at) {
        Label blockLabel  = new Label("Block size: " + at.getBlockSize() + "px");
        Slider blockSlider = slider(1, 20, at.getBlockSize());
        blockSlider.setMajorTickUnit(1);
        blockSlider.setSnapToTicks(true);

        blockSlider.valueProperty().addListener((obs, old, nv) -> {
            int sz = nv.intValue();
            at.setBlockSize(sz);
            blockLabel.setText("Block size: " + sz + "px");
            onChanged.run();
        });

        ToggleButton invertBtn  = new ToggleButton("Invert");
        ToggleButton coloredBtn = new ToggleButton("Colored");

        invertBtn.setSelected(at.isInverted());
        coloredBtn.setSelected(at.isColored());

        invertBtn.setOnAction(e -> { at.setInverted(invertBtn.isSelected());   onChanged.run(); });
        coloredBtn.setOnAction(e -> { at.setColored(coloredBtn.isSelected());  onChanged.run(); });

        return new VBox(8,
                labeled("Density", new VBox(2, blockLabel, blockSlider)),
                labeled("Style",   new HBox(8, invertBtn, coloredBtn))
        );
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Slider slider(double min, double max, double value) {
        Slider s = new Slider(min, max, value);
        s.setShowTickMarks(false);
        s.setShowTickLabels(false);
        return s;
    }

    private VBox labeled(String labelText, Node content) {
        Label label = new Label(labelText.toUpperCase());
        label.getStyleClass().add("param-label");
        return new VBox(4, label, content);
    }

    private void showPlaceholder(String text) {
        Label placeholder = new Label(text);
        placeholder.getStyleClass().add("param-hint");
        placeholder.setWrapText(true);
        getChildren().add(placeholder);
    }
}
