package com.motuslab.ui.component;

import com.motuslab.ui.UiHelper;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.kordamp.ikonli.feather.Feather;

import java.io.InputStream;
import java.util.List;

public class WindowTitleBar extends HBox {

    private static final String APP_ICON = "/images/logo-icon.png";
    private static final String PROP_MAXIMIZED = "ml.maximized";
    private static final String PROP_RESTORE_BOUNDS = "ml.restoreBounds";

    private double dragOffsetX;
    private double dragOffsetY;

    public WindowTitleBar(Stage stage, String titleText) {
        super(8);
        getStyleClass().add("ml-titlebar");
        setAlignment(Pos.CENTER_LEFT);

        try (InputStream is = getClass().getResourceAsStream(APP_ICON)) {
            if (is != null) {
                ImageView iconView = new ImageView(new Image(is));
                iconView.setFitWidth(16);
                iconView.setFitHeight(16);
                iconView.setPreserveRatio(true);
                iconView.setSmooth(true);
                getChildren().add(iconView);
            }
        } catch (Exception ignored) {
        }

        Label titleLabel = new Label(titleText);
        titleLabel.getStyleClass().add("ml-titlebar-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button minBtn = new Button("", UiHelper.icon(Feather.MINUS, 14));
        minBtn.getStyleClass().add("ml-win-btn");
        minBtn.setFocusTraversable(false);
        minBtn.setOnAction(e -> stage.setIconified(true));

        Button maxBtn = new Button("", UiHelper.icon(isCustomMaximized(stage) ? Feather.COPY : Feather.SQUARE, 12));
        maxBtn.getStyleClass().add("ml-win-btn");
        maxBtn.setFocusTraversable(false);
        maxBtn.setOnAction(e -> {
            toggleMaximize(stage);
            updateMaxIcon(stage, maxBtn);
        });

        Button closeBtn = new Button("", UiHelper.icon(Feather.X, 14));
        closeBtn.getStyleClass().addAll("ml-win-btn", "ml-win-btn-close");
        closeBtn.setFocusTraversable(false);
        closeBtn.setOnAction(e -> stage.close());

        getChildren().addAll(titleLabel, spacer, minBtn, maxBtn, closeBtn);

        setOnMousePressed(e -> {
            dragOffsetX = e.getSceneX();
            dragOffsetY = e.getSceneY();
        });

        setOnMouseDragged(e -> {
            if (isCustomMaximized(stage)) {
                double ratio = e.getSceneX() / Math.max(1.0, stage.getWidth());
                restoreBounds(stage);
                updateMaxIcon(stage, maxBtn);
                dragOffsetX = stage.getWidth() * ratio;
            }
            stage.setX(e.getScreenX() - dragOffsetX);
            stage.setY(e.getScreenY() - dragOffsetY);
        });

        setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                toggleMaximize(stage);
                updateMaxIcon(stage, maxBtn);
            }
        });
    }

    public static void maximizeToVisualBounds(Stage stage) {
        if (!isCustomMaximized(stage) && stage.getWidth() > 0 && stage.getHeight() > 0) {
            stage.getProperties().put(PROP_RESTORE_BOUNDS,
                    new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight()));
        }

        Rectangle2D vb = getCurrentVisualBounds(stage);
        stage.setX(vb.getMinX());
        stage.setY(vb.getMinY());
        stage.setWidth(vb.getWidth());
        stage.setHeight(vb.getHeight());
        stage.getProperties().put(PROP_MAXIMIZED, true);
    }

    public static void restoreBounds(Stage stage) {
        Rectangle2D bounds = (Rectangle2D) stage.getProperties().get(PROP_RESTORE_BOUNDS);
        stage.getProperties().put(PROP_MAXIMIZED, false);

        if (bounds != null) {
            stage.setX(bounds.getMinX());
            stage.setY(bounds.getMinY());
            stage.setWidth(bounds.getWidth());
            stage.setHeight(bounds.getHeight());
        } else {
            stage.setWidth(Math.max(1280, stage.getMinWidth()));
            stage.setHeight(Math.max(800, stage.getMinHeight()));
            stage.centerOnScreen();
        }
    }

    public static void toggleMaximize(Stage stage) {
        if (isCustomMaximized(stage)) {
            restoreBounds(stage);
        } else {
            maximizeToVisualBounds(stage);
        }
    }

    public static void resetMaximizeState(Stage stage) {
        stage.getProperties().put(PROP_MAXIMIZED, false);
        stage.getProperties().remove(PROP_RESTORE_BOUNDS);
    }

    public static boolean isCustomMaximized(Stage stage) {
        return Boolean.TRUE.equals(stage.getProperties().get(PROP_MAXIMIZED));
    }

    private static void updateMaxIcon(Stage stage, Button maxBtn) {
        maxBtn.setGraphic(UiHelper.icon(isCustomMaximized(stage) ? Feather.COPY : Feather.SQUARE, 12));
    }

    private static Rectangle2D getCurrentVisualBounds(Stage stage) {
        List<Screen> screens = Screen.getScreensForRectangle(
                stage.getX(), stage.getY(),
                Math.max(1.0, stage.getWidth()),
                Math.max(1.0, stage.getHeight())
        );
        Screen screen = screens.isEmpty() ? Screen.getPrimary() : screens.getFirst();
        return screen.getVisualBounds();
    }
}