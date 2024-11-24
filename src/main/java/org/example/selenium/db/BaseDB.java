package org.example.selenium.db;

import lombok.extern.slf4j.Slf4j;
import org.example.selenium.enums.FilePathEnums;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Slf4j
public class BaseDB {

    public static Connection getConnection() {
        try {
//            log.info("jdbc:sqlite:" + FilePathEnums.DBPath);
            return DriverManager.getConnection("jdbc:sqlite:" + FilePathEnums.DBPath);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void close(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
