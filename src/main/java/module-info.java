module com.l3.launcher {
    requires javafx.controls;
    requires javafx.fxml;

    // Apache POI modules for Excel generation
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;
    requires java.desktop;

    // JSON processing for Log Extraction module
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;

    // HTTP client dependencies (using automatic module names)
    requires org.apache.httpcomponents.client5.httpclient5;
    requires org.apache.httpcomponents.core5.httpcore5;

    // DQ Engine packages (new structure)
    opens com.l3.rcaengine.api to javafx.fxml;
    opens com.l3.rcaengine.controller to javafx.fxml, javafx.base;
    opens com.l3.rcaengine.api.model to javafx.fxml, javafx.base;
    opens com.l3.rcaengine.api.utils to javafx.fxml;
    opens com.l3.rcaengine.common.reporting to javafx.fxml;
    opens com.l3.rcaengine.pnr to javafx.fxml;
    opens com.l3.rcaengine.pnr.model to javafx.fxml, javafx.base;
    opens com.l3.rcaengine.pnr.utils to javafx.fxml;

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

    // Log extractor module
    opens com.l3.logextractor to javafx.fxml;
    opens com.l3.logextractor.controller to javafx.fxml, javafx.base;
    opens com.l3.logextractor.model to javafx.fxml, javafx.base, com.fasterxml.jackson.databind;
    opens com.l3.logextractor.config to javafx.fxml, com.fasterxml.jackson.databind;
    opens com.l3.logextractor.service to javafx.fxml;

    // Exports (updated to new structure)
    exports com.l3.rcaengine.controller;
    exports com.l3.rcaengine.api.model;
    exports com.l3.launcher;
    exports com.l3.logparser; // Export logparser package for JavaFX access
    exports com.l3.logextractor; // Export logextractor package for JavaFX access
}