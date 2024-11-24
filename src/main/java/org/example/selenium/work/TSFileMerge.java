package org.example.selenium.work;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.example.selenium.enums.FileEnums;

import java.io.*;
import java.util.Arrays;

@Slf4j
public class TSFileMerge {

    public static void work(String m3u8FilePath, String ffmpegTsFilePath, String tfFilePath) {
        try {
            if (!FileUtil.exist(m3u8FilePath)) {
                log.info("m3u8FilePath: " + m3u8FilePath);
                return;
            }

            BufferedReader br = new BufferedReader(new FileReader(m3u8FilePath));
            BufferedWriter bw = new BufferedWriter(new FileWriter(ffmpegTsFilePath));
            String line = "";
            while ((line = br.readLine()) != null) {
                if (!line.startsWith("#")) {
                    if (line.contains(FileEnums.TF_FILE_EXTENSION_NAME) || line.contains(FileEnums.VIDEO_FILE_EXTENSION_NAME)) {
                        String fileName = line;
                        if (line.contains("?")) {
                            fileName = fileName.substring(0, line.indexOf("?"));
                        } else if (line.contains("http")) {
                            fileName = fileName.substring(line.lastIndexOf("/"));
                        }
                        if (FileUtil.exist(tfFilePath + "/" + fileName)) {
                            String ffmpegTsLine = "file " + tfFilePath + "/" + fileName;
                            bw.write(ffmpegTsLine + "\n");
                            log.info(ffmpegTsLine);
                        } else {
                            log.info(fileName + " not exist");
                        }
                    }
                }
            }
            br.close();
            bw.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static void mergeFiles(File tsFiles, File mergedFileName) {
        try {
            File[] tsFileItems = tsFiles.listFiles();
            if (tsFileItems == null || tsFileItems.length == 0) {
                return;
            }
            FileInputStream inputStream = null;
            log.info("from: " + tsFiles.getAbsolutePath() + " to " + mergedFileName.getName());
            FileOutputStream fos = new FileOutputStream(mergedFileName);
            Arrays.sort(tsFileItems, new FileComparator());
            for (int i = 0; i < tsFileItems.length; i++) {
                try {
                    log.info(tsFileItems[i].getName());
                    inputStream = new FileInputStream(tsFileItems[i]);
                    int len = 0;
                    byte[] buf = new byte[1024];
                    while ((len = inputStream.read(buf)) != -1) {
                        fos.write(buf, 0, len);// 写入流中
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (inputStream != null)
                        try {
                            inputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                }
            }
            log.info("合并完成: " + mergedFileName);
            fos.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
