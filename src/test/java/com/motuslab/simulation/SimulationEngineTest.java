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
    void uniform1D_shouldCalculateFinalPositionAndDisplacementCorrectly() {
        // x(t) = x0 + v0 * t  ->  2 + 5 * 4 = 22 m, yer değiştirme = 20 m
        Map<String, Double> params = Map.of(
                "x0", 2.0,
                "v0", 5.0,
                "duration", 4.0,
                "dt", 0.1
        );

        SimulationResult result = engine.execute("UNIFORM_1D", params);

        assertFalse(result.samples().isEmpty());
        assertEquals(4.0, result.metrics().get("total_time"), 1e-6);
        assertEquals(22.0, result.metrics().get("final_position"), 1e-6);
        assertEquals(20.0, result.metrics().get("displacement"), 1e-6);
    }

    @Test
    void accel1D_shouldCalculateKinematicsCorrectly() {
        // x(t) = 0 + 10*3 + 0.5*2*9 = 39 m, v(t) = 10 + 2*3 = 16 m/s
        Map<String, Double> params = Map.of(
                "x0", 0.0,
                "v0", 10.0,
                "a", 2.0,
                "duration", 3.0,
                "dt", 0.05
        );

        SimulationResult result = engine.execute("ACCEL_1D", params);

        assertFalse(result.samples().isEmpty());
        assertEquals(39.0, result.metrics().get("final_position"), 1e-3);
        assertEquals(16.0, result.metrics().get("final_velocity"), 1e-3);
    }

    @Test
    void projectile2D_shouldCalculateRangeMaxHeightAndFlightTime() {
        // v0 = 20 m/s, angle = 45°, g = 9.81 m/s², y0 = 0
        // T = 2*v0*sin(45)/g ≈ 2.8832 s
        // R = v0^2 * sin(90)/g ≈ 40.7747 m
        // H = (v0*sin(45))^2 / (2*g) ≈ 10.1937 m
        Map<String, Double> params = Map.of(
                "v0", 20.0,
                "angle_deg", 45.0,
                "y0", 0.0,
                "g", 9.81,
                "dt", 0.01
        );

        SimulationResult result = engine.execute("PROJECTILE_2D", params);

        assertFalse(result.samples().isEmpty());
        assertEquals(2.8832, result.metrics().get("flight_time"), 0.05);
        assertEquals(40.7747, result.metrics().get("range"), 0.5);
        assertEquals(10.1937, result.metrics().get("max_height"), 0.1);
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
                "x0", 0.0,
                "v0", 5.0,
                "duration", 4.0,
                "dt", 0.0
        );

        assertThrows(ValidationException.class, () ->
                engine.execute("UNIFORM_1D", params)
        );
    }
}