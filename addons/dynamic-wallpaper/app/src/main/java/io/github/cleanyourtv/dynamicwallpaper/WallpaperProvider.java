package io.github.cleanyourtv.dynamicwallpaper;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * The two files a launcher may read: {@code content://<applicationId>.cardart/wallpaper.png}, the picture this app
 * chose, and {@code wallpaper_info.txt}, the line describing it.
 *
 * A launcher cannot read another app's private storage and asks for no permission, so a provider it can open is the
 * only way to hand over a picture chosen at runtime (docs/specs/card.md, "Live card art"). It serves those two paths
 * and nothing else: no rows, no writes, no permission of its own, and no directory it could be talked into listing.
 * Until a reading has been published the read fails and the launcher keeps its own wallpaper.
 */
public final class WallpaperProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        // Write or truncate modes would let any app replace the picture: read only, always.
        if (!("r".equals(mode) || "rt".equals(mode))) throw new FileNotFoundException("read only: " + uri);
        Context ctx = getContext();
        if (ctx == null || !serves(uri)) throw new FileNotFoundException("not the wallpaper: " + uri);
        String name = info(uri) ? Wallpapers.INFO : Wallpapers.PICTURE;
        return ParcelFileDescriptor.open(new File(ctx.getFilesDir(), name), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        if (!serves(uri)) return null;
        return info(uri) ? "text/plain" : "image/png";
    }

    private static boolean info(Uri uri) {
        String path = uri.getPath();
        return path != null && path.equals("/" + Wallpapers.INFO);
    }

    /** True for the two paths this provider serves, and for no other. */
    private static boolean serves(Uri uri) {
        String path = uri.getPath();
        return path != null && (path.equals("/" + Wallpapers.PICTURE) || path.equals("/" + Wallpapers.INFO));
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("the wallpaper is written by this app, not by its readers");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("the wallpaper is written by this app, not by its readers");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("the wallpaper is written by this app, not by its readers");
    }
}
