package org.example.selenium.work;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.example.selenium.entity.M3U8Info;
import org.example.selenium.enums.FileEnums;

import java.io.*;
import java.util.Arrays;

@Slf4j
public class TSFileMerge {

    public static String work(M3U8Info m3U8Info) {

        try {
            String cacheFilePath = m3U8Info.getCacheFilePath();
            String ffmpegTsFilePath = cacheFilePath + FileEnums.FILE_PATH_SEPARATOR + FileEnums.FFMPEG_TS_FILE_PATH;
            String tfFilePath = cacheFilePath + FileEnums.FILE_PATH_SEPARATOR + FileEnums.TF_FILE_PATH_NAME;

            File tfCacheFile = new File(tfFilePath);
            File[] tfFiles = tfCacheFile.listFiles();
            if (tfFiles == null || tfFiles.length == 0) {
                return null;
            }
            Arrays.sort(tfFiles, new FileComparator());
            log.info(JSON.toJSONString(tfFiles));

            BufferedWriter bw = new BufferedWriter(new FileWriter(ffmpegTsFilePath));
            for (File tfFile : tfFiles) {
                String ffmpegTsLine = "file " + tfFilePath + "/" + tfFile.getName();
                bw.write(ffmpegTsLine + "\n");
                log.info(ffmpegTsLine);
            }

            bw.close();

            return ffmpegTsFilePath;
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return null;
    }

    public static void main(String[] args) {
        try {
            BufferedReader br = new BufferedReader(new FileReader("C:\\Users\\guangbai\\Downloads\\ffmpegTsFilePath.txt"));
            String line;
            File[] files = new File[405];
            int i = 0;
            while ((line = br.readLine()) != null) {
                String fileName = line.replace("file ", "");
//                System.out.println(fileName);
                files[i++] = new File(fileName);
            }
            Arrays.sort(files, new FileComparator());
            br.close();
            for (File file : files) {
                System.out.println(file.getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
