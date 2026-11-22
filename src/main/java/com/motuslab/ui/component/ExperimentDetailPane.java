package com.motuslab.ui.component;

import com.motuslab.model.Experiment;
import com.motuslab.model.ExperimentParameter;
import com.motuslab.model.SimulationRun;
import com.motuslab.ui.RunResultPane;
import com.motuslab.ui.UiHelper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class ExperimentDetailPane extends BorderPane {

    public record Actions(
            Runnable onBack,
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

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final RunResultPane runResultPane;
    private final Actions actions;

    private final ListView<SimulationRun> runListView = new ListView<>();
    private final Label feedbackLabel = new Label();
    private final Label unsavedLabel = new Label("● Kaydedilmemiş değişiklikler");
    private final Map<Integer, TextField> parameterInputs = new LinkedHashMap<>();

    private Button runSimButton;
    private ProgressIndicator runProgress;

    public ExperimentDetailPane(RunResultPane runResultPane, Actions actions) {
        this.runResultPane = runResultPane;
        this.actions = actions;

        feedbackLabel.setWrapText(true);
        UiHelper.clearFeedback(feedbackLabel);

        unsavedLabel.getStyleClass().add("ml-unsaved-indicator");
        setUnsavedVisible(false);

        runListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(SimulationRun item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String time = item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_FMT) : "-";

                    Region dot = new Region();
                    dot.getStyleClass().addAll("ml-status-dot", statusDotClass(item.getStatus()));

                    Label name = new Label(item.getDisplayName());
                    name.getStyleClass().add("ml-text-bold");

                    Region sp = new Region();
                    HBox.setHgrow(sp, Priority.ALWAYS);

                    Label status = new Label(item.getStatus());
                    status.getStyleClass().addAll("ml-text-muted", "ml-mono");

                    HBox topRow = new HBox(8, dot, name, sp, status);
                    topRow.setAlignment(Pos.CENTER_LEFT);

                    Label sub = new Label(time + " · @" + item.getTriggeredByUsername());
                    sub.getStyleClass().addAll("ml-text-muted", "ml-mono");
                    sub.setPadding(new Insets(0, 0, 0, 16));

                    VBox box = new VBox(3, topRow, sub);
                    box.setPadding(new Insets(4, 2, 4, 2));
                    setGraphic(box);
                }
            }
        });

        runListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) ->
                actions.onSelectRun().accept(newVal)
        );
    }

    public void renderEmpty() {
        runResultPane.getChildren().clear();
        parameterInputs.clear();
        clearFeedback();
        setUnsavedVisible(false);
        setRight(null);

        Label title = new Label("Bir deney seçin");
        title.getStyleClass().add("ml-section-title");
        Label sub = new Label("Deney kütüphanesinden mevcut bir deneyi açın veya yeni bir simülasyon oluşturun.");
        sub.getStyleClass().add("ml-text-muted");

        VBox emptyBox = new VBox(10, UiHelper.icon(Feather.SLIDERS, 36, "ml-icon-accent"), title, sub);
        emptyBox.setAlignment(Pos.CENTER);
        setCenter(emptyBox);
    }

    public void renderExperiment(Experiment experiment, String permission, List<ExperimentParameter> params) {
        runResultPane.getChildren().clear();
        parameterInputs.clear();
        clearFeedback();
        setUnsavedVisible(false);
        runSimButton = null;
        runProgress = null;

        boolean isArchived = experiment.isArchived();
        boolean isOwner = "OWNER".equals(permission);
        boolean canEdit = (isOwner || "EDIT".equals(permission)) && !isArchived;
        boolean canRun = (canEdit || "RUN".equals(permission)) && !isArchived;

        // --- Breadcrumb ve Geri Dönüş ---
        Button backButton = new Button("Geri", UiHelper.icon(Feather.ARROW_LEFT, 14));
        backButton.getStyleClass().add("ml-back-btn");
        backButton.setGraphicTextGap(5);
        backButton.setOnAction(e -> actions.onBack().run());

        String sectionName = isArchived ? "ARŞİV" : "DENEYLER";
        Label breadcrumb = new Label(sectionName + "  /  " + experiment.getTitle().toUpperCase(Locale.ROOT));
        breadcrumb.getStyleClass().add("ml-breadcrumb");

        HBox navRow = new HBox(10, backButton, breadcrumb);
        navRow.setAlignment(Pos.CENTER_LEFT);

        // --- Başlık ve Aksiyon Barı ---
        Label titleLabel = new Label(experiment.getTitle());
        titleLabel.getStyleClass().add("ml-page-title");

        HBox titleRow = new HBox(10, titleLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        if (isArchived) {
            Label archiveBadge = new Label("ARŞİV");
            archiveBadge.getStyleClass().add("ml-badge-warning");
            titleRow.getChildren().add(archiveBadge);
        }

        Label metaLabel = new Label(
                experiment.getSimulationTypeName()
                        + "  ·  @" + experiment.getOwnerUsername()
                        + "  ·  " + permission
        );
        metaLabel.getStyleClass().addAll("ml-text-secondary", "ml-mono");

        VBox titleBlock = new VBox(5, titleRow, metaLabel);
        if (experiment.getDescription() != null && !experiment.getDescription().isBlank()) {
            Label descLabel = new Label(experiment.getDescription());
            descLabel.getStyleClass().add("ml-text-secondary");
            descLabel.setWrapText(true);
            titleBlock.getChildren().add(descLabel);
        }

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        HBox actionBox = new HBox(8);
        actionBox.setAlignment(Pos.CENTER_RIGHT);

        if (canRun) {
            runProgress = new ProgressIndicator();
            runProgress.setPrefSize(18, 18);
            runProgress.setVisible(false);
            runProgress.setManaged(false);

            runSimButton = new Button("Simülasyonu Çalıştır", UiHelper.icon(Feather.PLAY, 14, "ml-icon-light"));
            runSimButton.getStyleClass().add("ml-btn-primary");
            runSimButton.setGraphicTextGap(7);
            runSimButton.setOnAction(e -> actions.onRunSimulation().accept(canEdit));
            actionBox.getChildren().addAll(runProgress, runSimButton);
        }

        MenuButton moreMenu = buildMoreMenu(canEdit, isOwner, isArchived);
        if (moreMenu != null) {
            actionBox.getChildren().add(moreMenu);
        }

        HBox headerBar = new HBox(16, titleBlock, headerSpacer, actionBox);
        headerBar.setAlignment(Pos.TOP_LEFT);

        VBox centerContent = new VBox(16, navRow, headerBar, feedbackLabel, runResultPane);
        centerContent.getStyleClass().add("ml-detail-pane");

        ScrollPane centerScroll = new ScrollPane(centerContent);
        centerScroll.setFitToWidth(true);
        centerScroll.getStyleClass().add("ml-scroll-pane");
        setCenter(centerScroll);

        // --- Sağ Inspector Paneli ---
        Label inspectorKicker = new Label("INSPECTOR");
        inspectorKicker.getStyleClass().add("ml-inspector-kicker");

        VBox paramsTabContent = buildParamsInspector(params, canEdit);
        VBox runsTabContent = buildRunsInspector();

        Tab paramsTab = new Tab("Parametreler", paramsTabContent);
        paramsTab.setGraphic(UiHelper.icon(Feather.SLIDERS, 13));

        Tab runsTab = new Tab("Geçmiş", runsTabContent);
        runsTab.setGraphic(UiHelper.icon(Feather.CLOCK, 13));

        TabPane inspectorTabs = new TabPane(paramsTab, runsTab);
        inspectorTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        inspectorTabs.getStyleClass().add("ml-tabs");
        VBox.setVgrow(inspectorTabs, Priority.ALWAYS);

        VBox inspectorPane = new VBox(4, inspectorKicker, inspectorTabs);
        inspectorPane.getStyleClass().add("ml-inspector");
        inspectorPane.setPrefWidth(320);
        setRight(inspectorPane);
    }

    public void markParametersSaved() {
        setUnsavedVisible(false);
    }

    private void setUnsavedVisible(boolean visible) {
        unsavedLabel.setVisible(visible);
        unsavedLabel.setManaged(visible);
    }

    private static String statusDotClass(String status) {
        if ("COMPLETED".equals(status)) return "ml-dot-completed";
        if ("FAILED".equals(status)) return "ml-dot-failed";
        if ("RUNNING".equals(status)) return "ml-dot-running";
        return "ml-dot-created";
    }

    private MenuButton buildMoreMenu(boolean canEdit, boolean isOwner, boolean isArchived) {
        MenuButton menu = new MenuButton("", UiHelper.icon(Feather.MORE_VERTICAL, 16));
        menu.getStyleClass().add("ml-btn-secondary");

        if (canEdit) {
            MenuItem editItem = new MenuItem("Deneyi Düzenle", UiHelper.icon(Feather.EDIT_2, 14));
            editItem.setOnAction(e -> actions.onEditExperiment().run());
            menu.getItems().add(editItem);
        }
        if (isOwner) {
            if (!isArchived) {
                MenuItem shareItem = new MenuItem("Paylaş", UiHelper.icon(Feather.SHARE_2, 14));
                shareItem.setOnAction(e -> actions.onShareExperiment().run());
                menu.getItems().add(shareItem);
            }
            MenuItem archiveItem = new MenuItem(
                    isArchived ? "Arşivden Çıkar" : "Arşivle",
                    UiHelper.icon(Feather.ARCHIVE, 14)
            );
            archiveItem.setOnAction(e -> actions.onToggleArchive().accept(!isArchived));
            menu.getItems().add(archiveItem);

            menu.getItems().add(new SeparatorMenuItem());

            MenuItem deleteItem = new MenuItem("Deneyi Sil", UiHelper.icon(Feather.TRASH_2, 14, "ml-icon-danger"));
            deleteItem.setOnAction(e -> actions.onDeleteExperiment().run());
            menu.getItems().add(deleteItem);
        }

        return menu.getItems().isEmpty() ? null : menu;
    }

    private VBox buildParamsInspector(List<ExperimentParameter> params, boolean canEdit) {
        VBox listBox = new VBox(12);
        listBox.setPadding(new Insets(12, 4, 12, 4));

        for (ExperimentParameter param : params) {
            Label nameLabel = new Label(param.getParameterName());
            nameLabel.getStyleClass().add("ml-text-bold");

            Label unitLabel = new Label(param.getUnit() != null && !param.getUnit().isBlank() ? param.getUnit() : "");
            unitLabel.getStyleClass().addAll("ml-text-muted", "ml-mono");

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            TextField valueField = new TextField(String.valueOf(param.getValue()));
            valueField.getStyleClass().add("ml-mono");
            valueField.setPrefWidth(88);
            valueField.setAlignment(Pos.CENTER_RIGHT);
            valueField.setEditable(canEdit);

            HBox topRow = new HBox(6, nameLabel, unitLabel, sp, valueField);
            topRow.setAlignment(Pos.CENTER_LEFT);

            VBox itemBox = new VBox(6, topRow);
            itemBox.getStyleClass().add("ml-param-row");

            Double min = param.getMinValue();
            Double max = param.getMaxValue();
            if (min != null && max != null && max > min) {
                double clamped = Math.max(min, Math.min(max, param.getValue()));
                Slider slider = new Slider(min, max, clamped);
                slider.setDisable(!canEdit);

                boolean[] syncing = {false};
                slider.valueProperty().addListener((obs, oldV, newV) -> {
                    if (!syncing[0]) {
                        syncing[0] = true;
                        valueField.setText(String.format(Locale.US, "%.2f", newV.doubleValue()));
                        if (canEdit) setUnsavedVisible(true);
                        syncing[0] = false;
                    }
                });
                valueField.textProperty().addListener((obs, oldV, newV) -> {
                    if (!syncing[0] && newV != null) {
                        if (canEdit) setUnsavedVisible(true);
                        try {
                            double parsed = Double.parseDouble(newV.trim().replace(',', '.'));
                            if (parsed >= min && parsed <= max) {
                                syncing[0] = true;
                                slider.setValue(parsed);
                                syncing[0] = false;
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                });

                Label minLbl = new Label(String.valueOf(min));
                minLbl.getStyleClass().addAll("ml-text-muted", "ml-mono");
                Region rSp = new Region();
                HBox.setHgrow(rSp, Priority.ALWAYS);
                Label maxLbl = new Label(String.valueOf(max));
                maxLbl.getStyleClass().addAll("ml-text-muted", "ml-mono");

                itemBox.getChildren().addAll(slider, new HBox(minLbl, rSp, maxLbl));
            } else {
                if (canEdit) {
                    valueField.textProperty().addListener((obs, o, n) -> setUnsavedVisible(true));
                }
                String range = formatRange(min, max);
                if (!range.isEmpty()) {
                    Label rangeLabel = new Label(range);
                    rangeLabel.getStyleClass().addAll("ml-text-muted", "ml-mono");
                    itemBox.getChildren().add(rangeLabel);
                }
            }

            parameterInputs.put(param.getParameterDefinitionId(), valueField);
            listBox.getChildren().add(itemBox);
        }

        ScrollPane paramScroll = new ScrollPane(listBox);
        paramScroll.setFitToWidth(true);
        paramScroll.getStyleClass().add("ml-scroll-pane");
        VBox.setVgrow(paramScroll, Priority.ALWAYS);

        VBox wrapper = new VBox(8, paramScroll);
        if (canEdit) {
            Button saveBtn = new Button("Parametreleri Kaydet", UiHelper.icon(Feather.SAVE, 14));
            saveBtn.getStyleClass().add("ml-btn-secondary");
            saveBtn.setGraphicTextGap(6);
            saveBtn.setMaxWidth(Double.MAX_VALUE);
            saveBtn.setOnAction(e -> actions.onSaveParameters().run());
            wrapper.getChildren().addAll(unsavedLabel, saveBtn);
        }
        return wrapper;
    }

    private VBox buildRunsInspector() {
        Button compareBtn = new Button("Çalıştırmaları Karşılaştır", UiHelper.icon(Feather.GIT_MERGE, 14));
        compareBtn.getStyleClass().add("ml-btn-secondary");
        compareBtn.setGraphicTextGap(6);
        compareBtn.setMaxWidth(Double.MAX_VALUE);
        compareBtn.setOnAction(e -> actions.onCompareRuns().run());

        VBox.setVgrow(runListView, Priority.ALWAYS);
        VBox wrapper = new VBox(10, compareBtn, runListView);
        wrapper.setPadding(new Insets(10, 0, 0, 0));
        return wrapper;
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
        UiHelper.setFeedback(feedbackLabel, message, isError);
    }

    public void clearFeedback() {
        UiHelper.clearFeedback(feedbackLabel);
    }

    private String formatRange(Double min, Double max) {
        if (min != null && max != null) return "[" + min + " .. " + max + "]";
        if (min != null) return "[>= " + min + "]";
        if (max != null) return "[<= " + max + "]";
        return "";
    }
}