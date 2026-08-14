package com.xinyv.median;

import android.util.AtomicFile;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Shared bounded UTF-8 persistence for the browser library and user scripts. */
final class AtomicTextFile {
    static String read(AtomicFile file, int maxBytes) {
        try {
            return new String(NetworkSecurity.readBounded(file.openRead(), maxBytes,
                    "local data too large"), StandardCharsets.UTF_8);
        } catch (Exception ignored) { return null; }
    }

    static boolean write(AtomicFile file, String value) {
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(value.getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
            return true;
        } catch (Exception ignored) {
            if (output != null) file.failWrite(output);
            return false;
        }
    }

    private AtomicTextFile() {}
}
