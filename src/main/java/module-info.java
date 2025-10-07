module com.l3.api {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.testng;

    // Apache POI modules for Excel generation
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;
    requires java.desktop;

    opens com.l3.api to javafx.fxml;
    opens com.l3.api.controller to javafx.fxml;
    opens com.l3.api.model to javafx.fxml;
    opens com.l3.api.apiutils to javafx.fxml;

    exports com.l3.api;
    exports com.l3.api.controller;
    exports com.l3.api.model;
    exports com.l3.api.apiutils;
}