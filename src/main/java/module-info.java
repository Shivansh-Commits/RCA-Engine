module com.l3.engine {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.testng;


    opens com.l3.engine to javafx.fxml;
    opens com.l3.engine.controller to javafx.fxml;
    opens com.l3.engine.model to javafx.fxml;
    opens com.l3.engine.apiutils to javafx.fxml;

    exports com.l3.engine;
    exports com.l3.engine.controller;
    exports com.l3.engine.model;
    exports com.l3.engine.apiutils;
}