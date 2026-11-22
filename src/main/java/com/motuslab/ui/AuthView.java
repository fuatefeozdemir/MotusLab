package com.motuslab.ui;

import com.motuslab.exception.MotusLabException;
import com.motuslab.model.User;
import com.motuslab.service.AuthService;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

public class AuthView extends StackPane {

    private static final String LOGO_FULL = "/images/logo-full.png";
    private static final Interpolator EASE_OUT = Interpolator.SPLINE(0.22, 0.61, 0.36, 1.0);

    private final AuthService authService;
    private final Consumer<User> onLoginSuccess;

    private boolean registerMode = false;
    private boolean showPassword = false;

    private final TextField identifierField = new TextField();
    private final TextField usernameField = new TextField();
    private final TextField emailField = new TextField();
    private final TextField fullNameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final TextField visiblePasswordField = new TextField();
    private final Button eyeButton = new Button();

    private final Label feedbackLabel = new Label();
    private final Button submitButton = new Button("Giriş Yap");
    private final Label footerPromptLabel = new Label("Hesabınız yok mu?");
    private final Hyperlink toggleModeLink = new Hyperlink("Kayıt ol");

    private final VBox formBox = new VBox(12);
    private final VBox card = new VBox(18);

    private final Canvas particleCanvas = new Canvas();
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();
    private AnimationTimer particleTimer;

    private static final class Particle {
        double x, y, v, o;
        boolean accent;
    }

    public AuthView(AuthService authService, Consumer<User> onLoginSuccess) {
        this.authService = authService;
        this.onLoginSuccess = onLoginSuccess;

        getStyleClass().add("ml-auth-root");

        Pane gridOverlay = buildAnimatedGridOverlay();
        setupParticleLayer();
        StackPane contentLayer = buildContentLayer();

        getChildren().addAll(gridOverlay, particleCanvas, contentLayer);

        renderForm();
        playCardEntranceAnimation();
    }

    private StackPane buildContentLayer() {
        Node brandHeader = createBrandHeader();
        HBox logoBox = new HBox(brandHeader);
        logoBox.setAlignment(Pos.CENTER);

        configureInputs();

        feedbackLabel.setWrapText(true);
        UiHelper.clearFeedback(feedbackLabel);

        submitButton.getStyleClass().add("ml-btn-primary");
        submitButton.setGraphicTextGap(8);
        submitButton.setMaxWidth(Double.MAX_VALUE);
        submitButton.setMinHeight(40);
        submitButton.setDefaultButton(true);
        submitButton.setOnAction(e -> handleSubmit());

        toggleModeLink.setOnAction(e -> toggleMode());
        footerPromptLabel.getStyleClass().add("ml-text-secondary");

        HBox switchRow = new HBox(4, footerPromptLabel, toggleModeLink);
        switchRow.setAlignment(Pos.CENTER);

        VBox footerBox = new VBox(10, new Separator(), switchRow);
        footerBox.setAlignment(Pos.CENTER);

        card.getChildren().setAll(logoBox, formBox, feedbackLabel, submitButton, footerBox);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(390);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.getStyleClass().add("ml-auth-card");

        StackPane centerWrapper = new StackPane(card);
        centerWrapper.setPadding(new Insets(24));
        return centerWrapper;
    }

    private void configureInputs() {
        identifierField.setPromptText("kullaniciadi veya ornek@mail.com");
        usernameField.setPromptText("kullaniciadi");
        emailField.setPromptText("ornek@mail.com");
        fullNameField.setPromptText("Ad Soyad (opsiyonel)");
        passwordField.setPromptText("••••••••");
        visiblePasswordField.setPromptText("••••••••");

        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());
        visiblePasswordField.setVisible(false);
        visiblePasswordField.setManaged(false);

