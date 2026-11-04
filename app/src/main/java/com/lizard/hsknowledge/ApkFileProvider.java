package com.lizard.hsknowledge;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/** 把 filesDir/update.apk 以 content URI 暴露给系统安装器。 */
public class ApkFileProvider extends ContentProvider {
    @Override
    public boolean onCreate() { return true; }

    @Override
    /** 安装器有时会先查文件名和大小：给一份真实元信息，别返回 null，
     *  否则个别系统会认为这个 content URI 无效、直接不弹安装界面。 */
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File f = new File(getContext().getFilesDir(), "update.apk");
        MatrixCursor c = new MatrixCursor(new String[]{"_display_name", "_size"});
        if (f.exists()) c.addRow(new Object[]{"update.apk", f.length()});
        return c;
    }

    @Override
    public String getType(Uri uri) { return "application/vnd.android.package-archive"; }

    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = new File(getContext().getFilesDir(), "update.apk");
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
