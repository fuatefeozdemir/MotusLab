package com.motuslab.ui;

import atlantafx.base.theme.Styles;
import com.motuslab.exception.MotusLabException;
import com.motuslab.model.Experiment;
import com.motuslab.model.RunMetric;
import com.motuslab.model.RunSample;
import com.motuslab.model.SimulationRun;
import com.motuslab.service.SimulationRunService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

public final class RunComparisonDialog {

    private static final int MAX_CHART_POINTS = 500;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private RunComparisonDialog() {
    }

    public static void show(
            Experiment experiment,
            List<SimulationRun> completedRuns,
            SimulationRunService simulationRunService
    ) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Çalıştırma Karşılaştırma");
        dialog.setHeaderText(experiment.getTitle() + " — İki Çalıştırmayı Karşılaştır");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);

        ComboBox<SimulationRun> runABox = createRunComboBox(completedRuns);
        ComboBox<SimulationRun> runBBox = createRunComboBox(completedRuns);
        runABox.getSelectionModel().select(0);
        runBBox.getSelectionModel().select(1);

        Button compareBtn = new Button("Karşılaştır");
        compareBtn.getStyleClass().add(Styles.ACCENT);

        Label feedback = new Label();
        feedback.setWrapText(true);
        feedback.setVisible(false);
        feedback.setManaged(false);

        VBox resultBox = new VBox(16);

        Runnable doCompare = () -> {
            feedback.setVisible(false);
            feedback.setManaged(false);
            resultBox.getChildren().clear();

            SimulationRun runA = runABox.getValue();
            SimulationRun runB = runBBox.getValue();
            if (runA == null || runB == null) {
                return;
            }
            if (runA.getId() == runB.getId()) {
                feedback.setText("Karşılaştırma için iki farklı çalıştırma seçin.");
                feedback.getStyleClass().setAll(Styles.DANGER);
                feedback.setVisible(true);
                feedback.setManaged(true);
                return;
            }

            try {
                List<RunMetric> metricsA = simulationRunService.getMetricsByRun(runA.getId());
                List<RunMetric> metricsB = simulationRunService.getMetricsByRun(runB.getId());
                List<RunSample> samplesA = downsample(simulationRunService.getSamplesByRun(runA.getId()), MAX_CHART_POINTS);
                List<RunSample> samplesB = downsample(simulationRunService.getSamplesByRun(runB.getId()), MAX_CHART_POINTS);

                String labelA = "Run #" + runA.getDisplayName();
                String labelB = "Run #" + runB.getDisplayName();

                GridPane metricsGrid = buildMetricsGrid(labelA, labelB, metricsA, metricsB);

                boolean is2D = "PROJECTILE_2D".equals(experiment.getSimulationTypeCode());
                HBox chartsBox = new HBox(16);
                if (is2D) {
                    LineChart<Number, Number> trajectory = buildOverlayChart(
                            "Yörünge (y - x)", "Konum x (m)", "Yükseklik y (m)",
                            labelA, samplesA, labelB, samplesB,
                            RunSample::getPositionX, s -> s.getPositionY() != null ? s.getPositionY() : 0.0
                    );
                    LineChart<Number, Number> speed = buildOverlayChart(
                            "Hız Büyüklüğü - Zaman", "Zaman (s)", "Hız (m/s)",
                            labelA, samplesA, labelB, samplesB,
                            RunSample::getTimeSeconds, s -> Math.hypot(s.getVelocityX(), s.getVelocityY() != null ? s.getVelocityY() : 0.0)
                    );
                    HBox.setHgrow(trajectory, Priority.ALWAYS);
                    HBox.setHgrow(speed, Priority.ALWAYS);
                    chartsBox.getChildren().addAll(trajectory, speed);
                } else {
                    LineChart<Number, Number> pos = buildOverlayChart(
                            "Konum - Zaman", "Zaman (s)", "Konum x (m)",
                            labelA, samplesA, labelB, samplesB,
                            RunSample::getTimeSeconds, RunSample::getPositionX
                    );
                    LineChart<Number, Number> vel = buildOverlayChart(
                            "Hız - Zaman", "Zaman (s)", "Hız vx (m/s)",
                            labelA, samplesA, labelB, samplesB,
                            RunSample::getTimeSeconds, RunSample::getVelocityX
                    );
                    HBox.setHgrow(pos, Priority.ALWAYS);
                    HBox.setHgrow(vel, Priority.ALWAYS);
                    chartsBox.getChildren().addAll(pos, vel);
                }

                Label tableTitle = new Label("Metrik Karşılaştırması");
                tableTitle.getStyleClass().add(Styles.TITLE_4);

                resultBox.getChildren().addAll(tableTitle, metricsGrid, chartsBox);
            } catch (MotusLabException ex) {
                feedback.setText(ex.getMessage());
                feedback.getStyleClass().setAll(Styles.DANGER);
                feedback.setVisible(true);
                feedback.setManaged(true);
            }
        };

        compareBtn.setOnAction(e -> doCompare.run());

        HBox selectorBar = new HBox(10,
                new Label("Run A:"), runABox,
                new Label("Run B:"), runBBox,
                compareBtn
        );
        selectorBar.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(14, selectorBar, feedback, new Separator(), resultBox);
        content.setPadding(new Insets(12));

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefSize(960, 620);

        dialog.getDialogPane().setContent(scrollPane);

        doCompare.run();
        dialog.showAndWait();
    }

    private static ComboBox<SimulationRun> createRunComboBox(List<SimulationRun> runs) {
        ComboBox<SimulationRun> box = new ComboBox<>(FXCollections.observableArrayList(runs));
        box.setPrefWidth(260);
        box.setCellFactory(lv -> createRunCell());
        box.setButtonCell(createRunCell());
        return box;
    }

    private static ListCell<SimulationRun> createRunCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(SimulationRun item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String time = item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_FMT) : "-";
                    setText(item.getDisplayName() + " (" + time + ")");
                }
            }
        };
    }

    private static GridPane buildMetricsGrid(
            String labelA,
            String labelB,
            List<RunMetric> metricsA,
            List<RunMetric> metricsB
    ) {
        Map<String, RunMetric> mapA = new LinkedHashMap<>();
        for (RunMetric m : metricsA) {
            mapA.put(m.getMetricCode(), m);
        }
        Map<String, RunMetric> mapB = new LinkedHashMap<>();
        for (RunMetric m : metricsB) {
            mapB.put(m.getMetricCode(), m);
        }

        GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.setVgap(8);
        grid.setPadding(new Insets(8, 12, 8, 12));
        grid.setStyle("-fx-border-color: -color-border-default; -fx-border-radius: 6;");

        Label hMetric = new Label("Metrik");
        Label hA = new Label(labelA);
        Label hB = new Label(labelB);
        Label hDiff = new Label("Fark (B - A)");
        hMetric.getStyleClass().add(Styles.TEXT_BOLD);
        hA.getStyleClass().add(Styles.TEXT_BOLD);
        hB.getStyleClass().add(Styles.TEXT_BOLD);
        hDiff.getStyleClass().add(Styles.TEXT_BOLD);

        grid.addRow(0, hMetric, hA, hB, hDiff);

        int row = 1;
        for (String code : mapA.keySet()) {
            RunMetric mA = mapA.get(code);
            RunMetric mB = mapB.get(code);
            String unit = mA.getUnit();

            String valA = String.format(Locale.US, "%.3f %s", mA.getMetricValue(), unit);
            String valB = mB != null ? String.format(Locale.US, "%.3f %s", mB.getMetricValue(), unit) : "-";
            String diff = mB != null
                    ? String.format(Locale.US, "%+.3f %s", mB.getMetricValue() - mA.getMetricValue(), unit)
                    : "-";

            grid.addRow(row++, new Label(formatMetricName(code)), new Label(valA), new Label(valB), new Label(diff));
        }

        return grid;
    }

    private static LineChart<Number, Number> buildOverlayChart(
            String title,
            String xLabel,
            String yLabel,
            String nameA,
            List<RunSample> samplesA,
            String nameB,
            List<RunSample> samplesB,
            ToDoubleFunction<RunSample> xMapper,
            ToDoubleFunction<RunSample> yMapper
    ) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel(xLabel);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(yLabel);

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setLegendVisible(true);
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        chart.setPrefHeight(300);

        XYChart.Series<Number, Number> seriesA = new XYChart.Series<>();
        seriesA.setName(nameA);
        for (RunSample s : samplesA) {
            seriesA.getData().add(new XYChart.Data<>(xMapper.applyAsDouble(s), yMapper.applyAsDouble(s)));
        }

        XYChart.Series<Number, Number> seriesB = new XYChart.Series<>();
        seriesB.setName(nameB);
        for (RunSample s : samplesB) {
            seriesB.getData().add(new XYChart.Data<>(xMapper.applyAsDouble(s), yMapper.applyAsDouble(s)));
        }

        chart.getData().addAll(seriesA, seriesB);
        return chart;
    }

    private static List<RunSample> downsample(List<RunSample> samples, int maxPoints) {
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