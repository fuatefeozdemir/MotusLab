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
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.feather.Feather;

import java.io.File;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

public class RunResultPane extends VBox {

    private static final int MAX_CHART_POINTS = 500;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final SimulationRunService simulationRunService;
    private final CsvExportService csvExportService;
    private final BiConsumer<String, Boolean> feedbackHandler;
    private final Consumer<Integer> onRunsChanged;

    public RunResultPane(
            SimulationRunService simulationRunService,
            CsvExportService csvExportService,
            BiConsumer<String, Boolean> feedbackHandler,
            Consumer<Integer> onRunsChanged
    ) {
        super(16);
        this.simulationRunService = simulationRunService;
        this.csvExportService = csvExportService;
        this.feedbackHandler = feedbackHandler;
        this.onRunsChanged = onRunsChanged;
    }

    public void render(Experiment experiment, SimulationRun run) {
        getChildren().clear();
        if (experiment == null || run == null) {
            Label empty = new Label("Sonuçları incelemek için simülasyonu çalıştırın veya sağdaki Geçmiş sekmesinden bir çalıştırma seçin.");
            empty.getStyleClass().add("ml-text-muted");
            getChildren().add(empty);
            return;
        }

        // --- Çalıştırma Üst Şeridi ---
        Label runTitle = new Label(run.getDisplayName());
        runTitle.getStyleClass().add("ml-section-title");

        String timeText = run.getCreatedAt() != null ? run.getCreatedAt().format(DATE_FMT) : "-";
        Label runMeta = new Label(run.getStatus() + "  ·  " + timeText + "  ·  @" + run.getTriggeredByUsername());
        runMeta.getStyleClass().addAll("ml-text-muted", "ml-mono");

        VBox titleGroup = new VBox(2, runTitle, runMeta);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button editRunBtn = new Button("", UiHelper.icon(Feather.EDIT_2, 14));
        editRunBtn.getStyleClass().add("ml-icon-btn");
        editRunBtn.setTooltip(new Tooltip("Çalıştırma Adını / Notunu Düzenle"));
        editRunBtn.setOnAction(e -> {
            if (EditRunDialog.show(run, simulationRunService)) {
                feedbackHandler.accept("Çalıştırma bilgileri güncellendi.", false);
                onRunsChanged.accept(run.getId());
            }
        });

        Button exportCsvBtn = new Button("CSV", UiHelper.icon(Feather.DOWNLOAD, 13));
        exportCsvBtn.getStyleClass().add("ml-btn-secondary");
        exportCsvBtn.setGraphicTextGap(5);
        exportCsvBtn.setDisable(!"COMPLETED".equals(run.getStatus()));
        exportCsvBtn.setOnAction(e -> handleExportCsv(experiment, run));

        Button deleteRunBtn = new Button("", UiHelper.icon(Feather.TRASH_2, 14, "ml-icon-danger"));
        deleteRunBtn.getStyleClass().add("ml-icon-btn");
        deleteRunBtn.setTooltip(new Tooltip("Çalıştırmayı Sil"));
        deleteRunBtn.setOnAction(e -> handleDeleteRun(run));

        HBox runHeader = new HBox(8, titleGroup, spacer, editRunBtn, exportCsvBtn, deleteRunBtn);
        runHeader.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(runHeader);

        if (run.getNotes() != null && !run.getNotes().isBlank()) {
            Label notes = new Label(run.getNotes());
            notes.getStyleClass().add("ml-text-secondary");
            notes.setWrapText(true);
            getChildren().add(notes);
        }

        if ("FAILED".equals(run.getStatus())) {
            Label err = new Label("Hata: " + (run.getErrorMessage() != null ? run.getErrorMessage() : "Bilinmeyen hata"));
            err.getStyleClass().add("ml-feedback-error");
            err.setWrapText(true);
            getChildren().add(err);
            return;
        }

        try {
            List<RunMetric> metrics = simulationRunService.getMetricsByRun(run.getId());
            List<RunSample> allSamples = simulationRunService.getSamplesByRun(run.getId());
            List<RunParameterSnapshot> snapshots = simulationRunService.getSnapshotsByRun(run.getId());
            List<RunStatusHistory> history = simulationRunService.getStatusHistoryByRun(run.getId());

            if (!metrics.isEmpty()) {
                getChildren().add(buildMetricsRow(metrics));
            }

            if (!allSamples.isEmpty()) {
                List<RunSample> chartSamples = downsample(allSamples, MAX_CHART_POINTS);
                boolean is2D = "PROJECTILE_2D".equals(experiment.getSimulationTypeCode());

                if (is2D) {
                    VBox trajectoryCard = buildChartCard(
                            "Yörünge (y - x)",
                            "Cismin yatay menzil boyunca yükseklik profili · " + allSamples.size() + " örnek",
                            "Konum x (m)", "Yükseklik y (m)", "m", "m",
                            chartSamples,
                            RunSample::getPositionX,
                            s -> s.getPositionY() != null ? s.getPositionY() : 0.0,
                            360
                    );
                    VBox speedCard = buildChartCard(
                            "Hız Büyüklüğü - Zaman",
                            "Anlık hız vektörü büyüklüğünün zamana göre değişimi",
                            "Zaman (s)", "Hız (m/s)", "s", "m/s",
                            chartSamples,
                            RunSample::getTimeSeconds,
                            s -> Math.hypot(s.getVelocityX(), s.getVelocityY() != null ? s.getVelocityY() : 0.0),
                            270
                    );
                    getChildren().addAll(trajectoryCard, speedCard);
                } else {
                    VBox posCard = buildChartCard(
                            "Konum - Zaman",
                            "Cismin x ekseni üzerindeki konum değişimi · " + allSamples.size() + " örnek",
                            "Zaman (s)", "Konum x (m)", "s", "m",
                            chartSamples,
                            RunSample::getTimeSeconds,
                            RunSample::getPositionX,
                            310
                    );
                    VBox velCard = buildChartCard(
                            "Hız - Zaman",
                            "Cismin anlık hızının zamana göre değişimi",
                            "Zaman (s)", "Hız vx (m/s)", "s", "m/s",
                            chartSamples,
                            RunSample::getTimeSeconds,
                            RunSample::getVelocityX,
                            270
                    );
                    getChildren().addAll(posCard, velCard);
                }
            }

            if (!snapshots.isEmpty()) {
                getChildren().add(buildSnapshotsPane(snapshots));
            }
            if (!history.isEmpty()) {
                getChildren().add(buildStatusHistoryPane(history));
            }
        } catch (MotusLabException ex) {
            feedbackHandler.accept(ex.getMessage(), true);
        }
    }

