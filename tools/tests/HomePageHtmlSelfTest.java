package com.xinyv.median;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Arrays;

public final class HomePageHtmlSelfTest {
    public static void main(String[] args) throws Exception {
        String basic = HomePage.html("google", Collections.<BrowserDataStore.Bookmark>emptyList(), false, "safe-token");
        require(basic.contains("median-home-token"), "trust marker missing");
        require(basic.contains("prompt(t,u)") && basic.contains("content='safe-token'") &&
                        basic.contains("[name=median-home-token]"),
                "token-authenticated homepage command channel missing");
        require(!basic.contains("location.href='median://"),
                "homepage command must not start a disposable WebView navigation");
        require(!basic.contains("home-wallpaper?v="), "default page requested wallpaper");
        require(basic.contains("median://folders"), "logo folder-manager long press missing");

        String folderPage = HomePage.html("google", Collections.<BrowserDataStore.Bookmark>emptyList(), false,
                "token", HomePageConfig.defaults(), Collections.<SearchEngineStore.Engine>emptyList(),
                Arrays.asList(new HomePage.Shortcut(true, "f-work", "工作", "", 2L),
                        new HomePage.Shortcut(false, "", "Example", "https://example.com/", 1L)));
        require(folderPage.contains("median://folder?id=f-work") && folderPage.contains("folder-mark"),
                "homepage folder shortcut missing");
        require(folderPage.contains("median://open?url=https%3A%2F%2Fexample.com%2F"),
                "bookmark/folder mixed layout missing");

        String customEngineId = "custom:test";
        String enginePage = HomePage.html(customEngineId,
                Arrays.asList(new BrowserDataStore.Bookmark("Example", "https://example.com/", 1L)),
                false, "token", HomePageConfig.defaults(),
                Arrays.asList(new SearchEngineStore.Engine(customEngineId, "我的搜索", "https://example.com/?q=%s")));
        require(enginePage.contains("data-e='custom:test'>我的搜索"), "custom engine chip missing");
        require(enginePage.contains("/favicon?host=example.com"), "favicon endpoint missing");
        require(enginePage.contains("encodeURIComponent(e)"), "engine id is not encoded");

        HomePageConfig options = HomePageConfig.create("<我的主页>", "欢迎 & 你好", "aurora", "", 3, 135, "violet", 40, 6,
                "contain", "glass", "compact", "circle", 5, true, false, true, false,
                true, false, true, true, 0L, 123L, 456L);
        String custom = HomePage.html("bing", Collections.<BrowserDataStore.Bookmark>emptyList(), true, "token", options);
        require(custom.contains("home-wallpaper?v=123"), "wallpaper asset missing");
        require(custom.contains("home-logo?v=456"), "logo asset missing");
        require(custom.contains("repeat(5,1fr)"), "shortcut columns missing");
        require(custom.contains("class='wrap compact'"), "compact layout missing");
        require(custom.contains("id='clock'"), "clock missing");
        require(custom.contains("letter-spacing:3px;white-space:pre-wrap"), "logo letter spacing missing");
        require(!custom.contains("<我的主页>"), "title was not escaped");
        require(custom.contains("欢迎 &amp; 你好"), "subtitle was not escaped");

        HomePageConfig gradientOptions = HomePageConfig.create("漂亮 Logo", "", "aurora", "", 3, 135,
                "violet", 28, 0, "cover", "solid", "center", "rounded", 4,
                true, true, true, true, false, false, false, false, 0L, 0L, 0L);
        String gradientPage = HomePage.html("google", Collections.<BrowserDataStore.Bookmark>emptyList(), false,
                "token", gradientOptions);
        require(gradientPage.contains("linear-gradient(135deg,#8B5CF6,#6366F1,#22D3EE)"), "gradient style missing");

        HomePageConfig googleOptions = HomePageConfig.create("Google", "", "google", "", 0, 90, "blue", 28, 0,
                "cover", "solid", "center", "rounded", 4, true, true, true, true,
                false, false, false, false, 0L, 0L, 0L);
        String googlePage = HomePage.html("google", Collections.<BrowserDataStore.Bookmark>emptyList(), false, "token", googleOptions);
        require(googlePage.contains("#4285F4") && googlePage.contains("#EA4335") && googlePage.contains("#34A853"),
                "Google logo colors missing");

        HomePageConfig customHtmlOptions = HomePageConfig.create("Median", "", "median", "", 0, 90, "blue", 28, 0,
                "cover", "solid", "center", "rounded", 4, true, true, true, true,
                false, true, true, false, 77L, 88L, 0L);
        String customHtmlPage = HomePage.html("google", Collections.<BrowserDataStore.Bookmark>emptyList(), false,
                "token", customHtmlOptions);
        require(customHtmlPage.contains("home-custom?v=77"), "custom HTML asset missing");
        require(customHtmlPage.contains("sandbox="), "custom HTML sandbox missing");
        require(customHtmlPage.contains("allow-scripts") && !customHtmlPage.contains("allow-same-origin"),
                "custom HTML script sandbox is incorrect");
        require(!customHtmlPage.contains("<form id='form'>"), "built-in homepage leaked behind custom HTML");

        HomePageConfig styledLogo = HomePageConfig.createPersonalized("自由文字", "", "aurora", "", 4, 135,
                "blue", 28, 0, "cover", "solid", "center", "rounded", 4,
                true, true, true, true, false, false, false, false, 0L, 0L, 0L,
                "text", 64, 800, 180, 120, 24, ".brand{opacity:.91}");
        String styledPage = HomePage.html("google", Collections.<BrowserDataStore.Bookmark>emptyList(), false,
                "token", styledLogo);
        require(styledPage.contains("font-size:64px;font-weight:800") && styledPage.contains(".brand{opacity:.91}"),
                "custom logo typography or CSS missing");

        int start = folderPage.indexOf("<script>");
        int end = folderPage.indexOf("</script>", start);
        require(start >= 0 && end > start, "generated script missing");
        if (args.length > 0) Files.writeString(Path.of(args[0]), folderPage.substring(start + 8, end), StandardCharsets.UTF_8);
        System.out.println("HomePageHtmlSelfTest passed");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
