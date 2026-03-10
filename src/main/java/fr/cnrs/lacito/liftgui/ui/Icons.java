package fr.cnrs.lacito.liftgui.ui;

import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;

/**
 * Icônes pour les boutons Undo/Redo.
 * Utilise undo.png/redo.png depuis fr/cnrs/lacito/liftgui/ui/icons/ si présents,
 * sinon dessine des icônes vectorielles.
 */
public final class Icons {
    private static final String ICON_PATH = "/fr/cnrs/lacito/liftgui/ui/icons/";
    private static final double S = 16;
    private static final Color STROKE = Color.WHITE;  // visible sur fond menu sombre

    private Icons() {}

    /** Icône Annuler (Ctrl+Z). */
    public static Node undoIcon() {
        Node fromRes = loadPng(ICON_PATH + "undo.png");
        if (fromRes != null) return fromRes;
        Path p = new Path();
        p.getElements().addAll(
            new MoveTo(S * 0.75, S * 0.2),
            new ArcTo(S * 0.5, S * 0.5, 0, S * 0.2, S * 0.7, false, true),
            new LineTo(S * 0.35, S * 0.55),
            new LineTo(S * 0.2, S * 0.7),
            new LineTo(S * 0.35, S * 0.85),
            new LineTo(S * 0.5, S * 0.7)
        );
        p.setFill(Color.TRANSPARENT);
        p.setStroke(STROKE);
        p.setStrokeWidth(1.6);
        p.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        p.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        return wrap(p);
    }

    /** Icône Rétablir (Ctrl+Y). */
    public static Node redoIcon() {
        Node fromRes = loadPng(ICON_PATH + "redo.png");
        if (fromRes != null) return fromRes;
        Path p = new Path();
        p.getElements().addAll(
            new MoveTo(S * 0.25, S * 0.2),
            new ArcTo(S * 0.5, S * 0.5, 0, S * 0.8, S * 0.7, false, false),
            new LineTo(S * 0.65, S * 0.55),
            new LineTo(S * 0.8, S * 0.7),
            new LineTo(S * 0.65, S * 0.85),
            new LineTo(S * 0.5, S * 0.7)
        );
        p.setFill(Color.TRANSPARENT);
        p.setStroke(STROKE);
        p.setStrokeWidth(1.6);
        p.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        p.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        return wrap(p);
    }

    private static Node wrap(Node icon) {
        StackPane pane = new StackPane(icon);
        pane.setMinSize(24, 24);
        pane.setPrefSize(24, 24);
        pane.setMaxSize(24, 24);
        return pane;
    }

    private static Node loadPng(String resourcePath) {
        try {
            java.io.InputStream is = Icons.class.getResourceAsStream(resourcePath);
            if (is == null) return null;
            Image img = new Image(is);
            ImageView iv = new ImageView(img);
            iv.setFitWidth(20);
            iv.setFitHeight(20);
            iv.setPreserveRatio(true);
            return wrap(iv);
        } catch (Exception e) {
            return null;
        }
    }
}
