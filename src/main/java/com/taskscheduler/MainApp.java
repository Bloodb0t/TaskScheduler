package com.taskscheduler;

import com.taskscheduler.controller.MainController;
import com.taskscheduler.controller.StatsController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader mainLoader = loadFxml("/com/taskscheduler/view/main-view.fxml");
            Parent mainRoot = loadFromLoader(mainLoader, "/com/taskscheduler/view/main-view.fxml");
            MainController mainCtrl = mainLoader.getController();

            Scene mainScene = new Scene(mainRoot);
            mainScene.getStylesheets().add(styleSheetUrl());

            FXMLLoader statsLoader = loadFxml("/com/taskscheduler/view/stats-view.fxml");
            Parent statsRoot = loadFromLoader(statsLoader, "/com/taskscheduler/view/stats-view.fxml");
            StatsController statsCtrl = statsLoader.getController();

            Scene statsScene = new Scene(statsRoot);
            statsScene.getStylesheets().add(styleSheetUrl());

            mainCtrl.setPrimaryStage(primaryStage);
            mainCtrl.setMainScene(mainScene);
            mainCtrl.setStatsScene(statsScene, statsCtrl);

            primaryStage.setTitle("Task Scheduler — Desktop Manager");
            primaryStage.setWidth(1160);
            primaryStage.setHeight(620);
            primaryStage.setMinWidth(900);
            primaryStage.setMinHeight(540);
            trySetAppIcon(primaryStage);

            primaryStage.setOnCloseRequest(event -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Exit the Task Scheduler?", ButtonType.YES, ButtonType.NO);
                confirm.setTitle("Confirm Exit");
                confirm.setHeaderText(null);
                confirm.initOwner(primaryStage);
                confirm.showAndWait().ifPresent(choice -> {
                    if (choice == ButtonType.YES) {
                        try {
                            mainCtrl.shutdownApp();
                        } catch (Exception ignored) {
                        }
                        Platform.exit();
                    } else {
                        event.consume();
                    }
                });
            });

            primaryStage.setScene(mainScene);
            primaryStage.show();

        } catch (IOException e) {
            showFatalErrorAndExit("Failed to start application", e);
        }
    }

    private FXMLLoader loadFxml(String resourcePath) {
        URL location = getClass().getResource(resourcePath);
        if (location == null) {
            throw new IllegalStateException("FXML resource not found: " + resourcePath);
        }
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(location);
        loader.setClassLoader(getClass().getClassLoader());
        return loader;
    }

    private Parent loadFromLoader(FXMLLoader loader, String resourcePath) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Could not open InputStream for FXML: " + resourcePath);
            }
            return loader.load(in);
        }
    }

    private String styleSheetUrl() {
        URL css = getClass().getResource("/com/taskscheduler/style.css");
        if (css == null) {
            throw new IllegalStateException("Stylesheet resource not found: /com/taskscheduler/style.css");
        }
        return css.toExternalForm();
    }

    private void trySetAppIcon(Stage stage) {
        try (InputStream in = getClass().getResourceAsStream("/com/taskscheduler/icon.png")) {
            if (in != null) {
                stage.getIcons().add(new Image(in));
            }
        } catch (Exception ignored) {
        }
    }

    private static void showFatalErrorAndExit(String header, Exception e) {
        e.printStackTrace();
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    header + ": " + e.getMessage() + "\n\nThe application will now close.",
                    ButtonType.OK);
            alert.setTitle("Fatal Error");
            alert.setHeaderText(null);
            alert.showAndWait();
            Platform.exit();
        });
    }

    @Override
    public void init() {
    }

    @Override
    public void stop() {
    }

    public static void main(String[] args) {
        launch(args);
    }
}
