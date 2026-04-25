module com.chatapp {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires java.sql;
    requires com.google.gson;
    requires org.mariadb.jdbc;

    opens Views to javafx.fxml;

    exports Views;
    exports dao;
    exports databases;
    exports Messaging;
    exports model;
    exports org.example;
    exports server;
    exports server.Exceptions;
}
