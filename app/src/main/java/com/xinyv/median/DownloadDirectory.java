// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Median Browser contributors

package com.xinyv.median;

import android.content.Context;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.widget.Toast;

/** Device-local SAF permission and destination; never exported as a portable setting. */
final class DownloadDirectory {
    private static final String PREFS = "median_download_directory";
    private static final String TREE = "tree_uri";
    private static final String NAME = "display_name";

    private DownloadDirectory() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Uri selectedTree(Context context) {
        String value = prefs(context).getString(TREE, "");
        return value == null || value.length() == 0 ? null : Uri.parse(value);
    }

    static String label(Context context) {
        if (selectedTree(context) == null)
            return Build.VERSION.SDK_INT >= 29 ? "Download/Median（默认）" : "应用下载目录（默认）";
        return prefs(context).getString(NAME, "自定义下载文件夹");
    }

    static Intent picker(Context context) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        Uri current = selectedTree(context);
        if (current != null) intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, current);
        return intent;
    }

    static void showPickerDialog(final Activity activity, final int requestCode, final Runnable returnAction) {
        new AlertDialog.Builder(activity).setTitle("下载文件夹")
                .setMessage("当前：" + label(activity) + "\n\n下载完成时保存到所选目录，已有文件不会移动。外部下载器使用其自身的目录设置。")
                .setPositiveButton("选择文件夹", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        try { activity.startActivityForResult(picker(activity), requestCode); }
                        catch (ActivityNotFoundException error) {
                            Toast.makeText(activity, "系统文件夹选择器不可用", Toast.LENGTH_SHORT).show();
                            returnTo(activity, returnAction);
                        }
                    }
                }).setNeutralButton("恢复默认", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        reset(activity);
                        Toast.makeText(activity, "已恢复默认下载文件夹", Toast.LENGTH_SHORT).show();
                        returnTo(activity, returnAction);
                    }
                }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { returnTo(activity, returnAction); }
                }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override public void onCancel(DialogInterface dialog) { returnTo(activity, returnAction); }
                }).show();
    }

    static void handlePickerResult(final Activity activity, int resultCode, final Intent data, final Runnable returnAction) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            returnTo(activity, returnAction);
            return;
        }
        final Context context = activity.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                String message;
                try {
                    save(context, data.getData(), data.getFlags());
                    message = "下载文件夹已改为：" + label(context);
                } catch (Exception error) { message = "未修改下载文件夹：" + ErrorText.message(error, 120); }
                final String result = message;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override public void run() {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        Toast.makeText(activity, result, Toast.LENGTH_LONG).show();
                        returnTo(activity, returnAction);
                    }
                });
            }
        }, "Median-download-directory").start();
    }

    private static void returnTo(Activity activity, Runnable action) {
        if (action != null && !activity.isFinishing() && !activity.isDestroyed()) action.run();
    }

    static void save(Context context, Uri tree, int resultFlags) throws Exception {
        if (tree == null || !"content".equals(tree.getScheme()) || !DocumentsContract.isTreeUri(tree))
            throw new IllegalArgumentException("请选择有效的文件夹");
        int flags = resultFlags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if ((flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0)
            throw new IllegalArgumentException("所选文件夹未授予写入权限");
        boolean alreadyGranted = hasWriteGrant(context, tree);
        context.getContentResolver().takePersistableUriPermission(tree, flags);
        try {
            String name = requireWritable(context, tree);
            if (!prefs(context).edit().putString(TREE, tree.toString()).putString(NAME, name).commit())
                throw new IllegalStateException("无法保存下载文件夹设置");
        } catch (Exception error) {
            if (!alreadyGranted) try { context.getContentResolver().releasePersistableUriPermission(tree, flags); }
            catch (RuntimeException ignored) {}
            throw error;
        }
        // Keep old grants: completed files in previous folders must remain readable.
    }

    static void reset(Context context) {
        prefs(context).edit().remove(TREE).remove(NAME).apply();
    }

    static boolean hasWriteGrant(Context context, Uri tree) {
        if (tree == null) return false;
        for (UriPermission permission : context.getContentResolver().getPersistedUriPermissions())
            if (tree.equals(permission.getUri()) && permission.isReadPermission() && permission.isWritePermission()) return true;
        return false;
    }

    static String requireWritable(Context context, Uri tree) throws Exception {
        if (!hasWriteGrant(context, tree))
            throw new IllegalStateException("下载文件夹授权已失效，请在下载设置中重新选择文件夹");
        Uri document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        try (Cursor cursor = context.getContentResolver().query(document, new String[] {
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_FLAGS }, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst() ||
                    !DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(1)) ||
                    (cursor.getLong(2) & DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) == 0)
                throw new IllegalStateException("所选下载文件夹不可写，请重新选择文件夹");
            String name = cursor.getString(0);
            return name == null || name.length() == 0 ? "自定义下载文件夹" : name;
        } catch (SecurityException error) {
            throw new IllegalStateException("下载文件夹授权已失效，请在下载设置中重新选择文件夹", error);
        } catch (IllegalStateException error) { throw error;
        } catch (Exception error) {
            throw new IllegalStateException("下载文件夹不可用，请在下载设置中重新选择文件夹", error);
        }
    }

    static boolean isTreeDocument(Uri uri) {
        return uri != null && "content".equals(uri.getScheme()) && DocumentsContract.isTreeUri(uri) &&
                uri.getPathSegments().contains("document");
    }
}
