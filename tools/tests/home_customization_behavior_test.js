'use strict';

// Compare every migrated method with the supplied pre-refactor source, then compile and exercise
// the real controller against recording Android widgets, preferences, and task queues.
const fs = require('fs');
const path = require('path');
const os = require('os');
const cp = require('child_process');
const args = process.argv.slice(2);
const option = (name, fallback) => args.includes(name) ? args[args.indexOf(name) + 1] : fallback;
const repo = path.resolve(__dirname, '../..');
const out = path.resolve(option('--out', fs.mkdtempSync(path.join(os.tmpdir(), 'median-home-customization-'))));
const jdk = option('--jdk', process.env.JAVA_HOME || '');
const executable = name => jdk ? path.join(jdk, 'bin', name + (process.platform === 'win32' ? '.exe' : '')) : name;
const production = path.join(repo, 'app/src/main/java/com/xinyv/median');
const controller = fs.readFileSync(path.join(production, 'HomeCustomizationController.java'), 'utf8');
const baseline = option('--baseline', '');
function stripComments(text) {
  return text.replace(/"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\r\n]*|\/\*[\s\S]*?\*\//g,
    token => token.startsWith('//') || token.startsWith('/*') ? ' ' : token);
}
function tokens(text) {
  return stripComments(text).match(/"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[A-Za-z_$][\w$]*|\d+(?:\.\d+)?[fFlL]?|[^\s]/g) || [];
}
function normalize(text) {
  const substitutions = [
    ['new AlertDialog.Builder(this)', 'new AlertDialog.Builder(activity)'],
    ['new EditText(this)', 'new EditText(activity)'],
    ['if (isHomeUrl(currentPageUrl)) showHome();', 'host.refreshHomeIfVisible();'],
    ['showHomeOpenBehaviorChoice(true)', 'host.showHomeOpenBehaviorChoice()'],
    ['scriptExecutor == null || scriptExecutor.isShutdown()', '!queue.isAvailable()'],
    ['executeTask(scriptExecutor, new Runnable()', 'queue.execute(new Runnable()'],
    ['startActivityForResult(intent,', 'activity.startActivityForResult(intent,'],
    ['isFinishing() || isDestroyed()', 'activity.isFinishing() || activity.isDestroyed()'],
    ['safeMessage(result)', 'ErrorText.message(result, 180)'],
    ['HOME_WALLPAPER_REQUEST', 'REQUEST_WALLPAPER'],
    ['HOME_LOGO_REQUEST', 'REQUEST_LOGO'],
    ['nightMode', 'host.isNightMode()']
  ];
  for (const [before, after] of substitutions) text = text.split(before).join(after);
  return text;
}
function methods(text) {
  const found = [];
  const declarations = /^    private (?:static )?(?:final )?[\w<>.\[\]]+\s+\w+\([^;{]*\)\s*\{/gm;
  let match;
  while ((match = declarations.exec(text))) {
    const begin = match.index;
    const open = declarations.lastIndex - 1;
    const tail = stripComments(text.slice(open));
    const lexical = /"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[{}]/g;
    let braces = 0, token, close;
    while ((token = lexical.exec(tail))) {
      if (token[0] === '{') braces++;
      else if (token[0] === '}' && --braces === 0) { close = open + lexical.lastIndex; break; }
    }
    if (!close) throw new Error('Unclosed method');
    // stripComments preserves strings and may shorten comments, so locate through a lexical
    // scan of the unmodified tail for the actual end offset.
    let depth = 0;
    const raw = /"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/[^\r\n]*|\/\*[\s\S]*?\*\/|[{}]/g;
    raw.lastIndex = open;
    while ((token = raw.exec(text))) {
      if (token[0] === '{') depth++;
      else if (token[0] === '}' && --depth === 0) { close = raw.lastIndex; break; }
    }
    const signature = text.slice(begin, open).replace(/\s+/g, ' ').trim();
    found.push({signature, source: text.slice(begin, close)});
    declarations.lastIndex = close;
  }
  return found;
}
// Only these lifecycle guards may differ from the mechanically extracted baseline.
// Everything else in all migrated methods still goes through exact token comparison.
function withoutLifecycleGuards(method) {
  let source = method.source;
  const remove = (guard, count) => {
    if (source.split(guard).length - 1 !== count)
      throw new Error('Unexpected lifecycle guard count in ' + method.signature);
    source = source.split(guard).join('');
  };
  if (method.signature === 'private void importHomeImage(final Uri uri, final HomeImageStore.Kind kind)') {
    remove('if (isInactive()) return;', 2);
    remove('if (closed) return;', 2);
  } else if (method.signature === 'private void homeCustomizationChanged(String message, int returnSection)') {
    remove('if (isInactive()) return;', 1);
  } else if (method.signature === 'private void showHomeSection(int section)') {
    if (source.split('if (isInactive()) return;').length - 1 !== 1)
      throw new Error('Missing section lifecycle guard');
    source = source.replace('if (isInactive()) return;', 'if (activity.isFinishing() || activity.isDestroyed()) return;');
  }
  return source;
}
if (baseline) {
  const before = fs.readFileSync(path.resolve(baseline), 'utf8');
  const originalMethods = new Map(methods(before).map(method => [method.signature, method.source]));
  let compared = 0;
  const migrated = controller.slice(0, controller.indexOf('    private HomePageConfig homePageConfig()'));
  for (const method of methods(migrated)) {
    const original = originalMethods.get(method.signature);
    if (!original) continue; // Constructor/Host wrappers are the small new boundary.
    const expected = tokens(normalize(original));
    const actual = tokens(withoutLifecycleGuards(method));
    if (JSON.stringify(expected) !== JSON.stringify(actual)) {
      const index = expected.findIndex((token, i) => token !== actual[i]);
      throw new Error('Migrated method differs: ' + method.signature + '\nExpected: ' +
        expected.slice(Math.max(0, index - 8), index + 12).join(' ') + '\nActual: ' +
        actual.slice(Math.max(0, index - 8), index + 12).join(' '));
    }
    compared++;
  }
  if (compared !== 47) throw new Error('Expected all 47 migrated methods: ' + compared);
  // The return listener is a stateful inner class, so check it separately from outer methods.
  const returnClass = text => text.slice(text.indexOf('    /** One object handles both dialog cancellation paths'),
    text.indexOf('\n    }', text.indexOf('    /** One object handles both dialog cancellation paths')) + 6);
  if (JSON.stringify(tokens(returnClass(before))) !== JSON.stringify(tokens(returnClass(controller))))
    throw new Error('HomeDialogReturn changed');
  process.stdout.write('Home customization equivalence: ' + compared + ' migrated methods and return listener match baseline except explicit teardown guards\n');
}
const mainActivity = fs.readFileSync(path.join(production, 'MainActivity.java'), 'utf8');
const teardown = mainActivity.slice(mainActivity.indexOf('    protected void onDestroy()'));
const componentClose = teardown.indexOf('if (homeCustomizationController != null) homeCustomizationController.close();');
if (componentClose < 0 || componentClose < teardown.indexOf('activityDestroyed = true;') ||
    componentClose > teardown.indexOf('uiHandler.removeCallbacksAndMessages(null);'))
  throw new Error('Appearance controller must close after destruction flag and before clearing callbacks');
const sources = ['HomeCustomizationController.java', 'HomePageConfig.java', 'LogoMarkup.java',
  'CustomHomeCss.java', 'CustomHomeHtml.java', 'UiText.java', 'ErrorText.java'].map(file => path.join(production, file));
function source(relative, contents) {
  const file = path.join(out, relative);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, contents);
  sources.push(file);
}
source('android/content/Context.java', `package android.content;
public class Context {
 public final android.content.res.Resources resources = new android.content.res.Resources();
 public android.content.res.Resources getResources() { return resources; }
}`);
source('android/content/res/Resources.java', `package android.content.res;
public final class Resources {
 private final android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
 public android.util.DisplayMetrics getDisplayMetrics() { return metrics; }
}`);
source('android/util/DisplayMetrics.java', `package android.util;
public final class DisplayMetrics { public float density = 2f; }`);
source('android/net/Uri.java', `package android.net;
public final class Uri { public final String value; public Uri(String value) { this.value = value; } }`);
source('android/content/Intent.java', `package android.content;
public final class Intent {
 public static final String ACTION_OPEN_DOCUMENT = "android.intent.action.OPEN_DOCUMENT";
 public static final String CATEGORY_OPENABLE = "android.intent.category.OPENABLE";
 public static final int FLAG_GRANT_READ_URI_PERMISSION = 1;
 public String action, category, type; public int flags;
 private android.net.Uri data;
 public Intent() {} public Intent(String action) { this.action = action; }
 public Intent addCategory(String category) { this.category = category; return this; }
 public Intent setType(String type) { this.type = type; return this; }
 public Intent addFlags(int flags) { this.flags |= flags; return this; }
 public Intent setData(android.net.Uri data) { this.data = data; return this; }
 public android.net.Uri getData() { return data; }
}`);
source('android/content/SharedPreferences.java', `package android.content;
public interface SharedPreferences {
 String getString(String key, String fallback); boolean getBoolean(String key, boolean fallback);
 long getLong(String key, long fallback); Editor edit();
 interface Editor {
  Editor putString(String key, String value); Editor putBoolean(String key, boolean value);
  Editor putInt(String key, int value); Editor putLong(String key, long value); Editor remove(String key);
  void apply(); boolean commit();
 }
}`);
source('android/content/DialogInterface.java', `package android.content;
public interface DialogInterface {
 void dismiss();
 interface OnClickListener { void onClick(DialogInterface dialog, int which); }
 interface OnCancelListener { void onCancel(DialogInterface dialog); }
 interface OnShowListener { void onShow(DialogInterface dialog); }
}`);
source('android/app/Activity.java', `package android.app;
public final class Activity extends android.content.Context {
 public static final int RESULT_OK = -1;
 public boolean finishing, destroyed, failPicker;
 public int requestCode; public android.content.Intent pickerIntent;
 public boolean isFinishing() { return finishing; }
 public boolean isDestroyed() { return destroyed; }
 public void startActivityForResult(android.content.Intent intent, int code) {
  if (failPicker) throw new IllegalStateException("no picker"); pickerIntent = intent; requestCode = code;
 }
}`);
source('android/view/View.java', `package android.view;
public class View {
 private OnClickListener click;
 public interface OnClickListener { void onClick(View view); }
 public void setOnClickListener(OnClickListener click) { this.click = click; }
 public void performClick() { if (click != null) click.onClick(this); }
}`);
source('android/view/Gravity.java', `package android.view;
public final class Gravity { public static final int TOP = 48, START = 8388611; }`);
source('android/text/InputType.java', `package android.text;
public final class InputType {
 public static final int TYPE_CLASS_TEXT = 1, TYPE_TEXT_FLAG_MULTI_LINE = 2,
 TYPE_TEXT_FLAG_NO_SUGGESTIONS = 4, TYPE_TEXT_VARIATION_URI = 8;
}`);
source('android/graphics/Typeface.java', `package android.graphics;
public final class Typeface { public static final Typeface MONOSPACE = new Typeface(); }`);
source('android/graphics/drawable/GradientDrawable.java', `package android.graphics.drawable;
public final class GradientDrawable {
 public int color; public float radius;
 public void setColor(int color) { this.color = color; } public void setCornerRadius(float radius) { this.radius = radius; }
}`);
source('android/widget/EditText.java', `package android.widget;
public final class EditText extends android.view.View {
 private String text = "";
 public EditText(android.content.Context context) {}
 public void setText(String text) { this.text = text; } public CharSequence getText() { return text; }
 public int length() { return text.length(); } public void setSelection(int value) {}
 public void setSingleLine(boolean value) {} public void setSelectAllOnFocus(boolean value) {}
 public void setHint(String value) {} public void setMinLines(int value) {} public void setMaxLines(int value) {}
 public void setMinHeight(int value) {} public void setMaxHeight(int value) {}
 public void setVerticalScrollBarEnabled(boolean value) {} public void setHorizontallyScrolling(boolean value) {}
 public void setGravity(int value) {} public void setTypeface(android.graphics.Typeface value) {}
 public void setTextSize(float value) {} public void setTextColor(int value) {} public void setHintTextColor(int value) {}
 public void setBackground(android.graphics.drawable.GradientDrawable value) {} public void setInputType(int value) {}
 public void setPadding(int left, int top, int right, int bottom) {}
}`);
source('android/os/Handler.java', `package android.os;
import java.util.*;
public final class Handler {
 private final ArrayDeque<Runnable> tasks = new ArrayDeque<Runnable>();
 public boolean post(Runnable task) { tasks.add(task); return true; }
 public int pending() { return tasks.size(); }
 public void dispatch() { while (!tasks.isEmpty()) tasks.remove().run(); }
}`);
source('android/app/AlertDialog.java', `package android.app;
import android.content.DialogInterface;
import android.view.View;
import java.util.*;
public final class AlertDialog implements DialogInterface {
 public static final int BUTTON_POSITIVE = -1, BUTTON_NEGATIVE = -2, BUTTON_NEUTRAL = -3;
 public static AlertDialog latest;
 public String title, message; public String[] items; public int checked = -1;
 public View view; public boolean shown, dismissed;
 public DialogInterface.OnClickListener choice, positive, negative, neutral;
 public DialogInterface.OnCancelListener cancel; public DialogInterface.OnShowListener show;
 private final Map<Integer, View> buttons = new HashMap<Integer, View>();
 public void dismiss() { dismissed = true; }
 public void setOnCancelListener(DialogInterface.OnCancelListener value) { cancel = value; }
 public void setOnShowListener(DialogInterface.OnShowListener value) { show = value; }
 public void show() {
  shown = true; latest = this;
  for (final int code : new int[] {BUTTON_POSITIVE, BUTTON_NEGATIVE, BUTTON_NEUTRAL}) {
   View button = new View(); buttons.put(code, button);
   button.setOnClickListener(new View.OnClickListener() { public void onClick(View ignored) {
    DialogInterface.OnClickListener listener = code == BUTTON_POSITIVE ? positive : code == BUTTON_NEGATIVE ? negative : neutral;
    if (listener != null) listener.onClick(AlertDialog.this, code);
    dismiss();
   }});
  }
  if (show != null) show.onShow(this);
 }
 public View getButton(int code) { return buttons.get(code); }
 public void choose(int index) { choice.onClick(this, index); }
 public void cancel() { if (cancel != null) cancel.onCancel(this); }
 public static final class Builder {
  private final AlertDialog dialog = new AlertDialog();
  public Builder(android.content.Context context) {}
  public Builder setTitle(String text) { dialog.title = text; return this; }
  public Builder setMessage(String text) { dialog.message = text; return this; }
  public Builder setView(View view) { dialog.view = view; return this; }
  public Builder setPositiveButton(String text, DialogInterface.OnClickListener listener) { dialog.positive = listener; return this; }
  public Builder setNegativeButton(String text, DialogInterface.OnClickListener listener) { dialog.negative = listener; return this; }
  public Builder setNeutralButton(String text, DialogInterface.OnClickListener listener) { dialog.neutral = listener; return this; }
  public Builder setOnCancelListener(DialogInterface.OnCancelListener listener) { dialog.cancel = listener; return this; }
  public Builder setItems(String[] items, DialogInterface.OnClickListener listener) { dialog.items = items; dialog.choice = listener; return this; }
  public Builder setSingleChoiceItems(String[] items, int checked, DialogInterface.OnClickListener listener) {
   setItems(items, listener); dialog.checked = checked; return this;
  }
  public AlertDialog create() { return dialog; } public AlertDialog show() { dialog.show(); return dialog; }
 }
}`);
const iconNames = [...controller.matchAll(/BrowserIconView\.(\w+)/g)].map(match => match[1]);
source('com/xinyv/median/BrowserIconView.java', 'package com.xinyv.median;\nfinal class BrowserIconView {\n' +
  [...new Set(iconNames)].map((name, index) => ' static final int ' + name + ' = ' + index + ';').join('\n') + '\n}');
source('com/xinyv/median/HomeImageStore.java', `package com.xinyv.median;
final class HomeImageStore {
 enum Kind { WALLPAPER, LOGO }
 boolean wallpaper, logo, failSave; int saved, removed, removedAll; Kind savedKind; Runnable beforeSave;
 boolean has(Kind kind) { return kind == Kind.WALLPAPER ? wallpaper : logo; }
 void remove(Kind kind) { removed++; if (kind == Kind.WALLPAPER) wallpaper = false; else logo = false; }
 void removeAll() { removedAll++; wallpaper = logo = false; }
 void save(android.net.Uri uri, Kind kind) throws Exception {
  if (beforeSave != null) beforeSave.run();
  if (failSave) throw new Exception("bad-image"); saved++; savedKind = kind;
  if (kind == Kind.WALLPAPER) wallpaper = true; else logo = true;
 }
}`);
source('com/xinyv/median/HomeCustomizationBehaviorSelfTest.java', `package com.xinyv.median;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Handler;
import android.widget.EditText;
import java.util.*;
import java.util.concurrent.*;

public final class HomeCustomizationBehaviorSelfTest {
 static int cases;
 static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
 static final class Prefs implements SharedPreferences {
  final Map<String, Object> values = new HashMap<String, Object>(); boolean commits = true;
  public String getString(String key, String fallback) { Object value = values.get(key); return value == null ? fallback : (String) value; }
  public boolean getBoolean(String key, boolean fallback) { Object value = values.get(key); return value == null ? fallback : (Boolean) value; }
  public long getLong(String key, long fallback) { Object value = values.get(key); return value == null ? fallback : ((Number) value).longValue(); }
  public Editor edit() { return new Editor() {
   final Map<String, Object> updates = new LinkedHashMap<String, Object>();
   public Editor putString(String key, String value) { updates.put(key, value); return this; }
   public Editor putBoolean(String key, boolean value) { updates.put(key, value); return this; }
   public Editor putInt(String key, int value) { updates.put(key, value); return this; }
   public Editor putLong(String key, long value) { updates.put(key, value); return this; }
   public Editor remove(String key) { updates.put(key, null); return this; }
   public void apply() { for (Map.Entry<String, Object> entry : updates.entrySet()) {
    if (entry.getValue() == null) values.remove(entry.getKey()); else values.put(entry.getKey(), entry.getValue());
   }}
   public boolean commit() { if (!commits) return false; apply(); return true; }
  }; }
 }
 static final class Queue implements HomeCustomizationController.TaskQueue {
  boolean available = true, reject; final ArrayDeque<Runnable> tasks = new ArrayDeque<Runnable>();
  public boolean isAvailable() { return available; }
  public boolean execute(Runnable task) { if (reject) throw new IllegalStateException("busy"); tasks.add(task); return true; }
  void dispatch() { while (!tasks.isEmpty()) tasks.remove().run(); }
 }
 static final class Fixture implements HomeCustomizationController.Host {
  final Activity activity = new Activity(); final Prefs prefs = new Prefs();
  final HomeImageStore images = new HomeImageStore(); final Handler ui = new Handler();
  final Queue queue = new Queue(); final ArrayDeque<Runnable> transitions = new ArrayDeque<Runnable>();
  final HomeCustomizationController controller = new HomeCustomizationController(activity, prefs, images, ui, queue, this);
  String title; String[] items; Runnable back; HomeCustomizationController.SheetHandler handler;
  String toast = ""; int refreshes, homeOpen, searches, bookmarks, notifications, sheets; Runnable externalReturn;
  public HomePageConfig homePageConfig() {
   HomePageConfig d = HomePageConfig.defaults();
   return HomePageConfig.createPersonalized(prefs.getString("home_title", d.title), prefs.getString("home_subtitle", d.subtitle),
    prefs.getString("home_logo_style", d.logoStyle), prefs.getString("home_logo_code", d.logoCode), 0, 90,
    prefs.getString("home_accent", d.accent), 28, 0, "cover", prefs.getString("home_search_style", "solid"),
    "center", "rounded", 4, prefs.getBoolean("home_show_search", true), prefs.getBoolean("home_show_engines", true),
    true, true, prefs.getBoolean("home_show_clock", false), prefs.getBoolean("home_custom_html_enabled", false),
    images.wallpaper, images.logo, 0L, 0L, 0L, prefs.getString("home_logo_mode", "text"), 47, 720, 132, 96, 0,
    prefs.getString("home_custom_css", ""));
  }
  public boolean isNightMode() { return false; }
  public void refreshHomeIfVisible() { refreshes++; }
  public void toast(String message) { toast = message; notifications++; }
  public void postAfterUiTransition(Runnable action) { transitions.add(action); }
  public void showActionSheet(String title, String subtitle, String[] items, int[] icons, Runnable back,
      HomeCustomizationController.SheetHandler handler) { this.title = title; this.items = items; this.back = back; this.handler = handler; sheets++; }
  public String searchEngineLabel() { return "Google"; }
  public void showSearchEngineDialog(Runnable action) { searches++; externalReturn = action; }
  public void openBookmarkManager(Runnable action) { bookmarks++; externalReturn = action; }
  public String homeOpenBehaviorLabel() { return "打开主页"; }
  public void showHomeOpenBehaviorChoice() { homeOpen++; }
  void show() { controller.show(new Runnable() { public void run() {} }); }
  void item(int which) { handler.onItem(which); }
  void frames() { while (!transitions.isEmpty()) transitions.remove().run(); }
  AlertDialog dialog(String expected) { AlertDialog dialog = AlertDialog.latest; check(expected.equals(dialog.title), "dialog: " + dialog.title); return dialog; }
  void positive(String expected, String text) { AlertDialog dialog = dialog(expected); if (text != null) ((EditText) dialog.view).setText(text); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); }
  void backgroundPicker() { show(); item(4); item(0); }
 }
 public static void main(String[] args) throws Exception {
  mainNavigation(); layoutAndText(); logo(); searchAndShortcut(); css(); html(); imagePaths(); reset(); destroyedReturn(); imageLifecycle();
  System.out.println("HomeCustomizationController behavior: " + cases + " assertions passed");
 }
 static void expect(boolean condition, String message) { check(condition, message); cases++; }
 static void mainNavigation() {
  Fixture f = new Fixture(); final int[] back = {0}; Runnable returnAction = new Runnable() { public void run() { back[0]++; }};
  f.controller.show(returnAction);
  expect("主页与外观".equals(f.title) && f.items.length == 7 && f.back == returnAction, "main menu/back identity");
  f.item(1); expect(f.homeOpen == 1 && f.prefs.values.isEmpty(), "startup mode stays owned by host");
  f.controller.continueCustomization(); expect(f.transitions.size() == 1, "return deferred to frame");
  f.frames(); expect("主页与外观".equals(f.title), "host startup settings returns to main appearance panel");
  f.item(0); f.back.run(); expect("主页与外观".equals(f.title), "sheet back returns main immediately after dismissal");
  f.frames(); expect("主页与外观".equals(f.title), "sheet back does not queue a second return");
 }
 static void layoutAndText() {
  Fixture f = new Fixture(); f.show(); f.item(0); f.item(2);
  expect(f.prefs.getBoolean("home_show_clock", false) && f.refreshes == 1, "clock toggle saved and home refreshed");
  f.frames(); expect("页面布局".equals(f.title), "clock toggle returns layout");
  f.item(0); f.positive("主页副标题", "  hello\\nworld  "); f.frames();
  expect("hello world".equals(f.prefs.getString("home_subtitle", "")), "subtitle cleaned through production config");
  f.item(3); f.dialog("主页强调色").choose(2); f.frames();
  expect("green".equals(f.prefs.getString("home_accent", "")) && "页面布局".equals(f.title), "accent selection saved/return");
  f.item(0); f.dialog("主页副标题").cancel(); expect(f.transitions.size() == 1, "dialog cancel schedules return");
  f.frames(); expect("页面布局".equals(f.title), "dialog cancel retains section");
 }
 static void logo() {
  Fixture f = new Fixture(); f.show(); f.item(2); f.item(0); f.dialog("Logo 类型").choose(3); f.frames();
  expect("none".equals(f.prefs.getString("home_logo_mode", "")) && f.items.length == 1, "hidden logo changes menu action mapping");
  f.item(0); f.dialog("Logo 类型").choose(0); f.frames();
  expect("Median".equals(f.prefs.getString("home_title", "")) && Integer.valueOf(720).equals(f.prefs.values.get("home_logo_font_weight")), "default logo exact saved values");
  f.item(1); f.positive("Logo 文字", "New Logo"); f.frames();
  expect("New Logo".equals(f.prefs.getString("home_title", "")) && "text".equals(f.prefs.getString("home_logo_mode", "")), "logo title saves text mode");
  f.item(3); f.item(0); f.dialog("Logo 字号").choose(5); f.frames();
  expect(Integer.valueOf(64).equals(f.prefs.values.get("home_logo_font_size")) && "Logo".equals(f.title), "font selection saves and returns logo");
  f.item(4); f.positive("自定义文字 Logo", "[color=#4285F4]Hi[/color]"); f.frames();
  expect("custom".equals(f.prefs.getString("home_logo_style", "")) && "Hi".equals(f.prefs.getString("home_title", "")), "logo markup parsed by production validator");
 }
 static void searchAndShortcut() {
  Fixture f = new Fixture(); f.show(); f.item(3); f.item(0); f.dialog("搜索框").choose(2); f.frames();
  expect(!f.prefs.getBoolean("home_show_search", true) && "搜索框".equals(f.title), "search hide saves/returns");
  f.item(1); f.frames(); expect(!f.prefs.getBoolean("home_show_engines", true), "engine visibility toggle");
  f.item(2); expect(f.searches == 1, "default search setting delegated"); f.externalReturn.run();
  expect("搜索框".equals(f.title), "search settings return remains appearance section");
  f.show(); f.item(5); f.item(3); expect(f.bookmarks == 1, "bookmark manager delegated"); f.externalReturn.run();
  expect("快捷网站".equals(f.title), "bookmark return remains shortcuts section");
 }
 static void css() {
  Fixture f = new Fixture(); f.show(); f.item(6); f.item(0);
  AlertDialog editor = f.dialog("自定义 CSS"); f.positive("自定义 CSS", "body{background:url(https://example.com)}");
  expect(!editor.dismissed && f.toast.contains("外部 URL") && f.prefs.values.isEmpty(), "CSS external fetch remains rejected");
  f.prefs.values.put("home_custom_html_enabled", true); f.positive("自定义 CSS", ".brand{color:red}"); f.frames();
  expect(".brand{color:red}".equals(f.prefs.getString("home_custom_css", "")) && !f.prefs.getBoolean("home_custom_html_enabled", true), "CSS saves and disables full HTML");
  expect("自定义主页".equals(f.title), "CSS returns code section");
  f.item(0); f.dialog("自定义 CSS").choose(1); f.frames();
  expect(!f.prefs.values.containsKey("home_custom_css"), "clear CSS removes preference");
 }
 static void html() {
  Fixture f = new Fixture(); f.show(); f.item(6); f.item(1);
  AlertDialog editor = f.dialog("自定义主页 HTML"); f.prefs.commits = false; f.positive("自定义主页 HTML", "<h1>Hello</h1>");
  expect(!editor.dismissed && f.prefs.values.isEmpty() && "自定义主页保存失败".equals(f.toast), "failed HTML commit retains editor/data");
  f.prefs.commits = true; f.prefs.values.put("home_custom_html_version", Long.MAX_VALUE);
  f.positive("自定义主页 HTML", "<h1>Hello</h1>"); f.frames();
  expect(f.prefs.getBoolean("home_custom_html_enabled", false) && f.prefs.getLong("home_custom_html_version", 0L) == 1L, "HTML saved/version wraps");
  f.item(1); f.dialog("完整 HTML 页面").choose(1); f.frames();
  expect(!f.prefs.getBoolean("home_custom_html_enabled", true) && f.prefs.values.containsKey("home_custom_html"), "HTML disable retains source");
  f.item(1); f.dialog("完整 HTML 页面").choose(2); f.positive("删除自定义主页？", null); f.frames();
  expect(!f.prefs.values.containsKey("home_custom_html") && !f.prefs.values.containsKey("home_custom_html_version"), "HTML removal clears source/version");
 }
 static void imagePaths() {
  Fixture f = new Fixture(); f.backgroundPicker();
  expect(f.activity.requestCode == 409 && "image/*".equals(f.activity.pickerIntent.type) && f.activity.pickerIntent.flags == 1, "wallpaper picker intent/request contract");
  f.controller.onImagePickerResult(HomeImageStore.Kind.WALLPAPER, 0, null); f.frames();
  expect("背景".equals(f.title), "picker cancellation restores originating background section");
  f.backgroundPicker(); f.controller.onImagePickerResult(HomeImageStore.Kind.WALLPAPER, Activity.RESULT_OK, new Intent().setData(new Uri("wall")));
  expect(f.queue.tasks.size() == 1 && f.images.saved == 0, "image decoding remains on shared worker queue");
  f.queue.dispatch(); expect(f.images.saved == 1 && f.ui.pending() == 1 && f.refreshes == 0, "image success waits for UI publication");
  f.ui.dispatch(); f.frames(); expect("背景".equals(f.title) && f.refreshes == 1 && "主页壁纸已更新".equals(f.toast), "wallpaper saved/refresh/return order");
  Fixture logo = new Fixture(); logo.show(); logo.item(2); logo.item(0); logo.dialog("Logo 类型").choose(2);
  expect(logo.activity.requestCode == 410, "logo picker request");
  logo.controller.onImagePickerResult(HomeImageStore.Kind.LOGO, Activity.RESULT_OK, new Intent().setData(new Uri("logo")));
  logo.queue.dispatch(); logo.ui.dispatch(); logo.frames();
  expect("image".equals(logo.prefs.getString("home_logo_mode", "")) && "Logo".equals(logo.title), "logo result uses component pending return section");
  Fixture unavailable = new Fixture(); unavailable.backgroundPicker(); unavailable.queue.available = false;
  unavailable.controller.onImagePickerResult(HomeImageStore.Kind.WALLPAPER, Activity.RESULT_OK, new Intent().setData(new Uri("wall"))); unavailable.frames();
  expect("无法读取所选图片".equals(unavailable.toast) && "背景".equals(unavailable.title), "unavailable worker keeps previous prompt/return");
  Fixture busy = new Fixture(); busy.backgroundPicker(); busy.queue.reject = true;
  busy.controller.onImagePickerResult(HomeImageStore.Kind.WALLPAPER, Activity.RESULT_OK, new Intent().setData(new Uri("wall"))); busy.frames();
  expect("图片处理队列繁忙，请稍后重试".equals(busy.toast) && "背景".equals(busy.title), "queue exception prompt/return");
  Fixture failure = new Fixture(); failure.backgroundPicker(); failure.images.failSave = true;
  failure.controller.onImagePickerResult(HomeImageStore.Kind.WALLPAPER, Activity.RESULT_OK, new Intent().setData(new Uri("wall")));
  failure.queue.dispatch(); failure.ui.dispatch(); failure.frames();
  expect("图片处理失败：bad-image".equals(failure.toast) && "背景".equals(failure.title) && failure.refreshes == 0, "image processing failure returns section");
  Fixture noPicker = new Fixture(); noPicker.activity.failPicker = true; noPicker.backgroundPicker(); noPicker.frames();
  expect("没有可用的图片选择器".equals(noPicker.toast) && "背景".equals(noPicker.title), "missing picker return");
 }
 static void reset() {
  Fixture f = new Fixture(); f.prefs.values.put("home_title", "Custom"); f.prefs.values.put("home_custom_html", "<h1>X</h1>");
  f.prefs.values.put("home_open_mode", "keep_last"); f.prefs.values.put("restore_tabs", true); f.prefs.values.put("bookmark_data", "retained");
  f.show(); f.item(0); f.item(5); f.positive("恢复默认主页？", null); f.frames();
  expect(!f.prefs.values.containsKey("home_title") && !f.prefs.values.containsKey("home_custom_html") && f.images.removedAll == 1, "reset appearance clears customization/assets");
  expect(f.prefs.getBoolean("restore_tabs", false) && f.prefs.values.containsKey("home_open_mode") && "retained".equals(f.prefs.values.get("bookmark_data")), "reset does not own startup settings/bookmarks");
  expect("页面布局".equals(f.title), "reset returns originating layout section");
 }
 static void destroyedReturn() {
  Fixture f = new Fixture(); f.show(); f.item(0); f.item(2); f.activity.destroyed = true; f.frames();
  expect("页面布局".equals(f.title), "queued return does not reopen UI after Activity destruction");
 }
 static Fixture pendingLogo() {
  Fixture f = new Fixture(); f.backgroundPicker();
  f.prefs.values.put("home_logo_mode", "text");
  f.controller.onImagePickerResult(HomeImageStore.Kind.LOGO, Activity.RESULT_OK, new Intent().setData(new Uri("logo")));
  return f;
 }
 static void noImagePublication(Fixture f, int notifications, int sheets, String reason) {
  expect(f.refreshes == 0 && "text".equals(f.prefs.getString("home_logo_mode", "")), reason + " leaves homepage and logo preference untouched");
  expect(f.notifications == notifications && f.sheets == sheets && f.transitions.isEmpty(), reason + " does not call old Activity or queue a return");
 }
 static void imageLifecycle() throws Exception {
  Fixture queued = pendingLogo(); int notifications = queued.notifications, sheets = queued.sheets;
  queued.controller.close(); queued.controller.close(); queued.queue.dispatch(); queued.ui.dispatch(); queued.frames();
  expect(queued.images.saved == 0 && queued.ui.pending() == 0, "close skips image work still queued on executor");
  noImagePublication(queued, notifications, sheets, "queued image close");

  final Fixture inFlight = pendingLogo(); final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
  inFlight.images.beforeSave = new Runnable() { public void run() {
   entered.countDown();
   try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("close waited for image IO"); }
   catch (InterruptedException error) { throw new AssertionError(error); }
  }};
  final Runnable imageTask = inFlight.queue.tasks.remove(); final Throwable[] failure = {null};
  Thread worker = new Thread(new Runnable() { public void run() { try { imageTask.run(); } catch (Throwable error) { failure[0] = error; } }});
  worker.start(); expect(entered.await(5, TimeUnit.SECONDS), "image worker entered blocking IO");
  notifications = inFlight.notifications; sheets = inFlight.sheets;
  inFlight.controller.close(); expect(worker.isAlive(), "close returns while existing disk IO is still running");
  release.countDown(); worker.join(5000);
  expect(!worker.isAlive() && failure[0] == null && inFlight.images.saved == 1 && inFlight.ui.pending() == 0, "in-flight image may finish disk write but cannot publish after close");
  inFlight.ui.dispatch(); inFlight.frames(); noImagePublication(inFlight, notifications, sheets, "in-flight image close");

  Fixture posted = pendingLogo(); posted.queue.dispatch();
  expect(posted.ui.pending() == 1, "successful image result is posted before teardown");
  notifications = posted.notifications; sheets = posted.sheets; posted.controller.close(); posted.ui.dispatch(); posted.frames();
  noImagePublication(posted, notifications, sheets, "already-posted image close");

  Fixture failed = pendingLogo(); failed.images.failSave = true; failed.queue.dispatch();
  notifications = failed.notifications; sheets = failed.sheets; failed.controller.close(); failed.ui.dispatch(); failed.frames();
  noImagePublication(failed, notifications, sheets, "already-posted failed image close");

  Fixture cancelled = new Fixture(); cancelled.backgroundPicker();
  cancelled.controller.onImagePickerResult(HomeImageStore.Kind.WALLPAPER, 0, null);
  expect(cancelled.transitions.size() == 1, "picker cancellation queued its normal return");
  sheets = cancelled.sheets; cancelled.controller.close(); cancelled.frames();
  expect(cancelled.sheets == sheets && cancelled.refreshes == 0, "closing prevents a cancellation return already queued for a frame");
  notifications = cancelled.notifications;
  cancelled.controller.onImagePickerResult(HomeImageStore.Kind.LOGO, 0, null);
  cancelled.controller.onImagePickerResult(HomeImageStore.Kind.LOGO, Activity.RESULT_OK, new Intent().setData(new Uri("late")));
  cancelled.controller.continueCustomization(); cancelled.controller.show(new Runnable() { public void run() {} });
  expect(cancelled.queue.tasks.isEmpty() && cancelled.transitions.isEmpty() && cancelled.sheets == sheets && cancelled.notifications == notifications, "closed component ignores late picker cancellation, result, and reopening");

  Fixture destroyed = pendingLogo(); destroyed.queue.dispatch(); destroyed.activity.destroyed = true;
  notifications = destroyed.notifications; sheets = destroyed.sheets; destroyed.ui.dispatch(); destroyed.frames();
  noImagePublication(destroyed, notifications, sheets, "Activity-destroyed image result");
 }
}
`);
function run(command, arguments_) {
  const result = cp.spawnSync(command, arguments_, { encoding: 'utf8', windowsHide: true });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(result.stdout + result.stderr);
  if (result.stdout) process.stdout.write(result.stdout);
}
run(executable('javac'), ['-encoding', 'UTF-8', '--release', '17', '-d', out, ...sources]);
run(executable('java'), ['-cp', out, 'com.xinyv.median.HomeCustomizationBehaviorSelfTest']);
