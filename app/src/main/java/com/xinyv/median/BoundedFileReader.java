package com.xinyv.median;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/** Reads local files without growing and cloning a buffer for their usual stable size. */
final class BoundedFileReader {
    private BoundedFileReader() { }

    static byte[] read(File file, int maxBytes, String sizeError) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            // Use the opened file, so an atomic replacement cannot change this size hint.
            return readFully(input, input.getChannel().size(), maxBytes, sizeError);
        }
    }

    /** The caller owns input; expectedLength is only a hint, never an EOF or size check. */
    static byte[] readFully(InputStream input, long expectedLength, int maxBytes,
                            String sizeError) throws IOException {
        if (maxBytes < 0) throw new IllegalArgumentException("Negative maximum size");
        int capacity = (int) Math.min(Math.max(0L, expectedLength), (long) maxBytes);
        byte[] bytes = new byte[capacity];
        int total = 0;
        while (true) {
            if (total == bytes.length) {
                int next = input.read();
                if (next < 0) return bytes;
                if (total == maxBytes) throw new IllegalArgumentException(sizeError);
                int grown = (int) Math.min((long) maxBytes,
                        Math.max(16384L, Math.max((long) total + 1, (long) total * 2)));
                bytes = Arrays.copyOf(bytes, grown);
                bytes[total++] = (byte) next;
            } else {
                int read = input.read(bytes, total, bytes.length - total);
                if (read < 0) return Arrays.copyOf(bytes, total);
                if (read == 0) {
                    // A defensive fallback for streams that do not make bulk-read progress.
                    int next = input.read();
                    if (next < 0) return Arrays.copyOf(bytes, total);
                    bytes[total++] = (byte) next;
                } else {
                    total += read;
                }
            }
        }
    }
}
