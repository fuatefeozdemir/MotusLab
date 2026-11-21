package com.motuslab.ui;

import atlantafx.base.theme.Styles;
import com.motuslab.exception.MotusLabException;
import com.motuslab.model.User;
import com.motuslab.service.AdminService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.List;

public final class AdminPanelDialog {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private AdminPanelDialog() {
    }

    public static void show(User currentAdmin, AdminService adminService) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Admin Paneli");
        dialog.setHeaderText("Kullanıcı Yönetimi");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TableView<User> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(320);

        TableColumn<User, String> usernameCol = new TableColumn<>("Kullanıcı Adı");
        usernameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsername()));

        TableColumn<User, String> fullNameCol = new TableColumn<>("Ad Soyad");
        fullNameCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getFullName() != null ? c.getValue().getFullName() : "-"
        ));

        TableColumn<User, String> emailCol = new TableColumn<>("E-posta");
        emailCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEmail()));

        TableColumn<User, String> roleCol = new TableColumn<>("Rol");
        roleCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRoleName()));

        TableColumn<User, String> statusCol = new TableColumn<>("Durum");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().isActive() ? "Aktif" : "Pasif"
        ));

        TableColumn<User, String> lastLoginCol = new TableColumn<>("Son Giriş");
        lastLoginCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getLastLoginAt() != null ? c.getValue().getLastLoginAt().format(DATE_FMT) : "-"
        ));

        table.getColumns().addAll(usernameCol, fullNameCol, emailCol, roleCol, statusCol, lastLoginCol);

        ComboBox<String> roleBox = new ComboBox<>();
        roleBox.setPrefWidth(150);

        Button updateRoleBtn = new Button("Rolü Güncelle");
        Button toggleActiveBtn = new Button("Pasife Al");
        Label feedbackLabel = new Label();
        feedbackLabel.setWrapText(true);

        Runnable reloadUsers = () -> {
            User prevSelected = table.getSelectionModel().getSelectedItem();
            Integer prevId = prevSelected != null ? prevSelected.getId() : null;
            try {
                List<User> users = adminService.getAllUsers();
                table.setItems(FXCollections.observableArrayList(users));
                if (prevId != null) {
                    users.stream()
                            .filter(u -> u.getId() == prevId)
                            .findFirst()
                            .ifPresent(u -> table.getSelectionModel().select(u));
                }
            } catch (MotusLabException ex) {
                setFeedback(feedbackLabel, ex.getMessage(), true);
            }
        };

        try {
            roleBox.setItems(FXCollections.observableArrayList(adminService.getAllRoleNames()));
        } catch (MotusLabException ex) {
            setFeedback(feedbackLabel, ex.getMessage(), true);
        }

        Runnable syncControls = () -> {
            User selected = table.getSelectionModel().getSelectedItem();
            boolean none = selected == null;
            boolean isSelf = !none && selected.getId() == currentAdmin.getId();

            roleBox.setDisable(none || isSelf);
            updateRoleBtn.setDisable(none || isSelf);
            toggleActiveBtn.setDisable(none || isSelf);

            if (!none) {
                roleBox.getSelectionModel().select(selected.getRoleName());
                toggleActiveBtn.setText(selected.isActive() ? "Pasife Al" : "Aktif Et");
                toggleActiveBtn.getStyleClass().removeAll(Styles.DANGER, Styles.SUCCESS);
                toggleActiveBtn.getStyleClass().add(selected.isActive() ? Styles.DANGER : Styles.SUCCESS);
            }
        };

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> syncControls.run());

        updateRoleBtn.setOnAction(e -> {
            User selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            try {
                adminService.changeUserRole(selected.getId(), roleBox.getValue());
                setFeedback(feedbackLabel, selected.getUsername() + " kullanıcısının rolü güncellendi.", false);
                reloadUsers.run();
                syncControls.run();
            } catch (MotusLabException ex) {
                setFeedback(feedbackLabel, ex.getMessage(), true);
            }
        });

        toggleActiveBtn.setOnAction(e -> {
            User selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            boolean targetActive = !selected.isActive();
            try {
                adminService.setUserActive(selected.getId(), targetActive);
                setFeedback(feedbackLabel, selected.getUsername() + (targetActive ? " aktif edildi." : " pasife alındı."), false);
                reloadUsers.run();
                syncControls.run();
            } catch (MotusLabException ex) {
                setFeedback(feedbackLabel, ex.getMessage(), true);
            }
        });

        HBox controls = new HBox(10, new Label("Rol:"), roleBox, updateRoleBtn, toggleActiveBtn);
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(12, table, controls, feedbackLabel);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setPadding(new Insets(10));
        root.setPrefWidth(760);

        dialog.getDialogPane().setContent(root);

        reloadUsers.run();
        syncControls.run();
        dialog.showAndWait();
    }

    private static void setFeedback(Label label, String text, boolean isError) {
        label.setText(text);
        label.getStyleClass().removeAll(Styles.DANGER, Styles.SUCCESS);
        label.getStyleClass().add(isError ? Styles.DANGER : Styles.SUCCESS);
    }
}