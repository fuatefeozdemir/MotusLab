package com.motuslab.simulation;

import com.motuslab.exception.ValidationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConstantAccelerationModel implements PhysicsModel {

    @Override
    public SimulationResult simulate(Map<String, Double> params) {
        double x0 = require(params, "initial_position");
        double v0 = require(params, "initial_velocity");
        double a = require(params, "acceleration");
        double duration = require(params, "duration");
        double dt = require(params, "time_step");

        if (dt <= 0) {
            throw new ValidationException("Zaman adımı (dt) 0'dan büyük olmalıdır.");
        }

        int steps = (int) Math.ceil(duration / dt);
        if (steps > MAX_STEPS) {
            throw new ValidationException("Adım sayısı çok yüksek (" + steps + "). Süreyi azaltın veya zaman adımını (dt) büyütün. Maksimum: " + MAX_STEPS);
        }

        List<SimulationSample> samples = new ArrayList<>(steps + 1);
        double maxSpeed = 0.0;

        for (int i = 0; i <= steps; i++) {
            double t = Math.min(i * dt, duration);
            double x = x0 + v0 * t + 0.5 * a * t * t;
            double v = v0 + a * t;
            double speed = Math.abs(v);
            if (speed > maxSpeed) {
                maxSpeed = speed;
            }

            samples.add(new SimulationSample(i, t, x, 0.0, v, 0.0, speed, a, 0.0));
            if (t >= duration) {
                break;
            }
        }

        double finalX = x0 + v0 * duration + 0.5 * a * duration * duration;
        double finalV = v0 + a * duration;

        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("total_time", duration);
        metrics.put("final_position", finalX);
        metrics.put("displacement", finalX - x0);
        metrics.put("final_velocity", finalV);
        metrics.put("max_speed", maxSpeed);

        return new SimulationResult(samples, metrics);
    }

    private double require(Map<String, Double> params, String key) {
        Double val = params.get(key);
        if (val == null) {
            throw new ValidationException("Eksik parametre: " + key);
        }
        return val;
    }
}