package com.xinyv.median;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Bounded, app-private storage for optional home wallpaper and logo images. */
final class HomeImageStore {
    enum Kind { WALLPAPER, LOGO }

    private static final int WALLPAPER_MAX_DIMENSION = 2048;
    private static final int LOGO_MAX_DIMENSION = 512;
    private static final long WALLPAPER_MAX_SOURCE = 40L * 1024L * 1024L;
    private static final long LOGO_MAX_SOURCE = 16L * 1024L * 1024L;
    private static final long WALLPAPER_MAX_FILE = 4L * 1024L * 1024L;
    private static final long LOGO_MAX_FILE = 2L * 1024L * 1024L;

    private final Context context;
    private final File wallpaper;
    private final File logo;

    HomeImageStore(Context context) {
        this.context = context.getApplicationContext();
        wallpaper = new File(this.context.getFilesDir(), "home-wallpaper.jpg");
        logo = new File(this.context.getFilesDir(), "home-logo.png");
    }

    boolean has(Kind kind) { return file(kind).isFile() && file(kind).length() > 0L; }

    long version(Kind kind) { return has(kind) ? file(kind).lastModified() : 0L; }

    String mime(Kind kind) { return kind == Kind.WALLPAPER ? "image/jpeg" : "image/png"; }

    InputStream open(Kind kind) throws IOException {
        if (!has(kind)) return null;
        return new FileInputStream(file(kind));
    }

    void remove(Kind kind) {
        File target = file(kind);
        if (target.exists()) target.delete();
        File backup = new File(target.getPath() + ".bak");
        if (backup.exists()) backup.delete();
    }

    void removeAll() {
        remove(Kind.WALLPAPER);
        remove(Kind.LOGO);
    }

    void save(Uri uri, Kind kind) throws IOException {
        if (uri == null) throw new IOException("没有选择图片");
        rejectOversizedSource(uri, kind);
        int rotation = readRotation(uri);
        Bitmap decoded = null;
        Bitmap transformed = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            decode(uri, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 32768 || bounds.outHeight > 32768 ||
                    (long) bounds.outWidth * (long) bounds.outHeight > 300_000_000L)
                throw new IOException("无法识别图片或图片尺寸过大");

            int limit = kind == Kind.WALLPAPER ? WALLPAPER_MAX_DIMENSION : LOGO_MAX_DIMENSION;
            int sample = 1;
            int largest = Math.max(bounds.outWidth, bounds.outHeight);
            while ((largest + sample - 1) / sample > limit) sample <<= 1;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = kind == Kind.WALLPAPER ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
            decoded = decode(uri, options);
            if (decoded == null) throw new IOException("图片解码失败");
            transformed = rotate(decoded, rotation);
            if (transformed != decoded) { decoded.recycle(); decoded = null; }
            transformed = scaleDown(transformed, limit);
            writeAtomic(transformed, kind);
        } catch (OutOfMemoryError exhausted) {
            throw new IOException("图片过大，设备内存不足");
        } finally {
            if (decoded != null && decoded != transformed && !decoded.isRecycled()) decoded.recycle();
            if (transformed != null && !transformed.isRecycled()) transformed.recycle();
        }
    }

    private Bitmap decode(Uri uri, BitmapFactory.Options options) throws IOException {
        InputStream input = null;
        try {
            input = context.getContentResolver().openInputStream(uri);
            if (input == null) throw new IOException("无法读取所选图片");
            return BitmapFactory.decodeStream(input, null, options);
        } finally {
            if (input != null) try { input.close(); } catch (IOException ignored) {}
        }
    }

    private void rejectOversizedSource(Uri uri, Kind kind) throws IOException {
        AssetFileDescriptor descriptor = null;
        try {
            try { descriptor = context.getContentResolver().openAssetFileDescriptor(uri, "r"); }
            catch (IOException unavailableLength) { return; }
            long length = descriptor == null ? -1L : descriptor.getLength();
            long limit = kind == Kind.WALLPAPER ? WALLPAPER_MAX_SOURCE : LOGO_MAX_SOURCE;
            if (length > limit) throw new IOException(kind == Kind.WALLPAPER ? "壁纸原图不能超过 40 MB" : "Logo 原图不能超过 16 MB");
        } finally {
            if (descriptor != null) try { descriptor.close(); } catch (IOException ignored) {}
        }
    }

    private int readRotation(Uri uri) {
        InputStream input = null;
        try {
            input = context.getContentResolver().openInputStream(uri);
            if (input == null) return 0;
            int value = new ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (value == ExifInterface.ORIENTATION_ROTATE_90) return 90;
            if (value == ExifInterface.ORIENTATION_ROTATE_180) return 180;
            if (value == ExifInterface.ORIENTATION_ROTATE_270) return 270;
        } catch (Exception ignored) {
        } finally {
            if (input != null) try { input.close(); } catch (IOException ignored) {}
        }
        return 0;
    }

    private static Bitmap rotate(Bitmap source, int degrees) {
        if (degrees == 0) return source;
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private static Bitmap scaleDown(Bitmap source, int limit) {
        int width = source.getWidth();
        int height = source.getHeight();
        int largest = Math.max(width, height);
        if (largest <= limit) return source;
        float scale = (float) limit / (float) largest;
        Bitmap scaled = Bitmap.createScaledBitmap(source, Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)), true);
        if (scaled != source) source.recycle();
        return scaled;
    }

    private void writeAtomic(Bitmap image, Kind kind) throws IOException {
        AtomicFile atomic = new AtomicFile(file(kind));
        FileOutputStream output = null;
        try {
            output = atomic.startWrite();
            Bitmap.CompressFormat format = kind == Kind.WALLPAPER ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG;
            int quality = kind == Kind.WALLPAPER ? 84 : 100;
            if (!image.compress(format, quality, output)) throw new IOException("图片压缩失败");
            output.flush();
            long limit = kind == Kind.WALLPAPER ? WALLPAPER_MAX_FILE : LOGO_MAX_FILE;
            if (output.getChannel().size() > limit) throw new IOException("处理后的图片仍然过大");
            atomic.finishWrite(output);
            output = null;
        } catch (IOException error) {
            if (output != null) atomic.failWrite(output);
            throw error;
        } catch (RuntimeException error) {
            if (output != null) atomic.failWrite(output);
            throw new IOException("无法保存图片", error);
        }
    }

    private File file(Kind kind) { return kind == Kind.WALLPAPER ? wallpaper : logo; }
}
