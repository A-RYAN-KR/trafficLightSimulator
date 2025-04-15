package org.example.smarttrafficlight.service;

import org.example.smarttrafficlight.model.Direction;
import org.example.smarttrafficlight.model.TrafficLight;
import org.example.smarttrafficlight.model.TrafficLightState;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;
import org.example.smarttrafficlight.model.Vehicle;

import java.util.Optional;
import java.util.function.Consumer; // For callbacks

public class SimulationEngine {

    private final Intersection intersection;
    private final Timeline simulationLoop;
    private final TelegramBotHandler telegramBot; // Add reference to the bot

    private static final long NORMAL_GREEN_TIME_MS = 10000; // 10 seconds
    private static final long YELLOW_TIME_MS = 2000;      // 2 seconds
    private static final long PRIORITY_OVERRIDE_TIME_MS = 8000; // Green time for emergency

    private Direction currentGreenDirection = Direction.NORTH; // Or pair N/S
    private long phaseStartTime;
    private boolean priorityActive = false;
    private Direction priorityDirection = null;

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
        // Set initial state (e.g., N/S Green)
        intersection.setLightState(Direction.NORTH, TrafficLightState.GREEN);
        intersection.setLightState(Direction.SOUTH, TrafficLightState.GREEN);
        intersection.setLightState(Direction.EAST, TrafficLightState.RED);
        intersection.setLightState(Direction.WEST, TrafficLightState.RED);
        currentGreenDirection = Direction.NORTH; // Represents the N/S pair
        phaseStartTime = System.currentTimeMillis();
        priorityActive = false;
        simulationLoop.play();
        System.out.println("Simulation Started.");
    }

    public void stopSimulation() {
        simulationLoop.stop();
        System.out.println("Simulation Stopped.");
    }

    private void runSimulationStep() {
        long now = System.currentTimeMillis();
        long elapsedTime = now - phaseStartTime;

        // --- 1. Check for Priority Vehicles ---
        Optional<Direction> priorityRequest = intersection.checkForPriorityVehicle();

        if (priorityRequest.isPresent() && !priorityActive) {
            // New priority request!
            priorityActive = true;
            priorityDirection = priorityRequest.get();
            System.out.println(">>> EMERGENCY OVERRIDE ACTIVATED for " + priorityDirection + " <<<");

            // Notify Telegram
            telegramBot.sendMessage("🚨 Emergency vehicle detected from " + priorityDirection + "! Prioritizing traffic light.");

            // Immediately start transition to green for priority direction
            transitionToGreen(priorityDirection);
            phaseStartTime = now; // Reset phase timer for priority override

        } else if (priorityActive) {
            // --- 2. Handle Active Priority Override ---
            TrafficLight priorityLight = intersection.getLight(priorityDirection);
            if (priorityLight.getState() == TrafficLightState.GREEN && elapsedTime >= PRIORITY_OVERRIDE_TIME_MS) {
                // Priority time is up, or vehicle passed (more complex check needed for passed)
                System.out.println(">>> EMERGENCY OVERRIDE ENDING for " + priorityDirection + " <<<");

                // Let the priority vehicle pass (remove from queue)
                intersection.getNextVehicle(priorityDirection); // Consume the priority vehicle

                priorityActive = false;
                priorityDirection = null;
                // Transition back to normal cycle (e.g., start yellow for the priority direction)
                transitionToYellow(getOpposingDirection(currentGreenDirection)); // Start yellow for previously green lights
                phaseStartTime = now;

                telegramBot.sendMessage("✅ Emergency vehicle passed. Resuming normal traffic flow.");

            } else if (priorityLight.getState() == TrafficLightState.YELLOW && elapsedTime >= YELLOW_TIME_MS) {
                // Yellow phase during priority override finished
                priorityLight.setState(TrafficLightState.RED);
                // Set the priority direction to green
                setGreenPair(priorityDirection);
                phaseStartTime = now; // Reset timer for green phase
            }
            // else: Still in Red->Yellow or Green phase of priority override, just wait.

        } else {
            // --- 3. Normal Traffic Light Cycle ---
            TrafficLight currentLight = intersection.getLight(currentGreenDirection); // Check one of the pair

            if (currentLight.getState() == TrafficLightState.GREEN && elapsedTime >= NORMAL_GREEN_TIME_MS) {
                // Time to switch, start Yellow phase
                transitionToYellow(currentGreenDirection);
                phaseStartTime = now;
            } else if (currentLight.getState() == TrafficLightState.YELLOW && elapsedTime >= YELLOW_TIME_MS) {
                // Yellow finished, switch to Red and make the other direction Green
                setRedPair(currentGreenDirection); // Set current pair to Red
                currentGreenDirection = getOpposingDirection(currentGreenDirection); // Switch focus
                setGreenPair(currentGreenDirection); // Set new pair to Green
                phaseStartTime = now; // Reset timer for the new Green phase
            }
            // else: Still in Green or Red phase, just wait.
        }

        // --- 4. Process queues for GREEN lights (simple version) ---
        processGreenLightQueues();


        // --- 5. Notify Listener (e.g., GUI) ---
        if (updateListener != null) {
            // Ensure GUI updates happen on the JavaFX Application Thread
            Platform.runLater(() -> updateListener.accept(intersection));
        }
    }

    // --- Helper Methods for Light Changes ---

    private void transitionToGreen(Direction targetDirection) {
        // Make all lights RED first (safest approach)
        for (Direction dir : Direction.values()) {
            if(intersection.getLight(dir).getState() != TrafficLightState.RED) {
                intersection.setLightState(dir, TrafficLightState.YELLOW); // Go yellow if not red
            }
        }
        // We'll handle the actual switch in the next simulation step based on timer/priority state
        // This simplified version directly sets yellow if needed, but a timed approach is better
        // For priority, we might set yellow for others, then red, then green for priority dir
        System.out.println("Transitioning towards green for " + targetDirection);
        // In a real system, you'd wait for yellow/red phases before setting green.
        // For simplicity here, we'll let the priority logic handle the next step.
        // If not in priority mode, this would trigger the normal yellow->red->green sequence.

        // If handling priority, the next step in runSimulationStep will manage the timing.
        if(priorityActive && targetDirection.equals(priorityDirection)) {
            // If a light is currently green/yellow, make it yellow then red before target goes green.
            // This needs refinement based on exact timing desired.
            boolean needsYellow = false;
            for(Direction d : Direction.values()) {
                if (d != targetDirection && intersection.getLight(d).getState() != TrafficLightState.RED) {
                    intersection.setLightState(d, TrafficLightState.YELLOW);
                    needsYellow = true;
                }
            }
            // If no other light needed yellow, we can potentially go green faster
            // The state machine in runSimulationStep handles this timing now.
        }
    }


    private void transitionToYellow(Direction currentGreenPairDir) {
        setYellowPair(currentGreenPairDir);
    }

    private void setGreenPair(Direction dir) {
        intersection.setLightState(dir, TrafficLightState.GREEN);
        intersection.setLightState(getOpposingDirection(dir), TrafficLightState.GREEN);
    }

    private void setYellowPair(Direction dir) {
        intersection.setLightState(dir, TrafficLightState.YELLOW);
        intersection.setLightState(getOpposingDirection(dir), TrafficLightState.YELLOW);
    }

    private void setRedPair(Direction dir) {
        intersection.setLightState(dir, TrafficLightState.RED);
        intersection.setLightState(getOpposingDirection(dir), TrafficLightState.RED);
    }

    private Direction getOpposingDirection(Direction dir) {
        switch (dir) {
            case NORTH: return Direction.SOUTH;
            case SOUTH: return Direction.NORTH;
            case EAST: return Direction.WEST;
            case WEST: return Direction.EAST;
            default: throw new IllegalArgumentException("Invalid direction");
        }
    }

    // Pair directions for N/S and E/W green lights
    private Direction getOrthogonalDirection(Direction dir) {
        switch (dir) {
            case NORTH: case SOUTH: return Direction.EAST;
            case EAST: case WEST: return Direction.NORTH;
            default: throw new IllegalArgumentException("Invalid direction");
        }
    }


    private void processGreenLightQueues() {
        // Simple model: If a light is green, let one car pass per cycle (adjust rate as needed)
        for (Direction dir : Direction.values()) {
            if (intersection.getLight(dir).getState() == TrafficLightState.GREEN) {
                Optional<Vehicle> vehicleOpt = intersection.peekNextVehicle(dir);
                if(vehicleOpt.isPresent()) {
                    // Only let non-emergency vehicles pass during normal green
                    // Emergency vehicles are handled by the priority logic
                    if (!vehicleOpt.get().isEmergencyVehicle() || priorityActive && dir == priorityDirection) {
                        Vehicle passedVehicle = intersection.getNextVehicle(dir).orElse(null);
                        if(passedVehicle != null) {
                            System.out.println("Vehicle passed: " + passedVehicle);
                            // Optionally notify Telegram about passing vehicles, maybe only if queue was large
                        }
                    }
                }
            }
        }
    }
}
