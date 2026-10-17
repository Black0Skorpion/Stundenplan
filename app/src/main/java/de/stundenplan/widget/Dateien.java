package de.stundenplan.widget;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Gibt Dateien aus dem Ordner „teilen“ zum Weiterschicken frei (z. B. Stundenplan.ics), nur lesend. */
public class Dateien extends ContentProvider {

    static final String AUTORITAET = "de.stundenplan.widget.dateien";

    static File ordner(Context ctx) {
        File f = new File(ctx.getCacheDir(), "teilen");
        f.mkdirs();
        return f;
    }

    static Uri uri(File f) {
        return Uri.parse("content://" + AUTORITAET + "/" + Uri.encode(f.getName()));
    }

    private File datei(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("..")) throw new FileNotFoundException();
        File f = new File(ordner(getContext()), name);
        if (!f.exists()) throw new FileNotFoundException();
        return f;
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(datei(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            File f = datei(uri);
            MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            c.addRow(new Object[]{f.getName(), f.length()});
            return c;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        String n = uri.getLastPathSegment();
        if (n != null && n.endsWith(".ics")) return "text/calendar";
        if (n != null && n.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
