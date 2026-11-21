package com.motuslab.ui;

import com.motuslab.exception.MotusLabException;
import com.motuslab.model.Experiment;
import com.motuslab.model.SimulationType;
import com.motuslab.service.ExperimentService;
import javafx.collections.FXCollections;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

public final class NewExperimentDialog {

    private NewExperimentDialog() {
    }

    public static Optional<Experiment> show(ExperimentService experimentService) {
        List<SimulationType> simTypes;
        try {
            simTypes = experimentService.getAllSimulationTypes();
        } catch (MotusLabException ex) {
            UiHelper.showError("Simülasyon türleri alınamadı", ex.getMessage());
            return Optional.empty();
        }

        Dialog<Experiment> dialog = new Dialog<>();
        dialog.setTitle("Yeni Deney Oluştur");
        dialog.setHeaderText("Deney bilgilerini girin");
        UiHelper.styleDialog(dialog);

        ButtonType createBtnType = new ButtonType("Oluştur", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createBtnType, ButtonType.CANCEL);

        TextField titleField = new TextField();
        titleField.setPromptText("Örn: 45 Derece Eğik Atış");

        ComboBox<SimulationType> simTypeBox = new ComboBox<>(FXCollections.observableArrayList(simTypes));
        simTypeBox.setMaxWidth(Double.MAX_VALUE);
        if (!simTypes.isEmpty()) {
            simTypeBox.getSelectionModel().selectFirst();
        }

        TextArea descArea = new TextArea();
        descArea.setPromptText("Açıklama (opsiyonel)");
        descArea.setPrefRowCount(3);

        VBox content = new VBox(10,
                new Label("Başlık"), titleField,
                new Label("Simülasyon Türü"), simTypeBox,
                new Label("Açıklama"), descArea
        );
        content.setPrefWidth(360);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(button -> {
            if (button == createBtnType) {
                SimulationType selectedType = simTypeBox.getValue();
                if (selectedType == null) {
                    return null;
                }
                try {
                    return experimentService.createExperiment(
                            selectedType.getId(),
                            titleField.getText(),
                            descArea.getText(),
                            null
                    );
                } catch (MotusLabException ex) {
                    UiHelper.showError("Deney oluşturulamadı", ex.getMessage());
                    return null;
                }
            }
            return null;
        });

        return dialog.showAndWait();
    }
}