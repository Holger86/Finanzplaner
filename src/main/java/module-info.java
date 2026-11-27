module de.finanzplaner {
    requires java.sql;
    requires javafx.controls;
    requires liquibase.core;
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;
    requires org.apache.poi.ooxml.schemas;
    requires org.apache.xmlbeans;
    requires org.slf4j;
    requires org.xerial.sqlitejdbc;

    exports de.finanzplaner.domain;
    exports de.finanzplaner.persistence;
    exports de.finanzplaner.service;
    exports de.finanzplaner.ui to javafx.graphics;

    opens db.changelog;
    opens de.finanzplaner.ui to javafx.graphics;
}
