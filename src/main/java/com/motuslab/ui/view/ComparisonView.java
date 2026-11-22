package com.motuslab.ui.view;

import com.motuslab.exception.MotusLabException;
import com.motuslab.model.Experiment;
import com.motuslab.model.RunMetric;
import com.motuslab.model.RunSample;
import com.motuslab.model.SimulationRun;
import com.motuslab.service.SimulationRunService;
import com.motuslab.ui.UiHelper;
import javafx.collections.FXCollections;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
    private final VBox resultBox = new VBox(18);

    public ComparisonView(SimulationRunService simulationRunService) {
        this.simulationRunService = simulationRunService;

        setFitToWidth(true);
        getStyleClass().add("ml-scroll-pane");

        // --- Başlık ---
        Label title = new Label("Karşılaştırma");
        title.getStyleClass().add("ml-page-title");

        Label subtitle = new Label("Aynı deneye ait iki tamamlanmış çalıştırmanın yörünge, hız ve metrik farklarını inceleyin.");
        subtitle.getStyleClass().add("ml-text-secondary");

        // --- 1. Üst Seçim Alanı (Deney + Yan Yana Run A / Run B) ---
        experimentBox.setMaxWidth(Double.MAX_VALUE);
        experimentBox.setCellFactory(lv -> createExperimentCell());
        experimentBox.setButtonCell(createExperimentCell());
        experimentBox.valueProperty().addListener((obs, o, n) -> loadRunsForExperiment(n));

        VBox expGroup = new VBox(5, metaLabel("DENEY"), experimentBox);
        HBox.setHgrow(expGroup, Priority.ALWAYS);

        Region dotA = new Region();
        dotA.getStyleClass().add("ml-series-dot-a");
        HBox labelA = new HBox(6, dotA, metaLabel("RUN A (REFERANS)"));
        labelA.setAlignment(Pos.CENTER_LEFT);
        runABox.setMaxWidth(Double.MAX_VALUE);
        VBox runAGroup = new VBox(5, labelA, runABox);
        HBox.setHgrow(runAGroup, Priority.ALWAYS);

        Region dotB = new Region();
        dotB.getStyleClass().add("ml-series-dot-b");
        HBox labelB = new HBox(6, dotB, metaLabel("RUN B (KARŞILAŞTIRILAN)"));
        labelB.setAlignment(Pos.CENTER_LEFT);
        runBBox.setMaxWidth(Double.MAX_VALUE);
        VBox runBGroup = new VBox(5, labelB, runBBox);
        HBox.setHgrow(runBGroup, Priority.ALWAYS);

        Button compareBtn = new Button("Karşılaştır", UiHelper.icon(Feather.GIT_MERGE, 14, "ml-icon-light"));
        compareBtn.getStyleClass().add("ml-btn-primary");
        compareBtn.setGraphicTextGap(6);
        compareBtn.setOnAction(e -> doCompare());

        HBox selectorRow = new HBox(14, expGroup, runAGroup, runBGroup, compareBtn);
        selectorRow.setAlignment(Pos.BOTTOM_LEFT);

        feedbackLabel.setWrapText(true);
        UiHelper.clearFeedback(feedbackLabel);

        VBox selectorCard = new VBox(10, selectorRow, feedbackLabel);
        selectorCard.getStyleClass().add("ml-card");

        VBox content = new VBox(20, new VBox(4, title, subtitle), selectorCard, resultBox);
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

            // 2. Nötr Metrik Farkları Şeridi
            FlowPane diffStrip = buildDiffCards(metricsA, metricsB);

            // 3. Geniş Karşılaştırma Grafikleri
            boolean is2D = "PROJECTILE_2D".equals(exp.getSimulationTypeCode());
            VBox chartsBox = new VBox(16);
            if (is2D) {
                VBox trajectory = buildOverlayChartCard(
                        "Yörünge Karşılaştırması (y - x)",
                        labelA + " ve " + labelB + " yatay menzil - yükseklik profilleri",
                        "Konum x (m)", "Yükseklik y (m)", "m", "m",
                        labelA, samplesA, labelB, samplesB,
                        RunSample::getPositionX, s -> s.getPositionY() != null ? s.getPositionY() : 0.0,
                        360
                );
                VBox speed = buildOverlayChartCard(
                        "Hız Büyüklüğü - Zaman",
                        "İki çalıştırmanın zamana bağlı anlık hız karşılaştırması",
                        "Zaman (s)", "Hız (m/s)", "s", "m/s",
                        labelA, samplesA, labelB, samplesB,
                        RunSample::getTimeSeconds, s -> Math.hypot(s.getVelocityX(), s.getVelocityY() != null ? s.getVelocityY() : 0.0),
                        280
                );
                chartsBox.getChildren().addAll(trajectory, speed);
            } else {
                VBox pos = buildOverlayChartCard(
                        "Konum - Zaman Karşılaştırması",
                        labelA + " ve " + labelB + " konum eğrileri",
                        "Zaman (s)", "Konum x (m)", "s", "m",
                        labelA, samplesA, labelB, samplesB,
                        RunSample::getTimeSeconds, RunSample::getPositionX,
                        320
                );
                VBox vel = buildOverlayChartCard(
                        "Hız - Zaman Karşılaştırması",
                        "İki çalıştırmanın zamana bağlı hız profilleri",
                        "Zaman (s)", "Hız vx (m/s)", "s", "m/s",
                        labelA, samplesA, labelB, samplesB,
                        RunSample::getTimeSeconds, RunSample::getVelocityX,
                        280
                );
                chartsBox.getChildren().addAll(pos, vel);
            }

            // 4. Detaylı Metrik Tablosu
            Label tableTitle = new Label("Detaylı Metrik Tablosu");
            tableTitle.getStyleClass().add("ml-section-title");
            GridPane metricsGrid = buildMetricsGrid(labelA, labelB, metricsA, metricsB);

            resultBox.getChildren().addAll(diffStrip, chartsBox, new VBox(8, tableTitle, metricsGrid));
        } catch (MotusLabException ex) {
            UiHelper.setFeedback(feedbackLabel, ex.getMessage(), true);
        }
    }

    private static FlowPane buildDiffCards(List<RunMetric> metricsA, List<RunMetric> metricsB) {
        Map<String, RunMetric> mapB = new LinkedHashMap<>();
        for (RunMetric m : metricsB) mapB.put(m.getMetricCode(), m);

        FlowPane pane = new FlowPane(12, 12);
        for (RunMetric mA : metricsA) {
            RunMetric mB = mapB.get(mA.getMetricCode());
            if (mB == null) continue;

            double delta = mB.getMetricValue() - mA.getMetricValue();
            String diffText = String.format(Locale.US, "%+.3f %s", delta, mA.getUnit());

            Label nameLbl = new Label(formatMetricName(mA.getMetricCode()).toUpperCase(Locale.of("tr", "TR")));
            nameLbl.getStyleClass().add("ml-card-meta-label");

            Label diffVal = new Label(diffText);
            diffVal.getStyleClass().add("ml-metric-value");

            Label pairLbl = new Label(String.format(Locale.US, "%.2f → %.2f %s",
                    mA.getMetricValue(), mB.getMetricValue(), mA.getUnit()));
            pairLbl.getStyleClass().addAll("ml-mono", "ml-text-muted");

            VBox card = new VBox(4, nameLbl, diffVal, pairLbl);
            card.getStyleClass().add("ml-metric-card");
            card.setMinWidth(165);
            pane.getChildren().add(card);
        }
        return pane;
    }

    private static VBox buildOverlayChartCard(
            String title, String subtitle,
            String xLabel, String yLabel,
            String xUnit, String yUnit,
            String nameA, List<RunSample> samplesA,
            String nameB, List<RunSample> samplesB,
            ToDoubleFunction<RunSample> xMapper, ToDoubleFunction<RunSample> yMapper,
            double height
    ) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel(xLabel);
        xAxis.setForceZeroInRange(false);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(yLabel);
        yAxis.setForceZeroInRange(false);

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setLegendVisible(true);
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        chart.setPrefHeight(height);

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

    private static GridPane buildMetricsGrid(String labelA, String labelB, List<RunMetric> metricsA, List<RunMetric> metricsB) {
        Map<String, RunMetric> mapA = new LinkedHashMap<>();
        for (RunMetric m : metricsA) mapA.put(m.getMetricCode(), m);
        Map<String, RunMetric> mapB = new LinkedHashMap<>();
        for (RunMetric m : metricsB) mapB.put(m.getMetricCode(), m);

        GridPane grid = new GridPane();
        grid.setHgap(32);
        grid.setVgap(10);
        grid.getStyleClass().add("ml-card");

        Label hMetric = metaLabel("METRİK");
        Label hA = metaLabel(labelA.toUpperCase(Locale.ROOT));
        Label hB = metaLabel(labelB.toUpperCase(Locale.ROOT));
        Label hDiff = metaLabel("FARK (B - A)");
        grid.addRow(0, hMetric, hA, hB, hDiff);

        int row = 1;
        for (String code : mapA.keySet()) {
            RunMetric mA = mapA.get(code);
            RunMetric mB = mapB.get(code);
            String unit = mA.getUnit();

            Label nameLbl = new Label(formatMetricName(code));
            nameLbl.getStyleClass().add("ml-text-bold");

            Label valA = new Label(String.format(Locale.US, "%.3f %s", mA.getMetricValue(), unit));
            Label valB = new Label(mB != null ? String.format(Locale.US, "%.3f %s", mB.getMetricValue(), unit) : "-");
            Label diff = new Label(mB != null ? String.format(Locale.US, "%+.3f %s", mB.getMetricValue() - mA.getMetricValue(), unit) : "-");
            valA.getStyleClass().add("ml-mono");
            valB.getStyleClass().add("ml-mono");
            diff.getStyleClass().add("ml-diff-pill");

            grid.addRow(row++, nameLbl, valA, valB, diff);
        }
        return grid;
    }

    private static Label metaLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("ml-card-meta-label");
        return l;
    }

    private static ComboBox<SimulationRun> createRunComboBox() {
        ComboBox<SimulationRun> box = new ComboBox<>();
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