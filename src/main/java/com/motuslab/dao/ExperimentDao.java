package com.motuslab.dao;

import com.motuslab.config.DatabaseConnection;
import com.motuslab.exception.DataAccessException;
import com.motuslab.model.Experiment;
import com.motuslab.model.ExperimentParameter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ExperimentDao {

    private static final String BASE_SELECT = """
            SELECT e.id, e.owner_id, u.username AS owner_username,
                   e.simulation_type_id, st.code AS sim_code, st.name AS sim_name,
                   e.title, e.description, e.is_archived, e.created_at, e.updated_at,
                   COALESCE(rs.run_count, 0) AS run_count,
                   COALESCE(rs.last_run_number, 0) AS last_run_number
            FROM experiments e
            JOIN users u ON u.id = e.owner_id
            JOIN simulation_types st ON st.id = e.simulation_type_id
            LEFT JOIN LATERAL (
                SELECT COUNT(*)::int AS run_count,
                       COALESCE(MAX(sr.run_number), 0)::int AS last_run_number
                FROM simulation_runs sr
                WHERE sr.experiment_id = e.id
            ) rs ON true
            """;

    public List<Experiment> findAccessibleByUser(int userId) {
        String sql = BASE_SELECT + """
                 WHERE e.owner_id = ?
                    OR EXISTS (
                        SELECT 1 FROM experiment_members em
                        WHERE em.experiment_id = e.id AND em.user_id = ?
                    )
                 ORDER BY e.updated_at DESC
                """;
        List<Experiment> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setInt(2, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapExperiment(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Deney listesi alınırken hata oluştu.", e);
        }
    }

    public Optional<Experiment> findById(int experimentId) {
        String sql = BASE_SELECT + " WHERE e.id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, experimentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapExperiment(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Deney bilgisi alınırken hata oluştu.", e);
        }
    }

    public Experiment create(Experiment experiment, Map<Integer, Double> parameterValues) {
        String insertExpSql = """
                INSERT INTO experiments (owner_id, simulation_type_id, title, description)
                VALUES (?, ?, ?, ?)
                RETURNING id, created_at, updated_at
                """;
        String insertParamSql = """
                INSERT INTO experiment_parameters (experiment_id, simulation_type_id, parameter_definition_id, value)
                VALUES (?, ?, ?, ?)
                """;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(insertExpSql)) {
                    ps.setInt(1, experiment.getOwnerId());
                    ps.setInt(2, experiment.getSimulationTypeId());
                    ps.setString(3, experiment.getTitle());
                    ps.setString(4, experiment.getDescription());

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new DataAccessException("Deney oluşturuldu ancak ID alınamadı.");
                        }
                        experiment.setId(rs.getInt("id"));
                        experiment.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
                        experiment.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(insertParamSql)) {
                    for (Map.Entry<Integer, Double> entry : parameterValues.entrySet()) {
                        ps.setInt(1, experiment.getId());
                        ps.setInt(2, experiment.getSimulationTypeId());
                        ps.setInt(3, entry.getKey());
                        ps.setDouble(4, entry.getValue());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                conn.commit();
                return experiment;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Deney kaydedilirken hata oluştu.", e);
        }
    }

    public List<ExperimentParameter> findParametersByExperimentId(int experimentId) {
        String sql = """
                SELECT ep.id, ep.experiment_id, ep.parameter_definition_id,
                       pd.code, pd.name, pd.unit, ep.value, pd.min_value, pd.max_value
                FROM experiment_parameters ep
                JOIN parameter_definitions pd ON pd.id = ep.parameter_definition_id
                WHERE ep.experiment_id = ?
                ORDER BY pd.display_order
                """;
        List<ExperimentParameter> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, experimentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ExperimentParameter param = new ExperimentParameter();
                    param.setId(rs.getInt("id"));
                    param.setExperimentId(rs.getInt("experiment_id"));
                    param.setParameterDefinitionId(rs.getInt("parameter_definition_id"));
                    param.setParameterCode(rs.getString("code"));
                    param.setParameterName(rs.getString("name"));
                    param.setUnit(rs.getString("unit"));
                    param.setValue(rs.getDouble("value"));
                    param.setMinValue(rs.getObject("min_value", Double.class));
                    param.setMaxValue(rs.getObject("max_value", Double.class));
                    list.add(param);
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Deney parametreleri alınırken hata oluştu.", e);
        }
    }

    public void updateParameters(int experimentId, int simulationTypeId, Map<Integer, Double> parameterValues) {
        String upsertSql = """
                INSERT INTO experiment_parameters (experiment_id, simulation_type_id, parameter_definition_id, value)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (experiment_id, parameter_definition_id)
                DO UPDATE SET value = EXCLUDED.value
                """;
        String touchExpSql = "UPDATE experiments SET updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(upsertSql)) {
                    for (Map.Entry<Integer, Double> entry : parameterValues.entrySet()) {
                        ps.setInt(1, experimentId);
                        ps.setInt(2, simulationTypeId);
                        ps.setInt(3, entry.getKey());
                        ps.setDouble(4, entry.getValue());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                try (PreparedStatement ps = conn.prepareStatement(touchExpSql)) {
                    ps.setInt(1, experimentId);
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
            throw new DataAccessException("Deney parametreleri güncellenirken hata oluştu.", e);
        }
    }

    public void delete(int experimentId) {
        String deleteRunsSql = "DELETE FROM simulation_runs WHERE experiment_id = ?";
        String deleteMembersSql = "DELETE FROM experiment_members WHERE experiment_id = ?";
        String deleteParamsSql = "DELETE FROM experiment_parameters WHERE experiment_id = ?";
        String deleteExpSql = "DELETE FROM experiments WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(deleteRunsSql)) {
                    ps.setInt(1, experimentId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(deleteMembersSql)) {
                    ps.setInt(1, experimentId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(deleteParamsSql)) {
                    ps.setInt(1, experimentId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(deleteExpSql)) {
                    ps.setInt(1, experimentId);
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
            throw new DataAccessException("Deney silinirken hata oluştu.", e);
        }
    }

    private Experiment mapExperiment(ResultSet rs) throws SQLException {
        Experiment exp = new Experiment();
        exp.setId(rs.getInt("id"));
        exp.setOwnerId(rs.getInt("owner_id"));
        exp.setOwnerUsername(rs.getString("owner_username"));
        exp.setSimulationTypeId(rs.getInt("simulation_type_id"));
        exp.setSimulationTypeCode(rs.getString("sim_code"));
        exp.setSimulationTypeName(rs.getString("sim_name"));
        exp.setTitle(rs.getString("title"));
        exp.setDescription(rs.getString("description"));
        exp.setArchived(rs.getBoolean("is_archived"));
        exp.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        exp.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
        exp.setRunCount(rs.getInt("run_count"));
        exp.setLastRunNumber(rs.getInt("last_run_number"));
        return exp;
    }

    public void updateDetails(int experimentId, String title, String description) {
        String sql = """
                UPDATE experiments
                SET title = ?, description = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, title);
            ps.setString(2, description);
            ps.setInt(3, experimentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Deney bilgileri güncellenirken hata oluştu.", e);
        }
    }

    public void updateArchivedStatus(int experimentId, boolean archived) {
        String sql = """
                UPDATE experiments
                SET is_archived = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBoolean(1, archived);
            ps.setInt(2, experimentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Deney arşiv durumu güncellenirken hata oluştu.", e);
        }
    }
}