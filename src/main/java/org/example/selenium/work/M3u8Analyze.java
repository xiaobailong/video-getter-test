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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
     * 探测网速，返回最优并发线程数
     * <p>下载前 N 个片段测量平均耗时，根据耗时决定线程数:</p>
     * < 300ms → 8 线程, < 800ms → 6 线程, < 2000ms → 4 线程, < 5000ms → 2 线程, 否则 1 线程
     * @return 长度为2的int数组: [0]=threadCount, [1]=probeCount(已下载的探测片段数，后续需跳过)
     */
    private static int[] probeNetworkSpeed(M3U8Info m3U8Info, List<DownloadTask> allTasks, int total) {
        int probeCount = Math.min(5, total);
        if (total <= probeCount) {
            return new int[]{1, probeCount};
        }

        List<Long> probeTimes = new ArrayList<>();

        for (int i = 0; i < probeCount; i++) {
            DownloadTask task = allTasks.get(i);
            long start = System.currentTimeMillis();
            try {
                downloadFileWithReferer(task.url, task.destPath, m3U8Info.getVideoUrl());
                long elapsed = System.currentTimeMillis() - start;
                probeTimes.add(elapsed);
                log.info("[网速探测] [{}/{}] 耗时 {} ms", i + 1, probeCount, elapsed);
            } catch (Exception e) {
                probeTimes.add(30000L);
                log.warn("[网速探测] [{}/{}] 失败: {}", i + 1, probeCount, e.getMessage());
            }
        }

        double avgTime = probeTimes.stream().mapToLong(Long::longValue).average().orElse(5000);
        log.info("网速探测完成: 平均下载耗时 {} ms/段", String.format("%.0f", avgTime));

        int threadCount;
        if (avgTime < 300) {
            threadCount = 8;
        } else if (avgTime < 800) {
            threadCount = 6;
        } else if (avgTime < 2000) {
            threadCount = 4;
        } else if (avgTime < 5000) {
            threadCount = 2;
        } else {
            threadCount = 1;
        }

        log.info("根据网速决定使用 {} 线程并发下载", threadCount);
        return new int[]{threadCount, probeCount};
    }

    /**
     * 自适应并发下载 TS 段
     * <p>先探测网速决定线程数，再用线程池并发下载，每个片段失败重试3次</p>
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
        if (total == 0) {
            log.info("没有发现 TS 片段，跳过下载");
            return;
        }

        // 2. 探测网速，确定最佳并发线程数及已下载的探测片段数
        int[] probeResult = probeNetworkSpeed(m3U8Info, allTasks, total);
        int threadCount = probeResult[0];
        int probeCount = probeResult[1];
        log.info("总 TS 段数: {}, 使用 {} 线程并发下载 (已探测 {} 段, 跳过)", total, threadCount, probeCount);

        // 3. 使用线程池并发下载剩余片段
        AtomicInteger successCount = new AtomicInteger(probeCount); // 探测阶段已成功下载 probeCount 个
        AtomicInteger failCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = probeCount; i < total; i++) {
            DownloadTask task = allTasks.get(i);
            executor.submit(() -> {
                boolean success = false;
                for (int retry = 1; retry <= 3; retry++) {
                    try {
                        downloadFileWithReferer(task.url, task.destPath, m3U8Info.getVideoUrl());
                        success = true;
                        log.info("[下载线程][{}/{}] 下载成功: {}", task.index, total, task.url);
                        break;
                    } catch (Exception exception) {
                        log.warn("[下载线程][{}/{}] 第{}次下载失败: {} - {}", task.index, total, retry, task.url, exception.getMessage());
                        if (retry < 3) {
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
                    log.error("[下载线程][{}/{}] 重试3次后仍然失败: {}", task.index, total, task.url);
                }
            });
        }

        executor.shutdown();
        // 最多等待 1 小时
        executor.awaitTermination(1, TimeUnit.HOURS);

        log.info("全部下载完成: 成功 {} / 失败 {} / 总计 {}", successCount.get(), failCount.get(), total);
    }
}