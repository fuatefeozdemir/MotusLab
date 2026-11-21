package com.motuslab.ui;

import atlantafx.base.theme.Styles;
import com.motuslab.exception.MotusLabException;
import com.motuslab.model.Experiment;
import com.motuslab.model.ExperimentParameter;
import com.motuslab.model.SimulationRun;
import com.motuslab.model.User;
import com.motuslab.service.CsvExportService;
import com.motuslab.service.ExperimentMemberService;
import com.motuslab.service.ExperimentService;
import com.motuslab.service.SimulationRunService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DashboardView extends BorderPane {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final User currentUser;
    private final ExperimentService experimentService;
    private final SimulationRunService simulationRunService;
    private final ExperimentMemberService memberService;
    private final Runnable onLogout;

    private final ListView<Experiment> experimentListView = new ListView<>();
    private final ListView<SimulationRun> runListView = new ListView<>();
    private final VBox detailContainer = new VBox(16);
    private final Label feedbackLabel = new Label();
    private final RunResultPane runResultPane;

    private final Map<Integer, TextField> parameterInputs = new LinkedHashMap<>();
    private Experiment selectedExperiment;

    public DashboardView(
            User currentUser,
            ExperimentService experimentService,
            SimulationRunService simulationRunService,
            ExperimentMemberService memberService,
            Runnable onLogout
    ) {
        this.currentUser = currentUser;
        this.experimentService = experimentService;
        this.simulationRunService = simulationRunService;
        this.memberService = memberService;
        this.onLogout = onLogout;
        this.runResultPane = new RunResultPane(
                simulationRunService,
                new CsvExportService(),
                this::showFeedback,
                selectRunId -> {
                    if (selectedExperiment != null) {
                        loadRuns(selectedExperiment.getId(), selectRunId);
                    }
                }
        );
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
        newExpButton.setOnAction(e ->
                NewExperimentDialog.show(experimentService).ifPresent(created -> loadExperiments(created.getId()))
        );

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

                    String subtitle = item.getSimulationTypeName();
                    if (item.getOwnerId() != currentUser.getId()) {
                        subtitle += " • @" + item.getOwnerUsername();
                    }
                    Label type = new Label(subtitle);
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
                    sub.getStyleClass().add(Styles.TEXT_MUTED);
                    VBox box = new VBox(2, top, sub);
                    box.setPadding(new Insets(2, 0, 2, 0));
                    setGraphic(box);
                }
            }
        });

        runListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) ->
                runResultPane.render(selectedExperiment, newVal)
        );

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
        runResultPane.getChildren().clear();
        parameterInputs.clear();
        clearFeedback();

        if (selectedExperiment == null) {
            Label emptyLabel = new Label("Bir deney seçin veya sol menüden yeni bir deney oluşturun.");
            emptyLabel.getStyleClass().add(Styles.TEXT_MUTED);
            detailContainer.getChildren().add(emptyLabel);
            return;
        }

        String permission;
        try {
            permission = memberService.getEffectivePermission(selectedExperiment);
        } catch (MotusLabException ex) {
            permission = "VIEW";
        }

        boolean isOwner = "OWNER".equals(permission);
        boolean canEdit = isOwner || "EDIT".equals(permission);
        boolean canRun = canEdit || "RUN".equals(permission);

        Label titleLabel = new Label(selectedExperiment.getTitle());
        titleLabel.getStyleClass().add(Styles.TITLE_2);

        String metaText = "Model: " + selectedExperiment.getSimulationTypeName()
                + " • Sahip: @" + selectedExperiment.getOwnerUsername()
                + " • Yetkiniz: " + permission;
        Label simTypeLabel = new Label(metaText);
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
                valueField.setEditable(canEdit);

                Label rangeLabel = new Label(formatRange(param.getMinValue(), param.getMaxValue()));
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

        HBox actionBar = new HBox(10);
        if (canRun) {
            Button runSimButton = new Button("Simülasyonu Çalıştır");
            runSimButton.getStyleClass().add(Styles.ACCENT);
            runSimButton.setOnAction(e -> handleRunSimulation(canEdit));
            actionBar.getChildren().add(runSimButton);
        }
        if (canEdit) {
            Button saveParamsButton = new Button("Parametreleri Kaydet");
            saveParamsButton.setOnAction(e -> handleSaveParameters());
            actionBar.getChildren().add(saveParamsButton);
        }
        if (isOwner) {
            Button shareButton = new Button("Paylaş");
            shareButton.setOnAction(e -> ShareExperimentDialog.show(selectedExperiment, memberService));
            actionBar.getChildren().add(shareButton);

            Button deleteButton = new Button("Deneyi Sil");
            deleteButton.getStyleClass().add(Styles.DANGER);
            deleteButton.setOnAction(e -> handleDeleteExperiment());
            actionBar.getChildren().add(deleteButton);
        }

        Label runsHeader = new Label("Çalıştırma Geçmişi");
        runsHeader.getStyleClass().add(Styles.TITLE_4);

        Region runsSpacer = new Region();
        HBox.setHgrow(runsSpacer, Priority.ALWAYS);

        Button compareRunsButton = new Button("Çalıştırmaları Karşılaştır");
        compareRunsButton.setOnAction(e -> handleCompareRuns());

        HBox runsHeaderRow = new HBox(10, runsHeader, runsSpacer, compareRunsButton);
        runsHeaderRow.setAlignment(Pos.CENTER_LEFT);

        detailContainer.getChildren().addAll(
                headerBox,
                new Separator(),
                paramsHeader,
                paramGrid,
                feedbackLabel
        );
        if (!actionBar.getChildren().isEmpty()) {
            detailContainer.getChildren().add(actionBar);
        }
        detailContainer.getChildren().addAll(
                new Separator(),
                runsHeaderRow,
                runListView,
                runResultPane
        );

        loadRuns(selectedExperiment.getId(), null);
    }

    private void loadRuns(int experimentId, Integer selectRunId) {
        try {
            List<SimulationRun> runs = simulationRunService.getRunsByExperiment(experimentId);
            runListView.setItems(FXCollections.observableArrayList(runs));

            if (selectRunId != null) {
                runs.stream()
                        .filter(r -> r.getId() == selectRunId)
                        .findFirst()
                        .ifPresent(r -> runListView.getSelectionModel().select(r));
            } else if (!runs.isEmpty()) {
                runListView.getSelectionModel().selectFirst();
            } else {
                runResultPane.render(selectedExperiment, null);
            }
        } catch (MotusLabException ex) {
            showFeedback(ex.getMessage(), true);
        }
    }

    private void handleCompareRuns() {
        if (selectedExperiment == null) {
            return;
        }
        List<SimulationRun> completed = runListView.getItems().stream()
                .filter(r -> "COMPLETED".equals(r.getStatus()))
                .toList();

        if (completed.size() < 2) {
            showFeedback("Karşılaştırma için en az 2 tamamlanmış çalıştırma gerekir.", true);
            return;
        }

        RunComparisonDialog.show(selectedExperiment, completed, simulationRunService);
    }

    private void handleRunSimulation(boolean canEdit) {
        if (selectedExperiment == null) {
            return;
        }
        if (canEdit && !saveParametersSilently()) {
            return;
        }

        try {
            SimulationRun run = simulationRunService.executeExperiment(selectedExperiment.getId());
            showFeedback("Simülasyon tamamlandı (Run #" + run.getRunNumber() + ").", false);
            loadRuns(selectedExperiment.getId(), run.getId());
        } catch (MotusLabException ex) {
            showFeedback(ex.getMessage(), true);
            loadRuns(selectedExperiment.getId(), null);
        }
    }

    private void handleSaveParameters() {
        if (saveParametersSilently()) {
            showFeedback("Parametreler kaydedildi.", false);
        }
    }

    private boolean saveParametersSilently() {
        if (selectedExperiment == null) {
            return false;
        }
        clearFeedback();

        Map<Integer, Double> updatedValues = new LinkedHashMap<>();
        try {
            for (Map.Entry<Integer, TextField> entry : parameterInputs.entrySet()) {
                String raw = entry.getValue().getText().trim().replace(',', '.');
                updatedValues.put(entry.getKey(), Double.parseDouble(raw));
            }
            experimentService.updateExperimentParameters(selectedExperiment.getId(), updatedValues);
            return true;
        } catch (NumberFormatException ex) {
            showFeedback("Tüm parametreler geçerli bir sayı olmalıdır.", true);
            return false;
        } catch (MotusLabException ex) {
            showFeedback(ex.getMessage(), true);
            return false;
        }
    }

    private void handleDeleteExperiment() {
        if (selectedExperiment == null) {
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Deneyi Sil");
        confirm.setHeaderText(selectedExperiment.getTitle());
        confirm.setContentText("Bu deney ve tüm çalıştırma geçmişi kalıcı olarak silinecek. Emin misiniz?");

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