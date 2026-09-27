module com.taskscheduler {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;
    requires java.sql;
    requires java.net.http;
    requires java.desktop;
    requires static org.xerial.sqlitejdbc;

    opens com.taskscheduler to javafx.fxml, javafx.graphics, javafx.base;
    opens com.taskscheduler.controller to javafx.fxml, javafx.graphics, javafx.base;
    opens com.taskscheduler.model to javafx.base, javafx.fxml;

    exports com.taskscheduler;
    exports com.taskscheduler.controller;
    exports com.taskscheduler.model;
    exports com.taskscheduler.service;
    exports com.taskscheduler.dao;
    exports com.taskscheduler.concurrent;
    exports com.taskscheduler.exception;
    exports com.taskscheduler.util;
}
