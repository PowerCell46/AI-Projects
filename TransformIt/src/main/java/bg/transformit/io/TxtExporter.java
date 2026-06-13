package bg.transformit.io;


import bg.transformit.core.transforms.AsciiResult;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;


/** Saves the raw ASCII character grid as a plain-text file. */
public class TxtExporter {

    private static final FileChooser.ExtensionFilter TXT_FILTER =
            new FileChooser.ExtensionFilter("Text File (*.txt)", "*.txt");


    /**
     * Shows the native Save-file dialog and writes the character grid.
     *
     * @return {@code true} if saved, {@code false} if the user cancelled
     * @throws IOException if writing fails
     */
    public boolean exportWithDialog(AsciiResult ascii, Window owner) throws IOException {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export ASCII Art as TXT");
        chooser.getExtensionFilters().add(TXT_FILTER);
        chooser.setInitialFileName("ascii-art.txt");

        File file = chooser.showSaveDialog(owner);

        if (file == null) return false;

        try (PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8)) {
            writer.print(ascii.toText());
        }

        return true;
    }
}
