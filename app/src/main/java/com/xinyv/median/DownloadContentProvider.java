package com.xinyv.median;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Read-only URI bridge for downloads that an OEM MediaStore refuses to publish. */
public final class DownloadContentProvider extends ContentProvider {
    static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".downloads";

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        File file = resolve(uri);
        return DownloadFileTypes.resolveMime(file == null ? "" : file.getName(), "", "");
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File file = resolve(uri);
        MatrixCursor cursor = new MatrixCursor(new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE });
        if (file != null && file.isFile()) cursor.addRow(new Object[] { file.getName(), Long.valueOf(file.length()) });
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        File file = resolve(uri);
        if (file == null || !file.isFile()) throw new FileNotFoundException("Download not found");
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }

    static File root(Context context) {
        File external = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        File root = new File(external == null ? context.getFilesDir() : external, "published");
        if (!root.isDirectory()) root.mkdirs();
        return root;
    }

    static Uri uriFor(File file) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath(file == null ? "" : file.getName()).build();
    }

    private File resolve(Uri uri) {
        if (getContext() == null || uri == null || !AUTHORITY.equals(uri.getAuthority()) ||
                uri.getPathSegments().size() != 1) return null;
        String name = uri.getLastPathSegment();
        if (name == null || name.length() == 0 || name.length() > 220) return null;
        try {
            File root = root(getContext()).getCanonicalFile();
            File file = new File(root, name).getCanonicalFile();
            return root.equals(file.getParentFile()) ? file : null;
        } catch (Exception ignored) { return null; }
    }
}
