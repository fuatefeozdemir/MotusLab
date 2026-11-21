package com.motuslab.ui;

import com.motuslab.exception.MotusLabException;
import com.motuslab.model.User;
import com.motuslab.service.AuthService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.InputStream;
import java.util.function.Consumer;

public class AuthView extends VBox {

    private static final String LOGO_FULL = "/images/logo-full.png";

    private final AuthService authService;
    private final Consumer<User> onLoginSuccess;

    private boolean registerMode = false;

    private final Label subtitleLabel = new Label("Hesabınıza giriş yapın");

    private final TextField identifierField = new TextField();
    private final TextField usernameField = new TextField();
    private final TextField emailField = new TextField();
    private final TextField fullNameField = new TextField();
    private final PasswordField passwordField = new PasswordField();

    private final Label feedbackLabel = new Label();
    private final Button submitButton = new Button("Giriş Yap");
    private final Hyperlink toggleModeLink = new Hyperlink("Hesabın yok mu? Kayıt ol");

    private final VBox formBox = new VBox(10);

    public AuthView(AuthService authService, Consumer<User> onLoginSuccess) {
        this.authService = authService;
        this.onLoginSuccess = onLoginSuccess;

        initLayout();
        renderForm();
    }

    private void initLayout() {
        setAlignment(Pos.CENTER);
        setPadding(new Insets(32));

        Node brandHeader = createBrandHeader();

        subtitleLabel.getStyleClass().add("ml-text-secondary");

        VBox headerBox = new VBox(8, brandHeader, subtitleLabel);
        headerBox.setAlignment(Pos.CENTER);

        identifierField.setPromptText("Kullanıcı adı veya e-posta");
        usernameField.setPromptText("Kullanıcı adı");
        emailField.setPromptText("E-posta");
        fullNameField.setPromptText("Ad Soyad (opsiyonel)");
        passwordField.setPromptText("Şifre");

        feedbackLabel.setWrapText(true);
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);

        submitButton.getStyleClass().add("ml-btn-primary");
        submitButton.setMaxWidth(Double.MAX_VALUE);
        submitButton.setDefaultButton(true);
        submitButton.setOnAction(e -> handleSubmit());

        toggleModeLink.setOnAction(e -> {
            registerMode = !registerMode;
            clearFeedback();
            renderForm();
        });

        HBox footerBox = new HBox(toggleModeLink);
        footerBox.setAlignment(Pos.CENTER);

        VBox card = new VBox(16, headerBox, formBox, feedbackLabel, submitButton, footerBox);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(380);
        card.getStyleClass().add("ml-auth-card");

        getChildren().add(card);
    }

    private Node createBrandHeader() {
        try (InputStream is = getClass().getResourceAsStream(LOGO_FULL)) {
            if (is != null) {
                ImageView logoView = new ImageView(new Image(is));
                logoView.setPreserveRatio(true);
                logoView.setFitHeight(44);
                logoView.setSmooth(true);
                return logoView;
            }
        } catch (Exception ignored) {
            // Görsel bulunamazsa metin başlığa düşer.
        }
        Label fallback = new Label("MotusLab");
        fallback.getStyleClass().add("ml-brand-title");
        return fallback;
    }

    private void renderForm() {
        formBox.getChildren().clear();
        passwordField.clear();

        if (registerMode) {
            subtitleLabel.setText("Yeni araştırmacı hesabı oluşturun");
            submitButton.setText("Kayıt Ol");
            toggleModeLink.setText("Zaten hesabın var mı? Giriş yap");
            formBox.getChildren().addAll(usernameField, emailField, fullNameField, passwordField);
        } else {
            subtitleLabel.setText("Hesabınıza giriş yapın");
            submitButton.setText("Giriş Yap");
            toggleModeLink.setText("Hesabın yok mu? Kayıt ol");
            formBox.getChildren().addAll(identifierField, passwordField);
        }
    }

    private void handleSubmit() {
        clearFeedback();
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
                showFeedback("Kayıt başarılı. Şimdi giriş yapabilirsiniz.", false);
            } else {
                User user = authService.login(identifierField.getText(), passwordField.getText());
                onLoginSuccess.accept(user);
            }
        } catch (MotusLabException ex) {
            showFeedback(ex.getMessage(), true);
        }
    }

    private void showFeedback(String message, boolean isError) {
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().removeAll("ml-feedback-error", "ml-feedback-success");
        feedbackLabel.getStyleClass().add(isError ? "ml-feedback-error" : "ml-feedback-success");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }

    private void clearFeedback() {
        feedbackLabel.setText("");
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);
    }
}