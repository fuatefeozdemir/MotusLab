package com.motuslab.ui;

import atlantafx.base.theme.Styles;
import com.motuslab.exception.MotusLabException;
import com.motuslab.model.Experiment;
import com.motuslab.model.ExperimentParameter;
import com.motuslab.model.SimulationType;
import com.motuslab.model.User;
import com.motuslab.service.ExperimentService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DashboardView extends BorderPane {

    private final User currentUser;
    private final ExperimentService experimentService;
    private final Runnable onLogout;

    private final ListView<Experiment> experimentListView = new ListView<>();
    private final VBox detailContainer = new VBox(16);
    private final Label feedbackLabel = new Label();

    private final Map<Integer, TextField> parameterInputs = new LinkedHashMap<>();
    private Experiment selectedExperiment;

    public DashboardView(User currentUser, ExperimentService experimentService, Runnable onLogout) {
        this.currentUser = currentUser;
        this.experimentService = experimentService;
        this.onLogout = onLogout;

        initTopBar();
        initLeftPanel();
        initCenterPanel();
        loadExperiments(null);
    }

    private void initTopBar() {
        Label brandLabel = new Label("MotusLab");
        brandLabel.getStyleClass().add(Styles.TITLE_3);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label userLabel = new Label(currentUser.getUsername() + " (" + currentUser.getRoleName() + ")");
        userLabel.getStyleClass().add(Styles.TEXT_MUTED);

        Button logoutButton = new Button("Çıkış Yap");
        logoutButton.setOnAction(e -> onLogout.run());

        HBox topBar = new HBox(12, brandLabel, spacer, userLabel, logoutButton);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(12, 20, 12, 20));
        topBar.setStyle("-fx-border-color: transparent transparent -color-border-default transparent;");

        setTop(topBar);
    }

    private void initLeftPanel() {
        Label headerLabel = new Label("Deneylerim");
        headerLabel.getStyleClass().add(Styles.TITLE_4);

        Button newExpButton = new Button("+ Yeni Deney");
        newExpButton.getStyleClass().add(Styles.ACCENT);
        newExpButton.setMaxWidth(Double.MAX_VALUE);
        newExpButton.setOnAction(e -> openNewExperimentDialog());

        experimentListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Experiment item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label title = new Label(item.getTitle());
                    title.getStyleClass().add(Styles.TEXT_BOLD);
                    Label type = new Label(item.getSimulationTypeName());
                    type.getStyleClass().add(Styles.TEXT_MUTED);
                    VBox box = new VBox(2, title, type);
                    box.setPadding(new Insets(4, 0, 4, 0));
                    setGraphic(box);
                }
            }
        });

        experimentListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectedExperiment = newVal;
            renderExperimentDetails();
        });

        VBox.setVgrow(experimentListView, Priority.ALWAYS);

        VBox leftPanel = new VBox(12, headerLabel, newExpButton, experimentListView);
        leftPanel.setPrefWidth(280);
        leftPanel.setPadding(new Insets(16));
        leftPanel.setStyle("-fx-border-color: transparent -color-border-default transparent transparent;");

        setLeft(leftPanel);
    }

    private void initCenterPanel() {
        detailContainer.setPadding(new Insets(24));

        feedbackLabel.setWrapText(true);
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);

        ScrollPane scrollPane = new ScrollPane(detailContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        setCenter(scrollPane);
    }

    private void loadExperiments(Integer selectId) {
        try {
            List<Experiment> experiments = experimentService.getMyExperiments();
            experimentListView.setItems(FXCollections.observableArrayList(experiments));

            if (selectId != null) {
                experiments.stream()
                        .filter(exp -> exp.getId() == selectId)
                        .findFirst()
                        .ifPresent(exp -> experimentListView.getSelectionModel().select(exp));
            } else if (!experiments.isEmpty()) {
                experimentListView.getSelectionModel().selectFirst();
            } else {
                selectedExperiment = null;
                renderExperimentDetails();
            }
        } catch (MotusLabException ex) {
            showErrorAlert("Deneyler yüklenemedi", ex.getMessage());
        }
    }

    private void renderExperimentDetails() {
        detailContainer.getChildren().clear();
        parameterInputs.clear();
        clearFeedback();

        if (selectedExperiment == null) {
            Label emptyLabel = new Label("Bir deney seçin veya sol menüden yeni bir deney oluşturun.");
            emptyLabel.getStyleClass().add(Styles.TEXT_MUTED);
            detailContainer.getChildren().add(emptyLabel);
            return;
        }

        Label titleLabel = new Label(selectedExperiment.getTitle());
        titleLabel.getStyleClass().add(Styles.TITLE_2);

        Label simTypeLabel = new Label("Model: " + selectedExperiment.getSimulationTypeName());
        simTypeLabel.getStyleClass().add(Styles.TEXT_MUTED);

        VBox headerBox = new VBox(4, titleLabel, simTypeLabel);
        if (selectedExperiment.getDescription() != null && !selectedExperiment.getDescription().isBlank()) {
            Label descLabel = new Label(selectedExperiment.getDescription());
            descLabel.setWrapText(true);
            headerBox.getChildren().add(descLabel);
        }

        Label paramsHeader = new Label("Parametreler");
        paramsHeader.getStyleClass().add(Styles.TITLE_4);

        GridPane paramGrid = new GridPane();
        paramGrid.setHgap(14);
        paramGrid.setVgap(10);

        try {
            List<ExperimentParameter> params = experimentService.getExperimentParameters(selectedExperiment.getId());
            int row = 0;
            for (ExperimentParameter param : params) {
                Label nameLabel = new Label(param.getParameterName() + " (" + param.getUnit() + ")");
                TextField valueField = new TextField(String.valueOf(param.getValue()));
                valueField.setPrefWidth(160);

                String rangeText = formatRange(param.getMinValue(), param.getMaxValue());
                Label rangeLabel = new Label(rangeText);
                rangeLabel.getStyleClass().add(Styles.TEXT_MUTED);

                paramGrid.add(nameLabel, 0, row);
                paramGrid.add(valueField, 1, row);
                paramGrid.add(rangeLabel, 2, row);

                parameterInputs.put(param.getParameterDefinitionId(), valueField);
                row++;
            }
        } catch (MotusLabException ex) {
            showFeedback(ex.getMessage(), true);
        }

        Button saveParamsButton = new Button("Parametreleri Kaydet");
        saveParamsButton.getStyleClass().add(Styles.ACCENT);
        saveParamsButton.setOnAction(e -> handleSaveParameters());

        Button deleteButton = new Button("Deneyi Sil");
        deleteButton.getStyleClass().add(Styles.DANGER);
        deleteButton.setOnAction(e -> handleDeleteExperiment());

        HBox actionBar = new HBox(10, saveParamsButton, deleteButton);

        detailContainer.getChildren().addAll(
                headerBox,
                new Separator(),
                paramsHeader,
                paramGrid,
                feedbackLabel,
                actionBar
        );
    }

    private void handleSaveParameters() {
        if (selectedExperiment == null) {
            return;
        }
        clearFeedback();

        Map<Integer, Double> updatedValues = new LinkedHashMap<>();
        try {
            for (Map.Entry<Integer, TextField> entry : parameterInputs.entrySet()) {
                String raw = entry.getValue().getText().trim().replace(',', '.');
                updatedValues.put(entry.getKey(), Double.parseDouble(raw));
            }
            experimentService.updateExperimentParameters(selectedExperiment.getId(), updatedValues);
            showFeedback("Parametreler kaydedildi.", false);
        } catch (NumberFormatException ex) {
            showFeedback("Tüm parametreler geçerli bir sayı olmalıdır.", true);
        } catch (MotusLabException ex) {
            showFeedback(ex.getMessage(), true);
        }
    }

    private void handleDeleteExperiment() {
        if (selectedExperiment == null) {
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Deneyi Sil");
        confirm.setHeaderText(selectedExperiment.getTitle());
        confirm.setContentText("Bu deney ve tüm parametreleri kalıcı olarak silinecek. Emin misiniz?");

        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                try {
                    experimentService.deleteExperiment(selectedExperiment.getId());
                    loadExperiments(null);
                } catch (MotusLabException ex) {
                    showFeedback(ex.getMessage(), true);
                }
            }
        });
    }

    private void openNewExperimentDialog() {
        List<SimulationType> simTypes;
        try {
            simTypes = experimentService.getAllSimulationTypes();
        } catch (MotusLabException ex) {
            showErrorAlert("Simülasyon türleri alınamadı", ex.getMessage());
            return;
        }

        Dialog<Experiment> dialog = new Dialog<>();
        dialog.setTitle("Yeni Deney Oluştur");
        dialog.setHeaderText("Deney bilgilerini girin");

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
                    showErrorAlert("Deney oluşturulamadı", ex.getMessage());
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(created -> loadExperiments(created.getId()));
    }

    private String formatRange(Double min, Double max) {
        if (min != null && max != null) {
            return "[" + min + " .. " + max + "]";
        }
        if (min != null) {
            return "[>= " + min + "]";
        }
        if (max != null) {
            return "[<= " + max + "]";
        }
        return "";
    }

    private void showFeedback(String message, boolean isError) {
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().removeAll(Styles.DANGER, Styles.SUCCESS);
        feedbackLabel.getStyleClass().add(isError ? Styles.DANGER : Styles.SUCCESS);
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }

    private void clearFeedback() {
        feedbackLabel.setText("");
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);
    }

    private void showErrorAlert(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Hata");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }
}