package com.motuslab.ui.component;

import com.motuslab.model.User;
import com.motuslab.ui.UiHelper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

public class NavSidebar extends VBox {

    public enum Section {
        OVERVIEW,
        EXPERIMENTS,
        COMPARISON,
        ARCHIVE,
        MANAGEMENT
    }

    private static final String LOGO_FULL = "/images/logo-full.png";

    private final Map<Section, Button> navButtons = new EnumMap<>(Section.class);
    private final Map<Section, FontIcon> navIcons = new EnumMap<>(Section.class);

    public NavSidebar(User currentUser, Consumer<Section> onNavigate, Runnable onLogout) {
        super(4);
        getStyleClass().add("ml-nav-sidebar");
        setPrefWidth(226);

        VBox brandBox = new VBox(createBrandNode());
        brandBox.setPadding(new Insets(4, 8, 16, 10));

        Label workspaceLabel = new Label("ÇALIŞMA ALANI");
        workspaceLabel.getStyleClass().add("ml-nav-section-label");

        addNavButton(Section.OVERVIEW, "Genel Bakış", Feather.HOME, onNavigate);
        addNavButton(Section.EXPERIMENTS, "Deneyler", Feather.LAYERS, onNavigate);
        addNavButton(Section.COMPARISON, "Karşılaştırma", Feather.GIT_MERGE, onNavigate);
        addNavButton(Section.ARCHIVE, "Arşiv", Feather.ARCHIVE, onNavigate);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Region divider = new Region();
        divider.getStyleClass().add("ml-nav-divider");
        VBox.setMargin(divider, new Insets(8, 4, 8, 4));

        String managementLabel = currentUser.isAdmin() ? "Yönetim" : "Profil";
        Feather managementIcon = currentUser.isAdmin() ? Feather.SHIELD : Feather.USER;
        addNavButton(Section.MANAGEMENT, managementLabel, managementIcon, onNavigate);

        HBox userBox = buildUserFooter(currentUser, onLogout);
        VBox.setMargin(userBox, new Insets(8, 0, 0, 0));

        getChildren().addAll(
                brandBox,
                workspaceLabel,
                navButtons.get(Section.OVERVIEW),
                navButtons.get(Section.EXPERIMENTS),
                navButtons.get(Section.COMPARISON),
                navButtons.get(Section.ARCHIVE),
                spacer,
                divider,
                navButtons.get(Section.MANAGEMENT),
                userBox
        );
    }

    public void setActiveSection(Section active) {
        for (Map.Entry<Section, Button> entry : navButtons.entrySet()) {
            boolean isActive = entry.getKey() == active;
            Button btn = entry.getValue();
            FontIcon icon = navIcons.get(entry.getKey());

            btn.getStyleClass().remove("ml-nav-item-active");
            icon.getStyleClass().remove("ml-icon-accent");
            if (isActive) {
                btn.getStyleClass().add("ml-nav-item-active");
                icon.getStyleClass().add("ml-icon-accent");
            }
        }
    }

    private void addNavButton(Section section, String text, Feather feather, Consumer<Section> onNavigate) {
        FontIcon icon = UiHelper.icon(feather, 15);
        Button btn = new Button(text, icon);
        btn.getStyleClass().add("ml-nav-item");
        btn.setGraphicTextGap(10);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> onNavigate.accept(section));

        navButtons.put(section, btn);
        navIcons.put(section, icon);
    }

    private HBox buildUserFooter(User user, Runnable onLogout) {
        String displayName = (user.getFullName() != null && !user.getFullName().isBlank())
                ? user.getFullName()
                : "@" + user.getUsername();

        Label nameLbl = new Label(displayName);
        nameLbl.getStyleClass().add("ml-text-bold");
        nameLbl.setMaxWidth(135);

        Label roleLbl = new Label("@" + user.getUsername() + " · " + user.getRoleName());
        roleLbl.getStyleClass().addAll("ml-text-muted", "ml-mono");
        roleLbl.setMaxWidth(135);

        VBox info = new VBox(2, nameLbl, roleLbl);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button logoutBtn = new Button("", UiHelper.icon(Feather.LOG_OUT, 14));
        logoutBtn.getStyleClass().add("ml-icon-btn");
        logoutBtn.setTooltip(new Tooltip("Çıkış Yap"));
        logoutBtn.setOnAction(e -> onLogout.run());

        HBox box = new HBox(8, info, logoutBtn);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("ml-nav-user-box");
        return box;
    }

    private Node createBrandNode() {
        try (InputStream is = getClass().getResourceAsStream(LOGO_FULL)) {
            if (is != null) {
                ImageView logoView = new ImageView(new Image(is));
                logoView.setPreserveRatio(true);
                logoView.setFitHeight(32);
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