package com.motuslab.ui.component;

import atlantafx.base.theme.Styles;
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
import javafx.scene.control.Separator;
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
                    top.getStyleClass().add(Styles.TEXT_BOLD);
                    if ("FAILED".equals(item.getStatus())) {
                        top.getStyleClass().add(Styles.DANGER);
                    } else if ("COMPLETED".equals(item.getStatus())) {
                        top.getStyleClass().add(Styles.SUCCESS);
                    }
                    Label sub = new Label(time + " • " + item.getTriggeredByUsername());
                    sub.getStyleClass().add("ml-muted");
                    VBox box = new VBox(2, top, sub);
                    box.setPadding(new Insets(2, 0, 2, 0));
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
        emptyLabel.getStyleClass().add("ml-muted");
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

        Label titleLabel = new Label(experiment.getTitle());
        titleLabel.getStyleClass().add(Styles.TITLE_2);

        String metaText = "Model: " + experiment.getSimulationTypeName()
                + " • Sahip: @" + experiment.getOwnerUsername()
                + " • Yetkiniz: " + permission
                + (isArchived ? " • ARŞİVLENDİ" : "");
        Label simTypeLabel = new Label(metaText);
        simTypeLabel.getStyleClass().add("ml-muted");

        VBox headerBox = new VBox(4, titleLabel, simTypeLabel);
        if (experiment.getDescription() != null && !experiment.getDescription().isBlank()) {
            Label descLabel = new Label(experiment.getDescription());
            descLabel.setWrapText(true);
            headerBox.getChildren().add(descLabel);
        }

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
            rangeLabel.getStyleClass().add("ml-muted");

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
            runSimButton.getStyleClass().add(Styles.ACCENT);

            runProgress = new ProgressIndicator();
            runProgress.setPrefSize(20, 20);
            runProgress.setVisible(false);
            runProgress.setManaged(false);

            runSimButton.setOnAction(e -> actions.onRunSimulation().accept(canEdit));
            actionBar.getChildren().addAll(runSimButton, runProgress);
        }
        if (canEdit) {
            Button saveParamsButton = new Button("Parametreleri Kaydet");
            saveParamsButton.setOnAction(e -> actions.onSaveParameters().run());

            Button editExpButton = new Button("Deneyi Düzenle");
            editExpButton.setOnAction(e -> actions.onEditExperiment().run());

            actionBar.getChildren().addAll(saveParamsButton, editExpButton);
        }
        if (isOwner) {
            if (!isArchived) {
                Button shareButton = new Button("Paylaş");
                shareButton.setOnAction(e -> actions.onShareExperiment().run());
                actionBar.getChildren().add(shareButton);
            }

            Button archiveButton = new Button(isArchived ? "Arşivden Çıkar" : "Arşivle");
            archiveButton.setOnAction(e -> actions.onToggleArchive().accept(!isArchived));
            actionBar.getChildren().add(archiveButton);

            Button deleteButton = new Button("Deneyi Sil");
            deleteButton.getStyleClass().add(Styles.DANGER);
            deleteButton.setOnAction(e -> actions.onDeleteExperiment().run());
            actionBar.getChildren().add(deleteButton);
        }

        Label runsHeader = new Label("Çalıştırma Geçmişi");
        runsHeader.getStyleClass().add("ml-section-title");

        Region runsSpacer = new Region();
        HBox.setHgrow(runsSpacer, Priority.ALWAYS);

        Button compareRunsButton = new Button("Çalıştırmaları Karşılaştır");
        compareRunsButton.setOnAction(e -> actions.onCompareRuns().run());

        HBox runsHeaderRow = new HBox(10, runsHeader, runsSpacer, compareRunsButton);
        runsHeaderRow.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(headerBox, new Separator(), paramsHeader, paramGrid, feedbackLabel);
        if (!actionBar.getChildren().isEmpty()) {
            getChildren().add(actionBar);
        }
        getChildren().addAll(new Separator(), runsHeaderRow, runListView, runResultPane);
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