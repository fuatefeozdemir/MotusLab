package com.motuslab.ui.view;

import com.motuslab.model.Experiment;
import com.motuslab.model.User;
import com.motuslab.ui.UiHelper;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class OverviewView extends ScrollPane {

    private static final Locale TR = Locale.of("tr", "TR");
    private static final DateTimeFormatter HEADER_DATE_FMT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", TR);
    private static final DateTimeFormatter SHORT_DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", TR);

    private final User currentUser;
    private final Consumer<Experiment> onOpenExperiment;

    private final HBox kpiRow = new HBox(14);
    private final VBox recentListBox = new VBox();

    public OverviewView(
            User currentUser,
            Runnable onNewExperiment,
            Runnable onOpenComparison,
            Consumer<Experiment> onOpenExperiment
    ) {
        this.currentUser = currentUser;
        this.onOpenExperiment = onOpenExperiment;

        setFitToWidth(true);
        getStyleClass().add("ml-scroll-pane");

        // --- Editoryal Karşılama Alanı ---
        String dateText = LocalDate.now().format(HEADER_DATE_FMT).toUpperCase(TR);
        Label kicker = new Label("MOTUSLAB / WORKSPACE  ·  " + dateText);
        kicker.getStyleClass().add("ml-editorial-kicker");

        Label title = new Label("Araştırmalarına devam et.");
        title.getStyleClass().add("ml-editorial-title");

        Label subtitle = new Label("Deneylerini yönet, fizik simülasyonlarını çalıştır ve sonuçları karşılaştır.");
        subtitle.getStyleClass().add("ml-text-secondary");

        VBox titleBlock = new VBox(6, kicker, title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button compareBtn = new Button("Karşılaştırma", UiHelper.icon(Feather.ARROW_RIGHT, 14));
        compareBtn.getStyleClass().add("ml-btn-secondary");
        compareBtn.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        compareBtn.setGraphicTextGap(6);
        compareBtn.setOnAction(e -> onOpenComparison.run());

        Button newExpBtn = new Button("Yeni Deney", UiHelper.icon(Feather.PLUS, 15, "ml-icon-light"));
        newExpBtn.getStyleClass().add("ml-btn-primary");
        newExpBtn.setGraphicTextGap(6);
        newExpBtn.setOnAction(e -> onNewExperiment.run());

        HBox actionRow = new HBox(10, compareBtn, newExpBtn);
        actionRow.setAlignment(Pos.CENTER_RIGHT);

        HBox heroBar = new HBox(16, titleBlock, spacer, actionRow);
        heroBar.setAlignment(Pos.BOTTOM_LEFT);

        // --- Son Deneyler Başlığı ---
        Label recentTitle = new Label("Son Deneyler");
        recentTitle.getStyleClass().add("ml-section-title");

        recentListBox.getStyleClass().add("ml-editorial-list");

        VBox content = new VBox(24, heroBar, kpiRow, new VBox(10, recentTitle, recentListBox));
        content.getStyleClass().add("ml-detail-pane");
        setContent(content);
    }

    public void setExperiments(List<Experiment> experiments) {
        long activeCount = experiments.stream().filter(e -> !e.isArchived()).count();
        long totalRuns = experiments.stream().mapToLong(Experiment::getRunCount).sum();
        long sharedCount = experiments.stream().filter(e -> e.getOwnerId() != currentUser.getId()).count();

        VBox primaryKpi = buildKpiCard("Toplam Deney", String.valueOf(experiments.size()), "Kütüphanedeki tüm deneyler", true);
        primaryKpi.setPrefWidth(260);
        HBox.setHgrow(primaryKpi, Priority.ALWAYS);

        VBox activeKpi = buildKpiCard("Aktif", String.valueOf(activeCount), "Çalıştırılabilir", false);
        VBox runsKpi = buildKpiCard("Simülasyon", String.valueOf(totalRuns), "Toplam çalıştırma", false);
        VBox sharedKpi = buildKpiCard("Paylaşılan", String.valueOf(sharedCount), "Ortak çalışma", false);

        HBox.setHgrow(activeKpi, Priority.SOMETIMES);
        HBox.setHgrow(runsKpi, Priority.SOMETIMES);
        HBox.setHgrow(sharedKpi, Priority.SOMETIMES);

        kpiRow.getChildren().setAll(primaryKpi, activeKpi, runsKpi, sharedKpi);

        recentListBox.getChildren().clear();
        List<Experiment> recent = experiments.stream().limit(7).toList();

        if (recent.isEmpty()) {
            Label empty = new Label("Henüz kayıtlı bir deney yok. Sağ üstten yeni bir deney oluşturabilirsiniz.");
            empty.getStyleClass().add("ml-text-muted");
            HBox emptyRow = new HBox(empty);
            emptyRow.setStyle("-fx-padding: 18;");
            recentListBox.getChildren().add(emptyRow);
            return;
        }

        for (Experiment exp : recent) {
            recentListBox.getChildren().add(buildEditorialRow(exp));
        }
    }

    private VBox buildKpiCard(String label, String value, String subtext, boolean primary) {
        Label lbl = new Label(label.toUpperCase(TR));
        lbl.getStyleClass().add("ml-card-meta-label");

        Label val = new Label(value);
        val.getStyleClass().add(primary ? "ml-kpi-number-lg" : "ml-kpi-number");

        Label sub = new Label(subtext);
        sub.getStyleClass().add("ml-text-muted");

        VBox card = new VBox(6, lbl, val, sub);
        card.getStyleClass().add(primary ? "ml-kpi-card-primary" : "ml-kpi-card");
        card.setMinWidth(150);
        return card;
    }

    private HBox buildEditorialRow(Experiment exp) {
        Label title = new Label(exp.getTitle());
        title.getStyleClass().add("ml-text-bold");
        title.setPrefWidth(260);

        Label simType = new Label(exp.getSimulationTypeName());
        simType.getStyleClass().add("ml-text-secondary");
        simType.setPrefWidth(190);

        String runText = exp.getLastRunNumber() > 0
                ? String.format(Locale.US, "RUN #%02d", exp.getLastRunNumber())
                : "RUN —";
        Label runLbl = new Label(runText);
        runLbl.getStyleClass().addAll("ml-mono", "ml-text-secondary");
        runLbl.setPrefWidth(90);

        String dateText = exp.getUpdatedAt() != null
                ? exp.getUpdatedAt().format(SHORT_DATE_FMT)
                : "-";
        Label dateLbl = new Label(dateText);
        dateLbl.getStyleClass().addAll("ml-mono", "ml-text-muted");
        dateLbl.setPrefWidth(110);

        Region dot = new Region();
        dot.getStyleClass().addAll("ml-status-dot", exp.isArchived() ? "ml-dot-created" : "ml-dot-completed");
        Label statusLbl = new Label(exp.isArchived() ? "Arşiv" : "Aktif");
        statusLbl.getStyleClass().add("ml-text-secondary");
        HBox statusBox = new HBox(6, dot, statusLbl);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        statusBox.setPrefWidth(80);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(16, title, simType, runLbl, dateLbl, statusBox, spacer, UiHelper.icon(Feather.ARROW_RIGHT, 14));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("ml-editorial-row");
        row.setOnMouseClicked(e -> onOpenExperiment.accept(exp));
        return row;
    }
}