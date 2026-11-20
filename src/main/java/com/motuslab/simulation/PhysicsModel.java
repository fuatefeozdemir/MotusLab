package com.motuslab.simulation;

import java.util.Map;

public interface PhysicsModel {
    int MAX_STEPS = 50_000;

    SimulationResult simulate(Map<String, Double> params);
}