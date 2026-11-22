package com.motuslab.ui.view;

import com.motuslab.model.Experiment;
import com.motuslab.model.User;
import com.motuslab.ui.UiHelper;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class ExperimentLibraryView extends ScrollPane {

    private final User currentUser;
    private final boolean archivedMode;
    private final Consumer<Experiment> onOpenExperiment;

    private List<Experiment> allExperiments = List.of();

    private final TextField searchField = new TextField();
    private final ComboBox<String> ownershipFilterBox = new ComboBox<>();
    private final ComboBox<String> typeFilterBox = new ComboBox<>();
    private final FlowPane cardsPane = new FlowPane(14, 14);

    public ExperimentLibraryView(
            User currentUser,
            boolean archivedMode,
            Runnable onNewExperiment,
            Consumer<Experiment> onOpenExperiment
    ) {
        this.currentUser = currentUser;
        this.archivedMode = archivedMode;
        this.onOpenExperiment = onOpenExperiment;

        setFitToWidth(true);
        getStyleClass().add("ml-scroll-pane");

        Label title = new Label(archivedMode ? "Arşivlenen Deneyler" : "Deneylerim");
        title.getStyleClass().add("ml-page-title");

        Label subtitle = new Label(archivedMode
                ? "Salt okunur olarak saklanan tamamlanmış deneyler."
                : "Tüm aktif fizik deneylerinizi arayın, filtreleyin ve çalışma alanında açın.");
        subtitle.getStyleClass().add("ml-text-secondary");

        VBox titleBox = new VBox(4, title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox headerRow = new HBox(12, titleBox, spacer);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        if (!archivedMode && onNewExperiment != null) {
            Button newBtn = new Button("Yeni Deney", UiHelper.icon(Feather.PLUS, 15, "ml-icon-light"));
            newBtn.getStyleClass().add("ml-btn-primary");
            newBtn.setGraphicTextGap(6);
            newBtn.setOnAction(e -> onNewExperiment.run());
            headerRow.getChildren().add(newBtn);
        }

        searchField.setPromptText("Başlık, açıklama veya sahip ara...");
        searchField.setPrefWidth(280);
        searchField.textProperty().addListener((obs, o, n) -> renderCards());

        ownershipFilterBox.setItems(FXCollections.observableArrayList("Tümü", "Benim", "Paylaşılan"));
        ownershipFilterBox.getSelectionModel().selectFirst();
        ownershipFilterBox.valueProperty().addListener((obs, o, n) -> renderCards());

        typeFilterBox.setItems(FXCollections.observableArrayList("Tüm Türler"));
        typeFilterBox.getSelectionModel().selectFirst();
        typeFilterBox.valueProperty().addListener((obs, o, n) -> renderCards());

        HBox filterBar = new HBox(10, searchField, ownershipFilterBox, typeFilterBox);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(18, headerRow, filterBar, cardsPane);
        content.getStyleClass().add("ml-detail-pane");
        setContent(content);
    }

    public void setExperiments(List<Experiment> experiments) {
        this.allExperiments = experiments;
        refreshTypeFilter(experiments);
        renderCards();
    }

    private void refreshTypeFilter(List<Experiment> experiments) {
        String prev = typeFilterBox.getValue();
        List<String> types = new ArrayList<>();
        types.add("Tüm Türler");
        experiments.stream()
                .map(Experiment::getSimulationTypeName)
                .filter(n -> n != null && !n.isBlank())
                .distinct()
                .sorted()
                .forEach(types::add);
        typeFilterBox.setItems(FXCollections.observableArrayList(types));
        if (prev != null && types.contains(prev)) {
            typeFilterBox.getSelectionModel().select(prev);
        } else {
            typeFilterBox.getSelectionModel().selectFirst();
        }
    }

    private void renderCards() {
        cardsPane.getChildren().clear();
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String ownership = ownershipFilterBox.getValue();
        String simType = typeFilterBox.getValue();

        List<Experiment> filtered = allExperiments.stream()
                .filter(exp -> exp.isArchived() == archivedMode)
                .filter(exp -> {
                    if ("Benim".equals(ownership) && exp.getOwnerId() != currentUser.getId()) return false;
                    if ("Paylaşılan".equals(ownership) && exp.getOwnerId() == currentUser.getId()) return false;
                    if (simType != null && !"Tüm Türler".equals(simType) && !simType.equals(exp.getSimulationTypeName())) return false;
                    if (!query.isEmpty()) {
                        String t = exp.getTitle() != null ? exp.getTitle().toLowerCase(Locale.ROOT) : "";
                        String d = exp.getDescription() != null ? exp.getDescription().toLowerCase(Locale.ROOT) : "";
                        String o = exp.getOwnerUsername() != null ? exp.getOwnerUsername().toLowerCase(Locale.ROOT) : "";
                        return t.contains(query) || d.contains(query) || o.contains(query);
                    }
                    return true;
                })
                .toList();

        if (filtered.isEmpty()) {
            Label empty = new Label(archivedMode
                    ? "Arşivlenmiş deney bulunamadı."
                    : "Filtrelere uygun aktif deney bulunamadı.");
            empty.getStyleClass().add("ml-text-muted");
            cardsPane.getChildren().add(empty);
            return;
        }

        for (Experiment exp : filtered) {
            cardsPane.getChildren().add(buildExperimentCard(exp, currentUser, onOpenExperiment));
        }
    }

    public static VBox buildExperimentCard(Experiment exp, User currentUser, Consumer<Experiment> onOpen) {
        Feather typeIcon = "PROJECTILE_2D".equals(exp.getSimulationTypeCode())
                ? Feather.CROSSHAIR
                : Feather.ACTIVITY;

        Label typeBadge = new Label(exp.getSimulationTypeName());
        typeBadge.getStyleClass().add("ml-badge-accent");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label statusBadge = new Label(exp.isArchived() ? "Arşiv" : "Aktif");
        statusBadge.getStyleClass().add(exp.isArchived() ? "ml-badge-warning" : "ml-badge-success");

        HBox topRow = new HBox(8, UiHelper.icon(typeIcon, 16, "ml-icon-accent"), typeBadge, sp, statusBadge);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(exp.getTitle());
        title.getStyleClass().add("ml-section-title");
        title.setWrapText(true);

        String descText = (exp.getDescription() != null && !exp.getDescription().isBlank())
                ? exp.getDescription()
                : "Açıklama eklenmemiş.";
        Label desc = new Label(descText);
        desc.getStyleClass().add("ml-text-secondary");
        desc.setWrapText(true);
        desc.setMaxHeight(42);

        String ownerText = exp.getOwnerId() == currentUser.getId()
                ? "Sahip: Ben"
                : "Paylaşan: @" + exp.getOwnerUsername();
        Label ownerLbl = new Label(ownerText);
        ownerLbl.getStyleClass().add("ml-text-muted");

        Region fSp = new Region();
        HBox.setHgrow(fSp, Priority.ALWAYS);

        Label openHint = new Label("Aç →");
        openHint.getStyleClass().add("ml-text-bold");

        HBox footer = new HBox(8, ownerLbl, fSp, openHint);
        footer.setAlignment(Pos.CENTER_LEFT);

        Region vSp = new Region();
        VBox.setVgrow(vSp, Priority.ALWAYS);

        VBox card = new VBox(10, topRow, title, desc, vSp, footer);
        card.getStyleClass().add("ml-exp-card");
        card.setPrefSize(295, 168);
        card.setOnMouseClicked(e -> onOpen.accept(exp));
        return card;
    }
}