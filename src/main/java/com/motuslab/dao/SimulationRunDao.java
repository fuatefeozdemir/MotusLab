package com.motuslab.dao;

import com.motuslab.config.DatabaseConnection;
import com.motuslab.exception.DataAccessException;
import com.motuslab.model.RunMetric;
import com.motuslab.model.RunSample;
import com.motuslab.model.RunStatusHistory;
import com.motuslab.model.RunParameterSnapshot;
import com.motuslab.model.SimulationRun;
import com.motuslab.simulation.SimulationSample;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SimulationRunDao {

    private static final int BATCH_SIZE = 1000;

    public int createRunRecord(int experimentId, int userId) {
        String sql = "SELECT sp_create_run_record(?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, experimentId);
            ps.setInt(2, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                throw new DataAccessException("Çalıştırma kaydı oluşturuldu ancak ID alınamadı.");
            }
        } catch (SQLException e) {
            throw new DataAccessException(e.getMessage(), e);
        }
    }

    public void markRunning(int runId) {
        String sql = "UPDATE simulation_runs SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, runId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Çalıştırma RUNNING durumuna alınamadı.", e);
        }
    }

    public void saveResultsAndComplete(int runId, List<SimulationSample> samples, List<RunMetric> metrics, boolean is2D) {
        String insertSampleSql = """
                INSERT INTO run_samples (run_id, sample_index, time_seconds, position_x, position_y, velocity_x, velocity_y)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        String insertMetricSql = """
                INSERT INTO run_metrics (run_id, metric_code, metric_value, unit)
                VALUES (?, ?, ?, ?)
                """;
        String completeRunSql = """
                UPDATE simulation_runs
                SET status = 'COMPLETED', finished_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(insertSampleSql)) {
                    int count = 0;
                    for (SimulationSample s : samples) {
                        ps.setInt(1, runId);
                        ps.setInt(2, s.stepIndex());
                        ps.setDouble(3, s.timeSec());
                        ps.setDouble(4, s.x());
                        if (is2D) {
                            ps.setDouble(5, s.y());
                        } else {
                            ps.setNull(5, Types.DOUBLE);
                        }
                        ps.setDouble(6, s.vx());
                        if (is2D) {
                            ps.setDouble(7, s.vy());
                        } else {
                            ps.setNull(7, Types.DOUBLE);
                        }
                        ps.addBatch();

                        if (++count % BATCH_SIZE == 0) {
                            ps.executeBatch();
                        }
                    }
                    ps.executeBatch();
                }

                try (PreparedStatement ps = conn.prepareStatement(insertMetricSql)) {
                    for (RunMetric m : metrics) {
                        ps.setInt(1, runId);
                        ps.setString(2, m.getMetricCode());
                        ps.setDouble(3, m.getMetricValue());
                        ps.setString(4, m.getUnit());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                try (PreparedStatement ps = conn.prepareStatement(completeRunSql)) {
                    ps.setInt(1, runId);
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Simülasyon sonuçları kaydedilirken hata oluştu.", e);
        }
    }

    public void markFailed(int runId, String errorMessage) {
        String sql = """
                UPDATE simulation_runs
                SET status = 'FAILED', finished_at = CURRENT_TIMESTAMP, error_message = ?
                WHERE id = ?
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, errorMessage);
            ps.setInt(2, runId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Çalıştırma FAILED durumuna alınamadı.", e);
        }
    }

    public List<SimulationRun> findByExperimentId(int experimentId) {
        String sql = """
                SELECT sr.id, sr.experiment_id, sr.run_number, sr.triggered_by, u.username AS triggered_by_username,
                  sr.status, sr.created_at, sr.started_at, sr.finished_at, sr.error_message
                FROM simulation_runs sr
                JOIN users u ON u.id = sr.triggered_by
                WHERE sr.experiment_id = ?
                ORDER BY sr.created_at DESC
                """;
        List<SimulationRun> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, experimentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRun(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Çalıştırma geçmişi alınırken hata oluştu.", e);
        }
    }

    public Optional<SimulationRun> findById(int runId) {
        String sql = """
                SELECT sr.id, sr.experiment_id, sr.run_number, sr.triggered_by, u.username AS triggered_by_username,
                sr.status, sr.created_at, sr.started_at, sr.finished_at, sr.error_message
                FROM simulation_runs sr
                JOIN users u ON u.id = sr.triggered_by
                WHERE sr.id = ?
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRun(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Çalıştırma kaydı alınırken hata oluştu.", e);
        }
    }

    public List<RunSample> findSamplesByRunId(int runId) {
        String sql = """
                SELECT id, run_id, sample_index, time_seconds, position_x, position_y, velocity_x, velocity_y
                FROM run_samples
                WHERE run_id = ?
                ORDER BY sample_index
                """;
        List<RunSample> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RunSample sample = new RunSample();
                    sample.setId(rs.getInt("id"));
                    sample.setRunId(rs.getInt("run_id"));
                    sample.setSampleIndex(rs.getInt("sample_index"));
                    sample.setTimeSeconds(rs.getDouble("time_seconds"));
                    sample.setPositionX(rs.getDouble("position_x"));
                    sample.setPositionY(rs.getObject("position_y", Double.class));
                    sample.setVelocityX(rs.getDouble("velocity_x"));
                    sample.setVelocityY(rs.getObject("velocity_y", Double.class));
                    list.add(sample);
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Örneklem verileri alınırken hata oluştu.", e);
        }
    }

    public List<RunMetric> findMetricsByRunId(int runId) {
        String sql = """
                SELECT id, run_id, metric_code, metric_value, unit
                FROM run_metrics
                WHERE run_id = ?
                ORDER BY id
                """;
        List<RunMetric> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RunMetric metric = new RunMetric();
                    metric.setId(rs.getInt("id"));
                    metric.setRunId(rs.getInt("run_id"));
                    metric.setMetricCode(rs.getString("metric_code"));
                    metric.setMetricValue(rs.getDouble("metric_value"));
                    metric.setUnit(rs.getString("unit"));
                    list.add(metric);
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Metrik verileri alınırken hata oluştu.", e);
        }
    }

    private SimulationRun mapRun(ResultSet rs) throws SQLException {
        SimulationRun run = new SimulationRun();
        run.setId(rs.getInt("id"));
        run.setExperimentId(rs.getInt("experiment_id"));
        run.setTriggeredBy(rs.getInt("triggered_by"));
        run.setTriggeredByUsername(rs.getString("triggered_by_username"));
        run.setStatus(rs.getString("status"));
        run.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        run.setStartedAt(rs.getObject("started_at", OffsetDateTime.class));
        run.setFinishedAt(rs.getObject("finished_at", OffsetDateTime.class));
        run.setErrorMessage(rs.getString("error_message"));
        run.setRunNumber(rs.getInt("run_number"));
        return run;
    }

    public List<RunParameterSnapshot> findSnapshotsByRunId(int runId) {
        String sql = """
                SELECT rps.id, rps.run_id, rps.parameter_definition_id,
                       pd.code AS parameter_code, pd.name AS parameter_name, pd.unit, rps.value
                FROM run_parameter_snapshots rps
                JOIN parameter_definitions pd ON pd.id = rps.parameter_definition_id
                WHERE rps.run_id = ?
                ORDER BY pd.display_order ASC, pd.id ASC
                """;
        List<RunParameterSnapshot> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RunParameterSnapshot s = new RunParameterSnapshot();
                    s.setId(rs.getInt("id"));
                    s.setRunId(rs.getInt("run_id"));
                    s.setParameterDefinitionId(rs.getInt("parameter_definition_id"));
                    s.setParameterCode(rs.getString("parameter_code"));
                    s.setParameterName(rs.getString("parameter_name"));
                    s.setUnit(rs.getString("unit"));
                    s.setValue(rs.getDouble("value"));
                    list.add(s);
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Parametre snapshot verileri alınırken hata oluştu.", e);
        }
    }

    public List<RunStatusHistory> findStatusHistoryByRunId(int runId) {
        String sql = """
                SELECT id, run_id, old_status, new_status, changed_at
                FROM run_status_history
                WHERE run_id = ?
                ORDER BY changed_at ASC, id ASC
                """;
        List<RunStatusHistory> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RunStatusHistory h = new RunStatusHistory();
                    h.setId(rs.getInt("id"));
                    h.setRunId(rs.getInt("run_id"));
                    h.setOldStatus(rs.getString("old_status"));
                    h.setNewStatus(rs.getString("new_status"));
                    h.setChangedAt(rs.getObject("changed_at", OffsetDateTime.class));
                    list.add(h);
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Çalıştırma durum geçmişi alınırken hata oluştu.", e);
        }
    }
}