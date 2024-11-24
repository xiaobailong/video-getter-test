package org.example.selenium.work;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

@Slf4j
public class CMDProcess {

    //一般的执行方法，有时执行exe会卡在那    stmt要执行的命令
    public static void executive(String ffmpegTsFilePath, String videoPath) {
        String stmt = "D:/Tools/DevTools/base/ffmpeg-master-latest-win64-gpl/bin/ffmpeg.exe -y -f concat -safe 0 -i \"" + ffmpegTsFilePath + "\" -c copy \"" + videoPath + "\"";
        log.info(stmt);
        Runtime runtime = Runtime.getRuntime();  //获取Runtime实例
        //执行命令
        try {
            String[] command = {"cmd", "/c", stmt};
            Process process = runtime.exec(command);
            // 标准输入流（必须写在 waitFor 之前）
//            String inStr = consumeInputStream(process.getInputStream());
            // 标准错误流（必须写在 waitFor 之前）
            String errStr = consumeInputStream(process.getErrorStream()); //若有错误信息则输出
            int proc = process.waitFor();
            if (proc == 0) {
                log.info("执行成功");
            } else {
                System.err.println("执行失败" + errStr);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 消费inputstream，并返回
     */
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
