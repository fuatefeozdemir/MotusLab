package com.motuslab.ui;

import com.motuslab.exception.MotusLabException;
import com.motuslab.model.Experiment;
import com.motuslab.service.ExperimentService;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public final class EditExperimentDialog {

    private EditExperimentDialog() {
    }

    public static boolean show(Experiment experiment, ExperimentService experimentService) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Deneyi Düzenle");
        dialog.setHeaderText("Deney başlığını ve açıklamasını güncelleyin");
        UiHelper.styleDialog(dialog);

        ButtonType saveBtnType = new ButtonType("Kaydet", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        TextField titleField = new TextField(experiment.getTitle() != null ? experiment.getTitle() : "");
        titleField.setPromptText("Deney başlığı");

        TextArea descArea = new TextArea(experiment.getDescription() != null ? experiment.getDescription() : "");
        descArea.setPromptText("Açıklama (opsiyonel)");
        descArea.setPrefRowCount(4);
        descArea.setWrapText(true);

        VBox content = new VBox(10,
                new Label("Başlık"), titleField,
                new Label("Açıklama"), descArea
        );
        content.setPrefWidth(400);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(button -> {
            if (button == saveBtnType) {
                try {
                    experimentService.updateExperimentDetails(
                            experiment.getId(),
                            titleField.getText(),
                            descArea.getText()
                    );
                    return true;
                } catch (MotusLabException ex) {
                    UiHelper.showError("Deney güncellenemedi", ex.getMessage());
                    return false;
                }
            }
            return false;
        });

        return dialog.showAndWait().orElse(false);
    }
}