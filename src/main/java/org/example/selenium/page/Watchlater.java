package org.example.selenium.page;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.core.har.Har;
import org.example.selenium.db.ConfigTable;
import org.example.selenium.db.PageHistoryTable;
import org.example.selenium.entity.M3U8Info;
import org.example.selenium.entity.VideoInfo;
import org.example.selenium.enums.DateTimeFormatEnum;
import org.example.selenium.enums.FileEnums;
import org.example.selenium.enums.FilePathEnums;
import org.example.selenium.utils.FileIOUtils;
import org.example.selenium.utils.VideoUtils;
import org.example.selenium.work.M3u8Analyze;
import org.openqa.selenium.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Watchlater {

    public static void getVideoList(WebDriver webDriver, BrowserMobProxy proxy) throws Exception {

        Thread.sleep(3 * 1000);

        webDriver.get(ConfigTable.queryValue("watchlater"));

        Thread.sleep(3 * 1000);

        String js2 = "window.scrollBy(0,1000);";
        for (int i = 0; i < 10; i++) {
            ((JavascriptExecutor) webDriver).executeScript(js2);
            Thread.sleep(5 * 1000);
            log.info("scroll index: " + i);
        }

        Thread.sleep(3 * 1000);

        WebElement videoPlaylist = webDriver.findElement(By.id("videoPlaylist"));
        List<WebElement> videos = videoPlaylist.findElements(By.cssSelector("[class='pcVideoListItem js-pop videoblock videoBox  canEdit']"));

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

            getVideo(webDriver, proxy, m3U8Info);

            index++;
        }
    }


    public static void getVideo(WebDriver webDriver, BrowserMobProxy proxy, M3U8Info m3U8Info) throws Exception {
        String title = m3U8Info.getTitle();
        String url = m3U8Info.getVideoUrl();

        if (!url.startsWith(FileEnums.PROTOCOL)) {
            return;
        }

        proxy.newHar(title);

        Thread.sleep(3 * 1000);

        webDriver.get(url);

        Thread.sleep(40 * 1000);

        String cacheFilePath = m3U8Info.getCacheFilePath();
        String logFilePath = cacheFilePath + FileEnums.FILE_PATH_SEPARATOR + title + FileEnums.TXT_FILE_EXTENSION_NAME;

        Har har = proxy.getHar();
        har.getLog().getEntries().forEach(entry ->
                {
                    try {
                        FileIOUtils.outputBrowseLog(JSON.toJSONString(entry), logFilePath);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        webDriver.get(ConfigTable.queryValue("homeUrl"));

        m3U8Info.setLogFilePath(logFilePath);

        M3u8Analyze.downloadVideo(m3U8Info);
    }


}
