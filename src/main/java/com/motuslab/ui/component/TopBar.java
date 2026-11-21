package com.motuslab.ui.component;

import com.motuslab.model.User;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class TopBar extends HBox {

    public TopBar(User currentUser, Runnable onAdminClick, Runnable onLogout) {
        super(12);
        getStyleClass().add("ml-topbar");
        setAlignment(Pos.CENTER_LEFT);

        Label brandLabel = new Label("MotusLab");
        brandLabel.getStyleClass().add("ml-brand");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label userLabel = new Label(currentUser.getUsername() + " (" + currentUser.getRoleName() + ")");
        userLabel.getStyleClass().add("ml-muted");

        Button logoutButton = new Button("Çıkış Yap");
        logoutButton.setOnAction(e -> onLogout.run());

        getChildren().addAll(brandLabel, spacer);
        if (currentUser.isAdmin()) {
            Button adminButton = new Button("Admin Paneli");
            adminButton.setOnAction(e -> onAdminClick.run());
            getChildren().add(adminButton);
        }
        getChildren().addAll(userLabel, logoutButton);
    }
}