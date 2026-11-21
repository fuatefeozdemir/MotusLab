package com.motuslab.ui.component;

import com.motuslab.model.Experiment;
import com.motuslab.model.User;
import com.motuslab.ui.UiHelper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class ExperimentSidebar extends VBox {

    private final User currentUser;
    private final ObservableList<Experiment> allExperiments = FXCollections.observableArrayList();
    private final FilteredList<Experiment> filteredExperiments = new FilteredList<>(allExperiments, e -> true);

    private final TextField searchField = new TextField();
    private final ComboBox<String> ownershipFilterBox = new ComboBox<>();
    private final ComboBox<String> typeFilterBox = new ComboBox<>();
    private final CheckBox showArchivedCheckBox = new CheckBox("Arşivlenenleri göster");
    private final ListView<Experiment> experimentListView = new ListView<>();

    public ExperimentSidebar(
            User currentUser,
            Runnable onNewExperiment,
            Consumer<Experiment> onSelectExperiment
    ) {
        super(10);
        this.currentUser = currentUser;

        getStyleClass().add("ml-sidebar");
        setPrefWidth(275);

        Label headerLabel = new Label("Deneylerim");
        headerLabel.getStyleClass().add("ml-section-title");
        HBox headerRow = new HBox(8, UiHelper.icon(Feather.LAYERS, 15, "ml-icon-accent"), headerLabel);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Button newExpButton = new Button("Yeni Deney", UiHelper.icon(Feather.PLUS, 15, "ml-icon-light"));
        newExpButton.getStyleClass().add("ml-btn-primary");
        newExpButton.setGraphicTextGap(6);
        newExpButton.setMaxWidth(Double.MAX_VALUE);
        newExpButton.setOnAction(e -> onNewExperiment.run());

        searchField.setPromptText("Deney ara...");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        ownershipFilterBox.setItems(FXCollections.observableArrayList("Tümü", "Benim", "Paylaşılan"));
        ownershipFilterBox.getSelectionModel().selectFirst();
        ownershipFilterBox.setMaxWidth(Double.MAX_VALUE);
        ownershipFilterBox.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        typeFilterBox.setItems(FXCollections.observableArrayList("Tüm Türler"));
        typeFilterBox.getSelectionModel().selectFirst();
        typeFilterBox.setMaxWidth(Double.MAX_VALUE);
        typeFilterBox.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        HBox filterRow = new HBox(8, ownershipFilterBox, typeFilterBox);
        HBox.setHgrow(ownershipFilterBox, Priority.ALWAYS);
        HBox.setHgrow(typeFilterBox, Priority.ALWAYS);

        showArchivedCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        experimentListView.setItems(filteredExperiments);
        experimentListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Experiment item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Feather typeIcon = "PROJECTILE_2D".equals(item.getSimulationTypeCode())
                            ? Feather.CROSSHAIR
                            : Feather.ACTIVITY;

                    Label title = new Label(item.getTitle());
                    title.getStyleClass().add("ml-text-bold");

                    HBox titleRow = new HBox(6, title);
                    titleRow.setAlignment(Pos.CENTER_LEFT);
                    if (item.isArchived()) {
                        Label archiveBadge = new Label("Arşiv");
                        archiveBadge.getStyleClass().add("ml-badge-warning");
                        titleRow.getChildren().add(archiveBadge);
                    }

                    String subtitle = item.getSimulationTypeName();
                    if (item.getOwnerId() != currentUser.getId()) {
                        subtitle += " • @" + item.getOwnerUsername();
                    }
                    Label type = new Label(subtitle);
                    type.getStyleClass().add("ml-text-muted");

                    VBox textCol = new VBox(2, titleRow, type);
                    HBox row = new HBox(10, UiHelper.icon(typeIcon, 16, "ml-icon-accent"), textCol);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setPadding(new Insets(3, 2, 3, 2));
                    setGraphic(row);
                }
            }
        });

        experimentListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) ->
                onSelectExperiment.accept(newVal)
        );

        VBox.setVgrow(experimentListView, Priority.ALWAYS);
        getChildren().addAll(headerRow, newExpButton, searchField, filterRow, showArchivedCheckBox, experimentListView);
    }

    public void setExperiments(List<Experiment> experiments, Integer selectId) {
        allExperiments.setAll(experiments);
        refreshTypeFilterOptions(experiments);
        applyFilters();

        if (selectId != null) {
            filteredExperiments.stream()
                    .filter(exp -> exp.getId() == selectId)
                    .findFirst()
                    .ifPresent(exp -> experimentListView.getSelectionModel().select(exp));
        } else if (!filteredExperiments.isEmpty()) {
            experimentListView.getSelectionModel().selectFirst();
        } else {
            experimentListView.getSelectionModel().clearSelection();
        }
    }

    public boolean isShowArchivedSelected() {
        return showArchivedCheckBox.isSelected();
    }

    private void refreshTypeFilterOptions(List<Experiment> experiments) {
        String previous = typeFilterBox.getValue();
        List<String> types = new ArrayList<>();
        types.add("Tüm Türler");
        experiments.stream()
                .map(Experiment::getSimulationTypeName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted()
                .forEach(types::add);

        typeFilterBox.setItems(FXCollections.observableArrayList(types));
        if (previous != null && types.contains(previous)) {
            typeFilterBox.getSelectionModel().select(previous);
        } else {
            typeFilterBox.getSelectionModel().selectFirst();
        }
    }

    private void applyFilters() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String ownership = ownershipFilterBox.getValue();
        String simType = typeFilterBox.getValue();

        filteredExperiments.setPredicate(exp -> {
            if (!showArchivedCheckBox.isSelected() && exp.isArchived()) {
                return false;
            }
            if ("Benim".equals(ownership) && exp.getOwnerId() != currentUser.getId()) {
                return false;
            }
            if ("Paylaşılan".equals(ownership) && exp.getOwnerId() == currentUser.getId()) {
                return false;
            }
            if (simType != null && !"Tüm Türler".equals(simType) && !simType.equals(exp.getSimulationTypeName())) {
                return false;
            }
            if (!query.isEmpty()) {
                String title = exp.getTitle() != null ? exp.getTitle().toLowerCase(Locale.ROOT) : "";
                String desc = exp.getDescription() != null ? exp.getDescription().toLowerCase(Locale.ROOT) : "";
                String owner = exp.getOwnerUsername() != null ? exp.getOwnerUsername().toLowerCase(Locale.ROOT) : "";
                return title.contains(query) || desc.contains(query) || owner.contains(query);
            }
            return true;
        });
    }
}