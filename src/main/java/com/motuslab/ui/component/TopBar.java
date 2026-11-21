package com.motuslab.ui.component;

import com.motuslab.model.User;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.io.InputStream;

public class TopBar extends HBox {

    private static final String LOGO_FULL = "/images/logo-full.png";

    public TopBar(User currentUser, Runnable onAdminClick, Runnable onLogout) {
        super(12);
        getStyleClass().add("ml-topbar");
        setAlignment(Pos.CENTER_LEFT);

        Node brandNode = createBrandNode();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label usernameLabel = new Label("@" + currentUser.getUsername());
        usernameLabel.getStyleClass().add("ml-text-bold");

        Label roleBadge = new Label(currentUser.getRoleName());
        roleBadge.getStyleClass().add(currentUser.isAdmin() ? "ml-badge-accent" : "ml-badge");

        HBox userBox = new HBox(8, usernameLabel, roleBadge);
        userBox.setAlignment(Pos.CENTER_LEFT);

        Button logoutButton = new Button("Çıkış Yap");
        logoutButton.getStyleClass().add("ml-btn-secondary");
        logoutButton.setOnAction(e -> onLogout.run());

        getChildren().addAll(brandNode, spacer);
        if (currentUser.isAdmin()) {
            Button adminButton = new Button("Admin Paneli");
            adminButton.getStyleClass().add("ml-btn-secondary");
            adminButton.setOnAction(e -> onAdminClick.run());
            getChildren().add(adminButton);
        }
        getChildren().addAll(userBox, logoutButton);
    }

    private Node createBrandNode() {
        try (InputStream is = getClass().getResourceAsStream(LOGO_FULL)) {
            if (is != null) {
                ImageView logoView = new ImageView(new Image(is));
                logoView.setPreserveRatio(true);
                logoView.setFitHeight(28);
                logoView.setSmooth(true);
                return logoView;
            }
        } catch (Exception ignored) {
            // Görsel yüklenemezse metin başlığa düşer.
        }
        Label fallback = new Label("MotusLab");
        fallback.getStyleClass().add("ml-brand-title");
        return fallback;
    }
}