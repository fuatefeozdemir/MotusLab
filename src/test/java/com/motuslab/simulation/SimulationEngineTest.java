package com.motuslab.simulation;

import com.motuslab.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimulationEngineTest {

    private SimulationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SimulationEngine();
    }

    @Test
    void uniformMotion_shouldCalculateFinalPositionAndDisplacementCorrectly() {
        // x(t) = x0 + v * t  ->  2 + 5 * 4 = 22 m, yer değiştirme = 20 m
        Map<String, Double> params = Map.of(
                "initial_position", 2.0,
                "velocity", 5.0,
                "duration", 4.0,
                "time_step", 0.1
        );

        SimulationResult result = engine.execute("UNIFORM_MOTION", params);

        assertFalse(result.samples().isEmpty());
        assertEquals(4.0, result.metrics().get("total_time"), 1e-6);
        assertEquals(22.0, result.metrics().get("final_position"), 1e-6);
        assertEquals(20.0, result.metrics().get("displacement"), 1e-6);
        assertEquals(5.0, result.metrics().get("max_speed"), 1e-6);
    }

    @Test
    void constantAccel_shouldCalculateKinematicsCorrectly() {
        // x(t) = 0 + 10*3 + 0.5*2*9 = 39 m, v(t) = 10 + 2*3 = 16 m/s
        Map<String, Double> params = Map.of(
                "initial_position", 0.0,
                "initial_velocity", 10.0,
                "acceleration", 2.0,
                "duration", 3.0,
                "time_step", 0.05
        );

        SimulationResult result = engine.execute("CONSTANT_ACCEL", params);

        assertFalse(result.samples().isEmpty());
        assertEquals(39.0, result.metrics().get("final_position"), 1e-6);
        assertEquals(39.0, result.metrics().get("displacement"), 1e-6);
        assertEquals(16.0, result.metrics().get("final_velocity"), 1e-6);
        assertEquals(16.0, result.metrics().get("max_speed"), 1e-6);
    }

    @Test
    void projectile2D_shouldCalculateRangeMaxHeightAndFlightTime() {
        // v0 = 20 m/s, angle = 45°, g = 9.81 m/s², y0 = 0
        // T = 2*v0*sin(45)/g ≈ 2.8832 s
        // R = v0^2 * sin(90)/g ≈ 40.7747 m
        // H = (v0*sin(45))^2 / (2*g) ≈ 10.1937 m
        Map<String, Double> params = Map.of(
                "initial_height", 0.0,
                "initial_speed", 20.0,
                "launch_angle", 45.0,
                "gravity", 9.81,
                "time_step", 0.01
        );

        SimulationResult result = engine.execute("PROJECTILE_2D", params);

        assertFalse(result.samples().isEmpty());
        assertEquals(2.8832, result.metrics().get("flight_time"), 1e-3);
        assertEquals(40.7747, result.metrics().get("range"), 1e-3);
        assertEquals(10.1937, result.metrics().get("max_height"), 1e-3);
        assertEquals(20.0, result.metrics().get("impact_speed"), 1e-3);
    }

    @Test
    void unknownSimulationType_shouldThrowValidationException() {
        assertThrows(ValidationException.class, () ->
                engine.execute("UNKNOWN_TYPE", Map.of())
        );
    }

    @Test
    void nonPositiveTimeStep_shouldThrowValidationException() {
        Map<String, Double> params = Map.of(
                "initial_position", 0.0,
                "velocity", 5.0,
                "duration", 4.0,
                "time_step", 0.0
        );

        assertThrows(ValidationException.class, () ->
                engine.execute("UNIFORM_MOTION", params)
        );
    }

    @Test
    void missingParameter_shouldThrowValidationException() {
        Map<String, Double> params = Map.of(
                "initial_speed", 20.0,
                "launch_angle", 45.0
        );

        assertThrows(ValidationException.class, () ->
                engine.execute("PROJECTILE_2D", params)
        );
    }
}