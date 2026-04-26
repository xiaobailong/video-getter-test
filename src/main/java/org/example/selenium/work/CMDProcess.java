package org.example.selenium.work;

import lombok.extern.slf4j.Slf4j;
import org.example.selenium.enums.FileEnums;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

@Slf4j
public class CMDProcess {

    public static void executive(String ffmpegTsFilePath, String videoPath) {
        String stmt = FileEnums.FFMPEG_FILE_PATH + " -y -f concat -safe 0 -i \"" + ffmpegTsFilePath + "\" -c copy \"" + videoPath + "\"";
        log.info(stmt);

        Runtime runtime = Runtime.getRuntime();

        try {
            // macOS 使用 /bin/sh -c，Windows 使用 cmd /c
            String osName = System.getProperty("os.name").toLowerCase();
            String[] command;
            if (osName.contains("mac") || osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
                command = new String[]{"/bin/sh", "-c", stmt};
            } else {
                command = new String[]{"cmd", "/c", stmt};
            }
            Process process = runtime.exec(command);

            String errStr = consumeInputStream(process.getErrorStream());

            int proc = process.waitFor();
            if (proc == 0) {
                log.info("执行成功");
            } else {
                log.error("执行失败: {}", errStr);
            }

        } catch (Exception e) {
            log.error("执行 ffmpeg 命令异常", e);
        }
    }

    private static String consumeInputStream(InputStream is) throws IOException {
        // macOS 使用 UTF-8，Windows 使用 GBK
        String osName = System.getProperty("os.name").toLowerCase();
        String charset = osName.contains("mac") || osName.contains("nix") || osName.contains("nux") ? "UTF-8" : "GBK";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, charset));
        String s;
        StringBuilder sb = new StringBuilder();
        while ((s = br.readLine()) != null) {
            log.info(s);
            sb.append(s);
        }
        return sb.toString();
    }
}