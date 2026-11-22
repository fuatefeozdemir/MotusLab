package com.motuslab.ui.view;

import com.motuslab.exception.MotusLabException;
import com.motuslab.model.Experiment;
import com.motuslab.model.RunMetric;
import com.motuslab.model.RunSample;
import com.motuslab.model.SimulationRun;
import com.motuslab.service.SimulationRunService;
import com.motuslab.ui.UiHelper;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

public class ComparisonView extends ScrollPane {

    private static final int MAX_CHART_POINTS = 500;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final SimulationRunService simulationRunService;

    private final ComboBox<Experiment> experimentBox = new ComboBox<>();
    private final ComboBox<SimulationRun> runABox = createRunComboBox();
    private final ComboBox<SimulationRun> runBBox = createRunComboBox();
    private final Label feedbackLabel = new Label();
    private final VBox resultBox = new VBox(16);

    public ComparisonView(SimulationRunService simulationRunService) {
        this.simulationRunService = simulationRunService;

        setFitToWidth(true);
        getStyleClass().add("ml-scroll-pane");

        Label title = new Label("Çalıştırma Karşılaştırma");
        title.getStyleClass().add("ml-page-title");

        Label subtitle = new Label("Aynı deneye ait iki tamamlanmış çalıştırmanın grafik ve metriklerini yan yana analiz edin.");
        subtitle.getStyleClass().add("ml-text-secondary");

        experimentBox.setPrefWidth(250);
        experimentBox.setCellFactory(lv -> createExperimentCell());
        experimentBox.setButtonCell(createExperimentCell());
        experimentBox.valueProperty().addListener((obs, o, n) -> loadRunsForExperiment(n));

        Button compareBtn = new Button("Karşılaştır", UiHelper.icon(Feather.GIT_MERGE, 14, "ml-icon-light"));
        compareBtn.getStyleClass().add("ml-btn-primary");
        compareBtn.setGraphicTextGap(6);
        compareBtn.setOnAction(e -> doCompare());

        HBox selectorBar = new HBox(10,
                new Label("Deney:"), experimentBox,
                new Label("Run A:"), runABox,
                new Label("Run B:"), runBBox,
                compareBtn
        );
        selectorBar.setAlignment(Pos.CENTER_LEFT);

        VBox selectorCard = new VBox(10, selectorBar, feedbackLabel);
        selectorCard.getStyleClass().add("ml-card");

        feedbackLabel.setWrapText(true);
        UiHelper.clearFeedback(feedbackLabel);

        VBox content = new VBox(18, new VBox(4, title, subtitle), selectorCard, resultBox);
        content.getStyleClass().add("ml-detail-pane");
        setContent(content);
    }

    public void setExperiments(List<Experiment> experiments, Experiment preselect) {
        experimentBox.setItems(FXCollections.observableArrayList(experiments));
        if (preselect != null) {
            experiments.stream()
                    .filter(e -> e.getId() == preselect.getId())
                    .findFirst()
                    .ifPresent(e -> experimentBox.getSelectionModel().select(e));
        } else if (!experiments.isEmpty()) {
            experimentBox.getSelectionModel().selectFirst();
        } else {
            runABox.getItems().clear();
            runBBox.getItems().clear();
            resultBox.getChildren().clear();
        }
    }

    private void loadRunsForExperiment(Experiment exp) {
        UiHelper.clearFeedback(feedbackLabel);
        resultBox.getChildren().clear();
        runABox.getItems().clear();
        runBBox.getItems().clear();
        if (exp == null) {
            return;
        }
        try {
            List<SimulationRun> completed = simulationRunService.getRunsByExperiment(exp.getId()).stream()
                    .filter(r -> "COMPLETED".equals(r.getStatus()))
                    .toList();

            runABox.setItems(FXCollections.observableArrayList(completed));
            runBBox.setItems(FXCollections.observableArrayList(completed));

            if (completed.size() >= 2) {
                runABox.getSelectionModel().select(0);
                runBBox.getSelectionModel().select(1);
                doCompare();
            } else {
                UiHelper.setFeedback(feedbackLabel, "Bu deneyde karşılaştırma için en az 2 tamamlanmış çalıştırma gerekir.", true);
            }
        } catch (MotusLabException ex) {
            UiHelper.setFeedback(feedbackLabel, ex.getMessage(), true);
        }
    }

