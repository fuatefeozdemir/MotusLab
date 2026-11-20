package com.motuslab.simulation;

public record SimulationSample(
        int stepIndex,
        double timeSec,
        double x,
        double y,
        double vx,
        double vy,
        double speed,
        double ax,
        double ay
) {
}