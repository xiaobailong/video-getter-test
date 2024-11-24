package org.example.selenium.main;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.client.ClientUtil;
import net.lightbody.bmp.proxy.CaptureType;
import org.example.selenium.db.ConfigTable;
import org.example.selenium.enums.ChromeDriverEnums;
import org.example.selenium.page.Watchlater;
import org.example.selenium.page.Login;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.CapabilityType;

import java.net.Inet4Address;
import java.net.InetSocketAddress;

@Slf4j
public class SpiderMain {

    public static void main(String[] args) throws Exception {

        System.getProperties().setProperty("http.proxyHost", "127.0.0.1");
        System.getProperties().setProperty("http.proxyPort", "7897");
        System.getProperties().setProperty("https.proxyHost", "127.0.0.1");
        System.getProperties().setProperty("https.proxyPort", "7897");

        login();
    }

    private static void login() throws Exception {

        String homeUrl = ConfigTable.queryValue("homeUrl");

        BrowserMobProxy proxy = new BrowserMobProxyServer();
        proxy.setChainedProxy(new InetSocketAddress("127.0.0.1", 7897));
        proxy.start(8080);
        log.info(JSON.toJSONString(proxy));

        Proxy seleniumProxy = ClientUtil.createSeleniumProxy(proxy);
        String hostIp = Inet4Address.getLocalHost().getHostAddress();
        seleniumProxy.setHttpProxy(hostIp + ":" + proxy.getPort());
        seleniumProxy.setSslProxy(hostIp + ":" + proxy.getPort());
        log.info(JSON.toJSONString(seleniumProxy));

        System.getProperties().setProperty("webdriver.chrome.driver", ChromeDriverEnums.Home);
        System.setProperty("webdriver.chrome.whitelistedIps", "");

        // 设置浏览器参数
        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--no-sandbox");//禁用沙箱
        chromeOptions.addArguments("--disable-dev-shm-usage");//禁用开发者shm
        chromeOptions.addArguments("--disable-audio-output");//所有声音静音
//        chromeOptions.addArguments("blink-settings=imagesEnabled=false");//不加载图片, 提升速度
//        chromeOptions.addArguments("--headless"); //无头浏览器，这样不会打开浏览器窗口
//        chromeOptions.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));

        chromeOptions.addArguments("--disable-web-security");
        chromeOptions.addArguments("--allow-insecure-localhost");
        chromeOptions.addArguments("--ignore-urlfetcher-cert-requests");
        chromeOptions.setCapability(CapabilityType.PROXY, seleniumProxy);
        chromeOptions.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);//接受非安全的连接
        proxy.enableHarCaptureTypes(CaptureType.REQUEST_CONTENT, CaptureType.RESPONSE_CONTENT);

        WebDriver webDriver = new ChromeDriver(chromeOptions);

        // 设置浏览器的位置：
        Point point = new Point(2000, 0);
        webDriver.manage().window().setPosition(point);
        Dimension dimension=new Dimension(1200,1000);
        webDriver.manage().window().setSize(dimension);

        webDriver.get(homeUrl);

        Login.login(webDriver);

        Watchlater.go2LastView(webDriver, proxy);

        Thread.sleep(10 * 1000l);

        webDriver.quit();
        proxy.abort();
    }

}
