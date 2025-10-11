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

    // Log parser module - FIXED: Added javafx.base for TableView PropertyValueFactory
    opens com.l3.logparser to javafx.fxml;
    opens com.l3.logparser.controller to javafx.fxml, javafx.base;
    opens com.l3.logparser.model to javafx.fxml, javafx.base;
    opens com.l3.logparser.service to javafx.fxml;
    opens com.l3.logparser.parser to javafx.fxml;
    opens com.l3.logparser.enums to javafx.base;

    // Exports (updated to new structure)
    exports com.l3.dqengine.controller;
    exports com.l3.dqengine.api.model;
    exports com.l3.launcher;
}