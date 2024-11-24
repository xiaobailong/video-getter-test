package org.example.selenium.work;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.slf4j.Slf4j;
import net.lightbody.bmp.core.har.HarEntry;
import org.example.selenium.db.PageHistoryTable;
import org.example.selenium.entity.M3U8Info;
import org.example.selenium.entity.UrlInfo;
import org.example.selenium.enums.FileEnums;
import org.example.selenium.utils.CustomStringUtils;
import org.example.selenium.utils.TextFileOutputUtil;

import java.io.*;

@Slf4j
public class M3u8Analyze {


    public static void main(String[] args) throws Exception {

        System.getProperties().setProperty("http.proxyHost", "127.0.0.1");
        System.getProperties().setProperty("http.proxyPort", "10809");
        System.getProperties().setProperty("https.proxyHost", "127.0.0.1");
        System.getProperties().setProperty("https.proxyPort", "10809");

//        String title = "yahoo-1691502842775";

//        downloadM3u8(title);

//        TSFileMerge.mergeFiles(new File(pathPrex + FileEnums.FILE_PATH_SEPARATOR + title + "/ts"), new File(pathPrex + FileEnums.FILE_PATH_SEPARATOR + title + ".mp4"));

        BufferedReader reader = new BufferedReader(new FileReader("D:/a/a.txt"));

        String line;
        boolean isDownload = false;

        while ((line = reader.readLine()) != null && !isDownload) {

            HarEntry harEntry = JSON.parseObject(line, HarEntry.class);
            String url = harEntry.getRequest().getUrl();
            if (url.contains("hls") && !url.contains(".js") && !url.contains(".css")) {
                log.info("url: " + url);
                log.info(JSON.toJSONString(harEntry, JSONWriter.Feature.PrettyFormat));
            }
        }

        reader.close();
    }

    public static void downloadVideo(M3U8Info m3U8Info) throws Exception {
        String workFilePath = m3U8Info.getPathPrex() + FileEnums.FILE_PATH_SEPARATOR + m3U8Info.getTitle();
        m3U8Info.setWorkFilePath(workFilePath);

        File workFile = new File(workFilePath);
        if (!workFile.exists()) {
            workFile.mkdirs();
        }

        downloadM3u8(m3U8Info);

        TextFileOutputUtil.output(JSON.toJSONString(m3U8Info, JSONWriter.Feature.PrettyFormat), workFilePath + FileEnums.FILE_PATH_SEPARATOR + FileEnums.M3U8_INFO_FILE_NAME);

//        TSFileMerge.mergeFiles(new File(pathPrex + FileEnums.FILE_PATH_SEPARATOR + m3U8Info.getTitle() + "/ts"), new File(pathPrex + FileEnums.FILE_PATH_SEPARATOR + m3U8Info.getTitle() + ".mp4"));

        if (m3U8Info.getM3u8ItemFileNames().isEmpty()) {
            return;
        }
        String m3u8FilePath = workFilePath + FileEnums.FILE_PATH_SEPARATOR + m3U8Info.getM3u8ItemFileNames().get(0);
        String ffmpegTsFilePath = workFilePath + FileEnums.FILE_PATH_SEPARATOR + FileEnums.FFMPEG_TS_FILE_PATH;
        String tfFilePath = workFilePath + FileEnums.FILE_PATH_SEPARATOR + FileEnums.TF_FILE_PATH_NAME;

        TSFileMerge.work(m3u8FilePath, ffmpegTsFilePath, tfFilePath);

        String videoPath = m3U8Info.getPathPrex() + FileEnums.FILE_PATH_SEPARATOR + m3U8Info.getTitle() + FileEnums.VIDEO_FILE_EXTENSION_NAME;
        CMDProcess.executive(ffmpegTsFilePath, videoPath);

        if(FileUtil.exist(videoPath)){
            PageHistoryTable.insert(m3U8Info.getVideoUrl(),m3U8Info.getTitleOrigin());
        }
    }

