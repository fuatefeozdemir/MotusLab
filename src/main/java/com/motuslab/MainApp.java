package com.motuslab;

import atlantafx.base.theme.PrimerLight;
import com.motuslab.model.User;
import com.motuslab.service.AuthService;
import com.motuslab.service.ExperimentMemberService;
import com.motuslab.service.ExperimentService;
import com.motuslab.service.SimulationRunService;
import com.motuslab.ui.AuthView;
import com.motuslab.ui.DashboardView;
import com.motuslab.ui.component.WindowTitleBar;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.InputStream;
import java.util.Objects;

public class MainApp extends Application {

    private static final String APP_STYLESHEET = "/css/motuslab.css";
    private static final String APP_ICON = "/images/logo-icon.png";
    private static final String[] APP_FONTS = {
            "/fonts/Inter-Regular.ttf",
            "/fonts/Inter-Medium.ttf",
            "/fonts/Inter-Bold.ttf",
            "/fonts/JetBrainsMono-Regular.ttf",
            "/fonts/JetBrainsMono-Bold.ttf"
    };

    private final AuthService authService = new AuthService();
    private final ExperimentService experimentService = new ExperimentService();
    private final SimulationRunService simulationRunService = new SimulationRunService();
    private final ExperimentMemberService memberService = new ExperimentMemberService();
    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        primaryStage.initStyle(StageStyle.UNDECORATED);
        primaryStage.setTitle("MotusLab");
        loadAppIcon();
        loadFonts();
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

    private void loadFonts() {
        for (String path : APP_FONTS) {
            try (InputStream is = getClass().getResourceAsStream(path)) {
                if (is != null) {
                    Font.loadFont(is, 14);
                }
            } catch (Exception ignored) {
                // Font yüklenemezse CSS fallback fontları devreye girer.
            }
        }
    }

    private void showAuthView() {
        WindowTitleBar.resetMaximizeState(primaryStage);
        AuthView authView = new AuthView(authService, this::showDashboard);
        primaryStage.setMinWidth(680);
        primaryStage.setMinHeight(580);
        primaryStage.setScene(createScene(authView, 920, 660));
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
        primaryStage.setMinWidth(1040);
        primaryStage.setMinHeight(660);
        WindowTitleBar.maximizeToVisualBounds(primaryStage);
        primaryStage.setScene(createScene(dashboardView, primaryStage.getWidth(), primaryStage.getHeight()));
    }

    private Scene createScene(Parent content, double width, double height) {
        BorderPane frame = new BorderPane();
        frame.getStyleClass().add("ml-window-frame");
        frame.setTop(new WindowTitleBar(primaryStage, "MotusLab"));
        frame.setCenter(content);

        Scene scene = new Scene(frame, width, height);
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