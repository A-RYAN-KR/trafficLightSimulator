package org.example.smarttrafficlight.gui;

import org.example.smarttrafficlight.model.*;
import org.example.smarttrafficlight.service.Intersection;
import org.example.smarttrafficlight.service.SimulationEngine;
import org.example.smarttrafficlight.service.TelegramBotHandler; // Import the bot

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.collections.FXCollections;

import java.util.Map;
import java.util.stream.Collectors;


public class MainController {

    // --- FXML Injected Fields ---
    @FXML private Circle lightNorthRed, lightNorthYellow, lightNorthGreen;
    @FXML private Circle lightSouthRed, lightSouthYellow, lightSouthGreen;
    @FXML private Circle lightEastRed, lightEastYellow, lightEastGreen;
    @FXML private Circle lightWestRed, lightWestYellow, lightWestGreen;

    @FXML private ComboBox<VehicleType> vehicleTypeCombo;
    @FXML private ComboBox<Direction> directionCombo;

    @FXML private Button startButton, stopButton;
    @FXML private TextArea logTextArea; // For general logs maybe

    @FXML private Label queueNorthCount, queueSouthCount, queueEastCount, queueWestCount;
    @FXML private ListView<String> queueNorthList, queueSouthList, queueEastList, queueWestList;


    // --- Simulation Components ---
    private Intersection intersection;
    private SimulationEngine simulationEngine;
    private TelegramBotHandler telegramBot; // Add bot reference

    // --- Initialization ---
    @FXML
    public void initialize() {
        // *** IMPORTANT: Replace with your actual bot credentials and Chat ID ***
        String botUsername = "jashtraffic2bot"; // e.g., MyTrafficSimBot
        String botToken = "7979935037:AAHwb0ngwacJy7RcrON-dq-mL_F3NomlUjU"; // The token from BotFather
        String chatId = "6314336691";         // The chat ID where messages should be sent

        if (botUsername.isEmpty() || botToken.isEmpty() || chatId.isEmpty()) {
            System.err.println("Telegram Bot credentials are missing!");
            telegramBot = null;
            showAlert(Alert.AlertType.WARNING, "Telegram Not Configured",
                    "Please set your Bot Username, Token, and Chat ID in MainController.java to enable Telegram features.");
        } else {
            this.telegramBot = new TelegramBotHandler(botUsername, botToken, chatId);
            this.telegramBot.registerBot();
        }

        this.intersection = new Intersection();
        // Pass the bot instance to the engine
        this.simulationEngine = new SimulationEngine(intersection, this.telegramBot);

        // Set up the controller as the listener for simulation updates
        this.simulationEngine.setUpdateListener(this::updateUI);

        // Populate ComboBoxes
        vehicleTypeCombo.setItems(FXCollections.observableArrayList(VehicleType.values()));
        vehicleTypeCombo.setValue(VehicleType.CAR); // Default selection
        directionCombo.setItems(FXCollections.observableArrayList(Direction.values()));
        directionCombo.setValue(Direction.NORTH); // Default selection

        // Initialize UI elements
        updateUI(intersection); // Initial UI state
        stopButton.setDisable(true);
        startButton.setDisable(false);

        // Setup console redirection to TextArea (optional)
        // ConsoleRedirector.redirectSystemOutToTextArea(logTextArea); // See helper class below
        logTextArea.appendText("Simulation ready. Please configure Telegram Bot details if needed.\n");
    }

    // --- FXML Action Handlers ---
    @FXML
    private void startSimulation() {
        simulationEngine.startSimulation();
        startButton.setDisable(true);
        stopButton.setDisable(false);
        logTextArea.appendText("Simulation started.\n");
    }

    @FXML
    private void stopSimulation() {
        simulationEngine.stopSimulation();
        startButton.setDisable(false);
        stopButton.setDisable(true);
        logTextArea.appendText("Simulation stopped.\n");
    }

    @FXML
    private void addVehicle() {
        VehicleType type = vehicleTypeCombo.getValue();
        Direction dir = directionCombo.getValue();

        if (type != null && dir != null) {
            Vehicle vehicle = new Vehicle(type, dir);
            intersection.addVehicle(vehicle);
            logTextArea.appendText("Manually added: " + vehicle + "\n");
            updateUI(intersection); // Update UI immediately after adding
            if (vehicle.isEmergencyVehicle() && telegramBot != null) {
                telegramBot.sendMessage("Manual Add: 🚨 " + vehicle + " added to " + dir + " queue.");
            }

        } else {
            showAlert(Alert.AlertType.WARNING, "Input Error", "Please select both a vehicle type and a direction.");
        }
    }

    // --- UI Update Logic ---
    private void updateUI(Intersection currentIntersectionState) {
        if (currentIntersectionState == null) return; // Safety check

        // Update Traffic Lights
        updateLightCircle(currentIntersectionState.getLight(Direction.NORTH), lightNorthRed, lightNorthYellow, lightNorthGreen);
        updateLightCircle(currentIntersectionState.getLight(Direction.SOUTH), lightSouthRed, lightSouthYellow, lightSouthGreen);
        updateLightCircle(currentIntersectionState.getLight(Direction.EAST), lightEastRed, lightEastYellow, lightEastGreen);
        updateLightCircle(currentIntersectionState.getLight(Direction.WEST), lightWestRed, lightWestYellow, lightWestGreen);

        // Update Queue Counts and Lists
        updateQueueDisplay(Direction.NORTH, queueNorthCount, queueNorthList, currentIntersectionState);
        updateQueueDisplay(Direction.SOUTH, queueSouthCount, queueSouthList, currentIntersectionState);
        updateQueueDisplay(Direction.EAST, queueEastCount, queueEastList, currentIntersectionState);
        updateQueueDisplay(Direction.WEST, queueWestCount, queueWestList, currentIntersectionState);

        // Could add more info to logTextArea if needed, e.g., current simulation time/mode
    }


    private void updateLightCircle(TrafficLight light, Circle red, Circle yellow, Circle green) {
        red.setFill(Color.DARKGREY);
        yellow.setFill(Color.DARKGREY);
        green.setFill(Color.DARKGREY);

        switch (light.getState()) {
            case RED:
                red.setFill(Color.RED);
                break;
            case YELLOW:
                yellow.setFill(Color.YELLOW);
                break;
            case GREEN:
                green.setFill(Color.LIMEGREEN); // Use a bright green
                break;
        }
    }

    private void updateQueueDisplay(Direction dir, Label countLabel, ListView<String> listView, Intersection state) {
        int queueSize = state.getQueueSize(dir);
        countLabel.setText("Count: " + queueSize);

        // Get first few vehicles for display (limit for performance)
        int previewCount = 5; // Show top 5 vehicles in the list
        var vehiclePreview = state.getQueuePreview(dir, previewCount)
                .stream()
                .map(Vehicle::toString) // Convert Vehicle to String
                .collect(Collectors.toList());

        listView.setItems(FXCollections.observableArrayList(vehiclePreview));
    }

    // --- Helper Methods ---
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // Optional: Call this when the application closes to stop the bot gracefully
    public void shutdown() {
        stopSimulation(); // Stop the simulation loop
        // You might need to explicitly shutdown the bot's threads if necessary,
        // though DefaultBotSession often handles this. Consult telegrambots docs if needed.
        System.out.println("Application shutting down.");
    }
}
