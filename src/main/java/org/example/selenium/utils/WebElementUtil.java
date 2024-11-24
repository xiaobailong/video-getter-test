package org.example.selenium.utils;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;

@Slf4j
public class WebElementUtil {

    public static void getChilds(WebDriver webDriver, WebElement webElement) {
        List<WebElement> childs = webElement.findElements(By.xpath("./child::*"));
        if (childs.size() == 0) {
            String text = webElement.getText();
            String accessibleName = webElement.getAccessibleName();
            String ariaRole = webElement.getAriaRole();
            String tagName = webElement.getTagName();
            String textContent = webElement.getAttribute("textContent").replaceAll("\n", "");
            String innerText = webElement.getAttribute("innerText");
            log.info("Text=<" + text + ">\tAccessibleName=<" + accessibleName
                    + ">\tAriaRole=<" + ariaRole + ">\tTagName=<" + tagName
                    + ">\ttextContent=<" + textContent + ">");
//                    + ">\tinnerText=<" + webElement.getAttribute("innerText") + ">");

            JavascriptExecutor executor = (JavascriptExecutor) webDriver;
            Object arguments = executor.executeScript("var items = {}; for (index = 0; index < arguments[0].attributes.length; ++index) { items[arguments[0].attributes[index].name] = arguments[0].attributes[index].value }; return items;", webElement);
            log.info(arguments.toString());
            if (arguments != null && arguments.toString().contains("text/javascript")) {
                Object jsContent = executor.executeScript(textContent, webElement);
                if (jsContent != null) {
                    log.info("jsContent: " + jsContent.toString());
                }
            }
            return;
        }
        for (WebElement webElementChild : childs) {
            getChilds(webDriver, webElementChild);
        }
    }

    /**
     * 判断某个元素是否存在
     */
    public static boolean isJudgingElement(WebDriver webDriver, By by) {
        try {
            webDriver.findElement(by);
            return true;
        } catch (Exception e) {
//            log.info("不存在此元素");
            return false;
        }
    }
}
