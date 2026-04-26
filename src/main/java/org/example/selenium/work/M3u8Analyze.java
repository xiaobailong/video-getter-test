package org.example.selenium.work;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.slf4j.Slf4j;
import org.example.selenium.db.PageHistoryTable;
import org.example.selenium.entity.M3U8Info;
import org.example.selenium.entity.UrlInfo;
import org.example.selenium.enums.DateTimeFormatEnum;
import org.example.selenium.enums.FileEnums;
import org.example.selenium.utils.UrlAnalysisUtils;
import org.example.selenium.utils.TextOutputUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

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

        String pathDate = new DateTime().toString(DateTimeFormatEnum.PATH_DATE);
        String savePath = FileEnums.SAVE_PATH + FileEnums.FILE_PATH_SEPARATOR + pathDate;
        if (!FileUtil.exist(savePath)) {
            FileUtil.mkdir(savePath);
        }
        String videoPath = savePath + FileEnums.FILE_PATH_SEPARATOR + m3U8Info.getTitle() + FileEnums.VIDEO_FILE_EXTENSION_NAME;
        CMDProcess.executive(ffmpegTsFilePath, videoPath);

        if (FileUtil.exist(videoPath)) {
            PageHistoryTable.insert(m3U8Info.getVideoUrl(), m3U8Info.getTitleOrigin());
        }
    }

    public static void downloadM3u8(M3U8Info m3U8Info) throws Exception {

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(m3U8Info.getLogFilePath()), StandardCharsets.UTF_8));

        String line;
        boolean isDownload = false;

        while ((line = reader.readLine()) != null && !isDownload) {

//            log.info(line);

            // 解析 CDP Performance Log 的 JSON（摒弃 BMP 的 HarEntry）
            JSONObject entry = JSON.parseObject(line);
            String url = entry.getString("url");
            if (url == null) {
                continue;
            }
//            log.info("url: " + url);

            if (!url.contains("mp4")) {
//                log.info("not_mp4_url: " + url);
                continue;
            }

            if (url.contains(FileEnums.M3U8_MASTER_FILE_NAME)) {

                downloadFileWithReferer(url, m3U8Info.getCacheFilePath() + "/master.m3u8", m3U8Info.getVideoUrl());
                m3U8Info.setMasterUrl(url);
                log.info(url);

                String urlPrex = UrlAnalysisUtils.getUrlPrex(url, FileEnums.M3U8_MASTER_FILE_NAME);
                m3U8Info.setUrlPrex(urlPrex);

                downloadM3u8Item(m3U8Info);

                downloadM3u8TS(m3U8Info);

                isDownload = true;

            } else if (url.contains(FileEnums.M3U8_FILE_EXTENSION_NAME)) {

                downloadFileWithReferer(url, m3U8Info.getCacheFilePath(), m3U8Info.getVideoUrl());

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
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(masterFilePath), StandardCharsets.UTF_8));

        String line;
        boolean isDownload = false;

        while ((line = reader.readLine()) != null && !isDownload) {
            if (!line.contains("#") && !line.trim().isEmpty()) {

                String urlNew = m3U8Info.getUrlPrex() + line;

                if (line.startsWith("http")) {
                    urlNew = line;
                }

                UrlInfo urlInfo = UrlAnalysisUtils.parseUrlInfo(urlNew);

                m3U8Info.getM3u8ItemUrls().add(urlNew);
                m3U8Info.getM3u8ItemFileNames().add(urlInfo.getFileName());

                log.info("M3u8ItemFileName:\t" + urlInfo.getFileName());

                String fileNamePath = m3U8Info.getCacheFilePath() + FileEnums.FILE_PATH_SEPARATOR + urlInfo.getFileName();
                downloadFileWithReferer(urlNew, fileNamePath, m3U8Info.getVideoUrl());
                log.info(urlNew);

                isDownload = true;
            }
        }
        reader.close();
    }

    /**
     * 携带 Referer 头下载文件（绕过 CDN 防盗链）
     * @param url 下载地址
     * @param destPath 本地保存路径
     * @param referer Referer 来源（一般为视频页面 URL）
     */
    private static void downloadFileWithReferer(String url, String destPath, String referer) {
        HttpRequest.get(url)
                .header("Referer", referer)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(30000)
                .execute()
                .writeBody(new File(destPath));
    }

    /**
     * TS 下载任务
     */
    private static class DownloadTask {
        String url;
        String destPath;
        int index;

        DownloadTask(String url, String destPath, int index) {
            this.url = url;
            this.destPath = destPath;
            this.index = index;
        }
    }

    /**
     * 3 线程并发下载 TS 段，每个线程承接约 1/3 的任务
     */
    public static void downloadM3u8TS(M3U8Info m3U8Info) throws Exception {

        String tsFilePath = m3U8Info.getCacheFilePath() + FileEnums.FILE_PATH_SEPARATOR + FileEnums.TF_FILE_PATH_NAME;
        File tsFile = new File(tsFilePath);
        if (!tsFile.exists()) {
            tsFile.mkdirs();
        }

        String urlPrex = m3U8Info.getUrlPrex();
        Map<String, String> capturedTsUrls = m3U8Info.getCapturedTsUrls();

        // 1. 构建所有下载任务列表
        List<DownloadTask> allTasks = new ArrayList<>();
        for (String fileItem : m3U8Info.getM3u8ItemFileNames()) {
            String fileNamePath = m3U8Info.getCacheFilePath() + FileEnums.FILE_PATH_SEPARATOR + fileItem;
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(fileNamePath), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(FileEnums.TF_FILE_EXTENSION_NAME)) {
                    String tsFileName = line;
                    int qMark = line.indexOf('?');
                    if (qMark > 0) {
                        tsFileName = line.substring(0, qMark);
                    }
                    String url = capturedTsUrls.containsKey(tsFileName) ? capturedTsUrls.get(tsFileName) : urlPrex + line;
                    allTasks.add(new DownloadTask(url, tsFilePath, allTasks.size() + 1));
                } else if (!line.startsWith("#") && line.contains(FileEnums.VIDEO_FILE_EXTENSION_NAME)) {
                    String url = urlPrex + line;
                    if (line.startsWith("http")) {
                        url = line;
                    }
                    String videoFileName = line;
                    int qMark = line.indexOf('?');
                    if (qMark > 0) {
                        videoFileName = line.substring(0, qMark);
                    }
                    if (capturedTsUrls.containsKey(videoFileName)) {
                        url = capturedTsUrls.get(videoFileName);
                    }
                    allTasks.add(new DownloadTask(url, tsFilePath, allTasks.size() + 1));
                }
            }
            reader.close();
        }

        int total = allTasks.size();
        log.info("总 TS 段数: {}, 使用 3 线程并发下载", total);

        // 2. 将任务均分给 3 个线程
        int threadCount = 3;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            int start = t * total / threadCount;
            int end = (t + 1) * total / threadCount;
            if (t == threadCount - 1) {
                end = total; // 最后一个线程处理剩余部分
            }
            List<DownloadTask> subTasks = allTasks.subList(start, end);
            final int threadId = t + 1;
            final int s = start;
            final int e = end;

            new Thread(() -> {
                log.info("线程{} 启动: 负责第 {}-{} 段 (共 {} 段)", threadId, s + 1, e, subTasks.size());
                for (DownloadTask task : subTasks) {
                    boolean success = false;
                    for (int retry = 1; retry <= 3; retry++) {
                        try {
                            downloadFileWithReferer(task.url, task.destPath, m3U8Info.getVideoUrl());
                            success = true;
                            log.info("[线程{}][{}/{}] 下载成功: {}", threadId, task.index, total, task.url);
                            break;
                        } catch (Exception exception) {
                            log.warn("[线程{}][{}/{}] 第{}次下载失败: {} - {}", threadId, task.index, total, retry, task.url, exception.getMessage());
                            if (retry < 3) {
                                // 重试前短暂等待 2 秒
                                try {
                                    Thread.sleep(2000);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        }
                    }
                    if (success) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                        log.error("[线程{}][{}/{}] 重试3次后仍然失败: {}", threadId, task.index, total, task.url);
                    }
                }
                log.info("线程{} 完成", threadId);
                latch.countDown();
            }, "ts-download-" + threadId).start();
        }

        // 3. 等待所有线程完成
        latch.await();
        log.info("全部下载完成: 成功 {} / 失败 {} / 总计 {}", successCount.get(), failCount.get(), total);
    }
}