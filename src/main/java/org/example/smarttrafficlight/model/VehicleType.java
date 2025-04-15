package org.example.smarttrafficlight.model;

public enum VehicleType {
    CAR(1), // Lower number = lower priority
    BUS(2),
    TRUCK(2),
    MOTORCYCLE(1),
    FIRE_TRUCK(10), // Higher number = higher priority
    AMBULANCE(10),
    POLICE(9);

    private final int priorityLevel;

    VehicleType(int priorityLevel) {
        this.priorityLevel = priorityLevel;
    }

    public int getPriorityLevel() {
        return priorityLevel;
    }
}
