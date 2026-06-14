package bg.transformit.ui;


import bg.transformit.core.RenderResult;
import bg.transformit.io.ImageLoader;
import bg.transformit.io.PngExporter;
import bg.transformit.io.TxtExporter;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.io.IOException;


/**
 * Root view — assembles the toolbar, the left pipeline panel, the params area,
 * and the central workspace into the full application layout.
 *
 * <p>Also owns the {@link RenderService} and wires all callbacks so that any
 * pipeline or param change triggers a debounced background render.
 */
public class MainView extends BorderPane {

    private final Stage         stage;
    private final WorkspaceView workspace        = new WorkspaceView();
    private final PipelinePanel pipelinePanel    = new PipelinePanel();
    private final ParamControls paramControls;
    private final RenderService renderService    = new RenderService();

    private final ImageLoader   imageLoader      = new ImageLoader();
    private final PngExporter   pngExporter      = new PngExporter();
    private final TxtExporter   txtExporter      = new TxtExporter();

    private RenderResult        lastResult;


    public MainView(Stage stage) {
        this.stage = stage;
        this.paramControls = new ParamControls(renderService::scheduleRender);

        buildLayout();
        wireCallbacks();
    }


    // =========================================================================
    // Layout
    // =========================================================================

    private void buildLayout() {
        setTop(buildToolbar());
        setLeft(buildSidePanel());
        setCenter(workspace);
    }

    private ToolBar buildToolbar() {
        Button openBtn      = new Button("Open Image");
        Button exportPngBtn = new Button("Export PNG");
        Button exportTxtBtn = new Button("Export TXT");

        openBtn.getStyleClass().addAll("toolbar-btn", "accent");
        exportPngBtn.getStyleClass().add("toolbar-btn");
        exportTxtBtn.getStyleClass().add("toolbar-btn");

        openBtn.setOnAction(e      -> openImage());
        exportPngBtn.setOnAction(e -> exportPng());
        exportTxtBtn.setOnAction(e -> exportTxt());

        ToolBar toolbar = new ToolBar(openBtn, exportPngBtn, exportTxtBtn);
        toolbar.getStyleClass().add("app-toolbar");
        return toolbar;
    }

    private VBox buildSidePanel() {
        Label paramsHeading = new Label("PARAMETERS");
        paramsHeading.getStyleClass().add("section-label");
        paramsHeading.setPadding(new Insets(0, 0, 4, 0));

        VBox paramsSection = new VBox(6, paramsHeading, paramControls);
        paramsSection.getStyleClass().add("params-section");
        paramsSection.setPadding(new Insets(8, 12, 12, 12));

        VBox.setVgrow(pipelinePanel, Priority.ALWAYS);

        VBox panel = new VBox(pipelinePanel, new Separator(), paramsSection);
        panel.getStyleClass().add("side-panel");
        panel.setPrefWidth(280);
        panel.setMinWidth(280);
        return panel;
    }

    // =========================================================================
    // Wiring
    // =========================================================================

    private void wireCallbacks() {
        pipelinePanel.setOnPipelineChanged(renderService::scheduleRender);

        pipelinePanel.setOnEntrySelected(transform -> {
            if (transform == null) {
                paramControls.clear();
            } else {
                paramControls.show(transform);
            }
        });

        renderService.setTransformsSupplier(pipelinePanel::enabledTransforms);

        renderService.setOnResult(result -> {
            lastResult = result;
            workspace.showResult(result);
        });

        renderService.setOnBusy(workspace::setBusy);
    }

    // =========================================================================
    // Actions
    // =========================================================================

    private void openImage() {
        try {
            BufferedImage image = imageLoader.openWithDialog(stage);

            if (image == null) return;

            workspace.setOriginalImage(image);
            renderService.setOriginalImage(image);

        } catch (IOException ex) {
            showError("Could not open image", ex.getMessage());
        }
    }

    private void exportPng() {
        if (lastResult == null) {
            showInfo("Nothing to export yet.", "Open an image and apply a transform first.");
            return;
        }

        try {
            pngExporter.exportWithDialog(lastResult.image(), stage);
        } catch (IOException ex) {
            showError("Export failed", ex.getMessage());
        }
    }

    private void exportTxt() {
        if (lastResult == null || !lastResult.hasAscii()) {
            showInfo("No ASCII result.", "Add an ASCII Art transform to the pipeline first.");
            return;
        }

        try {
            txtExporter.exportWithDialog(lastResult.ascii(), stage);
        } catch (IOException ex) {
            showError("Export failed", ex.getMessage());
        }
    }

    // =========================================================================
    // Dialogs
    // =========================================================================

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
