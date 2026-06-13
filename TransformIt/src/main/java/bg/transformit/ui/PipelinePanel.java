package bg.transformit.ui;


import bg.transformit.core.Transform;
import bg.transformit.core.transforms.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.function.Consumer;


/**
 * Left-side panel with two sections:
 * <ol>
 *   <li><b>Add Transform</b> — buttons for each available transform.</li>
 *   <li><b>Pipeline</b> — the ordered list of active entries with
 *       enable/disable checkboxes and remove buttons.</li>
 * </ol>
 *
 * <p>Callbacks:
 * <ul>
 *   <li>{@code onPipelineChanged} — fires when the list or any enabled flag changes.</li>
 *   <li>{@code onEntrySelected}   — fires when the user clicks an entry row;
 *       passes the entry's {@link Transform} to {@link ParamControls}.</li>
 * </ul>
 */
public class PipelinePanel extends VBox {

    private final ObservableList<PipelineEntry> entries = FXCollections.observableArrayList();

    private Runnable              onPipelineChanged;
    private Consumer<Transform>   onEntrySelected;

    private PipelineEntry         selectedEntry;
    private VBox                  pipelineList;


    public PipelinePanel() {
        getStyleClass().add("side-panel");
        setSpacing(0);
        setPrefWidth(280);
        setMinWidth(280);

        getChildren().addAll(
                buildAddSection(),
                buildSeparator(),
                buildPipelineSection()
        );

        entries.addListener((ListChangeListener<PipelineEntry>) c -> firePipelineChanged());
    }


    // =========================================================================
    // Public API
    // =========================================================================

    public void setOnPipelineChanged(Runnable callback) {
        this.onPipelineChanged = callback;
    }

    public void setOnEntrySelected(Consumer<Transform> callback) {
        this.onEntrySelected = callback;
    }

    /** Returns only the enabled transforms, in pipeline order. */
    public List<Transform> enabledTransforms() {
        return entries.stream()
                .filter(e -> e.getEnabled().get())
                .map(PipelineEntry::getTransform)
                .toList();
    }

    // =========================================================================
    // UI builders
    // =========================================================================

    private Node buildAddSection() {
        Label heading = sectionLabel("Add Transform");

        Button grayscaleBtn  = paletteButton("Grayscale",          () -> addEntry(new GrayscaleTransform()));
        Button flipBtn       = paletteButton("Flip / Mirror",       () -> addEntry(new FlipTransform()));
        Button brightnessBtn = paletteButton("Brightness / Contrast", () -> addEntry(new BrightnessContrastTransform()));
        Button colorRemapBtn = paletteButton("Color Remap",         () -> addEntry(new ColorRemapTransform()));
        Button asciiBtn      = paletteButton("ASCII Art",           () -> addEntry(new AsciiTransform()));

        FlowPane palette = new FlowPane(6, 6,
                grayscaleBtn, flipBtn, brightnessBtn, colorRemapBtn, asciiBtn);
        palette.setPadding(new Insets(6, 0, 6, 0));

        VBox section = new VBox(6, heading, palette);
        section.setPadding(new Insets(12, 12, 8, 12));
        return section;
    }

    private Node buildPipelineSection() {
        Label heading = sectionLabel("Pipeline");

        pipelineList = new VBox(4);
        pipelineList.setPadding(new Insets(4, 0, 0, 0));

        ScrollPane scroll = new ScrollPane(pipelineList);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox section = new VBox(6, heading, scroll);
        section.setPadding(new Insets(8, 12, 12, 12));
        VBox.setVgrow(section, Priority.ALWAYS);
        return section;
    }

    // =========================================================================
    // Entry management
    // =========================================================================

    private void addEntry(Transform transform) {
        // ASCII must be last — block any addition after it
        if (hasAsciiAsLast() && !(transform instanceof AsciiTransform)) {
            showAsciiLastWarning();
            return;
        }

        // Only one ASCII allowed
        if (transform instanceof AsciiTransform && hasAscii()) {
            showAsciiLastWarning();
            return;
        }

        PipelineEntry entry = new PipelineEntry(transform);
        entries.add(entry);
        pipelineList.getChildren().add(buildEntryRow(entry));
        selectEntry(entry);
    }

    private HBox buildEntryRow(PipelineEntry entry) {
        CheckBox enabledBox = new CheckBox();
        enabledBox.selectedProperty().bindBidirectional(entry.getEnabled());
        enabledBox.selectedProperty().addListener((obs, old, nv) -> firePipelineChanged());

        Label nameLabel = new Label(entry.displayName());
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        Button removeBtn = new Button("×");
        removeBtn.getStyleClass().add("remove-btn");
        removeBtn.setOnAction(e -> removeEntry(entry));

        HBox row = new HBox(8, enabledBox, nameLabel, removeBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("pipeline-entry");
        row.setUserData(entry);

        row.setOnMouseClicked(e -> selectEntry(entry));

        return row;
    }

    private void removeEntry(PipelineEntry entry) {
        int idx = entries.indexOf(entry);
        if (idx < 0) return;

        entries.remove(idx);
        pipelineList.getChildren().remove(idx);

        if (selectedEntry == entry) {
            selectedEntry = null;
            if (onEntrySelected != null) onEntrySelected.accept(null);
        }
    }

    private void selectEntry(PipelineEntry entry) {
        // Update CSS highlight on all rows
        pipelineList.getChildren().forEach(node -> node.getStyleClass().remove("selected"));

        selectedEntry = entry;

        // Find and highlight the row for this entry
        pipelineList.getChildren().stream()
                .filter(node -> node.getUserData() == entry)
                .findFirst()
                .ifPresent(node -> node.getStyleClass().add("selected"));

        if (onEntrySelected != null) {
            onEntrySelected.accept(entry != null ? entry.getTransform() : null);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void firePipelineChanged() {
        if (onPipelineChanged != null) onPipelineChanged.run();
    }

    private boolean hasAsciiAsLast() {
        return !entries.isEmpty()
                && entries.getLast().getTransform() instanceof AsciiTransform;
    }

    private boolean hasAscii() {
        return entries.stream().anyMatch(e -> e.getTransform() instanceof AsciiTransform);
    }

    private void showAsciiLastWarning() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION,
                "ASCII Art must be the last step.\nRemove it first to add other transforms.",
                ButtonType.OK);
        alert.setTitle("Pipeline Order");
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text.toUpperCase());
        label.getStyleClass().add("section-label");
        return label;
    }

    private Button paletteButton(String text, Runnable action) {
        Button btn = new Button("+ " + text);
        btn.getStyleClass().add("palette-btn");
        btn.setOnAction(e -> action.run());
        return btn;
    }

    private Separator buildSeparator() {
        Separator sep = new Separator();
        sep.getStyleClass().add("panel-separator");
        return sep;
    }
}
