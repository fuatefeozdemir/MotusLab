package com.motuslab.ui;

import com.motuslab.exception.MotusLabException;
import com.motuslab.model.Experiment;
import com.motuslab.model.ExperimentMember;
import com.motuslab.service.ExperimentMemberService;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

public final class ShareExperimentDialog {

    private ShareExperimentDialog() {
    }

    public static void show(Experiment experiment, ExperimentMemberService memberService) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Deneyi Paylaş");
        dialog.setHeaderText(experiment.getTitle() + " — Üye Yönetimi");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        UiHelper.styleDialog(dialog);

        Label feedback = new Label();
        feedback.setWrapText(true);
        UiHelper.clearFeedback(feedback);

        ListView<ExperimentMember> memberListView = new ListView<>();
        memberListView.setPrefHeight(200);

        Runnable refreshMembers = () -> {
            try {
                List<ExperimentMember> members = memberService.getMembers(experiment.getId());
                memberListView.setItems(FXCollections.observableArrayList(members));
            } catch (MotusLabException ex) {
                UiHelper.setFeedback(feedback, ex.getMessage(), true);
            }
        };

        memberListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ExperimentMember item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label userLabel = new Label("@" + item.getUsername());
                    userLabel.getStyleClass().add("ml-text-bold");

                    Label permBadge = new Label(item.getPermission());
                    permBadge.getStyleClass().add("ml-badge");

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    Button removeBtn = new Button("Çıkar");
                    removeBtn.getStyleClass().add("ml-btn-danger");
                    removeBtn.setOnAction(e -> {
                        try {
                            memberService.removeMember(experiment.getId(), item.getUserId());
                            UiHelper.setFeedback(feedback, "@" + item.getUsername() + " çıkarıldı.", false);
                            refreshMembers.run();
                        } catch (MotusLabException ex) {
                            UiHelper.setFeedback(feedback, ex.getMessage(), true);
                        }
                    });

                    HBox row = new HBox(10, userLabel, permBadge, spacer, removeBtn);
                    row.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(row);
                }
            }
        });

        TextField usernameField = new TextField();
        usernameField.setPromptText("Kullanıcı adı");
        HBox.setHgrow(usernameField, Priority.ALWAYS);

        ComboBox<String> permBox = new ComboBox<>(FXCollections.observableArrayList("VIEW", "RUN", "EDIT"));
        permBox.getSelectionModel().select("VIEW");

        Button addBtn = new Button("Ekle / Güncelle");
        addBtn.getStyleClass().add("ml-btn-primary");
        addBtn.setOnAction(e -> {
            try {
                memberService.addOrUpdateMember(
                        experiment.getId(),
                        usernameField.getText(),
                        permBox.getValue()
                );
                usernameField.clear();
                UiHelper.setFeedback(feedback, "Üye yetkisi kaydedildi.", false);
                refreshMembers.run();
            } catch (MotusLabException ex) {
                UiHelper.setFeedback(feedback, ex.getMessage(), true);
            }
        });

        HBox addRow = new HBox(8, usernameField, permBox, addBtn);
        addRow.setAlignment(Pos.CENTER_LEFT);

        Label addTitle = new Label("Yeni Üye Ekle veya Yetki Güncelle");
        addTitle.getStyleClass().add("ml-text-bold");
        Label listTitle = new Label("Mevcut Üyeler");
        listTitle.getStyleClass().add("ml-text-bold");

        VBox content = new VBox(12, addTitle, addRow, feedback, new Separator(), listTitle, memberListView);
        content.setPrefWidth(460);
        dialog.getDialogPane().setContent(content);

        refreshMembers.run();
        dialog.showAndWait();
    }
}