'use strict';

// Compile the real loader with controllable stores and queues. These cases verify cancellation
// and transfer of store ownership, including when Activity teardown discards queued UI callbacks.
const fs = require('fs');
const path = require('path');
const os = require('os');
const cp = require('child_process');
const args = process.argv.slice(2);
const option = (name, fallback) => args.includes(name) ? args[args.indexOf(name) + 1] : fallback;
const repo = path.resolve(__dirname, '../..');
const out = path.resolve(option('--out', fs.mkdtempSync(path.join(os.tmpdir(), 'median-startup-loader-'))));
const jdk = option('--jdk', process.env.JAVA_HOME || '');
const executable = name => jdk ? path.join(jdk, 'bin', name + (process.platform === 'win32' ? '.exe' : '')) : name;
const sources = [path.join(repo, 'app/src/main/java/com/xinyv/median/StartupDataLoader.java')];
function source(relative, contents) {
  const file = path.join(out, relative);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, contents);
  sources.push(file);
}
source('android/content/Context.java', `package android.content;
public class Context {
 public Context application = this;
 public Context getApplicationContext() { return application; }
}`);
source('android/content/SharedPreferences.java', `package android.content;
public interface SharedPreferences { String getString(String key, String fallback); }
`);
source('android/os/Handler.java', `package android.os;
import java.util.ArrayDeque;
public final class Handler {
 private final ArrayDeque<Runnable> tasks = new ArrayDeque<Runnable>();
 public boolean reject;
 public boolean dispatching;
 public synchronized boolean post(Runnable task) {
  if (reject) return false;
  tasks.add(task); return true;
 }
 public synchronized int pending() { return tasks.size(); }
 public synchronized void clear() { tasks.clear(); }
 public void dispatch() {
  for (;;) {
   Runnable task;
   synchronized (this) { task = tasks.poll(); }
   if (task == null) return;
   dispatching = true;
   try { task.run(); } finally { dispatching = false; }
  }
 }
}`);
source('com/xinyv/median/StartupDataLoaderBehaviorSelfTest.java', `package com.xinyv.median;
import android.content.*;
import android.os.Handler;
import java.util.*;
import java.util.concurrent.*;

public final class StartupDataLoaderBehaviorSelfTest {
 static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
 static final class Queue implements StartupDataLoader.TaskQueue {
  final ArrayList<Runnable> tasks = new ArrayList<Runnable>();
  int submissions;
  int reject = -1, throwing = -1;
  public boolean execute(Runnable task) {
   int index = submissions++;
   if (index == throwing) throw new RejectedExecutionException();
   if (index == reject) return false;
   tasks.add(task); return true;
  }
  void run(int index) { Runnable task = tasks.get(index); tasks.set(index, null); task.run(); }
 }
 static final class Fixture implements StartupDataLoader.Listener {
  final Handler ui = new Handler();
  final Queue queue = new Queue();
  final Context application = new Context();
  final Context activity = new Context();
  final StartupDataLoader loader;
  StartupDataLoader.Result result;
  ConnectionHintsStore hints;
  int loaded, failed, workersFinished, hintsReady;
  boolean accept = true, closeDuringLoaded, throwDuringLoaded;
  Fixture() {
   Hooks.reset();
   activity.application = application;
   Hooks.application = application;
   Hooks.ui = ui;
   loader = new StartupDataLoader(activity, new SharedPreferences() {
    public String getString(String key, String fallback) {
     check("connection_hints_v1".equals(key), "shared connection-hints preference key");
     if (Hooks.failure.equals("preferences")) throw new IllegalStateException();
     return "saved-hints";
    }
   }, ui, queue, this);
  }
  public void onConnectionHintsReady(ConnectionHintsStore value) {
   check(ui.dispatching, "hints must run on UI queue"); hintsReady++; hints = value;
  }
  public boolean onLoaded(StartupDataLoader.Result value) {
   check(ui.dispatching, "result must run on UI queue"); loaded++; result = value;
   if (closeDuringLoaded) loader.close();
   if (throwDuringLoaded) throw new IllegalStateException("listener failure");
   return accept;
  }
  public void onFailed() { check(ui.dispatching, "failure must run on UI queue"); failed++; }
  public void onWorkersFinished() { workersFinished++; }
  void both(boolean scriptsFirst) { queue.run(scriptsFirst ? 1 : 0); queue.run(scriptsFirst ? 0 : 1); }
 }
 public static void main(String[] args) throws Exception {
  completionOrder(false);
  completionOrder(true);
  earlyHints();
  rejection(0, false); rejection(1, false); rejection(0, true); rejection(1, true);
  bothRejected();
  for (String failure : new String[] {"data", "folders", "sites", "scripts"}) storeFailure(failure);
  payloadFallback("payloads"); payloadFallback("enabled");
  optionalHintsFailure("preferences"); optionalHintsFailure("hints");
  disabledScripts();
  closeBeforeStart(); closeBeforeWorkers(); closeBetweenWorkers();
  closeAfterEarlyHints();
  closePendingDelivery(false); closePendingDelivery(true);
  refusedDelivery(false); refusedDelivery(true); exceptionalDelivery();
  rejectedUi();
  cancelInFlight("data"); cancelInFlight("sites"); cancelInFlight("scripts");
  cancelInFlight("payloads");
  System.out.println("StartupDataLoader behavior: 32 scenarios passed");
 }
 static void completionOrder(boolean scriptsFirst) {
  Fixture f = new Fixture(); f.loader.start(); f.loader.start();
  check(f.queue.submissions == 2, "start must be idempotent and retain two tasks");
  f.queue.run(scriptsFirst ? 1 : 0);
  check(f.workersFinished == 0 && f.loaded == 0, "one worker must not complete startup");
  f.queue.run(scriptsFirst ? 0 : 1);
  check(f.workersFinished == 1 && f.loaded == 0, "workers finish independently of UI delivery");
  f.ui.dispatch();
  check(f.loaded == 1 && f.failed == 0 && f.hintsReady == 1, "exactly one successful result");
  check(f.result.connectionHints == f.hints, "early hints and final result share the decoded cache");
  check("prepared-token-0123456789abcdef0123456789abcdef".equals(f.result.scriptToken), "prepared token");
  check(f.result.scriptPayloads.size() == 1 && Hooks.builds == 1, "payloads built once in worker");
  check(Hooks.created == 3 && Hooks.closed == 0, "all closeable stores transferred");
  f.loader.close();
  check(Hooks.closed == 0, "loader must not close transferred stores");
  f.result.close(); check(Hooks.closed == 3, "owner closes transferred stores");
 }
 static void earlyHints() {
  Fixture f = new Fixture();
  Hooks.onSites = new Runnable() { public void run() {
   check(f.ui.pending() == 1, "hints posted before settings decode");
   f.ui.dispatch();
   check(f.hintsReady == 1 && Hooks.builds == 0, "hints usable before script preparation");
  }};
  f.loader.start(); f.both(true); f.ui.dispatch(); f.result.close(); f.loader.close();
 }
 static void rejection(int index, boolean throwing) {
  Fixture f = new Fixture();
  if (throwing) f.queue.throwing = index; else f.queue.reject = index;
  f.loader.start(); f.queue.run(0);
  check(f.workersFinished == 1, "rejected submission must not leave completion waiting");
  f.ui.dispatch();
  check(f.failed == 1 && f.loaded == 0, "rejected worker gives one failure");
  check(Hooks.closed == Hooks.created, "partial stores closed after rejection"); f.loader.close();
 }
 static void storeFailure(String stage) {
  Fixture f = new Fixture(); Hooks.failure = stage;
  f.loader.start(); f.both(false); f.ui.dispatch();
  check(f.workersFinished == 1 && f.failed == 1 && f.loaded == 0, "store failure completes: " + stage);
  check(Hooks.closed == Hooks.created, "store failure closes partial stores: " + stage); f.loader.close();
 }
 static void bothRejected() {
  Fixture f = new Fixture(); f.queue.reject = 0; f.queue.throwing = 1;
  f.loader.start(); f.ui.dispatch();
  check(f.workersFinished == 1 && f.failed == 1 && Hooks.created == 0,
    "both rejected submissions still finish once"); f.loader.close();
 }
 static void payloadFallback(String stage) {
  Fixture f = new Fixture(); Hooks.failure = stage;
  f.loader.start(); f.both(false); f.ui.dispatch();
  check(f.loaded == 1 && f.failed == 0, "optional payload preparation fallback: " + stage);
  check(f.result.scriptToken.isEmpty() && f.result.scriptPayloads.isEmpty(), "empty fallback payloads");
  f.loader.close(); f.result.close(); check(Hooks.closed == 3, "fallback ownership");
 }
 static void optionalHintsFailure(String stage) {
  Fixture f = new Fixture(); Hooks.failure = stage;
  f.loader.start(); f.both(true); f.ui.dispatch();
  check(f.loaded == 1 && f.hintsReady == 1, "hints failure must not fail stores: " + stage);
  f.result.close(); f.loader.close();
 }
 static void disabledScripts() {
  Fixture f = new Fixture(); Hooks.enabled = false;
  f.loader.start(); f.both(false); f.ui.dispatch();
  check(f.result.scriptToken.isEmpty() && f.result.scriptPayloads.isEmpty() && Hooks.builds == 0,
    "disabled scripts skip payload generation"); f.result.close(); f.loader.close();
 }
 static void closeBeforeStart() {
  Fixture f = new Fixture(); f.loader.close(); f.loader.start();
  check(f.queue.submissions == 0 && Hooks.created == 0, "closed loader must not submit work");
 }
 static void closeBeforeWorkers() {
  Fixture f = new Fixture(); f.loader.start(); f.loader.close(); f.both(false); f.ui.dispatch();
  check(Hooks.created == 0 && f.loaded == 0 && f.failed == 0 && f.hintsReady == 0,
    "queued workers cannot create or publish after close");
  check(f.workersFinished == 1, "cancelled workers still retire executor");
 }
 static void closeBetweenWorkers() {
  Fixture f = new Fixture(); f.loader.start(); f.queue.run(0); f.loader.close();
  check(Hooks.closed == 1, "close finds first worker stores before second finishes");
  f.queue.run(1); f.ui.dispatch();
  check(Hooks.created == 1 && f.loaded == 0 && f.hintsReady == 0, "second worker cancelled");
 }
 static void closePendingDelivery(boolean discardMessages) {
  Fixture f = new Fixture(); f.loader.start(); f.both(false);
  check(f.ui.pending() == 2 && Hooks.closed == 0, "decoded stores await UI delivery");
  if (discardMessages) f.ui.clear();
  f.loader.close(); f.loader.close(); f.ui.dispatch();
  check(Hooks.closed == 3 && f.loaded == 0 && f.failed == 0 && f.hintsReady == 0,
    "teardown owns undelivered stores even when Handler messages were cleared");
 }
 static void closeAfterEarlyHints() {
  Fixture f = new Fixture(); f.loader.start(); f.queue.run(1); f.ui.dispatch();
  check(f.hintsReady == 1 && f.loaded == 0, "early hints precede final data delivery");
  f.loader.close(); f.queue.run(0); f.ui.dispatch();
  check(f.hintsReady == 1 && f.loaded == 0 && Hooks.closed == 2 && Hooks.created == 2,
    "close after early hint callback cleans partial data and prevents remaining decode");
 }
 static void refusedDelivery(boolean reentrantClose) {
  Fixture f = new Fixture(); f.accept = false; f.closeDuringLoaded = reentrantClose;
  f.loader.start(); f.both(false); f.ui.dispatch(); f.loader.close();
  check(f.loaded == 1 && Hooks.closed == 3, "refused delivery must close once");
  if (reentrantClose) return;
  Fixture accepted = new Fixture(); accepted.closeDuringLoaded = true;
  accepted.loader.start(); accepted.both(false); accepted.ui.dispatch();
  check(Hooks.closed == 0, "reentrant close must not close accepted delivery");
  accepted.result.close(); check(Hooks.closed == 3, "reentrant accepted stores belong to listener");
 }
 static void exceptionalDelivery() {
  Fixture f = new Fixture(); f.throwDuringLoaded = true;
  f.loader.start(); f.both(false);
  boolean threw = false;
  try { f.ui.dispatch(); } catch (IllegalStateException expected) { threw = true; }
  f.loader.close();
  check(threw && Hooks.closed == 3, "listener exception cannot leak stores");
 }
 static void rejectedUi() {
  Fixture f = new Fixture(); f.ui.reject = true;
  f.loader.start(); f.both(false); f.ui.dispatch();
  check(f.workersFinished == 1 && Hooks.closed == 3 && f.loaded == 0,
    "dead UI queue cannot leak stores or keep worker retirement waiting"); f.loader.close();
 }
 static void cancelInFlight(final String stage) throws Exception {
  final Fixture f = new Fixture();
  Hooks.block = stage;
  Hooks.entered = new CountDownLatch(1); Hooks.release = new CountDownLatch(1);
  f.loader.start();
  final int workerIndex = stage.equals("data") ? 0 : 1;
  Thread worker = new Thread(new Runnable() { public void run() { f.queue.run(workerIndex); }}, "decode-test");
  worker.start();
  check(Hooks.entered.await(2, TimeUnit.SECONDS), "decode entered: " + stage);
  f.loader.close();
  Hooks.release.countDown(); worker.join(2000);
  check(!worker.isAlive(), "cancelled worker terminates: " + stage);
  f.queue.run(workerIndex == 0 ? 1 : 0); f.ui.dispatch();
  check(f.loaded == 0 && f.failed == 0 && f.hintsReady == 0 && f.workersFinished == 1,
    "in-flight close prevents callbacks: " + stage);
  check(Hooks.closed == Hooks.created, "in-flight result cleaned: " + stage);
  if (stage.equals("data")) check(Hooks.created == 1, "cancel prevents folder construction");
  if (stage.equals("sites")) check(Hooks.created == 1, "cancel prevents script construction");
  if (stage.equals("scripts")) check(Hooks.builds == 0, "cancel prevents payload preparation");
 }
}

final class Hooks {
 static String failure, block;
 static int created, closed, builds;
 static boolean enabled;
 static Context application;
 static Handler ui;
 static Runnable onSites;
 static CountDownLatch entered, release;
 static void reset() {
  failure = ""; block = ""; created = closed = builds = 0; enabled = true;
  onSites = null; entered = release = null;
 }
 static void stage(String stage) {
  if (stage.equals(block)) {
   entered.countDown();
   try { if (!release.await(2, TimeUnit.SECONDS)) throw new AssertionError("blocked decode timeout"); }
   catch (InterruptedException e) { throw new AssertionError(e); }
  }
  if (stage.equals(failure)) throw new IllegalStateException(stage);
 }
}
class TestStore {
 private boolean closed;
 TestStore(Context context, String stage) {
  StartupDataLoaderBehaviorSelfTest.check(context == Hooks.application, "application context only");
  Hooks.stage(stage); Hooks.created++;
 }
 void close() {
  StartupDataLoaderBehaviorSelfTest.check(!closed, "store closed more than once");
  closed = true; Hooks.closed++;
 }
}
final class BrowserDataStore extends TestStore { BrowserDataStore(Context c) { super(c, "data"); } }
final class BookmarkFolderStore {
 BookmarkFolderStore(Context c) {
  StartupDataLoaderBehaviorSelfTest.check(c == Hooks.application, "folders use application context");
  Hooks.stage("folders");
 }
}
final class SiteSettingsStore extends TestStore {
 SiteSettingsStore(Context c) { super(c, "sites"); if (Hooks.onSites != null) Hooks.onSites.run(); }
}
final class UserScriptStore extends TestStore {
 UserScriptStore(Context c) { super(c, "scripts"); }
 boolean hasEnabledScripts() { Hooks.stage("enabled"); return Hooks.enabled; }
 List<DocumentStartPayload> buildDocumentStartPayloads(String token) {
  Hooks.builds++; Hooks.stage("payloads");
  return Collections.singletonList(new DocumentStartPayload());
 }
 static final class DocumentStartPayload {}
}
final class ConnectionHintsStore {
 void restore(String raw, long now) {
  StartupDataLoaderBehaviorSelfTest.check("saved-hints".equals(raw) && now > 0, "hints restored");
  Hooks.stage("hints");
 }
}
final class NavigationConnectionHints { static final String PREF_CONNECTION_HINTS = "connection_hints_v1"; }
final class UrlCleaner {
 static String randomToken() { return "prepared-token-0123456789abcdef0123456789abcdef"; }
}
`);
function run(command, arguments_) {
  const result = cp.spawnSync(command, arguments_, { encoding: 'utf8', windowsHide: true });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(result.stdout + result.stderr);
  if (result.stdout) process.stdout.write(result.stdout);
}
run(executable('javac'), ['-encoding', 'UTF-8', '--release', '17', '-d', out, ...sources]);
run(executable('java'), ['-cp', out, 'com.xinyv.median.StartupDataLoaderBehaviorSelfTest']);
