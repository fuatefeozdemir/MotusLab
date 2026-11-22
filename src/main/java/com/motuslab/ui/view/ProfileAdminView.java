package com.motuslab.ui.view;

import com.motuslab.exception.MotusLabException;
import com.motuslab.model.User;
import com.motuslab.service.AdminService;
import com.motuslab.ui.UiHelper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class ProfileAdminView extends ScrollPane {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final User currentUser;
    private final AdminService adminService;

    private TableView<User> userTable;
    private ComboBox<String> roleBox;
    private Button updateRoleBtn;
    private Button toggleActiveBtn;
    private Label feedbackLabel;

    public ProfileAdminView(User currentUser, AdminService adminService) {
        this.currentUser = currentUser;
        this.adminService = adminService;

        setFitToWidth(true);
        getStyleClass().add("ml-scroll-pane");

        Label title = new Label(currentUser.isAdmin() ? "Profil ve Yönetim" : "Profilim");
        title.getStyleClass().add("ml-page-title");

        VBox profileCard = buildProfileCard();

        VBox content = new VBox(20, title, profileCard);
        content.getStyleClass().add("ml-detail-pane");

        if (currentUser.isAdmin()) {
            content.getChildren().add(buildAdminSection());
        }

        setContent(content);
    }

    public void refresh() {
        if (currentUser.isAdmin() && userTable != null) {
            reloadUsers();
            syncControls();
        }
    }

    private VBox buildProfileCard() {
        Label sectionTitle = new Label("Hesap Bilgileri");
        sectionTitle.getStyleClass().add("ml-section-title");

        GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.setVgap(10);

        addProfileRow(grid, 0, "Kullanıcı Adı", "@" + currentUser.getUsername());
        addProfileRow(grid, 1, "Ad Soyad", currentUser.getFullName() != null ? currentUser.getFullName() : "-");
        addProfileRow(grid, 2, "E-posta", currentUser.getEmail());
        addProfileRow(grid, 3, "Rol", currentUser.getRoleName());
        addProfileRow(grid, 4, "Son Giriş", currentUser.getLastLoginAt() != null
                ? currentUser.getLastLoginAt().format(DATE_FMT) : "-");

        VBox card = new VBox(12, sectionTitle, grid);
        card.getStyleClass().add("ml-card");
        return card;
    }

    private void addProfileRow(GridPane grid, int row, String key, String value) {
        Label k = new Label(key);
        k.getStyleClass().add("ml-text-secondary");
        Label v = new Label(value);
        v.getStyleClass().add("ml-text-bold");
        grid.addRow(row, k, v);
    }

    private VBox buildAdminSection() {
        Label sectionTitle = new Label("Kullanıcı Yönetimi");
        sectionTitle.getStyleClass().add("ml-section-title");

        userTable = new TableView<>();
        userTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        userTable.setPrefHeight(340);

        TableColumn<User, String> usernameCol = new TableColumn<>("Kullanıcı Adı");
        usernameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsername()));

        TableColumn<User, String> fullNameCol = new TableColumn<>("Ad Soyad");
        fullNameCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getFullName() != null ? c.getValue().getFullName() : "-"));

        TableColumn<User, String> emailCol = new TableColumn<>("E-posta");
        emailCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEmail()));

        TableColumn<User, String> roleCol = new TableColumn<>("Rol");
        roleCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRoleName()));

        TableColumn<User, String> statusCol = new TableColumn<>("Durum");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isActive() ? "Aktif" : "Pasif"));

        TableColumn<User, String> lastLoginCol = new TableColumn<>("Son Giriş");
        lastLoginCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getLastLoginAt() != null ? c.getValue().getLastLoginAt().format(DATE_FMT) : "-"));

        userTable.getColumns().addAll(usernameCol, fullNameCol, emailCol, roleCol, statusCol, lastLoginCol);

        roleBox = new ComboBox<>();
        roleBox.setPrefWidth(150);

        updateRoleBtn = new Button("Rolü Güncelle");
        updateRoleBtn.getStyleClass().add("ml-btn-secondary");

        toggleActiveBtn = new Button("Pasife Al");
        feedbackLabel = new Label();
        feedbackLabel.setWrapText(true);
        UiHelper.clearFeedback(feedbackLabel);

        try {
            roleBox.setItems(FXCollections.observableArrayList(adminService.getAllRoleNames()));
        } catch (MotusLabException ex) {
            UiHelper.setFeedback(feedbackLabel, ex.getMessage(), true);
        }

        userTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> syncControls());

        updateRoleBtn.setOnAction(e -> {
            User selected = userTable.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            try {
                adminService.changeUserRole(selected.getId(), roleBox.getValue());
                UiHelper.setFeedback(feedbackLabel, selected.getUsername() + " kullanıcısının rolü güncellendi.", false);
                reloadUsers();
                syncControls();
            } catch (MotusLabException ex) {
                UiHelper.setFeedback(feedbackLabel, ex.getMessage(), true);
            }
        });

        toggleActiveBtn.setOnAction(e -> {
            User selected = userTable.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            boolean targetActive = !selected.isActive();
            try {
                adminService.setUserActive(selected.getId(), targetActive);
                UiHelper.setFeedback(feedbackLabel, selected.getUsername() + (targetActive ? " aktif edildi." : " pasife alındı."), false);
                reloadUsers();
                syncControls();
            } catch (MotusLabException ex) {
                UiHelper.setFeedback(feedbackLabel, ex.getMessage(), true);
            }
        });

        HBox controls = new HBox(10, new Label("Rol:"), roleBox, updateRoleBtn, toggleActiveBtn);
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(12, sectionTitle, userTable, controls, feedbackLabel);
        card.getStyleClass().add("ml-card");
        return card;
    }

    private void reloadUsers() {
        User prev = userTable.getSelectionModel().getSelectedItem();
        Integer prevId = prev != null ? prev.getId() : null;
        try {
            List<User> users = adminService.getAllUsers();
            userTable.setItems(FXCollections.observableArrayList(users));
            if (prevId != null) {
                users.stream().filter(u -> u.getId() == prevId).findFirst()
                        .ifPresent(u -> userTable.getSelectionModel().select(u));
            }
        } catch (MotusLabException ex) {
            UiHelper.setFeedback(feedbackLabel, ex.getMessage(), true);
        }
    }

    private void syncControls() {
        User selected = userTable.getSelectionModel().getSelectedItem();
        boolean none = selected == null;
        boolean isSelf = !none && selected.getId() == currentUser.getId();

        roleBox.setDisable(none || isSelf);
        updateRoleBtn.setDisable(none || isSelf);
        toggleActiveBtn.setDisable(none || isSelf);

        if (!none) {
            roleBox.getSelectionModel().select(selected.getRoleName());
            toggleActiveBtn.setText(selected.isActive() ? "Pasife Al" : "Aktif Et");
            toggleActiveBtn.getStyleClass().removeAll("ml-btn-danger", "ml-btn-primary");
            toggleActiveBtn.getStyleClass().add(selected.isActive() ? "ml-btn-danger" : "ml-btn-primary");
        }
    }
}