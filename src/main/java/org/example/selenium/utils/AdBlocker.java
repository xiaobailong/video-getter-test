package org.example.selenium.utils;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class AdBlocker {

    /**
     * 广告域名/URL 模式列表
     */
    private static final List<String> AD_PATTERNS = Arrays.asList(
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

    /**
     * 对当前 CDP session 设置广告 URL 拦截。
     * 每次 webDriver.get() 导航到新页面前调用，确保拦截规则始终生效。
     */
    public static void apply(WebDriver webDriver) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("urls", AD_PATTERNS);
            ((ChromeDriver) webDriver).executeCdpCommand("Network.setBlockedURLs", params);
            log.debug("广告拦截已应用 ({} 条规则)", AD_PATTERNS.size());
        } catch (Exception e) {
            log.warn("广告拦截设置失败: {}", e.getMessage());
        }
    }
}