package com.motuslab.ui.view;

import com.motuslab.model.Experiment;
import com.motuslab.model.User;
import com.motuslab.ui.UiHelper;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

import java.util.List;
import java.util.function.Consumer;

public class OverviewView extends ScrollPane {

    private final User currentUser;
    private final Runnable onNewExperiment;
    private final Runnable onOpenComparison;
    private final Consumer<Experiment> onOpenExperiment;

    private final HBox kpiRow = new HBox(14);
    private final FlowPane recentCardsPane = new FlowPane(14, 14);

    public OverviewView(
            User currentUser,
            Runnable onNewExperiment,
            Runnable onOpenComparison,
            Consumer<Experiment> onOpenExperiment
    ) {
        this.currentUser = currentUser;
        this.onNewExperiment = onNewExperiment;
        this.onOpenComparison = onOpenComparison;
        this.onOpenExperiment = onOpenExperiment;

        setFitToWidth(true);
        getStyleClass().add("ml-scroll-pane");

        String displayName = (currentUser.getFullName() != null && !currentUser.getFullName().isBlank())
                ? currentUser.getFullName()
                : "@" + currentUser.getUsername();

        Label welcomeTitle = new Label("Hoş geldin, " + displayName);
        welcomeTitle.getStyleClass().add("ml-page-title");

        Label welcomeSub = new Label("Deneylerini yönet, fizik simülasyonlarını çalıştır ve sonuçları karşılaştır.");
        welcomeSub.getStyleClass().add("ml-text-secondary");

        VBox titleBox = new VBox(4, welcomeTitle, welcomeSub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button newExpBtn = new Button("Yeni Deney", UiHelper.icon(Feather.PLUS, 15, "ml-icon-light"));
        newExpBtn.getStyleClass().add("ml-btn-primary");
        newExpBtn.setGraphicTextGap(6);
        newExpBtn.setOnAction(e -> onNewExperiment.run());

        Button compareBtn = new Button("Karşılaştır", UiHelper.icon(Feather.GIT_MERGE, 14));
        compareBtn.getStyleClass().add("ml-btn-secondary");
        compareBtn.setGraphicTextGap(6);
        compareBtn.setOnAction(e -> onOpenComparison.run());

        HBox headerBar = new HBox(12, titleBox, spacer, compareBtn, newExpBtn);
        headerBar.setAlignment(Pos.CENTER_LEFT);

        Label recentTitle = new Label("Hızlı Erişim — Aktif Deneyler");
        recentTitle.getStyleClass().add("ml-section-title");

        VBox content = new VBox(22, headerBar, kpiRow, recentTitle, recentCardsPane);
        content.getStyleClass().add("ml-detail-pane");
        setContent(content);
    }

    public void setExperiments(List<Experiment> experiments) {
        long activeCount = experiments.stream().filter(e -> !e.isArchived()).count();
        long ownCount = experiments.stream().filter(e -> e.getOwnerId() == currentUser.getId()).count();
        long sharedCount = experiments.size() - ownCount;
        long archivedCount = experiments.size() - activeCount;

        kpiRow.getChildren().setAll(
                buildKpiCard("Toplam Deney", String.valueOf(experiments.size()), Feather.LAYERS),
                buildKpiCard("Aktif Deney", String.valueOf(activeCount), Feather.ACTIVITY),
                buildKpiCard("Paylaşılan", String.valueOf(sharedCount), Feather.USERS),
                buildKpiCard("Arşivlenen", String.valueOf(archivedCount), Feather.ARCHIVE)
        );

        recentCardsPane.getChildren().clear();
        List<Experiment> recent = experiments.stream()
                .filter(e -> !e.isArchived())
                .limit(6)
                .toList();

        if (recent.isEmpty()) {
            Label empty = new Label("Henüz aktif bir deney yok. Sağ üstten yeni bir deney oluşturabilirsiniz.");
            empty.getStyleClass().add("ml-text-muted");
            recentCardsPane.getChildren().add(empty);
            return;
        }

        for (Experiment exp : recent) {
            recentCardsPane.getChildren().add(ExperimentLibraryView.buildExperimentCard(exp, currentUser, onOpenExperiment));
        }
    }

    private VBox buildKpiCard(String label, String value, Feather icon) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("ml-text-secondary");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        HBox top = new HBox(8, lbl, sp, UiHelper.icon(icon, 16, "ml-icon-accent"));
        top.setAlignment(Pos.CENTER_LEFT);

        Label val = new Label(value);
        val.getStyleClass().add("ml-kpi-number");

        VBox card = new VBox(8, top, val);
        card.getStyleClass().add("ml-kpi-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }
}