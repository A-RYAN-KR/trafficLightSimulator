package org.example.smarttrafficlight.model;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class Vehicle implements Comparable<Vehicle> {
    private static final AtomicInteger idCounter = new AtomicInteger(0); // Simple way to get unique IDs

    private final int id;
    private final VehicleType type;
    private final Direction originDirection; // Where the vehicle is coming FROM
    private final long arrivalTime; // Simulation time when it arrived at the queue

    public Vehicle(VehicleType type, Direction originDirection) {
        this.id = idCounter.incrementAndGet();
        this.type = Objects.requireNonNull(type, "Vehicle type cannot be null");
        this.originDirection = Objects.requireNonNull(originDirection, "Origin direction cannot be null");
        this.arrivalTime = System.currentTimeMillis(); // Or use a simulation clock
    }

    public int getId() {
        return id;
    }

    public VehicleType getType() {
        return type;
    }

    public Direction getOriginDirection() {
        return originDirection;
    }

    public long getArrivalTime() {
        return arrivalTime;
    }

    public boolean isEmergencyVehicle() {
        return type == VehicleType.AMBULANCE || type == VehicleType.FIRE_TRUCK || type == VehicleType.POLICE;
    }

    // --- Comparable Implementation for PriorityQueue ---
    // Higher priorityLevel means it comes *earlier* in the queue (higher priority).
    // If priorities are equal, the one that arrived earlier gets higher priority.
    @Override
    public int compareTo(Vehicle other) {
        if (this.type.getPriorityLevel() != other.type.getPriorityLevel()) {
            // Descending order of priority level (higher number is "smaller" for PQ)
            return Integer.compare(other.type.getPriorityLevel(), this.type.getPriorityLevel());
        } else {
            // Ascending order of arrival time (earlier time is "smaller" for PQ)
            return Long.compare(this.arrivalTime, other.arrivalTime);
        }
    }
    // --- End Comparable ---

    @Override
    public String toString() {
        return type + "#" + id + " (from " + originDirection + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vehicle vehicle = (Vehicle) o;
        return id == vehicle.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
