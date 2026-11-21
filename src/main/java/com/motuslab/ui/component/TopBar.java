package com.motuslab.ui.component;

import com.motuslab.model.User;
import com.motuslab.ui.UiHelper;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.feather.Feather;

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

        HBox userBox = new HBox(8, UiHelper.icon(Feather.USER, 15), usernameLabel, roleBadge);
        userBox.setAlignment(Pos.CENTER_LEFT);

        Button logoutButton = new Button("Çıkış Yap", UiHelper.icon(Feather.LOG_OUT, 14));
        logoutButton.getStyleClass().add("ml-btn-secondary");
        logoutButton.setGraphicTextGap(6);
        logoutButton.setOnAction(e -> onLogout.run());

        getChildren().addAll(brandNode, spacer);
        if (currentUser.isAdmin()) {
            Button adminButton = new Button("Admin Paneli", UiHelper.icon(Feather.SHIELD, 14, "ml-icon-accent"));
            adminButton.getStyleClass().add("ml-btn-secondary");
            adminButton.setGraphicTextGap(6);
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
        }
        Label fallback = new Label("MotusLab");
        fallback.getStyleClass().add("ml-brand-title");
        return fallback;
    }
}