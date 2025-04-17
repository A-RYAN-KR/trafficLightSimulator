package org.example.smarttrafficlight.service;

import org.example.smarttrafficlight.model.*; // Import all models
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;

import java.util.Optional;
import java.util.function.Consumer; // For callbacks

public class SimulationEngine {

    private final Intersection intersection;
    private final Timeline simulationLoop;
    private final TelegramBotHandler telegramBot; // Add reference to the bot

    // --- Time Constants ---
    private static final long NORMAL_GREEN_TIME_MS = 10000; // 10 seconds
    private static final long YELLOW_TIME_MS = 2000;      // 2 seconds
    private static final long PRIORITY_OVERRIDE_TIME_MS = 8000; // Max Green time for emergency

    // --- State Variables ---
    private Direction currentGreenDirection = Direction.NORTH; // Represents the N/S pair initially
    private long phaseStartTime;

    // State machine for priority override
    private enum PriorityState { IDLE, YELLOW_TRANSITION, GREEN_ACTIVE, ENDING_YELLOW }
    private PriorityState currentPriorityState = PriorityState.IDLE;
    private Direction priorityDirection = null; // Direction needing priority override

    // Callback to notify GUI/other components of updates
    private Consumer<Intersection> updateListener;

    public SimulationEngine(Intersection intersection, TelegramBotHandler telegramBot) {
        this.intersection = intersection;
        this.telegramBot = telegramBot; // Store the bot reference

        // Simulation loop using JavaFX Timeline (runs on FX thread)
        simulationLoop = new Timeline(new KeyFrame(Duration.seconds(1), event -> runSimulationStep()));
        simulationLoop.setCycleCount(Timeline.INDEFINITE);
    }

    public void setUpdateListener(Consumer<Intersection> listener) {
        this.updateListener = listener;
    }

    public void startSimulation() {
        // Set initial state (N/S Green)
        setRedPair(Direction.EAST); // Explicitly set E/W red first
        setGreenPair(Direction.NORTH);
        currentGreenDirection = Direction.NORTH;
        currentPriorityState = PriorityState.IDLE;
        priorityDirection = null;
        phaseStartTime = System.currentTimeMillis();
        simulationLoop.play();
        System.out.println("Simulation Started.");
        if (telegramBot != null) {
            telegramBot.sendMessage("Simulation Started. Initial state: N/S Green.");
        }
    }

    public void stopSimulation() {
        simulationLoop.stop();
        System.out.println("Simulation Stopped.");
        if (telegramBot != null) {
            telegramBot.sendMessage("Simulation Stopped.");
        }
    }

