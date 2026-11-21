package com.motuslab.ui;

import com.motuslab.exception.MotusLabException;
import com.motuslab.model.User;
import com.motuslab.service.AuthService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

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

    private final VBox formBox = new VBox(12);

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

        identifierField.setPromptText("kullaniciadi veya ornek@mail.com");
        usernameField.setPromptText("kullaniciadi");
        emailField.setPromptText("ornek@mail.com");
        fullNameField.setPromptText("Ad Soyad (opsiyonel)");
        passwordField.setPromptText("••••••••");

        feedbackLabel.setWrapText(true);
        UiHelper.clearFeedback(feedbackLabel);

        submitButton.getStyleClass().add("ml-btn-primary");
        submitButton.setGraphicTextGap(8);
        submitButton.setMaxWidth(Double.MAX_VALUE);
        submitButton.setDefaultButton(true);
        submitButton.setOnAction(e -> handleSubmit());

        toggleModeLink.setOnAction(e -> {
            registerMode = !registerMode;
            UiHelper.clearFeedback(feedbackLabel);
            renderForm();
        });

        HBox footerBox = new HBox(toggleModeLink);
        footerBox.setAlignment(Pos.CENTER);

        VBox card = new VBox(18, headerBox, formBox, feedbackLabel, submitButton, footerBox);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(390);
        card.getStyleClass().add("ml-auth-card");

        getChildren().add(card);
    }

    private Node createBrandHeader() {
        try (InputStream is = getClass().getResourceAsStream(LOGO_FULL)) {
            if (is != null) {
                ImageView logoView = new ImageView(new Image(is));
                logoView.setPreserveRatio(true);
                logoView.setFitHeight(46);
                logoView.setSmooth(true);
                return logoView;
            }
        } catch (Exception ignored) {
        }
        Label fallback = new Label("MotusLab");
        fallback.getStyleClass().add("ml-brand-title");
        return fallback;
    }

    private VBox labeledField(String labelText, Control input) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("ml-text-secondary");
        VBox box = new VBox(5, lbl, input);
        return box;
    }

    private void renderForm() {
        formBox.getChildren().clear();
        passwordField.clear();

        if (registerMode) {
            subtitleLabel.setText("Yeni araştırmacı hesabı oluşturun");
            submitButton.setText("Kayıt Ol");
            submitButton.setGraphic(UiHelper.icon(Feather.USER_PLUS, 15, "ml-icon-light"));
            toggleModeLink.setText("Zaten hesabın var mı? Giriş yap");
            formBox.getChildren().addAll(
                    labeledField("Kullanıcı Adı", usernameField),
                    labeledField("E-posta", emailField),
                    labeledField("Ad Soyad", fullNameField),
                    labeledField("Şifre", passwordField)
            );
        } else {
            subtitleLabel.setText("Hesabınıza giriş yapın");
            submitButton.setText("Giriş Yap");
            submitButton.setGraphic(UiHelper.icon(Feather.LOG_IN, 15, "ml-icon-light"));
            toggleModeLink.setText("Hesabın yok mu? Kayıt ol");
            formBox.getChildren().addAll(
                    labeledField("Kullanıcı Adı veya E-posta", identifierField),
                    labeledField("Şifre", passwordField)
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
}