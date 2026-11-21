package com.motuslab.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;
import java.net.URL;

public final class UiHelper {

    private static final String STYLESHEET = "/css/motuslab.css";
    private static final String APP_ICON = "/images/logo-icon.png";

    private UiHelper() {
    }

    public static void styleDialog(Dialog<?> dialog) {
        URL css = UiHelper.class.getResource(STYLESHEET);
        if (css != null) {
            dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
        }
        if (dialog.getDialogPane().getScene() != null
                && dialog.getDialogPane().getScene().getWindow() instanceof Stage stage) {
            applyIcon(stage);
        } else {
            dialog.setOnShowing(e -> {
                if (dialog.getDialogPane().getScene() != null
                        && dialog.getDialogPane().getScene().getWindow() instanceof Stage stage) {
                    applyIcon(stage);
                }
            });
        }
    }

    public static void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Hata");
        alert.setHeaderText(header);
        alert.setContentText(message);
        styleDialog(alert);
        alert.showAndWait();
    }

    public static boolean showConfirm(String title, String header, String content) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(title);
        confirm.setHeaderText(header);
        confirm.setContentText(content);
        styleDialog(confirm);
        return confirm.showAndWait().filter(r -> r == ButtonType.OK).isPresent();
    }

    public static void setFeedback(Label label, String message, boolean isError) {
        label.setText(message);
        label.getStyleClass().removeAll("ml-feedback-error", "ml-feedback-success");
        label.getStyleClass().add(isError ? "ml-feedback-error" : "ml-feedback-success");
        label.setVisible(true);
        label.setManaged(true);
    }

    public static void clearFeedback(Label label) {
        label.setText("");
        label.setVisible(false);
        label.setManaged(false);
    }

    private static void applyIcon(Stage stage) {
        if (!stage.getIcons().isEmpty()) {
            return;
        }
        try (InputStream is = UiHelper.class.getResourceAsStream(APP_ICON)) {
            if (is != null) {
                stage.getIcons().add(new Image(is));
            }
        } catch (Exception ignored) {
        }
    }
}