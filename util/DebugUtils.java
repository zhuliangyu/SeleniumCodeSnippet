package util;

import org.openqa.selenium.*;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v128.network.Network;
import org.openqa.selenium.devtools.v128.network.model.RequestId;
import org.openqa.selenium.io.FileHandler;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public final class DebugUtils {

    private DebugUtils() {
    }

    /* -------------------- Visual helpers -------------------- */

    public static void highlightAtOffset(WebDriver driver, WebElement base, int offsetX, int offsetY) {
        // 计算元素左上角在页面中的位置
        org.openqa.selenium.Point loc = base.getLocation();
        int absX = loc.getX() + offsetX;
        int absY = loc.getY() + offsetY;

        // 用 JS 在该位置画一个小圆点
        String script = "var dot = document.createElement('div');" +
                "dot.style.position='absolute';" +
                "dot.style.left='" + absX + "px';" +
                "dot.style.top='" + absY + "px';" +
                "dot.style.width='12px';" +
                "dot.style.height='12px';" +
                "dot.style.background='red';" +
                "dot.style.borderRadius='50%';" +
                "dot.style.border='2px solid black';" +
                "dot.style.zIndex='999999';" +
                "document.body.appendChild(dot);";
        ((JavascriptExecutor) driver).executeScript(script);
    }

    public static void highlightAtCoordinates(WebDriver driver, int x, int y) {
        String script = "var dot = document.createElement('div');" +
                "dot.style.position='absolute';" +
                "dot.style.left='" + x + "px';" +
                "dot.style.top='" + y + "px';" +
                "dot.style.width='10px';" +
                "dot.style.height='10px';" +
                "dot.style.background='lime';" +
                "dot.style.border='2px solid black';" +
                "dot.style.borderRadius='50%';" +
                "dot.style.zIndex='999999';" +
                "document.body.appendChild(dot);";
        ((JavascriptExecutor) driver).executeScript(script);
    }

    /** 暂停人工检查（回车继续） 脚本会卡住，你可以手动操作浏览器 */
    public static void pauseHere() {
        System.out.println("Paused. Press ENTER to continue...");
        try {
            System.in.read();
        } catch (IOException ignored) {
        }
    }

    /** 高亮元素（闪烁后还原，不污染样式） */
    public static void highlight(WebDriver driver, WebElement el) {
        ((JavascriptExecutor) driver).executeScript(
                "const e=arguments[0];" +
                        "const b=e.style.background, o=e.style.outline;" +
                        "e.style.outline='3px solid red'; e.style.background='yellow';" +
                        "setTimeout(()=>{e.style.outline=o; e.style.background=b;}, 800);",
                el);
    }

    /** 在元素中心打一个红点（0.8s） */
    public static void markClick(WebDriver driver, WebElement el) {
        ((JavascriptExecutor) driver).executeScript(
                "const r=arguments[0].getBoundingClientRect();" +
                        "const d=document.createElement('div');" +
                        "d.style.cssText='position:fixed;left:'+(r.left+r.width/2-6)+'px;" +
                        "top:'+(r.top+r.height/2-6)+'px;width:12px;height:12px;" +
                        "border-radius:50%;background:red;z-index:2147483647;pointer-events:none;';" +
                        "document.body.appendChild(d); setTimeout(()=>d.remove(),800);",
                el);
    }

    /** 将元素滚动到视口中间 */
    public static void scrollIntoViewCenter(WebDriver driver, WebElement el) {
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center',inline:'center'});", el);
    }

    /* -------------------- Screenshots -------------------- */

    /** 整页截图 */
    public static Path screenshot(WebDriver driver, String filePath) {
        File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        Path dest = Paths.get(filePath);
        try {
            Files.createDirectories(dest.getParent());
            FileHandler.copy(src, dest.toFile());
            return dest;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** 元素截图（Chrome/Firefox 支持） */
    public static Path elementScreenshot(WebElement el, String filePath) {
        File src = el.getScreenshotAs(OutputType.FILE);
        Path dest = Paths.get(filePath);
        try {
            Files.createDirectories(dest.getParent());
            FileHandler.copy(src, dest.toFile());
            return dest;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /* -------------------- Wait & Actions -------------------- */

    /** 等可见，返回元素 */
    public static WebElement waitVisible(WebDriver driver, By by, long seconds) {
        return new WebDriverWait(driver, Duration.ofSeconds(seconds))
                .until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    /** 等可点，返回元素 */
    public static WebElement waitClickable(WebDriver driver, By by, long seconds) {
        return new WebDriverWait(driver, Duration.ofSeconds(seconds))
                .until(ExpectedConditions.elementToBeClickable(by));
    }

    /** 等遮罩/加载层消失（可传多个选择器，任一存在则等待其消失） */
    public static void waitOverlaysGone(WebDriver driver, long seconds, String... cssSelectors) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(seconds));
        for (String sel : cssSelectors) {
            try {
                wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(sel)));
            } catch (TimeoutException ignored) {
            }
        }
    }

    /** 安全点击：高亮 → 滚动 → 等可点 → 点击（失败用 JS 兜底） */
    public static void clickWhenReady(WebDriver driver, By by, long seconds) {
        WebElement el = waitClickable(driver, by, seconds);
        scrollIntoViewCenter(driver, el);
        highlight(driver, el);
        markClick(driver, el);
        try {
            el.click();
        } catch (ElementClickInterceptedException | WebDriverException e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
        }
    }

    /** 安全输入：等可见 → 滚动 → 全选删除 → 输入 → 校验 */
    public static void typeWhenVisible(WebDriver driver, By by, String text, long seconds) {
        WebElement input = waitVisible(driver, by, seconds);
        scrollIntoViewCenter(driver, input);
        highlight(driver, input);
        input.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE);
        input.sendKeys(text);
        new WebDriverWait(driver, Duration.ofSeconds(seconds))
                .until(d -> text.equals(d.findElement(by).getAttribute("value")));
    }

    /** 等 Angular 稳定（如不可用则直接通过） */
    public static void waitAngularStable(WebDriver driver, long seconds) {
        new WebDriverWait(driver, Duration.ofSeconds(seconds)).until(d -> {
            Object ok = ((JavascriptExecutor) d).executeScript(
                    "return (window.getAllAngularTestabilities?" +
                            "window.getAllAngularTestabilities().every(t=>t.isStable()):true);");
            return Boolean.TRUE.equals(ok);
        });
    }

    /** 等旧元素失效（避免 stale） */
    public static void waitStaleness(WebDriver driver, WebElement el, long seconds) {
        new WebDriverWait(driver, Duration.ofSeconds(seconds))
                .until(ExpectedConditions.stalenessOf(el));
    }

    /** 自定义等待：直到函数返回非 null / true */
    public static <T> T waitUntil(WebDriver driver, long seconds, Function<WebDriver, T> condition) {
        return new WebDriverWait(driver, Duration.ofSeconds(seconds)).until(condition);
    }

    /* -------------------- Network (CDP) -------------------- */

    /** 启用 Network 日志，返回一个简单的监听句柄 */
    public static NetworkLogger startNetworkLogger(WebDriver driver, String urlKeyword) {
        DevTools dt = ((HasDevTools) driver).getDevTools();
        dt.createSession();
        dt.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));

        Map<RequestId, String> watch = new HashMap<>();
        dt.addListener(Network.requestWillBeSent(), e -> {
            String url = e.getRequest().getUrl();
            if (urlKeyword == null || url.contains(urlKeyword)) {
                System.out.println("[REQ] " + e.getRequest().getMethod() + " " + url);
                watch.put(e.getRequestId(), url);
            }
        });
        dt.addListener(Network.responseReceived(), e -> {
            if (watch.containsKey(e.getRequestId())) {
                System.out.println("[RES] " + watch.get(e.getRequestId()) +
                        " -> " + e.getResponse().getStatus());
            }
        });
        return new NetworkLogger(dt, watch);
    }

    public static final class NetworkLogger {
        private final DevTools devTools;
        private final Map<RequestId, String> watch;

        private NetworkLogger(DevTools dt, Map<RequestId, String> w) {
            this.devTools = dt;
            this.watch = w;
        }

        public void stop() {
            try {
                devTools.send(Network.disable());
            } catch (Exception ignored) {
            }
            watch.clear();
        }
    }

    /* -------------------- Helpers -------------------- */

    /** 用 Actions 模拟“移动→停顿→点击” */
    public static void humanClick(WebDriver driver, WebElement el) {
        new Actions(driver).moveToElement(el).pause(Duration.ofMillis(120)).click().perform();
    }

    /** JS 触发 input 的 change 事件（自定义复选框常用） */
    public static void setCheckedAndChange(WebDriver driver, WebElement checkbox, boolean checked) {
        ((JavascriptExecutor) driver).executeScript(
                "const cb=arguments[0], v=arguments[1]; cb.checked=v;" +
                        "cb.dispatchEvent(new Event('change',{bubbles:true}));",
                checkbox, checked);
    }
}
