package org.example.selenium.page;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.example.selenium.db.ConfigTable;
import org.example.selenium.db.PageHistoryTable;
import org.example.selenium.entity.M3U8Info;
import org.example.selenium.entity.VideoInfo;
import org.example.selenium.enums.DateTimeFormatEnum;
import org.example.selenium.enums.FileEnums;
import org.example.selenium.utils.FileIOUtils;
import org.example.selenium.utils.VideoUtils;
import org.example.selenium.work.M3u8Analyze;
import org.openqa.selenium.*;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

@Slf4j
public class Watchlater {

    /**
     * 一次性捕获当前 Performance 日志缓冲区中的 Network 请求
     * 返回 network 事件的 JSON 字符串（每条一行）
     */
    private static void drainNetworkLogs(WebDriver webDriver, StringBuilder sb) {
        try {
            LogEntries logs = webDriver.manage().logs().get(LogType.PERFORMANCE);
            int entryCount = 0;
            for (LogEntry entry : logs) {
                entryCount++;
                String msg = entry.getMessage();
                if (msg == null) {
                    log.debug("[PERF_DIAG] entry#{} level={} msg=null", entryCount, entry.getLevel());
                    continue;
                }
                // 诊断：打印前 3 条原始消息的 level 和 source
                if (entryCount <= 3) {
                    try {
                        JSONObject raw = JSON.parseObject(msg);
                        String source = raw.containsKey("message") ? raw.getJSONObject("message").getString("source") : "N/A";
                        log.info("[PERF_DIAG] entry#{} level={} source={} method={}",
                                entryCount, entry.getLevel(), source,
                                raw.containsKey("message") ? raw.getJSONObject("message").getString("method") : "N/A");
                    } catch (Exception e) {
                        log.info("[PERF_DIAG] entry#{} level={} parse_error={}", entryCount, entry.getLevel(), e.getMessage());
                    }
                }

                // Performance 日志的 message 中没有 source 字段（source 是 Console 日志才有的），
                // 需要通过 method 前缀 "Network." 来判断是否为 Network 事件
                JSONObject msgObj = JSON.parseObject(msg);
                JSONObject message = msgObj.getJSONObject("message");
                if (message == null) {
                    continue;
                }
                String method = message.getString("method");
                if (method == null || !method.startsWith("Network.")) {
                    continue;
                }
                JSONObject params = message.getJSONObject("params");
                if (params == null) {
                    continue;
                }
                JSONObject simplified = new JSONObject();
                simplified.put("method", message.getString("method"));
                simplified.put("type", params.getString("type"));
                String url = params.getString("documentURL");
                JSONObject req = params.getJSONObject("request");
                if (req != null && req.getString("url") != null) {
                    url = req.getString("url");
                }
                if (url == null) {
                    url = params.getString("redirectResponse") != null
                            ? params.getJSONObject("redirectResponse").getString("url")
                            : "";
                }
                simplified.put("url", url);
                simplified.put("timestamp", entry.getTimestamp());
                JSONObject response = params.getJSONObject("response");
                if (response != null) {
                    simplified.put("status", response.getInteger("status"));
                    simplified.put("statusText", response.getString("statusText"));
                    simplified.put("mimeType", response.getString("mimeType"));
                }
                sb.append(simplified.toJSONString()).append("\n");
            }
            log.info("[PERF_DIAG] drainNetworkLogs: total_entries={} extracted_urls={} (sample: {})",
                    entryCount, sb.toString().split("\n").length,
                    sb.length() > 0 ? sb.toString().substring(0, Math.min(200, sb.length())) : "NONE");
        } catch (Exception e) {
            log.warn("[PERF_DIAG] drainNetworkLogs 异常: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            e.printStackTrace();
        }
    }

    public static void getVideoList(WebDriver webDriver) throws Exception {

        Thread.sleep(3 * 1000);

        webDriver.get(ConfigTable.queryValue("watchlater"));

        Thread.sleep(3 * 1000);

        String js2 = "window.scrollBy(0,1000);";
        for (int i = 0; i < 5; i++) {
            ((JavascriptExecutor) webDriver).executeScript(js2);
            Thread.sleep(5 * 1000);
            log.info("scroll index: " + i);
        }

        Thread.sleep(3 * 1000);

        WebElement videoPlaylist = webDriver.findElement(By.id("videoPlaylist"));
        List<WebElement> videos = videoPlaylist.findElements(By.cssSelector("[class='pcVideoListItem js-pop videoblock videoBox canEdit']"));

        List<VideoInfo> videoInfos = new ArrayList<>();
        String listFileName = FileEnums.SAVE_PATH + "/" + new DateTime().toString(DateTimeFormatEnum.PATH_DATE) + ".txt";
        if (FileUtil.exist(listFileName)) {
            FileUtil.del(listFileName);
        }
        for (WebElement element : videos) {
            WebElement titleWebElement = element.findElement(By.className("title"));
            String title = titleWebElement.getText();
            String href = titleWebElement.findElement(By.tagName("a")).getAttribute("href");

            if (!href.startsWith(FileEnums.PROTOCOL)) {
                String viewkey = element.getAttribute("data-video-vkey");
                if (viewkey == null || viewkey.length() == 0) {
                    continue;
                }
                href = String.format(ConfigTable.queryValue("pageUrlTemp"), viewkey);

            }
            VideoInfo videoInfo = new VideoInfo();
            videoInfo.setHref(href);
            videoInfo.setTitle(title);
            videoInfos.add(videoInfo);
            FileUtil.appendUtf8String(href + "\n", listFileName);
        }

        int index = 0;
        for (VideoInfo videoInfo : videoInfos) {
            String title = videoInfo.getTitle().replaceAll("\\pP|\\pS|\\pC|\\pN|\\pZ", "");
            String href = videoInfo.getHref();
            String cacheFilePathName = DigestUtil.md5Hex(href);
            if (!href.startsWith(FileEnums.PROTOCOL)) {
                title = "ERROR ERROR " + title;
            }
            if (PageHistoryTable.isExist(href)) {
                continue;
            }

            log.info(index + FileEnums.FILE_PATH_SEPARATOR + videoInfos.size() + "\t" + title + "\t" + href);

            M3U8Info m3U8Info = new M3U8Info();

            m3U8Info.setCacheFilePathName(cacheFilePathName);
            String cacheFilePath = FileEnums.PATH_PREX + FileEnums.FILE_PATH_SEPARATOR + m3U8Info.getCacheFilePathName();
            m3U8Info.setCacheFilePath(cacheFilePath);

            m3U8Info.setTitle(title);
            m3U8Info.setTitleOrigin(videoInfo.getTitle());
            m3U8Info.setVideoUrl(href);

            VideoUtils.clearVideoCachePath(cacheFilePath);
            FileIOUtils.outputWatchlaterInfo(m3U8Info);

            getVideo(webDriver, m3U8Info);

            index++;
        }
    }


    public static void getVideo(WebDriver webDriver, M3U8Info m3U8Info) throws Exception {
        String title = m3U8Info.getTitle();
        String url = m3U8Info.getVideoUrl();

        if (!url.startsWith(FileEnums.PROTOCOL)) {
            return;
        }

        Thread.sleep(3 * 1000);

        webDriver.get(url);

        Thread.sleep(12 * 1000);

        log.info("page title: {}", webDriver.getTitle());
        log.info("page url: {}", webDriver.getCurrentUrl());

        // === 改用 Chrome Performance 日志替代 BrowserMobProxy HAR ===
        // 在等待期间定期 Drain 缓冲区，避免环形缓冲区溢出导致事件丢失
        StringBuilder networkLogsSb = new StringBuilder();
        long waitStart = System.currentTimeMillis();
        long waitDuration = 30 * 1000L;
        while (System.currentTimeMillis() - waitStart < waitDuration) {
            drainNetworkLogs(webDriver, networkLogsSb);
            Thread.sleep(3000);
        }
        // 最后一次 Drain
        drainNetworkLogs(webDriver, networkLogsSb);

        String cacheFilePath = m3U8Info.getCacheFilePath();
        String logFilePath = cacheFilePath + FileEnums.FILE_PATH_SEPARATOR + title + FileEnums.TXT_FILE_EXTENSION_NAME;

        // 确保日志文件所在目录存在
        FileIOUtils.ensureParentDirExists(logFilePath);

        // 写入日志文件
        String networkLogs = networkLogsSb.toString();
        if (networkLogs.isEmpty()) {
            log.warn("Performance 日志未捕获到 Network 事件 for: {}", title);
            FileIOUtils.outputBrowseLog("{\"note\":\"NO_NETWORK_EVENTS_CAPTURED\"}", logFilePath);
        } else {
            String[] lines = networkLogs.split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    FileIOUtils.outputBrowseLog(line, logFilePath);
                    log.info(line);
                }
            }
        }

        webDriver.get(ConfigTable.queryValue("homeUrl"));

        m3U8Info.setLogFilePath(logFilePath);

        // Get download method from config (default to original if not set)
//        String downloadMethod = ConfigTable.queryValue("downloadMethod");
//        if ("cococut".equalsIgnoreCase(downloadMethod)) {
//            log.info("Using CocoCut method to download video");
//            CocoCutVideoAnalyzer.downloadVideo(m3U8Info);
//        } else {
            log.info("Using original method to download video");
            M3u8Analyze.downloadVideo(m3U8Info);
//        }
    }


}