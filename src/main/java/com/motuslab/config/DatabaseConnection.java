package com.motuslab.config;

import com.motuslab.exception.DataAccessException;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class DatabaseConnection {

    private static final String CONFIG_FILE = "config.properties";
    private static final String URL;
    private static final String USER;
    private static final String PASSWORD;

    static {
        Properties props = new Properties();
        try (InputStream input = DatabaseConnection.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                throw new DataAccessException(
                        CONFIG_FILE + " bulunamadı. config.properties.example dosyasını kopyalayıp ayarlarını yapmalısın."
                );
            }
            props.load(input);
        } catch (IOException e) {
            throw new DataAccessException(CONFIG_FILE + " okunurken hata oluştu.", e);
        }

        URL = requireProperty(props, "db.url");
        USER = requireProperty(props, "db.user");
        PASSWORD = requireProperty(props, "db.password");
    }

    private DatabaseConnection() {
    }

    private static String requireProperty(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new DataAccessException(CONFIG_FILE + " içinde '" + key + "' değeri eksik veya boş.");
        }
        return value.trim();
    }

    public static Connection getConnection() {
        try {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (SQLException e) {
            throw new DataAccessException("PostgreSQL veritabanına bağlanılamadı.", e);
        }
    }

    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && conn.isValid(3);
        } catch (DataAccessException | SQLException e) {
            System.err.println("Veritabanı bağlantı hatası: " + e.getMessage());
            return false;
        }
    }
}