package bg.transformit;


import bg.transformit.ui.MainView;
import bg.transformit.ui.NativeTitleBarDarkMode;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;


/** JavaFX entry point — keeps this class as thin as possible. */
public class App extends Application {

    private static final String APP_ICON = "/icons/icon_two.png";


    @Override
    public void start(Stage primaryStage) {
        MainView root = new MainView(primaryStage);

        Scene scene = new Scene(root, 1200, 750);
        scene.getStylesheets().add(
                getClass().getResource("/css/transformit.css").toExternalForm()
        );

        primaryStage.setTitle("TransformIt");
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(550);
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream(APP_ICON)));
        primaryStage.setScene(scene);
        primaryStage.show();

        // Must run after show() — the native window handle only exists once shown.
        NativeTitleBarDarkMode.apply();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
