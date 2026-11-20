package com.motuslab;

import atlantafx.base.theme.PrimerLight;
import com.motuslab.model.User;
import com.motuslab.service.AuthService;
import com.motuslab.service.ExperimentMemberService;
import com.motuslab.service.ExperimentService;
import com.motuslab.service.SimulationRunService;
import com.motuslab.ui.AuthView;
import com.motuslab.ui.DashboardView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    private final AuthService authService = new AuthService();
    private final ExperimentService experimentService = new ExperimentService();
    private final SimulationRunService simulationRunService = new SimulationRunService();
    private final ExperimentMemberService memberService = new ExperimentMemberService();
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
        DashboardView dashboardView = new DashboardView(
                user,
                experimentService,
                simulationRunService,
                memberService,
                () -> {
                    authService.logout();
                    showAuthView();
                }
        );
        primaryStage.setScene(new Scene(dashboardView, 1180, 760));
    }

    public static void main(String[] args) {
        launch(args);
    }
}