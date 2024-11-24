package org.example.selenium.utils;

import lombok.extern.slf4j.Slf4j;
import org.example.selenium.entity.UrlInfo;

@Slf4j
public class CustomStringUtils {

    public static UrlInfo parseFileUrlInfo(String line) {
        UrlInfo urlInfo = new UrlInfo();
        String fileName = line;
        if (line.contains("?")) {
            fileName = line.substring(0, line.indexOf("?"));
            urlInfo.setFileName(fileName);
        }
        if (line.contains("&")) {
            String[] parmeters = line.substring(line.indexOf("?") + 1).split("&");
            for (String parmeter : parmeters) {
                if (parmeter.contains("=")) {
                    String[] parmeterKV = parmeter.split("=");
                    if (parmeterKV.length > 1) {
                        urlInfo.getParameters().put(parmeterKV[0], parmeterKV[1]);
                    } else {
                        urlInfo.getParameters().put(parmeter.replaceAll("=", ""), parmeter);
                    }
                }
            }
        }
        return urlInfo;
    }

    public static UrlInfo parseUrlInfo(String line) {
        UrlInfo urlInfo = new UrlInfo();
        String protocol = "";
        if (line.contains("://")) {
            protocol = line.substring(0, line.indexOf("://"));
        }
        urlInfo.setProtocol(protocol);
        String urlContent = line.substring(line.indexOf("://") + "://".length());
        if (urlContent.contains("/")) {
            String[] urlPaths = null;
            if (urlContent.contains("?")) {
                urlPaths = urlContent.substring(0, urlContent.indexOf("?")).split("/");
            } else {
                urlPaths = urlContent.split("/");
            }
            for (String urlPath : urlPaths) {
                urlInfo.getUrlPaths().add(urlPath);
            }
            if (urlInfo.getUrlPaths().size() > 0) {
                urlInfo.setFileName(urlInfo.getUrlPaths().get(urlInfo.getUrlPaths().size() - 1));
            }
        }
        if (urlContent.contains("?")) {
            String parmetersContent = urlContent.substring(urlContent.indexOf("?") + 1);
            if (parmetersContent.contains("&")) {
                String[] parmeters = parmetersContent.split("&");
                for (String parmeter : parmeters) {
                    if (parmeter.contains("=")) {
                        String[] parmeterKV = parmeter.split("=");
                        if (parmeterKV.length > 1) {
                            urlInfo.getParameters().put(parmeterKV[0], parmeterKV[1]);
                        } else {
                            urlInfo.getParameters().put(parmeter.replaceAll("=", ""), parmeter);
                        }
                    }
                }
            } else if (parmetersContent.contains("=")) {
                String[] parmeterKV = parmetersContent.split("=");
                if (parmeterKV.length > 1) {
                    urlInfo.getParameters().put(parmeterKV[0], parmeterKV[1]);
                } else {
                    urlInfo.getParameters().put(parmetersContent.replaceAll("=", ""), parmetersContent);
                }
            }
        }
        return urlInfo;
    }

    public static String getUrlPrex(String line, String key) {
        int index = line.length();
        if (line.contains(key)) {
            index = line.indexOf(key);
        }else {
            index = line.lastIndexOf("/");
        }
        log.info(line + "\t" + key + "\t" + index);
        log.info(line.substring(0, index));
        return line.substring(0, index);
    }

}
