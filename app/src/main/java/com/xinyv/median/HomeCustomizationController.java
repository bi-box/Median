package com.xinyv.median;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Owns the homepage appearance panels, editor return paths, and image-picker state. */
final class HomeCustomizationController {
    static final int REQUEST_WALLPAPER = 409;
    static final int REQUEST_LOGO = 410;
    private static final int HOME_SECTION_MAIN = 0;
    private static final int HOME_SECTION_LAYOUT = 1;
    private static final int HOME_SECTION_LOGO = 2;
    private static final int HOME_SECTION_SEARCH = 3;
    private static final int HOME_SECTION_BACKGROUND = 4;
    private static final int HOME_SECTION_SHORTCUTS = 5;
    private static final int HOME_SECTION_CODE = 6;
    private static final int TEXT = 0xFF0A0D12;
    private static final int MUTED = 0xFF6B7280;
    private static final int SURFACE = 0xFFF4F6F8;
    private static final int DARK_SURFACE = 0xFF1E2636;
    private static final int DARK_TEXT = 0xFFF0F1F3;
    private static final int DARK_MUTED = 0xFF9CA3AF;

    interface SheetHandler { void onItem(int index); }

    interface TaskQueue {
        boolean isAvailable();
        boolean execute(Runnable task);
    }

    interface Host {
        HomePageConfig homePageConfig();
        boolean isNightMode();
        void refreshHomeIfVisible();
        void toast(String message);
        void postAfterUiTransition(Runnable action);
        void showActionSheet(String title, String subtitle, String[] items, int[] icons,
                             Runnable backAction, SheetHandler handler);
        String searchEngineLabel();
        void showSearchEngineDialog(Runnable returnAction);
        void openBookmarkManager(Runnable returnAction);
        String homeOpenBehaviorLabel();
        void showHomeOpenBehaviorChoice();
    }

    private final Activity activity;
    private final SharedPreferences prefs;
    private final HomeImageStore homeImages;
    private final Handler uiHandler;
    private final TaskQueue queue;
    private final Host host;
    private volatile boolean closed;
    private Runnable homeCustomizationBackAction;
    private int pendingHomeImageReturnSection = HOME_SECTION_MAIN;

    HomeCustomizationController(Activity activity, SharedPreferences prefs, HomeImageStore images,
                                Handler uiHandler, TaskQueue queue, Host host) {
        this.activity = activity;
        this.prefs = prefs;
        this.homeImages = images;
        this.uiHandler = uiHandler;
        this.queue = queue;
        this.host = host;
    }

    void show(Runnable backAction) {
        if (!isInactive()) showHomeCustomization(backAction);
    }

    void continueCustomization() {
        if (!isInactive()) continueHomeCustomization();
    }

    void onImagePickerResult(HomeImageStore.Kind kind, int resultCode, Intent data) {
        if (isInactive()) return;
        if (resultCode == Activity.RESULT_OK && data != null) importHomeImage(data.getData(), kind);
        else continueHomeSection(pendingHomeImageReturnSection);
    }

    /** Cancels publication without waiting for image decoding or disk IO. */
    void close() {
        closed = true;
        homeCustomizationBackAction = null;
    }

    private boolean isInactive() {
        return closed || activity.isFinishing() || activity.isDestroyed();
    }

    /** One object handles both dialog cancellation paths instead of allocating two listeners. */
    private final class HomeDialogReturn implements DialogInterface.OnClickListener,
            DialogInterface.OnCancelListener, Runnable {
        private final int section;
        HomeDialogReturn(int section) { this.section = section; }
        @Override public void onClick(DialogInterface dialog, int which) { postAfterUiTransition(this); }
        @Override public void onCancel(DialogInterface dialog) { postAfterUiTransition(this); }
        @Override public void run() { showHomeSection(section); }
    }

    private void showHomeCustomization(Runnable backAction) {
        homeCustomizationBackAction = backAction;
        showHomeCustomizationPanel();
    }

    private void showHomeCustomizationPanel() {
        final HomePageConfig value = homePageConfig();
        boolean hasCustomHtml = CustomHomeHtml.valid(prefs.getString("home_custom_html", ""));
        boolean hasCustomCss = CustomHomeCss.clean(prefs.getString("home_custom_css", "")).length() > 0;
        String[] items = new String[] {
                "页面布局\n调整整体排版、副标题、时钟与强调色",
                "每次打开\n当前：" + homeOpenBehaviorLabel(),
                "Logo\n当前：" + logoModeLabel(value),
                "搜索框\n当前：" + (!value.showSearch ? "隐藏" : ("glass".equals(value.searchStyle) ? "磨砂" : "纯色")),
                "背景\n当前：" + (value.hasWallpaper ? "自定义壁纸" : "默认纯色"),
                "快捷网站\n当前：" + (value.showShortcuts ? value.shortcutColumns + " 列" : "隐藏"),
                "自定义主页\n当前：" + (value.customHtmlEnabled ? "完整 HTML" :
                        (hasCustomCss ? "自定义 CSS" : (hasCustomHtml ? "HTML 已保存、未启用" : "未设置")))
        };
        int[] icons = new int[] {
                BrowserIconView.TABS, BrowserIconView.HOME, BrowserIconView.INFO,
                BrowserIconView.SEARCH, BrowserIconView.DESKTOP, BrowserIconView.TABS,
                BrowserIconView.SCRIPT
        };
        showActionSheet("主页与外观", "修改立即保存", items, icons,
                null, null, homeCustomizationBackAction, new SheetHandler() {
            @Override public void onItem(int which) {
                if (which == 0) showHomeLayoutCustomization();
                else if (which == 1) host.showHomeOpenBehaviorChoice();
                else if (which == 2) showLogoCustomization();
                else if (which == 3) showSearchCustomization();
                else if (which == 4) showBackgroundCustomization();
                else if (which == 5) showShortcutCustomization();
                else showCustomCodeCustomization();
            }
        });
    }

