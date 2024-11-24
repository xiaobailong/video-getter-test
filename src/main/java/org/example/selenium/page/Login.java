package org.example.selenium.page;

import org.example.selenium.db.ConfigTable;
import org.example.selenium.utils.WebElementUtil;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class Login {

    public static void login(WebDriver webDriver) throws Exception {
        Thread.sleep(5 * 1000l);

//        try {
//            webDriver.findElement(By.cssSelector("[class='cbPrimaryCTA cbButton']")).click();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        if (WebElementUtil.isJudgingElement(webDriver, By.xpath("/html/body/div[4]/div/div/button"))) {
            webDriver.findElement(By.xpath("/html/body/div[4]/div/div/button")).click();
        }

        webDriver.findElement(By.id("headerLoginLink")).click();
        Thread.sleep(1000);

        webDriver.findElement(By.xpath("/html/body/div[6]/header/div[1]/div/div[3]/div/div/div[1]/a[2]/div")).click();
        Thread.sleep(1000);
//        webDriver.findElement(By.xpath("/html/body/div[6]/header/div[1]/div/div[3]/div/div/div[1]/a[2]/div")).click();
//        Thread.sleep(1000);

        WebElement accountWebElements = webDriver.findElement(By.id("usernameModal"));
        accountWebElements.clear();
        accountWebElements.sendKeys(ConfigTable.queryValue("username"));

        Thread.sleep(1000);

        WebElement passwordWebElements = webDriver.findElement(By.id("passwordModal"));
        passwordWebElements.clear();
        passwordWebElements.sendKeys(ConfigTable.queryValue("password"));

        Thread.sleep(1000);

        webDriver.findElement(By.id("signinSubmit")).click();
    }
}
