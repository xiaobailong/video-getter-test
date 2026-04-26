package org.example.selenium.work;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.slf4j.Slf4j;
import org.example.selenium.entity.M3U8Info;
import org.example.selenium.enums.FileEnums;
import org.example.selenium.utils.TextOutputUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
public class CocoCutVideoAnalyzer {

    // Video file extensions to detect
    private static final List<String> VIDEO_EXTENSIONS;
    static {
        List<String> extensions = new ArrayList<>();
        extensions.add("mp4");
        extensions.add("m3u8");
        extensions.add("ts");
        extensions.add("mkv");
        extensions.add("avi");
        extensions.add("wmv");
        extensions.add("flv");
        extensions.add("mov");
        extensions.add("webm");
        VIDEO_EXTENSIONS = Collections.unmodifiableList(extensions);
    }

    // Patterns for detecting video URLs
    private static final Pattern VIDEO_URL_PATTERN = Pattern.compile(
            "https?://[\\w\\-._~:/?#[\\]@!$&'()*+,;=.]+\\.(" +
                    String.join("|", VIDEO_EXTENSIONS) +
                    ")(\\?.*)?", Pattern.CASE_INSENSITIVE
    );

    // Patterns for detecting m3u8 playlists
    private static final Pattern M3U8_PATTERN = Pattern.compile(
            "https?://[\\w\\-._~:/?#[\\]@!$&'()*+,;=.]+\\.m3u8(\\?.*)?", Pattern.CASE_INSENSITIVE
    );