    private void runSimulationStep() {
        long now = System.currentTimeMillis();
        long elapsedTime = now - phaseStartTime;

        // --- 1. Check for New Priority Vehicles (Only if IDLE) ---
        if (currentPriorityState == PriorityState.IDLE) {
            Optional<Direction> priorityRequest = intersection.checkForPriorityVehicle();
            if (priorityRequest.isPresent()) {
                // New priority request!
                priorityDirection = priorityRequest.get();
                System.out.println(">>> EMERGENCY OVERRIDE ACTIVATED for " + priorityDirection + " <<<");
                if (telegramBot != null) {
                    telegramBot.sendMessage("🚨 Emergency vehicle detected from " + priorityDirection + "! Prioritizing traffic light.");
                }

                // Start yellow transition for conflicting lights
                boolean yellowStarted = startYellowTransitionForPriority(priorityDirection);

                if (yellowStarted) {
                    currentPriorityState = PriorityState.YELLOW_TRANSITION;
                    phaseStartTime = now; // Start YELLOW timer
                } else {
                    // No conflicting lights were green/yellow, can go straight to green
                    System.out.println("No conflicting traffic, setting " + priorityDirection + " pair to GREEN immediately.");
                    // Ensure others are red (might be redundant but safe)
                    setRedPair(getOrthogonalDirection(priorityDirection));
                    // Set priority direction GREEN
                    setGreenPair(priorityDirection);
                    currentPriorityState = PriorityState.GREEN_ACTIVE;
                    phaseStartTime = now; // Start GREEN timer
                }
            }
        }

        // --- 2. Handle Active Priority Override State Machine ---
        if (currentPriorityState != PriorityState.IDLE) {
            TrafficLight priorityLight = intersection.getLight(priorityDirection); // Get the specific light instance

            switch (currentPriorityState) {
                case YELLOW_TRANSITION:
                    if (elapsedTime >= YELLOW_TIME_MS) {
                        System.out.println("Priority Yellow phase finished for conflicting lights.");
                        // Set conflicting lights to RED
                        setRedPairBasedOnPriority(priorityDirection);
                        // Set priority light pair to GREEN
                        setGreenPair(priorityDirection);
                        System.out.println("Setting " + priorityDirection + " pair to GREEN for priority.");
                        currentPriorityState = PriorityState.GREEN_ACTIVE;
                        phaseStartTime = now; // Reset timer for GREEN phase
                    }
                    // else: Still waiting for yellow timer
                    break;

                case GREEN_ACTIVE:
                    // Check if the specific emergency vehicle is still at the front
                    boolean emergencyVehiclePresent = intersection.peekNextVehicle(priorityDirection)
                            .map(Vehicle::isEmergencyVehicle)
                            .orElse(false);

                    // End priority if time is up OR the emergency vehicle is gone
                    if (elapsedTime >= PRIORITY_OVERRIDE_TIME_MS || !emergencyVehiclePresent) {
                        if (!emergencyVehiclePresent) {
                            System.out.println("Emergency vehicle from " + priorityDirection + " appears to have passed.");
                        } else {
                            System.out.println("Priority GREEN time expired for " + priorityDirection + ".");
                        }
                        System.out.println(">>> EMERGENCY OVERRIDE ENDING for " + priorityDirection + " <<<");

                        // Start Yellow phase for the priority direction pair
                        setYellowPair(priorityDirection);
                        currentPriorityState = PriorityState.ENDING_YELLOW;
                        phaseStartTime = now; // Reset timer for ending yellow phase
                        if (telegramBot != null) {
                            telegramBot.sendMessage("✅ Emergency vehicle passed/priority time ended for " + priorityDirection + ". Resuming normal flow soon.");
                        }

                        // Remove the vehicle explicitly if it wasn't handled by processGreenLightQueues
                        // This ensures it's gone before we check again in IDLE state
                        intersection.peekNextVehicle(priorityDirection)
                                .filter(Vehicle::isEmergencyVehicle)
                                .ifPresent(v -> {
                                    System.out.println("Explicitly removing " + v + " after priority green phase.");
                                    intersection.getNextVehicle(priorityDirection);
                                });
                    }
                    // else: Still in Green phase
                    break;

                case ENDING_YELLOW:
                    if (elapsedTime >= YELLOW_TIME_MS) {
                        System.out.println("Priority ending Yellow phase finished for " + priorityDirection + " pair.");
                        // Set priority direction pair to RED
                        setRedPair(priorityDirection);
                        // Reset state and potentially go back to a default light state
                        currentPriorityState = PriorityState.IDLE;
                        priorityDirection = null; // Clear the priority direction

                        // Resume normal cycle - Decide which direction should be green next
                        // For simplicity, just go back to N/S green as default after E/W priority
                        // Or switch to the orthogonal direction of the priority one
                        currentGreenDirection = getOrthogonalDirection(intersection.getLight(currentGreenDirection).getDirection()); // Switch to the other pair
                        System.out.println("Resuming normal traffic flow. Setting " + currentGreenDirection + " pair to GREEN.");
                        setGreenPair(currentGreenDirection);
                        phaseStartTime = now; // Reset timer for normal green phase

                        if (telegramBot != null) {
                            telegramBot.sendMessage("🚦 Normal traffic flow resumed ("+currentGreenDirection+"/"+getOpposingDirection(currentGreenDirection)+" Green).");
                        }
                    }
                    // else: Still waiting for ending yellow timer
                    break;
            }
        } else {
            // --- 3. Normal Traffic Light Cycle (Only runs if currentPriorityState is IDLE) ---
            TrafficLight currentPairLight = intersection.getLight(currentGreenDirection); // Check one of the pair

            if (currentPairLight.getState() == TrafficLightState.GREEN && elapsedTime >= NORMAL_GREEN_TIME_MS) {
                // Time to switch, start Yellow phase for the current green pair
                System.out.println("Normal cycle: Green time ended for " + currentGreenDirection + " pair. Starting Yellow.");
                setYellowPair(currentGreenDirection);
                phaseStartTime = now;
            } else if (currentPairLight.getState() == TrafficLightState.YELLOW && elapsedTime >= YELLOW_TIME_MS) {
                // Yellow finished for the current pair
                System.out.println("Normal cycle: Yellow time ended for " + currentGreenDirection + " pair.");
                // Set current pair to Red
                setRedPair(currentGreenDirection);
                // Switch focus to the other pair
                currentGreenDirection = getOrthogonalDirection(currentGreenDirection);
                // Set new pair to Green
                System.out.println("Normal cycle: Setting " + currentGreenDirection + " pair to GREEN.");
                setGreenPair(currentGreenDirection);
                phaseStartTime = now; // Reset timer for the new Green phase
            }
            // else: Still in Green or Red phase, just wait.
        }

        // --- 4. Process queues for GREEN lights (respects priority) ---
        // Pass the direction that has priority green, or null if none (normal operation)
        processGreenLightQueues(currentPriorityState == PriorityState.GREEN_ACTIVE ? priorityDirection : null);

        // --- 5. Notify Listener (e.g., GUI) ---
        if (updateListener != null) {
            // Ensure GUI updates happen on the JavaFX Application Thread
            final Intersection currentState = this.intersection; // Capture current state for lambda
            Platform.runLater(() -> updateListener.accept(currentState));
        }
    }

