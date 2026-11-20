package com.motuslab.simulation;

import com.motuslab.exception.ValidationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Projectile2DModel implements PhysicsModel {

    @Override
    public SimulationResult simulate(Map<String, Double> params) {
        double y0 = require(params, "initial_height");
        double v0 = require(params, "initial_speed");
        double angleDeg = require(params, "launch_angle");
        double g = require(params, "gravity");
        double dt = require(params, "time_step");

        if (dt <= 0) {
            throw new ValidationException("Zaman adımı (dt) 0'dan büyük olmalıdır.");
        }
        if (g <= 0) {
            throw new ValidationException("Yerçekimi ivmesi (g) 0'dan büyük olmalıdır.");
        }

        double angleRad = Math.toRadians(angleDeg);
        double vx0 = v0 * Math.cos(angleRad);
        double vy0 = v0 * Math.sin(angleRad);

        // Yere çarpma süresi: y0 + vy0*t - 0.5*g*t^2 = 0
        double discriminant = vy0 * vy0 + 2.0 * g * y0;
        double flightTime = (vy0 + Math.sqrt(discriminant)) / g;

        int steps = (int) Math.ceil(flightTime / dt);
        if (steps > MAX_STEPS) {
            throw new ValidationException("Adım sayısı çok yüksek (" + steps + "). Zaman adımını (dt) büyütün. Maksimum: " + MAX_STEPS);
        }

        List<SimulationSample> samples = new ArrayList<>(steps + 1);
        double maxHeight = y0;
        double maxSpeed = v0;

        for (int i = 0; i <= steps; i++) {
            double t = Math.min(i * dt, flightTime);
            double x = vx0 * t;
            double y = Math.max(0.0, y0 + vy0 * t - 0.5 * g * t * t);
            double vy = vy0 - g * t;
            double speed = Math.hypot(vx0, vy);

            if (y > maxHeight) {
                maxHeight = y;
            }
            if (speed > maxSpeed) {
                maxSpeed = speed;
            }

            samples.add(new SimulationSample(i, t, x, y, vx0, vy, speed, 0.0, -g));
            if (t >= flightTime) {
                break;
            }
        }

        double range = vx0 * flightTime;
        double apexTime = Math.max(0.0, vy0 / g);
        double exactMaxHeight = y0 + vy0 * apexTime - 0.5 * g * apexTime * apexTime;

        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("flight_time", flightTime);
        metrics.put("range", range);
        metrics.put("max_height", Math.max(maxHeight, exactMaxHeight));
        metrics.put("impact_speed", Math.hypot(vx0, vy0 - g * flightTime));
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