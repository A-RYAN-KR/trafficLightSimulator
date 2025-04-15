package org.example.smarttrafficlight.service;

import org.example.smarttrafficlight.model.Direction;
import org.example.smarttrafficlight.model.TrafficLight;
import org.example.smarttrafficlight.model.TrafficLightState;
import org.example.smarttrafficlight.model.Vehicle;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue; // Thread-safe PriorityQueue

public class Intersection {

    // Map Direction -> Traffic Light for that direction
    private final Map<Direction, TrafficLight> trafficLights;

    // Map Direction -> Queue of vehicles waiting from that direction
    // Using PriorityBlockingQueue for thread safety if accessed from multiple threads (e.g., GUI + simulation)
    private final Map<Direction, PriorityBlockingQueue<Vehicle>> vehicleQueues;

    // HashMap to potentially store wait times or other stats (optional for now)
    private final Map<Direction, Long> maxWaitTimes; // Example use of HashMap

    public Intersection() {
        trafficLights = new EnumMap<>(Direction.class);
        vehicleQueues = new EnumMap<>(Direction.class);
        maxWaitTimes = new ConcurrentHashMap<>(); // Thread-safe HashMap

        for (Direction dir : Direction.values()) {
            trafficLights.put(dir, new TrafficLight(dir));
            // Initialize with a thread-safe PriorityQueue for each direction
            vehicleQueues.put(dir, new PriorityBlockingQueue<>());
            maxWaitTimes.put(dir, 0L); // Initialize wait times
        }

        // Initial state: North/South Green, East/West Red (example)
        trafficLights.get(Direction.NORTH).setState(TrafficLightState.GREEN);
        trafficLights.get(Direction.SOUTH).setState(TrafficLightState.GREEN);
    }

    // --- Vehicle Management ---

    public void addVehicle(Vehicle vehicle) {
        Direction dir = vehicle.getOriginDirection();
        if (vehicleQueues.containsKey(dir)) {
            vehicleQueues.get(dir).put(vehicle); // Use put for BlockingQueue
            System.out.println("Added " + vehicle + " to " + dir + " queue. Size: " + getQueueSize(dir));
            updateMaxWaitTime(dir, vehicle); // Update stats
        }
    }

    public Optional<Vehicle> getNextVehicle(Direction direction) {
        // Retrieves and removes the head of the queue (highest priority vehicle)
        return Optional.ofNullable(vehicleQueues.get(direction).poll());
    }

    public Optional<Vehicle> peekNextVehicle(Direction direction) {
        // Looks at the head of the queue without removing it
        return Optional.ofNullable(vehicleQueues.get(direction).peek());
    }

    public int getQueueSize(Direction direction) {
        return vehicleQueues.get(direction).size();
    }

    public Map<Direction, Integer> getAllQueueSizes() {
        Map<Direction, Integer> sizes = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.values()) {
            sizes.put(dir, getQueueSize(dir));
        }
        return sizes;
    }

    // --- Traffic Light Management ---

    public TrafficLight getLight(Direction direction) {
        return trafficLights.get(direction);
    }

    public Map<Direction, TrafficLight> getAllLights() {
        return Collections.unmodifiableMap(trafficLights);
    }

    public void setLightState(Direction direction, TrafficLightState state) {
        if (trafficLights.containsKey(direction)) {
            trafficLights.get(direction).setState(state);
        }
    }

    // --- Priority Check ---

    public Optional<Direction> checkForPriorityVehicle() {
        for (Direction dir : Direction.values()) {
            Optional<Vehicle> nextVehicle = peekNextVehicle(dir);
            if (nextVehicle.isPresent() && nextVehicle.get().isEmergencyVehicle()) {
                // Check if it's near the front (e.g., first few positions) - simplistic check
                if(getQueueSize(dir) > 0) { // Or more complex logic like checking position
                    System.out.println("PRIORITY: Emergency vehicle detected in " + dir + " queue: " + nextVehicle.get());
                    return Optional.of(dir);
                }
            }
        }
        return Optional.empty();
    }

    // --- Example HashMap Usage ---
    private void updateMaxWaitTime(Direction dir, Vehicle vehicle) {
        // This is a simple example; real wait time tracking would be more complex
        long currentWait = System.currentTimeMillis() - vehicle.getArrivalTime();
        maxWaitTimes.compute(dir, (key, currentMax) -> Math.max(currentMax == null ? 0 : currentMax, currentWait));
    }

    public Map<Direction, Long> getMaxWaitTimes() {
        return Collections.unmodifiableMap(maxWaitTimes);
    }

    public List<Vehicle> getQueuePreview(Direction direction, int count) {
        // Get a snapshot for display, without modifying the queue
        List<Vehicle> preview = new ArrayList<>();
        Iterator<Vehicle> iterator = vehicleQueues.get(direction).iterator();
        int i = 0;
        while (iterator.hasNext() && i < count) {
            preview.add(iterator.next());
            i++;
        }
        return preview;
    }
}