    // --- Helper Methods for Light Changes ---

    private void setGreenPair(Direction dir) {
        intersection.setLightState(dir, TrafficLightState.GREEN);
        intersection.setLightState(getOpposingDirection(dir), TrafficLightState.GREEN);
    }

    private void setYellowPair(Direction dir) {
        // Only change if currently green
        if (intersection.getLight(dir).getState() == TrafficLightState.GREEN) {
            intersection.setLightState(dir, TrafficLightState.YELLOW);
        }
        if (intersection.getLight(getOpposingDirection(dir)).getState() == TrafficLightState.GREEN) {
            intersection.setLightState(getOpposingDirection(dir), TrafficLightState.YELLOW);
        }
    }

    private void setRedPair(Direction dir) {
        intersection.setLightState(dir, TrafficLightState.RED);
        intersection.setLightState(getOpposingDirection(dir), TrafficLightState.RED);
    }

    private Direction getOpposingDirection(Direction dir) {
        switch (dir) {
            case NORTH:
                return Direction.SOUTH;
            case SOUTH:
                return Direction.NORTH;
            case EAST:
                return Direction.WEST;
            case WEST:
                return Direction.EAST;
            default:
                throw new IllegalArgumentException();
        }
        // Default case removed as Direction enum should cover all possibilities
    }

    // Gets the primary direction of the pair orthogonal to the given direction's pair
    // e.g., NORTH/SOUTH -> EAST, EAST/WEST -> NORTH
    private Direction getOrthogonalDirection(Direction dir) {
        switch (dir) {
            case NORTH:
            case SOUTH:
                return Direction.EAST;
            case EAST:
            case WEST:
                return Direction.NORTH;
            default:
                throw new IllegalArgumentException();
        }
    }


