package bg.transformit.io;


import javafx.stage.FileChooser;
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;


/** Opens an image file via the native file-picker and decodes it with {@link ImageIO}. */
public class ImageLoader {

    private static final FileChooser.ExtensionFilter IMAGE_FILTER =
            new FileChooser.ExtensionFilter(
                    "Images (png, jpg, bmp, gif)",
                    "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"
            );


    /**
     * Shows the native Open-file dialog and returns the decoded image, or
     * {@code null} if the user cancelled.
     *
     * @throws IOException if the chosen file cannot be read or decoded
     */
    public BufferedImage openWithDialog(Window owner) throws IOException {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Image");
        chooser.getExtensionFilters().add(IMAGE_FILTER);

        File file = chooser.showOpenDialog(owner);

        if (file == null) return null;

        BufferedImage image = ImageIO.read(file);

        if (image == null) {
            throw new IOException("Unsupported or corrupt image: " + file.getName());
        }

        return image;
    }
}
