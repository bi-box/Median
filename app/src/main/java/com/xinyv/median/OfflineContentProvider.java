package com.xinyv.median;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Read-only provider for saved MHTML pages. Paths are restricted to the app's offline folder. */
public final class OfflineContentProvider extends ContentProvider {
    static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".offline";

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) { return "application/x-mimearchive"; }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File file = resolve(uri);
        MatrixCursor cursor = new MatrixCursor(new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE });
        if (file != null && file.isFile()) cursor.addRow(new Object[] { file.getName(), Long.valueOf(file.length()) });
        return cursor;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        File file = resolve(uri);
        if (file == null || !file.isFile()) throw new FileNotFoundException("Offline page not found");
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }

    private File resolve(Uri uri) {
        if (getContext() == null || uri == null || !AUTHORITY.equals(uri.getAuthority())) return null;
        String name = uri.getLastPathSegment();
        if (name == null || !name.matches("[A-Za-z0-9._-]+") || !name.endsWith(".mht")) return null;
        try {
            File root = new File(getContext().getFilesDir(), "offline").getCanonicalFile();
            File file = new File(root, name).getCanonicalFile();
            return root.equals(file.getParentFile()) ? file : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