    private void showHomeLayoutCustomization() {
        final HomePageConfig value = homePageConfig();
        String[] items = new String[] {
                "副标题：" + settingPreview(value.subtitle, 16, "未设置"),
                "页面排列方式\n当前：" + ("compact".equals(value.layout) ? "紧凑" : "居中"),
                "主页时钟：" + (value.showClock ? "显示" : "隐藏"),
                "强调色：" + accentLabel(value.accent),
                "左上角品牌：" + (value.showCornerBrand ? "显示" : "隐藏"),
                "恢复全部默认外观"
        };
        showActionSheet("页面布局", null, items,
                new int[] { BrowserIconView.INFO, BrowserIconView.TABS, BrowserIconView.INFO,
                        BrowserIconView.SPEED, BrowserIconView.TABS, BrowserIconView.CLOSE },
                null, null, new HomeDialogReturn(HOME_SECTION_MAIN), new SheetHandler() {
                    @Override public void onItem(int which) {
                        if (which == 0) showHomeTextSetting(false, HOME_SECTION_LAYOUT);
                        else if (which == 1) showHomeStringChoiceInSection("页面布局", "home_layout",
                                new String[] { "center", "compact" }, new String[] { "居中", "紧凑" }, value.layout, HOME_SECTION_LAYOUT);
                        else if (which == 2) toggleHomeBooleanInSection("home_show_clock", value.showClock, HOME_SECTION_LAYOUT);
                        else if (which == 3) showHomeStringChoiceInSection("主页强调色", "home_accent",
                                new String[] { "blue", "violet", "green", "orange", "rose", "teal" },
                                new String[] { "蓝色", "紫色", "绿色", "橙色", "玫红", "青色" }, value.accent, HOME_SECTION_LAYOUT);
                        else if (which == 4) toggleHomeBooleanInSection("home_show_corner", value.showCornerBrand, HOME_SECTION_LAYOUT);
                        else confirmResetHomeCustomization(HOME_SECTION_LAYOUT);
                    }
                });
    }

    private void showLogoCustomization() {
        final HomePageConfig value = homePageConfig();
        ArrayList<String> labels = new ArrayList<String>();
        ArrayList<Integer> actions = new ArrayList<Integer>();
        labels.add("Logo 类型：" + logoModeLabel(value)); actions.add(Integer.valueOf(0));
        if ("text".equals(value.logoMode)) {
            labels.add("Logo 文字：" + settingPreview(value.title, 18, "Median")); actions.add(Integer.valueOf(1));
            labels.add("文字配色：" + logoStyleLabel(value.logoStyle)); actions.add(Integer.valueOf(2));
            labels.add("文字排版：" + value.logoFontSize + " px · " + value.logoFontWeight + " · " +
                    logoLetterSpacingLabel(value.logoLetterSpacing)); actions.add(Integer.valueOf(3));
            labels.add("高级文字代码"); actions.add(Integer.valueOf(7));
        } else if ("image".equals(value.logoMode)) {
            labels.add(value.hasLogo ? "更换 Logo 图片" : "选择 Logo 图片"); actions.add(Integer.valueOf(8));
            labels.add("图片宽度：" + value.logoImageWidth + " px"); actions.add(Integer.valueOf(9));
            labels.add("图片高度：" + value.logoImageHeight + " px"); actions.add(Integer.valueOf(10));
            labels.add("图片圆角：" + value.logoImageRadius + "%"); actions.add(Integer.valueOf(11));
        }
        final int[] mapped = new int[actions.size()];
        for (int i = 0; i < mapped.length; i++) mapped[i] = actions.get(i).intValue();
        String[] items = labels.toArray(new String[labels.size()]);
        int[] icons = new int[items.length];
        for (int i = 0; i < icons.length; i++) icons[i] = i == 0 ? BrowserIconView.INFO : BrowserIconView.SPEED;
        showActionSheet("Logo", null, items, icons,
                null, null, new HomeDialogReturn(HOME_SECTION_MAIN), new SheetHandler() {
            @Override public void onItem(int which) {
                int action = mapped[which];
                if (action == 0) showLogoModeChoice(value);
                else if (action == 1) showHomeTextSetting(true, HOME_SECTION_LOGO);
                else if (action == 2) showLogoStyleChoice(value, HOME_SECTION_LOGO);
                else if (action == 3) showLogoTypographyCustomization();
                else if (action == 7) showLogoCodeEditor(HOME_SECTION_LOGO);
                else if (action == 8) chooseHomeImage(HomeImageStore.Kind.LOGO, HOME_SECTION_LOGO);
                else if (action == 9) showHomeIntegerChoiceInSection("图片宽度", "home_logo_image_width",
                        new int[] { 64, 88, 112, 132, 160, 200, 240 },
                        new String[] { "64 px", "88 px", "112 px", "132 px", "160 px", "200 px", "240 px" }, value.logoImageWidth, HOME_SECTION_LOGO);
                else if (action == 10) showHomeIntegerChoiceInSection("图片高度", "home_logo_image_height",
                        new int[] { 48, 64, 80, 96, 112, 144, 176 },
                        new String[] { "48 px", "64 px", "80 px", "96 px", "112 px", "144 px", "176 px" }, value.logoImageHeight, HOME_SECTION_LOGO);
                else showHomeIntegerChoiceInSection("图片圆角", "home_logo_image_radius",
                        new int[] { 0, 8, 16, 24, 32, 50 },
                        new String[] { "0%", "8%", "16%", "24%", "32%", "50% 圆形" }, value.logoImageRadius, HOME_SECTION_LOGO);
            }
        });
    }

