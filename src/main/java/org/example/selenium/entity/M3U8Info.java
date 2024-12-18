package org.example.selenium.entity;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

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
}
