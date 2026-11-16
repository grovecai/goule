package com.goule.prototype;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Runs in the test APK process, which does not bundle the target APK's Kotlin runtime. */
public final class ThemeImageFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "image/png"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String kind = uri.getLastPathSegment();
        if (!"r".equals(mode) || "denied".equals(kind)) throw new FileNotFoundException("Fixture unavailable");
        File file = new File(getContext().getCacheDir(), "theme-fixture-" + kind);
        try {
            if ("valid".equals(kind) || "replace".equals(kind)) {
                boolean first = "valid".equals(kind);
                Bitmap bitmap = Bitmap.createBitmap(first ? 2400 : 90, first ? 1200 : 300, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(first ? Color.RED : Color.BLUE);
                try (FileOutputStream output = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
                } finally { bitmap.recycle(); }
            } else if ("oversize".equals(kind)) {
                try (RandomAccessFile output = new RandomAccessFile(file, "rw")) { output.setLength(20L * 1024 * 1024 + 1); }
            } else {
                try (FileOutputStream output = new FileOutputStream(file)) { output.write(new byte[]{1, 2, 3}); }
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (IOException error) {
            throw new FileNotFoundException(error.getMessage());
        }
    }
}
