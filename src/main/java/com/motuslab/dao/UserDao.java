package com.motuslab.dao;

import com.motuslab.config.DatabaseConnection;
import com.motuslab.exception.DataAccessException;
import com.motuslab.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;

public class UserDao {

    private static final String BASE_SELECT = """
            SELECT u.id, u.role_id, r.name AS role_name, u.username, u.email,
                   u.password_hash, u.full_name, u.is_active,
                   u.last_login_at, u.created_at, u.updated_at
            FROM users u
            JOIN roles r ON r.id = u.role_id
            """;

    public Optional<User> findByUsernameOrEmail(String identifier) {
        String sql = BASE_SELECT + " WHERE LOWER(u.username) = LOWER(?) OR LOWER(u.email) = LOWER(?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, identifier);
            ps.setString(2, identifier);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Kullanıcı sorgulanırken hata oluştu.", e);
        }
    }

    public Optional<User> findByUsername(String username) {
        String sql = BASE_SELECT + " WHERE LOWER(u.username) = LOWER(?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Kullanıcı adı ile sorgulanırken hata oluştu.", e);
        }
    }

    public Optional<User> findById(int id) {
        String sql = BASE_SELECT + " WHERE u.id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Kullanıcı ID ile sorgulanırken hata oluştu.", e);
        }
    }

    public boolean existsByUsername(String username) {
        String sql = "SELECT 1 FROM users WHERE LOWER(username) = LOWER(?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Kullanıcı adı kontrolü sırasında hata oluştu.", e);
        }
    }

    public boolean existsByEmail(String email) {
        String sql = "SELECT 1 FROM users WHERE LOWER(email) = LOWER(?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DataAccessException("E-posta kontrolü sırasında hata oluştu.", e);
        }
    }

    public boolean existsAnyAdmin() {
        String sql = """
                SELECT 1
                FROM users u
                JOIN roles r ON r.id = u.role_id
                WHERE r.name = 'ADMIN'
                LIMIT 1
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            return rs.next();
        } catch (SQLException e) {
            throw new DataAccessException("Admin hesabı kontrol edilirken hata oluştu.", e);
        }
    }

    public Optional<Integer> findRoleIdByName(String roleName) {
        String sql = "SELECT id FROM roles WHERE name = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, roleName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getInt("id")) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Rol bilgisi alınırken hata oluştu.", e);
        }
    }

    public User create(User user) {
        String sql = """
                INSERT INTO users (role_id, username, email, password_hash, full_name, is_active)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id, created_at, updated_at
                """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, user.getRoleId());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPasswordHash());
            ps.setString(5, user.getFullName());
            ps.setBoolean(6, user.isActive());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    user.setId(rs.getInt("id"));
                    user.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
                    user.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
                    return user;
                }
                throw new DataAccessException("Kullanıcı oluşturuldu ancak ID döndürülemedi.");
            }
        } catch (SQLException e) {
            throw new DataAccessException("Kullanıcı kaydedilirken hata oluştu.", e);
        }
    }

    public void updateLastLogin(int userId) {
        String sql = "UPDATE users SET last_login_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Son giriş zamanı güncellenirken hata oluştu.", e);
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setRoleId(rs.getInt("role_id"));
        user.setRoleName(rs.getString("role_name"));
        user.setUsername(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setFullName(rs.getString("full_name"));
        user.setActive(rs.getBoolean("is_active"));
        user.setLastLoginAt(rs.getObject("last_login_at", OffsetDateTime.class));
        user.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        user.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
        return user;
    }
}