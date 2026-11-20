package com.motuslab;

import atlantafx.base.theme.PrimerLight;
import com.motuslab.model.User;
import com.motuslab.service.AuthService;
import com.motuslab.service.ExperimentService;
import com.motuslab.ui.AuthView;
import com.motuslab.ui.DashboardView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    private final AuthService authService = new AuthService();
    private final ExperimentService experimentService = new ExperimentService();
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
        AuthView authView = new AuthView(authService, this::showDashboard);
        primaryStage.setScene(new Scene(authView, 480, 520));
    }

    private void showDashboard(User user) {
        DashboardView dashboardView = new DashboardView(user, experimentService, () -> {
            authService.logout();
            showAuthView();
        });
        primaryStage.setScene(new Scene(dashboardView, 960, 620));
    }

    public static void main(String[] args) {
        launch(args);
    }
}