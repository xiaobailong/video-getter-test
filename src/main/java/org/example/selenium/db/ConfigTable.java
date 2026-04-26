package org.example.selenium.db;

import cn.hutool.core.date.DateTime;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.example.selenium.enums.DateTimeFormatEnum;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ConfigTable extends BaseDB {

    public static void createTable() {
        try {
            Connection connection = getConnection();
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);

            statement.executeUpdate("create table config(key string,value string,remark string,create_time string,update_time string)");

            close(connection);
        } catch (SQLException e) {
            e.printStackTrace(System.err);
        }
    }

    public static void insert(String key, String value) {
        Connection connection = getConnection();
        try {
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);

            String create_time = new DateTime().toString(DateTimeFormatEnum.DATE_TIME);
            String update_time = new DateTime().toString(DateTimeFormatEnum.DATE_TIME);
            String sql = String.format("insert into config(key,value,create_time,update_time) " +
                    "values('%s','%s','%s','%s')", key, value, create_time, update_time);

            log.info(sql);
            statement.executeUpdate(sql);

        } catch (SQLException e) {
            e.printStackTrace(System.err);
        } finally {
            close(connection);
        }
    }

    public static Map<String, Object> queryOne(String key) {
        Connection connection = getConnection();
        try {
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);

            String sql = String.format("select * from config where key='%s'", key);

            ResultSet rs = statement.executeQuery(sql);

            ResultSetMetaData rsmd = rs.getMetaData();
            int count = rsmd.getColumnCount();
            String[] fieldNames = new String[count];
            for (int j = 0; j < count; j++) {
                fieldNames[j] = rsmd.getColumnName(j + 1);
            }

            Map<String, Object> fields = new HashMap<>();
            while (rs.next()) {
                for (String fieldName : fieldNames) {
                    fields.put(fieldName, rs.getObject(fieldName));
                }
                log.info(JSON.toJSONString(fields));
            }
            return fields;
        } catch (SQLException e) {
            e.printStackTrace(System.err);
        } finally {
            close(connection);
        }
        return new HashMap<>();
    }

    public static String queryValue(String key) {
        Connection connection = getConnection();
        try {
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);

            String sql = String.format("select value from config where key='%s'", key);

            ResultSet rs = statement.executeQuery(sql);

            String value = null;

            while (rs.next()) {
                value = rs.getString(1);
            }
            return value;
        } catch (SQLException e) {
            e.printStackTrace(System.err);
        } finally {
            close(connection);
        }
        return null;
    }

    /**
     * 写入配置：key 已存在则 update，不存在则 insert
     */
    public static void upsert(String key, String value) {
        if (isExist(key)) {
            Connection connection = getConnection();
            try {
                Statement statement = connection.createStatement();
                statement.setQueryTimeout(30);
                String update_time = new DateTime().toString(DateTimeFormatEnum.DATE_TIME);
                String sql = String.format("update config set value='%s', update_time='%s' where key='%s'",
                        value.replace("'", "''"), update_time, key);
                statement.executeUpdate(sql);
            } catch (SQLException e) {
                e.printStackTrace(System.err);
            } finally {
                close(connection);
            }
        } else {
            insert(key, value);
        }
    }

    public static boolean isExist(String key) {
        Connection connection = getConnection();
        try {
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);

            String sql = String.format("select COUNT(*) as ccount from config where key='%s'", key);

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
//        ConfigTable.createTable();
//        log.info(ConfigTable.queryValue("pageUrlTemp"));
        
        // Example: Set download method to 'cococut' or 'original'
        if (args.length > 0) {
            String method = args[0];
            if ("cococut".equalsIgnoreCase(method) || "original".equalsIgnoreCase(method)) {
                if (isExist("downloadMethod")) {
                    // Update existing setting
                    Connection connection = getConnection();
                    try {
                        Statement statement = connection.createStatement();
                        statement.setQueryTimeout(30);
                        String update_time = new DateTime().toString(DateTimeFormatEnum.DATE_TIME);
                        String sql = String.format("update config set value='%s', update_time='%s' where key='downloadMethod'", method, update_time);
                        statement.executeUpdate(sql);
                        log.info("Download method set to: " + method);
                    } catch (SQLException e) {
                        e.printStackTrace(System.err);
                    } finally {
                        close(connection);
                    }
                } else {
                    // Insert new setting
                    insert("downloadMethod", method);
                    log.info("Download method set to: " + method);
                }
            } else {
                log.info("Invalid download method. Use 'cococut' or 'original'");
            }
        } else {
            log.info("Current download method: " + queryValue("downloadMethod"));
        }
    }
}