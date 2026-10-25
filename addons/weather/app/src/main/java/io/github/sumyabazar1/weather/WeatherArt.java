package io.github.sumyabazar1.weather;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Locale;

/**
 * The launcher tile's artwork for the weather now, drawn as one 1600 x 900 bitmap (16:9, the shape of a Clean Your
 * TV module card): the place, the temperature and the sky on the left, the air quality pill at the top right, and a
 * five column strip of the readings across the bottom. The launcher crops a card's banner to its tile, so nothing that
 * matters is drawn outside a 60 px safe margin and the blocks keep their weight when a smaller tile shows the middle.
 *
 * The drawing is Android only (Canvas, Paint, Bitmap): it never runs in a test.
 */
final class WeatherArt {
    static final int W = 1600, H = 900;
    /** The margin the tile crop may eat; nothing that matters is drawn outside it. */
    static final int SAFE = 60;
    /** The five column strip: its top and bottom, and the two text sizes inside a column. */
    static final int STRIP_TOP = 620, STRIP_BOTTOM = 830, LABEL_SIZE = 34, VALUE_SIZE = 64;

    private static final String[] LABELS = {"SUHU", "TERASA", "LEMBAB", "ANGIN", "AQI"};
    private static final int WHITE = 0xFFFFFFFF;

    private WeatherArt() { }

    /**
     * The weather now, drawn: the place in small caps, the temperature at 300 px with the sky beside it, and a strip
     * of five readings. The air quality pill is left out when the air quality read failed ({@code r.aqi < 0}).
     */
    static Bitmap draw(Context ctx, Forecast.Report r, String place, boolean fahrenheit) {
        Bitmap bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        int[] colours = backdropColours(r.code, r.day);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, 0, 0, H, colours, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, W, H, paint);
        paint.setShader(null);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(WHITE);
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        // ---- left block: place, temperature, sky, and the small line when it fits ----
        int left = SAFE + 30;
        float tempSize = 300;
        Paint temp = new Paint(text);
        temp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        temp.setTextSize(tempSize);
        temp.setLetterSpacing(-0.05f);
        String degrees = Forecast.degrees(r.tempC, fahrenheit) + "°";
        float tempWidth = temp.measureText(degrees);

        Paint placePaint = new Paint(text);
        placePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        placePaint.setTextSize(44);
        placePaint.setLetterSpacing(0.16f);
        placePaint.setColor(0xB3FFFFFF);
        String where = place == null ? "" : place.trim().toUpperCase(Locale.getDefault());

        Paint skyPaint = new Paint(text);
        skyPaint.setTextSize(64);
        String skyWord = ctx.getString(Forecast.sky(r.code, r.day));

        Paint small = new Paint(text);
        small.setTextSize(36);
        small.setColor(0xCCFFFFFF);
        small.setLetterSpacing(0.04f);
        String detail = detail(ctx, r, fahrenheit);

        // The temperature block stands in the space above the strip: centred on it, so the left half reads alone.
        float blockTop = (STRIP_TOP - 26) / 2f - 200;
        float placeRoom = 1080 - left;
        canvas.drawText(cut(placePaint, where, placeRoom), left, blockTop, placePaint);
        float baseline = blockTop + 90 + tempSize * 0.72f;
        canvas.drawText(degrees, left, baseline, temp);

        // The sky beside the temperature; the small line under both when the whole block fits above the strip.
        float skyX = left + tempWidth + 34;
        float smallBaseline = baseline + 58;
        boolean skyBeside = skyX + skyPaint.measureText(skyWord) <= 1120;
        if (skyBeside) {
            canvas.drawText(skyWord, skyX, baseline - 26, skyPaint);
        } else {
            skyPaint.setTextSize(62);
            canvas.drawText(cut(skyPaint, skyWord, 1080 - left), left, baseline + 76, skyPaint);
            smallBaseline = baseline + 142;
        }
        if (smallBaseline <= STRIP_TOP - 40 && !detail.isEmpty()) {
            canvas.drawText(cut(small, detail, 1120 - left), left, smallBaseline, small);
        }

        // ---- right side: the air quality pill, over the strip ----
        if (r.aqi >= 0) {
            Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
            pill.setColor(Forecast.aqiColour(r.aqi));
            Paint pillText = new Paint(text);
            pillText.setColor(Forecast.aqiTextColour(r.aqi));
            pillText.setTextSize(38);
            pillText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            String words = ctx.getString(Forecast.aqiLabel(r.aqi));
            float wordsWidth = pillText.measureText(words) + 64;
            RectF box = new RectF(1180 - wordsWidth, 92, 1180, 162);
            canvas.drawRoundRect(box, 35, 35, pill);
            canvas.drawText(words, box.left + 32, centred(pillText, box), pillText);
            // The reading itself in the same colour, a little larger, to the left of the words.
            pillText.setTextSize(46);
            String number = String.valueOf(r.aqi);
            canvas.drawText(number, box.left - 16 - pillText.measureText(number), centred(pillText, box), pillText);
        }

