package com.xinyv.median;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class BoundedFileReaderSelfTest {
    private static final String SIZE_ERROR = "too large";

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static byte[] bytes(int size) {
        byte[] result = new byte[size];
        for (int i = 0; i < size; i++) result[i] = (byte) (i * 37);
        return result;
    }

    private static void checkRead(byte[] source, long hint, int limit) throws IOException {
        byte[] actual = BoundedFileReader.readFully(
                new ByteArrayInputStream(source), hint, limit, SIZE_ERROR);
        check(Arrays.equals(source, actual), "changed bytes for size " + source.length + "/hint " + hint);
    }

    private static void checkOversize(byte[] source, long hint, int limit) throws IOException {
        try {
            BoundedFileReader.readFully(new ByteArrayInputStream(source), hint, limit, SIZE_ERROR);
            throw new AssertionError("accepted oversized input");
        } catch (IllegalArgumentException expected) {
            check(SIZE_ERROR.equals(expected.getMessage()), "lost size error");
        }
    }

    public static void main(String[] args) throws Exception {
        for (int size : new int[] { 0, 1, 16383, 16384, 16385, 65536, 12 * 1024 * 1024 }) {
            byte[] source = bytes(size);
            checkRead(source, size, size);       // stable file, including the exact bound
            checkRead(source, 0, size);          // grows from an empty length hint
            checkRead(source, size / 2, size);   // grows after the opened-file size hint
            checkRead(source, (long) size + 5, size + 10); // shrinks after the size hint
            checkRead(source, Long.MAX_VALUE, size); // hint cannot allocate beyond the bound
        }
        checkRead(bytes(31), -1, 40);
        checkOversize(bytes(1), 0, 0);
        checkOversize(bytes(17), 16, 16);
        checkOversize(bytes(65537), 1, 65536);
        final byte[] source = bytes(40001);
        InputStream shortReads = new ByteArrayInputStream(source) {
            private boolean zero;
            @Override public synchronized int read(byte[] data, int offset, int count) {
                zero = !zero;
                return zero ? 0 : super.read(data, offset, Math.min(count, 7));
            }
        };
        check(Arrays.equals(source, BoundedFileReader.readFully(shortReads, 100, source.length,
                SIZE_ERROR)), "zero/short reads lost bytes");
        InputStream failing = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("read failed"); }
        };
        try {
            BoundedFileReader.readFully(failing, 0, 100, SIZE_ERROR);
            throw new AssertionError("swallowed read failure");
        } catch (IOException expected) {
            check("read failed".equals(expected.getMessage()), "changed read failure");
        }
        Path file = Files.createTempFile("median-bounded-reader-", ".bin");
        try {
            Files.write(file, source);
            check(Arrays.equals(source, BoundedFileReader.read(file.toFile(), source.length,
                    SIZE_ERROR)), "file reader changed bytes");
            // Deletion also verifies the successful path releases its handle on Windows.
            Files.delete(file);
            Files.write(file, source);
            try {
                BoundedFileReader.read(file.toFile(), source.length - 1, SIZE_ERROR);
                throw new AssertionError("file reader accepted oversized file");
            } catch (IllegalArgumentException expected) {
                check(SIZE_ERROR.equals(expected.getMessage()), "file lost size error");
            }
            // The exceptional path must release the file handle too.
            Files.delete(file);
        } finally {
            Files.deleteIfExists(file);
        }
        System.out.println("BoundedFileReaderSelfTest passed");
    }
}
