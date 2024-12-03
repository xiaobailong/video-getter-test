package org.example.selenium.utils;

import cn.hutool.core.io.FileUtil;
import org.example.selenium.enums.FileEnums;

import java.io.File;

public class VideoUtils {

    public static boolean isVideoExists(String title) {
        File file0 = new File(FileEnums.PATH_PREX + FileEnums.FILE_PATH_SEPARATOR + title + FileEnums.VIDEO_FILE_EXTENSION_NAME);

        File file1 = new File(FileEnums.PATH_PREX + FileEnums.FILE_PATH_SEPARATOR
                + title.replaceAll("\\pP|\\pS|\\pC", "") + FileEnums.VIDEO_FILE_EXTENSION_NAME);

        File file2 = new File(FileEnums.PATH_PREX + FileEnums.FILE_PATH_SEPARATOR
                + title.replaceAll("\\pP|\\pS|\\pC", "").replaceAll("\\pZ", "_")
                + FileEnums.VIDEO_FILE_EXTENSION_NAME);

        return file0.exists() || file1.exists() || file2.exists();
    }

    public static void clearVideoCachePath(String cacheFilePath) {
        File workFilePath0 = new File(cacheFilePath);
        if (workFilePath0.exists()) {
            FileUtil.del(workFilePath0);
            workFilePath0.mkdirs();
        }
    }
}
