package com.motuslab.ui;

import atlantafx.base.theme.Styles;
import com.motuslab.exception.MotusLabException;
import com.motuslab.model.Experiment;
import com.motuslab.model.RunMetric;
import com.motuslab.model.RunSample;
import com.motuslab.model.SimulationRun;
import com.motuslab.service.CsvExportService;
import com.motuslab.service.SimulationRunService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.ToDoubleFunction;

public class RunResultPane extends VBox {

    private static final int MAX_CHART_POINTS = 500;

    private final SimulationRunService simulationRunService;
    private final CsvExportService csvExportService;
    private final BiConsumer<String, Boolean> feedbackCallback;

    public RunResultPane(
            SimulationRunService simulationRunService,
            CsvExportService csvExportService,
            BiConsumer<String, Boolean> feedbackCallback
    ) {
        super(14);
        this.simulationRunService = simulationRunService;
        this.csvExportService = csvExportService;
        this.feedbackCallback = feedbackCallback;
    }

    public void render(Experiment experiment, SimulationRun run) {
        getChildren().clear();

        if (run == null) {
            Label empty = new Label("Henüz bir çalıştırma kaydı yok.");
            empty.getStyleClass().add(Styles.TEXT_MUTED);
            getChildren().add(empty);
            return;
        }

        if ("FAILED".equals(run.getStatus())) {
            Label errLabel = new Label("Çalıştırma başarısız oldu: " +
                    (run.getErrorMessage() != null ? run.getErrorMessage() : "Bilinmeyen hata"));
            errLabel.getStyleClass().add(Styles.DANGER);
            errLabel.setWrapText(true);
            getChildren().add(errLabel);
            return;
        }

        if (!"COMPLETED".equals(run.getStatus())) {
            Label statusLabel = new Label("Durum: " + run.getStatus());
            statusLabel.getStyleClass().add(Styles.TEXT_MUTED);
            getChildren().add(statusLabel);
            return;
        }

        try {
            List<RunMetric> metrics = simulationRunService.getMetricsByRun(run.getId());
            List<RunSample> samples = simulationRunService.getSamplesByRun(run.getId());

            Label metricsTitle = new Label("Sonuç Metrikleri (Run #" + run.getRunNumber() + ")");
            metricsTitle.getStyleClass().add(Styles.TITLE_4);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button exportCsvBtn = new Button("CSV Dışa Aktar");
            exportCsvBtn.setOnAction(e -> handleExportCsv(experiment, run, metrics, samples));

            HBox headerRow = new HBox(10, metricsTitle, spacer, exportCsvBtn);
            headerRow.setAlignment(Pos.CENTER_LEFT);

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

            getChildren().addAll(headerRow, metricsPane, chartsBox);
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