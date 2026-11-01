'use strict';
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const cp = require('node:child_process');
const project = path.resolve(__dirname, '../..');
const out = fs.mkdtempSync(path.join(os.tmpdir(), 'median-bounded-reader-'));
const jdk = process.env.JAVA_HOME || 'D:/MedianToolchain/jdk17';
function run(name, args) {
  const result = cp.spawnSync(path.join(jdk, 'bin', name + (process.platform === 'win32' ? '.exe' : '')), args,
    { encoding: 'utf8', maxBuffer: 4 * 1024 * 1024 });
  if (result.status !== 0) throw result.error || new Error(result.stderr || result.stdout);
  process.stdout.write(result.stdout);
}
try {
  run('javac', ['-encoding', 'UTF-8', '--release', '8', '-d', out,
    path.join(project, 'app/src/main/java/com/xinyv/median/BoundedFileReader.java'),
    path.join(__dirname, 'BoundedFileReaderSelfTest.java')]);
  run('java', ['-Xmx192m', '-cp', out, 'com.xinyv.median.BoundedFileReaderSelfTest']);
} finally {
  fs.rmSync(out, { recursive: true, force: true });
}
