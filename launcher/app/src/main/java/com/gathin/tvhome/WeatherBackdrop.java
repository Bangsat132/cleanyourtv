package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.Random;

/**
 * The weather behind Home: a sky drawn in code for the reading's mood and its own day or night, so the launcher carries
 * no picture files at all (an APK of about 520 KB has no room for twelve 1280x720 wallpapers, and drawing is cheaper
 * than decoding one on a 1 GB TV).
 *
 * Twelve skies: clear, clouds, rain, storm, fog and snow, each by day and by night. Every one of them fades to black in
 * its lower third, where the dock, the tile names and the header text sit, so nothing on Home fights the picture.
 *
 * Nothing animates and nothing runs between changes: {@link #set} only repaints when the mood, the day/night flag or the
 * size actually changed, which is a handful of times a day. With config {@code weather_backdrop} off the view is never
 * added to Home at all.
 */
final class WeatherBackdrop extends View {
    static final String CLEAR = "clear", CLOUDS = "clouds", RAIN = "rain", STORM = "storm", FOG = "fog", SNOW = "snow";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    /** The mood on screen; null until a reading arrives, which leaves the view transparent. */
    private String mood;
    private boolean day = true;

    WeatherBackdrop(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayerType(LAYER_TYPE_NONE, null);
    }