    private void doCompare() {
        UiHelper.clearFeedback(feedbackLabel);
        resultBox.getChildren().clear();

        Experiment exp = experimentBox.getValue();
        SimulationRun runA = runABox.getValue();
        SimulationRun runB = runBBox.getValue();
        if (exp == null || runA == null || runB == null) {
            return;
        }
        if (runA.getId() == runB.getId()) {
            UiHelper.setFeedback(feedbackLabel, "Karşılaştırma için iki farklı çalıştırma seçin.", true);
            return;
        }

        try {
            List<RunMetric> metricsA = simulationRunService.getMetricsByRun(runA.getId());
            List<RunMetric> metricsB = simulationRunService.getMetricsByRun(runB.getId());
            List<RunSample> samplesA = downsample(simulationRunService.getSamplesByRun(runA.getId()), MAX_CHART_POINTS);
            List<RunSample> samplesB = downsample(simulationRunService.getSamplesByRun(runB.getId()), MAX_CHART_POINTS);

            String labelA = runA.getDisplayName();
            String labelB = runB.getDisplayName();

            GridPane metricsGrid = buildMetricsGrid(labelA, labelB, metricsA, metricsB);

            boolean is2D = "PROJECTILE_2D".equals(exp.getSimulationTypeCode());
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
            tableTitle.getStyleClass().add("ml-section-title");

            resultBox.getChildren().addAll(chartsBox, tableTitle, metricsGrid);
        } catch (MotusLabException ex) {
            UiHelper.setFeedback(feedbackLabel, ex.getMessage(), true);
        }
    }

    private static ComboBox<SimulationRun> createRunComboBox() {
        ComboBox<SimulationRun> box = new ComboBox<>();
        box.setPrefWidth(210);
        box.setCellFactory(lv -> createRunCell());
        box.setButtonCell(createRunCell());
        return box;
    }

    private static ListCell<Experiment> createExperimentCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Experiment item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getTitle());
            }
        };
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

    private static GridPane buildMetricsGrid(String labelA, String labelB, List<RunMetric> metricsA, List<RunMetric> metricsB) {
        Map<String, RunMetric> mapA = new LinkedHashMap<>();
        for (RunMetric m : metricsA) mapA.put(m.getMetricCode(), m);
        Map<String, RunMetric> mapB = new LinkedHashMap<>();
        for (RunMetric m : metricsB) mapB.put(m.getMetricCode(), m);

        GridPane grid = new GridPane();
        grid.setHgap(28);
        grid.setVgap(10);
        grid.getStyleClass().add("ml-card");

        Label hMetric = new Label("Metrik");
        Label hA = new Label(labelA);
        Label hB = new Label(labelB);
        Label hDiff = new Label("Fark (B - A)");
        hMetric.getStyleClass().add("ml-text-bold");
        hA.getStyleClass().add("ml-text-bold");
        hB.getStyleClass().add("ml-text-bold");
        hDiff.getStyleClass().add("ml-text-bold");
        grid.addRow(0, hMetric, hA, hB, hDiff);

        int row = 1;
        for (String code : mapA.keySet()) {
            RunMetric mA = mapA.get(code);
            RunMetric mB = mapB.get(code);
            String unit = mA.getUnit();

            Label valA = new Label(String.format(Locale.US, "%.3f %s", mA.getMetricValue(), unit));
            Label valB = new Label(mB != null ? String.format(Locale.US, "%.3f %s", mB.getMetricValue(), unit) : "-");
            Label diff = new Label(mB != null ? String.format(Locale.US, "%+.3f %s", mB.getMetricValue() - mA.getMetricValue(), unit) : "-");
            valA.getStyleClass().add("ml-mono");
            valB.getStyleClass().add("ml-mono");
            diff.getStyleClass().add("ml-mono");

            grid.addRow(row++, new Label(formatMetricName(code)), valA, valB, diff);
        }
        return grid;
    }

    private static LineChart<Number, Number> buildOverlayChart(
            String title, String xLabel, String yLabel,
            String nameA, List<RunSample> samplesA,
            String nameB, List<RunSample> samplesB,
            ToDoubleFunction<RunSample> xMapper, ToDoubleFunction<RunSample> yMapper
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
        chart.setPrefHeight(340);

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