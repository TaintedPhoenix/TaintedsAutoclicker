module TaintedsAutoclicker {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.base;
    requires javafx.graphics;
    requires java.desktop;
    requires java.base;
    requires com.sun.jna;
    requires json.simple;
    requires jkeymaster;
    requires slf4j.api;

    exports io.github.TaintedPhoenix;
    opens io.github.TaintedPhoenix;
}