    /** The sky for a reading; a null mood (no reading yet) clears the view. */
    void set(String mood, boolean day) {
        if (mood == null ? this.mood == null : (mood.equals(this.mood) && day == this.day)) return;
        this.mood = mood;
        this.day = day;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mood == null) return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        // A fresh sky every time: the view is repainted a handful of times a day, not per frame.
        paint.setShader(null);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);

        int[] sky = sky(mood, day);
        paint.setShader(new LinearGradient(0, 0, 0, h, sky[0], sky[1], Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        if (day) sun(canvas, w, h);
        else {
            stars(canvas, w, h);
            moon(canvas, w, h);
        }
        if (CLOUDS.equals(mood) || RAIN.equals(mood) || STORM.equals(mood) || SNOW.equals(mood)) clouds(canvas, w, h);
        if (FOG.equals(mood)) fog(canvas, w, h);
        if (RAIN.equals(mood) || STORM.equals(mood)) rain(canvas, w, h);
        if (SNOW.equals(mood)) snow(canvas, w, h);
        if (STORM.equals(mood) && !day) lightning(canvas, w, h);
        ridge(canvas, w, h);
        scrim(canvas, w, h);
    }

    /** The sky's two ends: [top, horizon]. */
    private static int[] sky(String mood, boolean day) {
        if (day) {
            switch (mood) {
                case CLEAR: return new int[]{0xFF5C86BE, 0xFFA8C4DC};
                case CLOUDS: return new int[]{0xFF6E7C8C, 0xFF96A2B2};
                case RAIN: return new int[]{0xFF404A5A, 0xFF687482};
                case STORM: return new int[]{0xFF28283A, 0xFF4A485C};
                case FOG: return new int[]{0xFF969896, 0xFFBCBCB8};
                case SNOW: return new int[]{0xFF8092A8, 0xFFC4CEDC};
                default: return new int[]{0xFF5C86BE, 0xFFA8C4DC};
            }
        }
        switch (mood) {
            case CLEAR: return new int[]{0xFF080C1C, 0xFF1E2A42};
            case CLOUDS: return new int[]{0xFF101422, 0xFF2C3242};
            case RAIN: return new int[]{0xFF0A0E18, 0xFF222838};
            case STORM: return new int[]{0xFF08080E, 0xFF2E2A3C};
            case FOG: return new int[]{0xFF16181C, 0xFF3A3C40};
            case SNOW: return new int[]{0xFF0A1020, 0xFF223040};
            default: return new int[]{0xFF080C1C, 0xFF1E2A42};
        }
    }

    /** A warm glow just above the horizon, brightest on a clear day. */
    private void sun(Canvas canvas, int w, int h) {
        boolean bright = CLEAR.equals(mood);
        int colour = bright ? 0xFFFFF2D2 : 0xFFD8DEE8;
        int alpha = bright ? 150 : 70;
        float radius = h * 0.55f;
        paint.setShader(new RadialGradient(w * 0.5f, h * 0.62f, radius,
                (alpha << 24) | (colour & 0xFFFFFF), 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawCircle(w * 0.5f, h * 0.62f, radius, paint);
        paint.setShader(null);
        if (bright) {
            paint.setColor(0xFFF6F0DE);
            canvas.drawCircle(w * 0.5f, h * 0.60f, h * 0.035f, paint);
        }
    }

    private void moon(Canvas canvas, int w, int h) {
        paint.setShader(new RadialGradient(w * 0.78f, h * 0.18f, h * 0.30f, 0x40C8D4F0, 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawCircle(w * 0.78f, h * 0.18f, h * 0.30f, paint);
        paint.setShader(null);
        paint.setColor(0xFFE8ECE0);
        canvas.drawCircle(w * 0.78f, h * 0.18f, h * 0.028f, paint);
    }

    /** Fixed seeds: the same sky every time Home is drawn, instead of stars and rain that jump on each repaint. */
    private void stars(Canvas canvas, int w, int h) {
        if (SNOW.equals(mood) || RAIN.equals(mood) || STORM.equals(mood) || FOG.equals(mood) || CLOUDS.equals(mood)) return;
        Random random = new Random(5);
        paint.setColor(0xFFD2DCF0);
        for (int i = 0; i < 140; i++) {
            float x = random.nextFloat() * w, y = random.nextFloat() * h * 0.6f;
            float r = random.nextInt(3) == 0 ? 1.6f : 0.9f;
            paint.setAlpha(random.nextInt(120) + 90);
            canvas.drawCircle(x, y, r, paint);
        }
        paint.setAlpha(255);
    }

    private void clouds(Canvas canvas, int w, int h) {
        Random random = new Random(21);
        int base = STORM.equals(mood) ? 0xFF565064 : (day ? 0xFFE4EAF2 : 0xFF606878);
        int layers = STORM.equals(mood) ? 8 : 5;
        paint.setColor(base);
        for (int i = 0; i < layers; i++) {
            float y = h * (0.16f + 0.06f * i) + random.nextFloat() * h * 0.05f;
            paint.setAlpha(STORM.equals(mood) ? 70 : 34);
            for (int j = 0; j < 6; j++) {
                float cw = w * (0.18f + random.nextFloat() * 0.22f);
                float ch = h * (0.05f + random.nextFloat() * 0.05f);
                float x = random.nextFloat() * w - cw * 0.3f;
                oval.set(x, y, x + cw, y + ch);
                canvas.drawOval(oval, paint);
            }
        }
        paint.setAlpha(255);
    }

    private void fog(Canvas canvas, int w, int h) {
        Random random = new Random(31);
        paint.setColor(day ? 0xFFDCDCD8 : 0xFF8A8C92);
        for (int i = 0; i < 4; i++) {
            float y = h * (0.36f + 0.10f * i);
            paint.setAlpha(46 - i * 6);
            for (int j = 0; j < 5; j++) {
                float cw = w * (0.28f + random.nextFloat() * 0.3f);
                float ch = h * (0.05f + random.nextFloat() * 0.04f);
                float x = random.nextFloat() * w - cw * 0.25f;
                oval.set(x, y, x + cw, y + ch);
                canvas.drawOval(oval, paint);
            }
        }
        paint.setAlpha(255);
    }

    private void rain(Canvas canvas, int w, int h) {
        Random random = new Random(41);
        paint.setColor(day ? 0xFFB4C4D8 : 0xFF8496B4);
        int streaks = STORM.equals(mood) ? 320 : 200;
        for (int i = 0; i < streaks; i++) {
            float x = random.nextFloat() * w, y = random.nextFloat() * h * 0.85f;
            float length = h * (0.02f + random.nextFloat() * 0.03f);
            paint.setAlpha(40 + random.nextInt(60));
            paint.setStrokeWidth(1.2f);
            canvas.drawLine(x, y, x + length * 0.35f, y + length, paint);
        }
        paint.setAlpha(255);
        paint.setStrokeWidth(1f);
    }

    private void snow(Canvas canvas, int w, int h) {
        Random random = new Random(51);
        paint.setColor(0xFFF2F6FA);
        for (int i = 0; i < 220; i++) {
            float x = random.nextFloat() * w, y = random.nextFloat() * h * 0.8f;
            paint.setAlpha(90 + random.nextInt(120));
            canvas.drawCircle(x, y, 0.9f + random.nextFloat() * 1.8f, paint);
        }
        paint.setAlpha(255);
    }

    private void lightning(Canvas canvas, int w, int h) {
        path.reset();
        path.moveTo(w * 0.66f, h * 0.18f);
        path.lineTo(w * 0.62f, h * 0.40f);
        path.lineTo(w * 0.66f, h * 0.40f);
        path.lineTo(w * 0.60f, h * 0.58f);
        path.lineTo(w * 0.72f, h * 0.34f);
        path.lineTo(w * 0.67f, h * 0.34f);
        path.lineTo(w * 0.73f, h * 0.18f);
        path.close();
        paint.setColor(0xFFE2DEFA);
        canvas.drawPath(path, paint);
    }

    /** Two ridgelines, so the sky has a horizon instead of ending in a band of colour. */
    private void ridge(Canvas canvas, int w, int h) {
        int far = day ? 0xFF3A4A5C : 0xFF141A26;
        int near = day ? 0xFF22303E : 0xFF080C12;
        ridgePath(w, h, 0.66f, 0.09f, 3, far);
        canvas.drawPath(path, paint);
        ridgePath(w, h, 0.75f, 0.06f, 8, near);
        canvas.drawPath(path, paint);
    }

    private void ridgePath(int w, int h, float base, float amplitude, int seed, int colour) {
        Random random = new Random(seed);
        float step = w / 14f;
        float y = h * base;
        path.reset();
        path.moveTo(0, h);
        path.lineTo(0, y);
        for (float x = 0; x <= w + step; x += step) {
            y += (random.nextFloat() - 0.5f) * h * amplitude * 0.9f;
            y = Math.max(h * (base - amplitude), Math.min(h * (base + amplitude * 0.4f), y));
            path.lineTo(x, y);
        }
        path.lineTo(w, h);
        path.close();
        paint.setColor(colour);
    }

    /** The lower third fades to black: the dock, the tile names and the header text sit there. */
    private void scrim(Canvas canvas, int w, int h) {
        paint.setShader(new LinearGradient(0, h * 0.45f, 0, h, 0x00000000, 0xE6000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, h * 0.45f, w, h, paint);
        paint.setShader(null);
        // A little shade at the very top, for the clock and the pills.
        paint.setShader(new LinearGradient(0, 0, 0, h * 0.30f, 0x99000000, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h * 0.30f, paint);
        paint.setShader(null);
    }

    /** The picture's name for the line under the date, in the TV's own words. */
    static int labelOf(String mood, boolean day) {
        switch (mood == null ? CLOUDS : mood) {
            case CLEAR: return day ? R.string.sky_clear_day : R.string.sky_clear_night;
            case RAIN: return day ? R.string.sky_rain_day : R.string.sky_rain_night;
            case STORM: return day ? R.string.sky_storm_day : R.string.sky_storm_night;
            case FOG: return day ? R.string.sky_fog_day : R.string.sky_fog_night;
            case SNOW: return day ? R.string.sky_snow_day : R.string.sky_snow_night;
            default: return day ? R.string.sky_clouds_day : R.string.sky_clouds_night;
        }
    }

    static int colorOf(String mood) {
        switch (mood == null ? CLOUDS : mood) {
            case CLEAR: return Color.rgb(0x5C, 0x86, 0xBE);
            case RAIN: return Color.rgb(0x50, 0x6A, 0x84);
            case STORM: return Color.rgb(0x4A, 0x48, 0x5C);
            case FOG: return Color.rgb(0x8A, 0x8C, 0x92);
            case SNOW: return Color.rgb(0x9A, 0xAA, 0xBE);
            default: return Color.rgb(0x6E, 0x7C, 0x8C);
        }
    }
}
