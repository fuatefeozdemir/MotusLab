package com.motuslab.dao;

import com.motuslab.config.DatabaseConnection;
import com.motuslab.exception.DataAccessException;
import com.motuslab.model.ParameterDefinition;
import com.motuslab.model.SimulationType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SimulationTypeDao {

    public List<SimulationType> findAll() {
        String sql = "SELECT id, code, name, description FROM simulation_types ORDER BY id";
        List<SimulationType> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                SimulationType type = new SimulationType();
                type.setId(rs.getInt("id"));
                type.setCode(rs.getString("code"));
                type.setName(rs.getString("name"));
                type.setDescription(rs.getString("description"));
                list.add(type);
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Simülasyon türleri alınırken hata oluştu.", e);
        }
    }

    public Optional<SimulationType> findById(int id) {
        String sql = "SELECT id, code, name, description FROM simulation_types WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    SimulationType type = new SimulationType();
                    type.setId(rs.getInt("id"));
                    type.setCode(rs.getString("code"));
                    type.setName(rs.getString("name"));
                    type.setDescription(rs.getString("description"));
                    return Optional.of(type);
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Simülasyon türü sorgulanırken hata oluştu.", e);
        }
    }

    public List<ParameterDefinition> findParametersBySimulationTypeId(int simulationTypeId) {
        String sql = """
                SELECT id, simulation_type_id, code, name, unit,
                       default_value, min_value, max_value, display_order
                FROM parameter_definitions
                WHERE simulation_type_id = ?
                ORDER BY display_order
                """;
        List<ParameterDefinition> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, simulationTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ParameterDefinition def = new ParameterDefinition();
                    def.setId(rs.getInt("id"));
                    def.setSimulationTypeId(rs.getInt("simulation_type_id"));
                    def.setCode(rs.getString("code"));
                    def.setName(rs.getString("name"));
                    def.setUnit(rs.getString("unit"));
                    def.setDefaultValue(rs.getDouble("default_value"));
                    def.setMinValue(rs.getObject("min_value", Double.class));
                    def.setMaxValue(rs.getObject("max_value", Double.class));
                    def.setDisplayOrder(rs.getInt("display_order"));
                    list.add(def);
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Parametre tanımları alınırken hata oluştu.", e);
        }
    }
}