    public static void downloadM3u8(M3U8Info m3U8Info) throws Exception {

        BufferedReader reader = new BufferedReader(new FileReader(m3U8Info.getLogFilePath()));

        String line;
        boolean isDownload = false;

        while ((line = reader.readLine()) != null && !isDownload) {

            HarEntry harEntry = JSON.parseObject(line, HarEntry.class);
            String url = harEntry.getRequest().getUrl();
//            log.info("url: " + url);

            if (!url.contains("mp4")) {
                log.info("not_mp4_url: " + url);
                continue;
            }

            if (url.contains(FileEnums.M3U8_MASTER_FILE_NAME)) {
                log.info(line);

                HttpUtil.downloadFile(harEntry.getRequest().getUrl(), m3U8Info.getWorkFilePath() + "/master.m3u8");

                m3U8Info.setMasterUrl(url);
                log.info(url);

                String urlPrex = CustomStringUtils.getUrlPrex(url, FileEnums.M3U8_MASTER_FILE_NAME);
                m3U8Info.setUrlPrex(urlPrex);

                downloadM3u8Item(m3U8Info);

                downloadM3u8TS(m3U8Info);

                isDownload = true;
            } else if (url.contains(FileEnums.M3U8_FILE_EXTENSION_NAME)) {
                log.info(line);

                HttpUtil.downloadFile(harEntry.getRequest().getUrl(), m3U8Info.getWorkFilePath());

                String urlPrex = CustomStringUtils.getUrlPrex(url, FileEnums.M3U8_INDEX_FILE_NAME_PREX);
                m3U8Info.setUrlPrex(urlPrex);

                downloadM3u8TS(m3U8Info);

                isDownload = true;
            }
        }
        reader.close();
    }

    public static void downloadM3u8Item(M3U8Info m3U8Info) throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader(m3U8Info.getWorkFilePath() + FileEnums.FILE_PATH_SEPARATOR + FileEnums.M3U8_MASTER_FILE_NAME));

        String line;
        boolean isDownload = false;

        while ((line = reader.readLine()) != null && !isDownload) {
            if (!line.contains("#") && line.trim().length() > 0) {

                String urlNew = m3U8Info.getUrlPrex() + line;
                if (line.startsWith("http")) {
                    urlNew = line;
                }

                UrlInfo urlInfo = CustomStringUtils.parseFileUrlInfo(line);

                m3U8Info.getM3u8ItemUrls().add(urlNew);
                m3U8Info.getM3u8ItemFileNames().add(urlInfo.getFileName());
                log.info("urlInfo.getFileName():\t" + urlInfo.getFileName());

                HttpUtil.downloadFile(urlNew, m3U8Info.getWorkFilePath());

                log.info(urlNew);

                isDownload = true;
            }
        }
        reader.close();
    }

    public static void downloadM3u8TS(M3U8Info m3U8Info) throws Exception {

        File[] m3u8FileItems = new File(m3U8Info.getWorkFilePath()).listFiles();

        for (File fileItem : m3u8FileItems) {

            if (fileItem.isDirectory()) {
                continue;
            }

            String fileName = fileItem.getName();
            if (!fileName.endsWith(FileEnums.M3U8_FILE_EXTENSION_NAME)) {
                continue;
            }

            if (fileName.contains(".")) {
                fileName = fileName.split("\\.")[0];
            }

            if (fileName.equals(FileEnums.M3U8_MASTER_FILE_NAME_WITHOUT_EXTENSION_NAME)) {
                continue;
            }

            log.info(fileName);

            BufferedReader reader = new BufferedReader(new FileReader(fileItem));

            String line;

            String outputFilePath = m3U8Info.getWorkFilePath() + FileEnums.FILE_PATH_SEPARATOR + FileEnums.TF_FILE_PATH_NAME;
            File outputFile = new File(outputFilePath);
            if (!outputFile.exists()) {
                outputFile.mkdirs();
            }

            log.info("m3U8Info.getUrlPrex():\t" + m3U8Info.getUrlPrex());
            while ((line = reader.readLine()) != null) {
                if (line.contains(FileEnums.TF_FILE_EXTENSION_NAME)) {

                    String url = m3U8Info.getUrlPrex() + line;

                    try {
                        HttpUtil.downloadFile(url, outputFilePath);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    log.info(line);

                } else if (!line.startsWith("#") && line.contains(FileEnums.VIDEO_FILE_EXTENSION_NAME)) {
                    String url = m3U8Info.getUrlPrex() + line;
                    if (line.startsWith("http")) {
                        url = line;
                    }

                    try {
                        HttpUtil.downloadFile(url, outputFilePath);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    log.info(line + "\t" + m3U8Info.getWorkFilePath());
                }
            }

            reader.close();
        }
    }
}
