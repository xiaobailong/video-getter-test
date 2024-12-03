package org.example.selenium.utils;

import org.example.selenium.entity.M3U8Info;
import org.example.selenium.enums.FileEnums;

import java.io.File;

public class FileIOUtils {

    public static void outputBrowseLog(String content, String logFilePathStr) throws Exception {
        File logFilePath = new File(logFilePathStr);
        if (!logFilePath.getParentFile().exists()) {
            logFilePath.getParentFile().mkdirs();
        }
        TextOutputUtil.output(content, logFilePathStr);
    }

    public static void outputWatchlaterInfo(M3U8Info m3U8Info) throws Exception {
        File cacheFilePath = new File(m3U8Info.getCacheFilePath());
        if (!cacheFilePath.exists()) {
            cacheFilePath.mkdirs();
        }
        String fileName = cacheFilePath + FileEnums.FILE_PATH_SEPARATOR + m3U8Info.getTitle() + FileEnums.URL_FILE_EXTENSION_NAME;
        TextOutputUtil.output(m3U8Info.getVideoUrl(), fileName);
    }
}
