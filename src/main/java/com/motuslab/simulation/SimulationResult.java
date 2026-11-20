package com.motuslab.simulation;

import java.util.List;
import java.util.Map;

public record SimulationResult(
        List<SimulationSample> samples,
        Map<String, Double> metrics
) {
}