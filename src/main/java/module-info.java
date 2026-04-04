module fr.cnrs.lacito.liftgui {
    requires fr.cnrs.lacito.liftapi;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.logging;
    requires java.prefs;

    exports fr.cnrs.lacito.liftgui;

    opens fr.cnrs.lacito.liftgui.ui to javafx.fxml;
}