        // ---- the five column strip, in the full width under both blocks ----
        int stripLeft = SAFE + 20, stripRight = W - SAFE - 20;
        int columns = LABELS.length;
        float columnWidth = (stripRight - stripLeft) / (float) columns;
        String[] values = {Forecast.degrees(r.tempC, fahrenheit) + "°", Forecast.degrees(r.feelsC, fahrenheit) + "°",
                r.humidity + "%", String.valueOf(fahrenheit ? Forecast.mph(r.windKmh) : r.windKmh),
                r.aqi >= 0 ? String.valueOf(r.aqi) : "—"};

        Paint label = new Paint(text);
        label.setTextSize(LABEL_SIZE);
        label.setLetterSpacing(0.18f);
        label.setColor(0x99FFFFFF);
        Paint value = new Paint(text);
        value.setTextSize(VALUE_SIZE);
        value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        Paint separator = new Paint(Paint.ANTI_ALIAS_FLAG);
        separator.setColor(0x33FFFFFF);

        // One baseline for every value, so the five read across as one line; the label sits above it and under a
        // hairline, the way the readings are labelled on the page.
        float valueBaseline = STRIP_BOTTOM - 6;
        float labelBaseline = STRIP_TOP + 46;
        float dividerY = labelBaseline + 22;
        for (int i = 0; i < columns; i++) {
            float x = stripLeft + i * columnWidth;
            if (i > 0) canvas.drawRect(x, STRIP_TOP, x + 2, STRIP_BOTTOM, separator);
            float textX = x + 26;
            float room = columnWidth - 26 - (i > 0 ? 22 : 6);
            canvas.drawText(cut(label, LABELS[i], room), textX, labelBaseline, label);
            canvas.drawRect(textX, dividerY, textX + Math.min(room, columnWidth - 60), dividerY + 1.5f, separator);
            Paint pen = value;
            if ("AQI".equals(LABELS[i]) && r.aqi >= 0) {
                pen = new Paint(value);
                pen.setColor(Forecast.aqiColour(r.aqi));
            }
            canvas.drawText(cut(pen, values[i], room), textX, valueBaseline, pen);
        }
        // A hairline over the whole strip sets it apart from the temperature without a box.
        canvas.drawRect(stripLeft, STRIP_TOP - 26, stripRight, STRIP_TOP - 24, separator);
        return bitmap;
    }

    /**
     * The small line under the temperature, in the words the page uses: "Terasa 30°  ·  Lembab 78%  ·  Angin 12 km/j".
     * The degrees and wind follow the unit option; the words are the page's own strings, so the card reads in the
     * build's language.
     */
    private static String detail(Context ctx, Forecast.Report r, boolean fahrenheit) {
        StringBuilder out = new StringBuilder();
        out.append(small(ctx, R.string.feels_like, Forecast.degrees(r.feelsC, fahrenheit)));
        out.append("  ·  ").append(small(ctx, R.string.humidity, r.humidity));
        out.append("  ·  ").append(fahrenheit ? small(ctx, R.string.wind_mph, Forecast.mph(r.windKmh))
                : small(ctx, R.string.wind, r.windKmh));
        return out.toString();
    }

    /** A page string with its one number filled in, and the spacing the card reads with. */
    private static String small(Context ctx, int id, int number) {
        return ctx.getString(id, number).replace(" ", "  ");
    }

    /** The baseline that centres a line in a box, on the font's own ascent and descent. */
    private static float centred(Paint paint, RectF box) {
        return box.centerY() - (paint.descent() + paint.ascent()) / 2f;
    }

    /** The text, cut with an ellipsis where it is wider than the room it has. */
    private static String cut(Paint paint, String text, float room) {
        if (room <= 0 || paint.measureText(text) <= room) return text;
        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && paint.measureText(text.substring(0, end) + ellipsis) > room) end--;
        return text.substring(0, end).trim() + ellipsis;
    }

    /**
     * The page's sky colours, the same tables as {@link Forecast#backdrop}: the gradient's two stops, so the card and
     * the page behind it are the same sky. (A Drawable cannot be read back for its colours, so the pairs live here.)
     */
    static int[] backdropColours(int code, boolean day) {
        if (code <= 1) return day ? new int[]{0xFF2A6AA8, 0xFF0A1626} : new int[]{0xFF0D1B3E, 0xFF020308};
        if (code == 45 || code == 48) return new int[]{0xFF5A6068, 0xFF101113};
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return new int[]{0xFF6F8497, 0xFF101820};
        if (code >= 95) return new int[]{0xFF2D2140, 0xFF07050C};
        if (code >= 51) return new int[]{0xFF1E2C3D, 0xFF06090E};
        return new int[]{0xFF3B4654, 0xFF0C0F14};
    }
}
