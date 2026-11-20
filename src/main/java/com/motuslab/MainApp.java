package com.motuslab;

import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Styles;
import com.motuslab.model.User;
import com.motuslab.service.AuthService;
import com.motuslab.ui.AuthView;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class MainApp extends Application {

    private final AuthService authService = new AuthService();
    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        primaryStage.setTitle("MotusLab");
        showAuthView();
        primaryStage.show();
    }

    private void showAuthView() {
        AuthView authView = new AuthView(authService, this::showHomePlaceholder);
        primaryStage.setScene(new Scene(authView, 480, 520));
    }

    private void showHomePlaceholder(User user) {
        Label welcomeLabel = new Label("Hoş geldin, " + user.getUsername());
        welcomeLabel.getStyleClass().add(Styles.TITLE_2);

        Label roleLabel = new Label("Rol: " + user.getRoleName());
        roleLabel.getStyleClass().add(Styles.TEXT_MUTED);

        Button logoutButton = new Button("Çıkış Yap");
        logoutButton.setOnAction(e -> {
            authService.logout();
            showAuthView();
        });

        VBox root = new VBox(12, welcomeLabel, roleLabel, logoutButton);
        root.setAlignment(Pos.CENTER);

        primaryStage.setScene(new Scene(root, 640, 420));
    }

    public static void main(String[] args) {
        launch(args);
    }
}