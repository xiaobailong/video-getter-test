package org.example.selenium.page;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.example.selenium.db.ConfigTable;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Slf4j
public class Login {

    private static final String COOKIE_DB_KEY = "loginCookies";

    /**
     * 检查当前页面是否已登录（通过查找登录按钮是否存在）
     * 若已登录则直接返回 true，不需要执行登录流程
     */
    public static boolean isLoggedIn(WebDriver webDriver) {
        try {
            // 尝试查找登录按钮 — 如果找不到（或不可见）说明可能已登录
            WebElement loginLink = webDriver.findElement(By.id("headerLoginLink"));
            // 能找到登录按钮 → 未登录
            if (loginLink != null && loginLink.isDisplayed()) {
                log.info("[Login] 发现 headerLoginLink 按钮，当前未登录");
                return false;
            }
        } catch (Exception e) {
            // findElement 抛出 NoSuchElementException → 登录按钮不存在 → 已登录
            log.info("[Login] 未找到 headerLoginLink（可能已登录），page title: {}", webDriver.getTitle());
        }

        // 二次验证：检查用户名相关的元素（多个网站结构，确保准确）
        try {
            // 部分主题下可能是 userAvatar 或 usernameLink
            webDriver.findElement(By.cssSelector("[class*='usernameLink'],[class*='userAvatar'],[class*='headerUserLink']"));
            log.info("[Login] 找到用户信息元素，确认已登录");
            return true;
        } catch (Exception e) {
            log.debug("[Login] 未找到用户信息元素：{}", e.getMessage());
        }

        // 如果没有登录按钮，也没有用户元素，保守认为已登录
        return true;
    }

    /**
     * 将浏览器 Cookie 转为可序列化的 JSONArray（手动序列化，兼容 Selenium Cookie 类）
     */
    private static JSONArray cookiesToJsonArray(Set<Cookie> cookies) {
        JSONArray arr = new JSONArray(cookies.size());
        for (Cookie c : cookies) {
            JSONObject obj = new JSONObject();
            obj.put("name", c.getName());
            obj.put("value", c.getValue());
            obj.put("domain", c.getDomain());
            obj.put("path", c.getPath());
            obj.put("isSecure", c.isSecure());
            obj.put("isHttpOnly", c.isHttpOnly());
            if (c.getExpiry() != null) {
                obj.put("expiry", c.getExpiry().getTime());
            }
            obj.put("sameSite", c.getSameSite());
            arr.add(obj);
        }
        return arr;
    }

    /**
     * 将 JSONArray 反序列化为 Set<Cookie>
     */
    private static Set<Cookie> jsonArrayToCookies(JSONArray arr) {
        Set<Cookie> cookies = new HashSet<>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String name = obj.getString("name");
            String value = obj.getString("value");
            String domain = obj.getString("domain");
            String path = obj.getString("path");
            boolean isSecure = obj.getBooleanValue("isSecure");
            boolean isHttpOnly = obj.getBooleanValue("isHttpOnly");
            Date expiry = null;
            if (obj.containsKey("expiry") && obj.getLong("expiry") != null) {
                expiry = new Date(obj.getLong("expiry"));
            }
            String sameSite = obj.getString("sameSite");

            Cookie cookie = new Cookie.Builder(name, value)
                    .domain(domain)
                    .path(path)
                    .isSecure(isSecure)
                    .isHttpOnly(isHttpOnly)
                    .expiresOn(expiry)
                    .sameSite(sameSite)
                    .build();
            cookies.add(cookie);
        }
        return cookies;
    }

    /**
     * 将浏览器的当前 cookies 序列化并保存到数据库 (loginCookies)
     */
    public static void saveCookies(WebDriver webDriver) {
        try {
            Set<Cookie> cookies = webDriver.manage().getCookies();
            JSONArray arr = cookiesToJsonArray(cookies);
            ConfigTable.upsert(COOKIE_DB_KEY, arr.toJSONString());
            log.info("[Login] 已保存 {} 个 cookies 到数据库 [{}]", cookies.size(), COOKIE_DB_KEY);
        } catch (Exception e) {
            log.error("[Login] 保存 cookies 失败", e);
        }
    }

    /**
     * 从数据库加载 cookies 并注入浏览器
     * @return true 表示成功加载并注入 cookies，false 表示 cookie 不存在或加载失败
     */
    public static boolean loadCookies(WebDriver webDriver) {
        String json = ConfigTable.queryValue(COOKIE_DB_KEY);
        if (json == null || json.isEmpty() || "null".equals(json)) {
            log.info("[Login] 数据库中无 cookie 数据 [{}]", COOKIE_DB_KEY);
            return false;
        }

        try {
            JSONArray arr = JSONArray.parse(json);
            Set<Cookie> cookies = jsonArrayToCookies(arr);

            if (cookies == null || cookies.isEmpty()) {
                log.warn("[Login] 数据库 cookie 数据为空");
                return false;
            }

            // 注入 cookies（不需要重新导航，浏览器已经在目标域名下）
            // SpiderMain 已经执行了 webDriver.get(homeUrl)，直接在当前页面注入
            for (Cookie cookie : cookies) {
                try {
                    webDriver.manage().addCookie(cookie);
                } catch (Exception e) {
                    log.debug("[Login] 注入 cookie 失败: {}={}, 原因: {}", cookie.getName(), cookie.getValue(), e.getMessage());
                }
            }

            log.info("[Login] 已从数据库注入 {} 个 cookies", cookies.size());

            // 刷新页面让 cookies 生效（使用 refresh 而非新的 webDriver.get()，避免重复导航）
            webDriver.navigate().refresh();
            Thread.sleep(3000);

            // 验证 cookies 是否有效
            boolean loggedIn = isLoggedIn(webDriver);
            if (loggedIn) {
                log.info("[Login] cookies 有效，无需重新登录");
            } else {
                log.warn("[Login] cookies 已失效，需要重新登录");
            }
            return loggedIn;

        } catch (Exception e) {
            log.error("[Login] 加载 cookies 失败", e);
            return false;
        }
    }

    /**
     * 执行登录流程（输入账号密码提交）
     */
    public static void doLogin(WebDriver webDriver) throws Exception {
        log.info("[Login] 开始执行登录流程...");

        log.info("[Login] page title: {}", webDriver.getTitle());
        log.info("[Login] page url: {}", webDriver.getCurrentUrl());

        // 等待手动登录完成
        Thread.sleep(60000);
    }

    /**
     * 统一登录入口：先尝试 cookies，失效则重新登录并保存 cookies
     */
    public static void login(WebDriver webDriver) throws Exception {
        log.info("[Login] page title: {}", webDriver.getTitle());
        log.info("[Login] page url: {}", webDriver.getCurrentUrl());

        // 步骤 1：尝试从文件加载 cookies 并验证
        boolean cookieValid = loadCookies(webDriver);
        if (cookieValid) {
            return; // cookies 有效，跳过登录
        }

        // 步骤 2：cookies 无效 / 不存在，执行完整登录流程
        doLogin(webDriver);

        // 步骤 3：登录成功后保存 cookies
        saveCookies(webDriver);

        log.info("[Login] 登录流程完成");
    }
}