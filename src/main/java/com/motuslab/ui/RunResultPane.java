package com.motuslab.ui;

import com.motuslab.exception.MotusLabException;
import com.motuslab.model.Experiment;
import com.motuslab.model.RunMetric;
import com.motuslab.model.RunParameterSnapshot;
import com.motuslab.model.RunSample;
import com.motuslab.model.RunStatusHistory;
import com.motuslab.model.SimulationRun;
import com.motuslab.service.CsvExportService;
import com.motuslab.service.SimulationRunService;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.feather.Feather;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

public class RunResultPane extends VBox {

    private static final int MAX_CHART_POINTS = 500;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss.SSS");

    private final SimulationRunService simulationRunService;
    private final CsvExportService csvExportService;
    private final BiConsumer<String, Boolean> feedbackCallback;
    private final Consumer<Integer> onRunsChanged;

    public RunResultPane(
            SimulationRunService simulationRunService,
            CsvExportService csvExportService,
            BiConsumer<String, Boolean> feedbackCallback,
            Consumer<Integer> onRunsChanged
    ) {
        super(14);
        this.simulationRunService = simulationRunService;
        this.csvExportService = csvExportService;
        this.feedbackCallback = feedbackCallback;
        this.onRunsChanged = onRunsChanged;
    }

    public void render(Experiment experiment, SimulationRun run) {
        getChildren().clear();

        if (run == null) {
            Label empty = new Label("Henüz bir çalıştırma kaydı yok.");
            empty.getStyleClass().add("ml-text-muted");
            getChildren().add(empty);
            return;
        }

        boolean canManage = experiment != null && simulationRunService.canManageRun(experiment, run);

        Label titleLabel = new Label(run.getDisplayName());
        titleLabel.getStyleClass().add("ml-section-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox headerRow = new HBox(8, titleLabel, spacer);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        List<RunMetric> metrics = List.of();
        List<RunSample> samples = List.of();
        boolean isCompleted = "COMPLETED".equals(run.getStatus());

        if (isCompleted) {
            try {
                metrics = simulationRunService.getMetricsByRun(run.getId());
                samples = simulationRunService.getSamplesByRun(run.getId());
            } catch (MotusLabException ex) {
                feedbackCallback.accept(ex.getMessage(), true);
                return;
            }

            List<RunMetric> finalMetrics = metrics;
            List<RunSample> finalSamples = samples;
            Button exportCsvBtn = new Button("CSV", UiHelper.icon(Feather.DOWNLOAD, 14));
            exportCsvBtn.getStyleClass().add("ml-btn-secondary");
            exportCsvBtn.setGraphicTextGap(6);
            exportCsvBtn.setOnAction(e -> handleExportCsv(experiment, run, finalMetrics, finalSamples));
            headerRow.getChildren().add(exportCsvBtn);
        }

        if (canManage) {
            Button editBtn = new Button("Düzenle", UiHelper.icon(Feather.EDIT_2, 14));
            editBtn.getStyleClass().add("ml-btn-secondary");
            editBtn.setGraphicTextGap(6);
            editBtn.setOnAction(e -> {
                if (EditRunDialog.show(run, simulationRunService)) {
                    feedbackCallback.accept("Çalıştırma bilgileri güncellendi.", false);
                    onRunsChanged.accept(run.getId());
                }
            });

            Button deleteBtn = new Button("Sil", UiHelper.icon(Feather.TRASH_2, 14, "ml-icon-danger"));
            deleteBtn.getStyleClass().add("ml-btn-danger");
            deleteBtn.setGraphicTextGap(6);
            deleteBtn.setOnAction(e -> handleDeleteRun(run));

            headerRow.getChildren().addAll(editBtn, deleteBtn);
        }

        getChildren().add(headerRow);

        if (run.getNotes() != null && !run.getNotes().isBlank()) {
            Label notesTitle = new Label("Notlar");
            notesTitle.getStyleClass().add("ml-text-bold");
            Label notesBody = new Label(run.getNotes());
            notesBody.getStyleClass().add("ml-text-secondary");
            notesBody.setWrapText(true);

            VBox notesBox = new VBox(4, notesTitle, notesBody);
            notesBox.getStyleClass().add("ml-info-box");
            getChildren().add(notesBox);
        }

        if ("FAILED".equals(run.getStatus())) {
            Label errLabel = new Label("Çalıştırma başarısız oldu: " +
                    (run.getErrorMessage() != null ? run.getErrorMessage() : "Bilinmeyen hata"));
            errLabel.getStyleClass().add("ml-feedback-error");
            errLabel.setWrapText(true);
            getChildren().add(errLabel);
            renderAuditSection(run);
            return;
        }

        if (!isCompleted) {
            Label statusLabel = new Label("Durum: " + run.getStatus());
            statusLabel.getStyleClass().add("ml-text-muted");
            getChildren().add(statusLabel);
            renderAuditSection(run);
            return;
        }

        FlowPane metricsPane = new FlowPane(12, 12);
        for (RunMetric m : metrics) {
            VBox card = new VBox(4);
            card.getStyleClass().add("ml-metric-card");

            Label name = new Label(formatMetricName(m.getMetricCode()));
            name.getStyleClass().add("ml-text-muted");

            Label val = new Label(String.format(Locale.US, "%.3f %s", m.getMetricValue(), m.getUnit()));
            val.getStyleClass().add("ml-metric-value");

            card.getChildren().addAll(name, val);
            metricsPane.getChildren().add(card);
        }

        List<RunSample> sampled = downsample(samples, MAX_CHART_POINTS);
        boolean is2D = experiment != null && "PROJECTILE_2D".equals(experiment.getSimulationTypeCode());

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

        getChildren().addAll(metricsPane, chartsBox);
        renderAuditSection(run);
    }

    private void handleDeleteRun(SimulationRun run) {
        boolean confirmed = UiHelper.showConfirm(
                "Çalıştırmayı Sil",
                run.getDisplayName(),
                "Bu çalıştırma ve tüm sonuç verileri kalıcı olarak silinecek. Emin misiniz?"
        );
        if (confirmed) {
            try {
                simulationRunService.deleteRun(run.getId());
                feedbackCallback.accept(run.getDisplayName() + " silindi.", false);
                onRunsChanged.accept(null);
            } catch (MotusLabException ex) {
                feedbackCallback.accept(ex.getMessage(), true);
            }
        }
    }

    private void renderAuditSection(SimulationRun run) {
        try {
            List<RunParameterSnapshot> snapshots = simulationRunService.getSnapshotsByRun(run.getId());
            List<RunStatusHistory> history = simulationRunService.getStatusHistoryByRun(run.getId());

            VBox snapshotBox = new VBox(8);
            snapshotBox.getStyleClass().add("ml-info-box");
            Label snapTitle = new Label("Kullanılan Parametreler (Snapshot)");
            snapTitle.getStyleClass().add("ml-text-bold");

            GridPane snapGrid = new GridPane();
            snapGrid.setHgap(16);
            snapGrid.setVgap(6);
            int sRow = 0;
            for (RunParameterSnapshot snap : snapshots) {
                Label name = new Label(snap.getParameterName());
                name.getStyleClass().add("ml-text-secondary");
                Label val = new Label(String.format(Locale.US, "%s %s", snap.getValue(), snap.getUnit()));
                val.getStyleClass().add("ml-mono");
                snapGrid.addRow(sRow++, name, val);
            }
            snapshotBox.getChildren().addAll(snapTitle, snapGrid);

            VBox historyBox = new VBox(8);
            historyBox.getStyleClass().add("ml-info-box");
            Label histTitle = new Label("Durum Geçmişi");
            histTitle.getStyleClass().add("ml-text-bold");

            GridPane histGrid = new GridPane();
            histGrid.setHgap(16);
            histGrid.setVgap(6);
            int hRow = 0;
            for (RunStatusHistory h : history) {
                String transition = (h.getOldStatus() == null ? "—" : h.getOldStatus()) + " → " + h.getNewStatus();
                String time = h.getChangedAt() != null ? h.getChangedAt().format(TIME_FMT) : "-";
                Label transLabel = new Label(transition);
                Label timeLabel = new Label(time);
                timeLabel.getStyleClass().addAll("ml-text-muted", "ml-mono");
                histGrid.addRow(hRow++, transLabel, timeLabel);
            }
            historyBox.getChildren().addAll(histTitle, histGrid);

            HBox.setHgrow(snapshotBox, Priority.ALWAYS);
            HBox.setHgrow(historyBox, Priority.ALWAYS);
            HBox auditRow = new HBox(16, snapshotBox, historyBox);

            getChildren().add(auditRow);
        } catch (MotusLabException ex) {
            feedbackCallback.accept(ex.getMessage(), true);
        }
    }

    private void handleExportCsv(Experiment experiment, SimulationRun run, List<RunMetric> metrics, List<RunSample> samples) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Simülasyon Sonuçlarını CSV Olarak Kaydet");
        int expId = experiment != null ? experiment.getId() : 0;
        chooser.setInitialFileName("motuslab_exp" + expId + "_run" + run.getRunNumber() + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Dosyaları (*.csv)", "*.csv"));

        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) {
            return;
        }

        try {
            String title = experiment != null ? experiment.getTitle() : "Deney";
            csvExportService.exportRunToCsv(file.toPath(), title, run, metrics, samples);
            feedbackCallback.accept("CSV kaydedildi: " + file.getName(), false);
        } catch (MotusLabException ex) {
            feedbackCallback.accept(ex.getMessage(), true);
        }
    }

    private LineChart<Number, Number> buildChart(
            String title,
            String xLabel,
            String yLabel,
            List<RunSample> samples,
            ToDoubleFunction<RunSample> xMapper,
            ToDoubleFunction<RunSample> yMapper
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
}