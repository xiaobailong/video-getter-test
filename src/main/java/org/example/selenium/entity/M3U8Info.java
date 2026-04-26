package org.example.selenium.entity;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class M3U8Info {

    private String title;

    private String titleOrigin;

    private String masterFileKeys = "master.m3u8";

    private String masterFileName;

    private String masterUrl;

    private String logFilePath;

    private String videoUrl;

    private String cacheFilePath;

    private String urlPrex;

    private String cacheFilePathName;

    private List<String> m3u8ItemUrls = new ArrayList<>();
    private List<String> m3u8ItemFileNames = new ArrayList<>();

    /**
     * 从 Performance Log 中捕获到的浏览器实际请求的 TS 段 URL（含有效 CDN 签名）
     * key=TS文件名（如 seg-1-v1-a1.ts），value=完整URL（含 validfrom/validto/hash）
     */
    private Map<String, String> capturedTsUrls = new HashMap<>();
}