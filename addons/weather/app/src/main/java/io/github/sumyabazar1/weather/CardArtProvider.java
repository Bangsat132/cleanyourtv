package io.github.sumyabazar1.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * The one picture the launcher may read: {@code content://<applicationId>.cardart/card.png}, the PNG WeatherWidget
 * draws from the last reading. A launcher cannot read this app's private files and asks no one's permission, so a
 * provider it can open is the only way to hand over a picture drawn at runtime (docs/specs/card.md, "Live card
 * art"). It serves that file and nothing else: no rows, no writes, no permission of its own, and no directory it
 * could be talked into listing. Until WeatherWidget has published a reading, or the file is gone, the read fails and
 * the launcher draws the card's declared banner instead.
 */
public final class CardArtProvider extends ContentProvider {

    /** The path inside the provider's authority; the launcher reads exactly this. */
    static final String PATH = "card.png";

    /** The drawing the provider serves, in this app's private storage. */
    static File art(Context ctx) {
        return new File(ctx.getFilesDir(), WeatherWidget.FILE);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        // Write or truncate modes would let any app that reads the card replace the picture: read only, always.
        if (!("r".equals(mode) || "rt".equals(mode))) throw new FileNotFoundException("read only: " + uri);
        Context ctx = getContext();
        if (ctx == null || !serves(uri)) throw new FileNotFoundException("not the card art: " + uri);
        return ParcelFileDescriptor.open(art(ctx), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return serves(uri) ? "image/png" : null;
    }

    /** True for the one path this provider serves, and for no other. */
    private static boolean serves(Uri uri) {
        String path = uri.getPath();
        return path != null && path.equals("/" + PATH);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("the card art is written by this app, not by its readers");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("the card art is written by this app, not by its readers");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("the card art is written by this app, not by its readers");
    }
}
