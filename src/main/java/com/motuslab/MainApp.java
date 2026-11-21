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
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.Objects;

public class MainApp extends Application {

    private static final String APP_STYLESHEET = "/css/motuslab.css";
    private static final String APP_ICON = "/images/logo-icon.png";

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
        loadAppIcon();
        showAuthView();
        primaryStage.show();
    }

    private void loadAppIcon() {
        try (InputStream is = getClass().getResourceAsStream(APP_ICON)) {
            if (is != null) {
                primaryStage.getIcons().add(new Image(is));
            }
        } catch (Exception ignored) {
            // İkon yüklenemezse uygulama varsayılan ikonla açılır.
        }
    }

    private void showAuthView() {
        AuthView authView = new AuthView(authService, this::showDashboard);
        primaryStage.setMinWidth(440);
        primaryStage.setMinHeight(520);
        primaryStage.setScene(createScene(authView, 480, 560));
        primaryStage.centerOnScreen();
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
        primaryStage.setMinWidth(960);
        primaryStage.setMinHeight(640);
        primaryStage.setScene(createScene(dashboardView, 1200, 780));
        primaryStage.centerOnScreen();
    }

    private Scene createScene(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        String css = Objects.requireNonNull(
                getClass().getResource(APP_STYLESHEET),
                "Stylesheet bulunamadı: " + APP_STYLESHEET
        ).toExternalForm();
        scene.getStylesheets().add(css);
        return scene;
    }

    public static void main(String[] args) {
        launch(args);
    }
}