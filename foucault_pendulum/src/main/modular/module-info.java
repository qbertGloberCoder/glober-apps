module me.qbert {
    requires javafx.controls;
    requires javafx.graphics;

    // Swing/AWT live in java.desktop
    requires java.desktop;

    exports me.qbert.foucault;
}
