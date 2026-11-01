'use strict';

// Compile the production scheduler and LocalDataIo against a controllable Android queue.
// Dispatch runs on a worker, so a blocked flush can expose caller-thread or locking regressions.
const fs = require('fs');
const path = require('path');
const os = require('os');
const cp = require('child_process');
const args = process.argv.slice(2);
const option = (name, fallback) => args.includes(name) ? args[args.indexOf(name) + 1] : fallback;
const repo = path.resolve(__dirname, '../..');
const out = path.resolve(option('--out', fs.mkdtempSync(path.join(os.tmpdir(), 'median-cookies-'))));
const jdk = option('--jdk', process.env.JAVA_HOME || '');
const executable = name => jdk ? path.join(jdk, 'bin', name + (process.platform === 'win32' ? '.exe' : '')) : name;
fs.mkdirSync(out, { recursive: true });
const sources = [];
function source(relative, contents) {
  const file = path.join(out, relative);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, contents);
  sources.push(file);
}
source('android/os/Process.java', `package android.os;
public final class Process {
 public static final int THREAD_PRIORITY_BACKGROUND = 10;
 public static void setThreadPriority(int value) {}
}`);
source('android/os/Looper.java', `package android.os;
public final class Looper {}`);
source('android/os/HandlerThread.java', `package android.os;
public final class HandlerThread {
 public static int started, stopped;
 private final Looper looper = new Looper();
 public HandlerThread(String name, int priority) {}
 public void start() { started++; }
 public Looper getLooper() { return looper; }
 public boolean quitSafely() { stopped++; return true; }
}`);
source('android/os/Handler.java', `package android.os;
import java.util.ArrayDeque;
public final class Handler {
 private static final ArrayDeque<Runnable> queue = new ArrayDeque<Runnable>();
 public static long delay;
 public static boolean rejectNext;
 public Handler(Looper looper) {}
 public boolean postDelayed(Runnable task, long ms) {
  synchronized (queue) {
   if (rejectNext) { rejectNext = false; return false; }
   delay = ms; queue.add(task); return true;
  }
 }
 public static int pending() { synchronized (queue) { return queue.size(); } }
 public static Thread dispatchNext() {
  final Runnable next;
  synchronized (queue) { next = queue.remove(); }
  Thread worker = new Thread(next, "test-local-io");
  worker.setDaemon(true); worker.start(); return worker;
 }
}`);
source('android/webkit/CookieManager.java', `package android.webkit;
public final class CookieManager {
 private static final CookieManager instance = new CookieManager();
 public static volatile Thread flushedOn;
 public static CookieManager getInstance() { return instance; }
 public void flush() { flushedOn = Thread.currentThread(); }
}`);
sources.push(path.join(repo, 'app/src/main/java/com/xinyv/median/CookieFlushScheduler.java'));
sources.push(path.join(repo, 'app/src/main/java/com/xinyv/median/LocalDataIo.java'));
sources.push(path.join(__dirname, 'CookieFlushSchedulerSelfTest.java'));
function run(executable, arguments_) {
  const result = cp.spawnSync(executable, arguments_, { encoding: 'utf8', windowsHide: true });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(result.stdout + result.stderr);
  if (result.stdout) process.stdout.write(result.stdout);
}
run(executable('javac'), ['-encoding', 'UTF-8', '--release', '17', '-d', out, ...sources]);
run(executable('java'), ['-cp', out, 'com.xinyv.median.CookieFlushSchedulerSelfTest']);