    // Helper to start the yellow transition for conflicting lights during priority activation
    private boolean startYellowTransitionForPriority(Direction priorityDir) {
        boolean yellowStarted = false;
        // Find the direction pair *orthogonal* to the priority direction's pair
        Direction orthogonalDir = getOrthogonalDirection(priorityDir);

        // Check if the orthogonal pair is currently Green or Yellow
        TrafficLight light1 = intersection.getLight(orthogonalDir);
        TrafficLight light2 = intersection.getLight(getOpposingDirection(orthogonalDir));

        if (light1.getState() == TrafficLightState.GREEN || light2.getState() == TrafficLightState.GREEN) {
            System.out.println("Starting Yellow phase for conflicting pair: " + orthogonalDir + "/" + getOpposingDirection(orthogonalDir));
            setYellowPair(orthogonalDir); // This helper now correctly checks if they are green before setting yellow
            yellowStarted = true;
        } else if (light1.getState() == TrafficLightState.YELLOW || light2.getState() == TrafficLightState.YELLOW) {
            System.out.println("Conflicting pair " + orthogonalDir + "/" + getOpposingDirection(orthogonalDir) + " already Yellow.");
            yellowStarted = true; // Already in yellow transition
        }

        // Ensure the priority direction pair itself is RED initially if it wasn't already (safety check)
        if(intersection.getLight(priorityDir).getState() != TrafficLightState.RED ||
                intersection.getLight(getOpposingDirection(priorityDir)).getState() != TrafficLightState.RED) {
            setRedPair(priorityDir);
            System.out.println("Warning: Priority direction pair wasn't RED. Forced RED.");
        }
        return yellowStarted;
    }

    // Helper to set the lights (that were yellowing for priority) to red
    private void setRedPairBasedOnPriority(Direction priorityDir) {
        Direction orthogonalDir = getOrthogonalDirection(priorityDir);
        System.out.println("Setting conflicting pair " + orthogonalDir + "/" + getOpposingDirection(orthogonalDir) + " to RED.");
        setRedPair(orthogonalDir);
    }


    // Modified processGreenLightQueues to handle priority
    private void processGreenLightQueues(Direction activePriorityDirection) {
        // Simple model: Allow one vehicle per step if conditions met
        for (Direction dir : Direction.values()) {
            if (intersection.getLight(dir).getState() == TrafficLightState.GREEN) {
                Optional<Vehicle> vehicleOpt = intersection.peekNextVehicle(dir);
                if (vehicleOpt.isPresent()) {
                    Vehicle vehicle = vehicleOpt.get();
                    boolean isEmergency = vehicle.isEmergencyVehicle();
                    boolean canPass = false;

                    if (activePriorityDirection != null) {
                        // --- Priority is ACTIVE ---
                        // Only let the emergency vehicle from the *specific* priority direction pass
                        if (dir == activePriorityDirection && isEmergency) {
                            canPass = true;
                            System.out.println("PRIORITY PASS: Allowing " + vehicle);
                        } else if (dir == activePriorityDirection /* && !isEmergency */) {
                            // Optional: Block regular cars even if light is green in priority direction? Yes.
                            // System.out.println("PRIORITY BLOCK: Normal vehicle " + vehicle + " waiting during priority GREEN for " + dir);
                        } else {
                            // Other green lights (shouldn't happen if logic is right, but safety)
                            // System.out.println("PRIORITY BLOCK: Vehicle " + vehicle + " waiting at other GREEN light " + dir);
                        }
                    } else {
                        // --- Normal Operation (No active priority) ---
                        // Let non-emergency vehicles pass
                        if (!isEmergency) {
                            canPass = true;
                            System.out.println("NORMAL PASS: Allowing " + vehicle);
                        } else {
                            // Emergency vehicle waiting at a normal green light - it should trigger priority soon
                            System.out.println("NORMAL BLOCK: Emergency vehicle " + vehicle + " detected at GREEN light " + dir + ", waiting for priority trigger.");
                        }
                    }

                    if (canPass) {
                        Vehicle passedVehicle = intersection.getNextVehicle(dir).orElse(null);
                        // Log is now done before removal check for clarity
                        // if (passedVehicle != null) { System.out.println("Vehicle passed: " + passedVehicle); }
                    }
                }
            }
        }
    }
}