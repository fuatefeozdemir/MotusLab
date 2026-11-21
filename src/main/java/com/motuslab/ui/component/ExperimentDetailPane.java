package com.motuslab.ui.component;

import com.motuslab.model.Experiment;
import com.motuslab.model.ExperimentParameter;
import com.motuslab.model.SimulationRun;
import com.motuslab.ui.RunResultPane;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ExperimentDetailPane extends VBox {

    public record Actions(
            Consumer<Boolean> onRunSimulation,
            Runnable onSaveParameters,
            Runnable onEditExperiment,
            Runnable onShareExperiment,
            Consumer<Boolean> onToggleArchive,
            Runnable onDeleteExperiment,
            Runnable onCompareRuns,
            Consumer<SimulationRun> onSelectRun
    ) {
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final RunResultPane runResultPane;
    private final Actions actions;

    private final ListView<SimulationRun> runListView = new ListView<>();
    private final Label feedbackLabel = new Label();
    private final Map<Integer, TextField> parameterInputs = new LinkedHashMap<>();

    private Button runSimButton;
    private ProgressIndicator runProgress;

    public ExperimentDetailPane(RunResultPane runResultPane, Actions actions) {
        super(16);
        this.runResultPane = runResultPane;
        this.actions = actions;

        getStyleClass().add("ml-detail-pane");

        feedbackLabel.setWrapText(true);
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);

        runListView.setPrefHeight(160);
        runListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(SimulationRun item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String time = item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_FMT) : "-";
                    Label top = new Label(item.getDisplayName() + " • " + item.getStatus());
                    top.getStyleClass().add("ml-text-bold");
                    if ("FAILED".equals(item.getStatus())) {
                        top.getStyleClass().add("ml-feedback-error");
                    } else if ("COMPLETED".equals(item.getStatus())) {
                        top.getStyleClass().add("ml-feedback-success");
                    }
                    Label sub = new Label(time + " • @" + item.getTriggeredByUsername());
                    sub.getStyleClass().add("ml-text-muted");
                    VBox box = new VBox(2, top, sub);
                    box.setPadding(new Insets(3, 2, 3, 2));
                    setGraphic(box);
                }
            }
        });

        runListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) ->
                actions.onSelectRun().accept(newVal)
        );
    }

    public void renderEmpty() {
        getChildren().clear();
        runResultPane.getChildren().clear();
        parameterInputs.clear();
        clearFeedback();

        Label emptyLabel = new Label("Bir deney seçin veya sol menüden yeni bir deney oluşturun.");
        emptyLabel.getStyleClass().add("ml-text-muted");
        getChildren().add(emptyLabel);
    }

    public void renderExperiment(Experiment experiment, String permission, List<ExperimentParameter> params) {
        getChildren().clear();
        runResultPane.getChildren().clear();
        parameterInputs.clear();
        clearFeedback();
        runSimButton = null;
        runProgress = null;

        boolean isArchived = experiment.isArchived();
        boolean isOwner = "OWNER".equals(permission);
        boolean canEdit = (isOwner || "EDIT".equals(permission)) && !isArchived;
        boolean canRun = (canEdit || "RUN".equals(permission)) && !isArchived;

        // --- Başlık ---
        Label titleLabel = new Label(experiment.getTitle());
        titleLabel.getStyleClass().add("ml-page-title");

        HBox titleRow = new HBox(10, titleLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        if (isArchived) {
            Label archiveBadge = new Label("ARŞİVLENDİ");
            archiveBadge.getStyleClass().add("ml-badge-warning");
            titleRow.getChildren().add(archiveBadge);
        }

        Label permBadge = new Label(permission);
        permBadge.getStyleClass().add(isOwner ? "ml-badge-accent" : "ml-badge");

        Label metaLabel = new Label("Model: " + experiment.getSimulationTypeName()
                + " • Sahip: @" + experiment.getOwnerUsername());
        metaLabel.getStyleClass().add("ml-text-secondary");

        HBox metaRow = new HBox(8, metaLabel, permBadge);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        VBox headerBox = new VBox(6, titleRow, metaRow);
        if (experiment.getDescription() != null && !experiment.getDescription().isBlank()) {
            Label descLabel = new Label(experiment.getDescription());
            descLabel.getStyleClass().add("ml-text-secondary");
            descLabel.setWrapText(true);
            headerBox.getChildren().add(descLabel);
        }

        // --- Parametreler Kartı ---
        Label paramsHeader = new Label("Parametreler");
        paramsHeader.getStyleClass().add("ml-section-title");

        GridPane paramGrid = new GridPane();
        paramGrid.setHgap(14);
        paramGrid.setVgap(10);

        int row = 0;
        for (ExperimentParameter param : params) {
            Label nameLabel = new Label(param.getParameterName() + " (" + param.getUnit() + ")");
            TextField valueField = new TextField(String.valueOf(param.getValue()));
            valueField.setPrefWidth(160);
            valueField.setEditable(canEdit);

            Label rangeLabel = new Label(formatRange(param.getMinValue(), param.getMaxValue()));
            rangeLabel.getStyleClass().add("ml-text-muted");

            paramGrid.add(nameLabel, 0, row);
            paramGrid.add(valueField, 1, row);
            paramGrid.add(rangeLabel, 2, row);

            parameterInputs.put(param.getParameterDefinitionId(), valueField);
            row++;
        }

        HBox actionBar = new HBox(10);
        actionBar.setAlignment(Pos.CENTER_LEFT);

        if (canRun) {
            runSimButton = new Button("Simülasyonu Çalıştır");
            runSimButton.getStyleClass().add("ml-btn-primary");

            runProgress = new ProgressIndicator();
            runProgress.setPrefSize(20, 20);
            runProgress.setVisible(false);
            runProgress.setManaged(false);

            runSimButton.setOnAction(e -> actions.onRunSimulation().accept(canEdit));
            actionBar.getChildren().addAll(runSimButton, runProgress);
        }
        if (canEdit) {
            Button saveParamsButton = new Button("Parametreleri Kaydet");
            saveParamsButton.getStyleClass().add("ml-btn-secondary");
            saveParamsButton.setOnAction(e -> actions.onSaveParameters().run());

            Button editExpButton = new Button("Deneyi Düzenle");
            editExpButton.getStyleClass().add("ml-btn-secondary");
            editExpButton.setOnAction(e -> actions.onEditExperiment().run());

            actionBar.getChildren().addAll(saveParamsButton, editExpButton);
        }
        if (isOwner) {
            if (!isArchived) {
                Button shareButton = new Button("Paylaş");
                shareButton.getStyleClass().add("ml-btn-secondary");
                shareButton.setOnAction(e -> actions.onShareExperiment().run());
                actionBar.getChildren().add(shareButton);
            }

            Button archiveButton = new Button(isArchived ? "Arşivden Çıkar" : "Arşivle");
            archiveButton.getStyleClass().add("ml-btn-secondary");
            archiveButton.setOnAction(e -> actions.onToggleArchive().accept(!isArchived));
            actionBar.getChildren().add(archiveButton);

            Button deleteButton = new Button("Deneyi Sil");
            deleteButton.getStyleClass().add("ml-btn-danger");
            deleteButton.setOnAction(e -> actions.onDeleteExperiment().run());
            actionBar.getChildren().add(deleteButton);
        }

        VBox paramsCard = new VBox(12, paramsHeader, paramGrid, feedbackLabel);
        if (!actionBar.getChildren().isEmpty()) {
            paramsCard.getChildren().add(actionBar);
        }
        paramsCard.getStyleClass().add("ml-card");

        // --- Çalıştırma Geçmişi Kartı ---
        Label runsHeader = new Label("Çalıştırma Geçmişi");
        runsHeader.getStyleClass().add("ml-section-title");

        Region runsSpacer = new Region();
        HBox.setHgrow(runsSpacer, Priority.ALWAYS);

        Button compareRunsButton = new Button("Çalıştırmaları Karşılaştır");
        compareRunsButton.getStyleClass().add("ml-btn-secondary");
        compareRunsButton.setOnAction(e -> actions.onCompareRuns().run());

        HBox runsHeaderRow = new HBox(10, runsHeader, runsSpacer, compareRunsButton);
        runsHeaderRow.setAlignment(Pos.CENTER_LEFT);

        VBox runsCard = new VBox(12, runsHeaderRow, runListView);
        runsCard.getStyleClass().add("ml-card");

        getChildren().addAll(headerBox, paramsCard, runsCard, runResultPane);
    }

    public void setRuns(List<SimulationRun> runs, Integer selectRunId) {
        runListView.setItems(FXCollections.observableArrayList(runs));
        if (selectRunId != null) {
            runs.stream()
                    .filter(r -> r.getId() == selectRunId)
                    .findFirst()
                    .ifPresent(r -> runListView.getSelectionModel().select(r));
        } else if (!runs.isEmpty()) {
            runListView.getSelectionModel().selectFirst();
        } else {
            runListView.getSelectionModel().clearSelection();
        }
    }

    public List<SimulationRun> getRuns() {
        return List.copyOf(runListView.getItems());
    }

    public Map<Integer, Double> readParameterValues() {
        Map<Integer, Double> values = new LinkedHashMap<>();
        for (Map.Entry<Integer, TextField> entry : parameterInputs.entrySet()) {
            String raw = entry.getValue().getText().trim().replace(',', '.');
            values.put(entry.getKey(), Double.parseDouble(raw));
        }
        return values;
    }

    public void setRunningState(boolean running) {
        if (runSimButton != null) {
            runSimButton.setDisable(running);
            runSimButton.setText(running ? "Çalıştırılıyor..." : "Simülasyonu Çalıştır");
        }
        if (runProgress != null) {
            runProgress.setVisible(running);
            runProgress.setManaged(running);
        }
    }

    public void showFeedback(String message, boolean isError) {
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().removeAll("ml-feedback-error", "ml-feedback-success");
        feedbackLabel.getStyleClass().add(isError ? "ml-feedback-error" : "ml-feedback-success");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }

    public void clearFeedback() {
        feedbackLabel.setText("");
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);
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
}