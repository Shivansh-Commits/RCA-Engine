module com.l3.api {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.testng;

    // Apache POI modules for Excel generation
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;
    requires java.desktop;

    opens com.l3.api to javafx.fxml;
    opens com.l3.controller to javafx.fxml;
    opens com.l3.api.model to javafx.fxml;
    opens com.l3.api.utils to javafx.fxml;
    opens com.l3.common.reporting to javafx.fxml;
    opens com.l3.pnr to javafx.fxml;
    opens com.l3.pnr.model to javafx.fxml;
    opens com.l3.pnr.utils to javafx.fxml;

    // Launcher module
    opens com.l3.launcher to javafx.fxml;

    // Log parser module
    opens com.l3.logparser to javafx.fxml;
    opens com.l3.logparser.controller to javafx.fxml;
    opens com.l3.logparser.model to javafx.fxml;
    opens com.l3.logparser.service to javafx.fxml;
    opens com.l3.logparser.parser to javafx.fxml;

    exports com.l3.controller;
    exports com.l3.api.model;
    exports com.l3.api.utils;
    exports com.l3.common.reporting;
    exports com.l3.pnr;
    exports com.l3.pnr.model;
    exports com.l3.pnr.utils;

    // Launcher exports
    exports com.l3.launcher;

    // Log parser exports
    exports com.l3.logparser;
    exports com.l3.logparser.controller;
    exports com.l3.logparser.model;
    exports com.l3.logparser.service;
    exports com.l3.logparser.parser;
}