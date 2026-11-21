package com.motuslab.ui;

import com.motuslab.exception.MotusLabException;
import com.motuslab.model.SimulationRun;
import com.motuslab.service.SimulationRunService;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public final class EditRunDialog {

    private EditRunDialog() {
    }

    public static boolean show(SimulationRun run, SimulationRunService simulationRunService) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Çalıştırmayı Düzenle");
        dialog.setHeaderText("Run #" + run.getRunNumber() + " — Ad ve Notlar");
        UiHelper.styleDialog(dialog);

        ButtonType saveBtnType = new ButtonType("Kaydet", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        TextField nameField = new TextField(run.getName() != null ? run.getName() : "");
        nameField.setPromptText("Opsiyonel ad (örn: Yüksek hız testi)");

        TextArea notesArea = new TextArea(run.getNotes() != null ? run.getNotes() : "");
        notesArea.setPromptText("Bu çalıştırmaya ait gözlem veya notlar...");
        notesArea.setPrefRowCount(4);
        notesArea.setWrapText(true);

        VBox content = new VBox(10,
                new Label("Çalıştırma Adı"), nameField,
                new Label("Notlar"), notesArea
        );
        content.setPrefWidth(400);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(button -> {
            if (button == saveBtnType) {
                try {
                    simulationRunService.updateRunDetails(run.getId(), nameField.getText(), notesArea.getText());
                    return true;
                } catch (MotusLabException ex) {
                    UiHelper.showError("Çalıştırma güncellenemedi", ex.getMessage());
                    return false;
                }
            }
            return false;
        });

        return dialog.showAndWait().orElse(false);
    }
}