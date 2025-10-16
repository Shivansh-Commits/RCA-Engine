module com.l3.api {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.testng;

    // Apache POI modules for Excel generation
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;
    requires java.desktop;

    // DQ Engine packages (new structure)
    opens com.l3.dqengine.api to javafx.fxml;
    opens com.l3.dqengine.controller to javafx.fxml, javafx.base;
    opens com.l3.dqengine.api.model to javafx.fxml, javafx.base;
    opens com.l3.dqengine.api.utils to javafx.fxml;
    opens com.l3.dqengine.common.reporting to javafx.fxml;
    opens com.l3.dqengine.pnr to javafx.fxml;
    opens com.l3.dqengine.pnr.model to javafx.fxml, javafx.base;
    opens com.l3.dqengine.pnr.utils to javafx.fxml;

    // Launcher module
    opens com.l3.launcher to javafx.fxml;

    // Log parser module - CORRECTED: Controller is now shared between API and PNR
    opens com.l3.logparser to javafx.fxml;
    opens com.l3.logparser.enums to javafx.base;
    opens com.l3.logparser.controller to javafx.fxml, javafx.base; // Shared controller

    // API log parser packages (business logic only)
    opens com.l3.logparser.api.model to javafx.fxml, javafx.base;
    opens com.l3.logparser.api.service to javafx.fxml;
    opens com.l3.logparser.api.parser to javafx.fxml;

    // Exports (updated to new structure)
    exports com.l3.dqengine.controller;
    exports com.l3.dqengine.api.model;
    exports com.l3.launcher;
    exports com.l3.logparser; // Export logparser package for JavaFX access
}