package bg.transformit.io;


import javafx.stage.FileChooser;
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;


/** Saves a {@link BufferedImage} as a PNG file via the native save dialog. */
public class PngExporter {

    private static final FileChooser.ExtensionFilter PNG_FILTER =
            new FileChooser.ExtensionFilter("PNG Image (*.png)", "*.png");


    /**
     * Shows the native Save-file dialog and writes the image at full resolution.
     *
     * <p>Returns {@code true} if the file was saved, {@code false} if the user
     * cancelled.
     *
     * @throws IOException if writing fails
     */
    public boolean exportWithDialog(BufferedImage image, Window owner) throws IOException {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export as PNG");
        chooser.getExtensionFilters().add(PNG_FILTER);
        chooser.setInitialFileName("transformit-export.png");

        File file = chooser.showSaveDialog(owner);

        if (file == null) return false;

        File target = ensurePngExtension(file);
        ImageIO.write(image, "png", target);
        return true;
    }

    // -------------------------------------------------------------------------

    private File ensurePngExtension(File file) {
        String name = file.getName();
        return name.toLowerCase().endsWith(".png")
                ? file
                : new File(file.getParentFile(), name + ".png");
    }
}
