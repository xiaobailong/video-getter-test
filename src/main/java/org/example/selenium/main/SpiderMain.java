package org.example.selenium.main;

import lombok.extern.slf4j.Slf4j;
import org.example.selenium.db.ConfigTable;
import org.example.selenium.enums.ChromeDriverEnums;
import org.example.selenium.page.Watchlater;
import org.example.selenium.page.Login;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

@Slf4j
public class SpiderMain {

    public static void main(String[] args) throws Exception {
        work();
    }

    private static void work() throws Exception {

        String homeUrl = ConfigTable.queryValue("homeUrl");

        // 配置 ChromeDriver
        System.getProperties().setProperty("webdriver.chrome.driver", ChromeDriverEnums.Home);
        System.setProperty("webdriver.chrome.whitelistedIps", "");

        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments(
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-audio-output",
                "--disable-web-security",
                "--allow-insecure-localhost",
                "--ignore-urlfetcher-cert-requests",
                "--ignore-certificate-errors",
                "--allow-running-insecure-content",
                // === 禁用 Chrome 自带广告拦截警告（配合 CDP 广告屏蔽） ===
                "--disable-domain-reliability",
                "--disable-features=InterestFeedContentSuggestions,ChromeWhatsNewUI"
        );

        // === 启用 Performance 日志（CDP Network 域）捕获网络请求 ===
        LoggingPreferences loggingPrefs = new LoggingPreferences();
        loggingPrefs.enable(LogType.PERFORMANCE, Level.ALL);
        chromeOptions.setCapability("goog:loggingPrefs", loggingPrefs);

        // 不设置代理，Chrome 直接走系统网络（Clash 透明代理）
        chromeOptions.setCapability("acceptInsecureCerts", true);

        WebDriver webDriver = new ChromeDriver(chromeOptions);

        // === 使用 CDP Network.setBlockedURLs 拦截广告 ===
        // 在首次导航前执行，Chrome 会在网络层直接拒绝匹配的请求，不会下载广告资源
        try {
            List<String> adPatterns = Arrays.asList(
                    // Google 广告联盟
                    "*.doubleclick.net/*",
                    "*.googlesyndication.com/*",
                    "*.googleadservices.com/*",
                    "*.googleanalytics.com/*",
                    "*.googletagmanager.com/*",
                    "*.googletagservices.com/*",
                    "*.adservice.google.com/*",
                    "*.pagead2.googlesyndication.com/*",
                    "*.adservice.google.*",
                    // 通用广告网络
                    "*.adserver.*",
                    "*.exoclick.com/*",
                    "*.popads.net/*",
                    "*.trafficjunky.com/*",
                    "*.juicyads.com/*",
                    "*.adsterra.com/*",
                    "*.propellerads.com/*",
                    "*.popunder.net/*",
                    "*.adcash.com/*",
                    "*.adbrite.com/*",
                    "*.advertising.com/*",
                    "*.adreactor.com/*",
                    "*.adultad.net/*",
                    // 弹窗/悬浮广告
                    "*/popunder.*",
                    "*popup*",
                    "*banner*",
                    "*advertisement*",
                    "*adblock*",
                    // 第三方追踪
                    "*.scorecardresearch.com/*",
                    "*.quantserve.com/*",
                    "*.comscore.com/*",
                    "*.hotjar.com/*",
                    "*.crazyegg.com/*"
            );
            Map<String, Object> params = new HashMap<>();
            params.put("urls", adPatterns);
            ((ChromeDriver) webDriver).executeCdpCommand("Network.setBlockedURLs", params);
            log.info("已拦截 {} 条广告域名模式", adPatterns.size());
        } catch (Exception e) {
            log.warn("设置 CDP 广告拦截失败（不影响运行）: {}", e.getMessage());
        }

        // 首次导航
        webDriver.get(homeUrl);

        Login.login(webDriver);

        Watchlater.getVideoList(webDriver);

        Thread.sleep(10 * 1000L);

        webDriver.quit();
    }
}