    private void showLogoTypographyCustomization() {
        final HomePageConfig value = homePageConfig();
        String[] items = new String[] {
                "字号：" + value.logoFontSize + " px",
                "字重：" + value.logoFontWeight,
                "字间距：" + logoLetterSpacingLabel(value.logoLetterSpacing),
                "渐变方向：" + logoGradientAngleLabel(value.logoGradientAngle)
        };
        showActionSheet("文字排版", null, items,
                new int[] { BrowserIconView.SPEED, BrowserIconView.SPEED, BrowserIconView.SPEED, BrowserIconView.SPEED },
                null, null, new HomeDialogReturn(HOME_SECTION_LOGO), new SheetHandler() {
                    @Override public void onItem(int which) {
                        if (which == 0) showHomeIntegerChoiceInSection("Logo 字号", "home_logo_font_size",
                                new int[] { 28, 36, 44, 47, 52, 64, 72, 88 },
                                new String[] { "28 px", "36 px", "44 px", "47 px", "52 px", "64 px", "72 px", "88 px" }, value.logoFontSize, HOME_SECTION_LOGO);
                        else if (which == 1) showHomeIntegerChoiceInSection("Logo 字重", "home_logo_font_weight",
                                new int[] { 300, 400, 500, 600, 700, 800, 900 },
                                new String[] { "300 细", "400 常规", "500 中等", "600 半粗", "700 粗体", "800 特粗", "900 黑体" }, value.logoFontWeight, HOME_SECTION_LOGO);
                        else if (which == 2) showHomeIntegerChoiceInSection("Logo 字间距", "home_logo_letter_spacing",
                                new int[] { -3, -2, -1, 0, 1, 2, 3, 4, 6, 8, 10 },
                                new String[] { "-3 px", "-2 px", "-1 px", "标准", "+1 px", "+2 px", "+3 px", "+4 px", "+6 px", "+8 px", "+10 px" }, value.logoLetterSpacing, HOME_SECTION_LOGO);
                        else showHomeIntegerChoiceInSection("渐变方向", "home_logo_gradient_angle",
                                new int[] { 90, 135, 180, 45, 0 },
                                new String[] { "左 → 右", "左上 → 右下", "上 → 下", "左下 → 右上", "下 → 上" }, value.logoGradientAngle, HOME_SECTION_LOGO);
                    }
                });
    }

    private void showLogoModeChoice(final HomePageConfig value) {
        HomeDialogReturn back = new HomeDialogReturn(HOME_SECTION_LOGO);
        int checked;
        boolean defaults = "text".equals(value.logoMode) && "Median".equals(value.title) &&
                "median".equals(value.logoStyle) && value.logoFontSize == 47 &&
                value.logoFontWeight == 720 && value.logoLetterSpacing == 0;
        if ("none".equals(value.logoMode)) checked = 3;
        else if ("image".equals(value.logoMode)) checked = 2;
        else checked = defaults ? 0 : 1;
        new AlertDialog.Builder(activity).setTitle("Logo 类型").setSingleChoiceItems(
                new String[] { "默认 Median", "自定义文字", "图片", "无" }, checked,
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        SharedPreferences.Editor editor = prefs.edit();
                        if (which == 0) {
                            editor.putString("home_logo_mode", "text").putString("home_title", "Median")
                                    .putString("home_logo_style", "median").remove("home_logo_code")
                                    .putInt("home_logo_font_size", 47).putInt("home_logo_font_weight", 720)
                                    .putInt("home_logo_letter_spacing", 0).apply();
                            homeCustomizationChanged("已恢复默认 Median Logo", HOME_SECTION_LOGO);
                        } else if (which == 1) {
                            editor.putString("home_logo_mode", "text").apply();
                            homeCustomizationChanged(null, HOME_SECTION_LOGO);
                        } else if (which == 2) {
                            editor.putString("home_logo_mode", "image").apply();
                            if (value.hasLogo) homeCustomizationChanged(null, HOME_SECTION_LOGO);
                            else {
                                host.refreshHomeIfVisible();
                                chooseHomeImage(HomeImageStore.Kind.LOGO, HOME_SECTION_LOGO);
                            }
                        } else {
                            editor.putString("home_logo_mode", "none").apply();
                            homeCustomizationChanged("主页 Logo 已隐藏", HOME_SECTION_LOGO);
                        }
                    }
                }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void showSearchCustomization() {
        final HomePageConfig value = homePageConfig();
        String[] items = new String[] {
                "搜索框：" + (!value.showSearch ? "隐藏" : ("glass".equals(value.searchStyle) ? "磨砂" : "纯色")),
                "搜索引擎按钮：" + (value.showEngines ? "显示" : "隐藏"),
                "默认搜索引擎：" + searchEngineLabel()
        };
        showActionSheet("搜索框", null, items,
                new int[] { BrowserIconView.SEARCH, BrowserIconView.SEARCH, BrowserIconView.SEARCH },
                null, null, new HomeDialogReturn(HOME_SECTION_MAIN), new SheetHandler() {
                    @Override public void onItem(int which) {
                        if (which == 0) showHomeSearchChoice(value, HOME_SECTION_SEARCH);
                        else if (which == 1) toggleHomeBooleanInSection("home_show_engines", value.showEngines, HOME_SECTION_SEARCH);
                        else showSearchEngineDialog(new HomeDialogReturn(HOME_SECTION_SEARCH));
                    }
                });
    }

