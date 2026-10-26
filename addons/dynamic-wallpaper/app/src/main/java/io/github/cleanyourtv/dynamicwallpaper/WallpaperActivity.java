package io.github.cleanyourtv.dynamicwallpaper;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The page the card opens: the reading the wallpaper was chosen from, which picture that is, and a button that takes a
 * fresh reading now. Deliberately small — the picture itself is on Home, this page only explains it.
 *
 * Everything here is drawn in code: no layout file, no extra dependency, and the page works with the D-pad (the button
 * is the only focusable view, so Up and Down stay on it).
 */
public final class WallpaperActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView headline, detail, pictureLine, updatedLine;
    private ImageView preview;
    private Button refresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.BLACK);
        int pad = Math.round(48 * dp);
        root.setPadding(pad, pad, pad, pad);

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams pictureParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.2f);
        pictureParams.setMarginEnd(Math.round(40 * dp));
        root.addView(preview, pictureParams);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        headline = text(column, 34, Color.WHITE);
        detail = text(column, 20, 0xCCFFFFFF);
        pictureLine = text(column, 16, 0x99FFFFFF);
        updatedLine = text(column, 16, 0x99FFFFFF);

        refresh = new Button(this);
        refresh.setText(R.string.refresh);
        refresh.setFocusable(true);
        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = Math.round(24 * dp);
        column.addView(refresh, buttonParams);
        refresh.setOnClickListener(v -> load(true));

        setContentView(root);
        WallpaperJob.schedule(this);
        load(false);
    }

    private TextView text(LinearLayout column, int sizeSp, int color) {
        TextView view = new TextView(this);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setPadding(0, Math.round(4 * getResources().getDisplayMetrics().density), 0,
                Math.round(4 * getResources().getDisplayMetrics().density));
        column.addView(view);
        return view;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Coming back to the page is a good moment to look again, but never a reason to wait on the network: the
        // cache answers at once, and a fetch only runs when the reading is older than 30 minutes.
        load(false);
    }

    private void load(boolean force) {
        refresh.setEnabled(false);
        headline.setText(R.string.loading);
        new Thread(() -> {
            final Weather.Reading reading = WallpaperJob.publish(this, force);
            main.post(() -> show(reading));
        }, "wallpaper-page").start();
    }

    private void show(Weather.Reading reading) {
        refresh.setEnabled(true);
        if (reading == null) {
            headline.setText(R.string.no_reading);
            detail.setText(R.string.no_reading_hint);
            pictureLine.setText("");
            updatedLine.setText("");
            preview.setImageDrawable(null);
            return;
        }
        String place = getString(R.string.place_name);
        String condition = WallpaperJob.condition(this, reading);
        headline.setText(getString(R.string.headline, place, reading.tempC, condition));
        detail.setText(getString(R.string.detail, reading.feelsC, reading.highC, reading.lowC,
                reading.humidity, reading.windKmh, reading.rainChance));
        pictureLine.setText(getString(R.string.picture_line, reading.picture()));
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(reading.takenAt));
        updatedLine.setText(reading.stale ? getString(R.string.updated_stale, time) : getString(R.string.updated, time));
        preview.setImageDrawable(new android.graphics.drawable.BitmapDrawable(getResources(),
                android.graphics.BitmapFactory.decodeFile(Wallpapers.picture(this).getPath())));
    }
}
