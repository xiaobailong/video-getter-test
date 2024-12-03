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
import org.example.selenium.utils.UrlAnalysisUtils;
import org.example.selenium.utils.TextOutputUtil;

import java.io.*;

@Slf4j
public class M3u8Analyze {

    public static void downloadVideo(M3U8Info m3U8Info) throws Exception {

        String cacheFilePath = m3U8Info.getCacheFilePath();

        File workFile = new File(cacheFilePath);
        if (!workFile.exists()) {
            return;
        }

        downloadM3u8(m3U8Info);

        String m3U8InfoFileName = cacheFilePath + FileEnums.FILE_PATH_SEPARATOR + FileEnums.M3U8_INFO_FILE_NAME;
        TextOutputUtil.output(JSON.toJSONString(m3U8Info, JSONWriter.Feature.PrettyFormat), m3U8InfoFileName);

        if (m3U8Info.getM3u8ItemFileNames().isEmpty()) {
            log.info("未获取到视频数据!!!");
            return;
        }

        String ffmpegTsFilePath = TSFileMerge.work(m3U8Info);

        if (ffmpegTsFilePath == null) {
            log.info("视频数据合并失败!!!");
            return;
        }

        String videoPath = FileEnums.SAVE_PATH + FileEnums.FILE_PATH_SEPARATOR + m3U8Info.getTitle() + FileEnums.VIDEO_FILE_EXTENSION_NAME;
        CMDProcess.executive(ffmpegTsFilePath, videoPath);

        if (FileUtil.exist(videoPath)) {
            PageHistoryTable.insert(m3U8Info.getVideoUrl(), m3U8Info.getTitleOrigin());
        }
    }

    public static void downloadM3u8(M3U8Info m3U8Info) throws Exception {

        BufferedReader reader = new BufferedReader(new FileReader(m3U8Info.getLogFilePath()));

        String line;
        boolean isDownload = false;

        while ((line = reader.readLine()) != null && !isDownload) {

            log.info(line);

            HarEntry harEntry = JSON.parseObject(line, HarEntry.class);
            String url = harEntry.getRequest().getUrl();
//            log.info("url: " + url);

            if (!url.contains("mp4")) {
                log.info("not_mp4_url: " + url);
                continue;
            }

            if (url.contains(FileEnums.M3U8_MASTER_FILE_NAME)) {

                HttpUtil.downloadFile(url, m3U8Info.getCacheFilePath() + "/master.m3u8");
                m3U8Info.setMasterUrl(url);
                log.info(url);

                String urlPrex = UrlAnalysisUtils.getUrlPrex(url, FileEnums.M3U8_MASTER_FILE_NAME);
                m3U8Info.setUrlPrex(urlPrex);

                downloadM3u8Item(m3U8Info);

                downloadM3u8TS(m3U8Info);

                isDownload = true;

            } else if (url.contains(FileEnums.M3U8_FILE_EXTENSION_NAME)) {

                HttpUtil.downloadFile(harEntry.getRequest().getUrl(), m3U8Info.getCacheFilePath());

                String urlPrex = UrlAnalysisUtils.getUrlPrex(url, FileEnums.M3U8_INDEX_FILE_NAME_PREX);
                m3U8Info.setUrlPrex(urlPrex);

                downloadM3u8TS(m3U8Info);

                isDownload = true;
            }
        }
        reader.close();
    }

    public static void downloadM3u8Item(M3U8Info m3U8Info) throws Exception {
        String masterFilePath = m3U8Info.getCacheFilePath() + FileEnums.FILE_PATH_SEPARATOR + FileEnums.M3U8_MASTER_FILE_NAME;
        BufferedReader reader = new BufferedReader(new FileReader(masterFilePath));

        String line;
        boolean isDownload = false;

        while ((line = reader.readLine()) != null && !isDownload) {
            if (!line.contains("#") && !line.trim().isEmpty()) {

                String urlNew = m3U8Info.getUrlPrex() + line;

                if (line.startsWith("http")) {
                    urlNew = line;
                }

                UrlInfo urlInfo = UrlAnalysisUtils.parseUrlInfo(line);

                m3U8Info.getM3u8ItemUrls().add(urlNew);
                m3U8Info.getM3u8ItemFileNames().add(urlInfo.getFileName());

                log.info("M3u8ItemFileName:\t" + urlInfo.getFileName());

                String fileNamePath = m3U8Info.getCacheFilePath() + FileEnums.FILE_PATH_SEPARATOR + urlInfo.getFileName();
                HttpUtil.downloadFile(urlNew, fileNamePath);
                log.info(urlNew);

                isDownload = true;
            }
        }
        reader.close();
    }

    public static void downloadM3u8TS(M3U8Info m3U8Info) throws Exception {

        for (String fileItem : m3U8Info.getM3u8ItemFileNames()) {
            String fileNamePath = m3U8Info.getCacheFilePath() + FileEnums.FILE_PATH_SEPARATOR + fileItem;
            log.info(fileNamePath);

            BufferedReader reader = new BufferedReader(new FileReader(fileNamePath));

            String line;

            String tsFilePath = m3U8Info.getCacheFilePath() + FileEnums.FILE_PATH_SEPARATOR + FileEnums.TF_FILE_PATH_NAME;
            File tsFile = new File(tsFilePath);
            if (!tsFile.exists()) {
                tsFile.mkdirs();
            }

            String urlPrex = m3U8Info.getUrlPrex();
            log.info("m3U8Info.getUrlPrex():\t" + urlPrex);

            while ((line = reader.readLine()) != null) {
                if (line.contains(FileEnums.TF_FILE_EXTENSION_NAME)) {

                    String url = urlPrex + line;

                    try {
                        HttpUtil.downloadFile(url, tsFilePath);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                } else if (!line.startsWith("#") && line.contains(FileEnums.VIDEO_FILE_EXTENSION_NAME)) {

                    String url = urlPrex + line;
                    if (line.startsWith("http")) {
                        url = line;
                    }

                    try {
                        HttpUtil.downloadFile(url, tsFilePath);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                log.info(line);
            }

            reader.close();
        }
    }
}
