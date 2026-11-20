package com.motuslab.ui;

import atlantafx.base.theme.Styles;
import com.motuslab.exception.MotusLabException;
import com.motuslab.model.Experiment;
import com.motuslab.model.ExperimentMember;
import com.motuslab.model.ExperimentParameter;
import com.motuslab.model.RunMetric;
import com.motuslab.model.RunSample;
import com.motuslab.model.SimulationRun;
import com.motuslab.model.SimulationType;
import com.motuslab.model.User;
import com.motuslab.service.ExperimentMemberService;
import com.motuslab.service.ExperimentService;
import com.motuslab.service.SimulationRunService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashboardView extends BorderPane {

    private static final int MAX_CHART_POINTS = 500;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final User currentUser;
    private final ExperimentService experimentService;
    private final SimulationRunService simulationRunService;
    private final ExperimentMemberService memberService;
    private final Runnable onLogout;

    private final ListView<Experiment> experimentListView = new ListView<>();
    private final ListView<SimulationRun> runListView = new ListView<>();
    private final VBox detailContainer = new VBox(16);
    private final VBox runResultContainer = new VBox(14);
    private final Label feedbackLabel = new Label();

    private final Map<Integer, TextField> parameterInputs = new LinkedHashMap<>();
    private Experiment selectedExperiment;
    private String currentPermission = "NONE";

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
                    Label top = new Label("Run #" + item.getId() + " — " + item.getStatus());
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

        runListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            renderRunResults(newVal);
        });

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
        runResultContainer.getChildren().clear();
        parameterInputs.clear();
        clearFeedback();

        if (selectedExperiment == null) {
            currentPermission = "NONE";
            Label emptyLabel = new Label("Bir deney seçin veya sol menüden yeni bir deney oluşturun.");
            emptyLabel.getStyleClass().add(Styles.TEXT_MUTED);
            detailContainer.getChildren().add(emptyLabel);
            return;
        }

        try {
            currentPermission = memberService.getEffectivePermission(selectedExperiment);
        } catch (MotusLabException ex) {
            currentPermission = "VIEW";
        }

        boolean isOwner = "OWNER".equals(currentPermission);
        boolean canEdit = isOwner || "EDIT".equals(currentPermission);
        boolean canRun = canEdit || "RUN".equals(currentPermission);

        Label titleLabel = new Label(selectedExperiment.getTitle());
        titleLabel.getStyleClass().add(Styles.TITLE_2);

        String metaText = "Model: " + selectedExperiment.getSimulationTypeName()
                + " • Sahip: @" + selectedExperiment.getOwnerUsername()
                + " • Yetkiniz: " + currentPermission;
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
            shareButton.setOnAction(e -> openShareDialog());
            actionBar.getChildren().add(shareButton);

            Button deleteButton = new Button("Deneyi Sil");
            deleteButton.getStyleClass().add(Styles.DANGER);
            deleteButton.setOnAction(e -> handleDeleteExperiment());
            actionBar.getChildren().add(deleteButton);
        }

        Label runsHeader = new Label("Çalıştırma Geçmişi");
        runsHeader.getStyleClass().add(Styles.TITLE_4);

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
                runsHeader,
                runListView,
                runResultContainer
        );

        loadRuns(selectedExperiment.getId(), null);
    }

    private void openShareDialog() {
        if (selectedExperiment == null) {
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Deneyi Paylaş");
        dialog.setHeaderText(selectedExperiment.getTitle() + " — Üye Yönetimi");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        Label dialogFeedback = new Label();
        dialogFeedback.setWrapText(true);
        dialogFeedback.setVisible(false);
        dialogFeedback.setManaged(false);

        ListView<ExperimentMember> memberListView = new ListView<>();
        memberListView.setPrefHeight(200);

        Runnable refreshMembers = () -> {
            try {
                List<ExperimentMember> members = memberService.getMembers(selectedExperiment.getId());
                memberListView.setItems(FXCollections.observableArrayList(members));
            } catch (MotusLabException ex) {
                dialogFeedback.setText(ex.getMessage());
                dialogFeedback.getStyleClass().setAll(Styles.DANGER);
                dialogFeedback.setVisible(true);
                dialogFeedback.setManaged(true);
            }
        };

        memberListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ExperimentMember item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label userLabel = new Label("@" + item.getUsername());
                    userLabel.getStyleClass().add(Styles.TEXT_BOLD);

                    Label permLabel = new Label(item.getPermission());
                    permLabel.getStyleClass().add(Styles.TEXT_MUTED);

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    Button removeBtn = new Button("Çıkar");
                    removeBtn.getStyleClass().add(Styles.DANGER);
                    removeBtn.setOnAction(e -> {
                        try {
                            memberService.removeMember(selectedExperiment.getId(), item.getUserId());
                            dialogFeedback.setText("@" + item.getUsername() + " çıkarıldı.");
                            dialogFeedback.getStyleClass().setAll(Styles.SUCCESS);
                            dialogFeedback.setVisible(true);
                            dialogFeedback.setManaged(true);
                            refreshMembers.run();
                        } catch (MotusLabException ex) {
                            dialogFeedback.setText(ex.getMessage());
                            dialogFeedback.getStyleClass().setAll(Styles.DANGER);
                            dialogFeedback.setVisible(true);
                            dialogFeedback.setManaged(true);
                        }
                    });

                    HBox row = new HBox(10, userLabel, permLabel, spacer, removeBtn);
                    row.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(row);
                }
            }
        });

        TextField usernameField = new TextField();
        usernameField.setPromptText("Kullanıcı adı");
        HBox.setHgrow(usernameField, Priority.ALWAYS);

        ComboBox<String> permBox = new ComboBox<>(FXCollections.observableArrayList("VIEW", "RUN", "EDIT"));
        permBox.getSelectionModel().select("VIEW");

        Button addBtn = new Button("Ekle / Güncelle");
        addBtn.getStyleClass().add(Styles.ACCENT);
        addBtn.setOnAction(e -> {
            try {
                memberService.addOrUpdateMember(
                        selectedExperiment.getId(),
                        usernameField.getText(),
                        permBox.getValue()
                );
                usernameField.clear();
                dialogFeedback.setText("Üye yetkisi kaydedildi.");
                dialogFeedback.getStyleClass().setAll(Styles.SUCCESS);
                dialogFeedback.setVisible(true);
                dialogFeedback.setManaged(true);
                refreshMembers.run();
            } catch (MotusLabException ex) {
                dialogFeedback.setText(ex.getMessage());
                dialogFeedback.getStyleClass().setAll(Styles.DANGER);
                dialogFeedback.setVisible(true);
                dialogFeedback.setManaged(true);
            }
        });

        HBox addRow = new HBox(8, usernameField, permBox, addBtn);
        addRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(12,
                new Label("Yeni Üye Ekle veya Yetki Güncelle"),
                addRow,
                dialogFeedback,
                new Separator(),
                new Label("Mevcut Üyeler"),
                memberListView
        );
        content.setPrefWidth(460);
        dialog.getDialogPane().setContent(content);

        refreshMembers.run();
        dialog.showAndWait();
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
                renderRunResults(null);
            }
        } catch (MotusLabException ex) {
            showFeedback(ex.getMessage(), true);
        }
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
            showFeedback("Simülasyon tamamlandı (Run #" + run.getId() + ").", false);
            loadRuns(selectedExperiment.getId(), run.getId());
        } catch (MotusLabException ex) {
            showFeedback(ex.getMessage(), true);
            loadRuns(selectedExperiment.getId(), null);
        }
    }

    private void renderRunResults(SimulationRun run) {
        runResultContainer.getChildren().clear();

        if (run == null) {
            Label empty = new Label("Henüz bir çalıştırma kaydı yok.");
            empty.getStyleClass().add(Styles.TEXT_MUTED);
            runResultContainer.getChildren().add(empty);
            return;
        }

        if ("FAILED".equals(run.getStatus())) {
            Label errLabel = new Label("Çalıştırma başarısız oldu: " +
                    (run.getErrorMessage() != null ? run.getErrorMessage() : "Bilinmeyen hata"));
            errLabel.getStyleClass().add(Styles.DANGER);
            errLabel.setWrapText(true);
            runResultContainer.getChildren().add(errLabel);
            return;
        }

        if (!"COMPLETED".equals(run.getStatus())) {
            Label statusLabel = new Label("Durum: " + run.getStatus());
            statusLabel.getStyleClass().add(Styles.TEXT_MUTED);
            runResultContainer.getChildren().add(statusLabel);
            return;
        }

        try {
            List<RunMetric> metrics = simulationRunService.getMetricsByRun(run.getId());
            List<RunSample> samples = simulationRunService.getSamplesByRun(run.getId());

            Label metricsTitle = new Label("Sonuç Metrikleri (Run #" + run.getId() + ")");
            metricsTitle.getStyleClass().add(Styles.TITLE_4);

            FlowPane metricsPane = new FlowPane(12, 12);
            for (RunMetric m : metrics) {
                VBox card = new VBox(4);
                card.setPadding(new Insets(10, 14, 10, 14));
                card.setStyle("-fx-border-color: -color-border-default; -fx-border-radius: 6; -fx-background-radius: 6;");

                Label name = new Label(formatMetricName(m.getMetricCode()));
                name.getStyleClass().add(Styles.TEXT_MUTED);

                Label val = new Label(String.format(Locale.US, "%.3f %s", m.getMetricValue(), m.getUnit()));
                val.getStyleClass().add(Styles.TITLE_4);

                card.getChildren().addAll(name, val);
                metricsPane.getChildren().add(card);
            }

            List<RunSample> sampled = downsample(samples, MAX_CHART_POINTS);
            boolean is2D = selectedExperiment != null && "PROJECTILE_2D".equals(selectedExperiment.getSimulationTypeCode());

            HBox chartsBox = new HBox(16);
            if (is2D) {
                LineChart<Number, Number> trajectoryChart = buildChart(
                        "Yörünge (y - x)", "Konum x (m)", "Yükseklik y (m)", sampled,
                        RunSample::getPositionX, s -> s.getPositionY() != null ? s.getPositionY() : 0.0
                );
                LineChart<Number, Number> speedChart = buildChart(
                        "Hız Büyüklüğü - Zaman", "Zaman (s)", "Hız (m/s)", sampled,
                        RunSample::getTimeSeconds, s -> Math.hypot(s.getVelocityX(), s.getVelocityY() != null ? s.getVelocityY() : 0.0)
                );
                HBox.setHgrow(trajectoryChart, Priority.ALWAYS);
                HBox.setHgrow(speedChart, Priority.ALWAYS);
                chartsBox.getChildren().addAll(trajectoryChart, speedChart);
            } else {
                LineChart<Number, Number> posChart = buildChart(
                        "Konum - Zaman", "Zaman (s)", "Konum x (m)", sampled,
                        RunSample::getTimeSeconds, RunSample::getPositionX
                );
                LineChart<Number, Number> velChart = buildChart(
                        "Hız - Zaman", "Zaman (s)", "Hız vx (m/s)", sampled,
                        RunSample::getTimeSeconds, RunSample::getVelocityX
                );
                HBox.setHgrow(posChart, Priority.ALWAYS);
                HBox.setHgrow(velChart, Priority.ALWAYS);
                chartsBox.getChildren().addAll(posChart, velChart);
            }

            runResultContainer.getChildren().addAll(metricsTitle, metricsPane, chartsBox);
        } catch (MotusLabException ex) {
            showFeedback(ex.getMessage(), true);
        }
    }

    private LineChart<Number, Number> buildChart(
            String title,
            String xLabel,
            String yLabel,
            List<RunSample> samples,
            java.util.function.ToDoubleFunction<RunSample> xMapper,
            java.util.function.ToDoubleFunction<RunSample> yMapper
    ) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel(xLabel);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(yLabel);

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        chart.setPrefHeight(280);

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        for (RunSample s : samples) {
            series.getData().add(new XYChart.Data<>(xMapper.applyAsDouble(s), yMapper.applyAsDouble(s)));
        }
        chart.getData().add(series);
        return chart;
    }

    private List<RunSample> downsample(List<RunSample> samples, int maxPoints) {
        if (samples.size() <= maxPoints) {
            return samples;
        }
        List<RunSample> result = new ArrayList<>(maxPoints);
        double step = (double) (samples.size() - 1) / (maxPoints - 1);
        for (int i = 0; i < maxPoints; i++) {
            int idx = (int) Math.round(i * step);
            result.add(samples.get(Math.min(idx, samples.size() - 1)));
        }
        return result;
    }

    private String formatMetricName(String code) {
        return switch (code) {
            case "total_time" -> "Toplam Süre";
            case "flight_time" -> "Uçuş Süresi";
            case "final_position" -> "Son Konum";
            case "displacement" -> "Yer Değiştirme";
            case "range" -> "Menzil";
            case "max_height" -> "Maks. Yükseklik";
            case "final_velocity" -> "Son Hız";
            case "impact_speed" -> "Çarpma Hızı";
            case "max_speed" -> "Maks. Hız";
            default -> code;
        };
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