        eyeButton.getStyleClass().add("ml-eye-btn");
        eyeButton.setFocusTraversable(false);
        eyeButton.setGraphic(UiHelper.icon(Feather.EYE, 15));
        eyeButton.setOnAction(e -> togglePasswordVisibility());
    }

    private void togglePasswordVisibility() {
        showPassword = !showPassword;
        visiblePasswordField.setVisible(showPassword);
        visiblePasswordField.setManaged(showPassword);
        passwordField.setVisible(!showPassword);
        passwordField.setManaged(!showPassword);
        eyeButton.setGraphic(UiHelper.icon(showPassword ? Feather.EYE_OFF : Feather.EYE, 15));
    }

    private void toggleMode() {
        registerMode = !registerMode;
        UiHelper.clearFeedback(feedbackLabel);
        renderForm();
    }

    private VBox labeledIconField(String labelText, Feather icon, TextInputControl input) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("ml-text-bold");

        input.getStyleClass().add("ml-input-bare");
        HBox.setHgrow(input, Priority.ALWAYS);

        FontIcon leadingIcon = UiHelper.icon(icon, 15);
        HBox group = new HBox(6, leadingIcon, input);
        group.setAlignment(Pos.CENTER_LEFT);
        group.getStyleClass().add("ml-input-group");

        input.focusedProperty().addListener((obs, oldV, focused) -> {
            group.getStyleClass().remove("ml-input-group-focused");
            if (focused) {
                group.getStyleClass().add("ml-input-group-focused");
            }
        });

        return new VBox(6, lbl, group);
    }

    private VBox labeledPasswordField() {
        Label lbl = new Label("Şifre");
        lbl.getStyleClass().add("ml-text-bold");

        passwordField.getStyleClass().add("ml-input-bare");
        visiblePasswordField.getStyleClass().add("ml-input-bare");

        StackPane inputStack = new StackPane(passwordField, visiblePasswordField);
        HBox.setHgrow(inputStack, Priority.ALWAYS);

        FontIcon lockIcon = UiHelper.icon(Feather.LOCK, 15);
        HBox group = new HBox(6, lockIcon, inputStack, eyeButton);
        group.setAlignment(Pos.CENTER_LEFT);
        group.getStyleClass().add("ml-input-group");

        Runnable syncFocus = () -> {
            boolean focused = passwordField.isFocused() || visiblePasswordField.isFocused();
            group.getStyleClass().remove("ml-input-group-focused");
            if (focused) {
                group.getStyleClass().add("ml-input-group-focused");
            }
        };
        passwordField.focusedProperty().addListener((obs, o, n) -> syncFocus.run());
        visiblePasswordField.focusedProperty().addListener((obs, o, n) -> syncFocus.run());

        return new VBox(6, lbl, group);
    }

    private void renderForm() {
        formBox.getChildren().clear();
        passwordField.clear();
        if (showPassword) {
            togglePasswordVisibility();
        }

        if (registerMode) {
            submitButton.setText("Kayıt Ol");
            submitButton.setGraphic(UiHelper.icon(Feather.USER_PLUS, 15, "ml-icon-light"));
            footerPromptLabel.setText("Zaten hesabınız var mı?");
            toggleModeLink.setText("Giriş yap");

            formBox.getChildren().addAll(
                    labeledIconField("Kullanıcı Adı", Feather.AT_SIGN, usernameField),
                    labeledIconField("E-posta", Feather.MAIL, emailField),
                    labeledIconField("Ad Soyad", Feather.USER, fullNameField),
                    labeledPasswordField()
            );
        } else {
            submitButton.setText("Giriş Yap");
            submitButton.setGraphic(UiHelper.icon(Feather.LOG_IN, 15, "ml-icon-light"));
            footerPromptLabel.setText("Hesabınız yok mu?");
            toggleModeLink.setText("Kayıt ol");

            formBox.getChildren().addAll(
                    labeledIconField("Kullanıcı Adı veya E-posta", Feather.MAIL, identifierField),
                    labeledPasswordField()
            );
        }
    }

    private void handleSubmit() {
        UiHelper.clearFeedback(feedbackLabel);
        try {
            if (registerMode) {
                authService.register(
                        usernameField.getText(),
                        emailField.getText(),
                        passwordField.getText(),
                        fullNameField.getText()
                );
                String createdUsername = usernameField.getText().trim();
                registerMode = false;
                renderForm();
                identifierField.setText(createdUsername);
                UiHelper.setFeedback(feedbackLabel, "Kayıt başarılı. Şimdi giriş yapabilirsiniz.", false);
            } else {
                User user = authService.login(identifierField.getText(), passwordField.getText());
                onLoginSuccess.accept(user);
            }
        } catch (MotusLabException ex) {
            UiHelper.setFeedback(feedbackLabel, ex.getMessage(), true);
        }
    }

    private Node createBrandHeader() {
        try (InputStream is = getClass().getResourceAsStream(LOGO_FULL)) {
            if (is != null) {
                ImageView logoView = new ImageView(new Image(is));
                logoView.setPreserveRatio(true);
                logoView.setFitHeight(42);
                logoView.setSmooth(true);
                return logoView;
            }
        } catch (Exception ignored) {
        }
        Label fallback = new Label("MotusLab");
        fallback.getStyleClass().add("ml-brand-title");
        return fallback;
    }

    private Pane buildAnimatedGridOverlay() {
        Pane overlay = new Pane();
        overlay.setMouseTransparent(true);

        double[] hPositions = {0.18, 0.50, 0.82};
        double[] vPositions = {0.22, 0.50, 0.78};

        for (int i = 0; i < hPositions.length; i++) {
            Region line = new Region();
            line.getStyleClass().add("ml-auth-grid-line");
            line.setPrefHeight(1);
            line.prefWidthProperty().bind(overlay.widthProperty());
            line.layoutYProperty().bind(overlay.heightProperty().multiply(hPositions[i]));
            line.setScaleX(0);
            line.setOpacity(0);
            overlay.getChildren().add(line);
            animateLine(line, true, 120 + i * 100);
        }

        for (int i = 0; i < vPositions.length; i++) {
            Region line = new Region();
            line.getStyleClass().add("ml-auth-grid-line");
            line.setPrefWidth(1);
            line.prefHeightProperty().bind(overlay.heightProperty());
            line.layoutXProperty().bind(overlay.widthProperty().multiply(vPositions[i]));
            line.setScaleY(0);
            line.setOpacity(0);
            overlay.getChildren().add(line);
            animateLine(line, false, 420 + i * 120);
        }

        return overlay;
    }

    private void animateLine(Region line, boolean horizontal, int delayMs) {
        ScaleTransition st = new ScaleTransition(Duration.millis(horizontal ? 800 : 900), line);
        if (horizontal) {
            st.setFromX(0);
            st.setToX(1);
        } else {
            st.setFromY(0);
            st.setToY(1);
        }
        st.setInterpolator(EASE_OUT);

        FadeTransition ft = new FadeTransition(Duration.millis(horizontal ? 800 : 900), line);
        ft.setFromValue(0);
        ft.setToValue(0.85);
        ft.setInterpolator(EASE_OUT);

        ParallelTransition pt = new ParallelTransition(st, ft);
        pt.setDelay(Duration.millis(delayMs));
        pt.play();
    }

    private void playCardEntranceAnimation() {
        card.setOpacity(0);
        card.setTranslateY(20);

        FadeTransition ft = new FadeTransition(Duration.millis(700), card);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.setInterpolator(EASE_OUT);

        TranslateTransition tt = new TranslateTransition(Duration.millis(700), card);
        tt.setFromY(20);
        tt.setToY(0);
        tt.setInterpolator(EASE_OUT);

        ParallelTransition pt = new ParallelTransition(ft, tt);
        pt.setDelay(Duration.millis(250));
        pt.play();
    }

    private void setupParticleLayer() {
        particleCanvas.setMouseTransparent(true);
        particleCanvas.widthProperty().bind(widthProperty());
        particleCanvas.heightProperty().bind(heightProperty());

        particleCanvas.widthProperty().addListener((obs, o, n) -> initParticles());
        particleCanvas.heightProperty().addListener((obs, o, n) -> initParticles());

        particleTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                drawParticles();
            }
        };

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                initParticles();
                particleTimer.start();
            } else if (particleTimer != null) {
                particleTimer.stop();
            }
        });
    }

    private void initParticles() {
        double w = particleCanvas.getWidth();
        double h = particleCanvas.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        particles.clear();
        int count = Math.min(120, Math.max(24, (int) ((w * h) / 9500)));
        for (int i = 0; i < count; i++) {
            particles.add(createParticle(w, h, false));
        }
    }

    private Particle createParticle(double w, double h, boolean fromBottom) {
        Particle p = new Particle();
        p.x = random.nextDouble() * w;
        p.y = fromBottom ? h + random.nextDouble() * 40 : random.nextDouble() * h;
        p.v = random.nextDouble() * 0.30 + 0.08;
        p.o = random.nextDouble() * 0.22 + 0.08;
        p.accent = random.nextDouble() < 0.25;
        return p;
    }

    private void drawParticles() {
        double w = particleCanvas.getWidth();
        double h = particleCanvas.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        GraphicsContext gc = particleCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        for (Particle p : particles) {
            p.y -= p.v;
            if (p.y < -5) {
                p.x = random.nextDouble() * w;
                p.y = h + random.nextDouble() * 40;
                p.v = random.nextDouble() * 0.30 + 0.08;
                p.o = random.nextDouble() * 0.22 + 0.08;
            }
            gc.setFill(p.accent
                    ? Color.rgb(215, 16, 97, p.o)
                    : Color.rgb(32, 45, 62, p.o));
            gc.fillRect(p.x, p.y, 1.2, 3.0);
        }
    }
}