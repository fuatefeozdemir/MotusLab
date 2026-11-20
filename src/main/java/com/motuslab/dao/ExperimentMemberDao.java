package com.motuslab.dao;

import com.motuslab.config.DatabaseConnection;
import com.motuslab.exception.DataAccessException;
import com.motuslab.model.ExperimentMember;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ExperimentMemberDao {

    public List<ExperimentMember> findByExperimentId(int experimentId) {
        String sql = """
                SELECT em.id, em.experiment_id, em.user_id, u.username, em.permission, em.added_at
                FROM experiment_members em
                JOIN users u ON u.id = em.user_id
                WHERE em.experiment_id = ?
                ORDER BY em.added_at DESC
                """;
        List<ExperimentMember> list = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, experimentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ExperimentMember member = new ExperimentMember();
                    member.setId(rs.getInt("id"));
                    member.setExperimentId(rs.getInt("experiment_id"));
                    member.setUserId(rs.getInt("user_id"));
                    member.setUsername(rs.getString("username"));
                    member.setPermission(rs.getString("permission"));
                    member.setAddedAt(rs.getObject("added_at", OffsetDateTime.class));
                    list.add(member);
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Deney üyeleri alınırken hata oluştu.", e);
        }
    }

    public Optional<String> findPermission(int experimentId, int userId) {
        String sql = "SELECT permission FROM experiment_members WHERE experiment_id = ? AND user_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, experimentId);
            ps.setInt(2, userId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString("permission")) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Üye yetkisi sorgulanırken hata oluştu.", e);
        }
    }

    public void upsertMember(int experimentId, int userId, String permission) {
        String sql = """
                INSERT INTO experiment_members (experiment_id, user_id, permission)
                VALUES (?, ?, ?)
                ON CONFLICT (experiment_id, user_id)
                DO UPDATE SET permission = EXCLUDED.permission
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, experimentId);
            ps.setInt(2, userId);
            ps.setString(3, permission);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Deney üyesi kaydedilirken hata oluştu.", e);
        }
    }

    public void removeMember(int experimentId, int userId) {
        String sql = "DELETE FROM experiment_members WHERE experiment_id = ? AND user_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, experimentId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Deney üyesi çıkarılırken hata oluştu.", e);
        }
    }
}