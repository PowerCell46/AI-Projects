package bg.transformit;


import bg.transformit.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;


/** JavaFX entry point — keeps this class as thin as possible. */
public class App extends Application {

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
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
