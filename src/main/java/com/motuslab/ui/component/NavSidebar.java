package com.motuslab.ui.component;

import com.motuslab.model.User;
import com.motuslab.ui.UiHelper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
        super(6);
        getStyleClass().add("ml-nav-sidebar");
        setPrefWidth(224);

        Node brandNode = createBrandNode();
        VBox brandBox = new VBox(brandNode);
        brandBox.setPadding(new Insets(4, 8, 18, 8));

        String managementLabel = currentUser.isAdmin() ? "Yönetim" : "Profil";
        Feather managementIcon = currentUser.isAdmin() ? Feather.SHIELD : Feather.USER;

        addNavButton(Section.OVERVIEW, "Genel Bakış", Feather.HOME, onNavigate);
        addNavButton(Section.EXPERIMENTS, "Deneylerim", Feather.LAYERS, onNavigate);
        addNavButton(Section.COMPARISON, "Karşılaştırma", Feather.GIT_MERGE, onNavigate);
        addNavButton(Section.ARCHIVE, "Arşiv", Feather.ARCHIVE, onNavigate);
        addNavButton(Section.MANAGEMENT, managementLabel, managementIcon, onNavigate);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox userBox = buildUserFooter(currentUser, onLogout);

        getChildren().add(brandBox);
        getChildren().addAll(
                navButtons.get(Section.OVERVIEW),
                navButtons.get(Section.EXPERIMENTS),
                navButtons.get(Section.COMPARISON),
                navButtons.get(Section.ARCHIVE),
                navButtons.get(Section.MANAGEMENT),
                spacer,
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
        FontIcon icon = UiHelper.icon(feather, 16);
        Button btn = new Button(text, icon);
        btn.getStyleClass().add("ml-nav-item");
        btn.setGraphicTextGap(10);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> onNavigate.accept(section));

        navButtons.put(section, btn);
        navIcons.put(section, icon);
    }

    private VBox buildUserFooter(User user, Runnable onLogout) {
        Label usernameLbl = new Label("@" + user.getUsername());
        usernameLbl.getStyleClass().add("ml-text-bold");

        Label roleBadge = new Label(user.getRoleName());
        roleBadge.getStyleClass().add(user.isAdmin() ? "ml-badge-accent" : "ml-badge");

        HBox userRow = new HBox(8, usernameLbl, roleBadge);
        userRow.setAlignment(Pos.CENTER_LEFT);

        Button logoutBtn = new Button("Çıkış Yap", UiHelper.icon(Feather.LOG_OUT, 14));
        logoutBtn.getStyleClass().add("ml-btn-secondary");
        logoutBtn.setGraphicTextGap(6);
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setOnAction(e -> onLogout.run());

        VBox box = new VBox(10, userRow, logoutBtn);
        box.getStyleClass().add("ml-nav-user-box");
        return box;
    }

    private Node createBrandNode() {
        try (InputStream is = getClass().getResourceAsStream(LOGO_FULL)) {
            if (is != null) {
                ImageView logoView = new ImageView(new Image(is));
                logoView.setPreserveRatio(true);
                logoView.setFitHeight(30);
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