    private void showBackgroundCustomization() {
        final HomePageConfig value = homePageConfig();
        String[] items = new String[] {
                "主页壁纸：" + (value.hasWallpaper ? "已设置" : "默认纯色"),
                "壁纸遮罩：" + value.wallpaperDim + "%",
                "壁纸模糊：" + (value.wallpaperBlur == 0 ? "关闭" : value.wallpaperBlur + " px"),
                "壁纸显示：" + ("contain".equals(value.wallpaperFit) ? "完整显示" : "填充裁剪")
        };
        showActionSheet("背景", null, items,
                new int[] { BrowserIconView.DESKTOP, BrowserIconView.SHIELD, BrowserIconView.SPEED, BrowserIconView.DESKTOP },
                null, null, new HomeDialogReturn(HOME_SECTION_MAIN), new SheetHandler() {
                    @Override public void onItem(int which) {
                        if (which == 0) showHomeAssetActions(HomeImageStore.Kind.WALLPAPER);
                        else if (which == 1) showHomeIntegerChoiceInSection("壁纸遮罩", "home_wallpaper_dim",
                                new int[] { 0, 15, 28, 40, 55, 70 }, new String[] { "0%", "15%", "28%", "40%", "55%", "70%" }, value.wallpaperDim, HOME_SECTION_BACKGROUND);
                        else if (which == 2) showHomeIntegerChoiceInSection("壁纸模糊", "home_wallpaper_blur",
                                new int[] { 0, 3, 6, 9, 12 }, new String[] { "关闭", "3 px", "6 px", "9 px", "12 px" }, value.wallpaperBlur, HOME_SECTION_BACKGROUND);
                        else showHomeStringChoiceInSection("壁纸显示方式", "home_wallpaper_fit",
                                new String[] { "cover", "contain" }, new String[] { "填充裁剪", "完整显示" }, value.wallpaperFit, HOME_SECTION_BACKGROUND);
                    }
                });
    }

    private void showShortcutCustomization() {
        final HomePageConfig value = homePageConfig();
        String[] items = new String[] {
                "快捷网站：" + (value.showShortcuts ? "显示" : "隐藏"),
                "入口列数：" + value.shortcutColumns + " 列",
                "入口形状：" + tileShapeLabel(value.tileShape),
                "管理快捷网站"
        };
        showActionSheet("快捷网站", null, items,
                new int[] { BrowserIconView.TABS, BrowserIconView.TABS, BrowserIconView.TABS, BrowserIconView.INFO },
                null, null, new HomeDialogReturn(HOME_SECTION_MAIN), new SheetHandler() {
                    @Override public void onItem(int which) {
                        if (which == 0) toggleHomeBooleanInSection("home_show_shortcuts", value.showShortcuts, HOME_SECTION_SHORTCUTS);
                        else if (which == 1) showHomeIntegerChoiceInSection("快捷入口列数", "home_shortcut_columns",
                                new int[] { 3, 4, 5 }, new String[] { "3 列", "4 列", "5 列" }, value.shortcutColumns, HOME_SECTION_SHORTCUTS);
                        else if (which == 2) showHomeStringChoiceInSection("快捷入口形状", "home_tile_shape",
                                new String[] { "rounded", "circle", "square" }, new String[] { "圆角方形", "圆形", "小圆角方形" }, value.tileShape, HOME_SECTION_SHORTCUTS);
                        else openBookmarkManager(new HomeDialogReturn(HOME_SECTION_SHORTCUTS));
                    }
                });
    }

    private void showCustomCodeCustomization() {
        HomePageConfig value = homePageConfig();
        boolean hasCss = value.customCss.length() > 0;
        boolean hasHtml = CustomHomeHtml.valid(prefs.getString("home_custom_html", ""));
        String[] items = new String[] {
                "自定义 CSS\n" + (hasCss ? (value.customHtmlEnabled ? "已保存，未显示" : "已启用") : "未设置"),
                "完整 HTML\n" + (value.customHtmlEnabled ? "已启用" : (hasHtml ? "已保存" : "未设置"))
        };
        showActionSheet("自定义主页", null, items,
                new int[] { BrowserIconView.SPEED, BrowserIconView.SCRIPT },
                null, null, new HomeDialogReturn(HOME_SECTION_MAIN), new SheetHandler() {
                    @Override public void onItem(int which) {
                        if (which == 0) showCustomCssActions();
                        else showCustomHomeActions(HOME_SECTION_CODE);
                    }
                });
    }

