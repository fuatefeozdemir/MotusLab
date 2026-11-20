package com.motuslab.simulation;

import com.motuslab.exception.ValidationException;

import java.util.Map;

public class SimulationEngine {

    private final Map<String, PhysicsModel> models = Map.of(
            "UNIFORM_MOTION", new UniformMotionModel(),
            "CONSTANT_ACCEL", new ConstantAccelerationModel(),
            "PROJECTILE_2D", new Projectile2DModel()
    );

    public SimulationResult execute(String simulationTypeCode, Map<String, Double> params) {
        PhysicsModel model = models.get(simulationTypeCode);
        if (model == null) {
            throw new ValidationException("Desteklenmeyen simülasyon türü: " + simulationTypeCode);
        }
        return model.simulate(params);
    }
}