    private FlowPane buildMetricsRow(List<RunMetric> metrics) {
        FlowPane pane = new FlowPane(12, 12);
        for (RunMetric m : metrics) {
            Label name = new Label(formatMetricName(m.getMetricCode()));
            name.getStyleClass().add("ml-text-muted");

            Label value = new Label(String.format(Locale.US, "%.3f %s", m.getMetricValue(), m.getUnit()));
            value.getStyleClass().add("ml-metric-value");

            VBox card = new VBox(4, name, value);
            card.getStyleClass().add("ml-metric-card");
            card.setMinWidth(155);
            pane.getChildren().add(card);
        }
        return pane;
    }

    private VBox buildChartCard(
            String title, String subtitle,
            String xLabel, String yLabel,
            String xUnit, String yUnit,
            List<RunSample> samples,
            ToDoubleFunction<RunSample> xMapper,
            ToDoubleFunction<RunSample> yMapper,
            double chartHeight
    ) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel(xLabel);
        xAxis.setForceZeroInRange(false);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(yLabel);
        yAxis.setForceZeroInRange(false);

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        chart.setPrefHeight(chartHeight);

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        for (RunSample s : samples) {
            series.getData().add(new XYChart.Data<>(xMapper.applyAsDouble(s), yMapper.applyAsDouble(s)));
        }
        chart.getData().add(series);

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("ml-section-title");
        Label subLbl = new Label(subtitle);
        subLbl.getStyleClass().add("ml-text-muted");
        VBox titleBox = new VBox(2, titleLbl, subLbl);

        Label coordLabel = new Label("—");
        coordLabel.getStyleClass().add("ml-coord-readout");

        Button zoomInBtn = new Button("", UiHelper.icon(Feather.ZOOM_IN, 13));
        zoomInBtn.getStyleClass().add("ml-icon-btn");
        zoomInBtn.setTooltip(new Tooltip("Yakınlaştır"));
        zoomInBtn.setOnAction(e -> zoomAxes(xAxis, yAxis, 0.75));

        Button zoomOutBtn = new Button("", UiHelper.icon(Feather.ZOOM_OUT, 13));
        zoomOutBtn.getStyleClass().add("ml-icon-btn");
        zoomOutBtn.setTooltip(new Tooltip("Uzaklaştır"));
        zoomOutBtn.setOnAction(e -> zoomAxes(xAxis, yAxis, 1.33));