    private void showCustomCssActions() {
        final String saved = CustomHomeCss.clean(prefs.getString("home_custom_css", ""));
        if (saved.length() == 0) { showCustomCssEditor(); return; }
        HomeDialogReturn back = new HomeDialogReturn(HOME_SECTION_CODE);
        new AlertDialog.Builder(activity).setTitle("自定义 CSS")
                .setItems(new String[] { "编辑 CSS", "清除 CSS" }, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) showCustomCssEditor();
                        else {
                            prefs.edit().remove("home_custom_css").apply();
                            homeCustomizationChanged("自定义 CSS 已清除", HOME_SECTION_CODE);
                        }
                    }
                }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void showCustomCssEditor() {
        final EditText input = compactCodeEditor();
        final HomeDialogReturn back = new HomeDialogReturn(HOME_SECTION_CODE);
        String saved = CustomHomeCss.clean(prefs.getString("home_custom_css", ""));
        input.setText(saved.length() == 0 ? CustomHomeCss.EXAMPLE : saved);
        input.setHint(CustomHomeCss.EXAMPLE);
        final AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("自定义 CSS")
                .setMessage("保留内置功能。上限 32 KB，禁止外部 URL 与 @import。")
                .setView(input).setPositiveButton("保存并使用", null).setNeutralButton("示例", null)
                .setNegativeButton("取消", back).create();
        dialog.setOnCancelListener(back);
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) { input.setText(CustomHomeCss.EXAMPLE); input.setSelection(input.length()); }
                });
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        String raw = input.getText().toString().trim();
                        if (raw.length() == 0) { toast("CSS 不能为空，可使用“清除 CSS”移除"); return; }
                        String error = CustomHomeCss.error(raw);
                        if (error.length() > 0) { toast(error); return; }
                        prefs.edit().putString("home_custom_css", raw)
                                .putBoolean("home_custom_html_enabled", false).apply();
                        dialog.dismiss();
                        homeCustomizationChanged("自定义 CSS 已启用", HOME_SECTION_CODE);
                    }
                });
            }
        });
        dialog.show();
    }

    private EditText compactCodeEditor() {
        EditText input = new EditText(activity);
        input.setMinLines(4);
        input.setMaxLines(8);
        input.setMinHeight(dp(132));
        input.setMaxHeight(dp(238));
        input.setVerticalScrollBarEnabled(true);
        input.setHorizontallyScrolling(false);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setTypeface(android.graphics.Typeface.MONOSPACE);
        input.setTextSize(13.5f);
        input.setTextColor(host.isNightMode() ? DARK_TEXT : TEXT);
        input.setHintTextColor(host.isNightMode() ? DARK_MUTED : MUTED);
        input.setBackground(roundRect(host.isNightMode() ? DARK_SURFACE : SURFACE, 12));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        return input;
    }

    private void showCustomHomeActions() { showCustomHomeActions(HOME_SECTION_MAIN); }

    private void showCustomHomeActions(final int returnSection) {
        final String saved = prefs.getString("home_custom_html", "");
        if (!CustomHomeHtml.valid(saved)) { showCustomHomeEditor(returnSection); return; }
        final boolean enabled = prefs.getBoolean("home_custom_html_enabled", false);
        HomeDialogReturn back = new HomeDialogReturn(returnSection);
        new AlertDialog.Builder(activity).setTitle("完整 HTML 页面")
                .setItems(new String[] { "编辑 HTML", enabled ? "停用并显示默认主页" : "启用自定义主页", "删除自定义主页" },
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) showCustomHomeEditor(returnSection);
                                else if (which == 1) {
                                    prefs.edit().putBoolean("home_custom_html_enabled", !enabled).apply();
                                    homeCustomizationChanged(enabled ? "已停用完整 HTML" : "已启用完整 HTML", returnSection);
                                } else confirmDeleteCustomHome(returnSection);
                            }
                        }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void showCustomHomeEditor(final int returnSection) {
        final EditText input = compactCodeEditor();
        final HomeDialogReturn back = new HomeDialogReturn(returnSection);
        String saved = prefs.getString("home_custom_html", "");
        input.setText(CustomHomeHtml.valid(saved) ? saved : CustomHomeHtml.EXAMPLE);
        input.setHint(CustomHomeHtml.EXAMPLE);
        final AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("自定义主页 HTML")
                .setMessage("上限 64 KB；脚本在无同源、无联网、无内部权限的沙箱中运行。")
                .setView(input).setPositiveButton("保存并启用", null).setNeutralButton("示例", null)
                .setNegativeButton("取消", back).create();
        dialog.setOnCancelListener(back);
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        input.setText(CustomHomeHtml.EXAMPLE);
                        input.setSelection(input.length());
                    }
                });
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        String raw = input.getText().toString().trim();
                        if (raw.length() == 0) { toast("HTML 不能为空"); return; }
                        if (raw.getBytes(StandardCharsets.UTF_8).length > CustomHomeHtml.MAX_LENGTH) {
                            toast("HTML 不能超过 64 KB"); return;
                        }
                        long previous = Math.max(0L, prefs.getLong("home_custom_html_version", 0L));
                        long next = previous == Long.MAX_VALUE ? 1L : previous + 1L;
                        boolean saved = prefs.edit().putString("home_custom_html", raw)
                                .putBoolean("home_custom_html_enabled", true)
                                .putLong("home_custom_html_version", next).commit();
                        if (!saved) { toast("自定义主页保存失败"); return; }
                        dialog.dismiss();
                        homeCustomizationChanged("完整 HTML 已保存并启用", returnSection);
                    }
                });
            }
        });
        dialog.show();
    }

    private void confirmDeleteCustomHome(final int returnSection) {
        HomeDialogReturn back = new HomeDialogReturn(returnSection);
        new AlertDialog.Builder(activity).setTitle("删除自定义主页？")
                .setMessage("HTML 代码将从本机永久删除，壁纸不会受影响。")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        prefs.edit().remove("home_custom_html").remove("home_custom_html_enabled")
                                .remove("home_custom_html_version").apply();
                        homeCustomizationChanged("完整 HTML 已删除", returnSection);
                    }
                }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void showHomeAssetActions(final HomeImageStore.Kind kind) {
        final int returnSection = kind == HomeImageStore.Kind.WALLPAPER ? HOME_SECTION_BACKGROUND : HOME_SECTION_LOGO;
        if (homeImages == null || !homeImages.has(kind)) { chooseHomeImage(kind, returnSection); return; }
        String label = kind == HomeImageStore.Kind.WALLPAPER ? "壁纸" : "Logo";
        HomeDialogReturn back = new HomeDialogReturn(returnSection);
        new AlertDialog.Builder(activity).setTitle("主页" + label)
                .setItems(new String[] { "更换图片", "移除自定义" + label }, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) chooseHomeImage(kind, returnSection);
                        else {
                            homeImages.remove(kind);
                            if (kind == HomeImageStore.Kind.LOGO) prefs.edit().putString("home_logo_mode", "text").apply();
                            homeCustomizationChanged(kind == HomeImageStore.Kind.WALLPAPER ?
                                    "已恢复默认背景" : "已恢复文字 Logo", returnSection);
                        }
                    }
                }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void chooseHomeImage(HomeImageStore.Kind kind, int returnSection) {
        pendingHomeImageReturnSection = returnSection;
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivityForResult(intent, kind == HomeImageStore.Kind.WALLPAPER ? REQUEST_WALLPAPER : REQUEST_LOGO);
        } catch (Exception error) {
            toast("没有可用的图片选择器");
            continueHomeSection(pendingHomeImageReturnSection);
        }
    }

    private void importHomeImage(final Uri uri, final HomeImageStore.Kind kind) {
        if (isInactive()) return;
        if (uri == null || homeImages == null || !queue.isAvailable()) {
            toast("无法读取所选图片");
            continueHomeSection(pendingHomeImageReturnSection);
            return;
        }
        toast(kind == HomeImageStore.Kind.WALLPAPER ? "正在优化壁纸…" : "正在优化 Logo…");
        try {
            queue.execute(new Runnable() {
                @Override public void run() {
                    if (closed) return;
                    Exception failure = null;
                    try { homeImages.save(uri, kind); }
                    catch (Exception error) { failure = error; }
                    if (closed) return;
                    final Exception result = failure;
                    uiHandler.post(new Runnable() {
                        @Override public void run() {
                            if (isInactive()) return;
                            if (result != null) {
                                toast("图片处理失败：" + ErrorText.message(result, 180));
                                continueHomeSection(pendingHomeImageReturnSection);
                                return;
                            }
                            if (kind == HomeImageStore.Kind.LOGO)
                                prefs.edit().putString("home_logo_mode", "image").apply();
                            homeCustomizationChanged(kind == HomeImageStore.Kind.WALLPAPER ?
                                    "主页壁纸已更新" : "主页 Logo 已更新", pendingHomeImageReturnSection);
                        }
                    });
                }
            });
        } catch (RuntimeException rejected) {
            toast("图片处理队列繁忙，请稍后重试");
            continueHomeSection(pendingHomeImageReturnSection);
        }
    }

    private void showHomeTextSetting(final boolean title) { showHomeTextSetting(title, HOME_SECTION_MAIN); }

    private void showHomeTextSetting(final boolean title, final int returnSection) {
        final EditText input = new EditText(activity);
        HomeDialogReturn back = new HomeDialogReturn(returnSection);
        input.setSingleLine(true);
        input.setText(title ? homePageConfig().title : homePageConfig().subtitle);
        input.setHint(title ? "Median" : "例如：今天也要保持好奇");
        input.setSelectAllOnFocus(true);
        input.setPadding(dp(16), dp(8), dp(16), dp(8));
        new AlertDialog.Builder(activity).setTitle(title ? "Logo 文字" : "主页副标题")
                .setMessage(title ? "最多 28 个字符；留空恢复 Median。" : "最多 64 个字符；留空则隐藏。")
                .setView(input).setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String value = title ? HomePageConfig.cleanTitle(input.getText().toString()) :
                                HomePageConfig.cleanSubtitle(input.getText().toString());
                        SharedPreferences.Editor editor = prefs.edit().putString(title ? "home_title" : "home_subtitle", value);
                        if (title) {
                            editor.putString("home_logo_mode", "text");
                            if ("custom".equals(homePageConfig().logoStyle))
                                editor.putString("home_logo_style", "median").remove("home_logo_code");
                        }
                        editor.apply();
                        homeCustomizationChanged(title ? "Logo 文字已更新" : "主页副标题已更新", returnSection);
                    }
                }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void showLogoStyleChoice(final HomePageConfig value) { showLogoStyleChoice(value, HOME_SECTION_MAIN); }

    private void showLogoStyleChoice(final HomePageConfig value, final int returnSection) {
        final String[] styles = new String[] { "median", "google", "aurora", "sunset", "ocean", "rose_gold", "custom" };
        String[] labels = new String[] { "Median 经典", "Google 官方配色", "极光渐变", "日落渐变", "海洋渐变", "玫瑰金渐变", "自定义代码" };
        HomeDialogReturn back = new HomeDialogReturn(returnSection);
        int checked = 0;
        for (int i = 0; i < styles.length; i++) if (styles[i].equals(value.logoStyle)) checked = i;
        new AlertDialog.Builder(activity).setTitle("文字 Logo 样式").setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                String style = styles[which];
                dialog.dismiss();
                if ("custom".equals(style)) {
                    showLogoCodeEditor(returnSection);
                    return;
                }
                prefs.edit().putString("home_logo_style", style)
                        .putString("home_logo_mode", "text").apply();
                homeCustomizationChanged(null, returnSection);
            }
        }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void showLogoCodeEditor() { showLogoCodeEditor(HOME_SECTION_MAIN); }

    private void showLogoCodeEditor(final int returnSection) {
        final EditText input = new EditText(activity);
        final HomeDialogReturn back = new HomeDialogReturn(returnSection);
        String saved = homePageConfig().logoCode;
        String example = LogoMarkup.gradientExample(homePageConfig().title);
        input.setText(saved.length() == 0 ? example : saved);
        input.setHint(example);
        input.setMinLines(5);
        input.setMaxLines(9);
        input.setHorizontallyScrolling(false);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setTypeface(android.graphics.Typeface.MONOSPACE);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        final AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("自定义文字 Logo")
                .setMessage("安全标记语法（不会执行 HTML/JavaScript）：\n\n" +
                        "[color=#4285F4]文字[/color]\n" +
                        "[gradient=#8B5CF6,#6366F1,#22D3EE]文字[/gradient]\n" +
                        "Med[space=4]ian\n\n" +
                        "普通空格会保留；[space=0–24] 可精确控制局部间隔。渐变支持 2–4 个颜色。")
                .setView(input).setPositiveButton("保存", null)
                .setNeutralButton("Google 示例", null)
                .setNegativeButton("取消", back).create();
        dialog.setOnCancelListener(back);
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) { input.setText(LogoMarkup.GOOGLE_CODE); input.setSelection(input.length()); }
                });
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        LogoMarkup.Result parsed = LogoMarkup.parse(input.getText().toString(), homePageConfig().logoGradientAngle);
                        if (!parsed.valid()) { toast(parsed.error); return; }
                        prefs.edit().putString("home_logo_mode", "text")
                                .putString("home_logo_style", "custom")
                                .putString("home_logo_code", LogoMarkup.clean(input.getText().toString()))
                                .putString("home_title", HomePageConfig.cleanTitle(parsed.plainText)).apply();
                        dialog.dismiss();
                        homeCustomizationChanged("自定义文字 Logo 已保存", returnSection);
                    }
                });
            }
        });
        dialog.show();
    }

    private void showHomeStringChoice(String title, final String key, final String[] values,
                                      String[] labels, String current) {
        showHomeStringChoiceInSection(title, key, values, labels, current, HOME_SECTION_MAIN);
    }

    private void showHomeStringChoiceInSection(String title, final String key, final String[] values,
                                      String[] labels, String current, final int returnSection) {
        HomeDialogReturn back = new HomeDialogReturn(returnSection);
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) checked = i;
        new AlertDialog.Builder(activity).setTitle(title).setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                prefs.edit().putString(key, values[which]).apply();
                dialog.dismiss();
                homeCustomizationChanged(null, returnSection);
            }
        }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void showHomeIntegerChoice(String title, final String key, final int[] values,
                                       String[] labels, int current) {
        showHomeIntegerChoiceInSection(title, key, values, labels, current, HOME_SECTION_MAIN);
    }

    private void showHomeIntegerChoiceInSection(String title, final String key, final int[] values,
                                       String[] labels, int current, final int returnSection) {
        HomeDialogReturn back = new HomeDialogReturn(returnSection);
        int checked = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == current) checked = i;
        new AlertDialog.Builder(activity).setTitle(title).setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                prefs.edit().putInt(key, values[which]).apply();
                dialog.dismiss();
                homeCustomizationChanged(null, returnSection);
            }
        }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void showHomeSearchChoice(HomePageConfig value) { showHomeSearchChoice(value, HOME_SECTION_MAIN); }

    private void showHomeSearchChoice(HomePageConfig value, final int returnSection) {
        HomeDialogReturn back = new HomeDialogReturn(returnSection);
        int checked = !value.showSearch ? 2 : ("glass".equals(value.searchStyle) ? 1 : 0);
        new AlertDialog.Builder(activity).setTitle("搜索框").setSingleChoiceItems(
                new String[] { "显示 · 纯色", "显示 · 磨砂", "隐藏" }, checked, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences.Editor editor = prefs.edit().putBoolean("home_show_search", which != 2);
                        if (which != 2) editor.putString("home_search_style", which == 1 ? "glass" : "solid");
                        editor.apply();
                        dialog.dismiss();
                        homeCustomizationChanged(null, returnSection);
                    }
                }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void toggleHomeBoolean(String key, boolean current) {
        toggleHomeBooleanInSection(key, current, HOME_SECTION_MAIN);
    }

    private void toggleHomeBooleanInSection(String key, boolean current, int returnSection) {
        prefs.edit().putBoolean(key, !current).apply();
        homeCustomizationChanged(null, returnSection);
    }

    private void confirmResetHomeCustomization(final int returnSection) {
        HomeDialogReturn back = new HomeDialogReturn(returnSection);
        new AlertDialog.Builder(activity).setTitle("恢复默认主页？")
                .setMessage("将移除自定义壁纸、Logo、CSS 和 HTML，并恢复标题、颜色、布局及模块显示。书签和“每次打开”设置不会删除。")
                .setPositiveButton("恢复", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        SharedPreferences.Editor editor = prefs.edit();
                        String[] keys = new String[] { "home_title", "home_subtitle", "home_accent", "home_wallpaper_dim",
                                "home_logo_style", "home_logo_code", "home_logo_letter_spacing", "home_logo_gradient_angle",
                                "home_logo_mode", "home_logo_font_size", "home_logo_font_weight", "home_logo_image_width",
                                "home_logo_image_height", "home_logo_image_radius", "home_custom_css",
                                "home_wallpaper_blur", "home_wallpaper_fit", "home_search_style", "home_layout",
                                "home_tile_shape", "home_shortcut_columns", "home_show_search", "home_show_engines",
                                "home_show_shortcuts", "home_show_corner", "home_show_clock", "home_custom_html",
                                "home_custom_html_enabled", "home_custom_html_version" };
                        for (String key : keys) editor.remove(key);
                        editor.apply();
                        if (homeImages != null) homeImages.removeAll();
                        homeCustomizationChanged("已恢复默认主页", returnSection);
                    }
                }).setNegativeButton("取消", back).setOnCancelListener(back).show();
    }

    private void homeCustomizationChanged(String message) {
        homeCustomizationChanged(message, HOME_SECTION_MAIN);
    }

    private void homeCustomizationChanged(String message, int returnSection) {
        if (isInactive()) return;
        host.refreshHomeIfVisible();
        if (message != null && message.length() > 0) toast(message);
        continueHomeSection(returnSection);
    }

    private void continueHomeCustomization() {
        continueHomeSection(HOME_SECTION_MAIN);
    }

    private void continueHomeSection(final int section) {
        postAfterUiTransition(new HomeDialogReturn(section));
    }

    private void showHomeSection(int section) {
        if (isInactive()) return;
        if (section == HOME_SECTION_LAYOUT) showHomeLayoutCustomization();
        else if (section == HOME_SECTION_LOGO) showLogoCustomization();
        else if (section == HOME_SECTION_SEARCH) showSearchCustomization();
        else if (section == HOME_SECTION_BACKGROUND) showBackgroundCustomization();
        else if (section == HOME_SECTION_SHORTCUTS) showShortcutCustomization();
        else if (section == HOME_SECTION_CODE) showCustomCodeCustomization();
        else showHomeCustomizationPanel();
    }

    private static String settingPreview(String value, int max, String empty) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() == 0) return empty;
        return UiText.ellipsize(clean, max);
    }

    private static String accentLabel(String value) {
        if ("violet".equals(value)) return "紫色";
        if ("green".equals(value)) return "绿色";
        if ("orange".equals(value)) return "橙色";
        if ("rose".equals(value)) return "玫红";
        if ("teal".equals(value)) return "青色";
        return "蓝色";
    }

    private static String tileShapeLabel(String value) {
        if ("circle".equals(value)) return "圆形";
        if ("square".equals(value)) return "小圆角方形";
        return "圆角方形";
    }

    private static String logoModeLabel(HomePageConfig value) {
        if (value == null || "none".equals(value.logoMode)) return "无";
        if ("image".equals(value.logoMode)) return value.hasLogo ? "图片" : "图片（未选择）";
        return "文字 · " + settingPreview(value.title, 12, "Median");
    }

    private static String logoStyleLabel(String value) {
        if ("google".equals(value)) return "Google 官方配色";
        if ("aurora".equals(value)) return "极光渐变";
        if ("sunset".equals(value)) return "日落渐变";
        if ("ocean".equals(value)) return "海洋渐变";
        if ("rose_gold".equals(value)) return "玫瑰金渐变";
        if ("custom".equals(value)) return "自定义代码";
        return "Median 经典";
    }

    private static String logoLetterSpacingLabel(int value) {
        if (value == 0) return "标准";
        return (value > 0 ? "+" : "") + value + " px";
    }

    private static String logoGradientAngleLabel(int value) {
        if (value == 135) return "左上 → 右下";
        if (value == 180) return "上 → 下";
        if (value == 45) return "左下 → 右上";
        if (value == 0) return "下 → 上";
        if (value == 90) return "左 → 右";
        return value + "°";
    }

    private HomePageConfig homePageConfig() { return host.homePageConfig(); }

    private String homeOpenBehaviorLabel() { return host.homeOpenBehaviorLabel(); }

    private String searchEngineLabel() { return host.searchEngineLabel(); }

    private void showSearchEngineDialog(Runnable returnAction) { host.showSearchEngineDialog(returnAction); }

    private void openBookmarkManager(Runnable returnAction) { host.openBookmarkManager(returnAction); }

    private void postAfterUiTransition(Runnable action) {
        if (!isInactive()) host.postAfterUiTransition(action);
    }

    private void toast(String message) {
        if (!isInactive()) host.toast(message);
    }

    private void showActionSheet(String title, String subtitle, String[] items, int[] icons,
                                 int[] rowKinds, String[] sectionHeaders, Runnable backAction,
                                 SheetHandler handler) {
        host.showActionSheet(title, subtitle, items, icons, backAction, handler);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }
}
