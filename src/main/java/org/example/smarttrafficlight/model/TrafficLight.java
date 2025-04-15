package org.example.smarttrafficlight.model;

public class TrafficLight {
    private final Direction direction; // The direction this light controls
    private TrafficLightState state;

    public TrafficLight(Direction direction) {
        this.direction = direction;
        this.state = TrafficLightState.RED; // Default state
    }

    public Direction getDirection() {
        return direction;
    }

    public TrafficLightState getState() {
        return state;
    }

    public void setState(TrafficLightState state) {
        this.state = state;
        System.out.println("Light " + direction + " changed to " + state); // Simple logging
    }

    @Override
    public String toString() {
        return "Light[" + direction + "=" + state + "]";
    }
}
