package com.motuslab;

import atlantafx.base.theme.PrimerLight;
import com.motuslab.config.DatabaseConnection;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        boolean connected = DatabaseConnection.testConnection();

        Label titleLabel = new Label("MotusLab");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        Label statusLabel = new Label(
                connected ? "PostgreSQL Bağlantısı: Başarılı" : "PostgreSQL Bağlantısı: Başarısız"
        );
        statusLabel.setStyle(
                connected ? "-fx-text-fill: #1a7f37; -fx-font-size: 14px;"
                        : "-fx-text-fill: #cf222e; -fx-font-size: 14px;"
        );

        VBox root = new VBox(12, titleLabel, statusLabel);
        root.setAlignment(Pos.CENTER);

        Scene scene = new Scene(root, 480, 260);
        stage.setTitle("MotusLab — Sistem Testi");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}