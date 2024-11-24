package org.example.selenium.page;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson2.JSON;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.filters.RequestFilter;
import net.lightbody.bmp.util.HttpMessageContents;
import net.lightbody.bmp.util.HttpMessageInfo;
import org.example.selenium.db.ConfigTable;
import org.example.selenium.db.PageHistoryTable;
import org.example.selenium.entity.M3U8Info;
import org.example.selenium.entity.VideoInfo;
import org.example.selenium.enums.FileEnums;
import org.example.selenium.utils.TextFileOutputUtil;
import org.example.selenium.utils.WebElementUtil;
import org.example.selenium.work.M3u8Analyze;
import org.openqa.selenium.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Watchlater {

    public static void go2LastView(WebDriver webDriver, BrowserMobProxy proxy) throws Exception {

        Thread.sleep(3 * 1000);

        webDriver.get(ConfigTable.queryValue("watchlater"));

        Thread.sleep(3 * 1000);

        String js2 = "window.scrollBy(0,1000);";
        for (int i = 0; i < 5; i++) {
            ((JavascriptExecutor) webDriver).executeScript(js2);
            Thread.sleep(3 * 1000);
            log.info("scroll index: " + i);
        }

        Thread.sleep(3 * 1000);

        WebElement videoPlaylist = webDriver.findElement(By.id("videoPlaylist"));
        List<WebElement> videos = videoPlaylist.findElements(By.cssSelector("[class='pcVideoListItem js-pop videoblock videoBox  canEdit']"));

        List<VideoInfo> videoInfos = new ArrayList<>();
        for (WebElement element : videos) {
            WebElement titleWebElement = element.findElement(By.className("title"));
            String title = titleWebElement.getText();
            String href = titleWebElement.findElement(By.tagName("a")).getAttribute("href");
//            log.info(title + "\t" + href);
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
        }

        int index = 0;
        for (VideoInfo videoInfo : videoInfos) {
            String title = videoInfo.getTitle().replaceAll("\\pP|\\pS|\\pC", "").replaceAll("\\pZ", "_");
            String href = videoInfo.getHref();
            if (!href.startsWith(FileEnums.PROTOCOL)) {
                title = "ERROR ERROR " + title;
            }
            if (PageHistoryTable.isExist(href)) {
                continue;
            }
            if (!isVideoExists(videoInfo.getTitle())) {
                clearHistoryWorkPath(title);
                log.info(index + FileEnums.FILE_PATH_SEPARATOR + videoInfos.size() + "\t" + title + "\t" + href);
                outputWatchlaterInfo(href, title);
                M3U8Info m3U8Info = new M3U8Info();
                m3U8Info.setTitle(title);
                m3U8Info.setTitleOrigin(videoInfo.getTitle());
                m3U8Info.setVideoUrl(href);
                getVideo(webDriver, proxy, m3U8Info);
            }
            index++;
        }
    }

    public static void clearHistoryWorkPath(String title) {
        String path0 = FileEnums.PATH_PREX + File.separator + title;
        File workFilePath0 = new File(path0);
        if (workFilePath0.exists()) {
            FileUtil.del(workFilePath0);
            workFilePath0.mkdirs();
        }
    }

    public static boolean isVideoExists(String title) {
        File file0 = new File(FileEnums.PATH_PREX + FileEnums.FILE_PATH_SEPARATOR + title + FileEnums.VIDEO_FILE_EXTENSION_NAME);

        File file1 = new File(FileEnums.PATH_PREX + FileEnums.FILE_PATH_SEPARATOR
                + title.replaceAll("\\pP|\\pS|\\pC", "") + FileEnums.VIDEO_FILE_EXTENSION_NAME);

        File file2 = new File(FileEnums.PATH_PREX + FileEnums.FILE_PATH_SEPARATOR
                + title.replaceAll("\\pP|\\pS|\\pC", "").replaceAll("\\pZ", "_")
                + FileEnums.VIDEO_FILE_EXTENSION_NAME);

        return file0.exists() || file1.exists() || file2.exists();
    }

    public static void getVideo(WebDriver webDriver, BrowserMobProxy proxy, M3U8Info m3U8Info) throws Exception {
        String title = m3U8Info.getTitle();
        String url = m3U8Info.getVideoUrl();
        if (!url.startsWith(FileEnums.PROTOCOL)) {
            return;
        }

//        webDriver.switchTo().newWindow(WindowType.TAB);

        //必须在WebDriver.get(uri)之前调用
        proxy.newHar(title);

        Thread.sleep(3 * 1000);

        webDriver.get(url);

        Thread.sleep(40 * 1000);

//        if (WebElementUtil.isJudgingElement(webDriver, By.xpath("/html/body/div[6]/div[2]/div[5]/div[2]/div[1]/div[1]/div[1]/div/div/div[22]"))) {
//            webDriver.findElement(By.xpath("/html/body/div[6]/div[2]/div[5]/div[2]/div[1]/div[1]/div[1]/div/div/div[22]")).click();
//        }

        // get the HAR data
        Har har = proxy.getHar();
        String logFilePath = FileEnums.PATH_PREX + FileEnums.FILE_PATH_SEPARATOR + title
                + FileEnums.FILE_PATH_SEPARATOR + title + FileEnums.TXT_FILE_EXTENSION_NAME;
        har.getLog().getEntries().forEach(entry ->
                {
                    try {
//                        log.info(JSON.toJSONString(entry));
                        outputBrowseLog(JSON.toJSONString(entry), logFilePath);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        webDriver.get(ConfigTable.queryValue("homeUrl"));

        m3U8Info.setLogFilePath(logFilePath);
        M3u8Analyze.downloadVideo(m3U8Info);
    }


    public static void outputBrowseLog(String content, String logFilePathStr) throws Exception {
        File logFilePath = new File(logFilePathStr);
        if (!logFilePath.getParentFile().exists()) {
            logFilePath.getParentFile().mkdirs();
        }
        TextFileOutputUtil.output(content, logFilePathStr);
    }

    public static void outputWatchlaterInfo(String content, String title) throws Exception {
        File logFilePath = new File(FileEnums.PATH_PREX + FileEnums.FILE_PATH_SEPARATOR + title);
        if (!logFilePath.exists()) {
            logFilePath.mkdirs();
        }
        TextFileOutputUtil.output(content, FileEnums.PATH_PREX + FileEnums.FILE_PATH_SEPARATOR
                + title + FileEnums.FILE_PATH_SEPARATOR + title + FileEnums.URL_FILE_EXTENSION_NAME);
    }

}
