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
            String[] command = {"cmd", "/c", stmt};
            Process process = runtime.exec(command);

            String errStr = consumeInputStream(process.getErrorStream());

            int proc = process.waitFor();
            if (proc == 0) {
                log.info("执行成功");
            } else {
                System.err.println("执行失败" + errStr);
            }

        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    private static String consumeInputStream(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "GBK"));
        String s;
        StringBuilder sb = new StringBuilder();
        while ((s = br.readLine()) != null) {
            log.info(s);
            sb.append(s);
        }
        return sb.toString();
    }
}
