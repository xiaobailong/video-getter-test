package org.example.selenium.main;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.io.FileUtil;
import org.example.selenium.db.PageHistoryTable;
import org.example.selenium.enums.DateTimeFormatEnum;
import org.example.selenium.enums.FileEnums;

import java.util.List;

public class GetDownloadUrls {

    public static void main(String[] args) {
//        getDownloadUrls();
        saveDownloadUrls();
    }

    public static void getDownloadUrls() {
        String listFileName = FileEnums.SAVE_PATH + "/" + new DateTime().toString(DateTimeFormatEnum.PATH_DATE) + ".txt";
        List<String> lines = FileUtil.readUtf8Lines(listFileName);
        for (String line : lines) {
            if (PageHistoryTable.isExist(line)) {
                continue;
            }
            System.out.println(line);
        }
    }

    public static void saveDownloadUrls() {
        String listFileName = FileEnums.SAVE_PATH + "/" + new DateTime().toString(DateTimeFormatEnum.PATH_DATE) + ".txt";
        List<String> lines = FileUtil.readUtf8Lines(listFileName);
        for (String line : lines) {
            if (PageHistoryTable.isExist(line)) {
                continue;
            }
            PageHistoryTable.insert(line, "");
            System.out.println(line);
        }
    }
}
