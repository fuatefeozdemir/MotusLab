package com.motuslab.service;

import com.motuslab.dao.ExperimentDao;
import com.motuslab.dao.SimulationRunDao;
import com.motuslab.exception.NotFoundException;
import com.motuslab.model.*;
import com.motuslab.simulation.SimulationEngine;
import com.motuslab.simulation.SimulationResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SimulationRunService {

    private static final Map<String, String> METRIC_UNITS = Map.of(
            "total_time", "s",
            "flight_time", "s",
            "final_position", "m",
            "displacement", "m",
            "range", "m",
            "max_height", "m",
            "final_velocity", "m/s",
            "impact_speed", "m/s",
            "max_speed", "m/s"
    );

    private final ExperimentDao experimentDao;
    private final SimulationRunDao simulationRunDao;
    private final SimulationEngine simulationEngine;

    public SimulationRunService() {
        this(new ExperimentDao(), new SimulationRunDao(), new SimulationEngine());
    }

    public SimulationRunService(
            ExperimentDao experimentDao,
            SimulationRunDao simulationRunDao,
            SimulationEngine simulationEngine
    ) {
        this.experimentDao = experimentDao;
        this.simulationRunDao = simulationRunDao;
        this.simulationEngine = simulationEngine;
    }

    public SimulationRun executeExperiment(int experimentId) {
        User currentUser = SessionManager.requireCurrentUser();

        Experiment experiment = experimentDao.findById(experimentId)
                .orElseThrow(() -> new NotFoundException("Deney bulunamadı."));

        List<ExperimentParameter> params = experimentDao.findParametersByExperimentId(experimentId);
        Map<String, Double> paramMap = new LinkedHashMap<>();
        for (ExperimentParameter p : params) {
            paramMap.put(p.getParameterCode(), p.getValue());
        }

        int runId = simulationRunDao.createRunRecord(experimentId, currentUser.getId());

        try {
            simulationRunDao.markRunning(runId);

            SimulationResult result = simulationEngine.execute(experiment.getSimulationTypeCode(), paramMap);

            List<RunMetric> metrics = new ArrayList<>();
            for (Map.Entry<String, Double> entry : result.metrics().entrySet()) {
                String unit = METRIC_UNITS.getOrDefault(entry.getKey(), "-");
                metrics.add(new RunMetric(runId, entry.getKey(), entry.getValue(), unit));
            }

            boolean is2D = "PROJECTILE_2D".equals(experiment.getSimulationTypeCode());
            simulationRunDao.saveResultsAndComplete(runId, result.samples(), metrics, is2D);

            return simulationRunDao.findById(runId)
                    .orElseThrow(() -> new NotFoundException("Çalıştırma kaydı bulunamadı."));
        } catch (RuntimeException ex) {
            simulationRunDao.markFailed(runId, ex.getMessage());
            throw ex;
        }
    }

    public List<SimulationRun> getRunsByExperiment(int experimentId) {
        SessionManager.requireCurrentUser();
        return simulationRunDao.findByExperimentId(experimentId);
    }

    public List<RunSample> getSamplesByRun(int runId) {
        SessionManager.requireCurrentUser();
        return simulationRunDao.findSamplesByRunId(runId);
    }

    public List<RunMetric> getMetricsByRun(int runId) {
        SessionManager.requireCurrentUser();
        return simulationRunDao.findMetricsByRunId(runId);
    }

    public List<RunParameterSnapshot> getSnapshotsByRun(int runId) {
        SessionManager.requireCurrentUser();
        return simulationRunDao.findSnapshotsByRunId(runId);
    }

    public List<RunStatusHistory> getStatusHistoryByRun(int runId) {
        SessionManager.requireCurrentUser();
        return simulationRunDao.findStatusHistoryByRunId(runId);
    }
}