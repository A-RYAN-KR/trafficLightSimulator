package org.example.smarttrafficlight;

import org.example.smarttrafficlight.gui.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;


public class MainApp extends Application {

    private MainController controller; // Keep a reference to the controller

    @Override
    public void start(Stage primaryStage) {
        try {
            // Load the FXML file relative to the resources folder structure
            // Ensure the path matches your package structure under resources
            URL fxmlUrl = getClass().getResource("/com/example/smarttrafficlight/MainView.fxml");
            if (fxmlUrl == null) {
                System.err.println("Cannot load FXML file. Check the path!");
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load(); // Load the FXML

            // Get the controller instance created by the FXMLLoader
            controller = loader.getController();

            Scene scene = new Scene(root);

            primaryStage.setTitle("Smart Traffic Light Simulation");
            primaryStage.setScene(scene);
            primaryStage.setOnCloseRequest(event -> {
                // Ensure graceful shutdown
                if (controller != null) {
                    controller.shutdown();
                }
                // Platform.exit(); // Usually called implicitly
                // System.exit(0); // Force exit if needed
            });

            primaryStage.show();

        } catch (IOException e) {
            System.err.println("Error loading FXML or starting application:");
            e.printStackTrace();
            // Show a simple error dialog to the user
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Application Error");
            alert.setHeaderText("Failed to start the simulation GUI.");
            alert.setContentText("Could not load the interface file (MainView.fxml). Please check application resources.\nError: " + e.getMessage());
            alert.showAndWait();
        } catch (Exception e) {
            System.err.println("An unexpected error occurred:");
            e.printStackTrace();
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Unexpected Error");
            alert.setHeaderText("An unexpected error stopped the application.");
            alert.setContentText("Error: " + e.getMessage());
            alert.showAndWait();
        }
    }

    public static void main(String[] args) {
        // Launch the JavaFX application
        // This will internally call the start() method
        launch(args);
    }
}