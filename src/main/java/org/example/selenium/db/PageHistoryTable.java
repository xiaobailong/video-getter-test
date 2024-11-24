package org.example.selenium.db;

import cn.hutool.core.date.DateTime;
import lombok.extern.slf4j.Slf4j;
import org.example.selenium.enums.DateTimeFormatEnum;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
public class PageHistoryTable extends BaseDB {

    public static void createTable() {
        try {
            Connection connection = getConnection();
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);

            statement.executeUpdate("create table page_history(url string,title string,remark string,create_time string,update_time string)");

            close(connection);
        } catch (SQLException e) {
            e.printStackTrace(System.err);
        }
    }

    public static void insert(String url, String title) {
        Connection connection = getConnection();
        try {
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);

            String create_time = new DateTime().toString(DateTimeFormatEnum.DATATIME);
            String update_time = new DateTime().toString(DateTimeFormatEnum.DATATIME);
            String sql = String.format("insert into page_history(url,title,create_time,update_time) " +
                    "values('%s','%s','%s','%s')", url, title, create_time, update_time);

            log.info(sql);
            statement.executeUpdate(sql);

        } catch (SQLException e) {
            e.printStackTrace(System.err);
        } finally {
            close(connection);
        }
    }

    public static boolean isExist(String url) {
        Connection connection = getConnection();
        try {
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);

            String sql = String.format("select COUNT(*) as ccount from page_history where url='%s'", url);

            ResultSet rs = statement.executeQuery(sql);

            int rowCount = 0;
            while (rs.next()) {
                rowCount = rs.getInt("ccount");
            }

            if (rowCount != 0) {
                return true;
            }

        } catch (SQLException e) {
            e.printStackTrace(System.err);
        } finally {
            close(connection);
        }
        return false;
    }

    public static void main(String[] args) {
        PageHistoryTable.createTable();
    }
}
