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

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class ExperimentLibraryView extends ScrollPane {

    private static final Locale TR = Locale.of("tr", "TR");
    private static final DateTimeFormatter CARD_DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", TR);

    private final User currentUser;
    private final boolean archivedMode;
    private final Consumer<Experiment> onOpenExperiment;

    private List<Experiment> allExperiments = List.of();

    private final Label countLabel = new Label("0 deney");
    private final TextField searchField = new TextField();
    private final ComboBox<String> ownershipFilterBox = new ComboBox<>();
    private final ComboBox<String> typeFilterBox = new ComboBox<>();
    private final FlowPane cardsPane = new FlowPane(16, 16);

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

        // --- Tek Satırlık Kompakt Toolbar ---
        Label title = new Label(archivedMode ? "Arşiv" : "Deneyler");
        title.getStyleClass().add("ml-page-title");

        countLabel.getStyleClass().addAll("ml-mono", "ml-text-muted");

        HBox titleGroup = new HBox(10, title, countLabel);
        titleGroup.setAlignment(Pos.BASELINE_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        searchField.setPromptText("Ara...");
        searchField.setPrefWidth(210);
        searchField.textProperty().addListener((obs, o, n) -> renderCards());

        ownershipFilterBox.setItems(FXCollections.observableArrayList("Tümü", "Benim", "Paylaşılan"));
        ownershipFilterBox.getSelectionModel().selectFirst();
        ownershipFilterBox.valueProperty().addListener((obs, o, n) -> renderCards());

        typeFilterBox.setItems(FXCollections.observableArrayList("Tüm Türler"));
        typeFilterBox.getSelectionModel().selectFirst();
        typeFilterBox.valueProperty().addListener((obs, o, n) -> renderCards());

        HBox toolbar = new HBox(10, titleGroup, spacer, searchField, ownershipFilterBox, typeFilterBox);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        if (!archivedMode && onNewExperiment != null) {
            Button newBtn = new Button("Yeni Deney", UiHelper.icon(Feather.PLUS, 14, "ml-icon-light"));
            newBtn.getStyleClass().add("ml-btn-primary");
            newBtn.setGraphicTextGap(6);
            newBtn.setOnAction(e -> onNewExperiment.run());
            toolbar.getChildren().add(newBtn);
        }

        VBox content = new VBox(20, toolbar, cardsPane);
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

        countLabel.setText(filtered.size() + " deney");

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
        Label typeLbl = new Label(exp.getSimulationTypeName().toUpperCase(TR));
        typeLbl.getStyleClass().add("ml-card-meta-label");

        Region topSp = new Region();
        HBox.setHgrow(topSp, Priority.ALWAYS);

        String runBadgeText = exp.getLastRunNumber() > 0
                ? String.format(Locale.US, "RUN #%02d", exp.getLastRunNumber())
                : "YENİ";
        Label runLbl = new Label(runBadgeText);
        runLbl.getStyleClass().addAll("ml-mono", "ml-text-muted");

        HBox topRow = new HBox(8, typeLbl, topSp, runLbl);
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
        desc.setMaxHeight(40);

        Region vSp = new Region();
        VBox.setVgrow(vSp, Priority.ALWAYS);

        Region divider = new Region();
        divider.getStyleClass().add("ml-card-divider");

        String dateVal = exp.getUpdatedAt() != null ? exp.getUpdatedAt().format(CARD_DATE_FMT) : "-";
        VBox dateCol = new VBox(2, metaLabel("SON GÜNCELLEME"), monoValue(dateVal));

        String countVal = String.format(Locale.US, "%02d", exp.getRunCount());
        VBox runsCol = new VBox(2, metaLabel("ÇALIŞTIRMA"), monoValue(countVal));

        Region fSp = new Region();
        HBox.setHgrow(fSp, Priority.ALWAYS);

        HBox footer = new HBox(18, dateCol, runsCol, fSp, UiHelper.icon(Feather.ARROW_RIGHT, 14));
        footer.setAlignment(Pos.BOTTOM_LEFT);

        VBox card = new VBox(10, topRow, title, desc, vSp, divider, footer);
        card.getStyleClass().add("ml-exp-card");
        card.setPrefSize(305, 184);
        card.setOnMouseClicked(e -> onOpen.accept(exp));
        return card;
    }

    private static Label metaLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("ml-card-meta-label");
        return l;
    }

    private static Label monoValue(String text) {
        Label l = new Label(text);
        l.getStyleClass().addAll("ml-mono", "ml-text-bold");
        return l;
    }
}