        Button resetBtn = new Button("", UiHelper.icon(Feather.ROTATE_CCW, 13));
        resetBtn.getStyleClass().add("ml-icon-btn");
        resetBtn.setTooltip(new Tooltip("Görünümü Sıfırla"));
        resetBtn.setOnAction(e -> {
            xAxis.setAutoRanging(true);
            yAxis.setAutoRanging(true);
        });

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        HBox header = new HBox(8, titleBox, sp, coordLabel, zoomInBtn, zoomOutBtn, resetBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        // Fare ile gezerken canlı koordinat okuması
        chart.setOnMouseMoved(e -> {
            Node plot = chart.lookup(".chart-plot-background");
            if (plot == null) return;
            Point2D p = plot.sceneToLocal(e.getSceneX(), e.getSceneY());
            if (p.getX() >= 0 && p.getY() >= 0 && p.getX() <= plot.getBoundsInLocal().getWidth()
                    && p.getY() <= plot.getBoundsInLocal().getHeight()) {
                Number xVal = xAxis.getValueForDisplay(p.getX());
                Number yVal = yAxis.getValueForDisplay(p.getY());
                if (xVal != null && yVal != null) {
                    coordLabel.setText(String.format(Locale.US, "%.2f %s, %.2f %s",
                            xVal.doubleValue(), xUnit, yVal.doubleValue(), yUnit));
                }
            }
        });
        chart.setOnMouseExited(e -> coordLabel.setText("—"));

        VBox card = new VBox(8, header, chart);
        card.getStyleClass().add("ml-chart-card");
        return card;
    }

    private static void zoomAxes(NumberAxis xAxis, NumberAxis yAxis, double factor) {
        zoomAxis(xAxis, factor);
        zoomAxis(yAxis, factor);
    }

    private static void zoomAxis(NumberAxis axis, double factor) {
        double lower = axis.getLowerBound();
        double upper = axis.getUpperBound();
        double range = upper - lower;
        if (range <= 0) return;

        double center = (lower + upper) / 2.0;
        double half = (range * factor) / 2.0;

        axis.setAutoRanging(false);
        axis.setLowerBound(center - half);
        axis.setUpperBound(center + half);
        axis.setTickUnit(Math.max(1e-6, (half * 2.0) / 6.0));
    }

    private TitledPane buildSnapshotsPane(List<RunParameterSnapshot> snapshots) {
        VBox box = new VBox(6);
        for (RunParameterSnapshot s : snapshots) {
            String unit = (s.getUnit() != null && !s.getUnit().isBlank()) ? " " + s.getUnit() : "";
            Label lbl = new Label(s.getParameterName() + ": " + s.getValue() + unit);
            lbl.getStyleClass().add("ml-mono");
            box.getChildren().add(lbl);
        }
        TitledPane pane = new TitledPane("Çalıştırma Parametre Snapshot'ı", box);
        pane.setExpanded(false);
        return pane;
    }

    private TitledPane buildStatusHistoryPane(List<RunStatusHistory> history) {
        VBox box = new VBox(6);
        for (RunStatusHistory h : history) {
            String time = h.getChangedAt() != null ? h.getChangedAt().format(DATE_FMT) : "-";
            String from = h.getOldStatus() != null ? h.getOldStatus() + " → " : "";
            Label lbl = new Label(time + "  ·  " + from + h.getNewStatus());
            lbl.getStyleClass().addAll("ml-mono", "ml-text-secondary");
            box.getChildren().add(lbl);
        }
        TitledPane pane = new TitledPane("Durum Geçmişi", box);
        pane.setExpanded(false);
        return pane;
    }

    private void handleExportCsv(Experiment experiment, SimulationRun run) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("CSV Olarak Dışa Aktar");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Dosyaları", "*.csv"));
        chooser.setInitialFileName("motuslab_run_" + run.getRunNumber() + ".csv");

        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;

        try {
            List<RunMetric> metrics = simulationRunService.getMetricsByRun(run.getId());
            List<RunSample> samples = simulationRunService.getSamplesByRun(run.getId());
            csvExportService.exportRunToCsv(
                    file.toPath(),
                    experiment.getTitle(),
                    run,
                    metrics,
                    samples
            );            feedbackHandler.accept("CSV dışa aktarıldı: " + file.getName(), false);
        } catch (MotusLabException ex) {
            feedbackHandler.accept(ex.getMessage(), true);
        }
    }

    private void handleDeleteRun(SimulationRun run) {
        boolean confirmed = UiHelper.showConfirm(
                "Çalıştırmayı Sil",
                run.getDisplayName(),
                "Bu çalıştırma ve tüm zaman serisi verileri kalıcı olarak silinecek. Emin misiniz?"
        );
        if (confirmed) {
            try {
                simulationRunService.deleteRun(run.getId());
                feedbackHandler.accept("Çalıştırma silindi.", false);
                onRunsChanged.accept(null);
            } catch (MotusLabException ex) {
                feedbackHandler.accept(ex.getMessage(), true);
            }
        }
    }

    private static List<RunSample> downsample(List<RunSample> samples, int maxPoints) {
        if (samples.size() <= maxPoints) return samples;
        List<RunSample> result = new ArrayList<>(maxPoints);
        double step = (double) (samples.size() - 1) / (maxPoints - 1);
        for (int i = 0; i < maxPoints; i++) {
            int idx = (int) Math.round(i * step);
            result.add(samples.get(Math.min(idx, samples.size() - 1)));
        }
        return result;
    }

    private static String formatMetricName(String code) {
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