    /**
     * Analyze network traffic like CocoCut extension
     * @param m3U8Info M3U8Info object
     * @return true if videos were found
     */
    public static boolean analyzeNetworkTraffic(M3U8Info m3U8Info) throws Exception {
        log.info("Analyzing network traffic using CocoCut-like method");

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(m3U8Info.getLogFilePath()), StandardCharsets.UTF_8));
        String line;
        List<String> videoUrls = new ArrayList<>();
        List<String> m3u8Urls = new ArrayList<>();

        while ((line = reader.readLine()) != null) {
            JSONObject entry = JSON.parseObject(line);
            String url = entry.getString("url");

            // Check if URL contains video extensions
            if (isVideoUrl(url)) {
                videoUrls.add(url);
                log.info("Found video URL: " + url);
            }

            // Check specifically for m3u8 URLs
            if (isM3u8Url(url)) {
                m3u8Urls.add(url);
                log.info("Found m3u8 URL: " + url);
            }
        }
        reader.close();

        // Save detected URLs
        String detectedUrlsPath = m3U8Info.getCacheFilePath() + FileEnums.FILE_PATH_SEPARATOR + "detected_urls.txt";
        StringBuilder urlsContent = new StringBuilder();
        urlsContent.append("=== Detected Video URLs ===\n");
        videoUrls.forEach(url -> urlsContent.append(url).append("\n"));
        urlsContent.append("\n=== Detected m3u8 URLs ===\n");
        m3u8Urls.forEach(url -> urlsContent.append(url).append("\n"));
        TextOutputUtil.output(urlsContent.toString(), detectedUrlsPath);

        // Process m3u8 URLs first (highest priority)
        if (!m3u8Urls.isEmpty()) {
            return processM3u8Urls(m3U8Info, m3u8Urls);
        }

        // Process direct video URLs
        if (!videoUrls.isEmpty()) {
            return processDirectVideoUrls(m3U8Info, videoUrls);
        }

        log.info("No video URLs detected using CocoCut method");
        return false;
    }

    /**
     * Check if URL is a video URL
     */
    private static boolean isVideoUrl(String url) {
        return VIDEO_URL_PATTERN.matcher(url).matches() ||
                VIDEO_EXTENSIONS.stream().anyMatch(ext ->
                        url.toLowerCase().contains("." + ext.toLowerCase()) &&
                        !url.toLowerCase().contains(".php") &&
                        !url.toLowerCase().contains(".html")
                );
    }

    /**
     * Check if URL is an m3u8 URL
     */
    private static boolean isM3u8Url(String url) {
        return M3U8_PATTERN.matcher(url).matches() ||
                url.toLowerCase().contains(".m3u8");
    }

    /**
     * Process m3u8 URLs
     */
    private static boolean processM3u8Urls(M3U8Info m3U8Info, List<String> m3u8Urls) throws Exception {
        // Use the first m3u8 URL found
        String m3u8Url = m3u8Urls.get(0);
        log.info("Processing m3u8 URL: " + m3u8Url);

        // Download the m3u8 file
        String m3u8FileName = "playlist.m3u8";
        String m3u8FilePath = m3U8Info.getCacheFilePath() + FileEnums.FILE_PATH_SEPARATOR + m3u8FileName;
        HttpUtil.downloadFile(m3u8Url, m3u8FilePath);

        // Set m3u8 info
        m3U8Info.setMasterUrl(m3u8Url);
        m3U8Info.getM3u8ItemUrls().add(m3u8Url);
        m3U8Info.getM3u8ItemFileNames().add(m3u8FileName);

        // Extract URL prefix
        int lastSlashIndex = m3u8Url.lastIndexOf("/");
        if (lastSlashIndex != -1) {
            String urlPrex = m3u8Url.substring(0, lastSlashIndex + 1);
            m3U8Info.setUrlPrex(urlPrex);
        }

        // Download TS files
        downloadM3u8TS(m3U8Info, m3u8FilePath);

        return true;
    }

    /**
     * Process direct video URLs
     */
    private static boolean processDirectVideoUrls(M3U8Info m3U8Info, List<String> videoUrls) throws Exception {
        // Use the first video URL found
        String videoUrl = videoUrls.get(0);
        log.info("Processing direct video URL: " + videoUrl);

        // Extract file name from URL
        String fileName = videoUrl.substring(videoUrl.lastIndexOf("/") + 1);
        if (fileName.contains("?")) {
            fileName = fileName.substring(0, fileName.indexOf("?"));
        }

        // Download the video file
        String videoFilePath = m3U8Info.getCacheFilePath() + FileEnums.FILE_PATH_SEPARATOR + fileName;
        HttpUtil.downloadFile(videoUrl, videoFilePath);

        // Set video info
        m3U8Info.setMasterUrl(videoUrl);
        m3U8Info.getM3u8ItemUrls().add(videoUrl);
        m3U8Info.getM3u8ItemFileNames().add(fileName);

        return true;
    }

    /**
     * Download TS files from m3u8 playlist
     */
    private static void downloadM3u8TS(M3U8Info m3U8Info, String m3u8FilePath) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(m3u8FilePath), StandardCharsets.UTF_8));
        String line;

        String tsFilePath = m3U8Info.getCacheFilePath() + FileEnums.FILE_PATH_SEPARATOR + FileEnums.TF_FILE_PATH_NAME;
        File tsFile = new File(tsFilePath);
        if (!tsFile.exists()) {
            tsFile.mkdirs();
        }

        String urlPrex = m3U8Info.getUrlPrex();
        log.info("URL prefix: " + urlPrex);

        while ((line = reader.readLine()) != null) {
            final String currentLine = line.trim();
            // Skip comments and empty lines
            if (currentLine.startsWith("#") || currentLine.isEmpty()) {
                continue;
            }

            // Handle TS files
            if (currentLine.endsWith(".ts")) {
                String tsUrl = currentLine;
                if (!tsUrl.startsWith("http")) {
                    tsUrl = urlPrex + currentLine;
                }

                try {
                    HttpUtil.downloadFile(tsUrl, tsFilePath);
                    log.info("Downloaded TS file: " + tsUrl);
                } catch (Exception e) {
                    log.error("Failed to download TS file: " + tsUrl, e);
                }
            }
            // Handle other video files
            else if (VIDEO_EXTENSIONS.stream().anyMatch(ext -> currentLine.endsWith("." + ext))) {
                String videoUrl = currentLine;
                if (!videoUrl.startsWith("http")) {
                    videoUrl = urlPrex + currentLine;
                }

                try {
                    HttpUtil.downloadFile(videoUrl, tsFilePath);
                    log.info("Downloaded video file: " + videoUrl);
                } catch (Exception e) {
                    log.error("Failed to download video file: " + videoUrl, e);
                }
            }
        }

        reader.close();
    }

    /**
     * Download video using CocoCut-like method
     */
    public static void downloadVideo(M3U8Info m3U8Info) throws Exception {
        String cacheFilePath = m3U8Info.getCacheFilePath();
        File workFile = new File(cacheFilePath);
        if (!workFile.exists()) {
            log.warn("Cache directory does not exist: " + cacheFilePath);
            return;
        }

        boolean foundVideos = analyzeNetworkTraffic(m3U8Info);

        String m3U8InfoFileName = cacheFilePath + FileEnums.FILE_PATH_SEPARATOR + "cococut_m3u8_info.json";
        TextOutputUtil.output(JSON.toJSONString(m3U8Info, JSONWriter.Feature.PrettyFormat), m3U8InfoFileName);

        if (!foundVideos || m3U8Info.getM3u8ItemFileNames().isEmpty()) {
            log.info("No video data found using CocoCut method!!!");
            return;
        }

        // Use existing TSFileMerge and CMDProcess for merging
        String ffmpegTsFilePath = TSFileMerge.work(m3U8Info);

        if (ffmpegTsFilePath == null) {
            log.info("Video data merging failed using CocoCut method!!!");
            return;
        }

        // Use existing save path
        String savePath = FileEnums.SAVE_PATH + FileEnums.FILE_PATH_SEPARATOR + "cococut_downloads";
        if (!FileUtil.exist(savePath)) {
            FileUtil.mkdir(savePath);
        }
        String videoPath = savePath + FileEnums.FILE_PATH_SEPARATOR + m3U8Info.getTitle() + FileEnums.VIDEO_FILE_EXTENSION_NAME;
        CMDProcess.executive(ffmpegTsFilePath, videoPath);

        if (FileUtil.exist(videoPath)) {
            log.info("Video downloaded successfully using CocoCut method: " + videoPath);
        }
    }
}